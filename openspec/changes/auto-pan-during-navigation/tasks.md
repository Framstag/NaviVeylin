# Tasks: Auto Pan During Navigation

## 1. Shared pan handler (spec: auto/map-pan)

- [x] 1.1 Create `MapPanHandler` (new file `auto/src/main/java/com/naviveylin/auto/MapPanHandler.kt`) implementing `PanModeListener` with a `panning` flag: on pan-mode enter suspend auto-zoom (`autoZoomController.suspend()`) and disengage follow (`mapRenderer.setViewport` with the current viewport); on exit re-engage follow (`mapRenderer.reengageFollow()`); verify with new unit tests in `MapPanHandlerTest.kt` (enter disengages follow and suspends auto-zoom, exit re-engages follow, `panning` flag transitions)
- [x] 1.2 Implement `onScroll`: gate on `panning`, convert deltas via `ProjectionUtils.dragDeltaToNewCenterRotated` (angle, zoom, surface size, `mapRenderer.projectionDpi`) and commit with `setViewport`; mirror to `DiagnosticsLog` throttled (MapScreen's `GESTURE_LOG_INTERVAL_MS` pattern); verify with a unit test that a scroll moves the viewport center and that events are ignored when not panning
- [x] 1.3 Implement `onScale`: gate on `panning`, ignore jitter (`abs(scaleFactor - 1f) < 0.01`), accumulate via `mapRenderer.zoomStep`, zoom around the focus (negative focus → surface center) via `ProjectionUtils.zoomAtCursor`, commit with `setViewport(..., newFraction)`; verify with a unit test that a pinch zooms around the focus and jitter is ignored

## 2. Template and strips (spec: auto/navigation-view, auto/free-driving)

- [x] 2.1 Add `panModeListener: PanModeListener? = null` to `NavigationTemplateFactory.buildNavigationTemplate` and `buildFullScreenTemplate`, forwarding to `NavigationTemplate.Builder.setPanModeListener`; verify `NavigationTemplateFactoryTest` covers listener pass-through (and null default)
- [x] 2.2 Add `Action.PAN` (leftmost) to `NavigationScreenActions.navigationMapActionStrip` before the route-description action; verify `NavigationScreenActionsTest` asserts the strip is `[PAN, route-list]`
- [x] 2.3 Add `Action.PAN` (leftmost) to the free-driving map action strip in `FreeDrivingScreen.buildTemplate` before the exit action; verify the free-driving strip test asserts `[PAN, exit]`

## 3. NavigationScreen wiring (spec: auto/navigation-view)

- [x] 3.1 Instantiate `MapPanHandler` in `NavigationScreen` (surface-size lambda from the screen fields), pass it as the `panModeListener` to `buildNavigationTemplate`, and delegate `onScroll`/`onScale` from the `SurfaceCallback`; verify the `:auto` module compiles (`./gradlew :auto:compileDebugKotlin`)
- [x] 3.2 Gate the heading-up angle computation on `!panning` in the GPS handler so the viewport commit block is skipped while panned (auto-zoom suspension already nulls the zoom); verify with a screen test that a panning flag suppresses the viewport commit on a GPS fix
- [x] 3.3 Verify the updated `NavigationTemplateFactoryTest` and `NavigationScreenActionsTest` pass (`./gradlew :auto:testDebugUnitTest --tests "*NavigationTemplateFactoryTest" --tests "*NavigationScreenActionsTest"`)

## 4. FreeDrivingScreen wiring (spec: auto/free-driving)

- [x] 4.1 Instantiate `MapPanHandler` in `FreeDrivingScreen`, pass it as the `panModeListener` to `buildFullScreenTemplate`, and delegate `onScroll`/`onScale` from the `SurfaceCallback`; verify the `:auto` module compiles
- [x] 4.2 Gate the GPS commit block (`setViewport` + `reengageFollow`) on `!panning` in `onGpsFix` — heading-up is always computed there, so without the gate every fix re-engages follow and yanks the map back; verify with a screen test that a panning flag suppresses the commit on a GPS fix
- [x] 4.3 Verify the updated free-driving strip test passes (`./gradlew :auto:testDebugUnitTest --tests "*FreeDrivingScreen*"`)

## 5. Verification

- [x] 5.1 Verify the `:auto` module compiles: `./gradlew :auto:compileDebugKotlin` (or the build-app skill) succeeds without errors
- [x] 5.2 Run the `:auto` unit test suite (`./gradlew :auto:testDebugUnitTest` or the run-tests skill) and verify all tests pass, including the new `MapPanHandlerTest` and screen tests
- [ ] 5.3 On-device check (AA emulator or head unit): free driving — PAN button visible, pan moves the map, map stays panned while the marker keeps moving, exiting pan mode re-engages follow; routing — same during turn-by-turn with the ETA card shown; inspect `adb logcat -s NaviVeylin` for pan events
- [ ] 5.4 Tune PAN button position and the pan-exit re-engage behavior against the emulator/head unit if needed (design D4/D3 open questions); update the constants and re-run the unit tests
