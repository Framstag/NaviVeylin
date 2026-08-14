## Why

Favorites management dialog uses a flat list for groups and favorites. It lacks group rename, does not scale beyond a few groups, and offers no way to search favorites by name. Users with many groups must scroll a long list; finding a specific favorite requires manual browsing.

## What Changes

- Replace group list with a **grid of group cards**. Each card shows group name + fav count, has a dropdown menu with rename/delete actions. Clicking a card navigates to that group's favorites list.
- Add **search bar** at top of the grid to filter favorites by name across all groups.
- Add **rename group** support end-to-end: C++ service → JNI → Repository → ViewModel → UI.
- Add **back navigation** from group detail view to group grid.
- Keep existing add/delete group, add/delete/rename favorite functionality.

## Capabilities

### New Capabilities
- `group-grid-display`: Replace flat group list with a grid of cards. Each card shows group name, fav count, and a menu (rename, delete). Click navigates to group's favorites.
- `fav-search`: Search bar on top of the grid. Filters favorites by name across all groups as user types.
- `group-rename`: Rename groups via dropdown menu on group card. Requires new C++ service method + JNI bridge.

### Modified Capabilities
- *(none — no existing specs to modify)*

## Impact

- **C++** (`libosmscout-client`): Add `RenameGroup()` to `FavoriteLocationService` (header + .cpp)
- **JNI** (`libosmscout-client-java`): Add `renameGroup` native method in `OSMScoutClient.java` + JNI impl in `OSMScoutClient.cpp`
- **Kotlin** (`app` module):
  - `FavoriteRepository.kt` — add `renameGroup()` suspend function
  - `FavoritesViewModel.kt` — add `renameGroup()`, add search query state, add selected group state
  - `FavoritesSheet.kt` — major UI rewrite: grid layout, group cards with menu, search bar, group detail view
- **No new dependencies** — all Compose APIs already available
