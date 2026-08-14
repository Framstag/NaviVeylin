# Proposal: search-fixes

## Why

Search has five defects: typing any query can hard-crash the app (SIGABRT in native code, so no Kotlin try/catch can help), the route-panel swap button is mispositioned, the route search results always show "Current Location"/"Select Favorite" even while typing, selecting a search result on the map is immediately undone by follow mode, and adding a favorite to a brand-new group fails silently (neither group nor favorite is created).

## What Changes

- **Fix native crash during search**: `searchLocations` reads each result's object (node/area/way) to extract coordinates and name. When a map's text search index (`text*.dat`) is inconsistent with its data files (stale offsets after a partial/mixed-version download, or leftover files), `Area::Read`/`Way::Read`/`Node::Read` hit a type id that is out of range for the database's `TypeConfig` and `assert(id<=areaTypes.size())` aborts the whole process. Patch libosmscout so an out-of-range type id makes the read fail gracefully (return error) instead of asserting, and have the JNI `searchLocations` skip entries whose object cannot be resolved instead of returning garbage coordinates. Result: search degrades gracefully (missing/invalid entries dropped) instead of crashing.
- **Move route-panel swap button**: the swap (SwapVert) button currently sits centered between the start and destination fields. It SHALL be placed at the right edge of the panel, still vertically centered between the two fields.
- **Gate route search convenience entries on empty query**: "Current Location" and "Select Favorite" entries SHALL only appear while the query is empty. Once the user types, only real search results SHALL be listed. Clearing the field SHALL immediately restore both entries.
- **Deactivate follow mode when showing a search result**: selecting a search result SHALL turn off follow-location before centering, so GPS does not yank the viewport back and hide the result.
- **Fix "Add to Favorites" with new group**: the details-sheet group picker lets the user choose "+ New group...", but `FavoriteLocationService::AddFavorite` returns false when the group does not exist and no group is ever created. Creating a favorite in a non-existent group SHALL create the group first, then add the favorite; a duplicate group name during this flow SHALL surface an error message.

## Capabilities

### New Capabilities

- *(none)*

### Modified Capabilities

- `location-search`: search must never crash the app on inconsistent/stale search-index data — unresolvable result entries SHALL be skipped; selecting a search result SHALL deactivate follow-location mode so the selected location stays visible.
- `route-panel-ui`: the swap button SHALL be positioned at the right edge vertically centered between the start and destination fields; "Current Location" and "Select Favorite" entries SHALL be shown only while the query field is empty.
- `fav-detail-save`: adding a favorite to a new group SHALL create the group and then save the favorite; a duplicate new-group name SHALL show an error instead of failing silently.

## Impact

- `app/src/main/cpp/libosmscout/libosmscout/src/osmscout/Area.cpp`, `Way.cpp`, `Node.cpp` (submodule commit `bebd534`) — type-id bounds check in `*::Read`, fail gracefully instead of asserting via `TypeConfig::GetAreaTypeInfo`.
- `app/src/main/cpp/libosmscout/libosmscout-client-java/src/OSMScoutClient.cpp` — `searchLocations` (L2140): skip entries whose object ref cannot be resolved; propagate read failure instead of dereferencing.
- `app/src/main/java/com/naviveylin/ui/map/MapCanvasViewModel.kt` — `onSearchResultSelected` (L858): set `followMode = false` before `updateCenter`; `addSelectedToFavorites` (L1041): create missing group before adding favorite, surface failure snackbar.
- `app/src/main/java/com/naviveylin/ui/route/RoutePanel.kt` — swap button container alignment (L114-128); `RouteSearchResults` (L343): gate convenience entries on `query.isEmpty()`.
- `app/src/main/java/com/naviveylin/data/FavoriteRepository.kt` — optional `ensureGroup` helper for auto-create on add.
- `app/src/main/java/com/naviveylin/ui/map/LocationDetailsSheet.kt` — new-group duplicate error path.
- Risk: submodule patch must not regress map rendering reads; bounds check only affects reads of invalid ids.
