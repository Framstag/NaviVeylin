# smooth-zoom

## Purpose

Make zooming visually continuous by easing the map display between the start and end zoom levels whenever the magnification changes without an active pinch gesture, instead of snapping the front buffer from one rendered zoom level to the next.

## ADDED Requirements

### Requirement: Eased zoom animation on discrete zoom input

The system SHALL animate the displayed map from the current displayed scale toward the target magnification whenever the zoom level changes without an active pinch gesture (zoom buttons, scroll wheel, keyboard plus/minus). The zoom animation SHALL scale the previously rendered front buffer over approximately 200-300 ms with an ease-out curve. The displayed magnification level SHALL update to the target immediately when the animation starts.

#### Scenario: Zoom in button triggers animated transition

- **WHEN** the user taps the zoom in button while no pinch gesture is active
- **THEN** the displayed map SHALL scale up smoothly from the currently rendered magnification toward the target magnification over ~250 ms with an ease-out curve
- **AND** no single-frame jump between zoom levels SHALL be visible

#### Scenario: Scroll wheel zoom animates

- **WHEN** the user zooms with the scroll wheel
- **THEN** the displayed map SHALL animate toward the target magnification with an eased scale
- **AND** repeated wheel ticks during the animation SHALL be handled without visual snapping

### Requirement: Geographic anchor stays fixed during zoom animation

The geographical point under the zoom focal point SHALL be kept visually fixed: discrete zoom input via zoom buttons or keyboard SHALL anchor the screen center; scroll wheel input SHALL anchor the cursor position.

#### Scenario: Button zoom keeps map center fixed

- **WHEN** the user taps a zoom button
- **THEN** the geographic point at the screen center SHALL remain at the same screen pixel for every frame of the animation

#### Scenario: Scroll wheel zoom keeps cursor point fixed

- **WHEN** the user zooms with the scroll wheel while the cursor is over the map
- **THEN** the geographic point under the cursor SHALL remain under the cursor for every frame of the animation

### Requirement: Retracking on rapid zoom input

While a zoom animation is running, further zoom input SHALL retrack the running animation smoothly from the currently displayed scale toward the new target magnification. The system SHALL NOT snap back to the start scale of the previous animation.

#### Scenario: Zoom out tapped during zoom in animation

- **WHEN** a zoom in animation is halfway to its target and the user taps the zoom out button
- **THEN** the displayed scale SHALL smoothly reverse from its current value toward the new target magnification
- **AND** the animation SHALL NOT jump back to the zoom in animation's start scale

#### Scenario: Repeated scroll wheel zoom

- **WHEN** the user zooms with the scroll wheel several times in quick succession
- **THEN** each tick SHALL retrack the running animation toward the new target without a snap

### Requirement: Animation continues until native render completes

The system SHALL run the zoom animation independent of the native render pipeline. The debounced native render SHALL be queued while the animation plays. When the render at the target magnification completes, the overlay phase of the animation SHALL end and the rendered frame SHALL be shown as specified in the zoom-transition-scaling capability. If the render completes before the animation finishes, the rendered frame SHALL be displayed immediately at the target magnification.

#### Scenario: Render completes before animation ends

- **WHEN** the native render at the target magnification finishes while the zoom animation is still in progress
- **THEN** the rendered frame SHALL be shown at the exact target magnification from that point on
- **AND** the animation SHALL no longer scale the rendered frame

#### Scenario: Render completes after animation ends

- **WHEN** the zoom animation finishes before the native render completes
- **THEN** the display SHALL hold the front buffer at the target scale until the render completes
- **AND** the render completion SHALL replace the display as specified in the zoom-transition-scaling capability

### Requirement: Zoom animation composes with follow mode

The system SHALL compose the zoom animation with the smooth-follow display extrapolation. While both are active, the zoom animation SHALL scale the front buffer and the follow-mode display loop SHALL pan it; neither SHALL disable the other.

#### Scenario: Follow mode active during button zoom

- **WHEN** follow mode is engaged and the user taps a zoom button
- **THEN** the display SHALL animate the zoom toward the target magnification
- **AND** the follow-mode extrapolation SHALL continue offsetting the displayed center during the animation

#### Scenario: Follow mode not interrupted by zoom animation

- **WHEN** a zoom animation completes while follow mode is engaged
- **THEN** follow-mode extrapolation SHALL continue without a jump in the displayed position

### Requirement: Viewport records final magnification

The system SHALL persist the viewport with the target magnification of the zoom animation during and after the animation. The animated displayed scale SHALL NOT be written to the persisted viewport.

#### Scenario: App paused during animation

- **WHEN** the app is paused while a zoom animation is running
- **THEN** the persisted viewport magnification SHALL be the animation's target magnification or the currently rendered magnification, never an intermediate animated scale