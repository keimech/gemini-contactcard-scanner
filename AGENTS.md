# AGENTS.md — Gemini Contact Card Scanner

This document serves as a comprehensive operational and architectural guide for AI agents working on this codebase.

---

## 1. Project Overview

- **App Name:** Gemini Contact Card Scanner
- **Package Name:** `com.businesscard.scanner`
- **Core Philosophy:** **Offline-first & Privacy-first.** No network requests, no cloud dependencies, no analytics, no `android.permission.INTERNET` permission.
- **Repository:** `https://github.com/keimech/gemini-contactcard-scanner`

---

## 2. Tech Stack & Environment

- **Language:** Kotlin 2.0.21
- **Target / Compile SDK:** 34 | **Min SDK:** 29 (Android 10+)
- **Build System:** Android Gradle Plugin (AGP) 8.3.2, Gradle 8.6, JDK 17
- **UI Framework:** Android Views (XML Layouts, ViewBinding, Material Components 3, ViewPager2)
- **Database:** Room 2.6.1 (SQLite) with WAL mode enabled
- **On-Device AI & OCR:**
  - **ML Kit GenAI Prompt API:** `1.0.0-beta4` (`com.google.mlkit:genai-prompt`) — On-device Gemini Nano structured parsing
  - **ML Kit Text Recognition:** `16.0.1` / CJK `16.0.0` — Offline OCR for English, Japanese, Chinese (Simplified & Traditional), and Korean
- **Camera & Media:** CameraX `1.4.2`, Glide `4.16.0`
- **Sharing & Connectivity:** ZXing `3.5.3` (QR code generation), Android NFC API
- **Concurrency:** Kotlin Coroutines `1.9.0` & StateFlow / LiveData

---

## 3. Architecture & Directory Structure

```
app/src/main/java/com/businesscard/scanner/
├── App.kt                      # Application class (initializations)
├── data/                       # Data layer (Room DB, DAOs, Entities, Parsers)
│   ├── AppDatabase.kt          # Room DB setup (Version 6) & Migrations
│   ├── BusinessCard.kt         # Primary entity for contact card
│   ├── InteractionLog.kt       # Entity for calls, meetings, notes
│   ├── BusinessCardDao.kt      # DAO for card queries, search, tags
│   ├── InteractionLogDao.kt   # DAO for interaction history
│   ├── BusinessCardRepository.kt # Repository bridging ViewModel and DB
│   └── VCardParser.kt          # vCard (.vcf) import & parse logic
├── ocr/                        # OCR & GenAI Processing Layer
│   ├── GeminiContactParser.kt  # On-device Gemini Nano parsing via ML Kit GenAI Prompt API
│   ├── OcrHelper.kt            # ML Kit Text Recognition wrapper (Latin, Chinese, Japanese, Korean)
│   ├── TextParser.kt           # Regex/rule-based fallback contact parser
│   ├── AutoTagger.kt           # Automatic tag classification logic
│   ├── ParsedResult.kt         # Data model for intermediate batch scan results
│   ├── BatchResultWrapper.kt   # Parcelable wrapper for passing batch scan items
│   └── CjkUtils.kt             # CJK & Hangul character utilities
├── ui/                         # UI Presentation Layer (Activities & ViewModels)
│   ├── MainActivity.kt         # Main contacts list, search, tag filter, import/backup
│   ├── ScanActivity.kt         # Camera/Gallery scan, OCR & Gemini Nano extraction
│   ├── ReviewContactsActivity.kt # Step-by-step review & edit screen for batch scan verification
│   ├── ReviewAdapter.kt        # ViewPager2 adapter for card review and inline field editing
│   ├── ReviewViewModel.kt      # ViewModel for batch review persistence operations
│   ├── ContactDetailActivity.kt# Contact details, interaction logs, rescan
│   ├── EditContactActivity.kt   # Contact edit, manual tag management
│   ├── MyCardActivity.kt       # Digital card creation, QR code, NFC tag write
│   ├── MeetingRecorderActivity.kt # Speech-to-text recording & action extraction
│   ├── MergeDuplicatesActivity.kt # Duplicate card detection & merging
│   ├── BusinessCardViewModel.kt   # Central ViewModel for contact operations
│   ├── BusinessCardAdapter.kt  # RecyclerView adapter for contacts
│   ├── BusinessCardWidget.kt   # App Widget implementation
│   └── ImageViewerActivity.kt  # Fullscreen card photo viewer
└── util/                       # Helpers
    ├── CsvUtils.kt             # CSV import / export utilities
    └── VCardUtils.kt           # vCard export utilities
```

---

## 4. Key Components & Concepts

### On-Device AI Parsing (`GeminiContactParser.kt`)
- Uses `com.google.mlkit:genai-prompt` to interface with Gemini Nano via AICore.
- Uses `@Generable` annotated `GenerableParsedContact` data class for typed schema enforcement.
- Must verify `isStructuredOutputFeatureAvailable()` and `isAvailable()` before invoking `generateContent()`.
- Warmed up and downloaded as needed without blocking UI.
- Fallback: If Gemini Nano is unavailable on the device, the app seamlessly falls back to `TextParser.kt` (regex/heuristics).

### Scanning & Camera Features (`ScanActivity.kt`)
- **CameraX Enhancements:** Uses `CAPTURE_MODE_MAXIMIZE_QUALITY` and tap-to-focus (`FocusMeteringAction`).
- **System Camera Integration:** Supports launching external system camera app (`MediaStore.ACTION_IMAGE_CAPTURE`) for device-native camera processing.
- **Continuous Batch Scan:** Non-blocking continuous capture mode backed by a background Coroutine queue that performs OCR + Gemini Nano extraction + Room DB insertion asynchronously.
- **Extracted Details Preview:** Instantly renders a card preview panel showing classified details (Name, Company, Title, Phone, Email, Address) upon scan.

### Offline Storage & Privacy
- **Database:** SQLite DB located at `/data/data/com.businesscard.scanner/databases/business_card_database`.
- **Card Images:** Stored as JPGs in internal private storage `context.filesDir` (`/data/data/com.businesscard.scanner/files/`). Never saved to public gallery unless explicitly exported.
- **Backups:** Full ZIP backups bundle the SQLite DB entries, original card photos, and raw OCR text.

---

## 5. Development & Strict Guidelines for Agents

1. **NEVER add `INTERNET` permission:** The application must remain strictly offline-first. Do not introduce remote APIs, web SDKs, or cloud analytics.
2. **File Modifications:** ALWAYS use built-in tools (`write_file`, `replace_file_content`, `multi_replace_file_content`). NEVER use shell commands (`sed`, `echo >`, etc.) to edit files.
3. **Room Database Migrations:** If modifying `BusinessCard` or `InteractionLog` entities, bump the database version in `AppDatabase.kt` and provide an explicit `Migration` object.
4. **Error Handling:** On-device ML Kit / Gemini Nano operations must be wrapped in `try-catch` blocks and fail gracefully to fallback parsers.
5. **Keep AGENTS.md Updated:** Whenever new features, database schema/entities, dependencies, or structural changes are added or modified, ALWAYS update this `AGENTS.md` file to keep the documentation accurate and up to date for subsequent AI agents.

---

## 6. Build & Test Commands

```bash
# Debug Build & Run Unit Tests
./gradlew assembleDebug testDebugUnitTest

# Release Build (Requires environment variables)
export KEYSTORE_PASSWORD=<password>
export KEY_ALIAS=<alias>
export KEY_PASSWORD=<password>
./gradlew assembleRelease
```
