# Implementation Plan - Batch Audit & Re-parse Registered Cards

Implement a feature to crawl registered business cards in the database, identify contacts parsed with older low-accuracy algorithms, and batch re-parse them using the latest OCR & AI algorithms.

## User Review Required

> [!IMPORTANT]
> **Data Safety Guarantee**: Batch re-parsing will strictly update OCR-derived fields (`personName`, `phoneticName`, `companyName`, `jobTitle`, `phone`, `mobile`, `email`, `website`, `address`, `rawTextFront`, `rawTextBack`) while **preserving** user notes, manual tags, interaction history, and creation timestamps.

- **Two Re-parse Scope Options**:
  1. **Low Accuracy / Incomplete Cards Only**: Automatically diagnoses cards missing `phoneticName`, `personName`, `companyName`, or contact info.
  2. **All Cards with Stored Images**: Re-parses every card that has a valid front/back image file stored locally.

## Open Questions

- None.

## Proposed Changes

### ViewModel & Data Layer

#### [MODIFY] [BusinessCardViewModel.kt](file:///C:/Users/amaga/Desktop/AndroidProject/gemini-contactcard-scanner/app/src/main/java/com/businesscard/scanner/ui/BusinessCardViewModel.kt)
- Add `batchReparseCards(context: Context, lowAccuracyOnly: Boolean, onProgress: (Int, Int) -> Unit): Int` suspend function.
- Diagnoses eligible cards in the database (`repository.getAllCardsList()`).
- Sequentially processes each eligible card through `rescanCard(...)` with the latest `OcrHelper` and `TextParser.parseSmart` / `GeminiContactParser`.
- Reports real-time progress (`processedIndex`, `totalCount`) back to the UI.

### UI Presentation Layer

#### [MODIFY] [menu_main.xml](file:///C:/Users/amaga/Desktop/AndroidProject/gemini-contactcard-scanner/app/src/main/res/menu/menu_main.xml)
- Add overflow menu item `action_batch_reparse` ("Batch Re-parse Cards" / "登録データの自動診断・一括再解析").

#### [MODIFY] [MainActivity.kt](file:///C:/Users/amaga/Desktop/AndroidProject/gemini-contactcard-scanner/app/src/main/java/com/businesscard/scanner/ui/MainActivity.kt)
- Handle `action_batch_reparse` menu click.
- Show an option dialog allowing the user to select between "Low Accuracy Only" and "All Saved Image Cards".
- Display a modal progress dialog during batch re-parsing (showing current progress e.g. `再解析中... 3 / 15 件`).
- Show a completion dialog/toast summarizing the number of updated contacts.

#### [MODIFY] [strings.xml](file:///C:/Users/amaga/Desktop/AndroidProject/gemini-contactcard-scanner/app/src/main/res/values/strings.xml)
- Add string resources for the batch re-parse menu, selection dialog, progress messages, and completion summary (in English and Japanese).

### Documentation

#### [MODIFY] [AGENTS.md](file:///C:/Users/amaga/Desktop/AndroidProject/gemini-contactcard-scanner/AGENTS.md)
- Update `AGENTS.md` to document the batch audit and re-parsing feature.

## Verification Plan

### Automated Tests
- Run unit tests: `./gradlew :app:testDebugUnitTest`
- Add unit tests for low-accuracy card audit filtering in `BusinessCardViewModelTest` or `TextParserTest`.

### Manual Verification
- Deploy to emulator/device.
- Trigger "Batch Re-parse Cards" from `MainActivity` overflow menu.
- Verify progress dialog displays accurately during processing.
- Verify that older contact cards are updated with new fields (including `phoneticName`) while preserving user notes and manual tags.
