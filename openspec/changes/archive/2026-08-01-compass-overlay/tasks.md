## 1. Data Layer — Extend AppSettings

- [x] 1.1 Add `freeFormNorthUp: Boolean = true` and `navNorthUp: Boolean = false` fields to `AppSettings` data class in `SettingsStorage.kt`
- [x] 1.2 Verify existing `followMode` and `autoZoomEnabled` fields are unchanged (backward compat with `ignoreUnknownKeys`)

## 2. ViewModel — Orientation Logic

- [x] 2.1 Add `freeFormNorthUp` and `navNorthUp` state fields to `MapCanvasUiState`
- [x] 2.2 Add `onSetFreeFormOrientation(northUp: Boolean)` and `onSetNavOrientation(northUp: Boolean)` methods to `MapCanvasViewModel`
- [x] 2.3 Implement effective map angle computation in ViewModel: when north-up → 0°, when follow-direction → use GPS bearing (free-form) or nav bearing (navigation)
- [x] 2.4 Wire orientation settings persistence: load on init, save on every toggle via `settingsStorage.save()`
- [x] 2.5 Update `onToggleFollowMode` to also apply orientation when follow mode activates

## 3. UI — Bottom Sheet

- [x] 3.1 Rewrite `LocationOptionsOverlay.kt`: replace `DropdownMenu` with `ModalBottomSheet` from Material 3
- [x] 3.2 Add orientation section to bottom sheet: "North up" / "Follow direction" as radio buttons or segmented buttons
- [x] 3.3 Show free-form orientation controls when not navigating, navigation orientation controls when navigating
- [x] 3.4 Keep existing "Map follows position" toggle and "Auto zoom" toggle (nav only) in the sheet
- [x] 3.5 Update `MapCanvasScreen.kt` call site: pass new orientation state and callbacks to `LocationOptionsOverlay`

## 4. Build & Verify

- [x] 4.1 Run `./gradlew :app:assembleDebug -Pandroid.injected.build.abi=arm64-v8a` and verify compilation
- [x] 4.2 Run existing unit tests and verify they pass
- [x] 4.3 Add unit tests for orientation logic in `MapCanvasViewModel` (effective angle computation per mode)
- [x] 4.4 Add unit tests for `AppSettings` serialization with new fields
- [x] 4.5 Verify existing `location-options-ui` spec scenarios still pass (follow mode toggle behavior unchanged)
