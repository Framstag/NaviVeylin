# Basemap UI

## Purpose

Expose basemap state and controls in the app UI — installation status, download/update/delete actions, progress, and a visual indication that the basemap is active.

## Requirements

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
The system SHALL provide a control to download or update the basemap in the map manager screen when the active source offers a basemap. The update control SHALL be offered only for a source that reports an update state; a source that reports none offers the download control and no update control. While a basemap download or update is in progress, the basemap section SHALL collapse to a compact status line; the full progress indication SHALL appear in the active-downloads section of the map manager screen, not duplicated in the basemap section.

#### Scenario: Download basemap

- **WHEN** the basemap is available on the server but not installed
- **THEN** the map manager screen shows a "Download Basemap" control
- **WHEN** user taps the control
- **THEN** the system starts the basemap download
- **AND** the basemap section shows a compact status line indicating the download is in progress
- **AND** the full progress indication appears in the active-downloads section

#### Scenario: Update basemap

- **WHEN** the basemap is installed and a newer version is available
- **THEN** the map manager screen shows an "Update Basemap" control
- **WHEN** user taps the control
- **THEN** the system starts the basemap update
- **AND** the basemap section shows a compact status line indicating the update is in progress
- **AND** the full progress indication appears in the active-downloads section

#### Scenario: Basemap up to date

- **WHEN** the basemap is installed and the server has no newer version
- **THEN** the map manager screen shows no update control for the basemap

#### Scenario: Source reports no update state

- **WHEN** the basemap is installed and the active source reports no update state
- **THEN** the map manager screen shows no update control for the basemap
- **AND** still offers to download the basemap again for the source's published version

#### Scenario: Basemap status clears when its source's data is deleted

- **WHEN** the user switches source and the switch deletes the installed basemap
- **THEN** the basemap section shows the basemap as not installed for the newly active source
- **AND** the map view stops rendering the deleted basemap without an app restart

### Requirement: Indicate basemap status in map view

The system SHALL NOT show a basemap status indicator in the map view; the basemap renders silently as an overlay layer.

#### Scenario: Basemap active

- **WHEN** the basemap is loaded and rendering
- **THEN** the map view shows no basemap indicator

#### Scenario: No basemap

- **WHEN** no basemap is installed or loaded
- **THEN** the map view shows no basemap indicator

### Requirement: Report a basemap this build cannot read

The system SHALL report, in the installed basemap entry of the map manager, that the installed basemap carries a data format version this build cannot read. The notice SHALL name the installed version and the version this build reads, and SHALL name the remedy: delete the basemap and download it again. The notice SHALL appear whether or not the active source offers an update.

#### Scenario: Outdated installed basemap

- **WHEN** user opens the map manager screen
- **WHEN** the installed basemap's data format version is not the one this build reads
- **THEN** the basemap entry shows the installed version and the version this build reads
- **THEN** the entry names the remedy: delete the basemap and download it again

#### Scenario: Readable installed basemap shows no notice

- **WHEN** user opens the map manager screen
- **WHEN** the installed basemap's data format version is the one this build reads
- **THEN** the basemap entry shows no such notice

#### Scenario: Notice clears once a readable basemap is installed

- **WHEN** the basemap of an outdated entry is deleted or replaced by one of this build's data format version
- **THEN** the map manager's basemap entry shows no notice
