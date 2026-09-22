# Spec Delta

## MODIFIED Requirements

### Requirement: Extrapolation loop gating

The system SHALL run the extrapolation display loop only when the renderer is resumed, follow mode is active, and the vehicle is moving. The loop SHALL respect the surface lifecycle (pause, host surface destruction, surface failure). The surface's lifetime is owned by the session's car surface ownership (capability `car-host-fault-isolation`), not by individual renderers: a renderer SHALL stop locking the surface while its screen is stopped, and SHALL NOT release a surface that it does not own.

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

#### Scenario: Screen stopped while the session keeps the surface

- **WHEN** the owning screen is stopped but the session's surface has not been reported destroyed by the host
- **THEN** the extrapolation loop SHALL stop
- **AND** the renderer SHALL NOT release that surface
- **AND** the loop SHALL resume from the current displayed position when the screen is started again

#### Scenario: User pans the map

- **WHEN** the user pans or zooms (follow mode disengaged)
- **THEN** the extrapolation loop SHALL stop
- **AND** the map SHALL follow the user's gestures normally
