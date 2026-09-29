# LR-NewazTpoybf Version 1.0 - Production Screen Recording Plan
This checklist provides the exact sequence of clicks, UI testTags, gestures, and inputs for screen capture.

---

### Step 01: Setup & Emulator Specs
- **Instructions:** Resolution 1080x2400 (or 4K), 60 FPS, Dark/Light theme set to Light Parchment, touch feedback enabled (show taps).

### Step 02: APK Installation
- **Instructions:** Capture file download from release directory, Package Installer launch, 'Install' tap, and home screen icon launch.

### Step 03: Tesseract Language Setup
- **Instructions:** Tap AppBar 'tesseract_setup_button', show Bengali (ben) & English (eng) pack cards, tap verify status.

### Step 04: Dashboard Exploration
- **Instructions:** Scroll through LegalDisclaimerBanner, highlight MetricCard numbers (Total, Verified, Errors), tap Quick CSV bar.

### Step 05: Bulk SAF Upload
- **Instructions:** Tap 'fab_saf_folder', select sample deed directory, record animated progress indicators, hash computation, zero-byte skip.

### Step 06: Document OCR & Binarization
- **Instructions:** Open DocumentDetailScreen, demonstrate Otsu binarization slider, toggle between Raw Scan and High-Contrast Text.

### Step 07: Bangla HCR Review
- **Instructions:** Tap 'btn_open_hcr' banner, open HcrCorrectionScreen, inspect token confidence chips, edit one uncertain token, save.

### Step 08: Cadastral Map GIS Detection
- **Instructions:** Load survey sheet, show contour vector polygon extraction, Bengali digit bounding boxes, road and water layers.

### Step 09: Warishnama Inheritance Matching
- **Instructions:** Tap 'btn_open_warish_matcher', choose Warish certificate, view heir tree, verify statutory Ana-Ganda-Kranti shares.

### Step 10: Cadastral Plot Highlighting
- **Instructions:** Tap on heir share 'দাগ ৪০২', demonstrate instant emerald green polygon highlight and gold perimeter glow.

### Step 11: Historical Property Atlas
- **Instructions:** Tap 'card_atlas_banner', scrub CS -> SA -> RS -> BS timeline, observe Dag 402 split into 402/1 and 402/2.

### Step 12: Offline FTS Search
- **Instructions:** Type 'দাগ ৪০২' into 'search_input', demonstrate 15ms instantaneous query filtering on HomeScreen.

### Step 13: Report Export Suite
- **Instructions:** Tap 'export_csv_button', launch ReportExportDialog, generate PDF Dossier, Excel XLSX, and ZIP archive.

### Step 14: Final Security Summary
- **Instructions:** Show Settings, zero internet traffic in network profiler, Room database integrity confirmation.
