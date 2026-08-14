## 1. Repository Layer

- [x] 1.1 Add `setGroupColor(groupName, colorHex)` to `FavoriteRepository` — writes `attributes["color"]` on the JNI group object, calls `persist()`
- [x] 1.2 Add `getGroupColor(groupName)` helper to `FavoriteRepository` — reads `attributes["color"]` from cached state
- [x] 1.3 Add `setFavoriteStarred(groupName, favName, starred)` to `FavoriteRepository` — writes/removes `attributes["starred"]`, calls `persist()`
- [x] 1.4 Add `isFavoriteStarred(groupName, favName)` helper to `FavoriteRepository`
- [x] 1.5 Add `getAllStarredFavorites()` to `FavoriteRepository` — returns list of `Pair<String, FavoriteLocation>` across all groups where `attributes["starred"] == "true"`

## 2. ViewModel Layer

- [x] 2.1 Add `setGroupColor(groupName, colorHex)` action to `FavoritesViewModel`
- [x] 2.2 Add `toggleStar(groupName, favName)` action to `FavoritesViewModel`
- [x] 2.3 Add `starredFavorites: List<Pair<String, FavoriteLocation>>` derived state to `FavoritesUiState`
- [x] 2.4 Wire `starredFavorites` to update when favorites flow emits

## 3. Color Picker Dialog

- [x] 3.1 Create `ColorPickerDialog` composable with predefined Material color swatches (8-12 colors)
- [x] 3.2 Dialog returns selected color hex string via `onColorSelected(String)`
- [x] 3.3 Wire dialog to group card menu "Set Color" action in `FavoritesSheet`

## 4. Group Card Color Rendering

- [x] 4.1 Modify `GroupCard` composable to accept optional `colorHex` parameter
- [x] 4.2 Apply tinted background/shading effect using `Card(colors = CardDefaults.cardColors(containerColor = ...))` when color present
- [x] 4.3 Pass group color from state to `GroupCard` in the grid

## 5. Star Toggle on Favorite Items

- [x] 5.1 Add star `IconButton` to `FavoriteItem` composable (filled star when starred, outline when not)
- [x] 5.2 Wire star button click to `viewModel.toggleStar()`
- [x] 5.3 Pass `isStarred` state to `FavoriteItem`

## 6. Starred Chip Bar

- [x] 6.1 Create `StarredChipBar` composable — `LazyRow` of chips from `starredFavorites` state
- [x] 6.2 Each chip shows favorite name (and optionally group name as subtitle)
- [x] 6.3 Chip click calls `viewModel.selectGroup(groupName)` and triggers scroll to target favorite
- [x] 6.4 Place chip bar at top of main favorites view, above search bar
- [x] 6.5 Hide chip bar when `starredFavorites` is empty

## 7. Navigation & Scroll

- [x] 7.1 Pass `LazyListState` to group detail view for programmatic scroll
- [x] 7.2 On chip click: navigate to group detail, then `animateScrollToItem()` to target favorite index
- [x] 7.3 Verify back navigation from group detail still works

## 8. Build & Test

- [x] 8.1 Verify project compiles with `./gradlew :app:assembleDebug`
- [x] 8.2 Run existing unit tests: `./gradlew test`
- [x] 8.3 Add unit tests for new `FavoriteRepository` methods
- [x] 8.4 Add unit tests for new `FavoritesViewModel` actions
