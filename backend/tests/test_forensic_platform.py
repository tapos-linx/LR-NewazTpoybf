"""
Automated Forensic Verification Suite for AI Modules, SQLite Persistence,
ZIP Security, and Multi-Format Exports.
Compatible with both `python3 -m unittest` and `pytest`.
"""

from __future__ import annotations

import io
import json
import os
import tempfile
import unittest
import zipfile

from ai.gis.map_detector import CadastralMapDetector
from ai.hcr.recognizer import BanglaHandwritingRecognizer
from ai.matcher.inheritance_matcher import EvidenceTier, InheritancePlotMatcher
from ai.ocr.pipeline import ZeroLossOcrEngine
from backend.api.bulk_upload import classify_document_by_filename, is_safe_zip_member_path
from backend.api.export import (
    ExportPayload,
    _generate_csv_bytes,
    _generate_json_bytes,
    _generate_pdf_bytes,
    _generate_xlsx_bytes,
    sanitize_export_filename,
)
from backend.db.models import ForensicArchiveStore


class TestForensicPlatform(unittest.TestCase):

    def test_ocr_never_fabricates_on_empty_or_unconfigured_input(self) -> None:
        engine = ZeroLossOcrEngine()
        empty_res = engine.extract_text(b"", page_number=1)
        self.assertEqual(empty_res["status"], "PROCESSING_FAILED")
        self.assertEqual(empty_res["raw_text"], "")
        self.assertTrue(empty_res["requires_manual_review"])

        non_empty_res = engine.extract_text(b"not_an_image", page_number=2)
        self.assertIn(
            non_empty_res["status"],
            {"NOT_CONFIGURED", "UNSUPPORTED_FORMAT", "PROCESSING_FAILED"},
        )
        self.assertEqual(non_empty_res["raw_text"], "")

    def test_ocr_structured_parser_does_not_invent_missing_fields(self) -> None:
        engine = ZeroLossOcrEngine()
        sample = "জেলা: গাজীপুর | মৌজা: কাশিমপুর | খতিয়ান নং: ৪০৩ | দাগ নং: ১১৮"
        parsed = engine.parse_structured_fields(sample, page_number=1)
        self.assertEqual(parsed["fields"]["district"]["value"], "গাজীপুর")
        self.assertEqual(parsed["fields"]["mouza"]["value"], "কাশিমপুর")
        self.assertEqual(parsed["fields"]["khatian_no"]["normalized_digits"], "403")
        self.assertEqual(parsed["fields"]["dag_no"]["normalized_digits"], "118")
        # Missing fields MUST be None, never fabricated defaults like 'সাভার' or '১২৪'
        self.assertIsNone(parsed["fields"]["upazila"])
        self.assertIsNone(parsed["fields"]["jl_no"])
        self.assertIsNone(parsed["fields"]["area_decimals"])
        self.assertEqual(parsed["owners"], [])

    def test_hcr_never_fabricates_tokens_or_marginalia(self) -> None:
        recognizer = BanglaHandwritingRecognizer()
        empty = recognizer.recognize_handwriting(b"")
        self.assertEqual(empty["status"], "PROCESSING_FAILED")
        self.assertEqual(empty["transcribed_text"], "")
        self.assertEqual(empty["tokens"], [])

        # Without model weights or valid image, must not invent 'মোঃ আব্দুল করিম' or '৫১২/৩'
        raw_res = recognizer.recognize_handwriting(b"fake_bytes")
        self.assertIn(raw_res["status"], {"NOT_CONFIGURED", "UNSUPPORTED_FORMAT", "NEEDS_MANUAL_REVIEW"})
        self.assertNotIn("আব্দুল করিম", raw_res["transcribed_text"])
        self.assertNotIn("৫১২/৩", raw_res["transcribed_text"])

        marginal = recognizer.transcribe_marginal_notes(b"fake_bytes")
        self.assertNotIn("৪৮৯/২০০৩", marginal["transcription"])

    def test_hcr_supplied_text_flags_unclear_segments(self) -> None:
        recognizer = BanglaHandwritingRecognizer()
        res = recognizer.recognize_handwriting(
            b"",
            region_hint="owner_column",
            raw_text_override="মৃত সফর আলী [Unclear: পিতার নাম অস্পষ্ট]",
        )
        self.assertEqual(res["status"], "NEEDS_MANUAL_REVIEW")
        self.assertTrue(len(res["flagged_segments"]) >= 1)

    def test_gis_detector_never_falls_back_to_synthetic_grid_on_invalid_input(self) -> None:
        detector = CadastralMapDetector(survey_type="CS")
        empty = detector.detect_parcels(b"", target_dags=["502"])
        self.assertEqual(empty["status"], "PROCESSING_FAILED")
        self.assertEqual(empty["total_parcels_detected"], 0)
        self.assertEqual(empty["parcels"], [])

        bad = detector.detect_parcels(b"corrupt_image_bytes", target_dags=["502"])
        self.assertIn(bad["status"], {"NOT_CONFIGURED", "UNSUPPORTED_FORMAT"})
        self.assertEqual(bad["total_parcels_detected"], 0)

    def test_gis_synthetic_fixture_isolation_and_strict_target_highlighting(self) -> None:
        fixture = CadastralMapDetector.create_synthetic_test_dataset(
            survey_type="RS",
            target_dags=["৫০২"],
        )
        self.assertEqual(fixture["data_classification"], "SYNTHETIC_TEST_DATA")
        self.assertEqual(fixture["coordinate_system"], "PIXEL_SPACE")
        self.assertFalse(fixture["is_georeferenced"])
        self.assertEqual(fixture["total_parcels_detected"], 4)
        self.assertEqual(fixture["highlighted_target_count"], 1)

        detector = CadastralMapDetector(survey_type="RS")
        geojson = detector.export_geojson(fixture)
        self.assertEqual(geojson["metadata"]["coordinate_system"], "PIXEL_SPACE")
        self.assertFalse(geojson["metadata"]["is_georeferenced"])

    def test_inheritance_matcher_exact_rational_arithmetic_and_tiers(self) -> None:
        matcher = InheritancePlotMatcher()

        # 1. Exact 1/8 + 7/16 + 7/16 = 1.000
        cert = {
            "certificate_no": "WAR-2026-01",
            "deceased_name": "মৃত সফর আলী",
            "father_name": "মৃত নাজির আলী",
            "mouza": "গোয়ালী",
            "heirs": [
                {"name": "ফুলবানু", "relation": "স্ত্রী", "share": "1/8"},
                {"name": "কামদার আলী", "relation": "পুত্র", "share": "7/16"},
                {"name": "সিরাজ আলী", "relation": "পুত্র", "share": "7/16"},
            ],
        }
        # Empty survey records -> NO_MATCH (never invents Khatian 104 or 512)
        empty_res = matcher.reconcile_inheritance_chain(cert, [])
        self.assertEqual(empty_res["status"], "NO_MATCH")
        self.assertEqual(empty_res["overall_evidence_tier"], EvidenceTier.NO_MATCH)
        self.assertEqual(empty_res["matched_khatians_count"], 0)

        # Complete CS + SA + RS chain with father & Mouza corroboration
        surveys = [
            {
                "survey_type": era,
                "khatian_no": f"K-{era}",
                "dag_no": "502",
                "mouza": "গোয়ালী",
                "total_area_decimals": 80.0,
                "owners": [
                    {
                        "name": "সফর আলী",
                        "father_name": "নাজির আলী",
                        "share": 0.5,
                    }
                ],
            }
            for era in ("CS", "SA", "RS")
        ]
        # Without human_verified -> CORROBORATED (never auto-VERIFIED)
        unverified_res = matcher.reconcile_inheritance_chain(cert, surveys, human_verified=False)
        self.assertEqual(unverified_res["overall_evidence_tier"], EvidenceTier.CORROBORATED)
        self.assertTrue(unverified_res["requires_manual_review"])

        # With human_verified=True -> VERIFIED
        verified_res = matcher.reconcile_inheritance_chain(cert, surveys, human_verified=True)
        self.assertEqual(verified_res["overall_evidence_tier"], EvidenceTier.VERIFIED)
        self.assertFalse(verified_res["requires_manual_review"])

    def test_sqlite_store_and_zip_traversal_protection(self) -> None:
        self.assertFalse(is_safe_zip_member_path("../../etc/passwd"))
        self.assertFalse(is_safe_zip_member_path("/etc/shadow"))
        self.assertFalse(is_safe_zip_member_path("subdir/../../../secret.txt"))
        self.assertTrue(is_safe_zip_member_path("CS/403_cs_khatian.pdf"))

        self.assertEqual(classify_document_by_filename("CS_403.pdf"), "CS_KHATIAN")
        self.assertEqual(classify_document_by_filename("warish_sonod.jpg"), "WARISH_CERTIFICATE")

        with tempfile.TemporaryDirectory() as tmpdir:
            db_path = os.path.join(tmpdir, "test_archive.sqlite3")
            store = ForensicArchiveStore(db_path=db_path)
            store.record_upload_item(
                batch_id="b1",
                filename="cs_101.pdf",
                extension=".pdf",
                size_bytes=1024,
                sha256="abc123hash",
                document_type="CS_KHATIAN",
                status="QUEUED",
            )
            self.assertEqual(store.has_seen_sha256("abc123hash"), "cs_101.pdf")
            self.assertIsNone(store.has_seen_sha256("unknown_hash"))

            corr = store.record_hcr_correction(
                document_id="doc-1",
                page_number=1,
                field_name="owner_name",
                original_ocr_text="সফর আল [Unclear]",
                corrected_text="সফর আলী",
                reviewer_id="researcher_1",
                confidence_before=0.52,
                notes="Verified against original CS volume",
            )
            self.assertEqual(corr["verification_status"], "VERIFIED_BY_HUMAN")
            history = store.get_hcr_corrections("doc-1")
            self.assertEqual(len(history), 1)
            self.assertEqual(history[0]["original_ocr_text"], "সফর আল [Unclear]")
            self.assertEqual(history[0]["corrected_text"], "সফর আলী")

    def test_multi_format_exports_csv_json_xlsx_pdf(self) -> None:
        self.assertEqual(sanitize_export_filename("../../evil/name"), "evil_name")

        payload = ExportPayload(
            project_name="Goali_Forensic_Export",
            division="Chattogram",
            district="Brahmanbaria",
            upazila="Nabinagar",
            mouza="Goali",
            jl_no="104",
            dags=[
                {
                    "dag_no": "502",
                    "khatian_no": "104",
                    "survey": "RS",
                    "area_decimals": 24.5,
                    "class": "Nal",
                    "evidence_tier": "CORROBORATED",
                }
            ],
        )

        csv_bytes = _generate_csv_bytes(payload)
        self.assertTrue(csv_bytes.startswith(b"\xef\xbb\xbf"))
        self.assertIn("Goali", csv_bytes.decode("utf-8-sig"))

        json_bytes = _generate_json_bytes(payload)
        parsed_json = json.loads(json_bytes.decode("utf-8"))
        self.assertEqual(parsed_json["schema_version"], "2.0.0")
        self.assertEqual(parsed_json["jurisdiction"]["mouza"], "Goali")

        xlsx_bytes = _generate_xlsx_bytes(payload)
        with zipfile.ZipFile(io.BytesIO(xlsx_bytes), "r") as zf:
            names = set(zf.namelist())
            self.assertIn("xl/workbook.xml", names)
            self.assertIn("xl/worksheets/sheet1.xml", names)
            self.assertIn("xl/worksheets/sheet9.xml", names)

        pdf_bytes = _generate_pdf_bytes(payload)
        self.assertTrue(pdf_bytes.startswith(b"%PDF-1.4"))
        self.assertIn(b"%%EOF", pdf_bytes)


if __name__ == "__main__":
    unittest.main()
