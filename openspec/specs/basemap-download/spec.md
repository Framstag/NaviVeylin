# Basemap Download

## Purpose

Download the world basemap archive from the map provider and install it into the app's map storage directory for use as an overlay map.

## Requirements

### Requirement: Download and extract basemap archive

The system SHALL download the selected basemap tar.gz archive from `{provider.uri}/basemap/{archive}` and extract it into `{mapsDir}/basemap/` (Android internal storage `filesDir/maps/basemap/`).

#### Scenario: Successful basemap download

- **WHEN** user initiates a basemap download
- **THEN** the system downloads the selected tar.gz archive
- **THEN** the system extracts the archive into `{mapsDir}/basemap/`
- **THEN** the system reports download and extraction progress
- **THEN** the basemap becomes available for rendering

#### Scenario: Basemap download failure

- **WHEN** the basemap download fails (network error, partial archive, extraction error)
- **THEN** the system cleans up partial files
- **THEN** the system reports the error to the user
- **THEN** any previously installed basemap remains intact

### Requirement: Select basemap variant

The system SHALL let the user choose between available basemap variants when the server hosts more than one archive.

#### Scenario: Multiple variants available

- **WHEN** the server hosts both full and minimal basemap archives
- **THEN** the system presents both options to the user
- **THEN** the system shows size and date for each variant

#### Scenario: Single variant available

- **WHEN** the server hosts exactly one basemap archive
- **THEN** the system offers that archive without a variant choice

### Requirement: Update basemap atomically

The system SHALL replace an existing basemap installation without corrupting it when a newer version is downloaded.

#### Scenario: Update existing basemap

- **WHEN** user triggers a basemap update
- **THEN** the system downloads the new archive to a temporary location
- **THEN** the system extracts to a temporary directory
- **THEN** on success, the system replaces the old basemap directory atomically
- **THEN** the system reloads the basemap so the new version renders

#### Scenario: Update fails

- **WHEN** the basemap update download or extraction fails
- **THEN** the previous basemap installation remains fully usable
- **THEN** the system cleans up temporary files

### Requirement: Cancel basemap download

The system SHALL support cancelling an in-progress basemap download.

#### Scenario: Cancel during download

- **WHEN** user cancels a basemap download
- **THEN** the system stops the transfer
- **THEN** the system removes partial files
- **THEN** any previously installed basemap is not affected

### Requirement: Delete basemap

The system SHALL support removing the installed basemap from the device.

#### Scenario: Delete installed basemap

- **WHEN** user deletes the installed basemap
- **THEN** the basemap directory is removed from storage
- **THEN** the basemap is no longer loaded for rendering
- **THEN** the app continues to run normally with regional maps only
