# Tasks: Fix Rotation Gesture on Real Devices

## 1. Gesture handler — accumulate rotation deltas

- [x] 1.1 In `app/src/main/java/com/naviveylin/ui/map/MapGestures.kt`, replace the per-event rotation threshold (`abs(angleDelta) > 0.05f`) with a per-gesture accumulator: sum raw angle deltas, report when `abs(accumulated) > 0.01f` rad, reset (spec: map-rotation-gesture / Slow rotation with small per-event deltas).
- [x] 1.2 Flush any non-zero residual accumulator when the tracked pointer lifts, before `onRenderRequested` (spec: map-rotation-gesture / Slow rotation with small per-event deltas).

## 2. Tests

- [x] 2.1 Add `MapGestureComposeTest.slowRotationWithSmallPerEventDeltasIsReported`: rotate 90° in 100 steps of 0.9° (0.0157 rad/event, below the old 0.05 threshold); assert rotation is reported and totals ≈ 90° (spec: map-rotation-gesture / Slow rotation with small per-event deltas).
- [x] 2.2 Run `./gradlew :app:testDebugUnitTest --tests "com.naviveylin.ui.map.MapGestureComposeTest"` — all 10 tests pass, including the new regression test.

## 3. Visual transform — rotate around center, zoom around centroid

- [x] 3.1 In `app/src/main/java/com/naviveylin/ui/map/MapCanvasScreen.kt`, change the live multi-touch `graphicsLayer` to rotate around the screen center (fixed `transformOrigin` 0.5/0.5) instead of the moving gesture centroid; add `gestureTransformTranslation` computing `T = (1 − s)·R(θ)·(C − O) + P` so the zoom keeps its pivot at the centroid (spec: map-rotation-gesture / Map stays visible during rotation).
- [x] 3.2 Add `MapCanvasGestureTransformTest`: pure pinch keeps the centroid fixed; pure rotation has no translation (map stays covering the canvas at 180°); pan is preserved (spec: map-rotation-gesture / Map stays visible during rotation).
- [x] 3.3 Run `./gradlew :app:testDebugUnitTest --tests "com.naviveylin.ui.map.MapCanvasGestureTransformTest"` — all 4 tests pass.

## 4. Visual zoom — clamp to commit headroom at the limits

- [x] 4.1 In `app/src/main/java/com/naviveylin/ui/map/MapCanvasScreen.kt`, add `clampGestureVisualZoom(zoomFactor, mag)` clamping the live visual zoom to `[2^(GESTURE_MIN_MAG − mag), 2^(MAX_MAG − mag)]` ∩ `[0.25, 4.0]`; use it in `onZoom` (spec: map-rotation-gesture / Zoom preview does not exceed the commit range at the limits).
- [x] 4.2 Add `MapCanvasGestureTransformTest` cases: at mag 20 the preview clamps to 1.0 (no zoom-in snap-back), at mag 19 to 2.0, at mag 4 to 1.0 (no zoom-out snap-back), at mag 5 to 0.5, mid-range keeps the full ±2-level range (spec: map-rotation-gesture / Zoom preview does not exceed the commit range at the limits).
- [x] 4.3 Run `./gradlew :app:testDebugUnitTest` — full suite passes.

## 5. Verification

- [x] 5.1 Manual check on real device: two-finger rotation rotates the map live, no pan/zoom drift, map stays visible through 180°, no zoom snap-back at the magnification limits, single re-render on gesture end with upright labels.
- [x] 5.2 Run `./gradlew :app:assembleDebug -Pandroid.injected.build.abi=arm64-v8a` — build compiles.
