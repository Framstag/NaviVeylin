## Why

NaviVeylin has a complete build scaffold but zero UI — no screens, no map canvas, no way to get map data onto the device. Users need a visible app entry point and a way to download OSM maps before any rendering or routing can work.

## What Changes

- Create `MainScreen` composable — full-screen empty map canvas with subtle grid pattern, centered placeholder text, and top-right overflow menu
- Add overflow menu popup with "Download Maps" entry (placeholder for future settings)
- Create full-screen `MapManagerScreen` — unified map management view combining available maps browsing, active download progress, and installed map management in one list (no tabs)
- Wire `MapDownloadManager` (from `libosmscout-client-java` JNI bridge) into Android via Hilt module
- Store downloaded maps in `context.filesDir/maps/` (Android internal storage, no permissions needed)
- Add `MapManagerScreen` route to `NavGraph`
- Use `java.net.http.HttpClient` for HTTP (same as JavaScout, works on API 26+ via desugaring)

## Capabilities

### New Capabilities

- `map-canvas-screen`: Initial full-screen composable with empty map canvas (grid pattern placeholder), top-right overflow menu, and popup with navigation to download screen
- `map-download-ui`: Unified map management screen — browse available maps from provider, view active download progress inline, manage installed maps (delete), all in one scrollable tree view
- `map-download-infrastructure`: Android-side wiring for map downloads — Hilt module providing `MapDownloadManager`, storage path resolution to `filesDir/maps/`, provider configuration

### Modified Capabilities

<!-- No existing spec requirements change — we're adding new screens and infrastructure, not altering existing contracts -->

## Impact

- **New files** under `app/src/main/java/com/naviveylin/`:
  - `ui/MainScreen.kt` — map canvas + overflow menu
  - `ui/mapmanager/MapManagerScreen.kt` — unified download/management screen
  - `ui/mapmanager/MapManagerViewModel.kt` — state for available/active/installed maps
  - `ui/mapmanager/MapStorageManager.kt` — resolves `filesDir/maps/`, manages directories
  - `di/MapDownloadModule.kt` — Hilt module providing `MapDownloadManager`
- **Modified files**:
  - `app/src/main/java/com/naviveylin/MainActivity.kt` — add `WindowSizeClass`, set up NavHost
  - `app/src/main/java/com/naviveylin/navigation/NavGraph.kt` — add `MapManagerScreen` route
  - `app/src/main/java/com/naviveylin/NaviVeylinApp.kt` — `@HiltAndroidApp` application class
- **Dependencies**: No new Gradle dependencies — `java.net.http.HttpClient` is JDK standard, `MapDownloadManager` comes from `libosmscout-client-java` submodule
- **Storage**: Maps stored at `context.filesDir/maps/` — no storage permissions needed, survives app lifetime
