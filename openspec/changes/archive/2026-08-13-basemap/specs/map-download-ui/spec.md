# Map Download UI

## MODIFIED Requirements

### Requirement: Active downloads section

The system SHALL display a collapsible "Active Downloads" section at the top of the screen when downloads are in progress, including basemap downloads. This section SHALL be hidden when no downloads are active.

#### Scenario: Active downloads section appears during download

- **WHEN** a map or basemap download starts
- **THEN** an "Active Downloads" section appears at the top with the current download(s) and progress
- **AND** the section is collapsible

#### Scenario: Basemap download shown with progress

- **WHEN** a basemap download is in progress
- **THEN** the basemap entry appears in the active downloads section with progress
- **AND** a cancel control is available

#### Scenario: Active downloads section hides when empty

- **WHEN** all downloads (maps and basemap) complete or are cancelled
- **THEN** the "Active Downloads" section disappears

## ADDED Requirements

### Requirement: Basemap section in map manager screen

The system SHALL integrate basemap status and controls into the map manager screen without a separate tab or screen.

#### Scenario: Basemap status shown on screen open

- **WHEN** user opens the map manager screen
- **THEN** the screen shows whether the basemap is installed, available, or unavailable
- **AND** no separate navigation step is required to reach basemap controls

#### Scenario: Basemap errors surfaced

- **WHEN** a basemap download, update, or delete fails
- **THEN** the failure is reported to the user with the error message
- **AND** the user can retry the action
