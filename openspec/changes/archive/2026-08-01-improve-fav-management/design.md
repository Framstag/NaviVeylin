## Context

Favorites sheet (`FavoritesSheet.kt`) currently renders groups as a flat `LazyColumn` with `GroupSection` rows. Each row has add/delete icon buttons. Favorites within a group are listed inline below the group header. There is no search, no group rename, and no grid layout.

State lives in `FavoritesViewModel` with `FavoritesUiState(groups: Map<String, List<FavoriteLocation>>)`. Persistence goes through `FavoriteRepository` → JNI `OSMScoutClient` → C++ `FavoriteLocationService` → JSON file.

The C++ `FavoriteLocationService` has no `RenameGroup` method — it must be added.

See proposal.md for motivation, specs/ for requirements.

## Goals / Non-Goals

**Goals:**
- Replace flat group list with a scrollable grid of group cards
- Add dropdown menu on each card (rename, delete)
- Add group detail view (back-navigable) showing that group's favorites
- Add search bar filtering favorites by name across all groups
- Add group rename end-to-end (C++ → JNI → Kotlin → UI)
- Keep all existing CRUD operations working

**Non-Goals:**
- No changes to the JSON persistence format
- No changes to the C++ data model (`FavLocationGroup`, `FavLocation`)
- No Android Auto integration
- No drag-and-drop reordering of groups or favorites

## Decisions

### 1. UI state: two modes (grid vs detail) via enum flag
**Decision:** Add a `selectedGroup: String?` field to `FavoritesUiState`. When null, show the group grid. When set, show that group's favorites with a back button.
**Rationale:** Simplest approach — no navigation graph changes, no separate composable file. The sheet already uses conditional composition for dialogs.
**Alternative considered:** Separate composable + Jetpack Navigation — overkill for a sheet that lives on top of the map.

### 2. Grid layout: `LazyVerticalGrid` with `GridCells.Adaptive`
**Decision:** Use `LazyVerticalGrid(columns = GridCells.Adaptive(minSize = 140.dp))` for the group grid.
**Rationale:** Adaptive grid auto-adjusts columns for phone, foldable, tablet. No hardcoded column counts. Material 3 `Card` composable for each group block.
**Alternative considered:** `FlowRow` — no lazy loading, worse scroll perf with many groups.

### 3. Group card menu: `DropdownMenu` triggered by `IconButton`
**Decision:** Each card has a trailing `IconButton(Icons.Default.MoreVert)` that opens a `DropdownMenu` with "Rename" and "Delete" `DropdownMenuItem`s.
**Rationale:** Standard Material 3 pattern. Matches existing dialog patterns for delete confirmation.
**Alternative considered:** Long-press context menu — less discoverable on mobile.

### 4. Search: client-side filter on `FavoritesUiState`
**Decision:** Add `searchQuery: String` to `FavoritesUiState`. ViewModel filters the groups map reactively. When search is active, show matching favorites grouped by their parent group name.
**Rationale:** All data already in memory. No need for native search. Filter is instant.
**Alternative considered:** JNI-side search — adds complexity with no benefit for in-memory data.

### 5. Group rename: new C++ method + JNI bridge
**Decision:** Add `RenameGroup(const string& oldName, const string& newName)` to `FavoriteLocationService`, expose via JNI as `native boolean renameGroup(String oldName, String newName)`.
**Rationale:** Follows exact same pattern as existing `DeleteGroup`/`AddGroup`. Minimal new code.
**Alternative considered:** Delete + recreate group — race condition, loses attributes, ugly.

### 6. Group detail view: inline composable, not separate sheet
**Decision:** When `selectedGroup` is set, the sheet body switches to a `LazyColumn` of that group's favorites with a back `IconButton` in the top bar.
**Rationale:** Reuses existing `FavoriteItem` composable. No new sheet, no navigation. Back button replaces the grid.

## Risks / Trade-offs

- **Risk: Grid looks bad with 1 group** → Mitigation: Single card centered or full-width. Acceptable — user can add more groups.
- **Risk: Search + grid mode interaction** → Mitigation: Search filters favorites across all groups. When search is active, show results grouped by group name instead of the grid. When search is cleared, return to grid.
- **Risk: C++ `RenameGroup` not thread-safe** → Mitigation: Follow existing pattern using `std::shared_mutex` (same as all other methods in `FavoriteLocationService`).
- **Trade-off: Client-side search** means no fuzzy/typo-tolerant matching. Acceptable for MVP — names are short and user-typed.
