## MODIFIED Requirements

### Requirement: Top-right overlay column

The system SHALL split the portrait overlay controls into two columns: a left action column and a right view column. The left action column SHALL be positioned at the top-left below the status bar and SHALL contain, in order from top to bottom: menu button, search button, favorites button. The right view column SHALL be positioned at the bottom-right above the navigation bar and SHALL contain, in order from top to bottom: compass button, speed widget, location options button, and zoom controls — with the zoom controls at the bottom below all other controls. The re-center (MyLocation) button SHALL be positioned at the bottom-left when visible.

In landscape orientation, the system SHALL use the landscape layout arrangement defined by the `landscape-layout` capability instead.

#### Scenario: Portrait shows vertical column at top-right

- **WHEN** the device is in portrait orientation
- **THEN** the right view column SHALL show the compass button at the top
- **AND** the speed widget SHALL appear directly below the compass button
- **AND** the location options button SHALL appear below the speed widget
- **AND** the zoom controls SHALL appear at the bottom, below all other controls

#### Scenario: Portrait shows vertical column at bottom-right

- **WHEN** the device is in portrait orientation
- **THEN** the right view column SHALL be positioned at the bottom-right above the navigation bar
- **AND** the compass button SHALL be at the top of the column
- **AND** the zoom controls SHALL appear at the bottom, below all other controls

#### Scenario: Portrait shows action column at top-left
- **WHEN** the device is in portrait orientation
- **THEN** the left action column SHALL show the menu button at the top
- **AND** the search button SHALL appear directly below the menu button
- **AND** the favorites button SHALL appear directly below the search button

#### Scenario: Landscape uses landscape-layout arrangement

- **WHEN** the device is in landscape orientation
- **THEN** the left/right overlay columns SHALL follow the landscape-layout capability arrangement
