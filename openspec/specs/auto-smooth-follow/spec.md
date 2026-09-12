# auto-smooth-follow Specification

## Purpose
Smooth follow-mode map scrolling on the Android Auto map renderer by adding an overrun buffer with sub-region blit, extrapolating the displayed viewport between 1 Hz GPS fixes, and easing corrections on fix arrival.

## Requirements

### Requirement: Overrun render buffer

The system SHALL render the AA map at a size larger than the car surface (approximately 1.2x) and extract the visible region for display, so viewport changes within the overrun region can be served without a full native render.

#### Scenario: Follow-mode render uses overrun size

- **WHEN** the AA renderer performs a full render in follow mode
- **THEN** the render SHALL be at overrun size (approximately 1.2x the surface size)
- **AND** the visible region SHALL be extracted for display

### Requirement: Sub-region blit on viewport change

The system SHALL serve a viewport change within the overrun region by drawing the shifted overrun buffer to the surface instead of performing a full native render.

#### Scenario: Small GPS move served by blit

- **WHEN** the viewport center moves by a delta that stays within the overrun region
- **THEN** the surface SHALL be updated by drawing the overrun buffer shifted by the delta
- **AND** no full native render SHALL be initiated

#### Scenario: Viewport change exits overrun region

- **WHEN** the viewport center moves beyond the overrun region
- **THEN** a full native render SHALL be initiated at the new center
- **AND** the overrun buffer SHALL be refreshed

### Requirement: Display center extrapolation

The system SHALL extrapolate the displayed viewport center between GPS fixes in follow mode. The predicted position SHALL be computed from the last fix position, GPS speed, smoothed heading, and elapsed time since the fix. The map SHALL be scrolled to the predicted position by blitting the overrun buffer each display frame.

#### Scenario: Vehicle moves at constant speed between fixes

- **WHEN** a fix arrives at position P with speed 14 m/s and heading 90°, and 500 ms later no new fix has arrived
- **THEN** the displayed viewport center SHALL be approximately 7 m east of P
- **AND** the map SHALL be scrolled by blitting the overrun buffer, not by a full render

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
- **THEN** the navigation engine SHALL NOT be called with a predicted position
- **AND** routing decisions SHALL be based only on real fixes

### Requirement: Extrapolation loop gating

The system SHALL run the extrapolation display loop only when the renderer is resumed, follow mode is active, and the vehicle is moving. The loop SHALL respect the surface lifecycle (pause, surface destroyed, surface failure) and the shared surface lock across renderers.

#### Scenario: Vehicle stops

- **WHEN** the vehicle speed drops below the movement threshold
- **THEN** the extrapolation loop SHALL stop
- **AND** the displayed viewport SHALL remain at the last position

#### Scenario: Screen paused or surface destroyed

- **WHEN** the owning screen is stopped or the surface is destroyed
- **THEN** the extrapolation loop SHALL stop
- **AND** no surface lock SHALL be attempted

#### Scenario: User pans the map

- **WHEN** the user pans or zooms (follow mode disengaged)
- **THEN** the extrapolation loop SHALL stop
- **AND** the map SHALL follow the user's gestures normally

### Requirement: Fix feed from follow-mode screens

The system SHALL feed every GPS fix's speed, heading and timestamp to the map renderer from every follow-mode car screen (browse map, free driving, navigation view) so the extrapolation display loop can run between fixes. When the fix carries no GPS speed or bearing, the screen SHALL derive them from the movement between consecutive fixes before feeding the renderer.

#### Scenario: Routing view feeds speed to the renderer

- **WHEN** a GPS fix arrives while the navigation view is active in follow mode
- **THEN** the screen passes the fix speed, heading and receipt time to the renderer's fix API
- **AND** the extrapolation loop runs and the map glides toward the predicted position between fixes

#### Scenario: Speed derived from movement when GPS speed is missing

- **WHEN** a fix arrives with no GPS speed (speed unknown) while a follow-mode screen is active
- **THEN** the screen computes the speed from the distance travelled since the previous fix over the fix interval
- **AND** passes that derived speed to the renderer so the extrapolation loop keeps running

#### Scenario: Bearing derived from movement when GPS bearing is missing

- **WHEN** a fix arrives with no GPS bearing while a follow-mode screen is active
- **THEN** the screen uses the movement direction between the consecutive fixes as the heading for the prediction
- **AND** keeps the last effective bearing when the fix moved too little to yield a direction
