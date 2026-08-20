## ADDED Requirements

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
