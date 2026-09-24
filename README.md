# Newaz Land Record Extractor

[![Android](https://img.shields.io/badge/Platform-Android-green.svg)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Language-Kotlin-purple.svg)](https://kotlinlang.org)
[![Jetpack Compose](https://img.shields.io/badge/UI-Jetpack%20Compose%20M3-blue.svg)](https://developer.android.com/jetpack/compose)
[![Offline First](https://img.shields.io/badge/Privacy-100%25%20Offline-brightgreen.svg)](#offline-first--privacy)

**Newaz Land Record Extractor** is a local, evidence-preserving Android application engineered for extracting, validating, and managing Bangladesh land records (Porcha/Khatian, Dag, Mouza, Deeds, Mutation, Dakhila) from PDFs, scanned images, and multi-page camera captures.

Developed for target repository: **`tapos-py/newaz-lrecord-extractor`**

---

## ⚠️ আইনগত সতর্কতা ও নির্দেশিকা (Legal Disclaimer)

> **গুরুত্বপূর্ণ ব্যবহারবিধি:**
> 1. এই অ্যাপ্লিকেশনে প্রদর্শিত অপটিক্যাল ক্যারেক্টার রিকগনিশন (OCR) ফলাফল প্রযুক্তিগতভাবে আনুমানিক।
> 2. আহরিত সকল তথ্য অবশ্যই মূল নথির অক্ষরের সাথে মিলিয়ে যাচাই করে নিতে হবে।
> 3. এই অ্যাপ্লিকেশনটি কোনোভাবেই ভূমির আইনি মালিকানা (Legal Ownership) নির্ধারণ করে না।
> 4. এটি কোনো আইনি মতামত (Legal Opinion) প্রদান করে না।
> 5. এটি কোনো উত্তরাধিকার (Inheritance) বা চূড়ান্ত স্বত্ব (Title) প্রমাণ করে না।
> 6. সকল অনিশ্চিত বা সম্ভাব্য ক্ষেত্রসমূহ 'অযাচাইকৃত' (Unverified/Probable) হিসেবে চিহ্নিত করা থাকে।

---

## 🛠 প্রযুক্তি ও আর্কিটেকচার (Technology Stack)

- **Language:** Kotlin 2.0+
- **UI Framework:** Jetpack Compose with Material Design 3 (M3)
- **Architecture:** MVVM + Clean Architecture with Coroutines & StateFlow
- **Local Persistence:** Room Database with full schema entities & foreign keys
- **Background Tasks:** Android WorkManager (`BatchExtractionWorker`)
- **Camera Document Capture:** CameraX (Preview, Flash/Torch, ImageCapture, Framing guide)
- **File System & Trees:** Android Storage Access Framework (SAF) recursive crawling
- **PDF Page Extraction:** Android `PdfRenderer` with high-resolution page rasterization
- **OCR Engine:** Offline Bengali (`ben`) & English (`eng`) script analyzer + Tesseract Native pipeline
- **Image Preprocessing:** Grayscale conversion, contrast stretching, adaptive local thresholding/binarization
- **Report Export:** Structured JSON audit export, CSV batch export, and Formatted Text summaries

---

## 📂 ইনপুট ফোল্ডার ও কাঠামো (Input Directory Structure)

The app accepts a root directory via Android Storage Access Framework (SAF) and recursively crawls nested directories such as:

```text
LandRecords/
└── input/
    ├── CS/                   # ক্যাডাস্ট্রাল সার্ভে খতিয়ান (Cadastral Survey)
    ├── SA/                   # স্টেট একুইজিশন খতিয়ান (State Acquisition)
    ├── RS/                   # রিভিশনাল সার্ভে খতিয়ান (Revisional Survey)
    ├── BRS/                  # বাংলাদেশ জরিপ / সিটি জরিপ (BRS / BS / City Survey)
    ├── inheritance/          # ওয়ারিশনামা / উত্তরাধিকার সনদ
    ├── deeds/                # রেজিস্ট্রিকৃত দলিল (সাফ-কবলা, হেবা, দানপত্র)
    ├── mutation/             # নামজারি, জমাভাগ ও খারিজ খতিয়ান
    ├── maps/                 # মৌজা নকশা ও সিট
    ├── tax/                  # ভূমি উন্নয়ন কর পরিশোধ দাখিলা
    └── tituwhatsapp/         # মিশ্র হোয়াটসঅ্যাপ বা স্ক্যানার ইনপুট (স্বয়ংক্রিয় শ্রেণিবিন্যাস)
        ├── 403 নং সি এস খতিয়ান.pdf
        ├── 247 নং বি আর এস খতিয়ান.pdf
        └── 521 নং আর এস খতিয়ান.pdf
```

- **Supported Formats:** PDF, JPG, JPEG, PNG, TIFF, WEBP.
- **Zero-Byte File Detection:** Automatically identifies and logs empty files (`0 bytes`) with prominent warning badges.
- **Safe Handling:** Never modifies or deletes original source files; copies to app sandbox for evidence preservation.

---

## 🔍 প্রমাণ-সংরক্ষণ ও OCR কার্যপদ্ধতি (Evidence Preservation Pipeline)

1. **Pristine Raw Preservation:** Each rendered page or camera shot is saved as a pristine raw image.
2. **Processed Copy:** An enhanced, contrast-stretched, and binarized copy is generated for OCR.
3. **Evidence Inspector:** In the document details screen, switch seamlessly between original raw and binarized images with pinch-to-zoom (up to 600%), pan, and 90° rotation.
4. **Structured Parsing:**
   - District (জেলা) & Upazila/Thana (উপজেলা / থানা)
   - Mouza (মৌজা) & JL No (জে. এল. নং) & Touzi No (তৌজি নং)
   - Khatian No (খতিয়ান নং) & Former/Hal Khatian
   - Dag No (দাগ নং) & Former Dag (সাবেক দাগ) & Hal Dag (হাল দাগ)
   - Land Classification (নাল, ভিটি, বাড়ি, পুকুর, ধানী, পতিত, ইত্যাদি)
   - Area (একর ও শতাংশ)
   - Owners (মালিক ও রায়তের নাম, পিতা/স্বামী, এবং হিস্যা/অংশ)
5. **Hissa Validation:** Verifies that owner shares sum to `1.000` (or 16 আনা). Flags mathematical discrepancies automatically.

---

## 📥 Tesseract বাংলা মডেল সেটআপ ও ডাউনলোড (Tesseract Bengali & English Setup)

The application provides an automated in-app download pipeline and manual loading options for Tesseract traineddata:

1. **One-Tap Automated In-App Download:**
   - Tap the **OCR Models** icon on the top bar of the Home screen.
   - Tap **"বাংলা ও ইংরেজি মডেল ডাউনলোড করুন (ben + eng)"** to automatically download official models from GitHub directly into the app's local storage directory (`context.filesDir/tessdata/`).
   - Real-time download progress and size verification are displayed.

2. **Manual Download & Import via SAF:**
   - Download `ben.traineddata` and `eng.traineddata` from:
     - [Tesseract OCR Tessdata Fast](https://github.com/tesseract-ocr/tessdata_fast)
   - In the app, tap the **OCR Models** icon and select **"স্থানীয় ফাইল" (Import File)** to choose from your device storage.

3. **Direct ADB / Termux Transfer:**
   ```bash
   adb push ben.traineddata /data/data/com.aistudio.landrecord.nxwqpm/files/tessdata/
   adb push eng.traineddata /data/data/com.aistudio.landrecord.nxwqpm/files/tessdata/
   ```

4. **Offline Fallback Guarantee:**
   - If traineddata is not yet downloaded, the built-in offline Bengali & English script analyzer automatically processes land records without crashing or requiring internet access.

---

## 🚀 গিট রিপোজিটরি তৈরি ও পুশ করার নির্দেশিকা (Git Setup Instructions)

To publish this project to GitHub at **`tapos-py/newaz-lrecord-extractor`**:

```bash
# 1. Initialize git if not already initialized
git init

# 2. Add all project files
git add .

# 3. Create initial commit
git commit -m "feat: initial release of Newaz Land Record Extractor with Bengali/English OCR and evidence preservation"

# 4. Set default branch to main
git branch -M main

# 5. Add remote GitHub repository
git remote add origin https://github.com/tapos-py/newaz-lrecord-extractor.git

# 6. Push to GitHub (ensure you are authenticated via SSH or Personal Access Token)
git push -u origin main
```

---

## 📱 বিল্ড ও রান (Build & Execution)

- **Minimum SDK:** Android 7.0 (API 24)
- **Target / Compile SDK:** Android 15 / 16 Preview (API 36)
- **Build Tool:** Gradle with Kotlin DSL

To build debug APK:
```bash
gradle :app:assembleDebug
```

To run unit tests:
```bash
gradle :app:testDebugUnitTest
```
