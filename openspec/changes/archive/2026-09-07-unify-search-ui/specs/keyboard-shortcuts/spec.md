# keyboard-shortcuts Delta

## MODIFIED Requirements

### Requirement: Search via `/` key

The system SHALL open the unified search dialog when the user presses the `/` key while the map canvas has focus.

#### Scenario: Slash key opens search

- **WHEN** the map canvas has keyboard focus
- **AND** the user presses the `/` key
- **THEN** the unified search dialog SHALL open
- **AND** the search input SHALL be auto-focused

#### Scenario: Slash key when search is already open

- **WHEN** the unified search dialog is already open
- **AND** the user presses the `/` key
- **THEN** the search input SHALL be focused
- **AND** any existing search text SHALL be selected
