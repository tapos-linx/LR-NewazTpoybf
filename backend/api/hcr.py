"""
API Router for Bengali Handwritten Character Recognition (HCR) & Manual Correction Audit Trail.
Persists all manual corrections and audit events in SQLite via `archive_store`.
"""

from __future__ import annotations

from typing import Any, Dict, Optional
from backend.compat import APIRouter, BaseModel, Field, File, Form, HTTPException, UploadFile

from ai.hcr.recognizer import BanglaHandwritingRecognizer
from backend.db.models import archive_store

router = APIRouter()
recognizer = BanglaHandwritingRecognizer(confidence_threshold=0.70)


class ManualCorrectionPayload(BaseModel):
    document_id: str = Field(..., description="Target document UUID")
    page_number: int = Field(1, ge=1)
    field_name: str = Field(..., description="Field being corrected, e.g., owner_name, dag_no, marginal_note")
    original_ocr_text: str = Field(..., description="Raw HCR output before human correction")
    corrected_text: str = Field(..., description="Human-verified Bengali transcription")
    reviewer_id: str = Field("legal_researcher_01", description="User ID performing verification")
    confidence_before: float = Field(0.55, ge=0.0, le=1.0)
    notes: Optional[str] = Field(None, description="Evidence or physical ledger inspection remark")


@router.post("/recognize", summary="Run Bengali HCR on uploaded document image")
async def recognize_bengali_handwriting(
    file: UploadFile = File(...),
    region_hint: str = Form("full_page"),
    raw_text_override: Optional[str] = Form(None),
) -> Dict[str, Any]:
    raw_bytes = await file.read()
    if len(raw_bytes) == 0 and raw_text_override is None:
        raise HTTPException(status_code=400, detail="Zero-byte file uploaded. Cannot run HCR.")

    hcr_result = recognizer.recognize_handwriting(
        raw_bytes,
        region_hint=region_hint,
        raw_text_override=raw_text_override,
    )
    marginalia = recognizer.transcribe_marginal_notes(raw_bytes) if len(raw_bytes) > 0 else {
        "region": "marginal_notes",
        "status": hcr_result.get("status", "NEEDS_MANUAL_REVIEW"),
        "transcription": "",
        "confidence": 0.0,
        "requires_manual_review": True,
        "tokens": [],
    }

    return {
        "filename": file.filename,
        "byte_size": len(raw_bytes),
        "hcr_analysis": hcr_result,
        "marginal_notes": marginalia,
    }


@router.post("/correct", summary="Submit human correction with immutable audit trail")
async def submit_manual_correction(payload: ManualCorrectionPayload) -> Dict[str, Any]:
    audit_entry = archive_store.record_hcr_correction(
        document_id=payload.document_id,
        page_number=payload.page_number,
        field_name=payload.field_name,
        original_ocr_text=payload.original_ocr_text,
        corrected_text=payload.corrected_text,
        reviewer_id=payload.reviewer_id,
        confidence_before=payload.confidence_before,
        notes=payload.notes,
    )
    return {
        "status": "CORRECTION_PERSISTED",
        "zero_data_loss_preserved": True,
        "audit_record": audit_entry,
    }


@router.get("/corrections/{document_id}", summary="Get immutable revision history for a document")
async def get_correction_history(document_id: str) -> Dict[str, Any]:
    history = archive_store.get_hcr_corrections(document_id)
    return {
        "document_id": document_id,
        "total_revisions": len(history),
        "revisions": history,
    }
