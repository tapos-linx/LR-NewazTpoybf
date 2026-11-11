# বাংলাদেশ ভূমি রেকর্ড এক্সট্রাক্টর ও ফরেনসিক রিসার্চ প্ল্যাটফর্ম
## Bangladesh Land Record Extraction & Forensic Research Platform (`LR-NewazTpoybf`)

[![CI Verification](https://github.com/tapos-linx/LR-NewazTpoybf/actions/workflows/ci.yml/badge.svg)](https://github.com/tapos-linx/LR-NewazTpoybf/actions/workflows/ci.yml)
[![Android Build](https://github.com/tapos-linx/LR-NewazTpoybf/actions/workflows/android.yml/badge.svg)](https://github.com/tapos-linx/LR-NewazTpoybf/actions/workflows/android.yml)
[![Backend AI Engine](https://github.com/tapos-linx/LR-NewazTpoybf/actions/workflows/backend.yml/badge.svg)](https://github.com/tapos-linx/LR-NewazTpoybf/actions/workflows/backend.yml)
[![Release APK](https://github.com/tapos-linx/LR-NewazTpoybf/actions/workflows/release.yml/badge.svg)](https://github.com/tapos-linx/LR-NewazTpoybf/releases)

---

## 1. Project Overview

**Newaz Land Record Extractor (`LR-NewazTpoybf`)** is an evidence-preserving **Android (Kotlin + Jetpack Compose + Material 3 + Room v7 + WorkManager + CameraX)** and **Python (FastAPI + OpenCV + Tesseract + SQLite)** forensic research platform engineered for historical and modern Bangladesh cadastral land records:
- **Cadastral Surveys**: CS (1888–1940), SA (1956–1962), RS (1965–present), BS/BRS, and City Jarip Khatians.
- **Title & Mutation Instruments**: Registered Deeds (দলিল), Mutation / Namjari Khatians (নামজারি/খারিজ), Warish / Inheritance Certificates (ওয়ারিশ সনদ), and Cadastral Mouza Maps (মৌজা নকশা).

> **Legal Research Disclaimer**: This software is strictly an evidence-organization and optical extraction tool for researchers and legal practitioners. It never declares legal ownership, title validity, or inheritance entitlement.

---

## 2. Feature List

### Implemented Core Features (Ready Out-of-the-Box)
- **Unified Multi-Source Document Ingestion (`DocumentImportManager`)**:
  - Android Phone Storage multi-file (`ACTION_OPEN_DOCUMENT`) and recursive folder (`ACTION_OPEN_DOCUMENT_TREE`) import.
  - Recursive `.zip` archive unpacking with **ZipSlip** and **ZipBomb** protection.
  - Desktop / Web Companion browser upload with short-lived pairing tokens (`/api/v1/upload/pair`, `/api/v1/upload/companion`).
  - Google Drive Android SAF File/Folder Picker pre-targeted to `tapos.cpl@gmail.com` plus public shared-link ingestion.
- **Unified Room v7 Document Library**:
  - Preserves `id`, `sourceFileName`, `storedFilename`, `mimeType`, `fileSizeBytes`, `sourceType`, `sourceFilePath`, `googleDriveFileId`, `sha256`, `importTimestamp`, `processingStatus`, `processingError`, `classifiedType`, `primaryMouza`, `primaryKhatianNo`, `primaryDagNo`, and `verificationStatus`.
  - Non-destructive database migrations (`MIGRATION_1_2` through `MIGRATION_6_7`).
- **Durable Bulk Import Management**:
  - Persistent Room-backed queue (`import_queue_items`) and background `WorkManager` workers (`DocumentImportWorker`, `BatchExtractionWorker`).
  - Source breakdown, total actual byte size, retry failed imports, resume interrupted operations, cancel queued jobs, duplicate removal without deleting original evidence, and Bengali/English filename search.
- **Forensic Data Integrity**:
  - Streaming SHA-256 checksum verification, immutable import & human-correction audit logs (`import_audit_logs`, `hcr_corrections`), zero fabricated fields, and explicit uncertainty markers (`[Unclear: ...]`).
- **Multi-Format Forensic Exports**:
  - PDF (`.pdf`), 9-sheet OpenXML Excel Workbook (`.xlsx`), UTF-8 BOM CSV (`.csv`), Versioned JSON/GeoJSON (`.json`), Text Summary (`.txt`), and Full Evidence Archive (`.zip`).

### Optional Features Requiring External Configuration
- **Google Drive REST API v3 Direct OAuth 2.0 Client (`GoogleDriveImportSource`)**: Requires registering your Android OAuth 2.0 Client ID in Google Cloud Console (`drive.readonly` scope). When unconfigured, the app reports `NOT_CONFIGURED` and provides zero-configuration Google Drive access via the Android OS Google Drive SAF provider.
- **Firebase AI Logic (Gemini Vision OCR/HCR)**: Requires a configured Firebase project (`google-services.json` + App Check).
- **Offline Bengali Tesseract OCR**: Requires downloading `ben.traineddata` + `eng.traineddata` via the in-app Tesseract Setup screen.
- **Signed GitHub Release APK/AAB Publishing**: Requires configuring `RELEASE_KEYSTORE_BASE64`, `STORE_PASSWORD`, and `KEY_PASSWORD` in GitHub Actions repository secrets.

---

## 3. Real Multi-Source Import Architecture

All imported documents flow through `com.example.domain.ingestion`:
- `DocumentImportManager` — Orchestrates multi-source imports and schedules unique `WorkManager` jobs (`LR_NEWAZ_UNIFIED_DOCUMENT_IMPORT_WORK`).
- `AndroidSafImportSource` — Handles `ACTION_OPEN_DOCUMENT` single/multi-file selection, `takePersistableUriPermission`, and app-private sandbox copying.
- `LocalFolderImportSource` — Handles `ACTION_OPEN_DOCUMENT_TREE` recursive folder traversal and nested `.zip` unpacking.
- `GoogleDriveImportSource` — Implements OAuth 2.0 Bearer calls to Google Drive API v3 (`files.list`, `files.get`, `files.export` for Google Docs/Sheets), HTTP `Range` resumable retry, and token revocation (`disconnectAndRevokeToken`).
- `DesktopUploadImportSource` — Pairs with the FastAPI Desktop/Web Companion upload server using short-lived tokens and verifies transferred files with SHA-256.
- `DocumentValidationService` & `DocumentHashService` — Enforces MIME/extension allowlists, file size caps, `%PDF-` / ZIP header validation, ZipSlip defense, ZipBomb caps, and streaming SHA-256 computation.
- `DocumentImportRepository` & `DocumentImportWorker` — Persists lifecycle states (`PENDING`, `IMPORTING`, `IMPORTED`, `VALIDATING`, `READY_FOR_PROCESSING`, `PROCESSING`, `COMPLETED`, `NEEDS_MANUAL_REVIEW`, `FAILED`, `DUPLICATE`, `UNSUPPORTED_FORMAT`).

---

## 4. Android Phone Storage Import

1. Open the **আমদানি ও কিউ (Import)** tab in the Android app.
2. Tap **লোকাল ডিস্ক ফাইল** (`card_import_files`) to launch `ACTION_OPEN_DOCUMENT` with multi-select enabled, or tap **ফোল্ডার (SAF)** (`card_import_folder`) to select an entire directory tree via `ACTION_OPEN_DOCUMENT_TREE`.
3. Alternatively, tap **জিপ ফোল্ডার (.zip)** (`btn_bulk_import_zip`) to select one or more `.zip` archives.
4. The app acquires persistable read permissions (`takePersistableUriPermission`), computes the SHA-256 hash of each file, copies the original file unmodified into app-private storage (`filesDir/records/{documentId}/`), and enqueues it for extraction.

---

## 5. Computer Hard-Disk Upload Instructions (Desktop / Web Companion)

Because an Android phone cannot directly mount a remote Windows/macOS/Linux hard drive over the air, `LR-NewazTpoybf` provides two real computer-to-Android workflows:

### Workflow A — FastAPI Desktop / Web Companion Upload (Wireless LAN)
1. Start the FastAPI backend on your computer:
   ```bash
   uvicorn backend.main:app --host 0.0.0.0 --port 8000
   ```
2. In the Android app's **Import** tab, enter your computer's LAN URL (e.g., `http://192.168.1.100:8000`) and tap **পিসি পেয়ারিং কোড তৈরি** (`btn_pair_desktop_companion`).
3. Open `http://<PC-LAN-IP>:8000/api/v1/upload/companion` in your computer browser, enter the 6-character pairing code, and drag-and-drop multiple files or folders from your computer hard disk.
4. Tap **পিসি ফাইল সিঙ্ক করুন** (`btn_sync_desktop_companion`) in the Android app. Each file is downloaded over HTTP, verified against its server SHA-256 hash, and added to the unified Room library.

### Workflow B — USB MTP / Local ZIP Transfer (Offline)
1. Connect your Android phone to the computer via USB (File Transfer / MTP mode) and copy folders or `.zip` bundles into `Downloads/`.
2. Use **ফোল্ডার (SAF)** or **জিপ ফোল্ডার (.zip)** in the app to ingest the entire hierarchy offline.

---

## 6. Google Drive Authorization & Import Setup

1. **Zero-Config Android SAF Google Drive Picker**:
   - Tap **গুগল ড্রাইভ** (`card_import_drive`) for folders or **ড্রাইভ ফাইল / জিপ** (`btn_bulk_import_drive_files`) for multiple files.
   - The intent automatically targets `tapos.cpl@gmail.com` (`content://com.google.android.apps.docs.storage/root/tapos.cpl%40gmail.com`).
2. **Direct Google Drive API v3 OAuth 2.0**:
   - Uses scope `https://www.googleapis.com/auth/drive.readonly`.
   - Exports Google Docs (`application/vnd.google-apps.document`) to PDF/DOCX and Google Sheets (`application/vnd.google-apps.spreadsheet`) to XLSX/CSV.
   - Supports resumable HTTP `Range` downloads and token revocation (`https://oauth2.googleapis.com/revoke`).

---

## 7. Bulk Import Guide

The Bulk Import Dashboard supports:
- Selecting multiple files, multiple folders, and `.zip` archives across sources into one unified batch.
- Viewing total selected files, total actual file size (`MB` / `KB`), and source breakdown (`ANDROID_SAF_FILE`, `ANDROID_SAF_FOLDER`, `LOCAL_ZIP_ARCHIVE`, `GOOGLE_DRIVE`, `DESKTOP_COMPANION`).
- Filtering queued items by survey type (`CS`, `SA`, `RS`, `BRS`, `DEEDS`, `MUTATION`) and searching by Bengali or English filenames.
- Retrying failed imports (`পুনঃচেষ্টা`), removing duplicate queue entries (`ডুপ্লিকেট সরান`) without deleting original evidence, and pausing/canceling background jobs.

---

## 8. Supported Document Formats

| Format | Extensions | MIME Types | Ingestion Handling |
|---|---|---|---|
| PDF Documents | `.pdf` | `application/pdf` | `%PDF-` header verification + multi-page `PdfRenderer` + OCR |
| Cadastral Scans & Photos | `.jpg`, `.jpeg`, `.png`, `.tiff`, `.tif`, `.webp` | `image/*` | EXIF deskew + adaptive contrast + OCR/HCR |
| ZIP Archives | `.zip` | `application/zip`, `application/x-zip-compressed` | Recursive unpacking + ZipSlip & ZipBomb defense |
| Spreadsheets & Tables | `.csv`, `.xlsx` | `text/csv`, `application/vnd.openxmlformats-officedocument.spreadsheetml.sheet` | Direct UTF-8 / OpenXML sharedStrings extraction + parser |
| Word & Text Records | `.docx`, `.txt`, `.json` | `application/vnd.openxmlformats-officedocument.wordprocessingml.document`, `text/plain`, `application/json` | Direct OpenXML / UTF-8 text extraction + parser |

---

## 9. Forensic Evidence Integrity

- **SHA-256 Content Hashing**: Every imported file is hashed via streaming 16 KB / 64 KB chunks (`DocumentHashService`).
- **Original Evidence Preservation**: Imported files are copied into `filesDir/records/{documentId}/` without modifying the original source URI.
- **Immutable Audit Trail**: Every import, duplicate match, validation failure, and manual human edit is recorded in `import_audit_logs` (Android Room) and `audit_events` / `hcr_corrections` (FastAPI SQLite).
- **Evidence Statuses**: `VERIFIED`, `CORROBORATED`, `PROBABLE`, `POSSIBLE`, `UNVERIFIED`, `RECORD_GAP`, `CONTRADICTORY`, `NO_MATCH` (plus 4-tier UI badges: `CONFIRMED`, `PROBABLE`, `CONFLICTED`, `UNVERIFIED`).

---

## 10. OCR / HCR / GIS Functionality

- **OCR (`TesseractOcrService`, `BengaliOfflineOcrEngine`, `ZeroLossOcrEngine`)**: Extracts Bengali (`ben`) and English (`eng`) text. Never fabricates confidence scores (returns `null` when unknown) or fallback sample text.
- **HCR (`BanglaHcrEngine`, `BanglaHandwritingRecognizer`)**: Preprocesses handwritten crops and preserves both `originalText` and `correctedText` with `verifiedBy = USER`.
- **GIS (`CadastralMapDetector`)**: Never invents parcel grids; returns `NO_MAP_PARSED` until real contour detection or user-placed manual markers (`ব্যবহারকারী-চিহ্নিত`) are supplied.

---

## 11. Android APK Direct Download Links

Once a version tag (`v*.*.*`) is published via the **Production Release Pipeline** (`.github/workflows/release.yml`), the following permanent GitHub Releases URLs serve the verified assets:

- **Signed Release APK**:
  `https://github.com/tapos-linx/LR-NewazTpoybf/releases/latest/download/Newaz-Land-Record-Extractor-release.apk`
- **Verified Debug APK**:
  `https://github.com/tapos-linx/LR-NewazTpoybf/releases/latest/download/Newaz-Land-Record-Extractor-debug.apk`
- **SHA-256 Checksums (`SHA256SUMS.txt`)**:
  `https://github.com/tapos-linx/LR-NewazTpoybf/releases/latest/download/SHA256SUMS.txt`

> **Note on Availability**: The `/releases/latest/download/...` URLs resolve to HTTP 200 only after the first tagged release (`v2.2.0`) completes in GitHub Actions with signing secrets configured. For untagged branch commits, download `Newaz-Land-Record-Extractor-debug-apk` from the latest [Android CI workflow run summary](https://github.com/tapos-linx/LR-NewazTpoybf/actions/workflows/android.yml).

---

## 12. Release Version & Build Date

- **Current Version**: `2.2.0` (`versionCode = 1`, `versionName = "1.0"`, `applicationId = "com.aistudio.newazlrecord.kxrtpq"`)
- **Database Schema**: Room `v7` (with explicit migrations `1 -> 7`)
- **Build Date**: `2026-11-11`

---

## 13. SHA-256 Verification Instructions

After downloading the APK and `SHA256SUMS.txt`, verify integrity before installation:

```bash
# Linux / macOS
sha256sum -c SHA256SUMS.txt

# Windows PowerShell
Get-FileHash .\Newaz-Land-Record-Extractor-debug.apk -Algorithm SHA256
```

---

## 14. Installation Instructions (Android 7.0+ / API 24+)

1. Download `Newaz-Land-Record-Extractor-release.apk` (or `Newaz-Land-Record-Extractor-debug.apk`) to your Android phone.
2. Verify the SHA-256 hash against `SHA256SUMS.txt`.
3. Open the APK file on your Android device and allow installation from your browser/file manager if prompted.
4. Launch **ভূমি রেকর্ড** (`LR-NewazTpoybf`). A fresh install starts with **0 documents, 0 owners, and 0 plots**.

---

## 15. Backend Deployment Instructions

```bash
python3 -m venv .venv
source .venv/bin/activate
pip install --upgrade pip
pip install -r backend/requirements.txt pytest httpx

# Optional environment variables
export ALLOWED_ORIGINS="http://localhost:3000,http://localhost:8000"
export FORENSIC_API_KEY="your-strong-random-api-key"
export MAX_REQUEST_BODY_BYTES="62914560"

# Start FastAPI server
uvicorn backend.main:app --host 0.0.0.0 --port 8000
```

---

## 16. Google OAuth Configuration Instructions

To enable direct Google Drive REST API v3 calls (`GoogleDriveImportSource`):
1. Open [Google Cloud Console](https://console.cloud.google.com/) and enable the **Google Drive API**.
2. Configure the OAuth Consent Screen with minimum scope `https://www.googleapis.com/auth/drive.readonly`.
3. Create an **OAuth 2.0 Client ID** of type **Android** with package name `com.aistudio.newazlrecord.kxrtpq` and your signing certificate's SHA-1 fingerprint.
4. Pass the resulting OAuth access token to `GoogleDriveImportSource.configureOAuthClient(clientId, bearerToken)`. Never embed client secrets in source code.

---

## 17. Security & Privacy Policy

- **Zero Broad Storage Permissions**: Never requests `READ_EXTERNAL_STORAGE`, `WRITE_EXTERNAL_STORAGE`, or `MANAGE_EXTERNAL_STORAGE`. Uses Android SAF (`ACTION_OPEN_DOCUMENT`, `ACTION_OPEN_DOCUMENT_TREE`).
- **Cloud Backup Disabled**: `android:allowBackup="false"` in `AndroidManifest.xml`, with `backup_rules.xml` and `data_extraction_rules.xml` excluding `newaz_land_records.db` and `records/` evidence directories.
- **Archive Hardening**: Enforces ZipSlip canonical path checks and ZipBomb decompression limits (`500` files, `200 MB` max uncompressed size, `100x` ratio threshold).

---

## 18. Troubleshooting Guide

| Symptom | Cause | Resolution |
|---|---|---|
| GitHub `/releases/latest/download/...` returns HTTP 404 | No tagged release (`v*.*.*`) has been published yet, or `RELEASE_KEYSTORE_BASE64` secret is missing | Configure `RELEASE_KEYSTORE_BASE64`, `STORE_PASSWORD`, and `KEY_PASSWORD` in GitHub Settings → Secrets → Actions, then push tag `v2.2.0` or trigger `release.yml` via `workflow_dispatch`. |
| Banner shows `"OCR মডেল ইনস্টল নেই"` | Offline Tesseract `ben.traineddata` is not yet downloaded | Open Settings (⚙️) → Tesseract Setup and download the Bengali/English traineddata files. |
| Google Drive link download fails with HTTP 401/403 | Target Drive file is private (`Restricted`) | Use the **গুগল ড্রাইভ** SAF Folder/File Picker (`tapos.cpl@gmail.com`) or authenticate via OAuth 2.0 (`drive.readonly`). |
| Desktop Companion upload returns HTTP 401 | Pairing token expired (15-minute TTL) or `FORENSIC_API_KEY` mismatch | Generate a fresh 6-digit pairing code in the Android app (`পিসি পেয়ারিং কোড তৈরি`). |
