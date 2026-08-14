## 1. Settings Persistence

- [x] 1.1 Add `keepScreenOn: Boolean = true` field to `AppSettings` data class in `SettingsStorage.kt`

## 2. ViewModel Wiring

- [x] 2.1 Add `keepScreenOn: Boolean = true` to `MapCanvasUiState` data class
- [x] 2.2 Add `onToggleKeepScreenOn(enabled: Boolean)` function to `MapCanvasViewModel` that saves to `SettingsStorage` and updates state
- [x] 2.3 Load `keepScreenOn` from persisted settings in `MapCanvasViewModel.init` alongside existing settings

## 3. UI Toggle

- [x] 3.1 Add "Keep screen on" toggle row to `LocationOptionsSheetContent` composable, gated on `isNavigating`
- [x] 3.2 Wire `keepScreenOn` state and `onToggleKeepScreenOn` callback through `LocationOptionsOverlay` parameters

## 4. Screen-On Flag

- [x] 4.1 Add `DisposableEffect` in `MapCanvasScreen` keyed on `navState.isNavigating` and `uiState.keepScreenOn` that applies `FLAG_KEEP_SCREEN_ON` when both are true and clears it otherwise

## 5. Verification

- [x] 5.1 Build and verify app compiles without errors
