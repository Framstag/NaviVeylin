## Context

See `proposal.md` for motivation. NaviVeylin renders maps through a JNI bridge to `libosmscout-client-java`. JavaScout (the upstream desktop reference) already corrected rendering defects that NaviVeylin still exhibits: marker direction not compensating for map rotation, GPS marker drifting relative to roads during zoom transitions, and pinch-zoom placeholders that do not match the target magnification. The Kotlin UI composes markers on top of a native-rendered tile buffer, so any mismatch between the Kotlin projection and the native projection becomes visible as drift.

## Goals / Non-Goals

**Goals:**
- Align Kotlin-side marker projection with the native renderer's projection.
- Correct direction arrow rotation for map rotation.
- Fix pinch-zoom placeholder scaling and anchor origin using JavaScout-style sub-region blit with render epoch cancellation.
- Port equivalent JavaScout projection fixes into the JNI/native layer.
- Add unit regression tests for marker projection, direction angle, and zoom placeholder math.

**Non-Goals:**
- Rewriting the native renderer from scratch.
- Changing the map style or tile size.
- Adding new map data sources.
- General performance optimization beyond the listed defects.
- Adding instrumented Android tests (unit tests on `ProjectionUtils` and placeholder math are sufficient).

## Decisions

### Use a single shared projection helper for marker and zoom math

- **Choice**: Refactor `ProjectionUtils` so both native-render parameter computation and Compose marker projection use the same Mercator/DPI math.
- **Rationale**: The drift bugs come from two slightly different projection paths. Unifying them removes a class of alignment defects.
- **Alternative considered**: Patch the existing marker overlay only. Rejected because the root cause is duplicated projection logic.

### Pass map rotation through the entire render pipeline

- **Choice**: Add a `rotation` parameter to the JNI render call, the ViewModel viewport state, and the marker overlay.
- **Rationale**: JavaScout already applies rotation consistently; NaviVeylin must do the same to match output.
- **Alternative considered**: Apply rotation only as a post-rotation of the front buffer in Compose. Rejected because it would misalign native symbols with Compose markers.

### Keep placeholder rendering entirely in Compose

- **Choice**: The front buffer is scaled and drawn by Compose Canvas during the pinch gesture; no native re-render occurs until the debounce expires.
- **Rationale**: This is the current architecture. The fix is to compute the scale and origin correctly, not to move rendering to native.
- **Alternative considered**: Render intermediate zoom levels natively. Rejected because it is slow and complex.

### Implement JavaScout-style placeholder rendering with epoch cancellation

- **Choice**: Maintain `frontBufferMag`, `frontBufferLat`, `frontBufferLon`, `currentMag`, `currentAngle`, and a monotonic render `epoch`. During a pinch zoom, draw the previous front buffer scaled to the new magnification around the focal point. When the render completes for the new magnification, atomically swap the placeholder and increment the epoch so stale renders are ignored.
- **Rationale**: JavaScout `MapRenderer.trySubRegionBlit()` already proved this eliminates end-of-gesture jumps. Copying the exact algorithm keeps NaviVeylin behavior consistent with the upstream reference.
- **Alternative considered**: Minimal scale-only placeholder. Rejected because it causes a visible jump when the native render finishes.

### Port JavaScout fixes as targeted patches, not a full submodule update

- **Choice**: Identify the upstream commits that fix marker/zoom projection and apply equivalent changes to the NaviVeylin JNI/native code.
- **Rationale**: A full submodule update may bring unrelated changes and build breakage.
- **Alternative considered**: Update the submodule to latest upstream. Rejected to keep the change focused.

## Risks / Trade-offs

- **Risk**: JavaScout code diverges enough that a direct patch is not possible.
  - **Mitigation**: Record upstream commit references and write delta tests so regressions are caught even if the patch is manual.
- **Risk**: The existing JNI `render(...)` method already accepts `angle`, but no Kotlin caller passes rotation. Using it only requires app-side changes; no native ABI break.
  - **Mitigation**: No CMake/Gradle version bump needed for the signature. Force a clean native rebuild in CI to avoid stale `.so` caching.
- **Risk**: Compose marker projection still differs due to floating-point rounding.
  - **Mitigation**: Use the same `double` math for both paths and add pixel-tolerance unit tests.
- **Risk**: Full JavaScout-style placeholder state machine is more complex than a minimal fix.
  - **Mitigation**: Keep the state machine isolated in `MapCanvasViewModel`; test placeholder math directly via unit tests.

## Migration Plan

1. Merge the change and bump the native library build version so CI forces a fresh `.so` build.
2. Update developer docs to note the new JNI render signature.
3. No runtime migration for users; maps and favorites remain compatible.
4. Rollback: revert the Kotlin, JNI, and native changes together; the old ABI does not expect rotation.

## Open Questions

- Which exact JavaScout commits fix the reported marker/zoom issues? Need upstream repository inspection before implementation.
  - **Answer**: JavaScout `MapRenderer.trySubRegionBlit()` in `64ea5cea4d3103387fb00087c029cfd434cc1b40` implements zoom placeholder scaling with `zoomScale = Math.pow(2, newMag - frontBufferMag)` and focal-point math. Projection rotation fixes are in upstream `506fafa12` (rotated projection bounding-box fix), `9f65dc074` (Mercator bounding-box validity), and `8387faf1d` (Mercator linear interpolation in OpenGL). HiDPI icon positioning fix `1f5a2f9b03eefd0f8e816868233ab196abdedcf6` is relevant but out of scope for this change.
- Does the current app ever apply map rotation, or is rotation always 0° today? This affects whether rotation is a new feature or a latent bug enabler.
  - **Answer**: Current Kotlin code references `mapAngleRadians` in `LocationMarkerOverlay`, but the native render call passes `angle = 0` from `MapCanvasViewModel`. Rotation exists as a latent capability; this change wires it through so the marker overlay and native renderer agree.
