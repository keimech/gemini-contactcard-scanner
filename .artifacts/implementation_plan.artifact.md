# Continuous Scan Workflow Enhancement

This plan outlines significant improvements to the user experience and data integrity during the batch scanning process in `ScanActivity`. The goal is to implement a multi-stage workflow that separates high-speed capture (scanning) from careful review and saving, addressing performance bottlenecks and improving user confidence.

## User Review Required

**UX/Architecture:**
1.  The transition from continuous scanning mode to the confirmation screen must be seamless. I will manage this by updating the `ScanActivity`'s internal state and controlling the visibility of different UI components (e.g., replacing a single button with a full review list view).
2.  I propose creating a temporary, thread-safe data structure in the ViewModel to hold all extracted card data during an ongoing scan session. This will ensure that scanning can proceed without blocking or losing parsed results before the user reviews them.

**Functional Flow:**
1.  **Scan Phase (Initial):** User captures cards continuously. The system runs OCR/Gemini Nano in the background, and *all* successfully parsed card data are added to a temporary list buffer. The UI shows live capture feedback and a progress count of items scanned.
2.  **Mode Change:** When scanning stops or the user explicitly taps "End Scan," the button text changes from "Save" (or similar) to **"Scan Ended (Confirm)"**. This triggers the transition to the Review Phase.
3.  **Review/Confirmation Phase:** The `ScanActivity` switches focus to a dedicated review UI containing all buffered cards (`List<BusinessCard>`). Here, the user can:
    *   Review Name, Phone, etc., for each card (read-only display).
    *   Manually edit details or mark them as needing correction.
    *   Confirm and **Save** the selected/modified batch of cards to the database using `BusinessCardRepository`.

## Open Questions

1.  Should the confirmation screen allow users to filter the list of scanned items (e.g., "Show only incomplete records")?
2.  When saving, if a card already exists in the database, should we automatically prompt the user with a merge/update dialog, or simply overwrite it by default?

## Proposed Changes

### 1. Data Layer (`BusinessCardViewModel.kt`)

The ViewModel needs to manage the temporary state of scanned cards and handle batch processing safely.

#### [MODIFY] BusinessCardViewModel.kt
*   **Thread-Safe Buffer:** Introduce a `MutableStateFlow<List<BusinessCard>>` (or similar thread-safe structure) to hold all parsed cards during an active scan session. This list must be cleared upon successful saving or failure.
*   **Modified State Logic:** Refactor the state machine logic to handle `ScanStatus.SCANNING`, `ScanStatus.PAUSED_FOR_REVIEW`, and `ScanStatus.IDLE`.

#### [MODIFY] BusinessCardRepository.kt (Optional, if save logic is here)
*   Ensure that saving multiple cards from a batch is atomic or at least provides clear feedback on success/failure for the entire batch.

### 2. OCR/Scanning Logic (`GeminiContactParser.kt` & `OcrHelper.kt`)

The parsing results must now be fed into the ViewModel's buffer instead of potentially triggering an immediate UI update or database save.

#### [MODIFY] GeminiContactParser.kt
*   Ensure that when a parse is successful, it returns the fully populated data object (`BusinessCard` draft) to the calling function (likely in `ScanActivity` or `BusinessCardViewModel`). This decoupling is crucial for batching.

### 3. UI Layer (`ScanActivity.kt`) - **Primary Focus**

This activity will undergo the most changes, managing the state transition and displaying the confirmation list.

#### [MODIFY] ScanActivity.kt
*   **State Management:** Implement logic to track if the app is in continuous scan mode and manage the visibility of UI elements (e.g., `RecyclerView` for review vs. Camera View).
*   **Button Behavior:** Update the button's click listener: when continuous scanning is active, changing it to "Scan Ended (Confirm)" must transition the screen state.
*   **Confirmation UI:** Implement a `ListAdapter`/`RecyclerView.Adapter` that binds to the ViewModel's temporary card list, displaying key details and allowing for individual edit/selection tracking.

### 4. Core Dependencies

I will ensure all necessary Coroutine and State management utilities (like `MutableStateFlow`) are correctly imported and used for thread safety.
## Verification Plan

### Automated Tests
- Create unit tests in the ViewModel layer to verify that parsing results accumulate correctly in the internal list buffer (`test/BusinessCardViewModelTest.kt`).
- Write integration tests for `ScanActivity`'s state machine to ensure transitions between scanning, paused, and confirming states occur correctly when button handlers are called.

### Manual Verification
- Run the continuous scan flow on an Android emulator or physical device with several test cards to verify:
    1.  The intermediate list accumulation is visible/testable programmatically.
    2.  The UI transition upon stopping scanning feels natural and guides the user effectively.
    3.  Saving a batch of cards correctly populates the database and clears the temporary buffer.