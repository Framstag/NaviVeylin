# Tasks: Align phone map controls across standard and routing views

## 1. Shared right-widget column composable

- [x] 1.1 Extract `MapRightWidgetColumn` (internal, in `MapCanvasScreen.kt`): column content compass → speed widget → optional location-options slot → zoom controls, `horizontalAlignment = CenterHorizontally`, positioning via `modifier` param (design D1). Verify: `./gradlew :app:compileMobileDebugKotlin` succeeds.
- [x] 1.2 Fold `MapLocationZoomBlock` into the new composable (gear passed via the location-options slot, zoom as the column's last element) and delete the old block. Verify: `./gradlew :app:compileMobileDebugKotlin` succeeds with no unused-symbol errors.

## 2. Standard view restructure

- [x] 2.1 Landscape: replace the top-right (compass + speed) and bottom-right (gear + zoom) clusters with one bottom-anchored `MapRightWidgetColumn` (`align(BottomEnd)`, `end = 8.dp`, `bottom = 44.dp`, `navigationBarsPadding()`, `verticalScroll`), order compass → speed → gear → zoom (specs: landscape-layout, map-canvas-screen). Verify: compile; on-device check shows a single column at bottom-right with zoom at the bottom.
- [x] 2.2 Portrait: replace the top-right column with the same bottom-anchored `MapRightWidgetColumn` and padding (specs: map-canvas-screen, compass-button). Verify: compile; on-device check shows the column at bottom-right with zoom at the bottom.

## 3. Routing view zoom controls

- [x] 3.1 Append `Spacer(8.dp)` + `ZoomControls` after the speed widget in the routing column, directly above `NavigationStateOverlay` (spec: zoom-controls "Zoom controls shown during navigation"). Wire handlers with `attributionInteractionTick++`, `viewModel.zoomIn()/zoomOut()`, `viewModel.renderMap()`, `animateDiscreteZoomToCenter()` — deliberately no `disengageFollowMode()` (design D3). Verify: compile; on-device check during navigation shows zoom buttons above the routing status.
- [x] 3.2 Pass the `BoxWithConstraints` `isLandscape` into the routing `ZoomControls` so landscape shows a horizontal row and portrait a vertical column (design D4, spec: zoom-controls). Verify: on-device check in both orientations during navigation.

## 4. Tests

- [x] 4.1 Add a Compose test for the shared column (harness pattern like `MapReCenterButtonOverlayTest`): standard column renders compass, speed, gear, and zoom; routing column renders compass, speed, zoom and no gear; zoom is the last element. Verify: `./gradlew test` passes.
- [x] 4.2 Verify `MapCanvasViewModelAutoZoomPauseTest` covers button-zoom-during-navigation suspending auto-zoom (via `updateMagnification`); extend if the navigation case is missing. Verify: test passes.
- [x] 4.3 Run the full unit test suite. Verify: `./gradlew test` is green.

## 5. Build and on-device verification

- [x] 5.1 Build the phone debug APK. Verify: `./gradlew :app:assembleMobileDebug` succeeds.
- [ ] 5.2 On-device/emulator smoke check: standard view (portrait + landscape) shows one bottom-anchored right column with zoom at the bottom below all other controls; navigation shows zoom above the routing status bar; zooming during navigation keeps follow mode, suspends auto-zoom, and surfaces the re-center button. Verify: manual walkthrough.
