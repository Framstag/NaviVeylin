# Map Canvas Screen

## Purpose

Initial full-screen composable with empty map canvas placeholder, top-right overflow menu, and popup navigation to the map manager screen.

## ADDED Requirements

### Requirement: Full-screen map canvas placeholder
The system SHALL display a full-screen composable that fills the available space, showing a subtle grid pattern and centered placeholder text indicating where the map will render.

#### Scenario: Grid pattern visible on launch
- **WHEN** app launches and MainScreen is displayed
- **THEN** a subtle grid pattern fills the screen background
- **AND** centered text reads "Map will render here"

### Requirement: Top-right overflow menu
The system SHALL display an overflow menu button (⋮) in the top-right corner of the screen.

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
