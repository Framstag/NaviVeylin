# Render Mode Switch Specification (Delta)

## ADDED Requirements

### Requirement: User-selectable render mode

The system SHALL expose a persisted user setting that selects the map rendering mode: `TILES`
(geographic tile cache) or `DIRECT` (full-viewport native render, no tile cache).

- The setting SHALL default to `TILES`
- The setting SHALL be persisted across app restarts (stored with the existing app settings)
- Persisted settings written before this setting existed SHALL remain loadable (backward
  compatible)
- The UI SHALL offer both modes with clear, neutral labels and SHALL reflect the current selection

#### Scenario: First launch uses tile mode

- **WHEN** a user launches the app with no existing settings file
- **THEN** rendering runs in `TILES` mode

#### Scenario: Old settings file stays valid

- **WHEN** a settings file from a previous app version (without `renderMode`) is loaded
- **THEN** the app starts in `TILES` mode and no settings data is lost

#### Scenario: Selection persists across restarts

- **WHEN** the user selects `DIRECT` mode
- **THEN** the selection is persisted
- **WHEN** the user restarts the app
- **THEN** rendering runs in `DIRECT` mode

### Requirement: Render pipeline honors the selected mode

The render pipeline SHALL use the tile path only in `TILES` mode; in `DIRECT` mode the viewport
SHALL be rendered natively as a full buffer without consulting the tile cache.

- In `TILES` mode the existing tile-path conditions (north-up fast path, rotation live preview)
  SHALL keep applying
- In `DIRECT` mode every render SHALL be a full native render of the overrun-sized buffer; cached
  tiles SHALL NOT be composed
- Both modes SHALL keep delivering a screen-sized frame through the existing emission path
  (atomic frame emission, epoch-based stale discard, sub-region blit pans)

#### Scenario: Direct mode ignores the tile cache

- **WHEN** the user switches to `DIRECT` mode
- **WHEN** a render is requested
- **THEN** the frame is produced by a full native render
- **THEN** no tile is read from or written to the tile cache

#### Scenario: Tile mode falls back on tile-path failure

- **WHEN** the user is in `TILES` mode
- **WHEN** the tile path cannot serve the viewport (e.g., antimeridian, tile render failure)
- **THEN** the render falls back to the direct native render as today

### Requirement: Mode switch re-renders from scratch

Switching the render mode SHALL invalidate all cached tiles and SHALL trigger a full re-render so
no tiles or buffers from the previous mode survive.

- The tile cache SHALL be cleared on switch
- The render epoch SHALL be incremented on switch so in-flight results from the previous mode are
  discarded
- A forced full render SHALL be submitted immediately after the switch

#### Scenario: Switch from tiles to direct leaves no residue

- **WHEN** the user switches from `TILES` to `DIRECT`
- **THEN** the tile cache is cleared
- **THEN** the next frame is a full direct render showing current map content
- **THEN** subsequent pans and zooms show no previously cached tile content

#### Scenario: Switch mid-gesture discards the in-flight frame

- **WHEN** the user switches mode while a render job is in flight
- **WHEN** the in-flight job completes after the switch
- **THEN** its result is discarded (stale epoch) and the next render uses the new mode

### Requirement: Dead direct-render remnants removed

The codebase SHALL NOT contain unused remnants of the removed direct-render tile-splitting design:
`MapRenderer.blitSubRegion`, `MapRenderer.ANGLE_EPSILON_RAD`, and the `TileCache` screen-space
helpers (`computeTileGrid`, `storeTiles`, `compose`, `CompositeResult`, `TILE_SIZE`).

- Removed code SHALL have no callers (dead code)
- Documentation and specs SHALL NOT reference the removed design

#### Scenario: No dead-code references remain

- **WHEN** the change is complete
- **THEN** a search for `blitSubRegion`, `ANGLE_EPSILON_RAD`, `storeTiles`, and `computeTileGrid`
  finds no references outside the change's archived artifacts
