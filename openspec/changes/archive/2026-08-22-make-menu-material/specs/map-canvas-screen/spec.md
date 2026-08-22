# Map Canvas Screen — Delta

## MODIFIED Requirements

### Requirement: Top-right overflow menu
The system SHALL display the toaster menu button (hamburger icon) at the top-left corner of the screen, positioned below the status bar via system window insets. Tapping it SHALL open the animated Material 3 menu defined by the `map-menu` capability.

#### Scenario: Overflow menu opens popup
- **WHEN** the user taps the toaster menu button
- **THEN** the animated Material 3 map menu appears (fade/scale in)

#### Scenario: Menu button at top-left
- **WHEN** the map screen is displayed
- **THEN** the toaster menu button SHALL be positioned at the top-left of the screen below the status bar

### Requirement: Top-right overlay column

The system SHALL split the portrait overlay controls into two columns: a left action column and a right view column. The left action column SHALL be positioned at the top-left below the status bar and SHALL contain, in order from top to bottom: menu button, search button, favorites button. The right view column SHALL be positioned at the top-right below the status bar and SHALL contain, in order from top to bottom: compass button, location options button, and zoom controls. The re-center (MyLocation) button SHALL be positioned at the bottom-left when visible.

In landscape orientation, the system SHALL use the landscape layout arrangement defined by the `landscape-layout` capability instead.

#### Scenario: Portrait shows vertical column at top-right
- **WHEN** the device is in portrait orientation
- **THEN** the right view column SHALL show the compass button at the top
- **AND** the location options button SHALL appear below the compass button
- **AND** the zoom controls SHALL appear below the location options button

#### Scenario: Portrait shows action column at top-left
- **WHEN** the device is in portrait orientation
- **THEN** the left action column SHALL show the menu button at the top
- **AND** the search button SHALL appear directly below the menu button
- **AND** the favorites button SHALL appear directly below the search button

#### Scenario: Landscape uses landscape-layout arrangement
- **WHEN** the device is in landscape orientation
- **THEN** the left/right overlay columns SHALL follow the landscape-layout capability arrangement
