# Tasks — fix-display-dark

## 1. Keep-Screen-On Lifecycle Implementation

- [x] 1.1 In `MapCanvasScreen.kt`, replace the `DisposableEffect(navState.isNavigating, state.keepScreenOn)` block with a `LifecycleEventObserver` registered on the screen's lifecycle owner (registration managed by `DisposableEffect(Unit)`): `ON_RESUME` → `activity.window.addFlags(FLAG_KEEP_SCREEN_ON)` when `state.keepScreenOn`; `ON_PAUSE` → `activity.window.clearFlags(FLAG_KEEP_SCREEN_ON)`; remove the observer in `onDispose` (spec: always-on-display — "Screen stays on while app is in the foreground"; design D1-A)
- [x] 1.2 Add a small `DisposableEffect(state.keepScreenOn)` (no `isNavigating` gate) that applies/clears `FLAG_KEEP_SCREEN_ON` immediately when the setting toggles while foregrounded; keep `onDispose` clearing the flag (spec: always-on-display — "Screen turns off when setting is disabled while app is running" / "Screen stays on when setting is enabled while app is running"; design D2-A)
- [x] 1.3 Remove the `isNavigating` gating entirely; verify no other references to the old effect remain (grep `KEEP_SCREEN_ON`, `navState.isNavigating` in `MapCanvasScreen.kt`) (spec: always-on-display — "Screen stays on while app is running")

## 2. Tests

- [x] 2.1 Add/extend Robolectric Compose test for keep-screen-on in `MapCanvasScreen`: flag present on window when composed with `keepScreenOn = true`; absent when `keepScreenOn = false`; flag cleared on `ON_PAUSE` and re-applied on `ON_RESUME` (drive lifecycle via `LifecycleRegistry`); flag cleared when the composable leaves composition (spec: always-on-display scenarios — "Screen stays on while app is running" / "Screen stays on after switching back from another app" / "Screen turns off when app goes to background"; design D1-A, Risks)
- [ ] 2.2 Run `./gradlew :app:testDebugUnitTest` — full suite green, no regression in existing `MapCanvasScreen`/route-panel tests (apply rule: all tests pass)

## 3. Build & Device Verification

- [ ] 3.1 Build debug APK: `./gradlew :app:assembleDebug -Pandroid.injected.build.abi=arm64-v8a` (apply rule: build compiles)
- [ ] 3.2 Device — foreground stay-on: with "Keep screen on" enabled, browse the map (no navigation) and wait — screen SHALL NOT dim (spec: always-on-display — "Screen stays on while app is running")
- [ ] 3.3 Device — app switch: with keep-screen-on active, switch to another app, wait past the dim timeout, switch back — screen SHALL stay on after returning (spec: always-on-display — "Screen stays on after switching back from another app")
- [ ] 3.4 Device — disable/enable: toggle "Keep screen on" off while app foregrounded → screen SHALL dim per system timeout; toggle on → screen SHALL stay on (spec: always-on-display — disable/enable scenarios)

## 4. Finalize

- [ ] 4.1 Verify no docs contradict the new behavior (search `always-on-display`, "during navigation" in `docs/`, `README`, `AGENTS.md`)
