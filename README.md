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

## 🏛️ মডিউল ০৩: ওয়ারিশনামা ও মৌজা নকশা মেলানো (Module 03: Warish Matcher & Cadastral Map Highlights)

1. **উত্তরাধিকার সনদ (ওয়ারিশনামা) ও খতিয়ান শৃঙ্খল মেলানো:**
   - **Cross-Referencing Engine:** ওয়ারিশদের নাম, সম্পর্ক এবং দাগ নম্বরসমূহ ডাটাবেজে সংরক্ষিত CS, SA, RS ও BRS জরিপ রেকর্ডের সাথে স্বয়ংক্রিয়ভাবে মিলিয়ে দেখে।
   - **৬টি ফরেনসিক প্রমাণ স্তর (Forensic Evidence Tiers):**
     1. `VERIFIED (যাচাইকৃত)`: মূল নথির সাথে সরাসরি মিলে গেছে এবং জরিপ শৃঙ্খল অক্ষুণ্ন।
     2. `CORROBORATED (সমর্থিত)`: একাধিক স্বতন্ত্র দলিল বা জরিপের মাধ্যমে সমর্থিত।
     3. `PROBABLE (সম্ভাব্য)`: নাম, দাগ ও মৌজা অনুসারে জোরালো সম্ভাবনা।
     4. `POSSIBLE (সম্ভাবনাময়)`: প্রাথমিক মিল বিদ্যমান কিন্তু সংযোগকারী নথি অপূর্ণ।
     5. `RECORD GAP (রেকর্ড ফাঁক)`: জরিপ শৃঙ্খলে মধ্যবর্তী খতিয়ান (যেমন: এসএ বা আরএস) অনুপস্থিত।
     6. `CONTRADICTORY (পরস্পরবিরোধী)`: অতিরিক্ত হিস্যা (> ১.০০০), নাম অমিল বা সাংঘর্ষিক দাবি।
   - **হিস্যা সমতা যাচাই:** ১৬ আনা বা ১.০০০ গাণিতিক যোগফল নিখুঁতভাবে পরীক্ষা করে এবং গরমিল থাকলে সতর্কতা জারি করে।
   - **দাগভিত্তিক জমি বণ্টন:** ওয়ারিশদের দশমিক হিস্যা অনুযায়ী প্রতিটি দাগের শতাংশ হিসেবে জমি বরাদ্দ গণনা করে।

2. **মৌজা নকশা ইন্টেলিজেন্স ও লক্ষ্য দাগ হাইলাইট (Cadastral Map Highlights):**
   - **ফরেনসিক কড়া বিধি (Crucial Rule):** কখনো সব দাগ হাইলাইট করা হয় না। শুধুমাত্র ওয়ারিশনামায় শনাক্তকৃত লক্ষ্য দাগসমূহ (`target Dags`) উজ্জ্বল এমারেল্ড/অ্যাম্বার বর্ডার ও ফিলের মাধ্যমে হাইলাইট করা হয়, বাকি দাগসমূহ মূল জরিপ নকশার আদলে অক্ষত থাকে।
   - **ইন্টারেক্টিভ ক্যানভাস ম্যাপ:** পিঞ্চ-টু-জুম (৮০% থেকে ৪০০%), প্যান, উত্তর দিক নির্দেশক (North Arrow), এবং ক্যাডাস্ট্রাল স্কেল (১৬ ইঞ্চি = ১ মাইল)।
   - **দাগ বিশ্লেষণ প্যানেল:** নকশার যেকোনো দাগের উপর ট্যাপ করলে দাগ নং, জমির শ্রেণি, আয়তন (শতাংশ) এবং ওয়ারিশদের প্রাপ্য হিস্যা দৃশ্যমান হয়।
   - **ফরেনসিক অডিট রিপোর্ট:** এক ক্লিকে সম্পূর্ণ অডিট সার্টিফিকেট তৈরি ও শেয়ার করার সুবিধা।

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

## 🏛️ মডিউল ০৪ - ১৫: উন্নত ইন্টেলিজেন্স ও প্রোডাকশন ফিচারসমূহ (Modules 04 - 15)

1. **মডিউল ০৪: বাংলা হস্তলিপি ও মার্জিনাল নোট (HCR)**
   - ঐতিহাসিক বাংলা পেঁচানো হস্তলিপি (Cursive Bengali), কেরানির কাটাকাটি, অফিসিয়াল সিলমোহর ও রাজস্ব স্ট্যাম্পের পাঠোদ্ধার।
   - টোকেন-লেভেল নির্ভরযোগ্যতা স্কোর (Confidence score) ও অনির্ভরযোগ্য তথ্যের জন্য `[Unclear: ...]` ফ্ল্যাগিং।
   - ম্যানুয়াল সংশোধন ইন্টারফেস এবং অডিট হিস্ট্রি সংরক্ষণ (Zero-Data-Loss)।

