# Fix Marker Visibility

## Why

The GPS location marker is drawn natively (Cairo) into the same render surface as the map in `OSMScoutClient.cpp`. Because that surface is split into cached tiles and reused front buffers, the marker gets baked into tile-cache and double-buffer bitmaps. Result: ghost marker artifacts (marker frozen at a stale screen position) and the marker missing or misplaced in follow-location and routing modes on device. The marker needs a dedicated overlay target so cached tiles and reusable bitmaps are never "corrupted" with per-frame temporary drawings.

## What Changes

- **Remove native Cairo GPS marker rendering** from `OSMScoutClient.cpp`: drop the GPS marker state block, the marker draw block after `DrawMap`, and the `setGpsMarker`/`clearGpsMarker` JNI exports. The native render output becomes pure map content only.
- **Remove marker plumbing from the render pipeline**: drop GPS marker fields from `RenderJob`/`PendingRender` and the `client.setGpsMarker(...)` call in `MapRenderer.executeRender`, so tile renders can never contain the marker.
- **Remove the now-dead JNI surface**: delete `setGpsMarker`/`clearGpsMarker` native declarations in `OSMScoutClient.java` and the matching fake overrides in the test JNI stub.
- **Render the marker exclusively as a Compose overlay**: wire the existing (currently uncomposed) `LocationMarkerOverlay` into `MapCanvasScreen` on top of the map bitmap, projecting against `frontBufferViewportFlow` so the overlay matches the displayed bitmap exactly (same center, zoom, rotation, DPI).
- **Keep GPS-triggered re-renders only where map content actually changes**: follow-mode viewport motion still re-renders; a pure marker move with a static viewport updates only the overlay (no native re-render needed).

## Capabilities

### New Capabilities

None — this change restores behavior already specified; no new capability introduced.

### Modified Capabilities

- `gps-location-marker`: Add explicit requirement that the marker SHALL be drawn on an overlay target separate from the map render surface — it SHALL NOT be rendered into cached tiles, back/front buffers, or any bitmap that is reused across frames. Add scenario covering marker move without native re-render (overlay-only redraw).
- `tile-cache`: Add requirement that cached tiles SHALL contain only static map content; ephemeral per-frame overlays (GPS marker) SHALL be excluded from tile rendering so cached tiles are immutable across frames.
- `double-buffering`: Add requirement that back/front buffers SHALL contain only rendered map content; temporary overlays SHALL be drawn on top of the front buffer by the UI layer and never written into either buffer.

## Impact

- `app/src/main/cpp/libosmscout/libosmscout-client-java/src/OSMScoutClient.cpp` — remove GPS marker state (~L400-407), marker snapshot read (~L999-1015), Cairo marker draw (~L1238-1330), `setGpsMarker`/`clearGpsMarker` JNI functions
- `app/src/main/cpp/libosmscout/libosmscout-client-java/src/.../OSMScoutClient.java` — remove `setGpsMarker`/`clearGpsMarker` native declarations
- `app/src/main/java/com/naviveylin/ui/map/MapRenderer.kt` — remove GPS marker fields, `RenderJob`/`PendingRender` marker members, `setGpsMarker`/`clearGpsMarker` API, `client.setGpsMarker` call; keep `frontBufferViewportFlow` for the overlay
- `app/src/main/java/com/naviveylin/ui/map/MapCanvasViewModel.kt` — stop calling renderer marker API; expose latest `Location` + front-buffer viewport for the overlay
- `app/src/main/java/com/naviveylin/ui/map/MapCanvasScreen.kt` — compose `LocationMarkerOverlay` above the map bitmap
- `app/src/main/java/com/naviveylin/ui/map/LocationMarkerOverlay.kt` — reuse as-is (already spec-compliant Compose overlay); verify projection vs front-buffer viewport
- Tests: `MapRenderer` unit tests referencing GPS marker state, `MapCanvasViewModelFollowModeTest`, `FakeOSMScoutClient` (remove marker overrides), JNI stub
- No new dependencies; no public API beyond removing internal JNI methods
