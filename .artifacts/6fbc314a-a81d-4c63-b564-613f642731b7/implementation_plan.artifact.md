# Implementation Plan - Update Contact Details Using Stored Card Images

Add and enhance the ability to update contact registration details using already-loaded / stored business card images (front & back) directly from `ContactDetailActivity` and `EditContactActivity`.

## User Review Required

> [!NOTE]
> `EditContactActivity` already includes a "Rescan from Images" button (`btnRescan`), but `ContactDetailActivity` does not provide direct access to re-run OCR and update details from stored images without navigating to the edit screen first.

- **Proposed Enhancement**: Add a "Rescan from Images" menu action in `ContactDetailActivity` and ensure users can re-parse and update fields seamlessly from stored card images.

## Open Questions

- None. The stored card image paths (`frontImagePath` and `backImagePath`) are already persisted in the Room database (`BusinessCard` entity).

## Proposed Changes

### UI Presentation Layer

#### [MODIFY] [menu_detail.xml](file:///C:/Users/amaga/Desktop/AndroidProject/gemini-contactcard-scanner/app/src/main/res/menu/menu_detail.xml)
- Add menu item `action_rescan` ("Rescan from Images") to `ContactDetailActivity` action bar.

#### [MODIFY] [ContactDetailActivity.kt](file:///C:/Users/amaga/Desktop/AndroidProject/gemini-contactcard-scanner/app/src/main/java/com/businesscard/scanner/ui/ContactDetailActivity.kt)
- Handle `action_rescan` menu click.
- Load stored card images (`frontImagePath`, `backImagePath`), run `OcrHelper` and `TextParser.parseSmart` / `GeminiContactParser`, update the card entity in Room database, and reload the contact details view.

## Verification Plan

### Automated Tests
- Run unit tests: `./gradlew :app:testDebugUnitTest`

### Manual Verification
- Deploy app to emulator/device, open a contact with stored card images, tap "Rescan from Images" in the menu, and verify contact details are successfully updated.
