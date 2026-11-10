"""
Bulk Ingestion & Folder/ZIP Upload Router.
Supports PDF, JPG, JPEG, PNG, TIFF, TIF, WEBP, DOCX, XLSX, and ZIP archives.
Enforces:
- Zero-byte file detection and rejection
- SHA-256 content deduplication persisted in SQLite
- ZIP path traversal protection (ZipSlip defense)
- ZIP bomb protection (max entry count, max uncompressed size, max compression ratio)
- Corrupt archive & unsupported format reporting
"""

from __future__ import annotations

import hashlib
import io
import os
import posixpath
import uuid
import zipfile
from typing import Any, Dict, List
from backend.compat import APIRouter, File, UploadFile

from backend.db.models import archive_store

router = APIRouter()

SUPPORTED_EXTENSIONS = {
    ".pdf", ".jpg", ".jpeg", ".png", ".tiff", ".tif", ".webp", ".docx", ".xlsx", ".zip"
}

MAX_SINGLE_FILE_BYTES = 50 * 1024 * 1024       # 50 MB per single uploaded file
MAX_ZIP_ENTRIES = 500                          # Max files inside a single ZIP archive
MAX_ZIP_UNCOMPRESSED_BYTES = 200 * 1024 * 1024 # 200 MB total decompressed cap
MAX_ZIP_COMPRESSION_RATIO = 100.0              # Zip bomb ratio threshold


