## MODIFIED Requirements

### Requirement: Eased zoom animation on discrete zoom input

The system SHALL animate the displayed map from the current displayed scale toward the target magnification whenever the magnification changes without an active pinch gesture — discrete zoom input (zoom buttons, scroll wheel, keyboard plus/minus) as well as speed-driven auto-zoom commits during follow mode. The zoom animation SHALL scale the previously rendered front buffer with an ease-out curve; discrete input animates over approximately 200-300 ms, while auto-zoom commits animate over approximately 650 ms (slower, matching the driving zoom cadence so the easing does not read as pumping). The displayed magnification level SHALL update to the target immediately when the animation starts.

#### Scenario: Zoom in button triggers animated transition

- **WHEN** the user taps the zoom in button while no pinch gesture is active
- **THEN** the displayed map SHALL scale up smoothly from the currently rendered magnification toward the target magnification over ~250 ms with an ease-out curve
- **AND** no single-frame jump between zoom levels SHALL be visible

#### Scenario: Scroll wheel zoom animates

- **WHEN** the user zooms with the scroll wheel
- **THEN** the displayed map SHALL animate toward the target magnification with an eased scale
- **AND** repeated wheel ticks during the animation SHALL be handled without visual snapping

#### Scenario: Auto-zoom commit animates at the slower duration

- **GIVEN** follow mode and auto-zoom are active and the vehicle is centered on screen
- **WHEN** the speed-derived auto-zoom commits a new fractional magnification
- **THEN** the displayed map SHALL ease from the currently rendered scale to the target magnification over approximately 650 ms with an ease-out curve
- **AND** the commit SHALL NOT appear as a single-frame jump in map content

### Requirement: Retracking on rapid zoom input

While a zoom animation is running, further zoom input SHALL retrack the running animation smoothly from the currently displayed scale toward the new target magnification. The system SHALL NOT snap back to the start scale of the previous animation.

#### Scenario: Zoom out tapped during zoom in animation

- **WHEN** a zoom in animation is halfway to its target and the user taps the zoom out button
- **THEN** the displayed scale SHALL smoothly reverse from its current value toward the new target magnification
- **AND** the animation SHALL NOT jump back to the zoom in animation's start scale

#### Scenario: Repeated scroll wheel zoom

- **WHEN** the user zooms with the scroll wheel several times in quick succession
- **THEN** each tick SHALL retrack the running animation toward the new target without a snap

#### Scenario: Consecutive auto-zoom commits retrack

- **GIVEN** an auto-zoom animation is running toward a slower-speed target (zooming in)
- **WHEN** the vehicle accelerates and auto-zoom commits a new lower target while the animation is still in progress
- **THEN** the animation SHALL retrack from the currently displayed scale toward the new target
- **AND** the display SHALL NOT snap back to the previous animation's start scale
