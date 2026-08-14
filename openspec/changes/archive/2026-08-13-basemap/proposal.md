# Basemap Support

## Why

The app renders only installed regional maps. Outside those areas the map is blank — no borders, country names, or coastlines for context. The map provider (karry.cz, already configured) hosts a low-detail world basemap at `{provider.uri}/basemap/` that is not listed in the standard map JSON listing. Upstream JavaScout supports basemap discovery, download, and overlay rendering (libosmscout PR #1755). The app should offer the same optional basemap capability so zoomed-out or uncovered areas show world context instead of empty canvas.

## What Changes

- **Basemap discovery**: Probe `{provider.uri}/basemap/` for tar.gz archives when the map manager screen opens or on explicit refresh; report availability, archive name, size, and date. Silence failures (basemap is optional, no user-facing error).
- **Basemap download**: Download the tar.gz archive, extract into `{mapsDir}/basemap/` (Android internal storage `filesDir/maps/basemap/`), with progress reporting, cancellation, partial-file cleanup, variant selection (full vs minimal), and atomic replace on update. Existing basemap installation must never be corrupted by a failed or cancelled download.
- **Basemap loading**: Pass the basemap directory to `OSMScoutClientBuilder.withBasemapLookupDirectory()` during client initialization when installed; reload the basemap after download/update while the app runs. The basemap renders as an overlay underneath regional maps and stays visible in areas no regional map covers.
- **Basemap rendering (JNI)**: Update the `libosmscout` submodule to upstream master (27 commits) and port the upstream `renderWithRouteAndPois` basemap logic onto the app's customized JNI render path: basemap ground tiles (`baseMapTiles`) spliced under regional map data, sea/land background supplied by the basemap when present, and standalone basemap rendering when no regional database is loaded.
- **Basemap UI**: Add a basemap section to the map manager screen (Compose): download/update/delete controls, progress, installed state with size/version, and a basemap status indicator in the map view.
- **Android HTTP constraint**: Port upstream `BasemapManager` from `java.net.http.HttpClient` to `HttpURLConnection` (per `map-download-infrastructure` spec — no desugaring). Tar.gz extraction stays pure-Java (streaming, no new dependencies).
- **Stylesheet**: Ship `basemap-render.oss` via app assets (copied by `AssetCopier` to the stylesheets directory).

## Capabilities

### New Capabilities
- `basemap-discovery`: probing the provider basemap path, parsing the directory listing, reporting availability/version without user-visible errors when unavailable
- `basemap-download`: downloading and extracting the basemap archive to `{mapsDir}/basemap/` with progress, cancellation, cleanup, variants, and atomic updates
- `basemap-loading`: wiring the basemap directory into the native client at startup, reloading after download, overlay rendering underneath regional maps
- `basemap-ui`: basemap entry and controls in the map manager screen plus a status indicator in the map view

### Modified Capabilities
- `map-download-infrastructure`: Hilt module provides a `BasemapManager` (HTTP via `HttpURLConnection`); client builder receives `withBasemapLookupDirectory()` when the basemap directory exists
- `map-download-ui`: basemap section (download/update/delete, progress, version) inside the existing map manager screen
- `map-render`: basemap overlay renders underneath regional maps via the updated JNI render path; areas without regional maps show basemap content instead of blank canvas

## Impact

- **Submodule**: `app/src/main/cpp/libosmscout` — update to upstream master (includes PR #1755, label-precedence and routing fixes already present locally are preserved); re-merge NaviVeylin local changes (custom JNI render path must be ported, not overwritten).
- **libosmscout-client-java**: new `BasemapManager.java` (with `BasemapArchive`/`BasemapInfo`), tests; local override in the `:osmscout-client-java` Gradle module (existing override pattern).
- **JNI**: `libosmscout-client-java/src/OSMScoutClient.cpp` — `renderWithRouteAndPois` basemap handling (baseMapTiles splice, standalone render, sea/land precedence).
- **App (Kotlin)**:
  - `app/src/main/java/com/naviveylin/di/MapDownloadModule.kt` — provide `BasemapManager`, add `withBasemapLookupDirectory()` when installed
  - `app/src/main/java/com/naviveylin/ui/map/MapCanvasViewModel.kt` — basemap reload + status state
  - `app/src/main/java/com/naviveylin/ui/mapmanager/` — basemap section in `MapManagerScreen`
  - `app/src/main/java/com/naviveylin/data/AssetCopier.kt` — ship `basemap-render.oss`
- **Assets**: `stylesheets/basemap-render.oss`.
- **Tests**: `BasemapManagerTest` (directory-listing parsing, version comparison, extraction, cancellation, cleanup) ported to the app's test setup (JVM/Robolectric, no network).
- **Dependencies**: none new (pure `java.*`).
- **Server**: relies on the `{uri}/basemap/` convention on osmscout.karry.cz; no provider changes.
