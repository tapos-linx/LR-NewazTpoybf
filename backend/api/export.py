"""
Multi-Format Export Router (CSV, JSON, XLSX, PDF, and ZIP Archive).
Enforces:
- Zero fabricated default owners/Dags in ExportPayload
- Filename sanitization against path traversal
- UTF-8 with BOM for CSV so Excel displays Bengali script properly
- Versioned schema for JSON export
- Multi-sheet OpenXML (.xlsx) workbook generation (Metadata, Documents, Survey_Chain,
  Dags, Owners, Heirs, Shares, Discrepancies, Evidence)
- Portable PDF-1.4 report generation
"""

from __future__ import annotations

import csv
import datetime
import io
import json
import re
import zipfile
from typing import Any, Dict, List
from xml.sax.saxutils import escape as xml_escape
from backend.compat import APIRouter, BaseModel, Field, StreamingResponse

router = APIRouter()


def sanitize_export_filename(name: str, default: str = "land_record_export") -> str:
    if not name or not name.strip():
        return default
    cleaned = re.sub(r"[^A-Za-z0-9._\-\u0980-\u09FF]", "_", name.strip())
    cleaned = cleaned.strip("._")
    return cleaned or default


class ExportPayload(BaseModel):
    project_name: str = "Bangladesh_Land_Record_Forensic_Report"
    schema_version: str = "2.0.0"
    division: str = ""
    district: str = ""
    upazila: str = ""
    mouza: str = ""
    jl_no: str = ""
    documents: List[Dict[str, Any]] = Field(default_factory=list)
    survey_chain: List[Dict[str, Any]] = Field(default_factory=list)
    dags: List[Dict[str, Any]] = Field(default_factory=list)
    people: List[Dict[str, Any]] = Field(default_factory=list)
    heirs: List[Dict[str, Any]] = Field(default_factory=list)
    shares: List[Dict[str, Any]] = Field(default_factory=list)
    discrepancies: List[str] = Field(default_factory=list)
    evidence: List[Dict[str, Any]] = Field(default_factory=list)
    geojson: Dict[str, Any] = Field(
        default_factory=lambda: {"type": "FeatureCollection", "features": []}
    )


def _generate_csv_bytes(payload: ExportPayload) -> bytes:
    out = io.StringIO()
    writer = csv.writer(out)
    writer.writerow(
        [
            "Division",
            "District",
            "Upazila",
            "Mouza",
            "JL_No",
            "Dag_No",
            "Khatian_No",
            "Survey_Type",
            "Area_Decimals",
            "Class",
            "Evidence_Tier",
        ]
    )
    for d in payload.dags:
        writer.writerow(
            [
                payload.division,
                payload.district,
                payload.upazila,
                payload.mouza,
                payload.jl_no,
                d.get("dag_no", ""),
                d.get("khatian_no", ""),
                d.get("survey", ""),
                d.get("area_decimals", ""),
                d.get("class", ""),
                d.get("evidence_tier", ""),
            ]
        )
    # Prepend UTF-8 BOM for proper Bengali rendering in Excel
    return "\ufeff".encode("utf-8") + out.getvalue().encode("utf-8")


def _generate_json_bytes(payload: ExportPayload) -> bytes:
    export_obj = {
        "schema_version": payload.schema_version,
        "exported_at_utc": datetime.datetime.now(datetime.timezone.utc).isoformat(),
        "project_name": payload.project_name,
        "jurisdiction": {
            "division": payload.division,
            "district": payload.district,
            "upazila": payload.upazila,
            "mouza": payload.mouza,
            "jl_no": payload.jl_no,
        },
        "documents": payload.documents,
        "survey_chain": payload.survey_chain,
        "dags": payload.dags,
        "people": payload.people,
        "heirs": payload.heirs,
        "shares": payload.shares,
        "discrepancies": payload.discrepancies,
        "evidence": payload.evidence,
        "geojson": payload.geojson,
    }
    return json.dumps(export_obj, ensure_ascii=False, indent=2).encode("utf-8")