def compute_sha256(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def is_safe_zip_member_path(member_name: str) -> bool:
    """
    Prevents ZipSlip / directory traversal attacks (e.g., '../../etc/passwd' or '/abs/path').
    """
    if not member_name or member_name.startswith("/") or member_name.startswith("\\"):
        return False
    normalized = posixpath.normpath(member_name.replace("\\", "/"))
    if normalized.startswith("..") or "/../" in f"/{normalized}/":
        return False
    if ":" in normalized.split("/")[0]:  # Windows drive letter injection
        return False
    return True


def classify_document_by_filename(filename: str) -> str:
    lower = filename.lower()
    if "cs" in lower or "সিএস" in lower or "সি এস" in lower:
        return "CS_KHATIAN"
    if "sa" in lower or "এসএ" in lower or "এস এ" in lower:
        return "SA_KHATIAN"
    if "rs" in lower or "আরএস" in lower or "আর এস" in lower:
        return "RS_KHATIAN"
    if "bs" in lower or "brs" in lower or "বিএস" in lower or "বিআরএস" in lower:
        return "BS_KHATIAN"
    if "deed" in lower or "dalil" in lower or "দলিল" in lower or "কবলা" in lower:
        return "DEED_DALIL"
    if "mutation" in lower or "namjari" in lower or "নামজারি" in lower or "খারিজ" in lower:
        return "MUTATION_NAMJARI"
    if "map" in lower or "mouza" in lower or "নকশা" in lower:
        return "MOUZA_MAP"
    if "warish" in lower or "ওয়ারিশ" in lower or "ওয়ারিশ" in lower:
        return "WARISH_CERTIFICATE"
    return "UNCLASSIFIED_RECORD"


@router.post("/ingest", summary="Bulk upload land record files or ZIP archives")
async def bulk_ingest_documents(files: List[UploadFile] = File(...)) -> Dict[str, Any]:
    batch_id = str(uuid.uuid4())
    accepted: List[Dict[str, Any]] = []
    skipped_zero_byte: List[Dict[str, Any]] = []
    skipped_duplicates: List[Dict[str, Any]] = []
    rejected_unsupported: List[Dict[str, Any]] = []
    corrupt_or_security_rejected: List[Dict[str, Any]] = []

    def process_single_payload(fname: str, raw_bytes: bytes) -> None:
        ext = os.path.splitext(fname)[1].lower()
        doc_type = classify_document_by_filename(fname)

        if len(raw_bytes) == 0:
            entry = archive_store.record_upload_item(
                batch_id=batch_id,
                filename=fname,
                extension=ext,
                size_bytes=0,
                sha256=None,
                document_type=doc_type,
                status="ZERO_BYTE_SKIPPED",
                reason="ZERO_BYTE_EMPTY_FILE",
            )
            skipped_zero_byte.append(entry)
            return

        if len(raw_bytes) > MAX_SINGLE_FILE_BYTES:
            entry = archive_store.record_upload_item(
                batch_id=batch_id,
                filename=fname,
                extension=ext,
                size_bytes=len(raw_bytes),
                sha256=None,
                document_type=doc_type,
                status="REJECTED_OVERSIZE",
                reason=f"File exceeds {MAX_SINGLE_FILE_BYTES} bytes limit",
            )
            corrupt_or_security_rejected.append(entry)
            return

        if ext not in SUPPORTED_EXTENSIONS:
            entry = archive_store.record_upload_item(
                batch_id=batch_id,
                filename=fname,
                extension=ext,
                size_bytes=len(raw_bytes),
                sha256=None,
                document_type=doc_type,
                status="UNSUPPORTED_FORMAT",
                reason=f"Unsupported extension: {ext}",
            )
            rejected_unsupported.append(entry)
            return

        file_hash = compute_sha256(raw_bytes)
        existing = archive_store.has_seen_sha256(file_hash)
        if existing is not None:
            entry = archive_store.record_upload_item(
                batch_id=batch_id,
                filename=fname,
                extension=ext,
                size_bytes=len(raw_bytes),
                sha256=file_hash,
                document_type=doc_type,
                status="DUPLICATE_SKIPPED",
                reason=f"Duplicate of {existing}",
            )
            skipped_duplicates.append(entry)
            return

        entry = archive_store.record_upload_item(
            batch_id=batch_id,
            filename=fname,
            extension=ext,
            size_bytes=len(raw_bytes),
            sha256=file_hash,
            document_type=doc_type,
            status="QUEUED",
            reason=None,
        )
        accepted.append(entry)

    for upload in files:
        filename = upload.filename or "unnamed"
        ext = os.path.splitext(filename)[1].lower()
        content = await upload.read()

        if len(content) == 0:
            process_single_payload(filename, content)
            continue

        if ext == ".zip":
            try:
                with zipfile.ZipFile(io.BytesIO(content)) as zf:
                    infos = [info for info in zf.infolist() if not info.is_dir()]
                    if len(infos) > MAX_ZIP_ENTRIES:
                        entry = archive_store.record_upload_item(
                            batch_id=batch_id,
                            filename=filename,
                            extension=ext,
                            size_bytes=len(content),
                            sha256=None,
                            document_type="ZIP_ARCHIVE",
                            status="SECURITY_REJECTED",
                            reason=f"ZIP entry count ({len(infos)}) exceeds limit ({MAX_ZIP_ENTRIES})",
                        )
                        corrupt_or_security_rejected.append(entry)
                        continue

                    total_uncompressed = 0
                    for info in infos:
                        if not is_safe_zip_member_path(info.filename):
                            entry = archive_store.record_upload_item(
                                batch_id=batch_id,
                                filename=f"{filename}/{info.filename}",
                                extension=os.path.splitext(info.filename)[1].lower(),
                                size_bytes=info.file_size,
                                sha256=None,
                                document_type="ZIP_MEMBER",
                                status="SECURITY_REJECTED",
                                reason="PATH_TRAVERSAL_DETECTED",
                            )
                            corrupt_or_security_rejected.append(entry)
                            continue

                        total_uncompressed += info.file_size
                        if total_uncompressed > MAX_ZIP_UNCOMPRESSED_BYTES:
                            entry = archive_store.record_upload_item(
                                batch_id=batch_id,
                                filename=f"{filename}/{info.filename}",
                                extension=os.path.splitext(info.filename)[1].lower(),
                                size_bytes=info.file_size,
                                sha256=None,
                                document_type="ZIP_MEMBER",
                                status="SECURITY_REJECTED",
                                reason="ZIP_DECOMPRESSION_SIZE_LIMIT_EXCEEDED",
                            )
                            corrupt_or_security_rejected.append(entry)
                            break

                        compressed_sz = max(1, info.compress_size)
                        ratio = float(info.file_size) / float(compressed_sz)
                        if info.file_size > 1024 * 1024 and ratio > MAX_ZIP_COMPRESSION_RATIO:
                            entry = archive_store.record_upload_item(
                                batch_id=batch_id,
                                filename=f"{filename}/{info.filename}",
                                extension=os.path.splitext(info.filename)[1].lower(),
                                size_bytes=info.file_size,
                                sha256=None,
                                document_type="ZIP_MEMBER",
                                status="SECURITY_REJECTED",
                                reason=f"SUSPECTED_ZIP_BOMB (ratio={ratio:.1f}x)",
                            )
                            corrupt_or_security_rejected.append(entry)
                            continue

                        inner_bytes = zf.read(info.filename)
                        process_single_payload(f"{filename}/{info.filename}", inner_bytes)
            except zipfile.BadZipFile as exc:
                entry = archive_store.record_upload_item(
                    batch_id=batch_id,
                    filename=filename,
                    extension=ext,
                    size_bytes=len(content),
                    sha256=None,
                    document_type="ZIP_ARCHIVE",
                    status="CORRUPT_FILE",
                    reason=f"Corrupt ZIP archive: {exc}",
                )
                corrupt_or_security_rejected.append(entry)
            except Exception as exc:
                entry = archive_store.record_upload_item(
                    batch_id=batch_id,
                    filename=filename,
                    extension=ext,
                    size_bytes=len(content),
                    sha256=None,
                    document_type="ZIP_ARCHIVE",
                    status="PROCESSING_FAILED",
                    reason=f"ZIP extraction failure: {exc}",
                )
                corrupt_or_security_rejected.append(entry)
        else:
            process_single_payload(filename, content)

    return {
        "batch_id": batch_id,
        "summary": {
            "accepted_count": len(accepted),
            "zero_byte_skipped_count": len(skipped_zero_byte),
            "duplicate_skipped_count": len(skipped_duplicates),
            "unsupported_rejected_count": len(rejected_unsupported),
            "corrupt_or_security_rejected_count": len(corrupt_or_security_rejected),
        },
        "accepted_files": accepted,
        "skipped_zero_byte": skipped_zero_byte,
        "skipped_duplicates": skipped_duplicates,
        "rejected_unsupported": rejected_unsupported,
        "corrupt_or_security_rejected": corrupt_or_security_rejected,
    }


@router.get("/queue", summary="Inspect current bulk processing queue")
async def get_upload_queue() -> Dict[str, Any]:
    return archive_store.get_queue_summary()
