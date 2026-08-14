## ADDED Requirements

### Requirement: New group name collision error
When the user adds a favorite to a new group and the chosen group name already exists, the system SHALL NOT create a duplicate group or favorite, and SHALL show an error message.

#### Scenario: Duplicate new group name rejected
- **WHEN** user taps "Add to Favorites"
- **AND** selects "+ New group"
- **AND** enters a name that already exists
- **AND** confirms
- **THEN** the group SHALL NOT be duplicated
- **AND** the favorite SHALL NOT be saved
- **AND** an error message SHALL be shown
