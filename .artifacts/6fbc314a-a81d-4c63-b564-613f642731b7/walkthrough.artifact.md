# Walkthrough - Batch Audit & Re-parse Registered Cards

Implemented and verified a feature to crawl registered business cards in the database, automatically diagnose low-accuracy / incomplete contacts, and batch re-parse them using the latest OCR & AI algorithms.

## Changes

### ViewModel & Data Layer
- **`BusinessCardViewModel.kt`**: Implemented `batchReparseCards(...)` suspend function:
  - Queries all registered cards in the database (`repository.getAllCardsList()`).
  - Filters cards based on selection mode ("Low Accuracy / Incomplete Cards Only" vs "All Cards with Stored Images").
  - Sequentially re-parses eligible cards via `rescanCard(...)` using the latest `OcrHelper` and `TextParser.parseSmart` / `GeminiContactParser`.
  - Preserves user notes, manual tags, interaction history, and creation timestamps.
  - Reports real-time progress (`current`, `total`) back to the UI.

### UI Presentation Layer
- **`menu_main.xml`**: Added overflow menu item `action_batch_reparse` ("Auto-Audit & Re-parse Cards" / "登録データの自動診断・一括再解析").
- **`MainActivity.kt`**:
  - Implemented `showBatchReparseDialog()` to present option selection (Low Accuracy Only vs All Cards).
  - Implemented `performBatchReparse(lowAccuracyOnly)` displaying a modal progress dialog during execution (`再解析中... %1$d / %2$d 件`).
  - Displays summary Toast upon completion with total updated card count.
- **`strings.xml`**: Added string resources for menu actions, selection options, progress messages, and completion toasts.

### Documentation
- **`AGENTS.md`**: Updated documentation to include the batch audit and re-parsing feature.

## Verification Results

### Automated Tests
- Ran `./gradlew :app:testDebugUnitTest`: **217 tests passed successfully, 0 failed.**
- Ran `./gradlew :app:assembleDebug`: **Build finished successfully.**