2. **মডিউল ০৫: মৌজা ও ক্যাডাস্ট্রাল নকশা ইন্টেলিজেন্স (GIS)**
   - CS, SA, RS ও BS নকশার দাগ সীমানা (Polygon parcels), দাগ নম্বর, সংযোগকারী রাস্তা ও নদী/খাল স্বয়ংক্রিয় শনাক্তকরণ।
   - **কঠোর ফরেনসিক নিয়ম:** কখনো সব দাগ হাইলাইট করা হয় না। কেবল ওয়ারিশনামা বা খতিয়ানের মিলকৃত দাগসমূহ গাঢ় সবুজ রঙে হাইলাইট করা হয়।
   - GeoJSON ও জিআইএস স্তর প্রস্তুতকরণ।

3. **মডিউল ০৬: ওয়ারিশনামা ও ফারায়েজ ম্যাচার (Inheritance Matcher)**
   - মৃত ব্যক্তির ওয়ারিশদের হিস্যা যোগফল (১৬ আনা বা ১.০০০) সমতা পরীক্ষা।
   - ৬টি আইনি প্রমাণ স্তর: `VERIFIED`, `CORROBORATED`, `PROBABLE`, `POSSIBLE`, `RECORD GAP`, `CONTRADICTORY`।

4. **মডিউল ০৭: ফরেনসিক ডাটাবেজ (Room v3 & PostGIS)**
   - ১০টি নরমালাইজড টেবিল: Documents, OCR, HCR, Maps, Plots, Owners, Evidence, Jobs, SearchIndex।
   - সম্পূর্ণ অফলাইন SQLite এবং প্রোডাকশন PostgreSQL + PostGIS কম্প্যাটিবল স্কিমা।

5. **মডিউল ০৮: পেশাদার বাল্ক আপলোড কিউ (Bulk Upload)**
   - ড্র্যাগ অ্যান্ড ড্রপ, ফোল্ডার রিকার্শন, কিউ মনিটরিং, ডুপ্লিকেট ওয়ার্নিং ব্যাজ এবং পজ/রিজিউম সমর্থন।

6. **মডিউল ০৯: মাল্টি-ফরম্যাট এক্সপোর্ট সিস্টেম**
   - Excel (.xlsx), CSV, PDF (প্রিন্টযোগ্য HTML ডসিয়ার), JSON এবং ফরেনসিক ZIP বান্ডিল তৈরি।

7. **মডিউল ১০ & ১২: শতভাগ অফলাইন সাপোর্ট (Offline First)**
   - ইন্টারনেট সংযোগ ছাড়াই স্থানীয় ডিভাইসে পূর্ণাঙ্গ ডাটাবেজ, অফলাইন OCR/HCR ও সার্চ ইঞ্জিন পরিচালনা।

8. **মডিউল ১১: GitHub Actions CI/CD**
   - Android APK টেস্ট ও বিল্ড পাইপলাইন, FastAPI পাইথন টেস্ট এবং রিলিজ পাবলিশার।

9. **মডিউল ১৩: ঐতিহাসিক প্রপার্টি অ্যাটলাস (Historical Atlas)**
   - CS (১৮৮৮) ➔ SA (১৯৫৬) ➔ RS (১৯৬৫) ➔ BS (১৯৯৮) ৪ যুগের ধারাবাহিক শৃঙ্খল ও দাগ বিভাজন (Dag Split) ও একত্রীকরণ (Dag Merge) ট্র্যাক।

10. **মডিউল ১৪: কৃত্রিম বুদ্ধিমত্তা চালিত ফরেনসিক ভ্যালিডেশন**
    - নামের ভিন্নতা (মোঃ vs মুহম্মদ), পিতার নামের গরমিল, জমির পরিমাপের রূপান্তর (একর, শতাংশ, কাঠা, বিঘা) ও শৃঙ্খল বিচ্ছিন্নতা শনাক্তকরণ।

---

## ⚡ FastAPI ব্যাকএন্ড পরিচালনা (Backend Execution)

```bash
# ১. ডিপেনডেন্সি ইনস্টল
pip install -r backend/requirements.txt

# ২. সার্ভার চালু
uvicorn backend.main:app --reload --host 0.0.0.0 --port 8000

# ৩. এপিআই ডকুমেন্টেশন ব্রাউজ
# http://localhost:8000/docs
```

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
