# Basemap Discovery

## Purpose

Detect whether the map provider hosts a world basemap at the well-known basemap path, even though it is not listed in the standard map JSON listing.

## Requirements

### Requirement: Probe basemap availability

The system SHALL probe the provider's basemap path when the user opens the map manager screen or triggers an explicit refresh, and SHALL report whether a basemap archive is available.

#### Scenario: Basemap available on server

- **WHEN** user opens the map manager screen
- **THEN** the system probes `{provider.uri}/basemap/` for basemap archives
- **THEN** if a tar.gz archive exists, the system reports the basemap as available
- **THEN** the system reports the latest archive name, size, and date

#### Scenario: Basemap unavailable on server

- **WHEN** the probe receives HTTP 404, a connection error, or an unparseable listing
- **THEN** the system reports the basemap as unavailable
- **THEN** the system SHALL NOT show an error to the user (basemap is optional)
- **THEN** the system logs the probe URL and failure reason for debugging

### Requirement: Report basemap version for updates

The system SHALL determine the installed basemap version and the server version so the UI can indicate whether an update is available.

#### Scenario: Update available

- **WHEN** an installed basemap version is older than the server version
- **THEN** the system reports that an update is available

#### Scenario: No basemap installed

- **WHEN** no basemap directory exists locally
- **THEN** the system reports the basemap as available for initial download

#### Scenario: Up to date

- **WHEN** the installed basemap version matches the server version
- **THEN** the system reports that no update is available
