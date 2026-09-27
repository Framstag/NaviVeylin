# Spec Delta

## MODIFIED Requirements

### Requirement: Eased zoom animation on discrete zoom input

The system SHALL animate the displayed map from the current displayed scale toward the target magnification whenever the magnification changes without an active pinch gesture — discrete zoom input (zoom buttons, scroll wheel, keyboard plus/minus) as well as speed-driven auto-zoom commits during follow mode. The zoom animation SHALL scale the previously rendered front buffer with an ease-out curve; discrete input animates over approximately 200-300 ms, while auto-zoom commits animate over approximately 650 ms (slower, matching the driving zoom cadence so the easing does not read as pumping). The requested target magnification SHALL be recorded immediately when the animation starts; the displayed magnification reaches it across the transition.

The displayed frame SHALL NOT show a magnification farther than the window the frame in hand can serve (its overrun margin): a magnification change larger than that window SHALL be applied as a sequence of displayed steps, each step served by a native render at the magnification that step displays, with the last step landing exactly on the requested magnification. A change inside the serviceable window SHALL keep the single eased scale of one already-rendered frame.

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

#### Scenario: Magnification change larger than the serviceable window is applied across rendered frames

- **GIVEN** follow mode is active, the front buffer was rendered at magnification 13.0, and the window that frame can serve covers 0.25 magnification levels
- **WHEN** auto-zoom commits magnification 17.0
- **THEN** no displayed frame SHALL show a magnification farther than 0.25 levels from the magnification that frame was rendered at
- **AND** every step of the transition SHALL be served by a native render at the magnification it displays
- **AND** the last displayed frame SHALL be a native render at magnification 17.0
- **AND** the displayed map content SHALL show the geometry, road widths and label sizes of the magnification it shows, not of magnification 13.0 resampled

#### Scenario: Change inside the serviceable window keeps the single eased scale

- **WHEN** a committed magnification differs from the rendered one by less than the window that frame can serve
- **THEN** the display SHALL ease the rendered frame toward the committed magnification
- **AND** no additional native render SHALL be required for the transition

#### Scenario: Target magnification is recorded while the displayed scale still eases

- **WHEN** a zoom animation starts and the displayed scale has not yet reached the target
- **THEN** the recorded viewport magnification SHALL already be the target magnification
- **AND** the displayed scale SHALL NOT be recorded

### Requirement: Retracking on rapid zoom input

While a zoom animation is running, further zoom input SHALL retrack the running animation smoothly from the currently displayed scale toward the new target magnification. The system SHALL NOT snap back to the start scale of the previous animation. A stepped magnification change SHALL retrack through this rule: the render of the next step retracks the running animation toward that step's magnification.

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

#### Scenario: Stepped magnification change retracks per step

- **GIVEN** a magnification change is being applied as a sequence of rendered steps and the current step's easing is still in progress
- **WHEN** the next step's render lands
- **THEN** the displayed scale SHALL retrack from its current value toward the next step's magnification
- **AND** the display SHALL NOT snap back to the first step's start scale

### Requirement: Geographic anchor stays fixed during zoom animation

The geographical point under the zoom focal point SHALL be kept visually fixed: discrete zoom input via zoom buttons or keyboard SHALL anchor the screen center; scroll wheel input SHALL anchor the cursor position. While a follow mode is active, the point kept visually fixed SHALL be the vehicle marker's resolved follow anchor position on the map surface instead of the screen center, for every anchor preset, and it SHALL stay fixed for every frame of the animation.

#### Scenario: Button zoom keeps map center fixed

- **WHEN** the user taps a zoom button
- **THEN** the geographic point at the screen center SHALL remain at the same screen pixel for every frame of the animation

#### Scenario: Scroll wheel zoom keeps cursor point fixed

- **WHEN** the user zooms with the scroll wheel while the cursor is over the map
- **THEN** the geographic point under the cursor SHALL remain under the cursor for every frame of the animation

#### Scenario: Follow mode with an off-center anchor keeps the vehicle fixed

- **GIVEN** follow mode is active and the resolved follow anchor is bottom-center, so the vehicle marker is rendered below the surface center
- **WHEN** auto-zoom commits a zoom-in
- **THEN** the vehicle marker SHALL occupy the same screen pixel for every frame of the animation
- **AND** the map content under the marker SHALL NOT shift relative to the marker
- **AND** no correction jump of the vehicle marker or the map content SHALL appear when the frame at the target magnification lands

#### Scenario: Follow mode anchors discrete zoom input on the vehicle

- **GIVEN** follow mode is active with a resolved follow anchor other than the surface center
- **WHEN** the user taps a zoom button or uses the keyboard shortcut
- **THEN** the vehicle marker's screen pixel SHALL remain unchanged for every frame of the animation
- **AND** the map SHALL NOT ease around the surface center

## ADDED Requirements

### Requirement: Zoom animation never exposes uncovered map area

The displayed frame SHALL cover the whole map surface for every frame of a zoom animation. The displayed scale SHALL stay inside the region the frame in hand actually covers, or the displayed frame SHALL be a native render at the committed magnification.

#### Scenario: Large zoom-out does not expose surface background

- **GIVEN** the front buffer covers the surface with the margin of the overrun buffer
- **WHEN** the committed magnification is lower than the rendered one by more than that margin can serve
- **THEN** no frame of the animation SHALL show a strip of bare surface background at any edge
- **AND** the display SHALL either step through rendered frames or show a native render at the committed magnification

#### Scenario: Zoom-in keeps full coverage

- **WHEN** the animation zooms in
- **THEN** the displayed bitmap SHALL cover the whole surface at every frame
