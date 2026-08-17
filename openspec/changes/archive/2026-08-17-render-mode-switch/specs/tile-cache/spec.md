# Tile Cache Specification (Delta)

## MODIFIED Requirements

### Requirement: LRU tile cache

The system SHALL maintain an LRU (least-recently-used) cache of rendered map tiles.

- Tiles SHALL be geographic tiles keyed by `(zoomLevel, tileX, tileY)` at the current map
  magnification
- The tile pixel size SHALL be 256 px at 96 dpi, scaled by the device density
- Each missing tile SHALL be rendered natively as an individual viewport centered on the tile at
  the tile's magnification
- The cache SHALL have a configurable maximum size (default 200 tiles)
- When cache size exceeds the maximum, the least recently accessed tile SHALL be evicted
- Cache operations SHALL be thread-safe
- The cache SHALL be used only while `TILES` render mode is active

#### Scenario: Tile stored after full render

- **WHEN** a render at zoom level 12 requires a geographic tile that is not cached
- **THEN** the tile is rendered with a single native render call sized for one tile
- **THEN** the tile is stored in the cache with key (12, tileX, tileY)

#### Scenario: Cached tile reused on subsequent render

- **WHEN** a render is requested at the same zoom level as a previous render
- **THEN** the system checks the tile cache for each tile
- **THEN** cached tiles are composed into the result without calling native render
- **THEN** only missing tiles trigger a native render call

#### Scenario: LRU eviction

- **WHEN** the cache exceeds 200 tiles
- **THEN** the least recently accessed tile is evicted
- **THEN** the evicted tile must be re-rendered if needed again

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
