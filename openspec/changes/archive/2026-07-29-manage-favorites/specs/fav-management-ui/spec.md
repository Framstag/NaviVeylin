## Purpose

Provides a full-screen Compose sheet for managing favorite location groups and favorites, accessible from the map screen, with full CRUD operations.

## ADDED Requirements

### Requirement: Full-screen favorites sheet opens from map screen
The system SHALL provide a full-screen sheet (covering the entire screen) for managing favorites. It SHALL be accessible via a "Favorites" button or menu item on the map screen.

#### Scenario: Open favorites sheet
- **WHEN** user taps the "Favorites" button on the map screen
- **THEN** a full-screen sheet SHALL open showing all favorite groups

#### Scenario: Close favorites sheet
- **WHEN** user taps the close/back button
- **THEN** the sheet SHALL close and return to the map

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
