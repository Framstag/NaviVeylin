# Tasks: Fix Two-Finger Rotation

## 1. ProjectionUtils — rotation-aware drag delta

- [x] 1.1 Add `ProjectionUtils.dragDeltaToNewCenterRotated(dx, dy, angle, mag, viewWidth, viewHeight, centerLat, centerLon, dpi)` in `core/src/main/java/com/naviveylin/core/ProjectionUtils.kt` (spec: map-rotation-gesture / Two-finger rotation gesture).
- [x] 1.2 Add unit tests in `app/src/test/java/com/naviveylin/ui/map/ProjectionUtilsTest.kt`: rotated delta reduces to north-up at angle 0; pan right at 90° moves center south; pan down at 90° moves center west (spec: map-rotation-gesture / Two-finger rotation gesture).

## 2. ViewModel — manual rotation disengages follow mode and north-up

- [x] 2.1 Add `MapCanvasViewModel.onManualRotation(angleDeltaRadians: Double)` in `app/src/main/java/com/naviveylin/ui/map/MapCanvasViewModel.kt`: disengage follow mode, clear the active north-up flag (`navNorthUp` when navigating, else `freeFormNorthUp`), then `updateAngle(viewport.angle + angleDeltaRadians)` (spec: map-rotation-gesture / Rotation gesture disengages follow mode and north-up).
- [x] 2.2 Add unit test in `app/src/test/java/com/naviveylin/ui/map/MapCanvasViewModelFollowModeTest.kt` (or new test class): `onManualRotation` clears follow mode and the active north-up flag; re-engaging follow mode does not snap back to north-up (spec: map-rotation-gesture / Rotation gesture disengages follow mode and north-up).

## 3. Gesture handler — multi-touch pan/rotate/zoom

- [x] 3.1 Rewrite the multi-touch branch in `app/src/main/java/com/naviveylin/ui/map/MapCanvasScreen.kt` Phase-2 loop: single finger pans as today; two fingers pan via centroid (rotation-aware via 1.1), rotate via finger-line angle delta, zoom via continuous distance ratio vs gesture start (spec: map-rotation-gesture / Two-finger rotation gesture, Rotation gesture does not conflict with pinch-to-zoom).
- [x] 3.2 Extract the gesture handler into `Modifier.mapGestureHandler(MapGestureCallbacks)` in new file `app/src/main/java/com/naviveylin/ui/map/MapGestures.kt`; `MapCanvasScreen` wires callbacks to the ViewModel (spec: map-rotation-gesture / Two-finger rotation gesture).
- [x] 3.3 Add Compose UI test `app/src/test/java/com/naviveylin/ui/map/MapGestureComposeTest.kt`: two-finger clockwise/counter-clockwise rotation reports angle deltas, constant-distance rotation does not zoom or pan, rotation with small distance jitter reports zoom factors near 1.0, sustained pinch reports a growing zoom factor, pinch reports zoom, render is requested exactly once on gesture end, single-finger drag reports pan (spec: map-rotation-gesture / Two-finger rotation gesture, Rotation gesture does not conflict with pinch-to-zoom).

## 4. Renderer — fast rotated tile composition

- [x] 4.1 `MapRenderer.renderFromTiles` composes cached north-up tiles with a rotation transform (all-4-corners geo bounds) — used for north-up renders and as a fallback (spec: map-rotation-gesture / Two-finger rotation gesture).
- [x] 4.2 `executeRender` uses the tile path when `job.angle == 0.0 || !job.forceFullRender`; `forceFullRender` plumbed through `requestRender`/`submitDebounced`/`PendingRender`/`enqueueRenderJob`/`RenderJob` (spec: map-rotation-gesture / Two-finger rotation gesture).
- [x] 4.3 Debounce split: angle-only changes use `rotateDebounceMs = 50L` (spec: map-rotation-gesture / Two-finger rotation gesture).
- [x] 4.4 Multi-touch gesture applies a `graphicsLayer` visual transform (rotation/zoom/pan, clamped) to the current bitmap with no render calls; `onRenderRequested` (gesture end) commits the accumulated changes (`updateAngle` normalized, `updateMagnification` with `round(log2(totalZoom))` + `zoomAtCursor`, center already updated) and calls `renderMap(forceFullRender = true)` (spec: map-rotation-gesture / Two-finger rotation gesture).
- [x] 4.5 Add `MapRendererRotatedRenderTest`: north-up render emits the front buffer; forced full render carries a non-zero angle through to the front buffer; large angles (450°) normalize correctly (spec: map-rotation-gesture / Two-finger rotation gesture).
- [x] 4.6 Limit visual zoom to `[0.25, 4.0]` (±2 mag levels), ignore the zoom ratio when fingers start closer than 20 px, and normalize the rotation angle (visual `rotationZ` to `[-180, 180]`, committed angle to `[-π, π]`) so rotation cannot grow unbounded or shrink/zoom the map to nothing (spec: map-rotation-gesture / Two-finger rotation gesture).
- [x] 4.7 Report the two-finger centroid on every multi-touch event (`onGestureCentroid`) and clamp it to the canvas bounds; clamp pan deltas to the screen dimension; validate/clamp the map center to the Mercator-valid range in `updateCenter` — corrupted pointer positions from multi-touch emulation can no longer produce a garbage zoom pivot or center (spec: map-rotation-gesture / Two-finger rotation gesture).

## 5. Verification

- [x] 5.1 Run `./gradlew :app:testDebugUnitTest` — all existing and new unit tests pass.
- [x] 5.2 Run `./gradlew :app:assembleDebug -Pandroid.injected.build.abi=arm64-v8a` — build compiles without errors.
- [x] 5.3 Manual check on emulator: two-finger rotation rotates the map without zoom/pan drift; pinch still zooms; map stays visible (garbage pointer positions from emulator multi-touch emulation are clamped). Real-device check skipped by user decision — unit tests cover follow-mode/north-up clearing (`MapCanvasViewModelFollowModeTest`).
