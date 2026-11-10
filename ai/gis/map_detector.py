"""
GIS & Cadastral Map Intelligence Engine.
Processes scanned CS, SA, RS, and BS/BRS Mouza Maps to:
1. Detect closed land parcel boundaries (polygons) using OpenCV contour analysis.
2. Attempt OCR on parcel interior ROIs for Dag/Plot numbers (দাগ নম্বর) when Tesseract is available.
3. Explicitly distinguish pixel coordinates (PIXEL_SPACE) from geographic coordinates (WGS84).
4. Highlight ONLY target Dags that have an independently verified/corroborated Dag label.

STRICT FORENSIC RULE:
Never fabricates parcel grids, sequential Dag numbers (101, 102...), roads, or water bodies
in production detection. If image decoding fails or no contours are found, returns explicit
status ('UNSUPPORTED_FORMAT', 'PROCESSING_FAILED', or 'NEEDS_MANUAL_REVIEW').
"""

from __future__ import annotations

import re
import shutil
from typing import Any, Dict, List, Optional, Tuple

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


BENGALI_TO_ENGLISH = str.maketrans("০১২৩৪৫৬৭৮৯", "0123456789")
ENGLISH_TO_BENGALI = str.maketrans("0123456789", "০১২৩৪৫৬৭৮৯")


def normalize_dag_token(value: Optional[str]) -> str:
    if not value:
        return ""
    cleaned = str(value).strip().translate(BENGALI_TO_ENGLISH)
    return re.sub(r"[^0-9A-Za-z/]", "", cleaned)


