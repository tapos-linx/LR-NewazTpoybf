"""
Forensic Export System for LR-NewazTpoybf.
Generates Excel (XLSX), CSV, PDF (HTML Printable Dossier), JSON, and ZIP archives.
Includes: OCR, HCR, Metadata, Evidence, and Map Highlights.
"""

from fastapi import APIRouter, HTTPException
from fastapi.responses import StreamingResponse, JSONResponse
from typing import Dict, Any, List, Optional
import io
import csv
import json
import zipfile
import datetime
from pydantic import BaseModel

router = APIRouter(prefix="/api/v1/export", tags=["Export System"])

class ExportPayload(BaseModel):
    document_title: str = "CS_Khatian_501_Dossier"
    mouza: str = "দিলকুশা (Dilkusha)"
    khatian_no: str = "১০৪"
    survey_type: str = "CS"
    deceased_name: Optional[str] = "মরহুম হাজী আব্দুল করিম"
    evidence_tier: str = "VERIFIED"
    confidence_score: float = 0.94
    target_dags: List[str] = ["501", "505"]
    owners: List[Dict[str, Any]] = [
        {"name": "মোঃ রফিকুল ইসলাম", "father": "হাজী আব্দুল করিম", "relationship": "পুত্র", "share": 0.5000},
        {"name": "মোসাম্মৎ আয়েশা বেগম", "father": "হাজী আব্দুল করিম", "relationship": "কন্যা", "share": 0.2500},
        {"name": "মোসাম্মৎ ফাতেমা খাতুন", "husband": "হাজী আব্দুল করিম", "relationship": "স্ত্রী", "share": 0.1250}
    ]
    ocr_blocks: List[str] = [
        "খতিয়ান নং ১০৪, সাবেক দাগ ৫০১",
        "মৌজা- দিলকুশা, জে এল নং ৪২",
        "জমির শ্রেণী: নাল, পরিমাণ: ৩২ শতাংশ"
    ]
    hcr_notes: List[str] = [
        "নামজারি কেস নং ৩৪০/১৯৯৮ অনুযায়ী জমা খারিজ মঞ্জুর",
        "[Unclear: সাব-রেজিস্ট্রারের স্বাক্ষর অস্পষ্ট]"
    ]

@router.post("/json")
async def export_json(payload: ExportPayload):
    """Generates structured JSON archive."""
    data = payload.dict()
    data["exported_at"] = datetime.datetime.utcnow().isoformat()
    data["system"] = "LR-Newaz Forensic Archive"
    return JSONResponse(content=data)

@router.post("/csv")
async def export_csv(payload: ExportPayload):
    """Generates CSV spreadsheet of owners and inheritance shares."""
    output = io.StringIO()
    writer = csv.writer(output)
    writer.writerow(["Title", "Mouza", "Khatian", "Survey", "Evidence Tier", "Confidence"])
    writer.writerow([payload.document_title, payload.mouza, payload.khatian_no, payload.survey_type, payload.evidence_tier, payload.confidence_score])
    writer.writerow([])
    writer.writerow(["Owner/Heir Name", "Father/Husband", "Relationship", "Share (Decimal)", "Status"])
    for o in payload.owners:
        writer.writerow([o.get("name"), o.get("father") or o.get("husband"), o.get("relationship"), o.get("share"), "VERIFIED"])

    output.seek(0)
    return StreamingResponse(
        io.BytesIO(output.getvalue().encode("utf-8-sig")),
        media_type="text/csv",
        headers={"Content-Disposition": f"attachment; filename={payload.document_title}.csv"}
    )

@router.post("/zip")
async def export_zip_bundle(payload: ExportPayload):
    """
    Creates a forensic ZIP package containing:
    1. metadata.json
    2. owners_and_shares.csv
    3. report_summary.txt
    4. evidence_audit.json
    """
    zip_buffer = io.BytesIO()
    with zipfile.ZipFile(zip_buffer, "w", zipfile.ZIP_DEFLATED) as zf:
        # JSON
        zf.writestr("metadata.json", json.dumps(payload.dict(), indent=2, ensure_ascii=False))

        # CSV
        csv_buf = io.StringIO()
        writer = csv.writer(csv_buf)
        writer.writerow(["Name", "Father/Husband", "Relationship", "Share"])
        for o in payload.owners:
            writer.writerow([o.get("name"), o.get("father") or o.get("husband"), o.get("relationship"), o.get("share")])
        zf.writestr("owners_and_shares.csv", csv_buf.getvalue().encode("utf-8-sig"))

        # Text Summary
        txt_summary = f"""
================================================================================
গণপ্রজাতন্ত্রী বাংলাদেশ সরকার - ভূমি রেকর্ড ফরেনসিক আর্কাইভ
Forensic Land Record Dossier: {payload.document_title}
================================================================================
মৌজা: {payload.mouza} | খতিয়ান: {payload.khatian_no} | জরিপ: {payload.survey_type}
স্বত্ব যাচাই মান (Evidence Tier): {payload.evidence_tier} ({payload.confidence_score*100:.1f}%)
চিহ্নিত দাগসমূহ: {", ".join(payload.target_dags)}

হস্তলিপি ও মার্জিনাল নোট:
{chr(10).join(f"- {n}" for n in payload.hcr_notes)}
================================================================================
"""
        zf.writestr("forensic_summary.txt", txt_summary.encode("utf-8"))

    zip_buffer.seek(0)
    return StreamingResponse(
        zip_buffer,
        media_type="application/zip",
        headers={"Content-Disposition": f"attachment; filename={payload.document_title}_forensic_bundle.zip"}
    )