def _build_worksheet_xml(headers: List[str], rows: List[List[Any]]) -> str:
    def col_letter(idx: int) -> str:
        result = ""
        n = idx + 1
        while n > 0:
            n, rem = divmod(n - 1, 26)
            result = chr(65 + rem) + result
        return result

    all_rows = [headers] + rows
    row_xml_parts: List[str] = []
    for r_idx, row in enumerate(all_rows, start=1):
        cells: List[str] = []
        for c_idx, val in enumerate(row):
            cell_ref = f"{col_letter(c_idx)}{r_idx}"
            text = xml_escape(str(val if val is not None else ""))
            cells.append(
                f'<c r="{cell_ref}" t="inlineStr"><is><t>{text}</t></is></c>'
            )
        row_xml_parts.append(f'<row r="{r_idx}">{"".join(cells)}</row>')

    return (
        '<?xml version="1.0" encoding="UTF-8" standalone="yes"?>'
        '<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">'
        f'<sheetData>{"".join(row_xml_parts)}</sheetData>'
        "</worksheet>"
    )


def _generate_xlsx_bytes(payload: ExportPayload) -> bytes:
    """
    Generates a standards-compliant OpenXML (.xlsx) workbook containing all 9 forensic sheets:
    Metadata, Documents, Survey_Chain, Dags, Owners, Heirs, Shares, Discrepancies, Evidence.
    """
    sheets: List[tuple[str, List[str], List[List[Any]]]] = [
        (
            "Metadata",
            ["Field", "Value"],
            [
                ["Project Name", payload.project_name],
                ["Schema Version", payload.schema_version],
                ["Division", payload.division],
                ["District", payload.district],
                ["Upazila", payload.upazila],
                ["Mouza", payload.mouza],
                ["JL No", payload.jl_no],
                ["Exported At UTC", datetime.datetime.now(datetime.timezone.utc).isoformat()],
            ],
        ),
        (
            "Documents",
            ["ID", "Filename", "Type", "SHA256", "Status"],
            [
                [
                    d.get("id", ""),
                    d.get("filename", ""),
                    d.get("type", ""),
                    d.get("sha256", ""),
                    d.get("status", ""),
                ]
                for d in payload.documents
            ],
        ),
        (
            "Survey_Chain",
            ["Survey_Type", "Khatian_No", "Dag_No", "Area_Decimals", "Evidence_Tier"],
            [
                [
                    s.get("survey_type", ""),
                    s.get("khatian_no", ""),
                    s.get("dag_no", ""),
                    s.get("area_decimals", ""),
                    s.get("evidence_tier", ""),
                ]
                for s in payload.survey_chain
            ],
        ),
        (
            "Dags",
            ["Dag_No", "Khatian_No", "Survey", "Area_Decimals", "Class", "Evidence_Tier"],
            [
                [
                    d.get("dag_no", ""),
                    d.get("khatian_no", ""),
                    d.get("survey", ""),
                    d.get("area_decimals", ""),
                    d.get("class", ""),
                    d.get("evidence_tier", ""),
                ]
                for d in payload.dags
            ],
        ),
        (
            "Owners",
            ["Name", "Father_Name", "Role", "Share", "Khatian_No"],
            [
                [
                    p.get("name", ""),
                    p.get("father_name", ""),
                    p.get("role", ""),
                    p.get("share", ""),
                    p.get("khatian_no", ""),
                ]
                for p in payload.people
            ],
        ),
        (
            "Heirs",
            ["Name", "Relation", "Share", "Certificate_No"],
            [
                [
                    h.get("name", ""),
                    h.get("relation", ""),
                    h.get("share", ""),
                    h.get("certificate_no", ""),
                ]
                for h in payload.heirs
            ],
        ),
        (
            "Shares",
            ["Holder", "Dag_No", "Fraction", "Allocated_Decimals"],
            [
                [
                    sh.get("holder", ""),
                    sh.get("dag_no", ""),
                    sh.get("fraction", ""),
                    sh.get("allocated_decimals", ""),
                ]
                for sh in payload.shares
            ],
        ),
        (
            "Discrepancies",
            ["Index", "Description"],
            [[idx, desc] for idx, desc in enumerate(payload.discrepancies, start=1)],
        ),
        (
            "Evidence",
            ["Document_ID", "Page", "Field", "Raw_Text", "Confidence", "Status"],
            [
                [
                    ev.get("document_id", ""),
                    ev.get("page", ""),
                    ev.get("field", ""),
                    ev.get("raw_text", ""),
                    ev.get("confidence", ""),
                    ev.get("status", ""),
                ]
                for ev in payload.evidence
            ],
        ),
    ]

    buf = io.BytesIO()
    with zipfile.ZipFile(buf, mode="w", compression=zipfile.ZIP_DEFLATED) as zf:
        override_parts = [
            f'<Override PartName="/xl/worksheets/sheet{i}.xml" '
            'ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>'
            for i in range(1, len(sheets) + 1)
        ]
        content_types_xml = (
            '<?xml version="1.0" encoding="UTF-8" standalone="yes"?>'
            '<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">'
            '<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>'
            '<Default Extension="xml" ContentType="application/xml"/>'
            '<Override PartName="/xl/workbook.xml" '
            'ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>'
            + "".join(override_parts)
            + "</Types>"
        )
        zf.writestr("[Content_Types].xml", content_types_xml)

        rels_xml = (
            '<?xml version="1.0" encoding="UTF-8" standalone="yes"?>'
            '<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">'
            '<Relationship Id="rId1" '
            'Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" '
            'Target="xl/workbook.xml"/>'
            "</Relationships>"
        )
        zf.writestr("_rels/.rels", rels_xml)

        sheet_nodes = [
            f'<sheet name="{xml_escape(name)}" sheetId="{i}" r:id="rId{i}"/>'
            for i, (name, _, _) in enumerate(sheets, start=1)
        ]
        workbook_xml = (
            '<?xml version="1.0" encoding="UTF-8" standalone="yes"?>'
            '<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" '
            'xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">'
            f'<sheets>{"".join(sheet_nodes)}</sheets>'
            "</workbook>"
        )
        zf.writestr("xl/workbook.xml", workbook_xml)

        wb_rels_nodes = [
            f'<Relationship Id="rId{i}" '
            'Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" '
            f'Target="worksheets/sheet{i}.xml"/>'
            for i in range(1, len(sheets) + 1)
        ]
        wb_rels_xml = (
            '<?xml version="1.0" encoding="UTF-8" standalone="yes"?>'
            '<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">'
            + "".join(wb_rels_nodes)
            + "</Relationships>"
        )
        zf.writestr("xl/_rels/workbook.xml.rels", wb_rels_xml)

        for i, (_, headers, rows) in enumerate(sheets, start=1):
            zf.writestr(f"xl/worksheets/sheet{i}.xml", _build_worksheet_xml(headers, rows))

    buf.seek(0)
    return buf.getvalue()


