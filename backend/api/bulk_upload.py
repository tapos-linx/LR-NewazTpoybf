"""
Bulk Ingestion, Persistent Storage, Desktop Companion Pairing & Folder/ZIP Upload Router.
Supports PDF, JPG, JPEG, PNG, TIFF, TIF, WEBP, ZIP, CSV, XLSX, DOCX, TXT, and JSON.
Enforces:
- Streaming writes to persistent disk storage (never discarded after HTTP response)
- Safe filename normalization
- MIME & extension validation
- Zero-byte file detection and rejection
- SHA-256 content deduplication persisted in SQLite
- ZIP path traversal protection (ZipSlip defense)
- ZIP bomb protection (max entry count, max uncompressed size, max compression ratio)
- Desktop/Web Companion short-lived pairing tokens & responsive multi-file upload UI
- Upload status and verified file retrieval endpoints
"""

from __future__ import annotations

import hashlib
import io
import mimetypes
import os
import posixpath
import re
import uuid
import zipfile
from typing import Any, Dict, List, Optional

from backend.compat import (
    APIRouter,
    File,
    HTMLResponse,
    HTTPException,
    StreamingResponse,
    UploadFile,
)
from backend.db.models import archive_store

router = APIRouter()

SUPPORTED_EXTENSIONS = {
    ".pdf", ".jpg", ".jpeg", ".png", ".tiff", ".tif", ".webp",
    ".docx", ".xlsx", ".zip", ".csv", ".txt", ".json"
}

ALLOWED_MIME_PREFIXES = (
    "application/pdf",
    "image/",
    "application/zip",
    "application/x-zip",
    "application/octet-stream",
    "text/csv",
    "text/plain",
    "application/json",
    "application/vnd.openxmlformats-officedocument",
)

MAX_SINGLE_FILE_BYTES = 50 * 1024 * 1024       # 50 MB per single uploaded file
MAX_ZIP_ENTRIES = 500                          # Max files inside a single ZIP archive
MAX_ZIP_UNCOMPRESSED_BYTES = 200 * 1024 * 1024 # 200 MB total decompressed cap
MAX_ZIP_COMPRESSION_RATIO = 100.0              # Zip bomb ratio threshold
STREAM_CHUNK_BYTES = 65536                     # 64 KB streaming chunk size


