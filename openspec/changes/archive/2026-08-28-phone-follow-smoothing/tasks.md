## 1. Shared prediction helper (spec: smooth-follow)

- [x] 1.1 Create `FollowPrediction` in `core/src/main/java/com/naviveylin/core/` with `update(fix)`, `predictedPosition(now)`, and `easeOffset(...)` per design D5, and verify it compiles (`./gradlew :core:compileDebugKotlin` or the app build)
- [x] 1.2 Add unit tests for `FollowPrediction` covering: linear extrapolation along smoothed bearing, clamp when `now - fixTime > 1.5 s`, hold when speed/bearing unavailable, ease convergence to zero, and verify `./gradlew :app:testDebugUnitTest --tests "*FollowPrediction*"` passes

## 2. Renderer emission change (spec: gps-render-coalescing)

- [x] 2.1 Change follow-mode `emitFrame` to emit the full overrun-sized front buffer (`crop=false`) instead of the extracted center region, and verify existing `MapRenderer` tests still pass (`./gradlew :app:testDebugUnitTest --tests "*MapRenderer*"`)
- [x] 2.2 Verify the fix-driven `trySubRegionBlit` path is unchanged (blit still shifts the overrun buffer and emits a frame centered on the true fix) and its existing tests pass

## 3. Screen display loop (spec: smooth-follow, gps-location-marker)

- [x] 3.1 Add a `withFrameNanos` loop in `MapCanvasScreen` that computes the predicted position from the live `GpsFix` (`uiState.gpsLocation`), draws the emitted bitmap offset by the predicted delta (scaled to canvas), and clamps the offset to the overrun margin — verify no edge strip is visible in a manual drive test
- [x] 3.2 Request a render at the predicted position when the offset would exceed the overrun margin, and verify the map continues scrolling without a blank strip
- [x] 3.3 Implement correction easing: on fix arrival, ease the display offset from `(predicted − true)` to 0 over ~200-300 ms (design D4), and verify the map does not snap on fix arrival
- [x] 3.4 Draw the GPS marker at the predicted position each frame (glides with the blitted map), and verify the marker stays on the road during a manual drive test
- [x] 3.5 Gate the loop on follow mode + speed > ~1 m/s + valid fix; verify the map holds still when the vehicle stops and that non-follow gestures bypass the loop

## 4. ViewModel wiring (spec: smooth-follow)

- [x] 4.1 Ensure the follow-mode path in `MapCanvasViewModel` exposes fix speed/heading/time to the screen loop (via `uiState.gpsLocation` or a renderer call), and verify the navigation engine still receives only real fixes (`processLocation` unchanged)

## 5. Verification

- [x] 5.1 Verify the app builds without errors: `./gradlew :app:assembleMobileDebug`
- [x] 5.2 Verify all existing unit tests still pass: `./gradlew :app:testDebugUnitTest`
- [x] 5.3 Add/update unit tests for the new screen-loop logic where feasible (offset math, gating) and verify they pass
- [x] 5.4 Manual drive test: smooth scroll between fixes, no edge reveal, marker on road, no regression in pan/zoom gestures — verified on the emulator with GPX-track GPS replay (free follow mode, 2 stops, 7 hard brakes, irregular 1-2.4 s fix cadence); user confirmed the map scrolls smoothly
