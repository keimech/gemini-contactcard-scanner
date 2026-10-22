# Walkthrough - Update Contact Details Using Stored Card Images

Implemented and verified the ability to update contact registration details using already-loaded / stored business card images (`frontImagePath` and `backImagePath`) from `ContactDetailActivity` and `EditContactActivity` with full user review before saving.

## Changes

### Data & Logic Layer
- **`BusinessCardViewModel.kt`**: Added shared `rescanCard(...)` suspend function to handle loading stored card images, running `OcrHelper` and `TextParser.parseSmart` / `GeminiContactParser`, and returning the updated card object for user review (without silent background DB overwrites).

### UI Presentation Layer
- **`menu_detail.xml`**: Added `action_rescan` menu item ("Rescan from Images") to `ContactDetailActivity`.
- **`ContactDetailActivity.kt`**: Implemented `action_rescan` handling with a confirmation dialog that launches `EditContactActivity` with auto-rescan enabled.
- **`EditContactActivity.kt`**: Refactored to utilize shared `viewModel.rescanCard(...)` which populates the form fields for user review and lets the user click "Save Changes" (`btnSave`) to register/reflect updates.
- **`strings.xml`**: Added string resources for rescan action and confirmation dialog messages.

### Documentation
- **`AGENTS.md`**: Updated documentation to include the rescan feature from stored card images and Database Version 6.

## Verification Results

### Automated Tests
- Ran `./gradlew :app:testDebugUnitTest`: **216 tests passed successfully, 0 failed.**
- Ran `./gradlew :app:assembleDebug`: **Build finished successfully.**
