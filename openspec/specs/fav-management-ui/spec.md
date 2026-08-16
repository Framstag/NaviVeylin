# fav-management-ui Specification

## Purpose

Provides a full-screen Compose sheet for managing favorite location groups and favorites, accessible from the map screen, with full CRUD operations.
## Requirements
### Requirement: Full-screen favorites sheet opens from map screen
The system SHALL provide a full-screen sheet (covering the entire screen) for managing favorites. It SHALL be accessible via a "Favorites" button or menu item on the map screen. The sheet SHALL close via the toolbar back button or the system back gesture/button.

#### Scenario: Open favorites sheet
- **WHEN** user taps the "Favorites" button on the map screen
- **THEN** a full-screen sheet SHALL open showing all favorite groups

#### Scenario: Close favorites sheet
- **WHEN** user taps the close/back button
- **THEN** the sheet SHALL close and return to the map

#### Scenario: Back gesture closes favorites sheet
- **WHEN** the favorites sheet is open
- **AND** user performs the system back gesture (edge swipe) or presses the back button
- **THEN** the sheet SHALL close and return to the map
- **AND** the application SHALL NOT exit

### Requirement: Favorites sheet displays groups as expandable sections
The sheet SHALL display groups as expandable sections. Each section header SHALL show the group name and fav count. Expanding a section SHALL reveal its favorites with name and coordinates.

#### Scenario: Groups shown as sections
- **WHEN** favorites sheet opens
- **THEN** each group SHALL be displayed as an expandable section with name and count

#### Scenario: Expand group shows favorites
- **WHEN** user taps a group section header
- **THEN** the section SHALL expand to show all favorites in that group

### Requirement: Favorites sheet supports group CRUD
The sheet SHALL provide buttons to add a new group and delete an existing group. Adding a group SHALL prompt for a name. Deleting a group SHALL require confirmation and remove all its favorites.

#### Scenario: Add group
- **WHEN** user taps "Add Group" and enters a name
- **THEN** a new empty group SHALL appear in the list

#### Scenario: Delete group with confirmation
- **WHEN** user taps "Delete Group" on a group
- **THEN** a confirmation dialog SHALL appear, and on confirm the group and all its favs SHALL be removed

### Requirement: Favorites sheet supports favorite CRUD within groups
The sheet SHALL provide buttons to add, delete, and rename favorites within a group. Adding a favorite SHALL prompt for name and coordinates (or use current map center). Deleting SHALL require confirmation. Renaming SHALL prompt for a new name.

#### Scenario: Add favorite to group
- **WHEN** user taps "Add Favorite" in a group and enters name + coordinates
- **THEN** the favorite SHALL appear in that group's list

#### Scenario: Delete favorite with confirmation
- **WHEN** user taps "Delete" on a favorite
- **THEN** a confirmation dialog SHALL appear, and on confirm the favorite SHALL be removed

#### Scenario: Rename favorite
- **WHEN** user taps "Rename" on a favorite and enters a new name
- **THEN** the favorite SHALL be renamed

### Requirement: Favorites sheet has "Add current map location" option
The sheet SHALL provide a quick action to add the current map center as a favorite, prompting for group selection and name.

#### Scenario: Add map center as favorite
- **WHEN** user taps "Add Current Location" and selects a group and enters a name
- **THEN** the map center coordinates SHALL be saved as a favorite in that group

### Requirement: Favorites sheet resets to main screen on open

The favorites sheet SHALL always start on the main group grid screen when opened, regardless of which sub-screen was last viewed.

#### Scenario: Sheet opens on main screen

- **WHEN** the user opens the favorites sheet
- **THEN** the sheet SHALL display the main group grid
- **AND** SHALL NOT show a previously viewed sub-screen (e.g., group detail or favorite edit)

#### Scenario: Sheet re-opens after closing from sub-screen

- **GIVEN** the user navigated to a group detail sub-screen
- **WHEN** the user closes the favorites sheet
- **AND** re-opens it
- **THEN** the sheet SHALL display the main group grid, not the group detail sub-screen

### Requirement: Group card menu has "Set Color" option
The group card's action menu SHALL include a "Set Color" option that opens a color picker dialog.

#### Scenario: Set Color in group menu
- **WHEN** user taps the menu icon on a group card
- **THEN** the dropdown menu SHALL include "Set Color" alongside existing "Rename" and "Delete" options

#### Scenario: Color picker dialog appears
- **WHEN** user selects "Set Color" from the group menu
- **THEN** a color picker dialog SHALL appear with a set of predefined color swatches

#### Scenario: Color applied and card updates
- **WHEN** user selects a color swatch and confirms
- **THEN** the group color SHALL be saved and the group card SHALL immediately update with the new color tint

### Requirement: Favorite item has star toggle button
Each favorite item row SHALL include a star icon button that toggles the starred state.

#### Scenario: Star button on favorite row
- **WHEN** a favorite is displayed in a list
- **THEN** a star icon button SHALL be shown on the right side of the row, alongside rename and delete buttons

#### Scenario: Toggle star from favorite row
- **WHEN** user taps the star icon on a favorite row
- **THEN** the starred state SHALL toggle and the icon SHALL update immediately

### Requirement: Starred chip bar at top of main view
The favorites sheet main view SHALL display a horizontal scrollable chip bar above the group grid showing all starred favorites.

#### Scenario: Chip bar above group grid
- **WHEN** the favorites sheet main view is displayed and starred favorites exist
- **THEN** a chip bar SHALL appear above the group grid, before the search bar

#### Scenario: Chip click opens route panel
- **WHEN** user taps a starred favorite chip
- **THEN** the favorites sheet SHALL close
- **AND** the route panel SHALL open with current location as start and the tapped favorite as destination

### Requirement: Back gesture from group detail returns to main grid first
The system SHALL treat the group detail sub-screen as a navigation level inside the favorites sheet: the system back gesture/button SHALL first return from the group detail sub-screen to the main group grid, and only close the sheet when already on the main grid.

#### Scenario: Back from group detail sub-screen
- **WHEN** the favorites sheet is open
- **AND** user is viewing a group detail sub-screen
- **AND** user performs the system back gesture or presses the back button
- **THEN** the sheet SHALL return to the main group grid
- **AND** the sheet SHALL remain open

#### Scenario: Back from main grid closes sheet
- **WHEN** the favorites sheet is open
- **AND** user is viewing the main group grid
- **AND** user performs the system back gesture or presses the back button
- **THEN** the sheet SHALL close and return to the map

