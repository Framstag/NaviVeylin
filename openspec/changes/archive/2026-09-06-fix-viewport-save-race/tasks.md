## 1. Spec delta (spec: viewport-persist)

- [x] 1.1 Add the two ADDED requirements to `openspec/changes/fix-viewport-save-race/specs/viewport-persist/spec.md`: "Reject invalid viewport states on save" and "Apply restored viewport before wiring the view-change listener" (WHEN/THEN scenarios per config rules)

## 2. Reorder initMap (spec: viewport-persist — apply restored viewport before wiring the listener)

- [x] 2.1 Move the restore computation (`viewportStorage.load` + `getDatabaseBoundingBox` + clamp) in `MapCanvasViewModel.initMap` to BEFORE the renderer is created, so `mapRenderer` is null during the suspension and an early `setScreenSize`/`renderMap` cannot submit a render with the uninitialized default viewport (design D1)
- [x] 2.2 Apply the restored viewport to `_uiState` (`copy(viewport = vp, isLoading = false)`) immediately after renderer creation and BEFORE `renderer.addViewChangeListener`, with no suspension in between (design D1)
- [x] 2.3 Verify `:app:compileMobileDebugKotlin` compiles

## 3. Guard ViewportStorage.save (spec: viewport-persist — reject invalid viewport states on save)

- [x] 3.1 Add `ViewportState.isValid()` (NaN/infinity, lat ∉ [-90,90], lon ∉ [-180,180], mag NaN/inf/<=0, angle NaN/inf) (design D2)
- [x] 3.2 `ViewportStorage.save` rejects invalid states with a warning before touching the file (design D2)
- [x] 3.3 Verify `:app:compileMobileDebugKotlin` compiles

## 4. Regression tests (spec: viewport-persist — both new requirements)

- [x] 4.1 `ViewportStorageTest`: `saveRejectsNaNCoordinates`, `saveRejectsOutOfRangeCoordinates`, `saveRejectsInvalidMagnification`, `saveRejectedStateDoesNotClobberExistingFile` (race scenario), `isValidRejectsUninitializedState` (design D3)
- [x] 4.2 Run `./gradlew :app:testMobileDebugUnitTest --tests "com.naviveylin.data.ViewportStorageTest"` — 12 tests, 0 failures
- [x] 4.3 Add `MapCanvasViewModelViewportRestoreTest` — the exact interleaving (setScreenSize firing while `initMap` is suspended at `viewportStorage.load`, via a gated test dispatcher): asserts no render is submitted with the default viewport, exactly one render with the restored center, and the persisted file keeps the restored center. Verified it FAILS on the buggy ordering (`expected:<1> but was:<2>`) and passes with the fix

## 5. Full verification

- [x] 5.1 Run the full unit suite `./gradlew :app:testMobileDebugUnitTest` — all tests pass, including `MapCanvasViewModelSharedLocationTest` and `MapCanvasViewModelStyleTest` (they exercise `initMap` end-to-end)
- [x] 5.2 Build verification: `./gradlew :app:assembleMobileDebug` (arm64-v8a) compiles
- [x] 5.3 On-device/emulator check: start the app, verify `viewport-<map>.json` keeps its center after restart, and POI search centers on the restored viewport (not the default)
