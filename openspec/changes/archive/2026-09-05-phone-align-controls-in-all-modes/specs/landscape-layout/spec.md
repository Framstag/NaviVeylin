## MODIFIED Requirements

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
