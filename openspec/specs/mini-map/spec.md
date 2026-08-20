# mini-map Specification

## Purpose

Reusable interactive mini map widget for embedding in detail views: renders an independent, north-aligned map viewport with small zoom controls, single-finger panning, and an object marker.

## Requirements

### Requirement: Independent interactive viewport
The mini map SHALL render its own map viewport centered on a caller-provided location. Panning and zooming the mini map SHALL NOT change the main map's viewport, center, or magnification.

#### Scenario: Mini map centered on object
- **WHEN** the mini map is shown with an object location
- **THEN** the mini map SHALL render the map area centered on that location

#### Scenario: Mini map interaction does not move main map
- **WHEN** the user pans or zooms the mini map
- **THEN** the main map's viewport SHALL remain unchanged

### Requirement: Zoom controls
The mini map SHALL provide small zoom in and zoom out controls. Zooming SHALL be clamped to the app's minimum and maximum magnification limits.

#### Scenario: Zoom in via button
- **WHEN** the user taps the zoom in button
- **THEN** the mini map SHALL increase magnification by one step
- **AND** the object marker SHALL remain anchored to the object position

#### Scenario: Zoom clamped at limits
- **WHEN** the mini map magnification is at the maximum (or minimum) limit
- **AND** the user taps zoom in (or zoom out)
- **THEN** the magnification SHALL NOT change

### Requirement: Panning
The mini map SHALL support single-finger drag to pan.

#### Scenario: Drag pans the mini map
- **WHEN** the user drags one finger across the mini map
- **THEN** the visible map area SHALL follow the drag direction
- **AND** the object marker SHALL stay anchored to the object's geographic position (moving out of view if panned away)

### Requirement: North-aligned orientation
The mini map SHALL always render north-up with rotation locked to zero. It SHALL NOT expose rotation gestures, and it SHALL be unaffected by the main map's bearing.

#### Scenario: Mini map always north-up
- **WHEN** the main map is rotated to a non-zero bearing
- **AND** the mini map is visible
- **THEN** the mini map SHALL render with north at the top
- **AND** rotating the main map SHALL NOT rotate the mini map

### Requirement: Object marker
The mini map SHALL display a marker at the object's location. The marker SHALL remain at the object's geographic position while the map is panned or zoomed.

#### Scenario: Marker shown at object position
- **WHEN** the mini map renders an object location
- **THEN** a marker SHALL be drawn at the object's projected screen position

#### Scenario: Marker follows object during pan and zoom
- **WHEN** the user pans or zooms the mini map
- **THEN** the marker SHALL be redrawn at the object's new projected position
- **AND** the marker SHALL disappear when the object is panned outside the visible area

### Requirement: Reusable embeddable widget
The mini map SHALL be a self-contained widget that any screen can embed by supplying an object location and initial magnification. Its rendering lifecycle (start/stop, resource cleanup) SHALL be owned by the widget, independent of the embedding screen's state.

#### Scenario: Widget embedded in any screen
- **WHEN** a screen embeds the mini map widget with an object location and magnification
- **THEN** the widget SHALL render the map without requiring state from the main map screen

#### Scenario: Widget cleanup
- **WHEN** the embedding screen is removed from composition
- **THEN** the mini map SHALL stop rendering and release its resources

### Requirement: Multiple object markers
The mini map SHALL support rendering markers for multiple object locations in addition to the primary marker. Each marker SHALL be anchored to its object's geographic position and SHALL move with the map during pan and zoom, disappearing when its object is panned outside the visible area. The primary marker SHALL remain visually distinct from additional markers.

#### Scenario: All object markers shown
- **WHEN** the mini map is configured with multiple object locations
- **THEN** a marker is drawn at each object's projected screen position

#### Scenario: Additional markers follow pan and zoom
- **WHEN** the user pans or zooms the mini map
- **THEN** every marker is redrawn at its object's new projected position
- **AND** markers disappear when their object is panned outside the visible area

#### Scenario: Primary marker stays distinct
- **WHEN** the mini map shows the primary marker and additional markers
- **THEN** the primary marker is visually distinct from the additional markers

### Requirement: Optional current-position marker
The mini map SHALL support an optional current-position marker with a distinct style (e.g. a blue dot with an accuracy circle), shown only when a current position is provided. When no current position is provided, no such marker SHALL be drawn and no placeholder SHALL be shown.

#### Scenario: Current-position marker drawn when provided
- **WHEN** the mini map is configured with a current position
- **THEN** the mini map draws the current-position marker at that position
- **AND** the marker stays anchored while panning and zooming

#### Scenario: No current-position marker without a fix
- **WHEN** the mini map is configured without a current position
- **THEN** no current-position marker is drawn
