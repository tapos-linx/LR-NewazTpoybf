"""
FastAPI Router for Bangla Handwriting Recognition (HCR).
Supports crop transcription, marginalia extraction, and manual verification audits.
"""

from fastapi import APIRouter, UploadFile, File, Form, HTTPException
from typing import Dict, Any, List, Optional
from pydantic import BaseModel
import json
from ai.hcr.recognizer import BanglaHcrRecognizer

router = APIRouter(prefix="/api/v1/hcr", tags=["Bangla HCR"])
hcr_engine = BanglaHcrRecognizer()

# Audit log for manual corrections
_correction_audit_log: List[Dict[str, Any]] = []

class HcrCorrectionPayload(BaseModel):
    document_id: str
    page_number: int
    original_transcription: str
    corrected_transcription: str
    confidence: float
    editor_name: Optional[str] = "Forensic Analyst"
    notes: Optional[str] = None

@router.post("/recognize-crop")
async def recognize_handwritten_crop(
    file: UploadFile = File(...),
    context_hint: Optional[str] = Form("general")
):
    """
    Transcribes a handwritten crop (e.g. signature, marginal note, owner share)
    with token-level confidence scores and low-confidence flags.
    """
    image_bytes = await file.read()
    if not image_bytes:
        raise HTTPException(status_code=400, detail="Empty image data")

    result = hcr_engine.recognize_crop(image_bytes, context_hint=context_hint)
    return result

@router.post("/marginalia")
async def scan_marginalia(file: UploadFile = File(...)):
    """
    Specifically targets marginal notes, amendment scribbles, and office seals.
    """
    image_bytes = await file.read()
    if not image_bytes:
        raise HTTPException(status_code=400, detail="Empty image data")

    marginal_notes = hcr_engine.transcribe_marginal_notes(image_bytes)
    return {
        "filename": file.filename,
        "marginal_notes_count": len(marginal_notes),
        "notes": marginal_notes
    }

@router.post("/correct")
async def submit_manual_correction(payload: HcrCorrectionPayload):
    """
    Saves a manual correction in the audit trail without destroying the original HCR output.
    Zero-Data-Loss guarantee: both original and corrected versions are maintained.
    """
    record = {
        "document_id": payload.document_id,
        "page_number": payload.page_number,
        "original_transcription": payload.original_transcription,
        "corrected_transcription": payload.corrected_transcription,
        "original_confidence": payload.confidence,
        "editor_name": payload.editor_name,
        "notes": payload.notes,
        "status": "MANUALLY_VERIFIED"
    }
    _correction_audit_log.append(record)
    return {
        "status": "success",
        "message": "Manual correction recorded in forensic audit trail.",
        "record": record
    }

@router.get("/audit-log")
async def get_correction_audit_log():
    return {
        "total_corrections": len(_correction_audit_log),
        "audit_trail": _correction_audit_log
    }
