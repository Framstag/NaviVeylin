## ADDED Requirements

### Requirement: Compass visually differentiates orientation modes

The compass widget SHALL have a visually distinct appearance between "always north" (north-up) and "follow direction" modes, so the user can tell at a glance which mode is active.

#### Scenario: Always north mode shows fixed north indicator

- **WHEN** the orientation mode is "always north" (north-up)
- **THEN** the compass SHALL display a prominent north indicator (e.g., a red "N" or arrow)
- **AND** the compass body SHALL use a neutral, static appearance

#### Scenario: Follow direction mode shows directional indicator

- **WHEN** the orientation mode is "follow direction"
- **THEN** the compass SHALL display a directional indicator that visually conveys the map is rotating to match the driving direction
- **AND** the compass body SHALL use a distinct color or style compared to north-up mode

### Requirement: Thicker GPS fix status ring

The GPS fix status ring around the compass SHALL be thicker than the current implementation for better visibility.

#### Scenario: Ring is visibly thicker

- **WHEN** the compass is displayed
- **THEN** the GPS fix status ring SHALL have a stroke width of at least 3dp
- **AND** the ring SHALL be clearly visible at a glance

## MODIFIED Requirements

### Requirement: GPS fix status ring

The system SHALL display a colored ring around the compass that indicates GPS fix quality using light colors. The ring SHALL have a minimum stroke width of 3dp.

#### Scenario: No GPS fix shows light red ring

- **WHEN** no GPS location fix is available
- **THEN** the compass ring SHALL display a light red color
- **AND** the ring SHALL be at least 3dp thick

#### Scenario: Poor GPS accuracy shows light yellow ring

- **WHEN** a GPS fix is available with accuracy worse than 50 meters
- **THEN** the compass ring SHALL display a light yellow color
- **AND** the ring SHALL be at least 3dp thick

#### Scenario: Good GPS fix shows light green ring

- **WHEN** a GPS fix is available with accuracy ≤50 meters
- **THEN** the compass ring SHALL display a light green color
- **AND** the ring SHALL be at least 3dp thick
