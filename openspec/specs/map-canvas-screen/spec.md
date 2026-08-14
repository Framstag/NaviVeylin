# Map Canvas Screen

## Purpose

Initial full-screen composable with empty map canvas placeholder, top-right overflow menu, and popup navigation to the map manager screen.

## Requirements

### Requirement: Full-screen map canvas placeholder
The system SHALL display a full-screen composable that fills the available space, showing a subtle grid pattern and centered placeholder text indicating where the map will render.

#### Scenario: Grid pattern visible on launch
- **WHEN** app launches and MainScreen is displayed
- **THEN** a subtle grid pattern fills the screen background
- **AND** centered text reads "Map will render here"

### Requirement: Top-right overflow menu
The system SHALL display an overflow menu button (⋮) in the top-right corner of the screen, positioned below the status bar via system window insets.

#### Scenario: Overflow menu opens popup
- **WHEN** user taps the overflow menu button
- **THEN** a Material 3 DropdownMenu appears with menu items

### Requirement: Menu contains Download Maps entry
The system SHALL include a "Download Maps" entry in the overflow menu that navigates to the MapManagerScreen.

#### Scenario: Download Maps navigates to manager
- **WHEN** user taps "Download Maps" in the overflow menu
- **THEN** the app navigates to the MapManagerScreen route

### Requirement: Menu extensible for future settings
The system SHALL support adding future menu entries without structural changes.

#### Scenario: Placeholder entry exists
- **WHEN** overflow menu is open
- **THEN** a disabled or placeholder entry for "Settings" is visible (or the menu structure supports easy addition)

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
