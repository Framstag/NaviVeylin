# Tasks: Fix rotation gesture temporary angle jump

Parent spec: `map-rotation-gesture` (specs/map-rotation-gesture/spec.md — "No temporary angle jump on gesture end"). Design: design.md.

## 1. Rotation display-layer hold (spec: map-rotation-gesture — No temporary angle jump on gesture end)

- [x] 1.1 In `MapCanvasScreen.kt`, add `rotationHoldActive: Boolean` + `crossfadeAngle`/`lastFrontAngle` state; in `onRenderRequested` arm the flag when the gesture changed rotation (keep the committed `updateAngle(newAngle)` and `renderMap(forceFullRender = true)` unchanged); verify compile via `./gradlew :app:compileMobileDebugKotlin`
- [x] 1.2 In the graphicsLayer block, apply `rotationZ = normalizeDegrees(Math.toDegrees(rotationDisplayTheta(gestureRotation, rotationHoldActive, state.renderViewport?.angle, state.viewport.angle)))` — the hold angle is DERIVED per draw from the same collected state the draw reads, so it zeroes atomically with the committed bitmap swap (no one-frame double-rotation twist when the render lands); verify compile
- [x] 1.3 In the frame loop, track `lastFrontAngle`; when the front buffer changes TO the committed angle while the hold is armed, disarm it and capture the old rotated frame as a render-land crossfade (old bitmap rotated by `committed − lastFrontAngle` around the canvas center — mirror of the zoom crossfade, masking tile-vs-native rasterization differences); `drawFrontFrame` gains a `rotationDegrees` param; verify compile
- [x] 1.4 Disarm `rotationHoldActive` on the next gesture start (`onGestureCentroid` / `onRotate`) so a stale hold never corrupts a new gesture; verify compile

## 2. Unit tests (spec: map-rotation-gesture — No temporary angle jump on gesture end)

- [x] 2.1 Add `RotationHandoffTest.kt` under `app/src/test/java/com/naviveylin/ui/map/`: pure-function tests for `rotationDisplayTheta` — (a) hold keeps the final angle while the front buffer differs from the committed angle; (b) hold zeroes atomically when `renderViewport.angle == viewport.angle`; (c) wrapping-safe for large single-gesture rotations (gap ≡ accumulated rotation mod 2π); (d) unknown front buffer falls back to gesture rotation; (e) combined rotate+zoom gesture composes independently (zoom side covered by the existing smooth-zoom handoff tests); verify `./gradlew :app:testMobileDebugUnitTest --tests "*RotationHandoffTest*"` passes
- [x] 2.2 Verify existing gesture/transform tests still pass: `./gradlew :app:testMobileDebugUnitTest --tests "*MapCanvasGestureTransformTest*"` and the full suite `./gradlew test` (run-tests skill)

## 3. Guidelines update

- [x] 3.1 Update `guidelines/MapRendering.md`: document the rotation display-layer handoff contract — the live rotation angle SHALL stay applied after gesture end until the front buffer swaps to the committed angle (no intermediate frame at the pre-gesture angle); reference `MapCanvasScreen.onRenderRequested` and the frame-loop clear

## 4. Build and regression verification

- [x] 4.1 Verify full build compiles without errors: `./gradlew :app:assembleMobileDebug` (build-app skill)
- [x] 4.2 Verify existing tests still pass: `./gradlew test` (run-tests skill), including `MapCanvasGestureTransformTest.kt`, `MapRendererBlitTest.kt`, and navigation tests
- [x] 4.3 Confirm the four active sibling changes (`fast-reroute-trigger`, `fix-reroute-route-drawing`, `enlarge-phone-nav-overlays`, `fix-route-refresh-and-auto-zoom-reengage`) are unaffected (rotation gesture path untouched; code review diff check)

## 5. On-device verification (device, phone scope)

- [x] 5.1 Two-finger rotation on a real device: confirm the map does NOT temporarily jump back to the pre-gesture angle at gesture end — the display stays at the final angle while the re-render is in flight and the new frame lands in place (spec: No temporary angle jump on gesture end); repeat with a slow render (dense area) to confirm the hold spans the full render duration; logcat shows the held rotation cleared only after the `debounce enqueue` render lands
- [x] 5.2 Combined rotate+zoom gesture: confirm no jump-back in angle and no zoom snap at gesture end (both holds clear independently)
