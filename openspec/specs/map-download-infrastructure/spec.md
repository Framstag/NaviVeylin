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
The system SHALL configure a map source registry that offers the built-in karry.cz provider and a libosmscout mapgen repository source, and SHALL use the active source for map list fetching and downloads.

#### Scenario: Default provider available
- **WHEN** MapManagerScreen loads
- **THEN** the karry.cz provider is pre-selected as the active source

#### Scenario: Repository source available alongside the built-in provider
- **WHEN** MapManagerScreen loads
- **THEN** the repository source is offered in the same selector as the built-in provider
- **AND** selecting it fetches the repository's region index instead of the provider's listing

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

#### Scenario: Repository requests use HttpURLConnection
- **WHEN** the system fetches a repository's region index, a database's metadata, a database file, or its basemap manifest or slot files
- **THEN** every one of those requests uses `HttpURLConnection`
- **AND** no repository code path imports `java.net.http`

### Requirement: Wake lock managed via download lifecycle
The system SHALL integrate wake lock acquisition and release with the map download lifecycle — acquire when the first download starts, release when the last download ends (complete, cancelled, or error).

#### Scenario: Wake lock acquired on first download
- **WHEN** the first map download begins
- **THEN** a wake lock is acquired via Android `PowerManager`

#### Scenario: Wake lock released on last download end
- **WHEN** the last active download finishes, is cancelled, or fails
- **THEN** the wake lock is released

### Requirement: Foreground service for download
The system SHALL start a foreground service with a visible notification during active map downloads to prevent the app from being killed by the Android power management system, and SHALL handle the platform ending that service.

- The service SHALL be started from the download the user initiated and SHALL stop when no download is active (complete, cancelled or failed)
- The service SHALL implement the platform's foreground-service timeout notification and end cleanly when the platform ends it
- A platform-ended service SHALL NOT be reported as a completed download: the affected downloads SHALL remain resumable and their state SHALL be readable after the service ends
- Any wake lock held by the service SHALL be released when the service ends, whether the app or the platform ended it

#### Scenario: Foreground service starts with download
- **WHEN** a map download starts
- **THEN** a foreground service is started with a notification showing download progress

#### Scenario: Foreground service stops when downloads end
- **WHEN** all downloads complete, are cancelled, or fail
- **THEN** the foreground service is stopped

#### Scenario: Platform timeout ends the service cleanly
- **WHEN** the platform ends the foreground service because its type's runtime limit was reached
- **THEN** the service SHALL stop itself and release its resources without an error dialog
- **AND** the affected download SHALL be reported as not completed and SHALL remain resumable

#### Scenario: Wake lock does not outlive the service
- **WHEN** the service ends (by the app or by the platform)
- **THEN** no wake lock from the download service remains held

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

### Requirement: Shipped builds permit cleartext repository transport

Shipped NaviVeylin builds SHALL permit cleartext HTTP for map repository transport, so a libosmscout mapgen repository served over plain HTTP on a local network is reachable after installation. The permission SHALL be app-wide, SHALL be identical in both distribution flavours, and SHALL NOT alter how an `https://` source is fetched.

#### Scenario: Plain-HTTP source is reachable in a shipped build

- **WHEN** a Play-installed (release) build tests or downloads from a repository base URL whose scheme is `http`
- **AND** the host answers
- **THEN** the request reaches the host
- **AND** no cleartext refusal is raised

#### Scenario: HTTPS source is unaffected

- **WHEN** a repository base URL's scheme is `https`
- **THEN** the request is made with the platform's normal TLS validation
- **AND** the cleartext permission changes neither the request nor its validation

#### Scenario: Both flavours permit it

- **WHEN** the same plain-HTTP source is used from the mobile build and from the automotive build
- **THEN** both reach the host
- **AND** the two flavours' transport policy is identical

### Requirement: A denied cleartext request reports the denial itself

The system SHALL report a repository request that the platform's cleartext policy denies as an unencrypted-transport refusal, naming the requested URL, instead of reproducing the platform's exception text or reporting a generic connection failure.

#### Scenario: Denied request names the reason

- **WHEN** the platform's cleartext policy denies a repository request
- **THEN** the reported failure identifies the refusal as one of unencrypted transport
- **AND** it names the URL that was requested
- **AND** it does not reproduce the platform's own exception sentence

#### Scenario: Permitted request is not reported as a denial

- **WHEN** the platform's cleartext policy permits the request
- **THEN** a failure, if any, is reported as its own kind and not as a cleartext refusal

### Requirement: A base URL that cannot be parsed is reported as an unusable URL

The system SHALL report a repository base URL it cannot parse as an unusable URL, naming the URL and the expected form, instead of reporting a transport or connection failure.

#### Scenario: An unparseable URL is named as unusable

- **WHEN** the source test or a fetch is given a base URL that cannot be parsed
- **THEN** the reported failure identifies the URL as unusable
- **AND** it names the URL and the expected form
- **AND** it is not reported as a transport or connection failure

#### Scenario: A parseable URL is never reported as unusable

- **WHEN** the base URL can be parsed
- **THEN** no unusable-URL failure is reported, whatever the request's outcome
