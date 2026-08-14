# improved-fav-mgmt Proposal

## Why

Favorites are functional but lack visual organization and quick-access features. Users cannot color-code groups or mark important favorites for instant access, making the favorites sheet cumbersome with many entries.

## What Changes

- **Group color assignment**: Add "Set Color" option to group card menu. Color stored in group's `attributes` map. Group card rendered with tinted background/shading.
- **Favorite starring**: Add star toggle on each favorite item. Star state stored in favorite's `attributes` map. Star icon shown when starred.
- **Starred chip bar**: Add horizontal scrollable chip row at top of favorites sheet showing all starred favorites. Chip click navigates to that favorite's group detail and scrolls to it.
- **Modified group card rendering**: Group card background shaded with assigned color when present.
- **Modified favorite item rendering**: Star icon button on each favorite row.

## Capabilities

### New Capabilities

- `fav-group-color` — Assign a color to a favorite group via the group card menu. Color persisted in `FavoriteLocationGroup.attributes["color"]`. Group card rendered with a tinted background/shading effect.
- `fav-star` — Star/unstar individual favorites. State persisted in `FavoriteLocation.attributes["starred"]`. Star icon shown on starred favorites.
- `fav-starred-chip-bar` — Horizontal scrollable chip list at top of favorites sheet showing all starred favorites across all groups. Tapping a chip navigates to that favorite's group detail view.

### Modified Capabilities

- `fav-management-ui` — Add "Set Color" menu item to group card menu. Add star toggle button to favorite items. Add starred chip bar above the group grid. Wire chip click to group navigation.
- `group-grid-display` — Group card background tinted with assigned color when present. Shading effect applied.

## Impact

- **`FavoriteRepository.kt`** — Add `setGroupColor(groupName, color)` and `setFavoriteStarred(groupName, favName, starred)` methods. Both use existing `attributes` maps on JNI objects + `persist()`.
- **`FavoritesViewModel.kt`** — Add `setGroupColor()`, `toggleStar()` actions. Expose `starredFavorites` derived state for chip bar.
- **`FavoritesSheet.kt`** — Add color picker dialog, star icon on `FavoriteItem`, chip bar composable at top of main view. Wire chip click to `selectGroup()` + scroll.
- **`FavoriteLocationGroup.java`** / **`FavoriteLocation.java`** — No structural changes needed. `attributes` map already exists.
- No JNI/native changes required. All state in existing `attributes` maps.
