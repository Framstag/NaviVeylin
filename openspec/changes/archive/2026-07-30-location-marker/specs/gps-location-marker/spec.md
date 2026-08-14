## Purpose

Shows the user's current GPS-estimated position on the map as a Compose overlay, with visual indicators for accuracy and heading direction.

## ADDED Requirements

### Requirement: Location marker rendering

The system SHALL render a location marker overlay on top of the map canvas at the user's current GPS position. The marker SHALL consist of:

- An accuracy circle: a semi-transparent filled circle centered on the estimated position, with radius proportional to the GPS horizontal accuracy in meters, projected to screen pixels at the current zoom level
- A direction indicator: a filled compass-style arrow pointing in the direction of travel when bearing is available (bearing ≥ 0), or pointing north (0°) when bearing is unavailable (bearing < 0)
- The arrow SHALL be centered on the estimated position, not on the accuracy circle edge
- The marker SHALL be drawn using Compose Canvas drawing primitives, not via JNI/Cairo

#### Scenario: Accuracy circle reflects GPS accuracy

- **WHEN** GPS reports horizontal accuracy of 10 meters at zoom level 14
- **THEN** the accuracy circle SHALL have a screen radius of approximately 10 meters projected to screen pixels at zoom level 14

#### Scenario: Direction arrow shown when bearing is known

- **WHEN** GPS reports bearing ≥ 0 (e.g., bearing = 45 degrees)
- **THEN** the marker SHALL render as a filled arrow rotated to match the bearing angle

#### Scenario: Arrow shown when bearing is unknown

- **WHEN** GPS reports bearing < 0 (bearing unavailable)
- **THEN** the marker SHALL render as a filled arrow pointing north (0°)

#### Scenario: Marker updates on each GPS fix

- **WHEN** a new GPS location is received with different lat/lon/bearing/accuracy
- **THEN** the marker SHALL re-render at the new position within 100ms

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

#### Scenario: Accuracy circle hidden with good GPS fix

- **WHEN** GPS accuracy is good (accuracy circle radius < 20px on screen)
- **THEN** the accuracy circle SHALL NOT be rendered
- **THEN** only the direction arrow SHALL be shown
