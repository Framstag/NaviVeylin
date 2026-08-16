## Why

Long-press on a rotated map resolves the wrong OSM object: the details sheet describes objects that are not under the press point. `fireLongPress` converts screen → geo with the north-up `ProjectionUtils.screenToGeo`, which ignores the viewport rotation angle. The renderer and marker overlay already use the angle-aware `screenToGeoRotated`/`geoToScreenRotated`; the long-press path is the one conversion that was never updated for rotation.

## What Changes

- `MapCanvasScreen.fireLongPress` builds a `ProjectedViewport` including `s.viewport.angle` and converts the press position with `screenToGeoRotated` instead of the north-up `screenToGeo`.
- No native/JNI changes: `getDescription(lat, lon)` and the candidate-ranking algorithm are untouched — only the coordinate fed to them is corrected.
- `screenToGeoRotated` at angle 0 is mathematically identical to `screenToGeo`, so the fix is a no-op for north-up maps and only changes behavior when the map is rotated.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `long-press-details`: the existing requirement already states screen coordinates SHALL be converted "using the current map projection and viewport" — the implementation violates this when the viewport is rotated. Add an explicit scenario pinning the behavior: a long press in a rotated viewport SHALL resolve the object under the press point using the angle-aware projection.

## Impact

- `app/src/main/java/com/naviveylin/ui/map/MapCanvasScreen.kt` — `fireLongPress` only (one call site).
- `core/src/main/java/com/naviveylin/core/ProjectionUtils.kt` — unchanged; `ProjectedViewport.screenToGeoRotated` already exists and is verified by renderer/marker usage.
- Tests: add regression test in `app/src/test/java/com/naviveylin/ui/map/ProjectionUtilsTest.kt` — `screenToGeoRotated` round-trips with `geoToScreenRotated` at a non-zero angle, and equals `screenToGeo` at angle 0.
- Related finding, out of scope: `ProjectionUtils.zoomAtCursor` also uses the north-up `screenToGeo` internally, so the zoom pivot is wrong on a rotated map (scroll-wheel zoom and gesture-end zoom commit). Same root cause family; separate change.
