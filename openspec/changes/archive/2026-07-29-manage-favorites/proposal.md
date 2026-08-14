## Why

Users need to save and organize favorite locations for quick access — marking home, work, points of interest, or frequently visited places. NaviVeylin currently has no way to persist a location after search. This change adds full favorites management: saving locations from search results, organizing them into groups, viewing them on the map, and managing them through a dedicated full-screen UI.

## What Changes

- **Details sheet after search**: When user selects a search result, the map centers on the location and a details sheet opens showing location info with an "Add to Favorites" button
- **Full-screen favorites management sheet**: Accessible from the map screen, shows groups with expandable fav lists, supports add/delete group, add/delete/rename favorites
- **Favorite markers on map**: All saved favorites render as markers via the existing Cairo pipeline (`_favorite` type)
- **Kotlin repository layer**: Wraps existing JNI CRUD methods (`addGroup`, `addFavorite`, `getFavoriteGroups`, etc.) in a clean Kotlin repository with Flow-based state
- **Persistence**: Reuses existing C++ JSON persistence via JNI (`loadFavoriteLocations`/`saveFavoriteLocations`)

## Capabilities

### New Capabilities
- `fav-service`: Kotlin repository wrapping JNI CRUD for favorite location groups and favorites, with reactive state exposure
- `fav-detail-save`: Details sheet shown after search result selection, with "Add to Favorites" button and group picker
- `fav-management-ui`: Full-screen Compose sheet for managing favorite groups and favorites (CRUD)
- `fav-markers`: Render all saved favorites as map markers via the Cairo rendering pipeline

### Modified Capabilities
- None — no existing NaviVeylin specs are modified

## Impact

- **New files**: `app/src/main/java/com/naviveylin/data/FavoriteRepository.kt`, `app/src/main/java/com/naviveylin/ui/favorites/FavoritesSheet.kt`, `app/src/main/java/com/naviveylin/ui/favorites/FavoritesViewModel.kt`, `app/src/main/java/com/naviveylin/ui/map/LocationDetailsSheet.kt`
- **Modified files**: `app/src/main/java/com/naviveylin/ui/map/MapCanvasScreen.kt` (add details sheet trigger, favorites sheet trigger, favorites button), `app/src/main/java/com/naviveylin/ui/map/MapCanvasViewModel.kt` (add details state, favorites state, fav marker rendering)
- **JNI bridge**: Reuses existing `OSMScoutClient` methods — no native changes needed
- **DI**: `AppModule.kt` may need `FavoriteRepository` binding
