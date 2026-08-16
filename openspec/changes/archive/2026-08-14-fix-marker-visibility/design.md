# Fix Marker Visibility — Design

## Context

See `proposal.md` — Why. Current state: the GPS marker is drawn natively (Cairo) into the same render surface as the map in `OSMScoutClient.cpp`, after `MapPainterCairo::DrawMap`. That surface is (a) split into 256×256 tiles cached in `TileCache` and (b) reused as front buffer for sub-region blits. `MapRenderer.executeRender` pushes marker state once per job via `client.setGpsMarker(...)`; every subsequent per-tile JNI render then bakes the marker into each tile at its first-render position. Cached tiles + blitted front-buffer regions therefore carry ghost marker pixels; in follow/routing mode continuous viewport shifts leave the marker stale or off-screen.

A spec-compliant Compose overlay (`LocationMarkerOverlay`) already exists but is never composed — `MapCanvasScreen` shows only the map bitmap. The VM already exposes everything the overlay needs: `uiState.gpsLocation` (raw/nav-filtered `Location`) and `uiState.renderViewport` (front-buffer viewport, wired at `MapCanvasViewModel` L1118-1122).

## Goals / Non-Goals

**Goals:**
- Marker rendered exclusively as a per-frame Compose overlay on top of the displayed bitmap
- Cached tiles, back buffer, and front buffer contain only static map content (spec deltas: `gps-location-marker`, `tile-cache`, `double-buffering`)
- Marker stays on road in follow/routing mode, reprojected against the displayed frame's viewport
- No native re-render on marker-only movement (overlay redraws; renders only on viewport change)

**Non-Goals:**
- No new native surface or separate Cairo overlay layer (spec mandates Compose primitives)
- No changes to follow-mode render coalescing, COG derivation, auto-zoom, or map rotation logic
- No changes to favorite/search/route markers (those remain native POI overlays by design — `marker-render-accuracy` spec)
- No Android Auto marker changes (`:auto` module untouched)

## Decisions

### D1. Marker moves entirely out of the native render into `LocationMarkerOverlay`

Remove from `OSMScoutClient.cpp`: GPS marker state block (L~400-407), marker snapshot read (L~999-1015), Cairo marker draw block (L~1238-1330), and the `setGpsMarker`/`clearGpsMarker` JNI exports. Remove matching `native` declarations from `OSMScoutClient.java` and overrides from `FakeOSMScoutClient`. Strip marker plumbing from `MapRenderer`: fields, `RenderJob`/`PendingRender` members, `setGpsMarker`/`clearGpsMarker` API, the `client.setGpsMarker(...)` call in `executeRender`, and the now-dead `gpsRenderMinIntervalMs` throttle (`lastGpsRenderMs`, `lastMarker*`).

Rationale: `renderTilePixels` already passes `null, null` for marker args — the marker reached tiles only through the global native state, so removing that state restores tile purity with zero renderer-signature churn. Alternative (keep Cairo marker, draw to a separate native surface) was rejected: it duplicates the whole projection path, adds a second surface lifecycle, and violates the existing spec requirement (Compose primitives, not JNI/Cairo).

### D2. Overlay projection uses the front-buffer viewport, not the live viewport

`MapCanvasScreen` composes `LocationMarkerOverlay(position = uiState.gpsMarkerLat/Lon, bearing = uiState.gpsMarkerBearing, accuracy = uiState.gpsMarkerAccuracy, viewport = uiState.renderViewport, ...)` in the same `Box` as the map image, above it. The overlay consumes the VM's marker state — NOT the raw `uiState.gpsLocation` — so position and heading semantics are byte-for-byte identical to the old native path:

- **Position** = `followMarkerLat/Lon` (`markerLat/markerLon`): navigation-engine-filtered position in routing mode, raw GPS otherwise. Never the smoothed camera center (guidelines §8 — a smoothed marker drifts off the road).
- **Bearing** = the freshest-direction priority chain: window course → last segment bearing → last used bearing; in north-up orientation deliberately `-1.0` (arrow points up/north), exactly like today.
- **Accuracy** = `loc.accuracy` when > 0, else none (no accuracy circle).

