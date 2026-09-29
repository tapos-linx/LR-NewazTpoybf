"""
FastAPI Router for Cadastral Survey Map Intelligence (GIS).
Enforces zero-data-loss and strictly highlights ONLY inheritance-matched plots.
"""

from fastapi import APIRouter, UploadFile, File, Form, HTTPException
from typing import Dict, Any, List, Optional
from pydantic import BaseModel
import json
from ai.gis.map_detector import CadastralMapDetector

router = APIRouter(prefix="/api/v1/gis", tags=["Cadastral Map GIS"])
detector = CadastralMapDetector()

class HighlightRequest(BaseModel):
    target_dags: List[str]
    survey_type: Optional[str] = "CS"
    mouza_name: Optional[str] = None
    khatian_no: Optional[str] = None

@router.post("/detect-map")
async def detect_cadastral_map(
    file: UploadFile = File(...),
    survey_type: Optional[str] = Form("CS")
):
    """
    Detects parcels, plot numbers, road networks, water bodies, sheet number, and north arrow.
    """
    image_bytes = await file.read()
    if not image_bytes:
        raise HTTPException(status_code=400, detail="Empty map file")

    det = CadastralMapDetector(survey_type=survey_type)
    result = det.detect_parcels(image_bytes)
    return result

@router.post("/highlight-inheritance")
async def highlight_inheritance_plots(
    file: UploadFile = File(...),
    target_dags: str = Form("501, 505") # Comma-separated Dag numbers
):
    """
    STRICT FORENSIC RULE:
    Highlights ONLY the lands matched from the inheritance certificate.
    Does NOT highlight all parcels.
    """
    image_bytes = await file.read()
    if not image_bytes:
        raise HTTPException(status_code=400, detail="Empty map file")

    dags_list = [d.strip() for d in target_dags.split(",") if d.strip()]
    det = CadastralMapDetector(survey_type="CS")
    result = det.highlight_target_dags(image_bytes, dags_list)
    return result

@router.get("/surveys/supported")
async def get_supported_surveys():
    return {
        "supported_cadastral_surveys": [
            {"code": "CS", "name": "Cadastral Survey (1888-1940)", "scale": "16 inch = 1 mile"},
            {"code": "SA", "name": "State Acquisition Survey (1956-1962)", "scale": "16 inch = 1 mile"},
            {"code": "RS", "name": "Revisional Survey (1965-1990)", "scale": "16 or 32 inch = 1 mile"},
            {"code": "BS_BRS", "name": "Bangladesh Survey / City Survey (1998-Present)", "scale": "32 or 64 inch = 1 mile"}
        ]
    }
