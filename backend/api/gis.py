"""
API Router for Cadastral Map Detection, Target Dag Highlighting, and GeoJSON Export.
Never fabricates parcel grids, sequential Dag numbers, or GPS coordinates.
"""

from __future__ import annotations

import json
from typing import Any, Dict, List, Optional
from backend.compat import APIRouter, File, Form, HTTPException, UploadFile

from ai.gis.map_detector import CadastralMapDetector

router = APIRouter()


@router.post("/detect-parcels", summary="Detect parcels & highlight ONLY matched target Dags")
async def detect_cadastral_parcels(
    file: UploadFile = File(...),
    survey_type: str = Form("RS"),
    target_dags_csv: str = Form("", description="Comma-separated target Dag numbers, e.g., '502,505'"),
    ocr_dag_map_json: Optional[str] = Form(None, description="Optional JSON map of parcel_index -> dag_no"),
    origin_lat: Optional[float] = Form(None),
    origin_lon: Optional[float] = Form(None),
    pixel_to_degree_scale: Optional[float] = Form(None),
) -> Dict[str, Any]:
    raw_bytes = await file.read()
    if len(raw_bytes) == 0:
        raise HTTPException(status_code=400, detail="Zero-byte map file rejected.")

    target_dags: List[str] = [d.strip() for d in target_dags_csv.split(",") if d.strip()]
    ocr_dag_map: Optional[Dict[int, str]] = None
    if ocr_dag_map_json:
        try:
            raw_map = json.loads(ocr_dag_map_json)
            ocr_dag_map = {int(k): str(v) for k, v in raw_map.items()}
        except Exception as exc:
            raise HTTPException(status_code=400, detail=f"Invalid ocr_dag_map_json: {exc}")

    detector = CadastralMapDetector(survey_type=survey_type)
    detection = detector.detect_parcels(
        raw_bytes,
        target_dags=target_dags,
        ocr_dag_map=ocr_dag_map,
    )
    geojson = detector.export_geojson(
        detection,
        origin_lat=origin_lat,
        origin_lon=origin_lon,
        pixel_to_degree_scale=pixel_to_degree_scale,
    )

    return {
        "filename": file.filename,
        "detection_summary": {
            "status": detection["status"],
            "survey_type": detection["survey_type"],
            "coordinate_system": detection["coordinate_system"],
            "is_georeferenced": detection["is_georeferenced"],
            "total_parcels_detected": detection["total_parcels_detected"],
            "highlighted_target_count": detection["highlighted_target_count"],
            "requested_target_dags": target_dags,
            "highlighting_rule": detection["highlighting_rule"],
            "requires_manual_review": detection["requires_manual_review"],
        },
        "detection": detection,
        "geojson": geojson,
    }
