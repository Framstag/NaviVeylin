# Design

## Context

See `proposal.md` — Why. Design-relevant state:

- `FavoriteRepository` (`app/src/main/java/com/naviveylin/data/FavoriteRepository.kt`) is the single writer for favorites: every write is `writeMutex.withLock { …Locked }` → one JNI call → `refreshState()` → `persist()`. `persist()` writes **the native store as it stands** (`client.favoriteGroups` → `saveFavoriteLocations`), so the native store is authoritative and a Kotlin-side rewrite of the grouping is not a supported path. `AGENTS.md` forbids calling the native favourite methods outside the store.
- The native capability already exists and is pinned: `FavoriteLocationService::MoveFavoriteToGroup` (`libosmscout-client/src/osmscoutclient/FavoriteLocationService.cpp:634`), `FavoriteStore::MoveFavoriteToGroup` (`libosmscout-client/include/osmscoutclient/FavoriteStore.h:164`), JNI `Java_..._OSMScoutClient_moveFavoriteToGroup` (`libosmscout-client-java/src/OSMScoutClient.cpp:7260`), added by submodule commit `530ac8768`. Submodule is clean at `d62528e22` (`naviveylin-local`).
- `osmscout-client-java/build.gradle.kts` compiles the submodule Java sources **except** five files, and the local `osmscout-client-java/src/main/java/com/framstag/libosmscout/client/OSMScoutClient.java` shadows the submodule's. The local file declares the favorites API up to `getGroupColor` and is one API family behind the submodule (`moveFavoriteToGroup`, `moveGroup`, `moveStarredFavorite`, `getStarredFavorites`, `getFavoriteFileFormatVersion`, `isFavoriteFileFormatSupported` are missing).
- The favorites sheet is one sub-screen stack inside one full-screen sheet (`FavoritesSheet.kt`, 1002 lines): main group grid with a starred chip bar, group detail with an "Add favorite" leading row and a reorderable favorite list (`sh.calvin.reorderable`). The favorite row today: star `IconButton`, rename `IconButton`, delete `IconButton`, drag handle.
- `guidelines/UI.md` §1 already documents the "favorite reordering is phone-only" deviation (interaction phone-only, data shared). The same argument covers a cross-group move.

## Goals / Non-Goals

**Goals:**

- Make the move a single native store operation, so it cannot leave the favorite in a half-moved state and cannot lose its star, attributes or coordinates.
- Keep the repository's single-writer contract: the move is one more `*Locked` writer, not a new path.
- Reachable in two taps from the group detail list, without disturbing the row controls the existing `fav-management-ui` spec requires.

**Non-Goals:**

- No C++/JNI change, no submodule commit, no native rebuild, no file-format change.
- No car-side move flow, no `AutoFavoritesProvider` change (`:auto`, `:core` untouched).
- Not picking up the other bridge natives the override is missing — recorded in `TODO.md` instead.
- No position picker in the destination dialog: the default is the end of the destination group, and the destination list already has drag-to-reorder for a follow-up adjustment.
- No bulk move, no multi-select, no move of a whole group.

## Decisions

### D1 — Move implemented by declaring the existing JNI method in the bridge override

Add to `osmscout-client-java/src/main/java/com/framstag/libosmscout/client/OSMScoutClient.java`:

```java
public native boolean moveFavoriteToGroup(String groupName, String favName,
                                          String targetGroupName, int newIndex);
```

plus KDoc stating the contract (index over the destination list, clamped; collision refused with both groups untouched; `src == dst` is a successful no-op). No C++ work: the symbol is already in `libosmscout_client_java.so` at the pinned submodule commit, and the override file is the one the `:osmscout-client-java` build compiles.

