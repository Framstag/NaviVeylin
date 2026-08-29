## MODIFIED Requirements

### Requirement: Follow-mode render throttling

The system SHALL trigger a follow-mode map render when the predicted position exits the overrun region of the current front buffer, not on every GPS fix. Renders SHALL still be coalesced by the render debounce so no more than one render is initiated per debounce window.

#### Scenario: Multiple GPS ticks arrive within 200 ms

- **WHEN** two or more distinct GPS fixes arrive within 200 ms
- **THEN** only one follow-mode render is initiated
- **AND** the navigation engine still receives every fix

#### Scenario: Prediction stays inside overrun region

- **WHEN** the predicted position remains within the overrun region of the front buffer
- **THEN** no full render is initiated
- **AND** the map is scrolled by blitting the front buffer

### Requirement: Center position smoothing

The system SHALL NOT apply exponential smoothing to the follow-mode map center. The render target center SHALL be the latest raw (or navigation-filtered) GPS fix. Between fixes, the displayed viewport center SHALL be the predicted position extrapolated from the last fix, speed, and heading. A jump larger than 500 m resets the center directly to handle teleports.

#### Scenario: GPS position jitters by 3–5 m while the vehicle is stationary

- **WHEN** consecutive fixes differ by a few meters only
- **THEN** the render target center updates to the new fix
- **AND** the marker remains exactly on the vehicle position

#### Scenario: A GPS fix arrives while a render is still coalesced

- **WHEN** the latest fix is stored as the render target center even though the render is throttled
- **THEN** the next rendered frame is centered on the latest position
- **AND** the camera does not catch up in one big jump

#### Scenario: Displayed center is predicted between fixes

- **WHEN** the vehicle moves between two fixes and the predicted position is inside the overrun region
- **THEN** the displayed viewport center SHALL be the predicted position
- **AND** the render target center SHALL remain the latest raw fix
