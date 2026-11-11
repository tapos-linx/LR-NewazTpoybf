"""
Database persistence layer for the Bangladesh Land Record Forensic Research Platform.
Provides both SQLAlchemy declarative models (when SQLAlchemy is installed) and a
built-in SQLite durable store (`ForensicArchiveStore`) so all uploads, persistent file paths,
SHA-256 hashes, Desktop Companion pairing tokens, OCR/HCR corrections, audit logs, and export jobs
persist across restarts without in-memory list loss.
"""

from __future__ import annotations

import datetime
import json
import os
import secrets
import sqlite3
import threading
import time
import uuid
from typing import Any, Dict, List, Optional

try:
    from sqlalchemy import Column, String, Integer, Float, Boolean, ForeignKey, DateTime, Text, JSON
    from sqlalchemy.orm import declarative_base, relationship
    Base = declarative_base()
    HAS_SQLALCHEMY = True
except ImportError:
    Base = object  # type: ignore
    HAS_SQLALCHEMY = False


if HAS_SQLALCHEMY:
    class SourceDocument(Base):  # type: ignore
        __tablename__ = "source_documents"
        id = Column(String, primary_key=True, default=lambda: str(uuid.uuid4()))
        file_name = Column(String, nullable=False)
        file_hash_sha256 = Column(String, unique=True, nullable=False, index=True)
        document_type = Column(String, nullable=False)
        upload_timestamp = Column(DateTime, default=datetime.datetime.utcnow)
        storage_uri = Column(String, nullable=False)
        raw_ocr_json = Column(JSON, nullable=True)
        extraction_confidence = Column(Float, default=0.0)
        is_verified_by_human = Column(Boolean, default=False)
        records = relationship("LandRecord", back_populates="source_doc")

    class LandRecord(Base):  # type: ignore
        __tablename__ = "land_records"
        id = Column(String, primary_key=True, default=lambda: str(uuid.uuid4()))
        source_doc_id = Column(String, ForeignKey("source_documents.id"), nullable=False)
        division = Column(String, index=True)
        district = Column(String, index=True)
        upazila = Column(String, index=True)
        mouza = Column(String, index=True)
        jl_no = Column(String, index=True)
        khatian_no = Column(String, index=True)
        dag_no = Column(String, index=True)
        total_area_decimals = Column(Float, nullable=True)
        land_class = Column(String, nullable=True)
        evidence_tier = Column(String, default="POSSIBLE")
        field_confidences = Column(JSON, nullable=False, default=dict)
        source_doc = relationship("SourceDocument", back_populates="records")