*Alternatives:*
- **Submodule patch** — pointless: the native API and the submodule's own Java declaration exist. Editing the submodule for an app-side gap would also break the "patch in one place, never both" rule.
- **Kotlin-side move built from `deleteFavorite` + `addFavorite`** — rejected: two writes instead of one, a window in which the favorite exists in neither group (a crash there loses it), and the star plus attributes are gone because `addFavorite` only takes name + coordinates.
- **Kotlin-side rewrite of the grouping persisted through `saveFavoriteLocations`** — rejected: `persist()` writes the native store, so a Java-built grouping would have to be pushed into the native store separately (`loadFavoriteLocations`) and would race the store's own mutex; it duplicates logic the store already has, correctly.

### D2 — The repository owns the move, including creating a missing destination group

New `open suspend fun moveFavoriteToGroup(sourceGroup, favName, targetGroup, newIndex): Boolean` plus a private `…Locked` helper, matching the existing writers. Inside the single critical section: if the destination group is not in `_favorites.value.keys`, create it with `addGroupLocked` (the existing non-reentrant-safe helper), then call the JNI move, then `refreshState()` + `persist()`.

*Alternatives:*
- **ViewModel creates the group, then calls the move** — two lock acquisitions, so another write can interleave between "group exists but empty" and "favorite moved into it", and the UI can briefly show an empty group. Rejected.
- **ViewModel calls `OSMScoutClient` directly** — forbidden by `AGENTS.md` / the store contract.
- **No group creation from the dialog** (user must pre-create the group in the grid) — more navigation for the common case; the `addFavorite` flow already auto-creates a group, so this would be inconsistent.

Consequence to keep in mind: a move that creates the destination group performs the same two persists as `addFavorite` on a missing group (group creation, then the move) — the spec's "persists exactly once" applies to the move itself. Because the group is created before the native move runs, a move that is then refused (unknown source group or favorite) leaves that new group behind as an empty group; the sheet cannot reach that state, since its destination either exists or was chosen through the explicit "new group" option while the favorite is on screen.

### D3 — Destination position defaults to the end of the destination group

The dialog performs the move with `newIndex = destination group's current size`; the native method clamps, so an oversized index is safe.

*Alternatives:*
- **Top of the destination group (index 0)** — arbitrary, and buries existing favorites.
- **Position picker in the dialog** — a second control and a second state for a decision the user can make better in context; the destination group's list already supports drag-to-reorder (`fav-management-ui`), so a follow-up adjustment costs one gesture.
- **"Insert near the source neighbors"** — meaningless across groups; the source neighbors are not in the destination.

### D4 — A name already used in the destination refuses the move, and the refusal is reported

The native method checks the collision **before** removing anything, so both groups stay as they were; the repository returns `false` and persists nothing. The ViewModel reports it with a message naming the destination group and the favorite.

*Alternatives:*
- **Auto-rename to `"<name> (2)"`** — invents a name the user did not choose and diverges from the source name; the sheet's group-rename and favorite-rename paths both refuse duplicates, so this would be the only silent rename in the feature.
- **Inline rename prompt inside the move dialog** — two writes from one dialog and a second failure mode (the new name can collide too) for a case the user can resolve with the existing rename action.

### D5 — The move lives in a new row overflow menu; star, rename and delete keep their places

The favorite row gains a compact `MoreVert` menu carrying only **"Move to group"**, next to the existing star / rename / delete buttons and the drag handle. The existing `fav-management-ui` requirement ("a star icon button ... alongside rename and delete buttons") stays true, so no requirement has to be modified for this change.

*Alternatives:*
- **A sixth control as a plain icon button** — a row with star, rename, delete, move, drag handle is too crowded on a phone; the overflow menu is the established pattern on the group card (Set Color / Rename / Delete).
- **Consolidate rename + delete + move into one overflow menu, keeping star and drag handle** — cleaner row, but it MODIFIES the existing row-requirement and rewrites the existing compose tests for the row's actions. Worth doing on its own, not mixed into this change; noted as a follow-up.
- **Drag the favorite onto another group header** — the two groups live on different sub-screens and `sh.calvin.reorderable` is per-list with no cross-list drop target; a "move mode" with an inline group chooser is more state for the same result.

