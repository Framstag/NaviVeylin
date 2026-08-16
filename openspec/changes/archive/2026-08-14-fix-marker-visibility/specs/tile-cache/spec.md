# tile-cache Specification (Delta)

## ADDED Requirements

### Requirement: Cached tiles contain only static map content

The system SHALL store only immutable map content in cached tiles. Ephemeral per-frame overlays (e.g., the GPS location marker) SHALL NOT be part of tile rendering, tile storage, or tile composition.

- A tile SHALL be renderable once and reused any number of times without ever surfacing stale overlay pixels
- Overlay data SHALL NOT invalidate or re-render tiles
- Tile content SHALL depend only on geographic position, zoom level, and style — never on transient UI state

#### Scenario: Tile rendered while GPS marker active

- **WHEN** a tile is rendered while the GPS location marker is visible
- **THEN** the tile bitmap SHALL contain no marker pixels

#### Scenario: Marker moves and cached tiles are reused

- **WHEN** the marker moves to a new position and the user pans within the same zoom level
- **THEN** the reused cached tiles SHALL show the map content without any ghost marker at the old position

#### Scenario: Overlay change does not purge cache

- **WHEN** the marker appears, moves, or disappears and no map content changed
- **THEN** the tile cache SHALL NOT be invalidated or re-rendered
