## MODIFIED Requirements

### Requirement: Location marker rendering

The system SHALL render a location marker overlay on top of the map canvas at the user's current GPS position. The marker SHALL consist of:

- An accuracy circle: a semi-transparent filled circle centered on the estimated position, with radius proportional to the GPS horizontal accuracy in meters, projected to screen pixels at the current zoom level
- A direction indicator: a filled compass-style arrow pointing in the direction of travel when bearing is available (bearing ≥ 0), or pointing north (0°) when bearing is unavailable (bearing < 0), **corrected for current map rotation**
- The arrow SHALL point in the direction of travel whenever bearing is available, **regardless of the map orientation mode** (north-up or follow-direction). Orientation mode controls only the map rotation; it SHALL NOT change the arrow's travel-direction semantics. `bearing < 0` means "bearing unavailable" only — never "north-up mode active"
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

#### Scenario: Arrow points in travel direction in north-up mode

- **WHEN** the map is in north-up orientation (rotation 0°) and GPS reports bearing 180° (driving south)
- **THEN** the marker SHALL render as a filled arrow pointing south (180°) on the screen, in the direction of travel
- **AND** the arrow SHALL NOT point north

#### Scenario: Arrow points in travel direction in follow-direction mode

- **WHEN** the map is in follow-direction orientation (rotated to match the bearing) and GPS reports bearing 180°
- **THEN** the marker SHALL render as a filled arrow pointing up (0° on screen), aligned with the map rotation and the direction of travel

#### Scenario: Marker updates on each GPS fix

- **WHEN** a new GPS location is received with different lat/lon/bearing/accuracy
- **THEN** the marker SHALL re-render at the new position within 100ms

#### Scenario: Accuracy circle hidden with good GPS fix

- **WHEN** GPS accuracy is good (accuracy circle radius < 20px on screen)
- **THEN** the accuracy circle SHALL NOT be rendered
- **THEN** only the direction arrow SHALL be shown
