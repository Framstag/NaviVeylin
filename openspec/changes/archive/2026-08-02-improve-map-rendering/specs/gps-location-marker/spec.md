# gps-location-marker Specification (Delta)

## ADDED Requirements

### Requirement: Direction arrow corrects for map rotation

The system SHALL render the direction arrow at the GPS bearing angle adjusted by the current map rotation.

- When map rotation is 0°, the arrow SHALL point in the direction of travel on the screen.
- When the map is rotated by R degrees, the arrow SHALL be drawn at angle `(bearing - R)` on the screen.

#### Scenario: Rotated map keeps arrow aligned with travel

- **WHEN** GPS bearing is 90° and map rotation is 30°
- **THEN** the arrow SHALL be drawn at 60° on the screen

#### Scenario: Unknown bearing on rotated map

- **WHEN** GPS bearing is unavailable (bearing < 0) and map rotation is 45°
- **THEN** the arrow SHALL be drawn at -45° on the screen so it points north on the map

### Requirement: Marker position stable during map rotation

The system SHALL keep the GPS marker at the same geographic screen position while the map rotates around the viewport center.

- The marker projection SHALL use the same rotation value that the native renderer uses.
- The marker SHALL be reprojected on every rotation change.

#### Scenario: Map rotates around marker

- **WHEN** the map rotates and the GPS position is near the viewport center
- **THEN** the marker SHALL remain over the same map feature
- **THEN** the accuracy circle SHALL remain centered on the same map feature

## MODIFIED Requirements

### Requirement: Location marker rendering

The system SHALL render a location marker overlay on top of the map canvas at the user's current GPS position. The marker SHALL consist of:

- An accuracy circle: a semi-transparent filled circle centered on the estimated position, with radius proportional to the GPS horizontal accuracy in meters, projected to screen pixels at the current zoom level
- A direction indicator: a filled compass-style arrow pointing in the direction of travel when bearing is available (bearing ≥ 0), or pointing north (0°) when bearing is unavailable (bearing < 0), **corrected for current map rotation**
- The arrow SHALL be centered on the estimated position, not on the accuracy circle edge
- The marker SHALL be drawn using Compose Canvas drawing primitives, not via JNI/Cairo

#### Scenario: Accuracy circle reflects GPS accuracy

- **WHEN** GPS reports horizontal accuracy of 10 meters at zoom level 14
- **THEN** the accuracy circle SHALL have a screen radius of approximately 10 meters projected to screen pixels at zoom level 14

#### Scenario: Direction arrow shown when bearing is known

- **WHEN** GPS reports bearing ≥ 0 (e.g., bearing = 45 degrees) and map rotation is 0°
- **THEN** the marker SHALL render as a filled arrow rotated to match the bearing angle on the screen

#### Scenario: Arrow shown when bearing is unknown

- **WHEN** GPS reports bearing < 0 (bearing unavailable)
- **THEN** the marker SHALL render as a filled arrow pointing north (0°) on the map, which is `(0° - mapRotation)` on the screen

#### Scenario: Marker updates on each GPS fix

- **WHEN** a new GPS location is received with different lat/lon/bearing/accuracy
- **THEN** the marker SHALL re-render at the new position within 100ms

#### Scenario: Accuracy circle hidden with good GPS fix

- **WHEN** GPS accuracy is good (accuracy circle radius < 20px on screen)
- **THEN** the accuracy circle SHALL NOT be rendered
- **THEN** only the direction arrow SHALL be shown

### Requirement: Marker position tracks map viewport

The marker SHALL project the GPS coordinate to screen pixel coordinates using the current map viewport (center, zoom, rotation). The marker SHALL move correctly when the user pans or zooms the map.

#### Scenario: Marker moves during pan

- **WHEN** the user pans the map
- **THEN** the marker SHALL remain at the correct geographic position relative to map features

#### Scenario: Marker repositions on zoom

- **WHEN** the user zooms in or out
- **THEN** the marker SHALL re-project to the correct screen position at the new zoom level

#### Scenario: Marker hidden when off-screen

- **WHEN** the user's GPS position is outside the visible map viewport
- **THEN** the marker SHALL NOT be rendered (no off-screen indicators)

#### Scenario: Marker position is stable during map rotation

- **WHEN** the map rotates while the GPS position is visible
- **THEN** the marker SHALL stay at the same geographic location on screen
- **THEN** the direction arrow SHALL rotate to remain aligned with the direction of travel
