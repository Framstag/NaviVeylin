# Spec Delta

## MODIFIED Requirements

### Requirement: Report basemap version for updates
The system SHALL determine the installed basemap version and the server version so the UI can indicate whether an update is available, for every source whose listing publishes a version comparable with the installed basemap. A repository source publishes one per basemap version in its availability manifest, compared with the installed basemap's version and change time. A source that publishes no comparable version SHALL report that no update state exists instead of an update.

#### Scenario: Update available

- **WHEN** an installed basemap version is older than the server version
- **THEN** the system reports that an update is available

#### Scenario: No basemap installed

- **WHEN** no basemap directory exists locally
- **THEN** the system reports the basemap as available for initial download

#### Scenario: Up to date

- **WHEN** the installed basemap version matches the server version
- **THEN** the system reports that no update is available

#### Scenario: A repository source compares its manifest

- **WHEN** a repository source is active and its availability manifest names the installed basemap's version with a change time newer than the installed basemap's metadata
- **THEN** the system reports that an update is available for the basemap
- **AND** the comparison uses the version the client reads, not a newer version it cannot read

#### Scenario: Source publishes no comparable version

- **WHEN** the active source's listing publishes no version comparable with the installed basemap
- **THEN** the system reports no update state for the basemap
- **AND** issues no request whose only purpose is comparing the installed basemap with that source
