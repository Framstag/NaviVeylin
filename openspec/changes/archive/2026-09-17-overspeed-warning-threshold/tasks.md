## 1. Settings plumbing (spec: map-speed-widget — configurable delta, shared global)

- [x] 1.1 Add `overspeedWarningDeltaKmh: Int = 5` to `AppSettings` (`app/src/main/java/com/naviveylin/data/SettingsStorage.kt`) and verify a `SettingsStorage` unit test round-trips the field through JSON save/load (default 5 applied when the key is absent).
- [x] 1.2 Add `overspeedWarningDeltaKmh: Int = 5` to `AutoSettings` (`core/src/main/java/com/naviveylin/core/AutoSettings.kt`) so the AA process reads the same global value.
- [x] 1.3 Map the field both directions in `AutoSettingsMapping` (`toAutoSettings` / `toAppSettings`) and verify mapping unit tests cover the round-trip incl. the new field.
- [x] 1.4 Verify the app and core modules compile after the plumbing (`./gradlew :app:compileMobileDebugKotlin :core:compileDebugKotlin`).

## 2. Phone widget + settings sheet (spec: map-speed-widget, location-options-ui)

- [x] 2.1 Parameterize `isSpeedOverLimit(current, max, delta)` in `SpeedWidget.kt` with the unified rule `current >= max + delta` (unknown-limit guard kept), removing the hardcoded 5, and verify the pure-function tests cover the `>=` boundary (55/50 with delta 5 warns), delta 0 warns at the limit, and unknown limit never warns.
- [x] 2.2 Thread the delta through `SpeedWidget` (new parameter, default 5) and its call sites in `MapCanvasScreen`; verify `SpeedWidgetTest` still passes with the default-delta behavior changes (the old "exactly 5 over = normal" case flips to warning).
- [x] 2.3 Add the overspeed-warning control to `LocationOptionsOverlay` sheet content: a `Slider` 0..30 with `steps = 29`, value snapped to whole km/h, live label in km/h (spec: location-options-ui — 1 km/h precision, current value shown); wire a new `onSetOverspeedWarningDelta: (Int) -> Unit` callback.
- [x] 2.4 Add `MapCanvasViewModel.onSetOverspeedDelta` persisting via the `load → copy → save` pattern (mirror `onToggleLaneHints`) and pass the current delta from state into the sheet; verify the value survives a settings reload.
- [x] 2.5 Extend `SpeedWidgetTest` / sheet-level Compose tests for the slider row (value label updates, 0..30 selectable) and verify the full `./gradlew test` suite for the app module still passes.

## 3. Android Auto warning + picker (spec: auto-map-layout)

- [x] 3.1 Add `overLimitDeltaKmh: Int = 5` to `SurfaceIndicators.draw` and `drawSpeedBadge`, replacing the rounded `>`-comparison with the shared `>= max + delta` rule; keep the unknown-limit guard.
- [x] 3.2 Update `NavigationScreen` and `FreeDrivingScreen` to read the delta from shared settings at init (mirror of `autoZoomEnabled`) and pass it to `SurfaceIndicators.draw`.
- [x] 3.3 Add the "Overspeed warning" row to `PreferencesScreenMapper.rows` (key `overspeedWarningDeltaKmh`, title + current value) and a `toggle`-independent select handler; verify mapper unit tests cover the new row and its value text (0..30).
- [x] 3.4 Create `OverspeedDeltaPickerScreen` (ListTemplate, one row per whole km/h 0..30, current value marked, selection persists via `AutoSettingsProvider.save` and finishes back to preferences) and wire it from `PreferencesScreen` when the row is tapped; verify picker tests cover rendering 31 entries, marking the current delta, and the persisted selection.
- [x] 3.5 Verify the auto module compiles and its unit tests pass (`./gradlew :auto:compileDebugKotlin` + `:auto:testDebugUnitTest`).

## 4. Guidelines (config rule: change supersedes a guideline updates it)

- [x] 4.1 Update `guidelines/UI.md` §8: replace the fixed "(5+ km/h over)" overspeed rule with the configurable delta (`current >= max + delta`, 0–30 km/h, default 5, one global value shared with Android Auto) and verify the text reads consistently with specs `map-speed-widget` / `auto-map-layout`.

## 5. Integration verification

- [x] 5.1 Verify all unit test modules pass (`./gradlew test`) and the full mobile debug build succeeds (`./gradlew :app:assembleMobileDebug`). (Note: 3 unrelated pre-existing failures in `MapCanvasViewModelAutoZoomCommitTest` + `MapCanvasViewModelNavEndRestoreTest` stem from other in-flight working-tree changes — zoom convergence / nav-end restore — not this change; remaining 826 tests pass.)
- [x] 5.2 On-device (phone emulator): open the options sheet, drag the slider across 0..30 (label tracks each whole km/h), set e.g. 3, observe the badge warns at `max + 3` in follow mode and navigation (light + dark schemes), and the value persists after app restart. **Paused — no emulator/device attached.**
- [x] 5.3 AA emulator / head unit: preferences row shows the delta, the picker lists 0..30 with the current value marked, a new selection persists to the phone's shared settings, and the navigation + free-driving speed badges warn at the configured delta (fresh install with no settings file warns at `max + 5` by default). **Paused — no emulator/device attached.**
