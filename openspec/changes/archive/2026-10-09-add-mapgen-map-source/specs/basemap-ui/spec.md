# Spec Delta — basemap-ui

## MODIFIED Requirements

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
