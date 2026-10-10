# Proposal

## Why

A favorite's group is currently fixed at creation time: the favorites sheet can rename, delete, re-star and reorder a favorite inside its group, but a favorite saved into the wrong group (or whose group no longer describes it) can only be re-created by hand — add it to the target group, then delete the original, losing its star and its position. The native store already supports the move (`FavoriteLocationService::MoveFavoriteToGroup`, reachable through the JNI bridge), so the app is one bridge declaration and one surface away from letting the user do it in place.

## What Changes

- Declare `moveFavoriteToGroup(groupName, favName, targetGroupName, newIndex)` in the **local bridge override** `osmscout-client-java/src/main/java/com/framstag/libosmscout/client/OSMScoutClient.java`. The JNI implementation and the submodule's own Java declaration already exist and are pinned; the override file shadows the submodule file and is currently one API family behind it. No C++ change, no submodule commit, no native rebuild.
- `FavoriteRepository` gains `moveFavoriteToGroup(srcGroup, favName, dstGroup, newIndex)`: same shape as the existing `moveFavorite` — take the write lock, one JNI call, `refreshState()`, one `persist()`; return the native result unchanged.
- `FavoritesViewModel` gains a `moveFavoriteToGroup` action that reports success and the two distinguishable failures (destination group unknown, destination already holds a favorite of that name) through the existing snackbar channel.
- `FavoritesSheet` gains a **"Move to group"** action on the favorite row (overflow menu, matching the group card's Set Color/Rename/Delete menu), opening a dialog that lists the destination groups and offers "New group…" for a group that does not exist yet. The action is hidden when the store holds only one group, since there is nothing to move into.
- Default destination position is the **end** of the destination group; the moved favorite keeps its coordinates, its attributes and its star.
- **Scope is phone only.** The Android Auto / AAOS favorites screen stays a browse-only `PlaceListTemplate`: moving a favorite is a management action and the car templates cannot express it (no drag, no destination-picker step). `AutoFavoritesProvider` and `:auto` are unchanged.
- **Additive, no breaking change.** Rollback is a revert of the commits: no schema change and no new dependency. The favorites file itself is rewritten by the **native save path** on the first write after any operation, in the submodule's versioned format (`formatVersion: 1`, `starredPosition`, groups as an ordered list) — observed on device; that happens for a rename or a star toggle too, not only for a move, and the store keeps reading pre-version files. An older app build that carries the same submodule reads the upgraded file; a build with an older submodule would not, so a rollback that downgrades the submodule must expect the file to be re-read through its compatibility path.
- The change is a **local override in the bridge module**, not a submodule patch: the native side is already upstream/pinned.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `fav-service`: new requirement — `FavoriteRepository.moveFavoriteToGroup(...)` delegates to JNI `moveFavoriteToGroup`, re-emits the state flow with the moved favorite in its destination group, persists the favorites file exactly once on success, persists nothing and changes nothing on failure, runs off the main thread and takes part in the existing single-writer serialisation.
- `fav-ordering`: new requirement — a favorite can be moved from one group into another, taking its star and its coordinates with it, at a user-visible position in the destination group, surviving a restart; plus the invalid-target semantics (unknown source group, unknown destination group, destination already holding that name, source group equal to destination).
- `fav-management-ui`: new requirement — the favorite row exposes a "Move to group" action opening a destination-group dialog, and the action is absent when only one group exists.

The Android Auto capabilities (`auto-favorites`, `auto-destination-picker`) are deliberately **not** modified: the car surface keeps its current browse-only behavior.

## Impact

**Bridge module (`:osmscout-client-java`)**
- `osmscout-client-java/src/main/java/com/framstag/libosmscout/client/OSMScoutClient.java` — add the `moveFavoriteToGroup` native declaration with KDoc (contract, index clamping, collision refusal).

**App (`:app`)**
- `app/src/main/java/com/naviveylin/data/FavoriteRepository.kt` — new suspend write, following the `writeMutex.withLock { …Locked }` + `refreshState()` + `persist()` pattern.
- `app/src/main/java/com/naviveylin/ui/favorites/FavoritesViewModel.kt` — new action, snackbar messages, coverage by the existing in-flight order guard (widened to both move kinds).
- `app/src/main/java/com/naviveylin/ui/favorites/FavoritesSheet.kt` — favorite-row overflow menu, destination-group dialog, "New group…" path.
- `app/src/main/res/values/strings.xml` and the other `values-*/strings.xml` locales — new labels and failure messages.
- `app/src/test/java/com/framstag/libosmscout/client/FakeOSMScoutClient.kt` — override `moveFavoriteToGroup` in the in-memory fake (mirroring the C++ semantics) plus a call log, so repository/VM tests can assert the delegation.
- `app/src/test/java/com/naviveylin/data/FavoriteRepositoryTest.kt` — move-to-group persistence, failure and serialisation tests.
- `app/src/test/java/com/naviveylin/ui/favorites/FavoritesViewModelTest.kt` — action, message and guard tests.
- New Compose test for the destination dialog alongside `app/src/test/java/com/naviveylin/ui/favorites/` (the module's existing Compose UI test pattern).
- `TODO.md` — record the other natives the override is still missing (`moveGroup`, `moveStarredFavorite`, `getStarredFavorites`, `getFavoriteFileFormatVersion`, `isFavoriteFileFormatSupported`), which this change does not pick up.

**Not touched**
- libosmscout submodule (no C++ change; stays on its current commit).
- `:auto`, `:core`'s `AutoFavoritesProvider`, `NaviVeylinCarAppService`.

**Guidelines**
- `guidelines/UI.md` — the favorites-sheet row-action rules (which actions live on the row, how the destination dialog is presented, and the statement that favorites management stays on the phone while the car surface is browse-only).

**Specs modified**
- `openspec/specs/fav-service/spec.md`, `openspec/specs/fav-ordering/spec.md`, `openspec/specs/fav-management-ui/spec.md` — via the deltas in this change.
