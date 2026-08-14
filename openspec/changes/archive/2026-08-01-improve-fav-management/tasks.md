## 1. C++: Add RenameGroup to FavoriteLocationService

- [x] 1.1 Add `bool RenameGroup(const std::string& oldName, const std::string& newName)` declaration to `FavoriteLocationService.h`
- [x] 1.2 Implement `RenameGroup` in `FavoriteLocationService.cpp` — find group by oldName, rename, return false if oldName not found or newName already exists
- [x] 1.3 Build and verify C++ tests compile (`FavoriteLocationServiceTest.cpp`)

## 2. JNI: Expose renameGroup

- [x] 2.1 Add `public native boolean renameGroup(String oldName, String newName)` to `OSMScoutClient.java`
- [x] 2.2 Add `Java_com_framstag_libosmscout_client_OSMScoutClient_renameGroup` JNI wrapper in `OSMScoutClient.cpp` — call `service->RenameGroup()`
- [x] 2.3 Build native libs and verify no link errors

## 3. Kotlin: FavoriteRepository

- [x] 3.1 Add `suspend fun renameGroup(oldName: String, newName: String): Boolean` to `FavoriteRepository.kt` — call `client.renameGroup()`, refresh state, persist

## 4. Kotlin: FavoritesViewModel

- [x] 4.1 Add `selectedGroup: String?` field to `FavoritesUiState` data class
- [x] 4.2 Add `searchQuery: String` field to `FavoritesUiState` data class
- [x] 4.3 Add `fun selectGroup(name: String?)` to ViewModel — sets selectedGroup
- [x] 4.4 Add `fun onSearchQueryChange(query: String)` to ViewModel — updates searchQuery
- [x] 4.5 Add `fun renameGroup(oldName: String, newName: String)` to ViewModel — calls repo, shows snackbar

## 5. Kotlin: FavoritesSheet UI — Group Grid

- [x] 5.1 Replace `LazyColumn` group list with `LazyVerticalGrid(columns = GridCells.Adaptive(140.dp))`
- [x] 5.2 Create `GroupCard` composable — `Card` with group name, fav count, `MoreVert` icon button
- [x] 5.3 Add `DropdownMenu` to `GroupCard` with "Rename" and "Delete" items
- [x] 5.4 Wire delete action to existing confirmation dialog
- [x] 5.5 Wire rename action to new `TextFieldDialog` pre-filled with current group name
- [x] 5.6 Wire card click to `viewModel.selectGroup(groupName)`

## 6. Kotlin: FavoritesSheet UI — Group Detail View

- [x] 6.1 When `selectedGroup != null`, render `LazyColumn` of that group's favorites
- [x] 6.2 Add back `IconButton` in top bar to call `viewModel.selectGroup(null)`
- [x] 6.3 Reuse existing `FavoriteItem` composable for each favorite

## 7. Kotlin: FavoritesSheet UI — Search

- [x] 7.1 Add `OutlinedTextField` search bar at top of sheet with placeholder "Search favorites"
- [x] 7.2 Wire `onValueChange` to `viewModel.onSearchQueryChange()`
- [x] 7.3 When `searchQuery` is non-empty, filter favorites across all groups and show results grouped by group name
- [x] 7.4 Show "No favorites match your search" when no results

## 8. Build & Verify

- [x] 8.1 Build debug APK (`./gradlew :app:assembleDebug -Pandroid.injected.build.abi=arm64-v8a`)
- [x] 8.2 Run existing unit tests (`./gradlew test`)
- [x] 8.3 Manual smoke test: add group, rename group, delete group, add fav, search fav, navigate back