`MapCanvasUiState` gains `gpsMarkerLat`, `gpsMarkerLon`, `gpsMarkerBearing`, `gpsMarkerAccuracy` (fed from the renderer's atomic `frameFlow` emission, see D4); `gpsLocation` stays as-is for other UI consumers.

### D4. Marker position rides with each emitted frame (frame snapshot, not live fix)

The VM feeds marker state to `MapRenderer.setGpsMarkerState(...)` (storage only, no native draw, no render trigger). `MapRenderer` snapshots it into the render job at enqueue and emits it with the front buffer as ONE atomic `frameFlow` emission (`FrameState(bitmap, viewport, marker)` — bitmap, producing viewport, and marker snapshot can never come from different frames); the overlay draws that snapshot. The marker therefore always sits on the road of the displayed bitmap — it can never lead/jump while frames lag the live GPS fix (the old native marker behaved exactly this way because it was baked at render time). A pure marker move in non-follow mode triggers a render when it exceeds 5 m (1 s throttle, restoring the old `setGpsMarker` cadence); follow mode renders are already driven by viewport motion. Rationale: a live-fix overlay drawn on a stale frame leads by up to one fix of travel (~83 m at replay speed, observed as 150–270 px jumps) and snaps back when the frame lands — user-visible jumping. An earlier iteration fed the three frame parts (bitmap/viewport/marker) through three separate StateFlows combined in the ViewModel; `combine` re-emitted per sub-flow change, so intermediate mixed-frame states could be drawn (observed as 3 UI-state updates per render) — hence the single atomic `frameFlow`.

For the overlay to stay on the road, the map content it is drawn on must use the same rotation pivot: `renderFromTiles` composes rotated views by placing tiles north-up and rotating the whole canvas about the viewport center (`canvas.rotate(deg, W/2, H/2)`). Rotating each tile about its own corner would shift content by up to `d·θ` and push the marker off the road (pre-existing tile-path bug, exposed by the overlay; fixed in this change — see `guidelines/MapRendering.md` §13). `uiState.renderViewport` is fed by the emitted `frameFlow`, so the overlay always projects against the viewport of the bitmap actually on screen. During pan/zoom/rotation gestures the target viewport (`currentViewport`) leads the rendered frame — projecting against it would pin the marker to the wrong place. `ProjectionUtils.viewport(...)` + `geoToScreenRotated(...)` (already used by the overlay) match the projection the native renderer uses; the native marker log line in `executeRender` becomes obsolete and is removed.

### D3. Overlay bearing = VM's freshest-direction signal, not raw `Location.bearing`

The VM's follow path already computes `markerBearingRaw` (priority: window course → last segment bearing → last used bearing) and `markerBearing`. Raw `Location.bearing` is noisy/provider-dependent (guidelines §8). The follow path already computes `markerBearingRaw` (priority: window course → last segment bearing → last used bearing) and `markerBearing` (forced `-1.0` in north-up orientation). `gpsMarkerBearing` in `MapCanvasUiState` is updated in the same `location.collect` block: follow mode → `markerBearing`; non-follow mode → `loc.bearing` when ≥ 0 else NaN (mirrors today's `setGpsMarker(bearing, accuracy)` call at L546). The overlay's arrow uses this bearing, so the marker arrow reacts to turns at the same priority and speed as before; `bearing < 0` keeps the north-up arrow. The sign convention stays `screenBearing = bearing + angle` (guidelines §6 — do not flip).

### D4. Marker-only movement triggers nothing in the render pipeline

In non-follow mode the current code calls `setGpsMarker(...)` and returns without rendering — the marker only ever appears on the next unrelated render (a cause of "invisible marker"). With the overlay, `uiState.gpsMarkerLat/Lon/Bearing/Accuracy` updates recompose the overlay every frame; no renderer call, no epoch bump, no tile invalidation. Follow-mode `shouldRender` logic (position > 5 m, angle delta, zoom commit) stays exactly as-is — renders continue to be driven by `prepareViewport` + `requestRender` alone. `clearGpsMarker()` calls are replaced by `gpsMarkerLat = NaN` state updates (overlay returns early on NaN position).

## Risks / Trade-offs

- [Kotlin projection drifts from native projection at high zoom/rotation] → Overlay uses the same `ProjectionUtils` math already validated for marker logging; keep a debug log of projected overlay position vs rendered viewport for device verification in follow and routing modes
- [Overlay accidentally consumes raw `gpsLocation` instead of nav-filtered marker position] → Overlay takes explicit `gpsMarkerLat/Lon/Bearing/Accuracy` state set from the same expressions the native path used; unit test asserts the VM stores the nav-filtered position, not the raw fix
- [Overlay out of sync with bitmap during tile-path placeholder frames] → `frontBufferViewportFlow` updates only when a frame is emitted; overlay recomposes against the last emitted viewport, which is exactly the frame on screen — no drift
- [Removing `setGpsMarker` breaks existing tests] → Migrate `MapRendererGpsMarkerTest` (marker state/throttle assertions deleted or moved to overlay-level tests) and `MapCanvasViewModelFollowModeTest` (assert `gpsLocation`/`gpsMarkerBearing` state instead of renderer calls); add a `LocationMarkerOverlay` projection test (off-screen culling, front-buffer viewport anchoring)
- [Regression: marker missing because overlay not composed] → The composition is the first task, done before native removal so the marker never has a gap; device-verify both follow and routing modes
- [Stale `MapRendering.md` guidance misleads future work] → Update `guidelines/MapRendering.md` (pipeline §1, angle §6, marker §8, emission §12, blits §13, checklist) as the final task before `openspec sync`/archive

## Migration Plan

1. Compose `LocationMarkerOverlay` in `MapCanvasScreen` (add `gpsMarkerBearing` to UI state + VM wiring) — marker appears via overlay while native draw still active (temporarily double-drawn on device, acceptable during the transition)
2. Remove native marker path (cpp + Java + `MapRenderer` plumbing + fakes) — overlay becomes sole renderer
3. Migrate/extend unit tests; run `./gradlew test`
4. Device-verify: follow location mode, routing mode, pan/zoom/rotate, marker hide on GPS loss
5. Update `guidelines/MapRendering.md` to final state
6. `openspec sync` + archive

## Open Questions

None — remaining unknowns (exact overlay placement in the `Box`, log verbosity) are cosmetic and resolvable during implementation without affecting specs, approach, or task breakdown.
