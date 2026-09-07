## ADDED Requirements

### Requirement: Zoom controls shown during navigation

The system SHALL display the zoom controls during active navigation, positioned at the bottom of the right-side widget column, below all other controls (compass and speed widget), directly above the routing status bar. The zoom controls SHALL follow the same orientation rule as the standard view: a horizontal row in landscape, a vertical column in portrait.

#### Scenario: Zoom controls visible during navigation

- **WHEN** navigation is active
- **THEN** the zoom controls SHALL be visible at the bottom of the right-side widget column
- **AND** they SHALL appear below the compass and speed widget
- **AND** they SHALL appear directly above the routing status bar

#### Scenario: Zoom controls horizontal in landscape during navigation

- **WHEN** navigation is active and the device is in landscape orientation
- **THEN** the zoom controls SHALL display as a horizontal row (zoom in, magnification label, zoom out)

#### Scenario: Zoom controls vertical in portrait during navigation

- **WHEN** navigation is active and the device is in portrait orientation
- **THEN** the zoom controls SHALL display as a vertical column (zoom in, magnification label, zoom out)

#### Scenario: Zoom controls hidden when navigation stops

- **WHEN** navigation stops
- **THEN** the zoom controls SHALL no longer be shown in the navigation layout