def _generate_pdf_bytes(payload: ExportPayload) -> bytes:
    """
    Generates a valid Portable Document Format (PDF-1.4) report summarizing
    jurisdiction, Dags, owners, and discrepancies.
    """
    lines = [
        f"Project: {payload.project_name} (Schema v{payload.schema_version})",
        f"Jurisdiction: Div={payload.division} | Dist={payload.district} | Upazila={payload.upazila} | Mouza={payload.mouza} | JL={payload.jl_no}",
        f"Total Dags: {len(payload.dags)} | Total People/Owners: {len(payload.people)} | Discrepancies: {len(payload.discrepancies)}",
        "------------------------------------------------------------------------",
    ]
    for d in payload.dags[:30]:
        lines.append(
            f"Dag: {d.get('dag_no', '')} | Khatian: {d.get('khatian_no', '')} | Survey: {d.get('survey', '')} | Area(Dec): {d.get('area_decimals', '')} | Tier: {d.get('evidence_tier', '')}"
        )
    for p in payload.people[:30]:
        lines.append(
            f"Person: {p.get('name', '')} | Role: {p.get('role', '')} | Share: {p.get('share', '')}"
        )
    for disc in payload.discrepancies[:20]:
        lines.append(f"Discrepancy: {disc}")

    def pdf_escape(text: str) -> str:
        ascii_safe = text.encode("ascii", errors="replace").decode("ascii")
        return ascii_safe.replace("\\", "\\\\").replace("(", "\\(").replace(")", "\\)")

    content_ops = ["BT", "/F1 10 Tf", "50 780 Td", "14 TL"]
    for idx, line in enumerate(lines):
        if idx == 0:
            content_ops.append(f"({pdf_escape(line)}) Tj")
        else:
            content_ops.append(f"T* ({pdf_escape(line)}) Tj")
    content_ops.append("ET")
    stream_bytes = "\n".join(content_ops).encode("latin-1")

    objects: List[bytes] = [
        b"1 0 obj\n<< /Type /Catalog /Pages 2 0 R >>\nendobj\n",
        b"2 0 obj\n<< /Type /Pages /Kids [3 0 R] /Count 1 >>\nendobj\n",
        b"3 0 obj\n<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] /Resources << /Font << /F1 4 0 R >> >> /Contents 5 0 R >>\nendobj\n",
        b"4 0 obj\n<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>\nendobj\n",
        f"5 0 obj\n<< /Length {len(stream_bytes)} >>\nstream\n".encode("latin-1")
        + stream_bytes
        + b"\nendstream\nendobj\n",
    ]

    pdf = bytearray(b"%PDF-1.4\n%\xe2\xe3\xcf\xd3\n")
    offsets: List[int] = []
    for obj in objects:
        offsets.append(len(pdf))
        pdf.extend(obj)

    xref_offset = len(pdf)
    pdf.extend(f"xref\n0 {len(objects) + 1}\n".encode("latin-1"))
    pdf.extend(b"0000000000 65535 f \n")
    for off in offsets:
        pdf.extend(f"{off:010d} 00000 n \n".encode("latin-1"))
    pdf.extend(
        f"trailer\n<< /Size {len(objects) + 1} /Root 1 0 R >>\nstartxref\n{xref_offset}\n%%EOF\n".encode(
            "latin-1"
        )
    )
    return bytes(pdf)


