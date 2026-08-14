## Purpose

Provide pan, zoom, and rotation gestures on the Android Auto map display, adapted for the car input model including rotary controller and touch input.

## ADDED Requirements

### Requirement: Pan gesture on car map
The system SHALL support panning the map via rotary controller dial and touch drag on the car display.

#### Scenario: Pan via rotary controller
- **WHEN** the user rotates the rotary controller while the map is focused
- **THEN** the map pans in the corresponding direction

#### Scenario: Pan via touch drag
- **WHEN** the user drags a finger on the car touch screen
- **THEN** the map pans to follow the drag direction

### Requirement: Zoom gesture on car map
The system SHALL support zooming in and out on the car map via rotary controller button and pinch-to-zoom on touch displays.

#### Scenario: Zoom via rotary controller button
- **WHEN** the user clicks the rotary controller
- **THEN** the map zooms in (or out, configurable)

#### Scenario: Zoom via pinch gesture
- **WHEN** the user performs a pinch gesture on the car touch screen
- **THEN** the map zooms in or out at the pinch center point

### Requirement: Map rotation on car display
The system SHALL support rotating the map via two-finger rotation gesture on touch displays.

#### Scenario: Rotate via two-finger gesture
- **WHEN** the user performs a two-finger rotation gesture on the car touch screen
- **THEN** the map rotates to match the gesture

### Requirement: Map interaction disengages follow mode
The system SHALL disengage GPS follow mode when the user manually pans or zooms the map, and provide a re-center action.

#### Scenario: Manual pan disengages follow
- **WHEN** the user manually pans the map
- **THEN** GPS follow mode disengages

#### Scenario: Re-center action available
- **WHEN** GPS follow mode is disengaged
- **THEN** a re-center action is available to return to GPS position

### Requirement: Zoom controls on car map
The system SHALL provide zoom in/out action buttons on the car map display for non-touch input.

#### Scenario: Zoom buttons shown
- **WHEN** the car map is displayed
- **THEN** zoom in and zoom out action buttons are available

#### Scenario: Zoom button changes magnification
- **WHEN** the user taps the zoom in button
- **THEN** the map magnification increases by one level
- **WHEN** the user taps the zoom out button
- **THEN** the map magnification decreases by one level
