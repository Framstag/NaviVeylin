# Spec Delta — basemap-discovery

## MODIFIED Requirements

### Requirement: Probe basemap availability
The system SHALL probe the active map source's basemap location when the user opens the map manager screen or triggers an explicit refresh, and SHALL report whether a basemap is available. The probe SHALL be specific to the active source: an archive listing for the built-in provider, the basemap availability manifest for a repository source.

#### Scenario: Basemap available on server

- **WHEN** user opens the map manager screen while the built-in provider is active
- **THEN** the system probes `{provider.uri}/basemap/` for basemap archives
- **THEN** if a tar.gz archive exists, the system reports the basemap as available
- **THEN** the system reports the latest archive name, size, and date

#### Scenario: Basemap available on a repository source

- **WHEN** user opens the map manager screen while a repository source is active
- **THEN** the system probes the repository's basemap availability manifest
- **THEN** the system reports the basemap as available when the manifest lists a version this client can read
- **THEN** the system reports that version and the change time the manifest gives for it

#### Scenario: Basemap unavailable on server

- **WHEN** the probe receives HTTP 404, a connection error, or an unparseable listing
- **THEN** the system reports the basemap as unavailable
- **THEN** the system SHALL NOT show an error to the user (basemap is optional)
- **THEN** the system logs the probe URL and failure reason for debugging

#### Scenario: Probe follows the source, not the stale one

- **WHEN** the user switches the active source
- **THEN** the next probe addresses the newly active source
- **AND** a result from the previously active source is not reported as the new source's basemap state

### Requirement: Report basemap version for updates
The system SHALL determine the installed basemap version and the server version so the UI can indicate whether an update is available, for every source whose listing publishes a version comparable with the installed basemap. For a source that publishes no comparable version — the repository source in this change, which performs no update check — the system SHALL report that no update state exists instead of an update.

#### Scenario: Update available

- **WHEN** an installed basemap version is older than the server version
- **THEN** the system reports that an update is available

#### Scenario: No basemap installed

- **WHEN** no basemap directory exists locally
- **THEN** the system reports the basemap as available for initial download

#### Scenario: Up to date

- **WHEN** the installed basemap version matches the server version
- **THEN** the system reports that no update is available

#### Scenario: Source publishes no comparable version

- **WHEN** a repository source is active
- **THEN** the system reports no update state for the basemap
- **AND** issues no request whose only purpose is comparing the installed basemap with the repository's
