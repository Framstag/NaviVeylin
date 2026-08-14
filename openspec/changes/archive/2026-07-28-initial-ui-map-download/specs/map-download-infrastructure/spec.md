# Map Download Infrastructure

## Purpose

Android-side wiring for map downloads — Hilt module providing `MapDownloadManager`, storage path resolution, provider configuration, jar module for JNI bridge, and native library loading.

## ADDED Requirements

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
The system SHALL use `java.net.HttpURLConnection` instead of `java.net.http.HttpClient` for all map download HTTP requests.

#### Scenario: HTTP works on all Android versions
- **WHEN** `MapDownloadManager.fetchAvailableMaps()` or `downloadMap()` is called
- **THEN** HTTP requests use `HttpURLConnection`
- **AND** no desugaring or additional dependencies are required
