# Basemap UI

## Purpose

Expose basemap state and controls in the app UI — installation status, download/update/delete actions, progress, and a visual indication that the basemap is active.

## ADDED Requirements

### Requirement: Show basemap in installed maps list

The system SHALL display the basemap as an entry in the installed maps list of the map manager screen.

#### Scenario: Basemap installed

- **WHEN** user opens the map manager screen
- **WHEN** the basemap is installed
- **THEN** the installed maps list shows a "World Basemap" entry
- **THEN** the entry shows basemap size and version

#### Scenario: Basemap not installed

- **WHEN** user opens the map manager screen
- **WHEN** the basemap is not installed
- **THEN** the installed maps list does not show a basemap entry

### Requirement: Provide basemap download/update control

The system SHALL provide a control to download or update the basemap in the map manager screen.

#### Scenario: Download basemap

- **WHEN** the basemap is available on the server but not installed
- **THEN** the map manager screen shows a "Download Basemap" control
- **WHEN** user taps the control
- **THEN** the system starts the basemap download with progress indication

#### Scenario: Update basemap

- **WHEN** the basemap is installed and a newer version is available
- **THEN** the map manager screen shows an "Update Basemap" control
- **WHEN** user taps the control
- **THEN** the system starts the basemap update with progress indication

#### Scenario: Basemap up to date

- **WHEN** the basemap is installed and the server has no newer version
- **THEN** the map manager screen shows no update control for the basemap

### Requirement: Indicate basemap status in map view

The system SHALL NOT show a basemap status indicator in the map view; the basemap renders silently as an overlay layer.

#### Scenario: Basemap active

- **WHEN** the basemap is loaded and rendering
- **THEN** the map view shows no basemap indicator

#### Scenario: No basemap

- **WHEN** no basemap is installed or loaded
- **THEN** the map view shows no basemap indicator
