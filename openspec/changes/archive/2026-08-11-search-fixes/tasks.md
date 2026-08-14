# Tasks: search-fixes

## 1. Native crash fix (libosmscout submodule)

- [x] 1.1 `Area::Read` (app/src/main/cpp/libosmscout/libosmscout/src/osmscout/Area.cpp L173): bounds-check the raw type id read at L185-187 — if `0` or greater than the type-config area type count, `throw IOException` instead of calling `typeConfig.GetAreaTypeInfo` (which asserts)
- [x] 1.2 `Way::Read` (app/src/main/cpp/libosmscout/libosmscout/src/osmscout/Way.cpp L74): same bounds check + `throw IOException` instead of `GetWayTypeInfo`
- [x] 1.3 `Node::Read` (app/src/main/cpp/libosmscout/libosmscout/src/osmscout/Node.cpp L44): same bounds check + `throw IOException` instead of `GetNodeTypeInfo`
- [x] 1.4 Verify `DataFile<N>::ReadData` catches `IOException` and returns false (it does, DataFile.h L174-183) so `GetAreaByOffset`/`GetWayByOffset`/`GetNodeByOffset` report failure — confirm by reading DataFile.h; no signature changes needed
- [x] 1.5 Build: `./gradlew :app:assembleDebug -Pandroid.injected.build.abi=arm64-v8a` compiles with the submodule patch

## 2. JNI searchLocations skips unresolvable entries

- [x] 2.1 In `Java_com_framstag_libosmscout_client_OSMScoutClient_searchLocations` (OSMScoutClient.cpp L2140), track a per-entry `resolved` flag set true only when the object-ref lookup (node/area/way) succeeds
- [x] 2.2 Skip entries with no valid `ObjectFileRef` or failed lookup when building the Java `LocationEntry[]` (drop them from `results` before array construction, or build array from resolved entries only)
- [x] 2.3 Build JNI: `./gradlew :app:assembleDebug -Pandroid.injected.build.abi=arm64-v8a` compiles
- [x] 2.4 Device check: search that previously crashed (e.g. query 'Am') completes without SIGABRT; results with valid data still resolve lat/lon/name — *skipped by user (ignore test)*

## 3. Follow mode deactivation on search result selection

- [x] 3.1 `MapCanvasViewModel.onSearchResultSelected` (L858): set `_uiState.followMode = false` before `updateCenter(entry.lat, entry.lon)`; persist via `settingsStorage` (same pattern as `onToggleFollowMode` L1072)
- [x] 3.2 Unit test in `MapCanvasViewModelFollowModeTest.kt`: follow mode on → `onSearchResultSelected(entry)` → `uiState.followMode` false + viewport centered on entry
- [x] 3.3 Device check: follow mode active, select search result → map stays centered on result, no GPS re-centering

## 4. Route panel layout and convenience entries

- [x] 4.1 Swap button (RoutePanel.kt): restructured — fields in a weighted column, swap button to the right of the start/destination fields, vertically centered between them
- [x] 4.2 `RouteSearchResults` (RoutePanel.kt L343): wrap "Current Location" row + divider and "Select Favorite" row + divider in `if (query.isEmpty())` so they appear only with empty query
- [x] 4.4 `RouteSearchField` (RoutePanel.kt): add `onFocusChanged` so tapping an empty field opens the results popup immediately (previously only the first keystroke activated the field); blur closes the popup and restores the field label
- [x] 4.5 Main map `SearchPanel` (SearchPanel.kt + MapCanvasScreen.kt + MapCanvasViewModel.kt): show "Current Location" (if GPS) and "Select Favorite" entries when the query is empty; selecting them centers on GPS / opens the favorites sheet; `selectCurrentLocation()` helper added
- [x] 4.3 Device check: swap button right-aligned; empty query shows both convenience entries; typing hides them; clearing restores them

## 5. Favorite group auto-create on add

- [x] 5.1 `FavoriteRepository.addFavorite` (FavoriteRepository.kt L100): if `groupName` not in current group set, call `addGroup(groupName)` first; if `addGroup` fails (duplicate name), return false; then add favorite and persist
- [x] 5.2 `MapCanvasViewModel.addSelectedToFavorites` (L1041): distinct failure snackbar for duplicate group name ("Group already exists") vs generic failure
- [x] 5.3 Extend `FakeOSMScoutClient` (or add favorites fake) with in-memory group/favorite CRUD so `FavoriteRepository` is testable without native calls
- [x] 5.4 Unit test: `addFavorite("NewGroup", ...)` with no prior groups → group created + favorite added; duplicate group name → returns false, nothing created
- [x] 5.5 Device check: details sheet → "Add to Favorites" → "+ New group" → name → group appears in favorites sheet with the favorite; entering an existing name shows error

## 6. Verification

- [x] 6.1 `./gradlew test` — all unit tests pass
- [x] 6.2 `openspec validate --changes search-fixes` passes
- [x] 6.3 Manual regression: search with results, route panel pick, favorites CRUD, follow mode toggle — no crashes
