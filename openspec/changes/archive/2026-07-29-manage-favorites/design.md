## Context

NaviVeylin currently has search → select → map center + marker, but no way to persist locations. The JNI bridge (`OSMScoutClient`) already exposes full CRUD for favorite groups and favorites via `addGroup()`, `deleteGroup()`, `addFavorite()`, `deleteFavorite()`, `renameFavorite()`, `getFavoriteGroups()`, `loadFavoriteLocations()`, and `saveFavoriteLocations()`. The Cairo renderer accepts `favoriteLats`/`favoriteLons` arrays via `renderWithRouteAndPois()`.

See proposal.md for motivation and specs/ for detailed requirements.

## Goals / Non-Goals

**Goals:**
- Kotlin repository wrapping JNI CRUD with reactive state (StateFlow)
- Details sheet after search result selection with "Add to Favorites" flow
- Full-screen favorites management sheet with group + fav CRUD
- Favorite markers rendered on map via existing Cairo pipeline
- Persistence via existing JNI JSON save/load

**Non-Goals:**
- Room database for favorites (deferred — JNI JSON persistence is sufficient for now)
- Android Auto favorites integration (deferred)
- Favorites as route start/destination (separate change)
- Cloud sync or backup

## Decisions

### Decision: Kotlin repository wrapping JNI instead of Room
- **Choice**: `FavoriteRepository` class wrapping `OSMScoutClient` JNI methods, exposing `StateFlow<Map<String, List<FavoriteLocation>>>`
- **Rationale**: JNI already handles persistence (JSON file via C++). Adding Room would duplicate persistence logic and require syncing two data stores. The JNI layer is thread-safe for reads and serializes writes.
- **Alternatives considered**: Room DAO + JNI sync — adds complexity without benefit for current scope.

### Decision: Full-screen sheet instead of navigation destination
- **Choice**: Full-screen Compose sheet (covering entire screen) opened from map screen, not a separate navigation route
- **Rationale**: Favorites management is tightly coupled to map context (current location, visible markers). A sheet keeps the map alive in the background and avoids navigation graph changes.
- **Alternatives considered**: Navigation route to separate screen — loses map context, requires state serialization.

### Decision: Details sheet as a Modal Bottom Sheet
- **Choice**: `ModalBottomSheet` (M3) for the location details after search, with "Add to Favorites" button
- **Rationale**: Consistent with existing `SearchPanel` pattern. Bottom sheet is dismissable, shows location info, and provides the fav action without full-screen commitment.
- **Alternatives considered**: Full-screen sheet — too heavy for a single action. Dialog — doesn't fit M3 patterns.

### Decision: Group picker as inline dropdown in details sheet
- **Choice**: When user taps "Add to Favorites", show an inline dropdown of existing groups + "New group" option within the details sheet
- **Rationale**: Avoids opening another dialog/sheet for group selection. Keeps the flow contained.
- **Alternatives considered**: Separate dialog — adds friction. Auto-add to default group — removes user choice.

### Decision: Favorite markers passed via existing `renderWithRouteAndPois()`
- **Choice**: Extract lat/lon arrays from `FavoriteRepository` state and pass to the existing JNI render method
- **Rationale**: No native changes needed. The `_favorite` type and stylesheet already exist in libosmscout.
- **Alternatives considered**: Custom overlay rendering — would bypass the Cairo pipeline and require new rendering code.

## Risks / Trade-offs

- **Risk**: JSON file persistence may cause data loss if app crashes during write → **Mitigation**: JNI layer serializes writes; save is called after each successful operation, not debounced
- **Risk**: Large number of favorites could slow down map render → **Mitigation**: `_favorite` markers are only rendered at detail zoom levels (existing stylesheet behavior)
- **Risk**: JNI calls on main thread could cause ANR → **Mitigation**: Repository methods use `withContext(Dispatchers.Default)` for JNI calls
- **Trade-off**: No Room means no reactive queries or migrations — acceptable for current scope, Room can be added later if needed
