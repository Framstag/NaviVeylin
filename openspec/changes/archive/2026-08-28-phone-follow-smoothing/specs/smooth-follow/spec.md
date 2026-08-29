## Purpose

Smooth follow-mode map scrolling on the phone renderer by extrapolating the displayed viewport between 1 Hz GPS fixes and easing corrections on fix arrival.

## ADDED Requirements

### Requirement: Display center extrapolation

The system SHALL extrapolate the displayed viewport center between GPS fixes in follow mode. The predicted position SHALL be computed from the last fix position, GPS speed, smoothed heading, and elapsed time since the fix: `predicted = lastFix + speed * heading * (now - fixTime)`. The map SHALL be scrolled to the predicted position by blitting the front buffer each display frame.

#### Scenario: Vehicle moves at constant speed between fixes

- **WHEN** a fix arrives at position P with speed 14 m/s and heading 90°, and 500 ms later no new fix has arrived
- **THEN** the displayed viewport center SHALL be approximately 7 m east of P
- **AND** the map SHALL be scrolled by blitting the front buffer, not by a full render

#### Scenario: No speed or heading available

- **WHEN** the last fix has no speed or heading (speed unknown, bearing < 0)
- **THEN** the displayed viewport SHALL remain at the last fix position until the next fix
- **AND** no extrapolation SHALL be applied

### Requirement: Correction easing

The system SHALL ease the displayed viewport from the predicted position toward the true fix on arrival over approximately 200-300 ms instead of snapping. The easing SHALL absorb extrapolation drift caused by curves and acceleration.

#### Scenario: Prediction drifts before fix arrival

- **WHEN** the vehicle turns a corner and the predicted position is 8 m from the true fix when the fix arrives
- **THEN** the displayed viewport SHALL move smoothly from the predicted position to the true fix over ~200-300 ms
- **AND** the map SHALL NOT jump to the true fix in a single frame

#### Scenario: Fix arrives close to prediction

- **WHEN** the true fix is within 2 m of the predicted position
- **THEN** the correction SHALL be imperceptible (sub-pixel easing)
- **AND** no full render SHALL be triggered solely by the correction

### Requirement: Display-only prediction

The system SHALL feed predicted positions only to the map display (viewport blit and marker overlay). The navigation engine SHALL receive only real GPS fixes.

#### Scenario: Predicted position never reaches navigation engine

- **WHEN** the display extrapolates the viewport between fixes
- **THEN** `NavigationController.processLocation()` SHALL NOT be called with a predicted position
- **AND** routing decisions SHALL be based only on real fixes

### Requirement: Extrapolation loop gating

The system SHALL run the extrapolation display loop only in follow mode while the vehicle is moving. The loop SHALL stop when the vehicle is stationary (speed below a threshold) or follow mode is disengaged.

#### Scenario: Vehicle stops

- **WHEN** the vehicle speed drops below the movement threshold
- **THEN** the extrapolation loop SHALL stop
- **AND** the displayed viewport SHALL remain at the last position

#### Scenario: User pans the map

- **WHEN** the user pans or zooms (follow mode disengaged)
- **THEN** the extrapolation loop SHALL stop
- **AND** the map SHALL follow the user's gestures normally

### Requirement: Prediction state update

The system SHALL update the prediction state (position, speed, heading, fix time) on every GPS fix. A fix SHALL update the state without necessarily triggering a render.

#### Scenario: Fix updates prediction state only

- **WHEN** a new fix arrives while the predicted position is still inside the overrun region
- **THEN** the prediction state SHALL be updated to the new fix
- **AND** no full render SHALL be initiated
