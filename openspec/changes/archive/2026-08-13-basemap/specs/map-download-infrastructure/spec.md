# Map Download Infrastructure

## ADDED Requirements

### Requirement: BasemapManager provided via Hilt

The system SHALL provide a `BasemapManager` via Hilt, configured with the maps directory and the default map provider, for basemap discovery, download, and management.

#### Scenario: BasemapManager injected

- **WHEN** a ViewModel requests a `BasemapManager`
- **THEN** Hilt provides a configured instance ready for use

#### Scenario: BasemapManager targets internal storage

- **WHEN** the `BasemapManager` downloads or extracts the basemap
- **THEN** it operates under `context.filesDir/maps/basemap/`
- **AND** no storage permissions are required

### Requirement: Basemap directory registered with native client

The system SHALL pass the basemap directory to the native client builder via `withBasemapLookupDirectory()` when a basemap directory exists, and SHALL omit it otherwise.

#### Scenario: Basemap present at client build time

- **WHEN** the app builds the `OSMScoutClient`
- **WHEN** `{mapsDir}/basemap/` exists
- **THEN** the builder receives the basemap directory
- **AND** the native layer loads the basemap as an overlay

#### Scenario: No basemap at client build time

- **WHEN** the app builds the `OSMScoutClient`
- **WHEN** no basemap directory exists
- **THEN** the builder is created without a basemap directory
- **AND** the app starts normally

## MODIFIED Requirements

### Requirement: HttpURLConnection for HTTP

The system SHALL use `java.net.HttpURLConnection` instead of `java.net.http.HttpClient` for all map download HTTP requests, including basemap probe and download requests.

#### Scenario: HTTP works on all Android versions

- **WHEN** `MapDownloadManager.fetchAvailableMaps()`, `downloadMap()`, or the basemap probe/download is called
- **THEN** HTTP requests use `HttpURLConnection`
- **AND** no desugaring or additional dependencies are required

#### Scenario: Basemap requests use HttpURLConnection

- **WHEN** the system probes `{provider.uri}/basemap/` or downloads a basemap archive
- **THEN** the HTTP requests use `HttpURLConnection`
- **AND** the basemap flow works without `java.net.http` availability
