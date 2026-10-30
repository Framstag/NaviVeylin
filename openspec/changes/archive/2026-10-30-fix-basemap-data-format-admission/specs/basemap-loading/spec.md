## MODIFIED Requirements

### Requirement: Register basemap directory at client initialization

The system SHALL pass the basemap directory to the native client builder during client initialization when a basemap is installed AND its data format version — the first `uint32` of its `types.dat` — is the one this build reads. An installed basemap that carries another format version SHALL NOT be registered, and the refusal SHALL be recorded once as a diagnostics line naming the installed version and the version this build reads.

#### Scenario: Basemap installed at startup

- **WHEN** the app initializes the map client
- **WHEN** a basemap directory exists at `{mapsDir}/basemap/`
- **THEN** the system passes the basemap directory to the client builder
- **THEN** the native layer loads the basemap as an overlay database

#### Scenario: No basemap installed at startup

- **WHEN** the app initializes the map client
- **WHEN** no basemap directory exists
- **THEN** the system does not register a basemap directory
- **THEN** the app starts normally without a basemap overlay

#### Scenario: Installed basemap carries another build's data format

- **WHEN** the app initializes the map client
- **WHEN** a basemap is installed whose `types.dat` names a data format version other than the one this build reads
- **THEN** the system does not register a basemap directory
- **THEN** one diagnostics line names the installed version and the version this build reads
- **THEN** the app starts normally without a basemap overlay

#### Scenario: Data format version is unreadable

- **WHEN** the app initializes the map client
- **WHEN** a basemap directory exists but its `types.dat` is missing, shorter than the version field, or unreadable
- **THEN** the system registers the basemap directory as before
- **THEN** the native reader remains the authority on whether the database opens

#### Scenario: Basemap is nested one level below the lookup directory

- **WHEN** the installed basemap's `types.dat` sits at `{mapsDir}/basemap/basemap/types.dat` rather than beside the lookup directory
- **THEN** the same data format decision is taken on that file

### Requirement: Reload basemap after download or delete

The system SHALL reload or unload the basemap when it is downloaded, updated, or deleted while the app is running — including the first-time case where no basemap was installed when the app started. The current view SHALL re-render so the change is visible without restarting the app. A basemap directory whose data format version this build cannot read SHALL NOT be registered: the system SHALL unload the basemap instead and record the refusal once as a diagnostics line.

#### Scenario: Basemap downloaded while app is running

- **WHEN** user downloads or updates the basemap
- **THEN** the system triggers a reload of the basemap database
- **THEN** the current view re-renders with the basemap overlay active

#### Scenario: First-time basemap installation while app is running

- **WHEN** the app started without a basemap installed
- **WHEN** user downloads and installs the basemap while the app is running
- **THEN** the system registers the basemap for the running session
- **THEN** the system triggers a reload of the basemap database
- **THEN** the current view re-renders with the basemap overlay active without an app restart

#### Scenario: Basemap deleted while app is running

- **WHEN** user deletes the basemap
- **THEN** the system unloads the basemap database
- **THEN** the current view re-renders without the basemap overlay

#### Scenario: A registered basemap of another data format is unloaded

- **WHEN** a basemap download, update or source switch completes with a directory whose `types.dat` names another data format version
- **THEN** the system does not register that directory
- **THEN** the system unloads the basemap and the current view re-renders without it
- **THEN** one diagnostics line names the installed version and the version this build reads
