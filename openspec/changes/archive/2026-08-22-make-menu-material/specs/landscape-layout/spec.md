# Landscape Layout — Delta

## MODIFIED Requirements

### Requirement: Control cluster arrangement in landscape

In landscape mode, overlay controls SHALL be split into a left action cluster and a right view cluster. The action cluster SHALL be positioned at the top-left and SHALL contain the menu button, search button, and favorites button. The view cluster SHALL be positioned on the right side and SHALL contain the compass button, location options, and zoom controls. The re-center (MyLocation) button SHALL be positioned at the bottom-left when visible.

#### Scenario: All controls on right side

- **WHEN** the device is in landscape orientation
- **THEN** the view controls (compass, location options, zoom) SHALL be positioned on the right side of the screen
- **AND** the action buttons (menu, search, favorites) SHALL be positioned on the left side of the screen

#### Scenario: Zoom controls horizontal in landscape

- **WHEN** the device is in landscape orientation
- **THEN** the zoom controls SHALL display as a horizontal row (zoom in, magnification label, zoom out)

#### Scenario: Menu, compass at top-right, search + favorites side-by-side below

- **WHEN** the device is in landscape orientation
- **THEN** the menu button SHALL be at the top-left
- **AND** the compass button SHALL be at the top-right
- **AND** the search and favorites buttons SHALL appear below the menu button on the left side

#### Scenario: Location options and zoom at bottom-right

- **WHEN** the device is in landscape orientation
- **THEN** the location options overlay SHALL be positioned at the bottom-right
- **AND** the zoom controls (horizontal) SHALL be below the location options

#### Scenario: Re-center button bottom-left

- **WHEN** the device is in landscape orientation
- **AND** follow mode is off
- **AND** a GPS location is available
- **THEN** the re-center (MyLocation) button SHALL be displayed at the bottom-left