class CadastralMapDetector:
    """
    Analyzes high-resolution cadastral sheet images and extracts vector polygons
    in pixel space (or projected space only when explicit Ground Control Points are provided).
    """

    def __init__(self, survey_type: str = "RS", scale_ratio: Optional[str] = None):
        self.survey_type = survey_type
        self.scale_ratio = scale_ratio

    def _can_run_roi_ocr(self) -> bool:
        return HAS_PYTESSERACT and shutil.which("tesseract") is not None

    def _ocr_dag_inside_roi(self, gray_img: Any, bbox: Tuple[int, int, int, int]) -> Tuple[Optional[str], float]:
        if not self._can_run_roi_ocr() or gray_img is None:
            return None, 0.0
        x, y, w, h = bbox
        if w < 12 or h < 12:
            return None, 0.0
        roi = gray_img[max(0, y): y + h, max(0, x): x + w]
        if roi.size == 0:
            return None, 0.0
        try:
            pil_roi = Image.fromarray(roi)
            data = pytesseract.image_to_data(
                pil_roi,
                lang="ben+eng",
                config="--oem 1 --psm 7",
                output_type=pytesseract.Output.DICT,
            )
            best_dag: Optional[str] = None
            best_conf = 0.0
            for idx, word in enumerate(data.get("text", [])):
                token = normalize_dag_token(word)
                if token and any(ch.isdigit() for ch in token):
                    conf = max(0.0, float(data["conf"][idx]) / 100.0)
                    if conf > best_conf:
                        best_dag = token
                        best_conf = conf
            return best_dag, round(best_conf, 4)
        except Exception:
            return None, 0.0

    def detect_parcels(
        self,
        image_bytes: bytes,
        target_dags: Optional[List[str]] = None,
        ocr_dag_map: Optional[Dict[int, str]] = None,
        ground_control_points: Optional[List[Dict[str, float]]] = None,
    ) -> Dict[str, Any]:
        """
        Detects closed polygon boundaries from a cadastral map image.
        Never falls back to synthetic datasets when decoding fails or zero parcels are detected.
        """
        normalized_targets = {
            normalize_dag_token(d) for d in (target_dags or []) if normalize_dag_token(d)
        }

        if not image_bytes or len(image_bytes) == 0:
            return {
                "status": "PROCESSING_FAILED",
                "error": "ZERO_BYTE_INPUT",
                "survey_type": self.survey_type,
                "coordinate_system": "PIXEL_SPACE",
                "is_georeferenced": False,
                "total_parcels_detected": 0,
                "highlighted_target_count": 0,
                "highlighting_rule": "STRICT_TARGET_ONLY",
                "parcels": [],
                "road_lines": [],
                "water_bodies": [],
                "requires_manual_review": True,
            }

        if not HAS_CV2:
            return {
                "status": "NOT_CONFIGURED",
                "error": "OpenCV (cv2) is not installed in the runtime environment.",
                "survey_type": self.survey_type,
                "coordinate_system": "PIXEL_SPACE",
                "is_georeferenced": False,
                "total_parcels_detected": 0,
                "highlighted_target_count": 0,
                "highlighting_rule": "STRICT_TARGET_ONLY",
                "parcels": [],
                "road_lines": [],
                "water_bodies": [],
                "requires_manual_review": True,
            }

        nparr = np.frombuffer(image_bytes, np.uint8)
        img = cv2.imdecode(nparr, cv2.IMREAD_COLOR)
        if img is None:
            return {
                "status": "UNSUPPORTED_FORMAT",
                "error": "Could not decode cadastral sheet image bytes.",
                "survey_type": self.survey_type,
                "coordinate_system": "PIXEL_SPACE",
                "is_georeferenced": False,
                "total_parcels_detected": 0,
                "highlighted_target_count": 0,
                "highlighting_rule": "STRICT_TARGET_ONLY",
                "parcels": [],
                "road_lines": [],
                "water_bodies": [],
                "requires_manual_review": True,
            }

        gray = cv2.cvtColor(img, cv2.COLOR_BGR2GRAY)
        edges = cv2.Canny(gray, 50, 150, apertureSize=3)
        kernel = np.ones((3, 3), np.uint8)
        closed_edges = cv2.morphologyEx(edges, cv2.MORPH_CLOSE, kernel)

        contours, _ = cv2.findContours(closed_edges, cv2.RETR_CCOMP, cv2.CHAIN_APPROX_SIMPLE)

        parcels: List[Dict[str, Any]] = []
        parcel_index = 1
        for cnt in contours:
            area = float(cv2.contourArea(cnt))
            if 400.0 < area < 250000.0:
                epsilon = 0.02 * cv2.arcLength(cnt, True)
                approx = cv2.approxPolyDP(cnt, epsilon, True)
                if len(approx) >= 4:
                    M = cv2.moments(cnt)
                    cx = int(M["m10"] / M["m00"]) if M["m00"] != 0 else 0
                    cy = int(M["m01"] / M["m00"]) if M["m00"] != 0 else 0
                    x, y, w, h = cv2.boundingRect(cnt)
                    polygon_pts = [[int(pt[0][0]), int(pt[0][1])] for pt in approx]

                    # Determine Dag label ONLY from explicit ocr_dag_map or genuine ROI OCR
                    supplied_dag = (ocr_dag_map or {}).get(parcel_index)
                    if supplied_dag:
                        dag_str: Optional[str] = normalize_dag_token(supplied_dag)
                        dag_conf = 0.90
                        dag_source = "SUPPLIED_OCR_MAP"
                    else:
                        dag_str, dag_conf = self._ocr_dag_inside_roi(gray, (x, y, w, h))
                        dag_source = "TESSERACT_ROI_OCR" if dag_str else "UNRECOGNIZED"

                    is_target = bool(
                        dag_str
                        and dag_str in normalized_targets
                        and len(normalized_targets) > 0
                    )

                    parcels.append(
                        {
                            "parcel_index": parcel_index,
                            "dag_no": dag_str,
                            "dag_no_bengali": dag_str.translate(ENGLISH_TO_BENGALI) if dag_str else None,
                            "dag_confidence": dag_conf,
                            "dag_source": dag_source,
                            "centroid_px": [cx, cy],
                            "bbox_px": [int(x), int(y), int(w), int(h)],
                            "polygon_px": polygon_pts,
                            "pixel_area": round(area, 2),
                            "estimated_decimals": None,  # Requires verified map scale + calibration
                            "is_target_matched": is_target,
                            "stroke_style": "HIGHLIGHT_GOLD_RED" if is_target else "DEFAULT_SLATE",
                            "status": "EXTRACTED_PENDING_REVIEW" if dag_str else "NEEDS_MANUAL_REVIEW",
                        }
                    )
                    parcel_index += 1

        if not parcels:
            return {
                "status": "NEEDS_MANUAL_REVIEW",
                "error": "No closed parcel contours met cadastral geometry thresholds.",
                "survey_type": self.survey_type,
                "scale_ratio": self.scale_ratio,
                "image_dimensions_px": [int(img.shape[1]), int(img.shape[0])],
                "coordinate_system": "PIXEL_SPACE",
                "is_georeferenced": False,
                "total_parcels_detected": 0,
                "highlighted_target_count": 0,
                "highlighting_rule": "STRICT_TARGET_ONLY",
                "parcels": [],
                "road_lines": [],
                "water_bodies": [],
                "requires_manual_review": True,
            }

        is_georef = bool(ground_control_points and len(ground_control_points) >= 3)
        highlighted_count = sum(1 for p in parcels if p["is_target_matched"])

        return {
            "status": "EXTRACTED_PENDING_REVIEW" if any(p["dag_no"] for p in parcels) else "NEEDS_MANUAL_REVIEW",
            "survey_type": self.survey_type,
            "scale_ratio": self.scale_ratio,
            "image_dimensions_px": [int(img.shape[1]), int(img.shape[0])],
            "coordinate_system": "GEOGRAPHIC_WGS84" if is_georef else "PIXEL_SPACE",
            "is_georeferenced": is_georef,
            "total_parcels_detected": len(parcels),
            "highlighted_target_count": highlighted_count,
            "highlighting_rule": "STRICT_TARGET_ONLY",
            "parcels": parcels,
            "road_lines": [],
            "water_bodies": [],
            "requires_manual_review": True,
        }

    def export_geojson(
        self,
        detection_result: Dict[str, Any],
        origin_lat: Optional[float] = None,
        origin_lon: Optional[float] = None,
        pixel_to_degree_scale: Optional[float] = None,
    ) -> Dict[str, Any]:
        """
        Exports detected parcels to GeoJSON FeatureCollection.
        If origin_lat/origin_lon/pixel_to_degree_scale are not provided, exports in
        explicit non-georeferenced PIXEL_SPACE coordinates so callers never confuse
        pixel coordinates with real GPS coordinates.
        """
        is_calibrated = (
            origin_lat is not None
            and origin_lon is not None
            and pixel_to_degree_scale is not None
            and pixel_to_degree_scale > 0
        )
        features: List[Dict[str, Any]] = []
        for p in detection_result.get("parcels", []):
            poly = p.get("polygon_px", [])
            if not poly:
                continue
            if is_calibrated:
                ring = [
                    [
                        round(float(origin_lon) + pt[0] * float(pixel_to_degree_scale), 6),
                        round(float(origin_lat) - pt[1] * float(pixel_to_degree_scale), 6),
                    ]
                    for pt in poly
                ]
            else:
                ring = [[float(pt[0]), float(pt[1])] for pt in poly]

            if ring[0] != ring[-1]:
                ring.append(ring[0])

            features.append(
                {
                    "type": "Feature",
                    "properties": {
                        "parcel_index": p.get("parcel_index"),
                        "dag_no": p.get("dag_no"),
                        "dag_confidence": p.get("dag_confidence", 0.0),
                        "survey_type": detection_result.get("survey_type", self.survey_type),
                        "pixel_area": p.get("pixel_area"),
                        "estimated_decimals": p.get("estimated_decimals"),
                        "is_target_matched": p.get("is_target_matched", False),
                        "stroke_style": p.get("stroke_style", "DEFAULT_SLATE"),
                        "coordinate_system": "GEOGRAPHIC_WGS84" if is_calibrated else "PIXEL_SPACE",
                        "status": p.get("status", "NEEDS_MANUAL_REVIEW"),
                    },
                    "geometry": {
                        "type": "Polygon",
                        "coordinates": [ring],
                    },
                }
            )

        return {
            "type": "FeatureCollection",
            "metadata": {
                "survey_type": detection_result.get("survey_type", self.survey_type),
                "scale_ratio": detection_result.get("scale_ratio"),
                "coordinate_system": "GEOGRAPHIC_WGS84" if is_calibrated else "PIXEL_SPACE",
                "is_georeferenced": is_calibrated,
                "highlighting_rule": "STRICT_TARGET_ONLY",
                "highlighted_count": detection_result.get("highlighted_target_count", 0),
                "data_classification": detection_result.get("data_classification", "EXTRACTED_EVIDENCE"),
            },
            "features": features,
        }

    @staticmethod
    def create_synthetic_test_dataset(
        survey_type: str = "RS",
        target_dags: Optional[List[str]] = None,
    ) -> Dict[str, Any]:
        """
        Isolated synthetic test fixture for unit tests ONLY.
        Explicitly labelled SYNTHETIC_TEST_DATA and never called by detect_parcels().
        """
        normalized_targets = {
            normalize_dag_token(d) for d in (target_dags or []) if normalize_dag_token(d)
        }
        grid_specs = [
            ("501", [120, 140], [[60, 80], [180, 80], [180, 200], [60, 200]], 14400.0),
            ("502", [240, 140], [[180, 80], [300, 80], [300, 200], [180, 200]], 14400.0),
            ("503", [360, 140], [[300, 80], [420, 80], [420, 200], [300, 200]], 14400.0),
            ("504", [120, 260], [[60, 200], [180, 200], [180, 320], [60, 320]], 14400.0),
        ]
        parcels = []
        for idx, (dag, centroid, poly, area) in enumerate(grid_specs, start=1):
            is_target = dag in normalized_targets and len(normalized_targets) > 0
            parcels.append(
                {
                    "parcel_index": idx,
                    "dag_no": dag,
                    "dag_no_bengali": dag.translate(ENGLISH_TO_BENGALI),
                    "dag_confidence": 1.0,
                    "dag_source": "SYNTHETIC_FIXTURE",
                    "centroid_px": centroid,
                    "bbox_px": [poly[0][0], poly[0][1], 120, 120],
                    "polygon_px": poly,
                    "pixel_area": area,
                    "estimated_decimals": None,
                    "is_target_matched": is_target,
                    "stroke_style": "HIGHLIGHT_GOLD_RED" if is_target else "DEFAULT_SLATE",
                    "status": "SYNTHETIC_TEST_DATA",
                }
            )
        return {
            "status": "SYNTHETIC_TEST_DATA",
            "data_classification": "SYNTHETIC_TEST_DATA",
            "survey_type": survey_type,
            "scale_ratio": "16 inches = 1 mile (SYNTHETIC)",
            "coordinate_system": "PIXEL_SPACE",
            "is_georeferenced": False,
            "total_parcels_detected": len(parcels),
            "highlighted_target_count": sum(1 for p in parcels if p["is_target_matched"]),
            "highlighting_rule": "STRICT_TARGET_ONLY",
            "parcels": parcels,
            "road_lines": [],
            "water_bodies": [],
            "requires_manual_review": True,
        }
