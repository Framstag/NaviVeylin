# Render Mode Switch

## Why

`MapRenderer.executeRender` has two rendering paths with very different behavior:

1. **Tile path** (`renderFromTiles`, MapRenderer.kt:530): the visible viewport is covered from
   geographic tiles rendered natively one-per-tile (`renderTilePixels` → `client.renderWithRouteAndPois`)
   and cached in `TileCache`. Fast pans/zooms, but rotated views are composed by rotating the whole
   canvas after the fact, so labels stay north-up.
2. **Direct path** (`MapRenderUtil.renderToBitmap`, MapRenderer.kt:659): the full 1.2× overrun
   buffer is rendered natively in one call, no caching. Correct rotated labels (native), but no
   tile reuse and huge world renders at low zooms (z≤2 ~5s, z=1 hangs — see `MIN_MAG` comment).

Today the path is chosen implicitly: tile path unless the tile path bails (antimeridian, tile render
failure, >4×4 tile guard) or the job is a rotated forced full render (gesture end). Users cannot
choose, and there is no escape hatch when the tile path misbehaves on device.

Additionally, dead code from the old direct-render pipeline is still present and contradicts the
current specs:

- `MapRenderer.blitSubRegion` (MapRenderer.kt:840) — never called, superseded by `trySubRegionBlit`
- `MapRenderer.ANGLE_EPSILON_RAD` (MapRenderer.kt:896) — declared, never used
- `TileCache.computeTileGrid`/`storeTiles`/`compose`/`CompositeResult`/`TILE_SIZE` — the old
  "render full viewport → split into screen-space 256×256 tiles" design, zero callers from
  `MapRenderer`, while the `tile-cache` spec still describes that stale design

## What Changes

- **New persisted setting `renderMode`** in `AppSettings` (enum `RenderMode { TILES, DIRECT }`,
  default `TILES`). `SettingsStorage` uses kotlinx-serialization with `ignoreUnknownKeys = true`,
  so existing `settings.json` files remain valid.
- **Renderer gating**: `MapRenderer` gains a `@Volatile renderMode` field. `executeRender` uses the
  tile path only when `renderMode == TILES` (and the existing `angle`/`forceFullRender` conditions
  hold); otherwise it always renders directly via `MapRenderUtil.renderToBitmap`.
- **State + persistence plumbing**: `MapCanvasUiState` gains `renderMode`; `MapCanvasViewModel`
  loads it at init, exposes a toggle function that persists it and applies it via
  `mapRenderer.invalidateStyle()` (epoch bump, tile cache clear, forced full re-render — already
  used for style-sheet changes).
- **UI affordance**: `LocationOptionsOverlay` (the existing settings `ModalBottomSheet`, which
  already imports `RadioButton`) gains a "Rendering" radio group: *Tile cache* / *Direct*.
- **Dead code removal**: delete `blitSubRegion`, `ANGLE_EPSILON_RAD`, and the unused
  `TileCache` screen-space tile helpers (`computeTileGrid`, `storeTiles`, `compose`,
  `CompositeResult`, `TILE_SIZE`) together with their stale doc comments.
- **Spec drift fix**: update the `tile-cache` spec — its "LRU tile cache" / "Tile composition"
  requirements describe the removed screen-space splitting design; correct them to the actual
  geographic-tile implementation (per-tile native render, dpi-scaled tile size, tile path used only
  in `TILES` mode).

## Capabilities

### New Capabilities

- `render-mode-switch`: user-selectable rendering mode (tile-cached vs direct), persisted across
  restarts, applied to the render pipeline, with a full re-render + cache clear on switch.

### Modified Capabilities

- `tile-cache`: correct stale requirements describing the removed screen-space tile-splitting
  design (`storeTiles`/`compose`) to match the geographic-tile implementation; clarify the cache is
  used only while `TILES` mode is active.

## Impact

- `app/src/main/java/com/naviveylin/data/SettingsStorage.kt` — add `RenderMode` enum + `renderMode`
  field to `AppSettings` (default `TILES`; `ignoreUnknownKeys` keeps old settings valid)
- `app/src/main/java/com/naviveylin/ui/map/MapRenderer.kt` — add `@Volatile renderMode` + setter;
  gate tile path in `executeRender` (~L648); delete `blitSubRegion` (~L840) and `ANGLE_EPSILON_RAD`
  (~L896)
- `app/src/main/java/com/naviveylin/ui/map/MapCanvasViewModel.kt` — `MapCanvasUiState.renderMode`
  (~L84), load at init (~L729), `onSetRenderMode(...)` toggle persisting + applying via
  `invalidateStyle()`
- `app/src/main/java/com/naviveylin/ui/map/MapCanvasScreen.kt` — pass `renderMode`/toggle into
  `LocationOptionsOverlay`
- `app/src/main/java/com/naviveylin/ui/map/LocationOptionsOverlay.kt` — "Rendering" radio group
- `app/src/main/java/com/naviveylin/ui/map/TileCache.kt` — delete `computeTileGrid`, `storeTiles`,
  `compose`, `CompositeResult`, `TILE_SIZE`; update class doc comment
- `openspec/specs/tile-cache/spec.md` — corrected requirements (via change spec delta)
- Tests: `TileCacheTest`, `TileCacheRenderTest`, `MapRendererSmokeTest`,
  `MapRendererRotatedRenderTest` (new mode assertions), new `RenderModeSwitchTest`
  (persistence + renderer gating), `MapCanvasViewModel` settings test
- No new dependencies; no native/JNI changes; no public API changes
