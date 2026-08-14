## Context

NaviVeylin currently has a complete build scaffold (Gradle, CMake, vcpkg, submodule) but zero application code — no Kotlin source files, no Compose screens, no ViewModels, no navigation graph. The `libosmscout-client-java` submodule provides a complete JNI bridge including `MapDownloadManager`, `MapDownloadListener`, `AvailableMapEntry`, and `MapProvider` classes with all native methods implemented.

JavaScout (the JavaFX desktop demo) already implements map download with the same JNI bridge. Its design revealed critical constraints:
- `java.net.http.HttpClient` crashes the JVM when called from JNI/native code (OpenJDK 17.0.2 G1 read-barrier bug) — all HTTP must run on Java threads
- `MapManager::LookupDatabases()` is not automatic — must be triggered explicitly after download
- Downloaded maps need per-subdirectory registration, not opening the parent directory as a database

This change creates the first visible UI and the map download pipeline, reusing the existing JNI bridge without any C++ modifications.

## Goals / Non-Goals

**Goals:**
- Create `MainScreen` composable with empty map canvas (grid pattern placeholder) and top-right overflow menu
- Create `MapManagerScreen` — unified map management view (no tabs) with available/active/installed states inline, search field, and inline progress on download button
- Wire `MapDownloadManager` from `libosmscout-client-java` into Android via Hilt
- Store maps in `context.filesDir/maps/` (Android internal storage, no permissions)
- Register download directory with native `MapManager` so installed maps persist across restarts
- Use `java.net.HttpURLConnection` for HTTP (Android-compatible, no desugaring needed)
- Add navigation route for MapManagerScreen

**Non-Goals:**
- Actual map rendering (deferred to future change)
- Map data directory picker or bundled demo map
- Settings screen (placeholder only in menu)
- Multiple map providers in UI (karry.cz only, extendable)
- Resume interrupted downloads (`.download` temp suffix cleanup only)
- Map update detection or auto-update

## Decisions

### D1: Unified list over tabs for map management

**Decision:** Single scrollable tree view with inline state indicators instead of three tabs (Available / Downloads / Installed).

**Rationale:** Maps have a lifecycle (available → downloading → installed), not a category. Tabs force the user to switch contexts to find a map's status. A unified list shows every map's current state at a glance — available maps show [Download], downloading maps show progress inline, installed maps show ✅ and [Delete]. Active downloads appear as a collapsible section at the top, hidden when empty.

**Alternatives considered:**
- **Three tabs** (JavaScout pattern): Familiar but requires context switching. User must remember which tab a map was in.
- **Single list with filter chips**: Could work but adds UI complexity. The lifecycle-based approach is simpler.

### D2: Top-right overflow menu for secondary actions

**Decision:** Overflow menu button (⋮) in top-right corner, not a FAB or bottom bar. Uses `statusBarsPadding()` to avoid system UI overlap.

**Rationale:** The bottom-right FAB position is reserved for the future search button (Android standard). The top-right overflow menu is the canonical Material 3 pattern for secondary actions and settings. When a top app bar is added later, the overflow menu slides naturally into it. `statusBarsPadding()` ensures the button is below the status bar on edge-to-edge displays.

**Alternatives considered:**
- **Bottom navigation bar**: Too much structure for two entries (Download Maps + Settings placeholder).
- **Left drawer (hamburger)**: Common in map apps but heavyweight for this stage. Better added when there are more navigation destinations.
- **Secondary FAB**: Non-standard, conflicts with search FAB positioning.

### D3: Hilt module for MapDownloadManager

**Decision:** `MapDownloadModule` Hilt `@Module` provides a singleton `MapDownloadManager` instance, created from `OSMScoutClient.getMapDownloadManager()`. The `OSMScoutClient` is configured with `withMapLookupDirectories(mapsRootDir)` so the native `MapManager` scans the download directory on startup.

**Rationale:** `MapDownloadManager` is a Java class from the JNI bridge that wraps native methods. It needs to be a singleton (native state) and injectable into ViewModels. Hilt manages its lifecycle and makes it testable via mock replacement. Passing the actual download directory to the builder ensures installed maps are discovered on app restart.

**Alternatives considered:**
- **Manual singleton in Application class**: Works but bypasses DI. Harder to test and replace.
- **Create per-screen**: `MapDownloadManager` holds native state (active downloads list) — multiple instances would be inconsistent.

### D4: Storage at filesDir/maps/

