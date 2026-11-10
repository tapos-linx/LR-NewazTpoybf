# বাংলাদেশ ভূমি রেকর্ড এক্সট্রাক্টর ও ফরেনসিক রিসার্চ প্ল্যাটফর্ম
## Bangladesh Land Record Extraction & Forensic Research Platform (`LR-NewazTpoybf`)

[![CI Verification](https://github.com/tapos-linx/LR-NewazTpoybf/actions/workflows/ci.yml/badge.svg)](https://github.com/tapos-linx/LR-NewazTpoybf/actions/workflows/ci.yml)
[![Android Build](https://github.com/tapos-linx/LR-NewazTpoybf/actions/workflows/android.yml/badge.svg)](https://github.com/tapos-linx/LR-NewazTpoybf/actions/workflows/android.yml)
[![Backend AI Engine](https://github.com/tapos-linx/LR-NewazTpoybf/actions/workflows/backend.yml/badge.svg)](https://github.com/tapos-linx/LR-NewazTpoybf/actions/workflows/backend.yml)
[![Release APK](https://github.com/tapos-linx/LR-NewazTpoybf/actions/workflows/release.yml/badge.svg)](https://github.com/tapos-linx/LR-NewazTpoybf/releases)

A production-grade **Android (Kotlin + Jetpack Compose + Room)** and **Python (FastAPI + OpenCV + Tesseract + SQLite)** forensic land record platform engineered for Bangladesh historical and modern cadastral surveys (**CS, SA, RS, BS/BRS, City Jarip**), **Registered Deeds (দলিল)**, **Mutations (নামজারি/খারিজ)**, **Warish / Inheritance Certificates (ওয়ারিশ সনদ)**, and **Cadastral Mouza Maps (মৌজা নকশা)**.

---

## ১. ফরেনসিক নীতিমালা (Core Forensic Integrity Principles)

1. **Zero Fabricated Results (কোনো কাল্পনিক তথ্য নয়)**:
   - Production OCR (`ZeroLossOcrEngine`, `BengaliOfflineOcrEngine`), Handwriting Recognition (`BanglaHandwritingRecognizer`, `BanglaHcrEngine`), Cadastral Map Detection (`CadastralMapDetector`), and Warish Reconciliation (`InheritancePlotMatcher`, `InheritanceMatcher`) **never** invent Khatian numbers, Dag numbers, Mouza names, owner names, road lines, water bodies, or GPS coordinates when extraction fails or models are unavailable.
   - Missing or unreadable inputs return explicit forensic statuses: `PROCESSING_FAILED`, `NEEDS_MANUAL_REVIEW`, `NOT_CONFIGURED`, `UNSUPPORTED_FORMAT`, or `NO_MATCH`.
   - Unclear OCR/HCR tokens are preserved alongside `[Unclear: reason]` annotations and immutable human-correction audit logs.
   - Any synthetic test fixtures are strictly isolated to test helpers and explicitly tagged `SYNTHETIC_TEST_DATA`.

2. **Evidence-Tiered Title Chain & Warish Hissa Arithmetic**:
   - Reconciles **CS (1888–1940) → SA (1956–1962) → RS (1965–present) → BS/BRS → Deeds → Mutations → Warish Certificates**.
   - Uses exact rational (`Fraction` / `BigDecimal`) arithmetic to validate **16 Annas (`1.000`)** share totals.
   - Assigns `VERIFIED` only when a continuous documentary chain is corroborated by parent/spouse identity, Mouza/Dag alignment, **and** human review sign-off; otherwise classifies as `CORROBORATED`, `PROBABLE`, `POSSIBLE`, `RECORD_GAP`, `CONTRADICTORY`, or `NO_MATCH`.

3. **Hardened Document Ingestion (SAF, CameraX Crop/Align, Google Drive, ZIP)**:
   - Supports recursive folder trees, multi-file selection, and `.zip` archive unpacking via Android Storage Access Framework (SAF) with **zero broad storage permissions** (`READ_EXTERNAL_STORAGE` / `MANAGE_EXTERNAL_STORAGE` are never requested).
   - Enforces **ZipSlip path-traversal blocking**, **ZIP bomb decompression caps** (`MAX_ZIP_ENTRIES = 500`, `200 MB` uncompressed ceiling), **zero-byte file detection**, and **SHA-256 content deduplication**.
   - Includes interactive **CameraX manual 4-corner cropping, 90° rotation, and contrast enhancement** prior to OCR.

4. **Multi-Format Forensic Exports**:
   - **PDF (`.pdf`)**: Multi-page A4 formatted report (or portable PDF-1.4 stream in headless environments).
   - **Excel Workbook (`.xlsx`)**: Standards-compliant 9-sheet OpenXML `.xlsx` archive (`Metadata`, `Documents`, `Survey_Chain`, `Dags`, `Owners`, `Heirs`, `Shares`, `Discrepancies`, `Evidence`).
   - **CSV (`.csv`)**: UTF-8 with BOM (`\uFEFF`) for direct Bengali rendering in Microsoft Excel.
   - **JSON (`.json`) & GeoJSON (`.geojson`)**: Versioned schema (`2.0.0`) with explicit `PIXEL_SPACE` vs `GEOGRAPHIC_WGS84` coordinate separation.
   - **ZIP Bundle (`.zip`)**: Complete portable forensic archive.

---

## ২. প্রজেক্ট আর্কিটেকচার (Repository Structure)

```text
LR-NewazTpoybf/
├── app/                                      # Android Client (Kotlin, Jetpack Compose, Room v6)
│   └── src/
│       ├── main/java/com/example/
│       │   ├── data/local/                   # Room Database v6 + explicit Migrations (1->6), Entities, DAOs
│       │   ├── data/model/                   # Domain Models, BengaliNumberUtils, Confidence Levels
│       │   ├── data/repository/              # LandRecordRepository, InheritorRepository, ExtractedLandRecordRepository
│       │   ├── domain/
│       │   │   ├── atlas/                    # Historical Survey Chain & Dag Split Engine
│       │   │   ├── drive/                    # Google Drive Direct Injection Manager
│       │   │   ├── export/                   # PDF, XLSX, CSV (UTF-8 BOM), JSON, TXT, ZIP Exporter
│       │   │   ├── gis/                      # Cadastral Map Detector & Target-Only Dag Highlighter
│       │   │   ├── hcr/                      # Bengali Handwriting Preprocessor & Correction Audit Engine
│       │   │   ├── image/                    # ImagePreprocessor, Crop/Align & PDF Page Renderer
│       │   │   ├── matcher/                  # Warish Inheritance Hissa & Survey Matcher
│       │   │   ├── ocr/                      # TesseractOcrService, BengaliOfflineOcrEngine, TessDataManager
│       │   │   ├── parser/                   # Deterministic BangladeshLandRecordParser (Zero-Fabrication)
│       │   │   ├── saf/                      # SafFolderScanner (ZipSlip/ZipBomb Defense + SHA-256)
│       │   │   ├── search/                   # Bengali/English Normalized Search Engine
│       │   │   └── validation/               # ForensicValidationEngine
│       │   ├── ui/screens/                   # Compose Material 3 Screens (Home, SAF, Drive, Camera, Detail, Warish, Gallery)
│       │   └── worker/                       # WorkManager Durable BatchExtractionWorker
│       └── test/java/com/example/            # JUnit4 + Robolectric Automated Test Suite
├── ai/                                       # Python AI & Cadastral Processing Modules
│   ├── ocr/pipeline.py                       # ZeroLossOcrEngine (Tesseract ben+eng + [Unclear] tagging)
│   ├── hcr/recognizer.py                     # BanglaHandwritingRecognizer (OpenCV CLAHE + Deskew + Audit)
│   ├── gis/map_detector.py                   # CadastralMapDetector (Contour Polygons + PIXEL_SPACE GeoJSON)
│   └── matcher/inheritance_matcher.py        # InheritancePlotMatcher (Exact Fraction/Decimal 16-Anna Engine)
├── backend/                                  # FastAPI Server & SQLite Forensic Store
│   ├── main.py                               # CORS Allowlist, API Key Auth Middleware, Size Limits, /health
│   ├── compat.py                             # FastAPI/Stdlib Compatibility Layer
│   ├── db/models.py                          # ForensicArchiveStore (Thread-Safe SQLite Persistence)
│   ├── api/                                  # Routers: bulk_upload, hcr, gis, matcher, export
│   └── tests/test_forensic_platform.py       # Automated Python Forensic Test Suite
└── .github/workflows/                        # Strict CI/CD Pipelines (ci.yml, android.yml, backend.yml, release.yml)
```

---

## ৩. বিল্ড ও টেস্ট নির্দেশিকা (Build & Verification Commands)

### Android App & Robolectric Test Suite
```bash
# Run all Android Unit & Robolectric tests
gradle :app:testDebugUnitTest

# Assemble Debug APK
gradle :app:assembleDebug
```

### Python AI & FastAPI Backend Test Suite
```bash
# Run with Python standard library unittest (zero external dependencies required)
PYTHONPATH=. python3 -m unittest discover -s backend/tests -v

# Or run with pytest in a full virtualenv
pip install -r backend/requirements.txt pytest httpx
PYTHONPATH=. pytest backend/tests -v
```

---

## ৪. নিরাপত্তা ও গোপনীয়তা (Security & Privacy)
- **Permissions**: Uses `CAMERA` (optional hardware feature for live document scanning) and `INTERNET` / `ACCESS_NETWORK_STATE` (for optional Tesseract traineddata download, Google Drive injection, and Gemini AI analysis). Never requests `READ_EXTERNAL_STORAGE`, `WRITE_EXTERNAL_STORAGE`, or `MANAGE_EXTERNAL_STORAGE`.
- **Backend Hardening**: Enforces explicit `ALLOWED_ORIGINS` CORS configuration, `FORENSIC_API_KEY` header/Bearer authentication when configured, `MAX_REQUEST_BODY_BYTES` size limits, ZipSlip path-traversal blocking, and SQLite persistence.
