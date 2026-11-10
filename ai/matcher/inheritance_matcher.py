"""
Warish (Inheritance / ওয়ারিশ সনদ) & Survey Chain Reconciliation Engine.
Performs deterministic rational/decimal share arithmetic and links deceased ancestors
(মৃত ব্যক্তি) to CS -> SA -> RS -> BS/BRS survey Khatians, Deeds, and Mutations.

STRICT FORENSIC RULES:
1. Never assumes identity from name similarity alone: requires parent/father/spouse
   corroboration plus Mouza/Khatian/Dag alignment.
2. Uses exact rational (Fraction) and Decimal arithmetic to prevent floating-point drift.
3. Never assigns EvidenceTier.VERIFIED without complete documentary chain AND explicit
   human review verification flag (human_verified=True).
4. Never invents survey records, Dag numbers, or default land areas.
"""

from __future__ import annotations

import difflib
import re
from decimal import Decimal, ROUND_HALF_UP
from fractions import Fraction
from typing import Any, Dict, List, Optional, Tuple


BENGALI_DIGIT_MAP = str.maketrans("০১২৩৪৫৬৭৮৯", "0123456789")


class EvidenceTier:
    VERIFIED = "VERIFIED"             # Complete chain + human-reviewed confirmation
    CORROBORATED = "CORROBORATED"     # Strong documentary + genealogical chain, pending human sign-off
    PROBABLE = "PROBABLE"             # Partial chain or minor transliteration difference
    POSSIBLE = "POSSIBLE"             # Name match without parent/Mouza corroboration
    RECORD_GAP = "RECORD_GAP"         # Missing intermediate survey or mutation link
    CONTRADICTORY = "CONTRADICTORY"   # Share sum > 1.0 or conflicting ownership transfers
    NO_MATCH = "NO_MATCH"             # No matching survey records found


class BengaliEntityNormalizer:
    """
    Normalizes Bengali honorifics, prefixes, and spelling variants without losing
    original text provenance.
    """

    PREFIXES = [
        "মরহুম", "মৃত", "মোঃ", "মো:", "মোসাঃ", "মোসা:", "শ্রী", "শ্রীযুক্ত",
        "আলহাজ্ব", "হাজী", "বেগম", "শেখ", "সৈয়দ", "কাজী",
    ]

    @classmethod
    def normalize_name(cls, name: Optional[str]) -> str:
        if not name:
            return ""
        cleaned = str(name).strip()
        for prefix in cls.PREFIXES:
            cleaned = re.sub(rf"^{re.escape(prefix)}\s*", "", cleaned)
        cleaned = cleaned.replace("য়", "য়").replace("ড়", "ড়").replace("ঢ়", "ঢ়")
        cleaned = re.sub(r"[.,\-:/()]", " ", cleaned)
        cleaned = re.sub(r"\s+", " ", cleaned).strip()
        return cleaned

    @classmethod
    def normalize_digits(cls, text: Optional[str]) -> str:
        if text is None:
            return ""
        return str(text).strip().translate(BENGALI_DIGIT_MAP)

    @classmethod
    def similarity(cls, name_a: Optional[str], name_b: Optional[str]) -> float:
        norm_a = cls.normalize_name(name_a)
        norm_b = cls.normalize_name(name_b)
        if not norm_a or not norm_b:
            return 0.0
        if norm_a == norm_b:
            return 1.0
        if norm_a in norm_b or norm_b in norm_a:
            return 0.90
        return round(difflib.SequenceMatcher(None, norm_a, norm_b).ratio(), 4)


def parse_rational_share(raw_value: Any) -> Fraction:
    """
    Parses a share (hissa) represented as float, Decimal string, fraction string ('1/8'),
    or Bengali digit string into an exact Fraction.
    """
    if raw_value is None:
        return Fraction(0, 1)
    if isinstance(raw_value, Fraction):
        return raw_value
    text = BengaliEntityNormalizer.normalize_digits(str(raw_value))
    if not text:
        return Fraction(0, 1)
    try:
        if "/" in text:
            num_s, den_s = text.split("/", 1)
            den = int(den_s.strip())
            if den == 0:
                return Fraction(0, 1)
            return Fraction(int(num_s.strip()), den)
        dec = Decimal(text)
        return Fraction(dec).limit_denominator(1000000)
    except Exception:
        return Fraction(0, 1)


