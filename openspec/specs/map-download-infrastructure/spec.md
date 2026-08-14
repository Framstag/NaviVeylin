# Map Download Infrastructure

## Purpose

Android-side wiring for map downloads — Hilt module providing `MapDownloadManager`, storage path resolution, provider configuration, jar module for JNI bridge, and native library loading.

## Requirements

### Requirement: Hilt module provides MapDownloadManager
The system SHALL provide a Hilt module that creates and injects a `MapDownloadManager` instance from the `libosmscout-client-java` JNI bridge.

#### Scenario: MapDownloadManager injected
- **WHEN** `MapManagerViewModel` requests a `MapDownloadManager`
- **THEN** Hilt provides a configured instance ready for use

### Requirement: Maps stored in internal storage
The system SHALL store downloaded maps in `context.filesDir/maps/` (Android internal storage).

#### Scenario: Download target is filesDir/maps
- **WHEN** a map download starts
- **THEN** the target directory resolves to `context.filesDir/maps/<map-name>/`
- **AND** no storage permissions are required

### Requirement: Download directory registered with native MapManager
The system SHALL register the download directory with the native `MapManager` via `OSMScoutClientBuilder.withMapLookupDirectories()` so previously downloaded maps are discovered on app restart.

#### Scenario: Installed maps persist across restarts
- **WHEN** app restarts and MapManagerScreen opens
- **THEN** previously downloaded maps appear in the installed list
- **AND** no Refresh is required

#### Scenario: Newly downloaded map visible immediately
- **WHEN** a map download completes and the directory is registered
- **THEN** the map appears in the installed list without requiring an app restart or manual Refresh
- **AND** the installed-list refresh reflects the completed native map lookup

### Requirement: Installed list reflects completed lookup
The system SHALL ensure the installed map list is refreshed only after the native map lookup has finished scanning the registered directories, so a completed download is never missing from the list due to an in-flight asynchronous scan.

#### Scenario: No race between registration and list refresh
- **WHEN** a download completes and triggers a native map lookup
- **THEN** the installed list refresh waits for the lookup to finish before reading the installed directories
- **AND** the newly downloaded map is present in the list

### Requirement: Multiple maps usable simultaneously
All downloaded maps SHALL be usable at the same time: opening a map adds it to the set of loaded databases, and the renderer SHALL display whichever loaded map(s) cover the current viewport — no switching between maps is required. When a map is opened, the initial viewport SHALL center on that map's bounding box unless a viewport was previously saved for that map.

#### Scenario: Opening second map keeps first usable
- **WHEN** user opens map B after map A
- **THEN** both map databases remain loaded
- **AND** panning to a region covered by map A renders map A
- **AND** panning to a region covered by map B renders map B

#### Scenario: Initial viewport centers on selected map
- **WHEN** user opens a map that has no saved viewport
- **THEN** the viewport centers on the map's bounding box
- **AND** the map is visible without manual panning

#### Scenario: Per-map viewport resumes
- **WHEN** user reopens a map that has a saved viewport
- **THEN** the viewport resumes at the saved position for that map

### Requirement: Clean redownload
The system SHALL ensure that re-downloading a map that was previously downloaded and deleted starts from a clean state, so no state from the previous download can cause the re-download to fail or the map to remain invisible.

#### Scenario: Delete then re-download same map
- **WHEN** user deletes an installed map
- **AND** downloads the same map again
- **THEN** the download completes without error
- **AND** the map appears in the installed list

#### Scenario: Failed download does not poison next attempt
- **WHEN** a download fails or is cancelled
- **THEN** any partial files and metadata from that attempt are removed
- **AND** a subsequent download of the same map starts from a clean directory
- **AND** the subsequent download is not affected by the previous failure

### Requirement: Delete removes map manager registration
The system SHALL remove a deleted map's directory from the native map manager's lookup directories, not just delete its files, so a later re-download of the same map triggers a fresh lookup.

#### Scenario: Deleted map removed from lookup set
- **WHEN** user deletes an installed map
- **THEN** the map's directory is removed from the map manager's lookup directories
- **AND** the map no longer appears in the installed list

#### Scenario: Re-registration always triggers lookup
- **WHEN** a map directory is registered with the map manager
- **AND** the directory is already present in the lookup set (e.g., after a delete that left it registered)
- **THEN** a fresh lookup is still triggered
- **AND** the map appears in the installed list

### Requirement: Default map provider configured
The system SHALL configure the default map provider (karry.cz) for map list fetching and downloads.

#### Scenario: Default provider available
- **WHEN** MapManagerScreen loads
- **THEN** the karry.cz provider is pre-selected in the provider dropdown

### Requirement: Error handling for download failures
The system SHALL handle download errors gracefully and report them to the user.

#### Scenario: Download error shown
- **WHEN** a download fails due to network error or server issue
- **THEN** the entry shows an error state with the error message
- **AND** the user can retry by tapping [Download] again

### Requirement: Installed maps discovered at startup
The system SHALL discover previously downloaded maps when the app starts, so they appear in the installed list without re-downloading.

#### Scenario: Previously downloaded maps visible
- **WHEN** user opens MapManagerScreen
- **THEN** any maps already present in `filesDir/maps/` appear in the installed state

### Requirement: Jar module for JNI bridge
The system SHALL compile the `libosmscout-client-java` Java sources into a jar via a dedicated Gradle module (`:osmscout-client-java`). Local overrides for Android-specific fixes SHALL take priority over submodule sources.

#### Scenario: Jar module compiles
- **WHEN** developer runs `./gradlew :osmscout-client-java:compileJava`
- **THEN** all Java sources from the submodule are compiled
- **AND** local overrides (OSMScoutClientBuilder, OSMScoutClient, MapDownloadManager, AvailableMapEntry) replace submodule copies

### Requirement: Native library debug suffix handling
The system SHALL handle the Android debug build convention of appending `d` to native library names.

#### Scenario: Debug build loads library
- **WHEN** app is built in debug mode
- **THEN** `System.loadLibrary` tries `osmscout_client_java` first, then falls back to `osmscout_client_javad`
- **AND** the app does not crash with `UnsatisfiedLinkError`

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

### Requirement: Wake lock managed via download lifecycle
The system SHALL integrate wake lock acquisition and release with the map download lifecycle — acquire when the first download starts, release when the last download ends (complete, cancelled, or error).

#### Scenario: Wake lock acquired on first download
- **WHEN** the first map download begins
- **THEN** a wake lock is acquired via Android `PowerManager`

#### Scenario: Wake lock released on last download end
- **WHEN** the last active download finishes, is cancelled, or fails
- **THEN** the wake lock is released

### Requirement: Foreground service for download
The system SHALL start a foreground service with a visible notification during active map downloads to prevent the app from being killed by the Android power management system.

#### Scenario: Foreground service starts with download
- **WHEN** a map download starts
- **THEN** a foreground service is started with a notification showing download progress

#### Scenario: Foreground service stops when downloads end
- **WHEN** all downloads complete, are cancelled, or fail
- **THEN** the foreground service is stopped

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