### D6 — One in-flight guard for both move kinds

`FavoritesViewModel.reorderInFlight` becomes a single guard covering a reorder commit and a cross-group move (renamed to say what it guards). Both are whole-list mutations, and a drag commit racing a cross-group move would otherwise show order flicker; the repository's write lock already prevents a lost write, so the guard is about the visible sequence, not about data integrity.

*Alternatives:*
- **A second, separate flag** — two flags must be kept in sync; forgetting one pair means the interleaving the guard exists for is back.
- **No guard** — the original defect the guard was added for.

### D7 — Phone-only, documented as an existing parity deviation

No `:auto` or `:core` change. `guidelines/UI.md` §1 ("Why favorite reordering is phone-only") gains the cross-group case: the *data* is shared (the car list renders the new grouping), the *interaction* cannot be (Car App Library templates expose no drag and no destination-selection step, and the screen is driver-facing).

*Alternatives:*
- **Car provider operation without a car UI** — an API nobody calls; dead code that the archive gate would have to justify.
- **Full car flow** — a picker template, host-mutation guards, car tests; explicitly out of scope per the proposal.

### Threading and lifecycle

- No new threads, no new lifecycle owners. The repository write runs on `defaultDispatcher` (`Dispatchers.Default`) via `withContext`, exactly like the other writers; the ViewModel launches on `viewModelScope` (main) and only touches `_uiState`.
- The destination dialog is Compose-remembered local state in `FavoritesSheet`, consistent with the sheet's other dialogs (delete group / rename group / delete favorite / rename favorite) and with the "sheet resets to the main screen on open" rule — a move in flight when the sheet closes cannot resurrect a stale dialog.

## Risks / Trade-offs

- **[A moved starred favorite changes its group in the starred chip bar]** → Intended (the star belongs to the favorite, the chip bar groups by group); stated in the `fav-ordering` delta and covered by an on-device check.
- **[Two different `newIndex` conventions in one file]** (`moveFavorite`: index over the list *after* removal; `moveFavoriteToGroup`: index over the destination list, untouched) → spelled out in the KDoc of both the bridge declaration and the repository function, plus a test that appends with an index past the end.
- **[A refused move reads as a bug]** → distinct, actionable message ("Group 'X' already has a favorite named 'Y'") instead of a generic failure.
- **[Row crowding from a fourth control]** → compact overflow icon on the trailing edge; the consolidate-the-row alternative is recorded above.
- **[The bridge override is one API family behind the submodule]** (the gap this change works around) → `TODO.md` entry naming the missing natives; a `buildSrc` gate comparing the override's `native` declarations against the submodule's Java source would prevent a repeat and is a candidate for its own change.
- **[Group creation + move are not one native transaction]** → both writes are committed under one repository lock and the pair is ordered (create, then move), so the worst observable state after a failure is an empty new group, never a lost favorite. A refused move into a just-created destination leaves that empty group behind; unreachable from the sheet, whose destination always already holds nothing of that name.

## Migration Plan

- No data migration by this change: a move only re-arranges existing entries. The file format itself is
  the native store's: the first write after **any** operation rewrites the favorites file in the
  submodule's versioned format (`formatVersion: 1`, `starredPosition`, groups as an ordered list —
  observed on device during verification), and the store still reads pre-version files.
- Rollback: revert the commits. The arrangement of the entries is what a move changes; the store reads
  both the versioned and the pre-version file. A rollback that also downgrades the libosmscout submodule
  must expect the upgraded file to be read through the store's pre-version compatibility path.
- Ship order: bridge declaration and repository first (individually testable), then the ViewModel, then
  the sheet. No build-time or manifest change.

## Open Questions

- Whether the destination dialog should later offer a position (top / end) or rely purely on the destination group's existing drag-to-reorder. Deferrable: the specs require no position control, and adding one changes neither the repository contract nor the native call.
