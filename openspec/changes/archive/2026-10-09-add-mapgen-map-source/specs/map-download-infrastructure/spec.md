# Spec Delta — map-download-infrastructure

## MODIFIED Requirements

### Requirement: Default map provider configured
The system SHALL configure a map source registry that offers the built-in karry.cz provider and a libosmscout mapgen repository source, and SHALL use the active source for map list fetching and downloads.

#### Scenario: Default provider available
- **WHEN** MapManagerScreen loads
- **THEN** the karry.cz provider is pre-selected as the active source

#### Scenario: Repository source available alongside the built-in provider
- **WHEN** MapManagerScreen loads
- **THEN** the repository source is offered in the same selector as the built-in provider
- **AND** selecting it fetches the repository's region index instead of the provider's listing

### Requirement: HttpURLConnection for HTTP
The system SHALL use `java.net.HttpURLConnection` instead of `java.net.http.HttpClient` for all map download HTTP requests, including basemap probe and download requests.

#### Scenario: HTTP works on all Android versions
- **WHEN** `MapDownloadManager.fetchAvailableMaps()` or `downloadMap()` is called
- **THEN** HTTP requests use `HttpURLConnection`
- **AND** no desugaring or additional dependencies are required

#### Scenario: Basemap requests use HttpURLConnection
- **WHEN** the system probes `{provider.uri}/basemap/` or downloads a basemap archive
- **THEN** the HTTP requests use `HttpURLConnection`
- **AND** the basemap flow works without `java.net.http` availability

#### Scenario: Repository requests use HttpURLConnection
- **WHEN** the system fetches a repository's region index, a database's metadata, a database file, or its basemap manifest or slot files
- **THEN** every one of those requests uses `HttpURLConnection`
- **AND** no repository code path imports `java.net.http`

## ADDED Requirements

### Requirement: Database format version has one source of truth

The database format version the app requests from a source — the version slot of a repository database and the version bounds of a provider listing — SHALL come from a single value that matches the version the native client can read. No request URL and no metadata check SHALL embed a literal version value.

#### Scenario: Version slot and listing bounds agree

- **WHEN** the app requests a provider listing and when it builds a repository database's version slot URL
- **THEN** both use the same version value
- **AND** that value is the client's supported database format version

#### Scenario: A version change reaches every request

- **WHEN** the client's supported database format version changes
- **THEN** the provider listing's requested bounds and the repository's version slot URL both change with it
- **AND** no other source of the version has to be edited

### Requirement: Repository downloads join the download lifecycle

A repository download — regional database or basemap slot — SHALL be a first-class map download: it SHALL report progress and completion through the same listener contract as a provider download, SHALL be cancellable, and SHALL keep the foreground service and its wake lock alive while it runs.

#### Scenario: Repository download keeps the foreground service alive

- **WHEN** the only active download is a repository database or basemap slot download
- **THEN** the foreground service runs with a progress notification
- **AND** no wake lock outlives the service

#### Scenario: Repository download is cancellable

- **WHEN** the user cancels a repository download
- **THEN** the transfer stops, partial files are removed, and the entry returns to its available state
- **AND** the same cancellation path serves provider downloads

#### Scenario: Repository download completes into the installed list

- **WHEN** a repository download finishes and verifies
- **THEN** the directory is registered with the native map manager
- **AND** the map appears in the installed list without an app restart