class ForensicArchiveStore:
    """
    Thread-safe SQLite persistence backing for upload queues, persistent file storage paths,
    file deduplication, Desktop Companion pairing sessions, immutable HCR correction audit logs,
    and export jobs.
    """

    def __init__(self, db_path: Optional[str] = None, storage_dir: Optional[str] = None):
        self.db_path = db_path or os.environ.get(
            "FORENSIC_SQLITE_PATH",
            os.path.join(os.path.dirname(__file__), "forensic_archive.sqlite3"),
        )
        self.storage_dir = storage_dir or os.environ.get(
            "FORENSIC_UPLOAD_STORAGE_DIR",
            os.path.join(os.path.dirname(__file__), "uploads_storage"),
        )
        os.makedirs(self.storage_dir, exist_ok=True)
        self._lock = threading.Lock()
        self._init_schema()

    def _connect(self) -> sqlite3.Connection:
        conn = sqlite3.connect(self.db_path, check_same_thread=False)
        conn.row_factory = sqlite3.Row
        return conn

    def _has_column(self, conn: sqlite3.Connection, table: str, column: str) -> bool:
        rows = conn.execute(f"PRAGMA table_info({table})").fetchall()
        return any(r["name"] == column for r in rows)

    def _init_schema(self) -> None:
        with self._lock, self._connect() as conn:
            conn.executescript(
                """
                CREATE TABLE IF NOT EXISTS upload_files (
                    id TEXT PRIMARY KEY,
                    batch_id TEXT NOT NULL,
                    filename TEXT NOT NULL,
                    stored_path TEXT,
                    mime_type TEXT,
                    extension TEXT NOT NULL,
                    size_bytes INTEGER NOT NULL,
                    sha256 TEXT,
                    document_type TEXT NOT NULL,
                    status TEXT NOT NULL,
                    reason TEXT,
                    pairing_token TEXT,
                    created_at TEXT NOT NULL
                );
                CREATE INDEX IF NOT EXISTS idx_upload_files_sha256 ON upload_files(sha256);
                CREATE INDEX IF NOT EXISTS idx_upload_files_batch ON upload_files(batch_id);

                CREATE TABLE IF NOT EXISTS desktop_pairing_sessions (
                    pairing_token TEXT PRIMARY KEY,
                    pairing_code TEXT NOT NULL,
                    expires_at INTEGER NOT NULL,
                    created_at TEXT NOT NULL
                );
                CREATE INDEX IF NOT EXISTS idx_pairing_code ON desktop_pairing_sessions(pairing_code);

                CREATE TABLE IF NOT EXISTS hcr_corrections (
                    revision_id TEXT PRIMARY KEY,
                    document_id TEXT NOT NULL,
                    page_number INTEGER NOT NULL,
                    field_name TEXT NOT NULL,
                    original_ocr_text TEXT NOT NULL,
                    corrected_text TEXT NOT NULL,
                    reviewer_id TEXT NOT NULL,
                    confidence_before REAL NOT NULL,
                    confidence_after REAL NOT NULL,
                    verification_status TEXT NOT NULL,
                    notes TEXT,
                    timestamp_utc TEXT NOT NULL
                );
                CREATE INDEX IF NOT EXISTS idx_hcr_doc_id ON hcr_corrections(document_id);

                CREATE TABLE IF NOT EXISTS audit_events (
                    event_id TEXT PRIMARY KEY,
                    event_type TEXT NOT NULL,
                    entity_id TEXT,
                    actor_id TEXT,
                    details_json TEXT NOT NULL,
                    created_at TEXT NOT NULL
                );
                """
            )
            if not self._has_column(conn, "upload_files", "stored_path"):
                conn.execute("ALTER TABLE upload_files ADD COLUMN stored_path TEXT")
            if not self._has_column(conn, "upload_files", "mime_type"):
                conn.execute("ALTER TABLE upload_files ADD COLUMN mime_type TEXT")
            if not self._has_column(conn, "upload_files", "pairing_token"):
                conn.execute("ALTER TABLE upload_files ADD COLUMN pairing_token TEXT")
            conn.commit()

    def reset_for_testing(self) -> None:
        with self._lock, self._connect() as conn:
            conn.execute("DELETE FROM upload_files")
            conn.execute("DELETE FROM desktop_pairing_sessions")
            conn.execute("DELETE FROM hcr_corrections")
            conn.execute("DELETE FROM audit_events")
            conn.commit()

    def create_pairing_session(self, ttl_seconds: int = 900) -> Dict[str, Any]:
        token = secrets.token_urlsafe(24)
        code = "".join(secrets.choice("ABCDEFGHJKLMNPQRSTUVWXYZ23456789") for _ in range(6))
        expires_at = int(time.time()) + ttl_seconds
        now_iso = datetime.datetime.now(datetime.timezone.utc).isoformat()
        with self._lock, self._connect() as conn:
            conn.execute(
                """
                INSERT INTO desktop_pairing_sessions (pairing_token, pairing_code, expires_at, created_at)
                VALUES (?, ?, ?, ?)
                """,
                (token, code, expires_at, now_iso),
            )
            conn.commit()
        return {
            "pairing_token": token,
            "pairing_code": code,
            "expires_at": expires_at,
            "created_at": now_iso,
        }

    def verify_pairing_token(self, token_or_code: str) -> Optional[str]:
        if not token_or_code or not token_or_code.strip():
            return None
        clean = token_or_code.strip()
        now_epoch = int(time.time())
        with self._lock, self._connect() as conn:
            row = conn.execute(
                """
                SELECT pairing_token, expires_at FROM desktop_pairing_sessions
                WHERE pairing_token = ? OR UPPER(pairing_code) = UPPER(?)
                LIMIT 1
                """,
                (clean, clean),
            ).fetchone()
            if not row:
                return None
            if int(row["expires_at"]) < now_epoch:
                return None
            return str(row["pairing_token"])

    def has_seen_sha256(self, sha256: str) -> Optional[str]:
        if not sha256:
            return None
        with self._lock, self._connect() as conn:
            row = conn.execute(
                "SELECT filename FROM upload_files WHERE sha256 = ? AND status IN ('QUEUED', 'IMPORTED', 'COMPLETED') LIMIT 1",
                (sha256,),
            ).fetchone()
            return str(row["filename"]) if row else None

    def record_upload_item(
        self,
        batch_id: str,
        filename: str,
        extension: str,
        size_bytes: int,
        sha256: Optional[str],
        document_type: str,
        status: str,
        reason: Optional[str] = None,
        stored_path: Optional[str] = None,
        mime_type: Optional[str] = None,
        pairing_token: Optional[str] = None,
        item_id: Optional[str] = None,
    ) -> Dict[str, Any]:
        doc_id = item_id or str(uuid.uuid4())
        now_iso = datetime.datetime.now(datetime.timezone.utc).isoformat()
        with self._lock, self._connect() as conn:
            conn.execute(
                """
                INSERT INTO upload_files
                (id, batch_id, filename, stored_path, mime_type, extension, size_bytes, sha256, document_type, status, reason, pairing_token, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                (
                    doc_id,
                    batch_id,
                    filename,
                    stored_path,
                    mime_type,
                    extension,
                    size_bytes,
                    sha256,
                    document_type,
                    status,
                    reason,
                    pairing_token,
                    now_iso,
                ),
            )
            conn.commit()
        return {
            "id": doc_id,
            "batch_id": batch_id,
            "filename": filename,
            "stored_path": stored_path,
            "mime_type": mime_type,
            "extension": extension,
            "size_bytes": size_bytes,
            "sha256": sha256,
            "document_type": document_type,
            "status": status,
            "reason": reason,
            "pairing_token": pairing_token,
            "created_at": now_iso,
        }

    def get_upload_item_by_id(self, doc_id: str) -> Optional[Dict[str, Any]]:
        with self._lock, self._connect() as conn:
            row = conn.execute(
                "SELECT * FROM upload_files WHERE id = ? LIMIT 1",
                (doc_id,),
            ).fetchone()
            return dict(row) if row else None

    def get_uploads_by_pairing_token(self, token_or_code: str) -> List[Dict[str, Any]]:
        resolved_token = self.verify_pairing_token(token_or_code)
        if not resolved_token:
            return []
        with self._lock, self._connect() as conn:
            rows = conn.execute(
                """
                SELECT * FROM upload_files
                WHERE pairing_token = ? AND status IN ('QUEUED', 'IMPORTED', 'COMPLETED')
                ORDER BY created_at ASC
                """,
                (resolved_token,),
            ).fetchall()
            return [dict(r) for r in rows]

    def get_queue_summary(self) -> Dict[str, Any]:
        with self._lock, self._connect() as conn:
            rows = conn.execute(
                "SELECT * FROM upload_files ORDER BY created_at DESC LIMIT 200"
            ).fetchall()
            total_queued = conn.execute(
                "SELECT COUNT(*) AS c FROM upload_files WHERE status = 'QUEUED'"
            ).fetchone()["c"]
            unique_hashes = conn.execute(
                "SELECT COUNT(DISTINCT sha256) AS c FROM upload_files WHERE sha256 IS NOT NULL AND sha256 != ''"
            ).fetchone()["c"]
            return {
                "total_queued_documents": int(total_queued),
                "unique_hashes_registered": int(unique_hashes),
                "queue": [dict(r) for r in rows],
            }

    def record_hcr_correction(
        self,
        document_id: str,
        page_number: int,
        field_name: str,
        original_ocr_text: str,
        corrected_text: str,
        reviewer_id: str,
        confidence_before: float,
        notes: Optional[str] = None,
    ) -> Dict[str, Any]:
        revision_id = str(uuid.uuid4())
        now_iso = datetime.datetime.now(datetime.timezone.utc).isoformat()
        # Preserve original model confidence; do not overwrite with fabricated 1.0
        record = {
            "revision_id": revision_id,
            "document_id": document_id,
            "page_number": page_number,
            "field_name": field_name,
            "original_ocr_text": original_ocr_text,
            "corrected_text": corrected_text,
            "reviewer_id": reviewer_id,
            "confidence_before": confidence_before,
            "confidence_after": confidence_before,
            "verification_status": "VERIFIED_BY_HUMAN",
            "verified_by": "USER",
            "notes": notes,
            "timestamp_utc": now_iso,
        }
        with self._lock, self._connect() as conn:
            conn.execute(
                """
                INSERT INTO hcr_corrections
                (revision_id, document_id, page_number, field_name, original_ocr_text,
                 corrected_text, reviewer_id, confidence_before, confidence_after,
                 verification_status, notes, timestamp_utc)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                (
                    revision_id,
                    document_id,
                    page_number,
                    field_name,
                    original_ocr_text,
                    corrected_text,
                    reviewer_id,
                    confidence_before,
                    confidence_before,
                    "VERIFIED_BY_HUMAN",
                    notes,
                    now_iso,
                ),
            )
            conn.execute(
                """
                INSERT INTO audit_events (event_id, event_type, entity_id, actor_id, details_json, created_at)
                VALUES (?, ?, ?, ?, ?, ?)
                """,
                (
                    str(uuid.uuid4()),
                    "HCR_MANUAL_CORRECTION",
                    document_id,
                    reviewer_id,
                    json.dumps(record, ensure_ascii=False),
                    now_iso,
                ),
            )
            conn.commit()
        return record

    def get_hcr_corrections(self, document_id: str) -> List[Dict[str, Any]]:
        with self._lock, self._connect() as conn:
            rows = conn.execute(
                "SELECT * FROM hcr_corrections WHERE document_id = ? ORDER BY timestamp_utc ASC",
                (document_id,),
            ).fetchall()
            return [dict(r) for r in rows]


archive_store = ForensicArchiveStore()
