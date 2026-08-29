## 1. Overrun buffer (spec: auto-smooth-follow)

- [x] 1.1 Add an overrun render buffer to `AutoMapRenderer`: render at ~1.2x surface size (design D1) and draw the visible region to the surface, and verify `./gradlew :auto:testDebugUnitTest --tests "*AutoMapRenderer*"` passes
- [x] 1.2 Add unit tests for the overrun extraction (visible region matches the viewport center at 1.2x size) and verify they pass

## 2. Sub-region blit (spec: auto-smooth-follow, auto-map-renderer)

- [x] 2.1 Implement sub-region blit: on a viewport change within the overrun region, `lockCanvas` → `drawBitmap(overrun, dx, dy)` → `unlockCanvasAndPost` (design D2), and verify a small viewport move does not trigger a full native render (test via render-count assertion)
- [x] 2.2 Fall back to a full render when the shift exceeds the overrun margin, and verify the map re-centers correctly
- [x] 2.3 Verify the blit respects the shared `surfaceLock` and the existing surface-failure path (invalid surface → stop, no `lockCanvas` exception)

## 3. Extrapolation loop (spec: auto-smooth-follow)

- [x] 3.1 Add a gated coroutine loop on the renderer scope (~30 fps, design D3) that runs only when resumed + follow mode + speed > ~1 m/s + surface valid, and verify the loop stops when the vehicle stops or the screen pauses
- [x] 3.2 Reuse `FollowPrediction` from `core` (landed in `phone-follow-smoothing`) for predicted position + easing, and verify the shared unit tests pass
- [x] 3.3 Draw the GPS marker (and destination marker) at the predicted position each frame, and verify the marker glides with the blitted map

## 4. Wiring (spec: auto-smooth-follow)

- [x] 4.1 Extend `setGpsMarker` (or add an API) in `AutoMapRenderer` to receive fix speed/heading/time, and wire `MapScreen` and `FreeDrivingScreen` to pass them from `AutoLocationProvider.position()`
- [x] 4.2 Verify the navigation engine still receives only real fixes (predicted positions are display-only)

## 5. Verification

- [x] 5.1 Verify the app builds without errors: `./gradlew :app:assembleMobileDebug` and `./gradlew :app:assembleAutomotiveDebug`
- [x] 5.2 Verify all existing unit tests still pass: `./gradlew :auto:testDebugUnitTest` and `./gradlew :app:testDebugUnitTest`
- [x] 5.3 Add/update unit tests for the new loop gating and blit logic and verify they pass
- [x] 5.4 On-device drive test (AAOS head unit or projection): smooth scroll between fixes, no edge reveal, marker on road, no surface lock errors, no battery/thermal regression
