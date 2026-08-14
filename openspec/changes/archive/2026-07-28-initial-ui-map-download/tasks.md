## 1. Foundation — App scaffold (Kotlin)

- [x] 1.1 Create `NaviVeylinApp.kt` — `@HiltAndroidApp` Application class (spec: `map-canvas-screen`)
- [x] 1.2 Create `MainActivity.kt` — single Activity with `setContent`, `WindowSizeClass` detection, Compose NavHost (spec: `map-canvas-screen`)
- [x] 1.3 Create `NavGraph.kt` — route definitions for `MainScreen` and `MapManagerScreen` (spec: `map-canvas-screen`)
- [x] 1.4 Create `app/src/main/res/values/` resource files — themes (Material 3), strings, colors (spec: `map-canvas-screen`)
- [x] 1.5 Verify `./gradlew :app:assembleDebug` compiles with new Kotlin source files

## 2. Map Canvas Screen

- [x] 2.1 Create `MainScreen.kt` — full-screen `Box` with grid pattern drawn via Compose `Canvas` (spec: `map-canvas-screen`)
- [x] 2.2 Add centered placeholder text "Map will render here" over the grid (spec: `map-canvas-screen`)
- [x] 2.3 Add top-right overflow menu button (⋮) with `DropdownMenu` and `statusBarsPadding()` (spec: `map-canvas-screen`)
- [x] 2.4 Add "Download Maps" menu item that navigates to `MapManagerScreen` route (spec: `map-canvas-screen`)
- [x] 2.5 Add placeholder "Settings" menu item (disabled or stub) for future extensibility (spec: `map-canvas-screen`)
- [x] 2.6 Verify `MainScreen` renders correctly — grid visible, menu opens, navigation triggers

## 3. Map Download Infrastructure

- [x] 3.1 Create `MapStorageManager.kt` — resolves `context.filesDir/maps/` path, provides target directory for downloads (spec: `map-download-infrastructure`)
- [x] 3.2 Create `:osmscout-client-java` Gradle module — compiles submodule Java sources into jar, with local overrides for Android compatibility (spec: `map-download-infrastructure`)
- [x] 3.3 Create `MapDownloadModule.kt` — Hilt `@Module` providing singleton `MapDownloadManager` and `OSMScoutClient` with `withMapLookupDirectories(mapsRootDir)` (spec: `map-download-infrastructure`)
- [x] 3.4 Configure default map provider (karry.cz) in the module (spec: `map-download-infrastructure`)
- [x] 3.5 Override `OSMScoutClientBuilder.java` and `OSMScoutClient.java` — handle debug `d` suffix in `System.loadLibrary` (spec: `map-download-infrastructure`)
- [x] 3.6 Override `MapDownloadManager.java` — use `HttpURLConnection` instead of `java.net.http.HttpClient` (spec: `map-download-infrastructure`)
- [x] 3.7 Override `AvailableMapEntry.java` — make leaf constructor public for synthetic entry creation (spec: `map-download-infrastructure`)
- [x] 3.8 Add ProGuard/R8 keep rules for JNI bridge classes and `-dontwarn` for `java.net.http` (spec: `map-download-infrastructure`)
- [x] 3.9 Add 16 KB page size alignment flag to CMakeLists.txt (spec: `map-download-infrastructure`)
- [x] 3.10 Verify Hilt injection works — `MapDownloadManager` is injectable and non-null

## 4. Map Manager Screen

- [x] 4.1 Create `MapManagerViewModel.kt` — holds `StateFlow` for available maps, active downloads, installed maps, downloading names; orchestrates `MapDownloadManager` calls on `Dispatchers.IO` (spec: `map-download-ui`, `map-download-infrastructure`)
- [x] 4.2 Create `MapManagerScreen.kt` — full-screen composable with top bar (back arrow + title), provider dropdown, refresh button (spec: `map-download-ui`)
- [x] 4.3 Implement collapsible "Active Downloads" section at top — visible only when downloads active, shows progress bars and cancel buttons (spec: `map-download-ui`)
- [x] 4.4 Implement unified tree view — hierarchical list grouped by region, each entry shows state (available/downloading/installed) with appropriate action button (spec: `map-download-ui`)
- [x] 4.5 Wire download action — [Download] button calls `MapDownloadManager.downloadMap()`, entry transitions to downloading state with inline progress (spec: `map-download-ui`)
- [x] 4.6 Wire cancel action — [Cancel] button calls `MapDownloadManager.cancelDownload()`, entry returns to available state (spec: `map-download-ui`)
- [x] 4.7 Wire delete action — [Delete] button on installed entries calls `MapDownloadManager.deleteMap()`, entry returns to available state (spec: `map-download-ui`)
- [x] 4.8 Handle download completion — entry transitions to installed state (✅ checkmark), active downloads section updates (spec: `map-download-ui`)
- [x] 4.9 Handle download errors — entry shows error state with message, user can retry (spec: `map-download-ui`, `map-download-infrastructure`)
- [x] 4.10 Add search field — `OutlinedTextField` with search icon filters tree by name/path (spec: `map-download-ui`)
- [x] 4.11 Add `LaunchedEffect` to refresh installed maps on screen open; create synthetic entries when available list empty (spec: `map-download-ui`)
- [x] 4.12 Add throttled logging to ViewModel for download debugging (spec: `map-download-ui`)
- [x] 4.13 Verify full flow: browse available maps → search → download → see progress → complete → see installed → delete → available again

## 5. Build & Verify

- [x] 5.1 Run `./gradlew :app:assembleDebug` and verify no compilation errors
- [x] 5.2 Verify existing tests still pass (`./gradlew test`)
- [x] 5.3 Verify ProGuard/R8 rules don't strip JNI classes (add keep rules if needed)
