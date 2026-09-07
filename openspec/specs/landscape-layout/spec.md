# Landscape Layout Specification

## Purpose

Provides orientation-aware map screen overlay layout that rearranges controls for landscape mode, following Android best practices for safe-zone placement and foldable/multi-window compatibility.

## Requirements

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

In landscape mode, overlay controls SHALL be split into a left action cluster and a right view cluster. The action cluster SHALL be positioned at the top-left and SHALL contain the menu button, search button, and favorites button. The view cluster SHALL be a single column positioned at the bottom-right, bottom-anchored, and SHALL contain, in order from top to bottom: compass button, speed widget, location options, and zoom controls — with the zoom controls at the bottom below all other controls. The re-center (MyLocation) button SHALL be positioned at the bottom-left when visible.

#### Scenario: All controls on right side

- **WHEN** the device is in landscape orientation
- **THEN** the view controls (compass, speed widget, location options, zoom) SHALL be positioned on the right side of the screen
- **AND** the action buttons (menu, search, favorites) SHALL be positioned on the left side of the screen

#### Scenario: Zoom controls horizontal in landscape

- **WHEN** the device is in landscape orientation
- **THEN** the zoom controls SHALL display as a horizontal row (zoom in, magnification label, zoom out)

#### Scenario: Menu, compass at top-right, search + favorites side-by-side below

- **WHEN** the device is in landscape orientation
- **THEN** the menu button SHALL be at the top-left
- **AND** the compass button SHALL be at the top of the bottom-anchored view cluster
- **AND** the search and favorites buttons SHALL appear below the menu button on the left side

#### Scenario: Location options and zoom at bottom-right

- **WHEN** the device is in landscape orientation
- **THEN** the location options overlay SHALL be positioned in the bottom-anchored view cluster
- **AND** the zoom controls (horizontal) SHALL be below the location options, at the bottom below all other controls

#### Scenario: View cluster bottom-anchored at bottom-right

- **WHEN** the device is in landscape orientation
- **THEN** the view cluster SHALL be positioned at the bottom-right of the screen
- **AND** the compass button SHALL be at the top of the cluster
- **AND** the speed widget SHALL appear directly below the compass button
- **AND** the location options SHALL appear below the speed widget
- **AND** the zoom controls SHALL appear at the bottom, below all other controls

#### Scenario: Search + favorites side-by-side below menu

- **WHEN** the device is in landscape orientation
- **THEN** the menu button SHALL be at the top-left
- **AND** the search and favorites buttons SHALL appear below the menu button on the left side

#### Scenario: Re-center button bottom-left

- **WHEN** the device is in landscape orientation
- **AND** follow mode is off
- **AND** a GPS location is available
- **THEN** the re-center (MyLocation) button SHALL be displayed at the bottom-left
