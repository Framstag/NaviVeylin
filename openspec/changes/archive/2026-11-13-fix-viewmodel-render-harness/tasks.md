# Tasks

## 1. Diagnose the empty harness

- [x] 1.1 Add `app/src/test/java/com/naviveylin/ui/map/MapCanvasViewModelFrameHarnessTest.kt` with the
  harness only: build the ViewModel against `FakeOSMScoutClient`, assign
  `defaultDispatcher`/`rendererDispatcher` from `MainDispatcherRule`, call `initMap`, call `setScreenSize`,
  advance. Run it and record, from the log, which of the three lines is printed — `initMap: viewport mag=…`,
  `MapRenderer created`, `enqueueRenderJob skipped: screen 0x0` — or the failure before the renderer exists
  (design D5). Verify: `./gradlew :app:testMobileDebugUnitTest --tests
  "com.naviveylin.ui.map.MapCanvasViewModelFrameHarnessTest"` runs and the record is quoted into the change's
  `apply-evidence.md`.

## 2. The harness case

- [x] 2.1 Add `aFrameLandsThroughTheFrameCollector`: after the drive of 1.1, assert `renderViewport` is
  non-null and its `mag` equals the magnification the fake client recorded (`renderMags.last()`), and
  `renderedBitmap` matches the emitted bitmap's dimensions (spec: map-render — A frame lands through the
  collector). Verify: the case is green, and it fails when `setScreenSize` is omitted (the finding's own
  condition) — record that red run in `apply-evidence.md`.
- [x] 2.2 `initMap` reaches the case without a real map database: 1.1 measured no refusal — the fake opens the
  path and builds the renderer — so nothing more was needed. Recorded in `apply-evidence.md` §1.1. Original:
  Make `initMap` reachable for the case without a real map database if 1.1 shows it refusing: pass a
  map path the fake opens, and record in the case why the fake stands in for the file. Verify: the case needs
  no `files/maps` directory and runs in the existing Robolectric sandbox (spec `unit-test-suite-runtime`).

## 3. Move the walk and route-fit cases onto the harness

- [x] 3.1 Replace `publishRenderedFrameForTest` in `MapCanvasViewModelZoomWalkTest.walkToCompletion` with the
  dispatcher drive, keeping every existing assertion (design D2). Verify: the class is green.
- [x] 3.2 Do the same in `MapCanvasViewModelRouteFitTest.fitAfterALandedWalkIsRenderedAtTheFitMagnification`
  (`:227-240`). Verify: the class is green.
- [x] 3.3 Add the harness case `a walk step is the magnification the renderer rendered` (spec: map-render —
  A walked step is the magnification the renderer rendered). Verify: green, and red when the walk's step is
  compared against a step the harness invents.

## 4. Retire the hook

- [x] 4.1 Delete `MapCanvasViewModel.publishRenderedFrameForTest` (`MapCanvasViewModelCamera.kt:392-403`).
  Verify: `grep -rn publishRenderedFrameForTest app/` returns nothing and both case classes stay green.

## 5. Record the recipe

- [x] 5.1 Add the harness rule to `guidelines/BuildTests.md` §6: `initMap` → `setScreenSize` →
  `rendererDispatcher` → collector, why the size gate silently skips a job, and where the fake's pixels come
  from (`FakeOSMScoutClient.render`). Verify: `tools/check-doc-routes.sh` and the documentation gates stay
  green.

## 6. Verification

- [x] 6.1 Run the affected suites and the gate: `./gradlew :app:testMobileDebugUnitTest --tests
  "com.naviveylin.ui.map.*"` then the project gate. Verify: green, and no other render case regressed.
- [x] 6.2 `openspec validate fix-viewmodel-render-harness --strict` and
  `bash tools/check-change-readiness.sh openspec/changes/fix-viewmodel-render-harness`. Verify: valid, and
  `LOOP-CHANGE-READY: ok`.