def compute_sha256(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def normalize_safe_filename(filename: str) -> str:
    """
    Normalizes an arbitrary client filename into a safe storage filename while
    preserving Bengali Unicode characters, alphanumeric characters, dots, and hyphens.
    """
    base = os.path.basename(filename.replace("\\", "/")).strip()
    if not base:
        return "document.bin"
    safe = re.sub(r"[^a-zA-Z0-9._\-\u0980-\u09FF]", "_", base)
    return safe[:180] or "document.bin"


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


def is_allowed_mime_type(content_type: Optional[str], ext: str) -> bool:
    if ext not in SUPPORTED_EXTENSIONS:
        return False
    if not content_type:
        return True
    lower_ct = content_type.lower().strip()
    if any(lower_ct.startswith(prefix) for prefix in ALLOWED_MIME_PREFIXES):
        return True
    return False


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


def _persist_bytes_streaming(doc_id: str, safe_name: str, data: bytes) -> str:
    doc_dir = os.path.join(archive_store.storage_dir, doc_id)
    os.makedirs(doc_dir, exist_ok=True)
    target_path = os.path.join(doc_dir, safe_name)
    with open(target_path, "wb") as f:
        offset = 0
        total = len(data)
        while offset < total:
            chunk = data[offset:offset + STREAM_CHUNK_BYTES]
            f.write(chunk)
            offset += len(chunk)
    return target_path


async def _read_upload_streaming(upload: UploadFile) -> tuple[bytes, bool]:
    """
    Reads an UploadFile in bounded chunks so oversized payloads are caught without
    unbounded memory allocation.
    Returns (bytes_data, exceeded_limit).
    """
    chunks: List[bytes] = []
    total = 0
    while True:
        try:
            chunk = await upload.read(STREAM_CHUNK_BYTES)
        except TypeError:
            chunk = await upload.read()
        if not chunk:
            break
        total += len(chunk)
        if total > MAX_SINGLE_FILE_BYTES:
            return b"", True
        chunks.append(chunk)
        if len(chunk) < STREAM_CHUNK_BYTES:
            # If UploadFile shim returned everything in one call, next read() will be empty
            pass
    return b"".join(chunks), False


@router.post("/ingest", summary="Bulk upload land record files or ZIP archives")
async def bulk_ingest_documents(
    files: List[UploadFile] = File(...),
    pairing_token: Optional[str] = None,
) -> Dict[str, Any]:
    verified_pairing: Optional[str] = None
    if pairing_token:
        verified_pairing = archive_store.verify_pairing_token(pairing_token)
        if verified_pairing is None:
            raise HTTPException(status_code=401, detail="Invalid or expired desktop pairing token.")

    batch_id = str(uuid.uuid4())
    accepted: List[Dict[str, Any]] = []
    skipped_zero_byte: List[Dict[str, Any]] = []
    skipped_duplicates: List[Dict[str, Any]] = []
    rejected_unsupported: List[Dict[str, Any]] = []
    corrupt_or_security_rejected: List[Dict[str, Any]] = []

    def process_single_payload(
        fname: str,
        raw_bytes: bytes,
        supplied_mime: Optional[str] = None,
        oversize_flag: bool = False,
    ) -> None:
        ext = os.path.splitext(fname)[1].lower()
        doc_type = classify_document_by_filename(fname)
        safe_name = normalize_safe_filename(fname)
        guessed_mime = supplied_mime or mimetypes.guess_type(safe_name)[0] or "application/octet-stream"

        if oversize_flag or len(raw_bytes) > MAX_SINGLE_FILE_BYTES:
            entry = archive_store.record_upload_item(
                batch_id=batch_id,
                filename=fname,
                extension=ext,
                size_bytes=len(raw_bytes) if not oversize_flag else MAX_SINGLE_FILE_BYTES + 1,
                sha256=None,
                document_type=doc_type,
                status="REJECTED_OVERSIZE",
                reason=f"File exceeds {MAX_SINGLE_FILE_BYTES} bytes limit",
                mime_type=guessed_mime,
                pairing_token=verified_pairing,
            )
            corrupt_or_security_rejected.append(entry)
            return

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
                mime_type=guessed_mime,
                pairing_token=verified_pairing,
            )
            skipped_zero_byte.append(entry)
            return

        if ext not in SUPPORTED_EXTENSIONS or not is_allowed_mime_type(supplied_mime, ext):
            entry = archive_store.record_upload_item(
                batch_id=batch_id,
                filename=fname,
                extension=ext,
                size_bytes=len(raw_bytes),
                sha256=None,
                document_type=doc_type,
                status="UNSUPPORTED_FORMAT",
                reason=f"Unsupported extension or MIME type: {ext} ({guessed_mime})",
                mime_type=guessed_mime,
                pairing_token=verified_pairing,
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
                mime_type=guessed_mime,
                pairing_token=verified_pairing,
            )
            skipped_duplicates.append(entry)
            return

        doc_id = str(uuid.uuid4())
        stored_path = _persist_bytes_streaming(doc_id, safe_name, raw_bytes)

        entry = archive_store.record_upload_item(
            item_id=doc_id,
            batch_id=batch_id,
            filename=fname,
            extension=ext,
            size_bytes=len(raw_bytes),
            sha256=file_hash,
            document_type=doc_type,
            status="QUEUED",
            reason=None,
            stored_path=stored_path,
            mime_type=guessed_mime,
            pairing_token=verified_pairing,
        )
        accepted.append(entry)

    for upload in files:
        filename = upload.filename or "unnamed"
        ext = os.path.splitext(filename)[1].lower()
        supplied_mime = getattr(upload, "content_type", None)
        content, exceeded = await _read_upload_streaming(upload)

        if exceeded:
            process_single_payload(filename, b"", supplied_mime=supplied_mime, oversize_flag=True)
            continue

        if len(content) == 0:
            process_single_payload(filename, content, supplied_mime=supplied_mime)
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
            process_single_payload(filename, content, supplied_mime=supplied_mime)

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


@router.get("/status/{document_id}", summary="Get persistent upload metadata and processing status")
async def get_upload_status(document_id: str) -> Dict[str, Any]:
    record = archive_store.get_upload_item_by_id(document_id)
    if not record:
        raise HTTPException(status_code=404, detail=f"Document '{document_id}' not found.")
    stored_path = record.get("stored_path")
    file_exists = bool(stored_path and os.path.isfile(stored_path))
    return {
        **record,
        "file_persisted_on_disk": file_exists,
    }


@router.get("/files/{document_id}/download", summary="Download or retrieve persisted uploaded document bytes")
async def download_uploaded_document(document_id: str) -> StreamingResponse:
    record = archive_store.get_upload_item_by_id(document_id)
    if not record:
        raise HTTPException(status_code=404, detail=f"Document '{document_id}' not found.")
    stored_path = record.get("stored_path")
    if not stored_path or not os.path.isfile(stored_path):
        raise HTTPException(status_code=404, detail=f"Persisted file for '{document_id}' is missing on disk.")

    with open(stored_path, "rb") as f:
        payload = f.read()

    safe_name = normalize_safe_filename(record.get("filename", f"{document_id}.bin"))
    media_type = record.get("mime_type") or "application/octet-stream"
    headers = {
        "Content-Disposition": f'attachment; filename="{safe_name}"',
        "X-Content-SHA256": record.get("sha256") or "",
        "Content-Length": str(len(payload)),
    }
    return StreamingResponse(io.BytesIO(payload), media_type=media_type, headers=headers)


@router.post("/pair", summary="Create a short-lived pairing token for Desktop/Web Companion upload")
async def create_desktop_pairing_session() -> Dict[str, Any]:
    return archive_store.create_pairing_session(ttl_seconds=900)


