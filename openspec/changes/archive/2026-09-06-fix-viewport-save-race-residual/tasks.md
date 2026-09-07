## 1. Flag + guard (spec: viewport-persist, "Save viewport state")

- [x] 1.1 Add `private var viewportRestored = false` to `MapCanvasViewModel`; reset to `false` at the top of `initMap` (before the coroutine launch); set to `true` immediately after the restored viewport is applied to `_uiState` (the `copy(viewport = vp, isLoading = false)` line).
- [x] 1.2 Guard `saveViewport()`: return early (with a debug log) when `viewportRestored` is false, so a lifecycle save during the restore window never persists the default viewport.

## 2. Regression tests (spec: viewport-persist, "Pause during restore does not clobber")

- [x] 2.1 Add test to `MapCanvasViewModelViewportRestoreTest`: seed a persisted viewport, gate `viewportStorage.ioDispatcher` (existing `GatedDispatcher`), `initMap` + `runCurrent` (suspends at load), call `saveViewport()`, release, `advanceUntilIdle` — assert the viewport file still holds the seeded center (not the default).
- [x] 2.2 Add test: after the restore completes, `saveViewport()` persists the current viewport (file updated with the current center/magnification).

## 3. Verify

- [x] 3.1 Run `:app:testMobileDebugUnitTest` (run-tests skill) — all tests green, including the new regression tests and the existing `MapCanvasViewModelViewportRestoreTest`.
- [x] 3.2 Build `:app:assembleMobileDebug -Pandroid.injected.build.abi=arm64-v8a` (build-app skill) — compiles without errors.
