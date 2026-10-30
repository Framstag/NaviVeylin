## ADDED Requirements

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
