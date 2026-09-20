# Tasks

## 1. Shared lifecycle hook (spec: auto/preferences)

- [x] 1.1 Add optional `onReVisible: (() -> Unit)? = null` parameter to `Screen.observeSettingsLifecycle` in `auto/.../SettingsLoadGuard.kt`: the observer's `onStart` invokes it before `guard.onLifecycleStart()`, `onDestroy` keeps cancelling the scope; verify `SettingsLoadGuardTest` (or the existing lifecycle-factory tests) covers the hook being invoked on start and NOT after destroy
- [x] 1.2 Update/extend the guard lifecycle unit tests: hook fires on re-visibility even when content is already loaded; no crash when the hook throws; onDestroy cancels so the hook cannot fire post-destroy (verify `./gradlew :auto:testDebugUnitTest` green)

## 2. PreferencesScreen re-read on re-visibility (spec: auto/preferences, auto-map-layout)

- [x] 2.1 Wire the new hook into `PreferencesScreen`: pass `::loadSettings` as `onReVisible`; verify unit test: returning to the screen (after a picker pop-back) triggers a fresh `settingsProvider.load()` and the vehicle-position rows render the NEW anchor label, not the stale snapshot
- [x] 2.2 Verify guard contract preserved on the re-read path: load failure during re-read shows the error row with Retry (existing `PreferencesScreenTest` cases keep passing; add the re-read-failure case if missing)

## 3. Pickers persist before pop (spec: auto-map-layout — "Anchor selection survives immediate dismissal")

- [x] 3.1 `VehicleAnchorPickerScreen`: change `onSelect` to await `persistSelection` on the screen scope and only then `screenManager.pop()`; on save failure keep the picker visible (log + guard error/retry, no silent loss); verify unit test: `persistSelection` failure leaves the screen open and a successful persist triggers the pop callback
- [x] 3.2 Same persist-then-pop hardening in `OverspeedDeltaPickerScreen` (mirror pattern, keeps its unit-test seam); verify its picker tests still pass and a new case asserts pop happens only after save success
- [x] 3.3 Verify double-tap cannot double-pop: while a persist is in flight, further row taps are ignored (car-app row disabled or an in-flight guard); unit test with a test dispatcher (spec: auto-map-layout)

## 4. FreeDrivingScreen resume reload (spec: auto/free-driving)

- [x] 4.1 Add an `onStart` settings reload to `FreeDrivingScreen` that re-runs the existing settings load (auto-zoom, overspeed, anchor): on anchor id change call `rendererGate.setFollowAnchor(new)` + `requestRender`, and re-derive the street-label placement from the anchor; on re-read failure keep the previous anchor and log (spec: "Free-driving anchor survives a settings re-read failure")
- [x] 4.2 Unit tests (Robolectric): resume reload applies a changed free-driving anchor (renderer receives it), no-op when unchanged, failure keeps the previous anchor; verify `./gradlew :auto:testDebugUnitTest` green

## 5. NavigationScreen resume reload (spec: auto/navigation-view)

- [x] 5.1 Move the settings re-read out of the `hasStateChanged` gate: `startObserving` performs an unconditional settings load per resume (diff on anchor/lane-hints/orientation/auto-zoom/overspeed, apply anchor via `rendererGate.setFollowAnchor` only on id change, then `requestRender`); keep the existing state-changed reload for live re-application
- [x] 5.2 Unit tests: a new routing anchor from the settings surfaces on the navigation screen without a navigation-state change (spec: "Anchor applies on resume without a maneuver change"); unchanged anchor produces no renderer call; existing `NavigationScreenTest` cases keep passing

## 6. Guideline (guidelines/UI.md)

- [x] 6.1 Extend the Android Auto car-screen rule in `guidelines/UI.md` (the settings/loading rule added by the archived `fix-aa-settings-stuck-loading`): settings-bearing car screens must re-READ persisted settings on re-visibility, not just re-render the last snapshot; picker dismissal must not lose the write

## 7. Build and unit verification

- [x] 7.1 Run `./gradlew :auto:testDebugUnitTest` and `./gradlew test` — all units green, including unchanged phone anchor suites (`FollowAnchorFramingTest`, `MapCanvasViewModelVehicleAnchorTest`); fix any regressions (rules: existing tests still pass) — **verified 2026-09-17**: `./gradlew test` aggregate `BUILD SUCCESSFUL` (app mobile executed fresh); final four-task run `--no-build-cache`: both app flavors REALLY executed fresh, `exit=0`, no failure line (3m50s); `:auto` 434 tests green post-edit (one known flake `AutoMapRendererTest.marginRenderRequestHonoursThrottleParity` — in-flight `fix-follow-vehicle-jumps` WIP, passes standalone, TODO §18); `:core` green; named phone anchor gates `FollowAnchorFramingTest` + `MapCanvasViewModelVehicleAnchorTest` green on BOTH flavors; earlier `:app:testAutomotiveDebugUnitTest` fork crash was flaky infra — full 136-class rerun green (TODO §18)
- [x] 7.2 Build `:app:assembleMobileDebug` and `:app:assembleAutomotiveDebug` — compile clean without warnings (use build-app skill) — **verified 2026-09-17: both BUILD SUCCESSFUL (mobile 1m21s, automotive 19s); no compiler warnings in the touched :auto files**

## 8. On-device verification (spec: auto-map-layout, auto/free-driving, auto/navigation-view)

- [x] 8.1 On head unit / car-connected phone (GPX replay): open Preferences → pick a bottom-right preset → return; confirm the row shows the new label and the follow map re-frames at the preset during the active session, for routing AND free driving, without leaving the session; check `adb logcat -s NaviVeylin` for the settings reload + follow-anchor lines — **verified 2026-09-17 (AAOS emulator, free driving + GPX replay)**: persisted `autoFreeDrivingAnchorId=bottom-center` applied live mid-session — `FreeDrivingScreen: applied free-driving anchor BOTTOM_CENTER` + `settings loaded: … anchor=BOTTOM_CENTER` on the active session (no restart); the map re-framed at the preset (marker at the anchor). Note: value written to the settings JSON (picker row display not exercised — same load path); routing anchor persisted separately (`bottom-right`) and loads on the navigation screen
- [x] 8.2 Pick a preset and pop back immediately, then restart the car app session: the value from the previous session SHALL still be applied (spec: "Anchor selection survives immediate dismissal") — this also answers the design.md open question about the persist race on the user's device — **verified 2026-09-17 (AAOS emulator)**: `autoFreeDrivingAnchorId=bottom-center` survived two force-stop/relaunch cycles AND an APK reinstall (`adb install -r` kept data); on every cold start the free-driving screen re-applied the anchor from the settings file (`applied free-driving anchor BOTTOM_CENTER` after restart)

## 9. Change sequencing

- [x] 9.1 Record the archive order: `vehicle-position-presets` (after its `fix-phone-vehicle-anchor-framing` prerequisite, per its tasks 8.1/8.2) archives BEFORE this change; `anchor-per-surface-visible-area` archives after; verify with `openspec validate` before archiving and note the INFO-level MODIFIED-delta lines until the prerequisite archives
