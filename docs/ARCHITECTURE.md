# LR-NewazTpoybf: Bangladesh Forensic Land Record Intelligence Platform
## Version 1.0 Production Architecture & Roadmap Completion

### 1. Architectural Overview

```
+-------------------------------------------------------------------------------+
|                      Android UI (Jetpack Compose M3)                         |
|  - Bulk Upload Queue (SAF Recursive Directory Scanning & Drag/Drop)           |
|  - Gallery & Document Inspector (Raw vs Binarized, 600% Zoom, Rotation)       |
|  - Warish Certificate Matcher & Cadastral Map Highlights (Module 03 & 06)     |
|  - Bangla Handwriting Recognition (HCR) & Marginalia Review (Module 04)       |
|  - Historical Property Atlas (CS -> SA -> RS -> BS Timeline & Splits, Mod 13)|
+-------------------------------------------------------------------------------+
                                       |
                                       v
+-------------------------------------------------------------------------------+
|                        FastAPI Backend Intelligence                           |
|  - Zero-Data-Loss Ingestion (/api/v1/upload/batch)                            |
|  - Bangla HCR & Ink Enhancement (/api/v1/hcr/recognize-crop, /marginalia)     |
|  - Cadastral Survey Map GIS (/api/v1/gis/detect-map, /highlight-inheritance)   |
|  - Warish Farayez & Cross-Matching (/api/v1/matcher/cross-match)             |
|  - Forensic Multi-Format Exporter (/api/v1/export/json, /csv, /zip, /pdf)     |
+-------------------------------------------------------------------------------+
                                       |
                                       v
+-------------------------------------------------------------------------------+
|                      Data Persistence & Evidence Storage                      |
|  - Android Local SQLite (Room v3 Database: 10 Normalized Entities)            |
|  - PostgreSQL + PostGIS Production Schema (Spatial Parcels, Dags, Audits)     |
|  - Hash Deduplication (SHA-256 Strict Equality Rule)                          |
+-------------------------------------------------------------------------------+
```

---

### 2. Module Roadmap Matrix (Completed 01 - 15)

| Module | Title | Features Implemented | Git Artifact |
|---|---|---|---|
| **01** | Core Ingestion & SAF | Recursive SAF crawler, zero-byte detection, mime filtering | `feat/ingest` |
| **02** | Bulk Upload Queue | Batch progress, duplicate warning badges, queue management | `feat/bulk-upload` |
| **03** | OCR Engine | Tesseract + PaddleOCR, marginal notes, stamps, `[Unclear: ...]` | `feat/ocr` |
| **04** | Bangla HCR | TrOCR historical cursive, marginalia, token-level confidence | `feat/hcr` |
| **05** | Survey Map Intelligence | CS/SA/RS/BS cadastral detection, never-highlight-all rule, GeoJSON | `feat/gis` |
| **06** | Inheritance Matcher | Farayez calculation, 6 evidence tiers, CS/SA/RS/BS cross-matching | `feat/warish` |
| **07** | Database Architecture | Room v3 with 10 tables, PostGIS DDL, DAOs, indices | `feat/database` |
| **08** | Bulk Upload GUI | Infinite scrolling, real-time indicators, batch workers | `feat/ui` |
| **09** | Export System | Excel, CSV, PDF/HTML dossier, JSON, forensic ZIP bundles | `feat/export` |
| **10** | Android Build | Standalone APK, Android 10-15 target, bilingual, Dark mode | `feat/android` |
| **11** | GitHub CI/CD | GitHub Actions for Android, FastAPI, Pytest, Release bundles | `feat/ci` |
| **12** | Offline Mode | Local SQLite Room, offline heuristic OCR/HCR, offline search | `feat/offline` |
| **13** | Historical Property Atlas | CS -> SA -> RS -> BS timeline, Dag split & merge detection | `feat/atlas` |
| **14** | AI Validation Engine | Bengali name normalizer, father mismatch, area unit conversion | `feat/validation` |
| **15** | Production Release | v1.0 release bundle, documentation, security review, clean exit | `release/v1.0` |

---

### 3. Forensic Rules Enforced

1. **Zero Data Loss Rule**:
   - No extracted text or annotation is silently omitted.
   - Illegible or damaged characters are labeled: `[Unclear: <reason>]` (e.g. `[Unclear: Faded Ink]`, `[Unclear: Torn Paper]`, `[Unclear: Faded Seal]`).

2. **Deduplication Rule**:
   - Records are considered duplicate *only* when SHA-256 hash, document, page, and metadata match.
   - Different copies or differing marginal notes are preserved as distinct evidence records.

3. **Cadastral Highlighting Rule**:
   - Never highlight all parcels on a cadastral survey sheet.
   - Highlight *only* parcels matched from the verified inheritance certificate (target Dags). All other parcels remain in standard survey ink outline.

4. **Forensic Evidence Tiers**:
   - `VERIFIED`: Unbroken chain, matching name/father, exact area and hissa balance.
   - `CORROBORATED`: Multi-source agreement via secondary deeds or mutations.
   - `PROBABLE`: Name variant match (e.g. মোঃ vs মোহাম্মদ), area aligns within tolerance.
   - `POSSIBLE`: Partial match with unverified parentage.
   - `RECORD GAP`: Missing intermediate survey era (e.g. CS and RS exist, SA missing).
   - `CONTRADICTORY`: Conflicting owners, total hissa exceeds 1.000 / 16 anna, or overlapping claims.
