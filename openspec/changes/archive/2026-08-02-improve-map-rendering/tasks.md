## 1. Investigate JavaScout fixes

- [x] 1.1 Find JavaScout commits/issues for marker direction, marker drift, and zoom placeholder scaling; record references in `design.md` (`jnisync-java-scout`).
- [x] 1.2 Inspect current `libosmscout-client-java` source to locate render call and parameter handling (`jnisync-java-scout`).
- [x] 1.3 Inspect current app Kotlin code for marker overlay, `ProjectionUtils`, and zoom placeholder logic (`marker-render-accuracy`, `zoom-transition-scaling`).

## 2. Update JNI/native render path

- [x] 2.1 Confirm JNI render method signature already has `angle`; no signature change needed (`jnisync-java-scout`).
- [x] 2.2 Verify C++ JNI wrapper forwards `angle` to `MercatorProjection::Set(...)` (`jnisync-java-scout`).
- [x] 2.3 Verify native renderer uses the same projection and ground resolution as JavaScout for the same inputs (`jnisync-java-scout`).
- [x] 2.4 Force clean native rebuild and run `./gradlew :app:assembleDebug` for all three ABIs (`jnisync-java-scout`).

## 3. Unify projection in Kotlin

- [x] 3.1 Refactor `ProjectionUtils` so marker projection and render-parameter computation share the same Mercator/DPI math (`marker-render-accuracy`, `zoom-transition-scaling`).
- [x] 3.2 Add `rotation` field to viewport state and propagate it to marker overlay and native render call (`marker-render-accuracy`, `jnisync-java-scout`).
- [x] 3.3 Add `lastRenderedMagnification` field to the render state to enable correct placeholder scaling (`zoom-transition-scaling`).

## 4. Fix marker rendering

- [x] 4.1 Update GPS marker overlay to project position with rotation (`marker-render-accuracy`).
- [x] 4.2 Update GPS direction arrow to draw at `(bearing + mapRotation)` using `ProjectionUtils.screenBearing` (`marker-render-accuracy`, `gps-location-marker`).
- [x] 4.3 Verify favorite markers use `_favorite` synthetic node and are rendered by the native pipeline (`marker-render-accuracy`, `fav-markers`).
- [x] 4.4 Add unit tests for marker projection with and without rotation (`marker-render-accuracy`).


## 5. Fix zoom placeholder scaling

- [x] 5.1 Track `frontBufferMag`, `frontBufferLat`, `frontBufferLon`, `currentMag`, `currentAngle`, and render `epoch` in `MapRenderer` (render state) and wire `MapCanvasViewModel.projectToScreen` to `lastRenderedMagnification` (`zoom-transition-scaling`).
- [x] 5.2 Implement JavaScout-style `trySubRegionBlit()` in `MapRenderer`: when `newMag != frontBufferMag`, compute `zoomScale = 2^(newMag - frontBufferMag)` and scale the front buffer around the new center (`zoom-transition-scaling`, `map-pan-zoom`).
- [x] 5.3 For zoom in, clamp source rect to front-buffer bounds and draw scaled region to fill screen (`zoom-transition-scaling`).
- [x] 5.4 For zoom out, scale buffer down and position so new center aligns with screen center (`zoom-transition-scaling`).
- [x] 5.5 Increment `epoch` after showing the placeholder so stale native renders are ignored; atomically swap front buffer when the matching render completes (`zoom-transition-scaling`).
- [x] 5.6 Add unit tests for placeholder scale factor and anchor origin math, including focal point clamping (`zoom-transition-scaling`).

## 6. Verify and finalize

- [x] 6.1 Run `./gradlew test` and ensure all unit tests pass.
- [x] 6.2 Run `./gradlew :app:assembleDebug` for all target ABIs and verify no build errors.
- [x] 6.3 Archive the change with `openspec validate` and `openspec archive`.
