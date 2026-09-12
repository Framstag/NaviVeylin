## MODIFIED Requirements

### Requirement: Provide basemap download/update control
The system SHALL provide a control to download or update the basemap in the map manager screen. While a basemap download or update is in progress, the basemap section SHALL collapse to a compact status line; the full progress indication SHALL appear in the active-downloads section of the map manager screen, not duplicated in the basemap section.

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