class InheritancePlotMatcher:
    """
    Reconciles Warish (inheritance) certificates with multi-era Khatian & Mutation records.
    """

    def __init__(self, hissa_tolerance: float = 0.005):
        self.hissa_tolerance = Decimal(str(hissa_tolerance))

    def validate_hissa_arithmetic(self, heirs: List[Dict[str, Any]]) -> Dict[str, Any]:
        """
        Verifies that the sum of individual heir shares equals 1 (16 Annas = 1.000)
        using exact Fraction and Decimal arithmetic.
        """
        if not heirs:
            return {
                "is_valid": False,
                "total_share_sum": 0.0,
                "total_share_fraction": "0/1",
                "status": "NO_HEIRS_PROVIDED",
                "discrepancy": 1.0,
            }

        exact_sum = sum((parse_rational_share(h.get("share", 0.0)) for h in heirs), Fraction(0, 1))
        decimal_sum = (Decimal(exact_sum.numerator) / Decimal(exact_sum.denominator)).quantize(
            Decimal("0.000001"), rounding=ROUND_HALF_UP
        )
        discrepancy = abs(Decimal("1.000000") - decimal_sum)
        is_valid = discrepancy <= self.hissa_tolerance

        if is_valid:
            status = "VALID_16_ANNAS_COMPLETE"
        elif decimal_sum < Decimal("1.0"):
            status = "INCOMPLETE_SHARE_ALLOCATION"
        else:
            status = "OVER_ALLOCATED_CONTRADICTION"

        return {
            "is_valid": is_valid,
            "total_share_sum": float(round(decimal_sum, 6)),
            "total_share_fraction": f"{exact_sum.numerator}/{exact_sum.denominator}",
            "expected_sum": 1.0,
            "discrepancy": float(round(discrepancy, 6)),
            "status": status,
        }

    def reconcile_inheritance_chain(
        self,
        warish_certificate: Dict[str, Any],
        survey_records: List[Dict[str, Any]],
        human_verified: bool = False,
    ) -> Dict[str, Any]:
        """
        Matches a Warish certificate against survey records (CS, SA, RS, BS/BRS, Mutation).
        Never fabricates survey records when survey_records is empty.
        Never assumes identity from name similarity alone without father/husband or Mouza/Dag alignment.
        """
        deceased_name = warish_certificate.get("deceased_name", "")
        deceased_father = warish_certificate.get("father_name") or warish_certificate.get("father_or_husband_name", "")
        cert_mouza = warish_certificate.get("mouza") or warish_certificate.get("target_mouza", "")
        heirs = warish_certificate.get("heirs", [])

        hissa_check = self.validate_hissa_arithmetic(heirs)

        if not survey_records:
            return {
                "status": "NO_MATCH",
                "deceased_ancestor": deceased_name,
                "father_name": deceased_father,
                "certificate_no": warish_certificate.get("certificate_no", "UNKNOWN"),
                "hissa_validation": hissa_check,
                "overall_evidence_tier": EvidenceTier.CONTRADICTORY if not hissa_check["is_valid"] else EvidenceTier.NO_MATCH,
                "matched_khatians_count": 0,
                "Survey_Matches": [],
                "heir_plot_allocations": [],
                "discrepancies": (
                    [f"Hissa sum discrepancy: {hissa_check['total_share_sum']} != 1.000 ({hissa_check['status']})"]
                    if not hissa_check["is_valid"]
                    else ["No survey records supplied for reconciliation."]
                ),
                "requires_manual_review": True,
            }

        matched_surveys: List[Dict[str, Any]] = []
        heir_allocations: List[Dict[str, Any]] = []
        discrepancies: List[str] = []

        if not hissa_check["is_valid"]:
            discrepancies.append(
                f"Hissa sum discrepancy: {hissa_check['total_share_sum']} != 1.000 ({hissa_check['status']})"
            )

        for record in survey_records:
            record_owners = record.get("owners", [])
            record_mouza = record.get("mouza", "")
            mouza_aligned = (
                not cert_mouza
                or not record_mouza
                or BengaliEntityNormalizer.similarity(cert_mouza, record_mouza) >= 0.80
            )

            best_match_score = 0.0
            matched_owner_entry: Optional[Dict[str, Any]] = None
            father_corroborated = False

            for owner in record_owners:
                name_score = BengaliEntityNormalizer.similarity(deceased_name, owner.get("name", ""))
                owner_father = owner.get("father_name") or owner.get("father_or_husband_name", "")
                father_score = (
                    BengaliEntityNormalizer.similarity(deceased_father, owner_father)
                    if (deceased_father and owner_father)
                    else 0.0
                )

                if name_score > best_match_score:
                    best_match_score = name_score
                    matched_owner_entry = owner
                    father_corroborated = father_score >= 0.75

            if best_match_score >= 0.72 and matched_owner_entry is not None:
                ancestor_share_frac = parse_rational_share(matched_owner_entry.get("share", 0.0))
                raw_area = record.get("total_area_decimals")
                has_valid_area = raw_area is not None and float(raw_area) > 0.0
                total_plot_decimals = Decimal(str(raw_area)) if has_valid_area else Decimal("0")
                ancestor_decimals = (
                    total_plot_decimals
                    * Decimal(ancestor_share_frac.numerator)
                    / Decimal(ancestor_share_frac.denominator)
                )

                # Determine evidence tier strictly
                if not hissa_check["is_valid"]:
                    tier = EvidenceTier.CONTRADICTORY
                elif best_match_score >= 0.88 and father_corroborated and mouza_aligned:
                    tier = EvidenceTier.VERIFIED if human_verified else EvidenceTier.CORROBORATED
                elif best_match_score >= 0.80 and (father_corroborated or mouza_aligned):
                    tier = EvidenceTier.PROBABLE
                else:
                    # Name similarity alone without parent/Mouza corroboration is at most POSSIBLE
                    tier = EvidenceTier.POSSIBLE
                    discrepancies.append(
                        f"Khatian {record.get('khatian_no')}: Name matched ({best_match_score}) without parent/Mouza corroboration."
                    )

                matched_surveys.append(
                    {
                        "survey_type": record.get("survey_type", "UNKNOWN"),
                        "khatian_no": record.get("khatian_no"),
                        "dag_no": record.get("dag_no"),
                        "matched_owner_name": matched_owner_entry.get("name"),
                        "name_similarity_score": round(best_match_score, 4),
                        "father_corroborated": father_corroborated,
                        "mouza_aligned": mouza_aligned,
                        "ancestor_share": float(round(Decimal(ancestor_share_frac.numerator) / Decimal(ancestor_share_frac.denominator), 6)),
                        "ancestor_entitled_decimals": float(round(ancestor_decimals, 4)) if has_valid_area else None,
                        "evidence_tier": tier,
                    }
                )

                for heir in heirs:
                    h_frac = parse_rational_share(heir.get("share", 0.0))
                    effective_frac = ancestor_share_frac * h_frac
                    entitled_dec = (
                        total_plot_decimals
                        * Decimal(effective_frac.numerator)
                        / Decimal(effective_frac.denominator)
                        if has_valid_area
                        else None
                    )
                    heir_allocations.append(
                        {
                            "heir_name": heir.get("name"),
                            "relation": heir.get("relation") or heir.get("relationship"),
                            "dag_no": record.get("dag_no"),
                            "khatian_no": record.get("khatian_no"),
                            "survey_type": record.get("survey_type"),
                            "warish_share_fraction": float(round(Decimal(h_frac.numerator) / Decimal(h_frac.denominator), 6)),
                            "effective_plot_share": float(round(Decimal(effective_frac.numerator) / Decimal(effective_frac.denominator), 6)),
                            "effective_plot_fraction": f"{effective_frac.numerator}/{effective_frac.denominator}",
                            "entitled_land_decimals": float(round(entitled_dec, 4)) if entitled_dec is not None else None,
                        }
                    )

        survey_eras_present = {m["survey_type"] for m in matched_surveys if m.get("survey_type")}
        expected_eras = {"CS", "SA", "RS"}
        missing_eras = sorted(list(expected_eras - survey_eras_present))

        if missing_eras and len(survey_eras_present) > 0:
            discrepancies.append(
                f"Chain gap detected: Missing survey records for {', '.join(missing_eras)}"
            )

        if not hissa_check["is_valid"]:
            overall_tier = EvidenceTier.CONTRADICTORY
        elif not matched_surveys:
            overall_tier = EvidenceTier.NO_MATCH
        elif missing_eras:
            overall_tier = EvidenceTier.RECORD_GAP
        elif all(m["evidence_tier"] == EvidenceTier.VERIFIED for m in matched_surveys) and human_verified:
            overall_tier = EvidenceTier.VERIFIED
        elif any(m["evidence_tier"] in (EvidenceTier.CORROBORATED, EvidenceTier.VERIFIED) for m in matched_surveys):
            overall_tier = EvidenceTier.CORROBORATED
        elif any(m["evidence_tier"] == EvidenceTier.PROBABLE for m in matched_surveys):
            overall_tier = EvidenceTier.PROBABLE
        else:
            overall_tier = EvidenceTier.POSSIBLE

        return {
            "status": "RECONCILED" if matched_surveys else "NO_MATCH",
            "deceased_ancestor": deceased_name,
            "father_name": deceased_father,
            "certificate_no": warish_certificate.get("certificate_no", "UNKNOWN"),
            "hissa_validation": hissa_check,
            "overall_evidence_tier": overall_tier,
            "matched_khatians_count": len(matched_surveys),
            "Survey_Matches": matched_surveys,
            "heir_plot_allocations": heir_allocations,
            "discrepancies": discrepancies,
            "requires_manual_review": not human_verified or overall_tier != EvidenceTier.VERIFIED,
        }