**Decision:** Maps stored at `context.filesDir/maps/<map-name>/` using Android internal storage.

**Rationale:** No storage permissions required. Directory is automatically scoped to the app. Survives app lifetime. Maps are private to the app (no other apps can corrupt them). `filesDir` is cleaned on app uninstall, which is correct behavior.

**Alternatives considered:**
- **External storage** (`getExternalFilesDir`): Requires MANAGE_EXTERNAL_STORAGE on API 30+, adds permission complexity. Maps are app-internal data, not user-facing files.
- **MediaStore**: Overkill — maps are not media files.
- **Custom SD card path**: Adds configuration UI and permission handling. Defer if needed.

### D5: HttpURLConnection for HTTP (not java.net.http.HttpClient)

**Decision:** Use `java.net.HttpURLConnection` (JDK standard since Java 1.1) instead of `java.net.http.HttpClient` (Java 11+).

**Rationale:** `java.net.http.HttpClient` is not available on Android API < 33 without core-library-desugaring. Even with desugaring configured, R8/proguard cannot resolve the class at compile time for pure Java library modules. `HttpURLConnection` is available on all Android versions without any desugaring, has no R8 issues, and provides the same functionality (GET requests, streaming downloads, cancellation via stream close).

**Alternatives considered:**
- **java.net.http.HttpClient with desugaring**: Requires `coreLibraryDesugaring` dependency and configuration. R8 still reports missing classes for pure Java library modules. More complex build setup.
- **OkHttp**: More Android-idiomatic, better cancellation, interceptors. But `MapDownloadManager` is a Java class from the submodule — adding OkHttp would require modifying the JNI bridge or creating a parallel download path. Defer if HTTP issues arise.

### D6: Grid pattern via Compose Canvas

**Decision:** Draw the grid pattern using Compose `Canvas` with `drawLine` calls, not a static image asset.

**Rationale:** The grid is a visual placeholder for the future map. A Canvas-drawn grid is resolution-independent, themable (adapts to dark/light), and costs zero asset bytes. The grid lines are subtle (low alpha, thin strokes) so they don't distract when the real map renders.

**Alternatives considered:**
- **Static image asset**: Needs multiple densities, can't adapt to theme. Less elegant.
- **Modifier/border approach**: Can't create a full grid with standard Compose modifiers.
- **No grid, just solid color**: Less suggestive of a map canvas.

### D7: MapManagerViewModel with StateFlow

**Decision:** `MapManagerViewModel` holds `MapManagerUiState` (StateFlow) with available entries, active downloads, installed map paths, downloading names set, loading flag, and error. The ViewModel orchestrates calls to `MapDownloadManager` on `Dispatchers.IO`. Download state changes are propagated via `downloadingNames: Set<String>` in the UI state to trigger recomposition.

**Rationale:** Standard Android architecture. ViewModel survives config changes. StateFlow integrates with Compose `collectAsState()`. Background work on `Dispatchers.IO` keeps UI thread free. The `downloadingNames` set ensures the `LazyColumn` recomposes when a download starts, transforming the Download button into a progress indicator immediately.

**Alternatives considered:**
- **Loading state directly in composable**: Works for simple cases but loses state on config change and mixes concerns.
- **Repository pattern**: Premature — there's one data source (MapDownloadManager). Add when there are multiple sources.

### D8: Jar module for JNI bridge (osmscout-client-java)

**Decision:** Create `:osmscout-client-java` Gradle module (Java library) that compiles the submodule's Java sources and produces a jar. The app depends on this jar. Local overrides for `OSMScoutClientBuilder`, `OSMScoutClient`, `MapDownloadManager`, and `AvailableMapEntry` are placed in the module's local source directory and take priority over submodule copies.

**Rationale:** The submodule's Java sources must be compiled and available as a dependency. A separate Gradle module is cleaner than copying sources into the app or relying on source sets. Local overrides allow Android-specific fixes (debug suffix handling, HttpURLConnection) without modifying upstream code.

**Alternatives considered:**
- **Copy sources into app/src/main/java**: Works but duplicates code and makes updates harder.
- **Source sets in app module**: KSP/Hilt annotation processing can't resolve Java classes from external source sets.
- **Prebuilt jar from submodule build**: Requires full native build chain (CMake, vcpkg) which is heavy for development.

### D9: Native library debug suffix handling

