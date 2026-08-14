## Purpose

Provides orientation-aware map screen overlay layout that rearranges controls for landscape mode, following Android best practices for safe-zone placement and foldable/multi-window compatibility.

## ADDED Requirements

### Requirement: Orientation detection via BoxWithConstraints

The system SHALL detect device orientation using Compose `BoxWithConstraints` (width vs height comparison) rather than the deprecated `Configuration.orientation` API.

#### Scenario: Portrait detected when height exceeds width

- **WHEN** the map screen is displayed
- **AND** `maxWidth < maxHeight` from `BoxWithConstraints`
- **THEN** the system SHALL use the portrait layout arrangement

#### Scenario: Landscape detected when width exceeds height

- **WHEN** the map screen is displayed
- **AND** `maxWidth > maxHeight` from `BoxWithConstraints`
- **THEN** the system SHALL use the landscape layout arrangement

#### Scenario: Layout re-evaluated on configuration change

- **WHEN** the device is rotated
- **THEN** the layout SHALL re-evaluate and switch arrangements without activity restart

### Requirement: Safe-zone placement in landscape

In landscape mode, overlay controls SHALL NOT be placed at the very top edge of the screen. Controls SHALL maintain at least 8dp padding from the top edge to avoid overlap with the camera notch or status bar area.

#### Scenario: Controls positioned away from top edge

- **WHEN** the device is in landscape orientation
- **THEN** no overlay button SHALL be positioned at the very top edge of the screen
- **AND** controls SHALL have at least 8dp padding from the top edge to avoid camera notch overlap

### Requirement: Control cluster arrangement in landscape

In landscape mode, overlay controls SHALL be arranged on the right side of the screen only. The left side SHALL remain clear for navigation hints during routing.

#### Scenario: All controls on right side

- **WHEN** the device is in landscape orientation
- **THEN** all overlay buttons SHALL be positioned on the right side of the screen
- **AND** no overlay buttons SHALL be on the left side

#### Scenario: Zoom controls horizontal in landscape

- **WHEN** the device is in landscape orientation
- **THEN** the zoom controls SHALL display as a horizontal row (zoom in, magnification label, zoom out)

#### Scenario: Menu, compass at top-right, search + favorites side-by-side below

- **WHEN** the device is in landscape orientation
- **THEN** the menu button and compass button SHALL be stacked vertically at the top-right
- **AND** the favorites button and search button SHALL be positioned below in a horizontal row
- **AND** favorites SHALL be to the left of search in that row

#### Scenario: Location options and zoom at bottom-right

- **WHEN** the device is in landscape orientation
- **THEN** the location options overlay SHALL be positioned at the bottom-right
- **AND** the zoom controls (horizontal) SHALL be below the location options
- **AND** the MyLocation re-center button SHALL be below the zoom controls
