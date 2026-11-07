# Tasks

## 1. The red case

- [x] 1.1 Add `MapCanvasViewModelBrowseReCenterTest#aCanvasSizeChangeAloneReDerivesTheOffset`: canvas 0×0 with an off-centre viewport and a fix already in state (offset NaN, no arm), then only `setScreenSize(SCREEN_W, SCREEN_H)`; assert the arm starts and the button appears past the dwell. Verify: `./gradlew :app:testMobileDebugUnitTest --tests "com.naviveylin.ui.map.MapCanvasViewModelBrowseReCenterTest"` — red on the base, `tests=24 failures=1`, XML in `evidence/red-on-head.xml`.

## 2. The fix

- [x] 2.1 `MapCanvasUiState` gains `canvasWidthPx` / `canvasHeightPx` (default 0). Verify: the two fields are read by the derivation in 2.3 and by nothing else — `grep -rn 'canvasWidthPx\|canvasHeightPx' app/src/main` prints the state, `setScreenSize` and the derivation only.
- [x] 2.2 `setScreenSize` copies the two metrics into `_uiState` when they change; the renderer push and the first-render trigger are unchanged. Verify: `MapCanvasViewModelInitThreadingTest` and `MapCanvasViewModelStartMapRecordingTest` (both drive `setScreenSize`) stay green in the focused run of 4.1.
- [x] 2.3 `refreshBrowseReCenter` reads `state.canvasWidthPx` / `state.canvasHeightPx` instead of `screenWidth` / `screenHeight`. Verify: 1.1 turns green; `offsetHelperReportsUnknownInputsAsNaN` stays green (the helper's own signatures are untouched).

## 3. Documents

- [x] 3.1 `guidelines/UI.md`, section 7a, states that the canvas metrics are part of the observed state. Verify: `tools/check-doc-routes.sh` (no route changes, the section is already routed from `AGENTS.md`), and the structural read of the section.
- [x] 3.2 The `map-modes` delta carries the sentence and the scenario with its `Case` line. Verify: `openspec validate fix-a-canvas-size-change-re-derives-the-browse-offset --strict`.

## 4. Verification

- [x] 4.1 Focused suites — the rule and the five suites that name it: `./gradlew :app:testMobileDebugUnitTest --tests "com.naviveylin.ui.map.MapCanvasViewModelBrowseReCenterTest" --tests "com.naviveylin.ui.map.MapCanvasViewModelFixQualityTest" --tests "com.naviveylin.ui.map.MapCanvasViewModelFollowModeTest" --tests "com.naviveylin.ui.map.MapCanvasViewModelNavEndRestoreTest" --tests "com.naviveylin.ui.map.MapCanvasViewModelVehicleAnchorTest" --tests "com.naviveylin.ui.map.MapReCenterButtonOverlayTest"`; copy the XML into `evidence/`.
- [x] 4.2 The project's gate, once, from the change root after the archive: `./tools/loop/gate.sh` — every one of its 23 suites passed, and its own summary is `evidence/gate.log` with the per-suite tallies under `evidence/gate-tallies/`.
