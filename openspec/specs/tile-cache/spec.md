# Tile Cache Specification

## Purpose

Reduce redundant native render calls by splitting rendered buffers into 256×256 tiles and reusing cached tiles across consecutive renders at the same zoom level.

## Requirements

### Requirement: LRU tile cache

The system SHALL maintain an LRU (least-recently-used) cache of rendered map tiles.

- Tiles SHALL be geographic tiles keyed by `(zoomLevel, tileX, tileY, renderDpi)` at the current map
  magnification and the DPI the tile was rendered with
- The tile pixel size SHALL be 256 px at 96 dpi, scaled by the render DPI of the request that
  produced the tile
- Each missing tile SHALL be rendered natively as an individual viewport centered on the tile at
  the tile's magnification
- The cache SHALL have a configurable maximum size (default 200 tiles)
- When cache size exceeds the maximum, the least recently accessed tile SHALL be evicted
- Cache operations SHALL be thread-safe
- The cache SHALL be used only while `TILES` render mode is active
- A tile rendered at one DPI SHALL NOT be served for a frame rendered at another DPI

#### Scenario: Tile stored after full render

- **WHEN** a render at zoom level 12 and DPI D requires a geographic tile that is not cached
- **THEN** the tile is rendered with a single native render call sized for one tile
- **THEN** the tile is stored in the cache with key (12, tileX, tileY, D)

#### Scenario: Cached tile reused on subsequent render

- **WHEN** a render is requested at the same zoom level and the same DPI as a previous render
- **THEN** the system checks the tile cache for each tile
- **THEN** cached tiles are composed into the result without calling native render
- **THEN** only missing tiles trigger a native render call

#### Scenario: LRU eviction

- **WHEN** the cache exceeds 200 tiles
- **THEN** the least recently accessed tile is evicted
- **THEN** the evicted tile must be re-rendered if needed again

#### Scenario: A different render DPI does not serve stale tiles

- **WHEN** a frame is rendered at a DPI different from the DPI a cached tile was rendered with
- **THEN** that tile is not composed into the frame
- **THEN** the tile is rendered natively at the frame's DPI

### Requirement: Epoch-based cache invalidation

The system SHALL invalidate cached tiles when the viewport state changes significantly.

- Each cache entry SHALL store the epoch at which it was created
- On cache lookup, the entry's epoch SHALL match the current epoch
- On epoch change, all tiles with stale epochs SHALL be purged
- The epoch SHALL be incremented on zoom level change, rotation, or overlay data change

#### Scenario: Stale tiles purged on zoom change

- **WHEN** the user zooms from level 12 to level 13
- **THEN** the epoch is incremented
- **THEN** all tiles with the old epoch are purged from the cache
- **THEN** subsequent renders at level 13 start with an empty cache

### Requirement: Tile composition

The system SHALL compose a screen-sized frame from the geographic tiles covering the visible
viewport, rendering only missing tiles natively.

- The composition SHALL compute the tile grid covering the visible geo bounds at the current
  magnification (all four screen corners when the viewport is rotated)
- For each cached tile, its bitmap SHALL be drawn at the tile's projected screen position
- For rotated viewports, the composition SHALL place tiles north-up and rotate the whole canvas
  about the viewport center
- If the viewport spans the antimeridian or the tile grid exceeds the sanity guard, the tile path
  SHALL bail and the direct native render SHALL be used instead
- If no tile could be composed or rendered, the tile path SHALL return null and the caller SHALL
  fall back to a full native render

#### Scenario: Partial cache hit

- **WHEN** a render is requested and some viewport tiles are cached and others are not
- **THEN** the composed frame contains the cached tiles
- **THEN** the system renders only the missing tiles natively
- **THEN** the newly rendered tiles are added to the cache

#### Scenario: Tile-path bail on antimeridian

- **WHEN** the visible viewport crosses the antimeridian in `TILES` mode
- **THEN** the tile path returns no frame
- **THEN** the direct native render produces the frame

### Requirement: Cached tiles contain only static map content

The system SHALL store only immutable map content in cached tiles. Ephemeral per-frame overlays (e.g., the GPS location marker) SHALL NOT be part of tile rendering, tile storage, or tile composition.

- A tile SHALL be renderable once and reused any number of times without ever surfacing stale overlay pixels
- Overlay data SHALL NOT invalidate or re-render tiles
- Tile content SHALL depend only on geographic position, zoom level, style, and the render DPI — never on transient UI state
- Two tiles with identical geographic position, zoom level and style but different render DPI SHALL be treated as different content

#### Scenario: Tile rendered while GPS marker active

- **WHEN** a tile is rendered while the GPS location marker is visible
- **THEN** the tile bitmap SHALL contain no marker pixels

#### Scenario: Marker moves and cached tiles are reused

- **WHEN** the marker moves to a new position and the user pans within the same zoom level
- **THEN** the reused cached tiles SHALL show the map content without any ghost marker at the old position

#### Scenario: Overlay change does not purge cache

- **WHEN** the marker appears, moves, or disappears and no map content changed
- **THEN** the tile cache SHALL NOT be invalidated or re-rendered

#### Scenario: DPI is content, not transient state

- **WHEN** a surface renders tiles at its own DPI and another surface's frames use a different DPI in the same process
- **THEN** no tile rendered for one DPI is composed into a frame of the other
- **THEN** neither surface's tile set is disturbed by the other's overlay or viewport changes
