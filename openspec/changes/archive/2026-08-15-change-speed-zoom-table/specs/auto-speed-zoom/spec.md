# auto-speed-zoom Specification

## MODIFIED Requirements

### Requirement: SPEED_ZOOM_TABLE with narrowed range
The system SHALL use a speed-to-magnification table with a range of 18→12 (6 levels), with linear interpolation between breakpoints. Walking speeds (≤6 km/h) SHALL target magnification 18–17.5, and speeds up to 60 km/h SHALL target magnification at least 16 so building names and numbers are rendered.

#### Scenario: Speed of 5 km/h
- **GIVEN** the vehicle is walking at 5 km/h
- **WHEN** the auto-zoom computes the target magnification
- **THEN** the target SHALL be approximately 17.6

#### Scenario: City speed of 30 km/h
- **GIVEN** the vehicle is driving at 30 km/h
- **WHEN** the auto-zoom computes the target magnification
- **THEN** the target SHALL be 16.0

#### Scenario: Suburban speed of 60 km/h
- **GIVEN** the vehicle is driving at 60 km/h
- **WHEN** the auto-zoom computes the target magnification
- **THEN** the target SHALL be 16.0
- **AND** building names and numbers SHALL be rendered (magnification ≥ 16)

#### Scenario: Speed of 100 km/h
- **GIVEN** the vehicle is driving at 100 km/h
- **WHEN** the auto-zoom computes the target magnification
- **THEN** the target SHALL be approximately 12.75

### Requirement: Auto-zoom uses linear interpolation between speed breakpoints
The system SHALL use a configurable lookup table of speed-to-magnification pairs with linear interpolation between entries to produce smooth zoom transitions.

#### Scenario: Speed between breakpoints
- **GIVEN** a speed of 75 km/h
- **WHEN** the breakpoints are (60 km/h → mag 16) and (90 km/h → mag 13)
- **THEN** the computed magnification is approximately 14.5 (linearly interpolated)
