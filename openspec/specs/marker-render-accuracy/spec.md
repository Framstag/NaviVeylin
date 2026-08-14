# Marker Render Accuracy

## Purpose

Ensure GPS location markers and favorite markers stay visually aligned with map geometry and correctly indicate heading during all viewport changes.
## Requirements
### Requirement: GPS marker position matches projected map coordinate

The system SHALL project the GPS coordinate to the screen pixel using the same projection and state that the native renderer uses for the current map viewport.

- The projection SHALL use the viewport center, current magnification, display DPI, and map rotation.
- The marker screen position SHALL be recomputed on every GPS fix and on every viewport change.

#### Scenario: Marker stays on road while panning

- **WHEN** the user pans the map and the GPS location is on a visible road
- **THEN** the location marker SHALL remain on that road relative to the map features

#### Scenario: Marker does not drift during zoom transition

- **WHEN** the user pinch-zooms the map
- **THEN** the GPS marker screen position SHALL be recomputed at the current placeholder magnification
- **THEN** the marker SHALL land on the same geographic point after the native render completes

### Requirement: GPS direction arrow reflects true heading

The system SHALL render the direction arrow at the GPS bearing angle corrected for the current map rotation.

- When map rotation is 0°, the arrow SHALL point in the direction of travel on the screen.
- When the map is rotated counter-clockwise by R degrees, the arrow SHALL be rotated by `(bearing + R)` so the arrow continues to point in the direction of travel relative to the screen.

#### Scenario: North-up map with known bearing

- **WHEN** map rotation is 0° and GPS bearing is 45°
- **THEN** the direction arrow SHALL be drawn at 45° on the screen

#### Scenario: Rotated map with known bearing

- **WHEN** map rotation is 30° and GPS bearing is 45°
- **THEN** the direction arrow SHALL be drawn at 75° on the screen

### Requirement: Favorite markers align with native-rendered POIs

The system SHALL pass favorite marker coordinates to the native renderer so that favorite markers are drawn by the same Cairo pipeline as other map symbols.

- Favorite coordinates SHALL be converted to the native coordinate format expected by `renderWithRouteAndPois`.
- The marker symbol type `_favorite` SHALL be used.

#### Scenario: Favorite marker lands on building

- **WHEN** a favorite is placed on a building and the map is rendered
- **THEN** the favorite marker SHALL appear on that building in the native map render

#### Scenario: Favorite marker survives pan and zoom

- **WHEN** the user pans or zooms the map
- **THEN** the favorite marker SHALL remain anchored to its geographic location in the native render

