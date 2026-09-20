# Spec Delta

## MODIFIED Requirements

### Requirement: Correction easing

The system SHALL ease the displayed viewport from the predicted position toward the true fix on arrival over approximately 200-300 ms instead of snapping. The easing SHALL absorb extrapolation drift caused by curves and acceleration.

The correction SHALL be forward-only: the displayed position SHALL NEVER move backward along the direction of travel on fix arrival. When the true fix (or the predicted target derived from it) lies behind the current displayed position, the display SHALL hold at its current position until the extrapolation from the new fix advances beyond it. The residual forward lead SHALL be bounded (at most the eased lead of one fix interval) and SHALL NOT accumulate across fixes.

#### Scenario: Prediction drifts before fix arrival

- **WHEN** the vehicle turns a corner and the predicted position is 8 m from the true fix when the fix arrives
- **THEN** the displayed viewport SHALL move smoothly from the predicted position to the true fix over ~200-300 ms
- **AND** the map SHALL NOT jump to the true fix in a single frame

#### Scenario: Fix arrives close to prediction

- **WHEN** the true fix is within 2 m of the predicted position
- **THEN** the correction SHALL be imperceptible (sub-pixel easing)
- **AND** no full render SHALL be triggered solely by the correction

#### Scenario: Fix arrival behind the display holds

- **WHEN** a fix arrives whose position lies behind the current displayed position along the direction of travel (the extrapolated prediction overshot into a curve or a deceleration)
- **THEN** the displayed position SHALL hold at its current position
- **AND** the display SHALL resume advancing once the extrapolation from the new fix passes the held position

#### Scenario: Forward-only across consecutive fixes

- **WHEN** the vehicle drives a series of curves at highway speed and a fix arrives every second
- **THEN** the displayed position SHALL never decrease its progress along the direction of travel between fixes
- **AND** the per-fix backward correction slide SHALL NOT occur (no "moved then jumped back" sawtooth)

### Requirement: Extrapolation loop gating

The system SHALL run the extrapolation display loop only when the renderer is resumed, follow mode is active, and the vehicle is moving. The loop SHALL respect the surface lifecycle (pause, surface destroyed, surface failure) and the shared surface lock across renderers.

While the gate is closed (vehicle stopped), the displayed position and the vehicle marker SHALL remain frozen at the last displayed position — the renderer SHALL NOT reset the displayed position to the last raw fix and SHALL NOT re-anchor the viewport on stationary fixes. A stationary fix may re-seed the displayed position to the fix at most once per moving-to-stopped transition, after which the display SHALL stay frozen until movement resumes and the loop advances from the frozen position.

#### Scenario: Vehicle stops

- **WHEN** the vehicle speed drops below the movement threshold
- **THEN** the extrapolation loop SHALL stop
- **AND** the displayed viewport SHALL remain at the last position
- **AND** the vehicle marker SHALL remain at the displayed position (SHALL NOT fall back to the last raw fix)
- **AND** the map content SHALL NOT re-frame on stationary GPS-jitter fixes

#### Scenario: Vehicle resumes after a stop

- **WHEN** the vehicle starts moving again after a stop
- **THEN** the display SHALL continue advancing from the frozen position toward the prediction
- **AND** the map SHALL NOT snap to the new fix or to a re-initialized display position

#### Scenario: Screen paused or surface destroyed

- **WHEN** the owning screen is stopped or the surface is destroyed
- **THEN** the extrapolation loop SHALL stop
- **AND** no surface lock SHALL be attempted

#### Scenario: User pans the map

- **WHEN** the user pans or zooms (follow mode disengaged)
- **THEN** the extrapolation loop SHALL stop
- **AND** the map SHALL follow the user's gestures normally