@router.get("/companion/session/{token}", summary="List uploaded files authorized under a Desktop Companion pairing token")
async def get_companion_session_files(token: str) -> Dict[str, Any]:
    verified = archive_store.verify_pairing_token(token)
    if not verified:
        raise HTTPException(status_code=401, detail="Invalid or expired pairing token.")
    files = archive_store.get_uploads_by_pairing_token(verified)
    return {
        "pairing_token": verified,
        "total_files": len(files),
        "files": files,
    }


@router.get("/companion", summary="Responsive Desktop/Web Companion multi-file & folder upload interface")
async def serve_desktop_companion_ui(token: Optional[str] = None) -> HTMLResponse:
    safe_token = re.sub(r"[^a-zA-Z0-9_\-]", "", token or "")
    html = f"""<!DOCTYPE html>
<html lang="bn">
<head>
  <meta charset="utf-8" />
  <meta name="viewport" content="width=device-width, initial-scale=1" />
  <title>LR-NewazTpoybf — Desktop / Web Companion Bulk Upload</title>
  <style>
    body {{ font-family: system-ui, -apple-system, sans-serif; background: #0f172a; color: #f8fafc; margin: 0; padding: 24px; }}
    .card {{ max-width: 760px; margin: 0 auto; background: #1e293b; border-radius: 14px; padding: 24px; border: 1px solid #334155; }}
    h1 {{ font-size: 20px; margin-top: 0; color: #38bdf8; }}
    .dropzone {{ border: 2px dashed #38bdf8; border-radius: 12px; padding: 28px; text-align: center; margin: 16px 0; background: #0f172a; }}
    input[type="text"] {{ width: 100%; padding: 10px; border-radius: 8px; border: 1px solid #475569; background: #0f172a; color: #fff; margin-bottom: 12px; box-sizing: border-box; }}
    button {{ background: #0284c7; color: #fff; border: none; padding: 12px 20px; border-radius: 8px; font-weight: bold; cursor: pointer; }}
    progress {{ width: 100%; height: 18px; margin-top: 12px; }}
    pre {{ background: #0f172a; padding: 12px; border-radius: 8px; overflow-x: auto; font-size: 12px; }}
  </style>
</head>
<body>
  <div class="card">
    <h1>বাংলাদেশ ভূমি রেকর্ড এক্সট্রাক্টর — পিসি হার্ডডিস্ক কম্প্যানিয়ন আপলোড</h1>
    <p>আপনার কম্পিউটারের হার্ডডিস্ক থেকে এক বা একাধিক PDF, ছবি, বা সম্পূর্ণ ফোল্ডার (.ZIP / সাবফোল্ডার) নির্বাচন করে অ্যান্ড্রয়েড অ্যাপে পাঠান।</p>
    <label>পেয়ারিং টোকেন বা ৬-সংখ্যার কোড:</label>
    <input id="tokenInput" type="text" value="{safe_token}" placeholder="অ্যান্ড্রয়েড অ্যাপে প্রাপ্ত পেয়ারিং কোড দিন" />
    <div class="dropzone" id="dropzone">
      <p><strong>এখানে ফাইল বা ফোল্ডার ড্র্যাগ অ্যান্ড ড্রপ করুন</strong></p>
      <p>সমর্থিত ফরম্যাট: PDF, JPG, PNG, TIFF, WEBP, ZIP, CSV, XLSX, DOCX, TXT, JSON</p>
      <input id="fileInput" type="file" multiple />
      <input id="folderInput" type="file" webkitdirectory directory multiple />
    </div>
    <button onclick="uploadSelected()">নির্বাচিত ফাইল আপলোড করুন</button>
    <progress id="progressBar" value="0" max="100"></progress>
    <pre id="statusOutput">প্রস্তুত। কোনো ফাইল এখনো আপলোড হয়নি।</pre>
  </div>
  <script>
    function uploadSelected() {{
      const token = document.getElementById('tokenInput').value.trim();
      const filesA = document.getElementById('fileInput').files;
      const filesB = document.getElementById('folderInput').files;
      const formData = new FormData();
      for (const f of filesA) formData.append('files', f, f.webkitRelativePath || f.name);
      for (const f of filesB) formData.append('files', f, f.webkitRelativePath || f.name);
      const xhr = new XMLHttpRequest();
      const url = '/api/v1/upload/ingest' + (token ? ('?pairing_token=' + encodeURIComponent(token)) : '');
      xhr.open('POST', url, true);
      xhr.upload.onprogress = function(e) {{
        if (e.lengthComputable) {{
          document.getElementById('progressBar').value = Math.round((e.loaded / e.total) * 100);
        }}
      }};
      xhr.onload = function() {{
        document.getElementById('statusOutput').textContent = xhr.responseText;
      }};
      xhr.send(formData);
    }}
  </script>
</body>
</html>"""
    return HTMLResponse(content=html, status_code=200)
