"""
Forensic Bengali + English Land Record OCR Pipeline.
Provides lossless image preprocessing, genuine Tesseract OCR (ben+eng),
per-token confidence scoring, and strict [Unclear: reason] formatting.
Never invents or fabricates text when OCR fails or Tesseract is unavailable.
"""

from __future__ import annotations

import io
import re
import shutil
from typing import Any, Dict, List, Optional

try:
    import cv2
    import numpy as np
    HAS_CV2 = True
except ImportError:
    cv2 = None  # type: ignore
    np = None  # type: ignore
    HAS_CV2 = False

try:
    import pytesseract
    from PIL import Image
    HAS_PYTESSERACT = True
except ImportError:
    pytesseract = None  # type: ignore
    Image = None  # type: ignore
    HAS_PYTESSERACT = False


BENGALI_DIGIT_MAP = str.maketrans("0123456789", "০১২৩৪৫৬৭৮৯")
ENGLISH_DIGIT_MAP = str.maketrans("০১২৩৪৫৬৭৮৯", "0123456789")


def to_bengali_digits(value: str) -> str:
    return value.translate(BENGALI_DIGIT_MAP)


def to_english_digits(value: str) -> str:
    return value.translate(ENGLISH_DIGIT_MAP)


class ZeroLossOcrEngine:
    """
    Production OCR engine for Bangladesh land records (CS, SA, RS, BS/BRS, Deeds, Mutations).
    Never fabricates text: if Tesseract or image decoding is unavailable, returns an explicit
    status ('NOT_CONFIGURED', 'UNSUPPORTED_FORMAT', or 'PROCESSING_FAILED').
    """

    def __init__(self, lang: str = "ben+eng", confidence_threshold: float = 0.65):
        self.lang = lang
        self.confidence_threshold = confidence_threshold

    def is_tesseract_ready(self) -> bool:
        return HAS_PYTESSERACT and shutil.which("tesseract") is not None

    def preprocess_image(self, image_bytes: bytes) -> Optional[Any]:
        if not image_bytes:
            return None
        if not HAS_CV2:
            return None
        arr = np.frombuffer(image_bytes, np.uint8)
        img = cv2.imdecode(arr, cv2.IMREAD_COLOR)
        if img is None:
            return None
        gray = cv2.cvtColor(img, cv2.COLOR_BGR2GRAY)
        clahe = cv2.createCLAHE(clipLimit=2.0, tileGridSize=(8, 8))
        enhanced = clahe.apply(gray)
        denoised = cv2.fastNlMeansDenoising(enhanced, None, 10, 7, 21)
        binary = cv2.adaptiveThreshold(
            denoised,
            255,
            cv2.ADAPTIVE_THRESH_GAUSSIAN_C,
            cv2.THRESH_BINARY,
            25,
            11,
        )
        return binary

    def extract_text(self, image_bytes: bytes, page_number: int = 1) -> Dict[str, Any]:
        """
        Extracts Bengali + English text with bounding boxes and confidence from image bytes.
        Never returns synthetic or placeholder Khatian text.
        """
        if not image_bytes or len(image_bytes) == 0:
            return {
                "status": "PROCESSING_FAILED",
                "error_reason": "ZERO_BYTE_INPUT",
                "page_number": page_number,
                "raw_text": "",
                "overall_confidence": 0.0,
                "tokens": [],
                "unclear_segments": [],
                "extraction_method": "NONE",
                "requires_manual_review": True,
            }

        if not self.is_tesseract_ready():
            return {
                "status": "NOT_CONFIGURED",
                "error_reason": "Tesseract binary or pytesseract library is not installed in runtime environment.",
                "page_number": page_number,
                "raw_text": "",
                "overall_confidence": 0.0,
                "tokens": [],
                "unclear_segments": [],
                "extraction_method": "TESSERACT_UNAVAILABLE",
                "requires_manual_review": True,
            }

        try:
            processed = self.preprocess_image(image_bytes)
            if processed is not None:
                pil_img = Image.fromarray(processed)
            else:
                pil_img = Image.open(io.BytesIO(image_bytes))
        except Exception as exc:
            return {
                "status": "UNSUPPORTED_FORMAT",
                "error_reason": f"Failed to decode image bytes: {exc}",
                "page_number": page_number,
                "raw_text": "",
                "overall_confidence": 0.0,
                "tokens": [],
                "unclear_segments": [],
                "extraction_method": "IMAGE_DECODE_ERROR",
                "requires_manual_review": True,
            }

        try:
            ocr_data = pytesseract.image_to_data(
                pil_img,
                lang=self.lang,
                config="--oem 1 --psm 6",
                output_type=pytesseract.Output.DICT,
            )
        except Exception as exc:
            return {
                "status": "PROCESSING_FAILED",
                "error_reason": f"Tesseract execution failed: {exc}",
                "page_number": page_number,
                "raw_text": "",
                "overall_confidence": 0.0,
                "tokens": [],
                "unclear_segments": [],
                "extraction_method": f"TESSERACT_{self.lang}",
                "requires_manual_review": True,
            }

        tokens: List[Dict[str, Any]] = []
        unclear_segments: List[Dict[str, Any]] = []
        assembled_words: List[str] = []
        confidences: List[float] = []

        n_boxes = len(ocr_data.get("text", []))
        for i in range(n_boxes):
            word = (ocr_data["text"][i] or "").strip()
            if not word:
                continue
            raw_conf = float(ocr_data["conf"][i])
            if raw_conf < 0:
                continue
            conf = round(max(0.0, min(1.0, raw_conf / 100.0)), 4)
            bbox = [
                int(ocr_data["left"][i]),
                int(ocr_data["top"][i]),
                int(ocr_data["width"][i]),
                int(ocr_data["height"][i]),
            ]
            flagged = conf < self.confidence_threshold
            token_entry = {
                "text": word,
                "confidence": conf,
                "bbox": bbox,
                "flagged_for_review": flagged,
                "page_number": page_number,
            }
            tokens.append(token_entry)
            confidences.append(conf)

            if flagged:
                reason = "Low OCR confidence"
                unclear_tag = f"{word} [Unclear: {reason} ({int(conf * 100)}%)]"
                assembled_words.append(unclear_tag)
                unclear_segments.append(
                    {
                        "token": word,
                        "confidence": conf,
                        "bbox": bbox,
                        "formatted": unclear_tag,
                    }
                )
            else:
                assembled_words.append(word)

        raw_text = " ".join(assembled_words).strip()
        avg_conf = round(sum(confidences) / len(confidences), 4) if confidences else 0.0
        requires_review = (not raw_text) or (avg_conf < 0.80) or (len(unclear_segments) > 0)
        status = "NEEDS_MANUAL_REVIEW" if requires_review else "EXTRACTED_PENDING_REVIEW"

        return {
            "status": status,
            "page_number": page_number,
            "raw_text": raw_text,
            "overall_confidence": avg_conf,
            "tokens": tokens,
            "unclear_segments": unclear_segments,
            "extraction_method": f"TESSERACT_{self.lang}",
            "requires_manual_review": requires_review,
        }

    def parse_structured_fields(self, raw_text: str, page_number: int = 1) -> Dict[str, Any]:
        """
        Extracts structured Bangladesh land record fields from OCR text without inventing defaults.
        Missing fields are returned as None with status='MISSING'.
        """
        if not raw_text or not raw_text.strip():
            return {
                "status": "NO_TEXT_EXTRACTED",
                "fields": {
                    "district": None,
                    "upazila": None,
                    "mouza": None,
                    "jl_no": None,
                    "touzi_no": None,
                    "khatian_no": None,
                    "dag_no": None,
                    "former_dag_no": None,
                    "hal_dag_no": None,
                    "area_acres": None,
                    "area_decimals": None,
                },
                "owners": [],
                "requires_manual_review": True,
            }

        def match_first(patterns: List[str]) -> Optional[Dict[str, Any]]:
            for pat in patterns:
                m = re.search(pat, raw_text, flags=re.IGNORECASE)
                if m and m.group(1).strip():
                    val = m.group(1).strip()
                    return {
                        "value": val,
                        "normalized_digits": to_english_digits(val),
                        "bengali_digits": to_bengali_digits(val),
                        "raw_match": m.group(0).strip(),
                        "page_number": page_number,
                        "verification_status": "EXTRACTED_UNVERIFIED",
                    }
            return None

        fields = {
            "district": match_first([r"জেলা[:\s]+([\u0980-\u09FFA-Za-z\-]+)", r"district[:\s]+([A-Za-z\-]+)"]),
            "upazila": match_first([r"(?:উপজেলা|থানা)[:\s]+([\u0980-\u09FFA-Za-z\-]+)", r"(?:upazila|thana)[:\s]+([A-Za-z\-]+)"]),
            "mouza": match_first([r"মৌজা[:\s]+([\u0980-\u09FFA-Za-z\-]+)", r"mouza[:\s]+([A-Za-z\-]+)"]),
            "jl_no": match_first([r"(?:জে[.\s]*এল|jl)[.\s]*(?:নং|no)?[:\s]*([০-৯0-9]+)"]),
            "touzi_no": match_first([r"(?:তৌজি|touzi)[.\s]*(?:নং|no)?[:\s]*([০-৯0-9]+)"]),
            "khatian_no": match_first([r"(?:খতিয়ান|খতিয়ান|khatian)[.\s]*(?:নং|no)?[:\s]*([০-৯0-9/]+)"]),
            "dag_no": match_first([r"(?:দাগ|প্লট|dag|plot)[.\s]*(?:নং|no)?[:\s]*([০-৯0-9/]+)"]),
            "former_dag_no": match_first([r"(?:সাবেক|পূর্বে)\s*দাগ[.\s]*(?:নং|no)?[:\s]*([০-৯0-9/]+)"]),
            "hal_dag_no": match_first([r"(?:হাল|বর্তমান)\s*দাগ[.\s]*(?:নং|no)?[:\s]*([০-৯0-9/]+)"]),
            "area_acres": match_first([r"([০-৯0-9.]+)\s*(?:একর|acre)"]),
            "area_decimals": match_first([r"([০-৯0-9.]+)\s*(?:শতাংশ|শতক|ডেসিমাল|decimal)"]),
        }

        owner_pattern = re.compile(
            r"(?:মালিক|রায়ত|নাম)[:\s]+([^,\n]+)(?:,\s*(?:পিতা|স্বামী)[:\s]+([^,\n]+))?(?:,\s*অংশ[:\s]+([০-৯0-9./]+))?",
            flags=re.IGNORECASE,
        )
        owners: List[Dict[str, Any]] = []
        for idx, m in enumerate(owner_pattern.finditer(raw_text), start=1):
            name = m.group(1).strip()
            father = m.group(2).strip() if m.group(2) else None
            share = m.group(3).strip() if m.group(3) else None
            owners.append(
                {
                    "serial": idx,
                    "name": name,
                    "father_or_husband": father,
                    "share_raw": share,
                    "page_number": page_number,
                    "verification_status": "EXTRACTED_UNVERIFIED",
                }
            )

        missing_critical = any(fields[k] is None for k in ("mouza", "khatian_no", "dag_no"))
        return {
            "status": "NEEDS_MANUAL_REVIEW" if missing_critical else "EXTRACTED_PENDING_REVIEW",
            "fields": fields,
            "owners": owners,
            "requires_manual_review": True,
        }
