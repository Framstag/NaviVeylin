## MODIFIED Requirements

### Requirement: Rotation gesture does not conflict with pinch-to-zoom

The system SHALL handle two-finger rotation and pinch-to-zoom simultaneously without gesture conflicts. Zoom SHALL be derived from the finger-distance ratio relative to the distance at the moment the second finger touches down, and the magnification SHALL change only when the gesture ends.

#### Scenario: Simultaneous rotate and zoom

- **WHEN** the user places two fingers on the map canvas
- **AND** both rotates and pinches
- **THEN** the map SHALL both rotate and zoom in response to the combined gesture
- **AND** both operations SHALL be applied live to the currently displayed map without any render call during the gesture
- **AND** both the rotation and the magnification change SHALL be committed with a single re-render on gesture end

#### Scenario: Pinch without rotation

- **WHEN** the user pinches two fingers without rotating them
- **THEN** the map SHALL zoom without changing its rotation angle

#### Scenario: Zoom steps on gesture end with limits

- **WHEN** the user pinches two fingers and lifts them
- **THEN** the magnification SHALL change by the nearest whole magnification level implied by the total distance ratio (rounded via `round(log2(ratio))`)
- **AND** the change SHALL be bounded to ±2 magnification levels per gesture
- **AND** the resulting magnification SHALL be clamped to the application limits (4–20)

