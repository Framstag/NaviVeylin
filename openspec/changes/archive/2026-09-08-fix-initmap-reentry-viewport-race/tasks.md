# Tasks: fix-initmap-reentry-viewport-race

Specs: `viewport-persist` (ADDED requirement "Re-entry supersedes an in-flight map init"). Design: see design.md — D1 job cancellation, D2 re-arm-before-cancel ordering, D3 gated-suspension tests.

## 1. Implementation

- [x] 1.1 Add a private `initJob: Job?` field to `MapCanvasViewModel` and, in `initMap`, re-arm `viewportRestored = false` first, then `initJob?.cancel()`, then assign `initJob = viewModelScope.launch { ... }` (existing body unchanged); verify `:app:compileMobileDebugKotlin` compiles (or build-app skill)

## 2. Regression tests

- [x] 2.1 Add a gated `FavoriteRepository` double to `MapCanvasViewModelViewportRestoreTest` (open subclass overriding `init()` to suspend on a `CompletableDeferred` before returning) and a re-entry test: block the gate, `initMap(pathB)` while `initMap(pathA)` is suspended, release the gate, drain the dispatcher — verify `_uiState.viewport` equals pathB's restored viewport, `viewportRestored` is true for B, and no stale pathA viewport/renderer assignment lands (spec scenario "Re-entry during restore does not clobber the new restore"); the gating landed as `FirstDispatchGatedDispatcher` on `viewportStorage.ioDispatcher` (holds the first load, later dispatches inline) — `FavoriteRepository.init` is final, so the repository override was not possible; discrimination verified: test fails with the fix disabled, passes with it
- [x] 2.2 Add a save-guard test: with pathB's init still gate-suspended, call `saveViewport()` and verify the seeded viewport file keeps its prior content and `saveViewport` logs the skip (spec scenario "Lifecycle save during the re-entry window")
- [x] 2.3 Verify the full viewport-restore test class passes: `./gradlew :app:testMobileDebugUnitTest --tests "*MapCanvasViewModelViewportRestoreTest*"` (3 existing + 2 new tests; keep the default Robolectric sandbox — no `@Config` — per the JNI stub classloader rule)
- [x] 2.4 Sync the renderer to the restored viewport in `initMap` (`renderer.prepareViewport(vp...)` right after renderer creation) so init-time dark/style/favorites renders use the restored viewport, not the renderer default (mag 5) — fixes the low-zoom first frame after re-entry (user repro: basemap reload + switch back shows low zoom until refresh; spec scenario "Init-time renders use the restored viewport"); verify with a test asserting the FIRST native render is at 2^restoredMag (4096), not 2^5 (32) — discrimination proven (fails with the fix disabled; `FakeOSMScoutClient` gained a `renderMags` history)

## 3. Verification

- [x] 3.1 Run the full mobile unit suite `./gradlew :app:testMobileDebugUnitTest` (or run-tests skill) and verify all tests pass — single-init paths (spec scenario "Single initialization unaffected") covered by the existing suite (699 tests, 105 classes, 0 failures)
- [x] 3.2 On-device sanity: after a map download (or basemap reload) returns to the map, the restored viewport holds and no stale region flashes; check `adb logcat -s NaviVeylin` for `saveViewport: skipped` only within init windows. User repro (2026-09-08): basemap reload + switch back showed low zoom until refresh — root-caused to the init-time dark/style render at the renderer default (mag 5), fixed by task 2.4; re-verified on device 2026-09-08: first frame after re-entry at restored zoom, no low-zoom flash
