## Why

Current map pan/zoom is basic and janky. Pan uses a crude `degPerPx` heuristic instead of proper Mercator projection. Zoom is step-based (±1 per pinch), not smooth. No double buffering — each gesture end triggers a full native render, causing visible flicker and latency. No render-beyond-screen, no tile caching, no adaptive zoom fallback. JavaScout (reference JavaFX client) has mature solutions for all of these. Reimplement them in the Compose-based app for smooth, responsive map interaction.

## What Changes

- Replace crude `degPerPx` pan math with proper Mercator projection (matching libosmscout's `MercatorProjection`)
- Add double buffering: render to offscreen buffer, swap on completion, blit during gestures
- Add canvas overrun: render at 2.5× screen size so small pans reuse existing buffer (sub-region blit)
- Add zoom-at-cursor: pinch/scroll zoom keeps geographic point under finger/mouse fixed
- Add debounce: coalesce rapid gesture events before triggering native render (50ms pan, 200ms zoom)
- Add tile cache: split rendered buffer into 256×256 tiles, reuse on subsequent renders
- Add zoom placeholder: scale current buffer as immediate visual feedback during zoom, then full render
- Add epoch-based invalidation: discard stale renders when viewport changes during render
- Add adaptive zoom: if render takes too long, scale up lower-magnification image as fallback
- Update zoom controls to show current magnification level
- **BREAKING**: `MapCanvasUiState` gains new fields for double-buffer state, debounce state, render metrics

## Capabilities

### New Capabilities
- `double-buffering`: Render to offscreen buffer, atomic swap to front buffer on completion. Sub-region blit from overrun buffer during pan gestures without re-render.
- `tile-cache`: LRU cache of 256×256 rendered tiles. On subsequent renders, compose from cached tiles and only render missing tiles individually. Epoch-based invalidation.
- `canvas-overrun`: Render at configurable multiplier (default 2.5×) of screen size. Pan within overrun bounds uses sub-region blit instead of full re-render.
- `adaptive-zoom`: When full render takes too long, scale up lower-magnification buffer as placeholder. Tile cache enables incremental quality improvement.

### Modified Capabilities
- `map-pan-zoom`: Replace heuristic pan math with `ProjectionUtils.dragDeltaToNewCenter()` using proper Mercator projection. Add zoom-at-cursor for pinch and scroll zoom. Add debounce (50ms pan, 200ms zoom). Add smooth magnification changes (not just ±1 steps).
- `map-render`: Add double buffering pipeline. Add canvas overrun (render at 2.5× screen). Add tile cache integration. Add epoch-based stale render detection. Add render timing metrics.
- `zoom-controls`: Update to show current magnification level. Add smooth zoom animation. Wire through debounced render pipeline.

## Impact

- `app/src/main/java/com/naviveylin/ui/map/MapCanvasScreen.kt` — gesture handlers use `ProjectionUtils` for correct Mercator math; pan during gesture uses sub-region blit from overrun buffer
- `app/src/main/java/com/naviveylin/ui/map/MapCanvasViewModel.kt` — add double-buffer state, debounce logic, epoch tracking, render timing; replace `renderOnDefault()` with pipeline using `TileCache` and canvas overrun
- `app/src/main/java/com/naviveylin/ui/map/ProjectionUtils.kt` — **new file**: Mercator projection utilities (ported from JavaScout `ProjectionUtils.java`)
- `app/src/main/java/com/naviveylin/ui/map/MapRenderer.kt` — **new file**: render pipeline with double buffering, debounce, sub-region blit, tile cache integration (ported from JavaScout `MapRenderer.java`)
- `app/src/main/java/com/naviveylin/ui/map/TileCache.kt` — **new file**: LRU tile cache (ported from JavaScout `TileCache.java`)
- `app/src/main/java/com/naviveylin/ui/map/ZoomControls.kt` — update to show current magnification level
- `openspec/specs/map-pan-zoom/spec.md` — update with Mercator math, zoom-at-cursor, debounce requirements
- `openspec/specs/map-render/spec.md` — update with double buffering, canvas overrun, tile cache, epoch invalidation
- `openspec/specs/zoom-controls/spec.md` — update with magnification display, smooth zoom
