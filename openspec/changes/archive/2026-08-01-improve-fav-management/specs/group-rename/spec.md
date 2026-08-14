## Purpose

Lets users rename a favorite group from the group card menu, with the change persisted across app restarts.

## ADDED Requirements

### Requirement: Rename group via menu
The system SHALL allow renaming a group by selecting "Rename" from the group card's action menu.

#### Scenario: Open rename dialog
- **WHEN** user selects "Rename" from the group card menu
- **THEN** a dialog appears with a text field pre-filled with the current group name

### Requirement: Rename validates uniqueness
The system SHALL reject a rename if the new name matches an existing group name.

#### Scenario: Rename to unique name
- **WHEN** user enters a new unique name and confirms
- **THEN** the group is renamed and the grid updates with the new name

#### Scenario: Rename to duplicate name
- **WHEN** user enters a name that already exists as another group
- **THEN** the rename fails and a snackbar message "Group name already exists" is shown

### Requirement: Rename persists across restarts
The renamed group SHALL persist to the JSON file and survive app restart.

#### Scenario: Rename survives restart
- **WHEN** user renames a group, closes the app, and reopens
- **THEN** the group still shows the new name
