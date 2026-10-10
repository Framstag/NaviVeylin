# Spec Delta — basemap-download

## MODIFIED Requirements

### Requirement: Download and extract basemap archive
The system SHALL install the basemap the active source publishes, into `{mapsDir}/basemap/` (Android internal storage `filesDir/maps/basemap/`), and SHALL obtain it the way that source publishes it: a tar.gz archive downloaded from `{provider.uri}/basemap/{archive}` and extracted for the built-in provider, or a version slot downloaded from `basemap/v{version}/` with each file verified against the metadata's size and CRC-32 for a repository source. Progress, atomic replacement, cancellation and deletion SHALL behave the same for both.

#### Scenario: Successful basemap download

- **WHEN** user initiates a basemap download while the built-in provider is active
- **THEN** the system downloads the selected tar.gz archive
- **THEN** the system extracts the archive into `{mapsDir}/basemap/`
- **THEN** the system reports download and extraction progress
- **THEN** the basemap becomes available for rendering

#### Scenario: Successful basemap download from a repository

- **WHEN** user initiates a basemap download while a repository source is active
- **THEN** the system downloads that version's files, verifying each against the metadata
- **THEN** the system installs them into `{mapsDir}/basemap/`
- **THEN** the system reports download progress
- **THEN** the basemap becomes available for rendering

#### Scenario: Basemap download failure

- **WHEN** the basemap download fails (network error, partial archive, extraction error, or a file that fails verification)
- **THEN** the system cleans up partial files
- **THEN** the system reports the error to the user
- **THEN** any previously installed basemap remains intact

#### Scenario: A tar.gz archive is not used by a repository source

- **WHEN** a repository source is active and its basemap location also serves tar.gz archives
- **THEN** the system installs from the repository's version slot
- **AND** does not extract the archive
