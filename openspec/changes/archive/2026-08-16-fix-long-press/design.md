## Context

See proposal.md — Why. The long-press screen→geo conversion in `MapCanvasScreen.fireLongPress` uses the north-up `ProjectionUtils.screenToGeo`, ignoring `s.viewport.angle`. The angle-aware `ProjectedViewport.screenToGeoRotated` already exists in `core` and is the conversion used by `MapRenderer` (tile bounds) and `LocationMarkerOverlay` (marker placement), so the projection convention is established and verified by existing usage.

## Goals / Non-Goals

**Goals:**
- Long-press resolves the object under the press point on a rotated map.
- Zero behavior change for north-up maps (angle 0).

**Non-Goals:**
- Fixing `ProjectionUtils.zoomAtCursor`, which has the same north-up blind spot (zoom pivot wrong on rotated maps). Same root-cause family, separate change.
- Any native/JNI or ranking-algorithm changes — `getDescription(lat, lon)` is fed the corrected coordinate only.

## Decisions

**D1: Use `ProjectedViewport.screenToGeoRotated` in `fireLongPress` instead of adding a new helper.**
`fireLongPress` builds a `ProjectionUtils.viewport(centerLat, centerLon, mag, w, h, dpi, angle)` and calls `screenToGeoRotated(pos.x, pos.y)`. Matches the `MapRenderer.renderFromTiles` pattern (L533-543). No new math, no new API surface.
- Alternative considered: adding `ProjectionUtils.screenToGeoRotated(...)` object-level overload. Rejected — `ProjectedViewport` already carries the angle; a parallel overload duplicates state plumbing for one call site.

**D2: Always use the rotated variant, no angle branch.**
`screenToGeoRotated` at angle 0 reduces exactly to `screenToGeo` (cos 0 = 1, sin 0 = 0), so branching on `angle != 0.0` would be dead code. The renderer branches only because it also optimizes the corner list; the conversion itself is unconditional.

**D3: Scope boundary — `zoomAtCursor` stays untouched.**
`zoomAtCursor` internally calls the north-up `screenToGeo` (core L~200), so scroll-wheel zoom and gesture-end zoom commit misplace the pivot on rotated maps. Fixing it changes zoom behavior and needs its own spec delta; keeping it out of this change keeps the long-press fix reviewable.

## Risks / Trade-offs

- [Regression on north-up maps] → `screenToGeoRotated` ≡ `screenToGeo` at angle 0; covered by a unit test asserting equality.
- [Angle sign convention mismatch with native render] → `screenToGeoRotated` is already the conversion used for tile bounds and marker placement; long-press joins the same convention, so any residual error would be consistent across the app.
- [Zoom pivot still wrong when rotated] → Accepted; tracked as separate change (see Non-Goals).
