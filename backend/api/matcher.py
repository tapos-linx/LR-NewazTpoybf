"""
API Router for Warish (Inheritance) Certificate & Historical Survey Chain Reconciliation.
Never invents fallback survey records when none are provided in the request.
"""

from __future__ import annotations

from typing import Any, Dict, List, Optional
from backend.compat import APIRouter, BaseModel, Field

from ai.matcher.inheritance_matcher import InheritancePlotMatcher

router = APIRouter()
matcher = InheritancePlotMatcher()


class HeirEntry(BaseModel):
    name: str
    relation: str
    share: float = Field(..., ge=0.0, le=1.0)
    nid: Optional[str] = None


class WarishCertificateInput(BaseModel):
    certificate_no: str
    deceased_name: str
    father_name: Optional[str] = None
    mouza: Optional[str] = None
    heirs: List[HeirEntry]


class SurveyOwnerInput(BaseModel):
    name: str
    father_name: Optional[str] = None
    share: float = Field(..., ge=0.0, le=1.0)


class SurveyRecordInput(BaseModel):
    survey_type: str = Field(..., description="CS, SA, RS, BS, or MUTATION")
    khatian_no: str
    dag_no: str
    mouza: Optional[str] = None
    total_area_decimals: Optional[float] = Field(None, ge=0.0)
    owners: List[SurveyOwnerInput]


class ReconciliationRequest(BaseModel):
    warish_certificate: WarishCertificateInput
    survey_records: List[SurveyRecordInput] = Field(default_factory=list)
    human_verified: bool = Field(False, description="True only when a human reviewer has verified source documents")


@router.post("/reconcile", summary="Reconcile Warish Certificate with CS/SA/RS/BS Survey Khatians")
async def reconcile_warish_and_surveys(payload: ReconciliationRequest) -> Dict[str, Any]:
    cert_dict = payload.warish_certificate.model_dump()
    records_list = [r.model_dump() for r in payload.survey_records]
    return matcher.reconcile_inheritance_chain(
        cert_dict,
        records_list,
        human_verified=payload.human_verified,
    )
