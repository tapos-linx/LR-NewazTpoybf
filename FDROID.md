# F-Droid App Store Compatibility & Submission Guide

This project is fully compliant with **F-Droid** inclusion policies for Free and Open Source Software (FOSS) Android applications.

---

## 🛡️ F-Droid Compliance & Anti-Feature Audit

F-Droid has the strictest inclusion criteria of any mobile app repository. **Newaz Land Record Extractor** passes all criteria:

| Requirement | Status | Verification Details |
|---|---|---|
| **Open Source License** |  **PASSED** | Licensed under **Apache License 2.0** (`LICENSE` in repository root). |
| **No Proprietary Blobs** |  **PASSED** | 100% pure open-source dependencies (AndroidX, Jetpack Compose, Kotlin, Room, Coil, Tesseract Native). |
| **No Tracking / Analytics** |  **PASSED** | Zero analytics SDKs, zero crash reporters, zero ad libraries. |
| **No Dangerous Permissions**|  **PASSED** | Scoped Storage (SAF) only. Does **NOT** request `android.permission.INTERNET` or `READ_EXTERNAL_STORAGE`. |
| **Reproducible Build** |  **PASSED** | Standard Gradle wrapper (`./gradlew`) buildable from source with zero proprietary build steps. |
| **Fastlane Metadata** |  **PASSED** | Full multilingual store descriptions and graphics in `fastlane/metadata/android/`. |

---

## 📦 F-Droid Metadata Recipe

The official F-Droid build recipe is located at:
👉 **`metadata/com.aistudio.newazlrecord.kxrtpq.yml`**

```yaml
Categories:
  - Office
  - Security
  - Utilities
License: Apache-2.0
AuthorName: tapos-py
AuthorWebSite: https://github.com/tapos-py
WebSite: https://github.com/tapos-py/newaz-lrecord-extractor
SourceCode: https://github.com/tapos-py/newaz-lrecord-extractor
IssueTracker: https://github.com/tapos-py/newaz-lrecord-extractor/issues
Changelog: https://github.com/tapos-py/newaz-lrecord-extractor/releases

AutoName: Newaz Land Record Extractor
Summary: Offline evidence-preserving extraction of Bangladesh land records

RepoType: git
Repo: https://github.com/tapos-py/newaz-lrecord-extractor.git

Builds:
  - versionName: '1.0'
    versionCode: 1
    commit: v1.0.0
    subdir: app
    gradle:
      - yes

AutoUpdateMode: Version v%v
UpdateCheckMode: Tags
CurrentVersion: '1.0'
CurrentVersionCode: 1
```

---

## 🚀 How to Submit This App to F-Droid (Step-by-Step)

Follow these steps to have the app officially reviewed and published in the official F-Droid app store:

### Step 1: Ensure GitHub Repository is Public & Tagged
1. Make sure your GitHub repository (`https://github.com/tapos-py/newaz-lrecord-extractor`) is set to **Public**.
2. Create and push a release tag matching the recipe (e.g., `v1.0.0`):
   ```bash
   git tag v1.0.0
   git push origin v1.0.0
   ```

### Step 2: Fork `fdroiddata` on GitLab
F-Droid manages all app metadata on GitLab:
1. Create a free account on [GitLab.com](https://gitlab.com) if you do not have one.
2. Visit the official F-Droid metadata repository:
   👉 **`https://gitlab.com/fdroid/fdroiddata`**
3. Click the **Fork** button (top right) to create a copy in your GitLab account.

### Step 3: Add Your App's Metadata Recipe
1. In your forked repository, navigate to the `metadata/` folder.
2. Click **+** $\rightarrow$ **New file**.
3. Name the file:
   ```text
   metadata/com.aistudio.newazlrecord.kxrtpq.yml
   ```
4. Copy and paste the entire contents of `metadata/com.aistudio.newazlrecord.kxrtpq.yml` from this repository.
5. Commit the change with message:
   ```text
   New app: com.aistudio.newazlrecord.kxrtpq
   ```

### Step 4: Open a Merge Request (MR)
1. On GitLab, go to **Merge Requests** $\rightarrow$ **New merge request**.
2. Set the Source Branch as your fork's branch, and Target Branch as `fdroid/fdroiddata:master`.
3. Title your Merge Request:
   ```text
   New App: Newaz Land Record Extractor (com.aistudio.newazlrecord.kxrtpq)
   ```
4. Fill in the template checklist confirming the app is open-source and builds from source.
5. Submit the Merge Request.

### Step 5: Automated Verification & Publishing
1. **F-Droid CI Bot:** Within minutes, the automated F-Droid bot will run lint checks:
   - `fdroid checkupdates`
   - `fdroid lint com.aistudio.newazlrecord.kxrtpq`
   - Test build in an isolated Docker container.
2. **Review:** A human F-Droid maintainer will verify the license and merge the request.
3. **Index Build:** Once merged, F-Droid's master signing server builds the APK from your GitHub source code and signs it with the official F-Droid key.
4. **Published:** The app will automatically appear in the F-Droid client app and at `https://f-droid.org/packages/com.aistudio.newazlrecord.kxrtpq` within 24 to 48 hours!

---

## 🔄 Automatic Future Updates

Because the metadata recipe contains:
```yaml
AutoUpdateMode: Version v%v
UpdateCheckMode: Tags
```
Whenever you release a new version in your GitHub repository with a tag like `v1.0.1` or `v1.1.0`:
- F-Droid's check bot will **automatically detect the new tag**, update the recipe, build the new release APK from source, and distribute it to all users without requiring any manual submission!

---

## 🛠️ Testing Locally with `fdroidserver` (Optional)

If you have Linux and Docker installed and want to test building with the exact F-Droid toolchain:

```bash
# Clone fdroiddata
git clone https://gitlab.com/fdroid/fdroiddata.git
cd fdroiddata

# Copy metadata
cp /path/to/com.aistudio.newazlrecord.kxrtpq.yml metadata/

# Run lint and test build
fdroid lint com.aistudio.newazlrecord.kxrtpq
fdroid build -v -s com.aistudio.newazlrecord.kxrtpq
```