@router.post("/csv", summary="Export extracted land records as UTF-8 BOM CSV")
async def export_csv(payload: ExportPayload) -> StreamingResponse:
    safe_name = sanitize_export_filename(payload.project_name)
    csv_bytes = _generate_csv_bytes(payload)
    return StreamingResponse(
        io.BytesIO(csv_bytes),
        media_type="text/csv; charset=utf-8",
        headers={"Content-Disposition": f'attachment; filename="{safe_name}_dags.csv"'},
    )


@router.post("/json", summary="Export extracted land records as versioned JSON")
async def export_json(payload: ExportPayload) -> StreamingResponse:
    safe_name = sanitize_export_filename(payload.project_name)
    json_bytes = _generate_json_bytes(payload)
    return StreamingResponse(
        io.BytesIO(json_bytes),
        media_type="application/json; charset=utf-8",
        headers={"Content-Disposition": f'attachment; filename="{safe_name}.json"'},
    )


@router.post("/xlsx", summary="Export multi-sheet forensic XLSX workbook")
async def export_xlsx(payload: ExportPayload) -> StreamingResponse:
    safe_name = sanitize_export_filename(payload.project_name)
    xlsx_bytes = _generate_xlsx_bytes(payload)
    return StreamingResponse(
        io.BytesIO(xlsx_bytes),
        media_type="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
        headers={"Content-Disposition": f'attachment; filename="{safe_name}.xlsx"'},
    )


@router.post("/pdf", summary="Export formatted PDF forensic report")
async def export_pdf(payload: ExportPayload) -> StreamingResponse:
    safe_name = sanitize_export_filename(payload.project_name)
    pdf_bytes = _generate_pdf_bytes(payload)
    return StreamingResponse(
        io.BytesIO(pdf_bytes),
        media_type="application/pdf",
        headers={"Content-Disposition": f'attachment; filename="{safe_name}.pdf"'},
    )


@router.post("/zip", summary="Export complete forensic archive bundle (.zip)")
async def export_zip_bundle(payload: ExportPayload) -> StreamingResponse:
    safe_name = sanitize_export_filename(payload.project_name)
    zip_buffer = io.BytesIO()
    with zipfile.ZipFile(zip_buffer, "w", zipfile.ZIP_DEFLATED) as zf:
        zf.writestr("records.json", _generate_json_bytes(payload))
        zf.writestr("dags.csv", _generate_csv_bytes(payload))
        zf.writestr("forensic_workbook.xlsx", _generate_xlsx_bytes(payload))
        zf.writestr("forensic_report.pdf", _generate_pdf_bytes(payload))
        zf.writestr(
            "parcels.geojson",
            json.dumps(payload.geojson, ensure_ascii=False, indent=2).encode("utf-8"),
        )

    zip_buffer.seek(0)
    return StreamingResponse(
        zip_buffer,
        media_type="application/zip",
        headers={"Content-Disposition": f'attachment; filename="{safe_name}_Bundle.zip"'},
    )
