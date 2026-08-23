## MODIFIED Requirements

### Requirement: Search result selection triggers destination picker
The system SHALL allow the user to select a search result, which opens the details screen for that location (the destination picker flow); navigation starts only from the details screen's "Navigate here" action.

#### Scenario: Select search result
- **WHEN** user taps a search result
- **THEN** the system opens the details screen with that location as the target

#### Scenario: Navigation starts from details screen
- **WHEN** user taps "Navigate here" on the details screen
- **THEN** the system starts navigation to the selected location
