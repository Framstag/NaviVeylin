# Tile Cache Specification

## Purpose

Reduce redundant native render calls by splitting rendered buffers into 256×256 tiles and reusing cached tiles across consecutive renders at the same zoom level.

## Requirements

### Requirement: LRU tile cache

The system SHALL maintain an LRU (least-recently-used) cache of rendered map tiles.

- Each tile SHALL be 256×256 pixels
- The cache SHALL have a configurable maximum size (default 200 tiles)
- Tiles SHALL be keyed by (zoomLevel, tileX, tileY)
- When cache size exceeds the maximum, the least recently accessed tile SHALL be evicted
- Cache operations SHALL be thread-safe

#### Scenario: Tile stored after full render

- **WHEN** a full render completes at zoom level 12
- **THEN** the rendered pixel buffer is split into 256×256 tiles
- **THEN** each tile is stored in the cache with key (12, col, row)

#### Scenario: Cached tile reused on subsequent render

- **WHEN** a render is requested at the same zoom level as a previous render
- **THEN** the system checks the tile cache for each tile
- **THEN** cached tiles are composed into the result without calling native render
- **THEN** only missing tiles trigger a native render call

#### Scenario: LRU eviction

- **WHEN** the cache exceeds 200 tiles
- **THEN** the least recently accessed tile is evicted
- **THEN** the evicted tile must be re-rendered if needed again

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

The system SHALL compose a full image from cached tiles, filling missing regions with a placeholder.

- The composition SHALL iterate over the tile grid covering the render area
- For each cached tile, its pixels SHALL be copied into the output buffer at the correct position
- For each missing tile, the corresponding region SHALL be left as a neutral background
- The composition SHALL report the number of missing tiles

#### Scenario: Partial cache hit

- **WHEN** 80 of 100 tiles are cached
- **THEN** the composed image contains 80 cached tiles
- **THEN** the system renders only the 20 missing tiles
- **THEN** the newly rendered tiles are added to the cache
