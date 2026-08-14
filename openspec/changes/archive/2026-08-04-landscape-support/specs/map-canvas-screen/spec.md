## MODIFIED Requirements

### Requirement: Top-right overlay column

The system SHALL display a vertical column of overlay buttons in the top-right corner of the map screen when in portrait orientation, positioned below the status bar via system window insets. The column SHALL contain, in order from top to bottom: menu button, compass button, search button, favorites button, location options button, and zoom controls.

In landscape orientation, the system SHALL use the landscape layout arrangement defined by the `landscape-layout` capability instead.

#### Scenario: Portrait shows vertical column at top-right

- **WHEN** the device is in portrait orientation
- **THEN** the top-right overlay column SHALL show the menu button at the top
- **AND** the compass button SHALL appear directly below the menu button
- **AND** the search button SHALL appear directly below the compass button

#### Scenario: Landscape uses landscape-layout arrangement

- **WHEN** the device is in landscape orientation
- **THEN** the top-right vertical column SHALL NOT be displayed
- **AND** controls SHALL be arranged on the right side per the landscape-layout capability
