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
from typing import Any, Dict
from fastapi import FastAPI, Request
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse

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
        "Warish Inheritance Hissa Validator, and Cadastral GIS API"
    ),
    version="2.2.0",
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=ALLOWED_ORIGINS,
    allow_credentials=False,
    allow_methods=["GET", "POST", "OPTIONS"],
    allow_headers=["Authorization", "Content-Type", "X-API-Key"],
)


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
    configured_key = os.environ.get("FORENSIC_API_KEY", "").strip()
    if configured_key and request.url.path not in ("/health", "/docs", "/openapi.json"):
        supplied_key = request.headers.get("X-API-Key") or ""
        auth_header = request.headers.get("Authorization") or ""
        if auth_header.startswith("Bearer "):
            supplied_key = auth_header.removeprefix("Bearer ").strip()
        if supplied_key != configured_key:
            return JSONResponse(
                status_code=401,
                content={"detail": "Unauthorized: valid X-API-Key or Bearer token required."},
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
            "queued_documents_count": queue_summary["total_queued_documents"],
        },
    }
