# Gemini Contact Card Scanner (v1.4.0)

[![Build](https://github.com/keimech/gemini-contactcard-scanner/actions/workflows/build.yml/badge.svg)](https://github.com/keimech/gemini-contactcard-scanner/actions/workflows/build.yml)

An offline-first Android app for scanning, storing, and managing business cards using on-device OCR and Gemini Nano AI — no account, no cloud, no data ever leaves your device.

---

## Features

- **Scan cards** — capture the front and back with your camera or import from the gallery
- **On-device GenAI (Gemini Nano)** — structured contact information extraction (Name, Company, Job Title, Phone, Mobile, Email, Website, Address) powered by ML Kit GenAI Prompt API running locally on supported devices
- **On-device OCR** — powered by ML Kit with bundled models for English, Chinese (Simplified & Traditional), and Japanese; works fully offline
- **Parser debug panel** — see exactly how each OCR line was classified (name, company, phone, email, etc.) and copy the raw text to correct mistakes
- **Search & filter** — find contacts by name, company, or tag; filter by tag chips in the toolbar
- **Contact management** — view, edit, delete; find and merge duplicates
- **Interactions log** — record calls, meetings, emails, and notes against a contact with timestamps
- **Meeting Recorder** — live audio recording with on-device speech-to-text transcript and key-action extraction
- **Follow-up reminders** — schedule notifications to follow up with a contact
- **My Digital Card** — create your own card, generate a QR code, share via NFC tag write
- **Event mode** — batch-tag every card scanned at an event with a single tap
- **Import** — vCard (.vcf) and CSV
- **Export / Backup** — CSV export, full ZIP backup (contacts + scanned card images + raw OCR text), legacy JSON backup also supported for restore
- **Home screen widget** — shows contact count with quick-scan and view-all buttons
- **Theme** — system default, light, or dark

---

## Privacy

- **No INTERNET permission** — the app cannot make network requests
- All OCR, ML, and Gemini Nano AI processing happens 100% on-device
- Contact data is stored only in a local SQLite database in the app's private storage
- Camera, microphone, and NFC are used only for on-device features, never to transmit data
- Backups and exports are always user-initiated and go directly to your chosen app (Files, Drive, email, etc.)
- No analytics, crash reporters, or third-party SDKs that phone home

---

## Requirements

- Android 10+ (API 29)
- Camera required for scanning
- On-device AI features require device support for ML Kit GenAI Prompt API (e.g. Pixel 8/9 series or compatible Android devices)
- NFC optional — for writing your digital card to NFC tags
- Microphone optional — for the meeting recorder

---

## Building

**Prerequisites:** JDK 17, Android SDK (API 34 platform + build-tools)

```bash
git clone https://github.com/keimech/gemini-contactcard-scanner.git
cd gemini-contactcard-scanner
./gradlew assembleDebug          # debug APK → app/build/outputs/apk/debug/
./gradlew testDebugUnitTest      # unit tests
```

### Release build

```bash
export KEYSTORE_PASSWORD=<your-keystore-password>
export KEY_ALIAS=<your-key-alias>
export KEY_PASSWORD=<your-key-password>
./gradlew assembleRelease
```

---

## Libraries

| Library | Version | Licence |
|---------|---------|---------|
| [ML Kit GenAI Prompt API (Gemini Nano)](https://developers.google.com/ml-kit) | `1.0.0-beta4` | Google APIs Terms |
| [ML Kit Text Recognition](https://developers.google.com/ml-kit/vision/text-recognition) | `16.0.1` | Google APIs Terms |
| [CameraX](https://developer.android.com/training/camerax) | `1.4.2` | Apache 2.0 |
| [Room](https://developer.android.com/jetpack/androidx/releases/room) | `2.6.1` | Apache 2.0 |
| [Glide](https://github.com/bumptech/glide) | `4.16.0` | Apache 2.0 |
| [ZXing](https://github.com/zxing/zxing) | `3.5.3` | Apache 2.0 |
| [Material Components for Android](https://github.com/material-components/material-components-android) | `1.12.0` | Apache 2.0 |
| [Kotlin Coroutines](https://github.com/Kotlin/kotlinx.coroutines) | `1.9.0` | Apache 2.0 |

---

## Contributing

Pull requests are welcome. Please keep changes offline-first — no cloud SDKs, no telemetry, no INTERNET permission.

---

## Licence

[GNU Lesser General Public Licence v3.0](LICENSE)