**Decision:** Both `OSMScoutClientBuilder` and `OSMScoutClient` static initializers try `System.loadLibrary("osmscout_client_java")` first, then fall back to `"osmscout_client_javad"` on `UnsatisfiedLinkError`.

**Rationale:** Android Gradle plugin appends `d` suffix to native library names in debug builds (`libosmscout_client_javad.so`), but the upstream code calls `System.loadLibrary("osmscout_client_java")` (without `d`). The try/catch pattern handles both debug and release builds without modifying the submodule.

**Alternatives considered:**
- **Disable debug suffix in Gradle**: Not directly configurable via public API.
- **Pre-load in Application class**: Runs too late — Hilt creates singletons before Application.onCreate().

### D10: Search field in MapManagerScreen

**Decision:** Add an `OutlinedTextField` with search icon between the provider selector and the tree view. Filters the available entries by name or path segment as the user types.

**Rationale:** The map list from karry.cz contains hundreds of entries across multiple continents. A search field lets users quickly find specific maps without scrolling through the entire tree. Material 3 `OutlinedTextField` with leading search icon follows standard Android search patterns.

### D11: Download button transforms to progress indicator

**Decision:** When a download starts, the [Download] button transforms to show a `CircularProgressIndicator` and [Cancel] button inline. Progress percentage is shown next to the spinner.

**Rationale:** Material Design guidelines recommend showing progress inline rather than in a separate notification or dialog. The user sees immediate feedback that the download has started and can cancel if needed. The progress is driven by `downloadingNames` in the UI state, which triggers recomposition via StateFlow.

### D12: Installed maps discovered on screen open

**Decision:** `LaunchedEffect(Unit)` in `MapManagerScreen` calls `refreshInstalledMaps()` when the screen first appears. If no available entries have been fetched yet, synthetic `AvailableMapEntry` leaf entries are created from the installed map paths so they appear in the tree immediately.

**Rationale:** Users expect to see their previously downloaded maps without having to tap Refresh. The native `MapManager` scans the download directory on startup (via `withMapLookupDirectories`), and the ViewModel creates synthetic entries for any discovered maps. When the user taps Refresh, the synthetic entries are replaced with real entries from the provider.

### D13: 16 KB page size alignment

**Decision:** Added `-Wl,-z,max-page-size=16384` to CMake shared linker flags for all native libraries.

**Rationale:** Android 15+ (API 35+) requires native libraries to have LOAD segments aligned at 16 KB boundaries for Google Play distribution. Since the app targets SDK 36, this flag ensures compatibility.

### D14: ProGuard/R8 rules for JNI bridge

**Decision:** Keep all classes in `com.framstag.libosmscout.client.**` and suppress `-dontwarn` for `java.net.http` classes (not available on Android without desugaring, but not used at runtime since we use `HttpURLConnection`).

**Rationale:** R8 would strip JNI bridge classes and their native method declarations, causing `UnsatisfiedLinkError` at runtime. The `-dontwarn` rules prevent false positives from the unused `java.net.http` imports in the original submodule code.

## Risks / Trade-offs

| Risk | Mitigation |
|------|------------|
| **HttpURLConnection lacks modern features** | No built-in redirect following, connection pooling, or HTTP/2. Acceptable for simple GET/download use case. Upgrade to OkHttp if needed. |
| **JNI crash if HttpClient called from native code** | All HTTP runs on Java threads via `MapDownloadManager` (same pattern as JavaScout). No JNI HTTP calls. |
| **Large map download blocks UI thread** | `MapDownloadManager.downloadMap()` runs on a background thread. Progress callbacks throttled to ~4 Hz. ViewModel collects on `Dispatchers.IO`. |
| **Downloaded maps invisible after restart** | `withMapLookupDirectories(mapsRootDir)` registers the download directory with native `MapManager`. `LaunchedEffect` triggers `refreshInstalledMaps()` on screen open. |
| **Partial download on crash** | `.download` temp suffix prevents pickup. Stale `.download` files cleaned on next scan. |
| **No checksum validation** | File sizes used for progress only. No integrity check. Acceptable for initial implementation. |
| **Single provider hardcoded** | karry.cz is the only public libosmscout map provider. Provider abstraction exists in `MapProvider` class — adding more is a data change, not a code change. |
| **arm64-v8a native build fails** | Pre-existing `ThreadedBreaker` vtable linker issue in libosmscout for arm64. Build with `-Pandroid.injected.build.abi=x86_64` for emulator testing. |
