## 1. FavoriteRepository (fav-service)

- [x] 1.1 Create `FavoriteRepository` class wrapping `OSMScoutClient` JNI methods
- [x] 1.2 Expose `StateFlow<Map<String, List<FavoriteLocation>>>` for reactive group/fav state
- [x] 1.3 Implement `loadFavorites()` calling `client.loadFavoriteLocations()` on init
- [x] 1.4 Implement `addGroup(name)`, `deleteGroup(name)` suspend functions with state update
- [x] 1.5 Implement `addFavorite(group, name, lat, lon)`, `deleteFavorite(group, name)`, `renameFavorite(group, oldName, newName)` suspend functions with state update
- [x] 1.6 Call `client.saveFavoriteLocations()` after every successful write
- [x] 1.7 Run all JNI calls on `Dispatchers.Default` to avoid main-thread ANR
- [x] 1.8 Register `FavoriteRepository` as Hilt `@Singleton` in `AppModule

## 2. Favorite Markers on Map (fav-markers)

- [x] 2.1 Collect favorite lat/lon arrays from `FavoriteRepository` state in `MapCanvasViewModel`
- [x] 2.2 Pass `favoriteLats`/`favoriteLons` to `renderWithRouteAndPois()` when favorites exist
- [x] 2.3 Trigger re-render when `FavoriteRepository` state changes (collect as flow)
- [x] 2.4 Verify `_favorite` markers render at detail zoom and hide at low zoom (existing stylesheet behavior)

## 3. Location Details Sheet (fav-detail-save)

- [x] 3.1 Add `selectedLocation` state to `MapCanvasUiState` (already exists — extend with details-sheet visibility flag)
- [x] 3.2 Create `LocationDetailsSheet` composable: shows location label, admin region, "Add to Favorites" / "Remove from Favorites" button
- [x] 3.3 Implement group picker inline in details sheet: dropdown of existing groups + "New group" option
- [x] 3.4 Wire "Add to Favorites" to `FavoriteRepository.addFavorite()` with selected group
- [x] 3.5 Wire "Remove from Favorites" to `FavoriteRepository.deleteFavorite()` for already-faved locations
- [x] 3.6 Show Snackbar confirmation after add/remove
- [x] 3.7 Detect if selected location is already a favorite (compare coords across all groups)

## 4. Full-Screen Favorites Management Sheet (fav-management-ui)

- [x] 4.1 Create `FavoritesSheet` composable: full-screen sheet with close button
- [x] 4.2 Display groups as expandable sections with name + fav count
- [x] 4.3 Implement "Add Group" with name prompt dialog
- [x] 4.4 Implement "Delete Group" with confirmation dialog
- [x] 4.5 Implement "Add Favorite" within group with name + coordinates prompt
- [x] 4.6 Implement "Delete Favorite" with confirmation dialog
- [x] 4.7 Implement "Rename Favorite" with name prompt dialog
- [x] 4.8 Implement "Add Current Map Location" quick action (uses map center coords)
- [x] 4.9 Create `FavoritesViewModel` with `FavoriteRepository` injected, exposing state for the sheet

## 5. Integration & Wiring

- [x] 5.1 Add "Favorites" button to map screen (top bar or menu) that opens `FavoritesSheet`
- [x] 5.2 Wire `LocationDetailsSheet` into `MapCanvasScreen` — show after search result selection
- [x] 5.3 Wire `FavoritesSheet` into `MapCanvasScreen` — show/hide via state
- [x] 5.4 Ensure map re-renders after favorites change (flow collection in ViewModel)
- [x] 5.5 Verify search → select → details sheet → add fav → marker appears flow end-to-end

## 6. Build & Verify

- [x] 6.1 Build debug APK — verify no compilation errors
- [x] 6.2 Run existing unit tests — verify no regressions
- [x] 6.3 Manual test: search location, add to favorites, verify marker appears
- [x] 6.4 Manual test: open favorites sheet, create group, add fav, rename, delete
- [x] 6.5 Manual test: close and reopen app — verify favorites persist
