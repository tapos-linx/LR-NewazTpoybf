"""
FastAPI Backend Entrypoint for the Bangladesh Land Record Extraction & Forensic Research Platform.
Enforces:
- Strict CORS origin allowlist (configurable via ALLOWED_ORIGINS env var; never wildcard '*' with credentials)
- Optional API key authentication middleware (configurable via FORENSIC_API_KEY env var)
- Request body size limits
- Honest, verifiable /health readiness diagnostics
"""

from __future__ import annotations

import os
import shutil
from typing import Any, Callable, Dict

try:
    from fastapi import FastAPI, Request
    from fastapi.middleware.cors import CORSMiddleware
    from fastapi.responses import JSONResponse
except ImportError:
    from backend.compat import JSONResponse  # type: ignore

    class Request:  # type: ignore
        headers: Dict[str, str] = {}
        url: Any = None

    class CORSMiddleware:  # type: ignore
        pass

    class FastAPI:  # type: ignore
        def __init__(self, **kwargs: Any):
            self.kwargs = kwargs
            self.routers = []

        def add_middleware(self, *args: Any, **kwargs: Any) -> None:
            pass

        def middleware(self, *args: Any, **kwargs: Any) -> Callable[[Any], Any]:
            def decorator(fn: Any) -> Any:
                return fn
            return decorator

        def include_router(self, router: Any, **kwargs: Any) -> None:
            self.routers.append((router, kwargs))

        def get(self, *args: Any, **kwargs: Any) -> Callable[[Any], Any]:
            def decorator(fn: Any) -> Any:
                return fn
            return decorator

from ai.hcr.recognizer import HAS_CV2, HAS_PYTESSERACT
from backend.api import bulk_upload, export, gis, hcr, matcher
from backend.db.models import archive_store

ALLOWED_ORIGINS = [
    origin.strip()
    for origin in os.environ.get(
        "ALLOWED_ORIGINS",
        "http://localhost:3000,http://localhost:8000,http://127.0.0.1:8000",
    ).split(",")
    if origin.strip()
]

MAX_REQUEST_BODY_BYTES = int(os.environ.get("MAX_REQUEST_BODY_BYTES", str(60 * 1024 * 1024)))

app = FastAPI(
    title="Bangladesh Land Record Forensic AI Engine",
    description=(
        "Production-grade OCR/HCR, Historical Survey Chain (CS->SA->RS->BS), "
        "Warish Inheritance Hissa Validator, Cadastral GIS API, and Desktop Companion Upload Service"
    ),
    version="2.2.0",
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=ALLOWED_ORIGINS,
    allow_credentials=False,
    allow_methods=["GET", "POST", "OPTIONS"],
    allow_headers=["Authorization", "Content-Type", "X-API-Key", "X-Pairing-Token"],
)


def verify_api_key_if_configured(headers: Dict[str, str], path: str = "") -> tuple[bool, int, str]:
    """
    Pure helper for API key verification used by both FastAPI HTTP middleware
    and direct unit tests.
    """
    configured_key = os.environ.get("FORENSIC_API_KEY", "").strip()
    if not configured_key or path in ("/health", "/docs", "/openapi.json", "/api/v1/upload/companion"):
        return True, 200, "OK"

    lower_headers = {k.lower(): v for k, v in headers.items()}
    supplied_key = (lower_headers.get("x-api-key") or "").strip()
    auth_header = (lower_headers.get("authorization") or "").strip()
    if auth_header.startswith("Bearer "):
        supplied_key = auth_header.removeprefix("Bearer ").strip()

    if supplied_key != configured_key:
        return False, 401, "Unauthorized: valid X-API-Key or Bearer token required."
    return True, 200, "OK"


@app.middleware("http")
async def security_and_size_middleware(request: Request, call_next):
    # 1. Enforce bounded request size from Content-Length header if present
    content_length = request.headers.get("content-length")
    if content_length and content_length.isdigit():
        if int(content_length) > MAX_REQUEST_BODY_BYTES:
            return JSONResponse(
                status_code=413,
                content={"detail": f"Request payload exceeds maximum limit of {MAX_REQUEST_BODY_BYTES} bytes."},
            )

    # 2. Enforce API key when FORENSIC_API_KEY is configured in environment
    authorized, status_code, detail = verify_api_key_if_configured(dict(request.headers), request.url.path)
    if not authorized:
        return JSONResponse(
            status_code=status_code,
            content={"detail": detail},
        )

    return await call_next(request)


app.include_router(bulk_upload.router, prefix="/api/v1/upload", tags=["1. Bulk Ingestion & Folder Scanner"])
app.include_router(hcr.router, prefix="/api/v1/hcr", tags=["2. Bengali Handwritten Recognition (HCR)"])
app.include_router(gis.router, prefix="/api/v1/gis", tags=["3. Cadastral GIS & Mouza Map Detector"])
app.include_router(matcher.router, prefix="/api/v1/matcher", tags=["4. Warish & Survey Chain Reconciliation"])
app.include_router(export.router, prefix="/api/v1/export", tags=["5. Multi-Format Export (CSV/JSON/XLSX/PDF/ZIP)"])


@app.get("/health", tags=["System Health"])
async def health_check() -> Dict[str, Any]:
    tesseract_bin = shutil.which("tesseract")
    queue_summary = archive_store.get_queue_summary()
    return {
        "status": "healthy",
        "version": "2.2.0",
        "capabilities": {
            "opencv_available": HAS_CV2,
            "pytesseract_available": HAS_PYTESSERACT,
            "tesseract_binary_installed": tesseract_bin is not None,
            "sqlite_archive_connected": os.path.exists(archive_store.db_path),
            "persistent_upload_storage_ready": os.path.isdir(archive_store.storage_dir),
            "queued_documents_count": queue_summary["total_queued_documents"],
        },
    }
