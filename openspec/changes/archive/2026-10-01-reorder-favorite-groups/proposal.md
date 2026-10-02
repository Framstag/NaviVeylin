# Proposal

## Why

The favorites file has stored the groups as an **ordered list** since the submodule's `fav-ordering` work, and the native store already exposes `FavoriteStore::MoveGroup` through the JNI bridge (`Java_..._OSMScoutClient_moveGroup`, `libosmscout-client-java/src/OSMScoutClient.cpp`), declared in the submodule's Java source as `moveGroup(String groupName, int newIndex)`. Order already reaches every surface — `FavoriteRepository.refreshState()` keeps the `getFavoriteGroups()` array order in its `LinkedHashMap`, the phone grid renders `state.groups.keys` in that order, and the car place list reads the same map. **The only missing piece is the user's ability to change it**: every group arrives from the store in whatever order it was created, and nothing in the app can move one. The app is one bridge declaration and one drag affordance away from letting the user put the groups that matter first.

## What Changes

- Declare `moveGroup` in the **local bridge override** `osmscout-client-java/src/main/java/com/framstag/libosmscout/client/OSMScoutClient.java`, with the same KDoc the submodule's source carries. The JNI implementation, the C++ store method and the submodule's own Java declaration already exist and are pinned; the override file shadows the submodule file and currently trails it by one API family. No C++ change, no submodule commit, no native rebuild.
- `FavoriteRepository` gains `moveGroup(groupName, newIndex)`: the shape of the existing `moveFavorite` — take the write lock, one JNI call, `refreshState()`, one `persist()`; return the native result unchanged.
- `FavoritesViewModel` gains a `moveGroup` action that reports failure through the existing snackbar channel and takes part in the existing in-flight **order write** guard (`orderWriteInFlight`, shared with the favorite reorder and the cross-group move).
- `FavoritesSheet`'s group grid becomes reorderable: a **long-press drag on a group card** picks the card up and drops it at another position in the grid. The drag follows the same shape as the favorite rows in `GroupDetailList` — a working order owned by the composable, the released position committed through a pure index helper (nothing to persist when the card ends where it started), and the commit deferred to a `LaunchedEffect` so a sheet dismissed mid-drag writes nothing.
- A tap on a card still opens the group, and the card's overflow menu (Set Color / Rename / Delete) is unchanged: the drag handle is a distinct affordance and only a **long** press starts a drag.
- The stored group order becomes the group order everywhere: the next opening of the sheet, the group detail back-navigation, the car `PlaceListTemplate` headers, and the default destination of "Add current location" (which uses the first group in the stored order — a user who drags a group to the top now chooses that too).
- The order travels on its **own state channel** — `FavoriteRepository.groupOrder` (a `StateFlow<List<String>>`), surfaced to the car through `AutoFavoritesProvider.groupOrder()`. A reorder changes no map *contents*, and `Map.equals` ignores order, so the existing `StateFlow<Map<…>>` would drop the emission and every consumer iterating that map would keep the stale sequence (empty groups make the two maps value-equal). Both group-listing surfaces — the phone grid and the car place list — take their sequence from the channel and read the contents from the map, paired by the shared `orderedGroupNames` helper.
- **Scope is phone only for the gesture, shared for the data.** The Android Auto / AAOS surface gains no reorder affordance — Car App Library templates have no drag, and a driver-facing "move up/down" strip would duplicate a management task the phone already offers. The car keeps rendering the stored order, so the *data* is at parity and only the interaction deviates, exactly as `guidelines/UI.md` already states for the favorite reorder and for move-to-group. The car side has no drag affordance and no new screen: it collects the order channel and orders its existing section headers by it.
- **Additive, no breaking change.** No schema change, no new dependency (`sh.calvin.reorderable:3.1.0` is already used and supports `LazyVerticalGrid` via `rememberReorderableLazyGridState`), no favorites-file format change: the format already stores the groups as an ordered list, so a file written before this change is read unchanged and a file written after it is read by the current build as-is. **Rollback** is a revert of the commits; an older build keeps rendering the stored group order (it already iterates the `getFavoriteGroups()` array), so a rollback does not lose the user's order — it only freezes it.
- The change is a **local override in the bridge module**, not a submodule patch: the native side is already upstream and pinned.

## Capabilities

### New Capabilities

- `group-ordering`: a favorite group's position in the group order is user-defined, persisted in the favorites JSON file and restored unchanged after a restart, is what every surface that lists groups renders (phone grid, group detail entry point, car place-list headers), and carries the invalid-target semantics (unknown group fails without changing stored state; a target position outside the order is clamped to the nearest valid position; a negative position means the first; a single-group store has no order to change); one committed reorder is at most one persistence write, a failed one writes nothing, and overlapping commits are serialised.

`group-ordering` is a **new capability rather than a delta to `fav-ordering`**: `fav-ordering` is scoped by its own purpose to "where a favorite sits inside its group" (per-group, per-favorite positions), while this is the sequence of the groups themselves — a different subject with different invalid-target rules, and the two must stay independently readable.

### Modified Capabilities

- `fav-service`: new requirement — `FavoriteRepository.moveGroup(groupName, newIndex)` delegates to JNI `moveGroup`, re-emits the state flow with the group at its new position on success, persists the favorites file exactly once, persists nothing and changes nothing on failure, runs off the main thread and takes part in the existing single-writer serialisation.
- `group-grid-display`: new requirement — the group grid supports picking up a group card by touch-and-hold and dropping it at another position, commits only the released position, and keeps tap-to-open and the card's action menu working; plus the rule that a sheet dismissed mid-drag and a same-position drag write nothing.
- `auto-favorites`: new requirement — the car screen's group headers follow the **stored group order** read-only, updating in place when the order changes while the screen is open, with no reorder affordance on the car surface (the interaction parity statement; the data is shared).

## Impact

**Bridge module (`:osmscout-client-java`)**
- `osmscout-client-java/src/main/java/com/framstag/libosmscout/client/OSMScoutClient.java` — add the `moveGroup` native declaration with KDoc (contract, index clamping, negative index, `false` for an unknown group).

**App (`:app`)**
- `app/src/main/java/com/naviveylin/data/FavoriteRepository.kt` — new suspend write, following the `writeMutex.withLock { …Locked }` + `refreshState()` + `persist()` pattern; plus the new `groupOrder` state channel, written in `refreshState()` together with the map.
- `app/src/main/java/com/naviveylin/di/AutoFavoritesProviderImpl.kt` — implements `groupOrder()` by delegating to the repository.
- `app/src/test/java/com/naviveylin/di/AutoProviderLazinessTest.kt` — keeps the provider's lazy-repository contract for the new accessor.
- `app/src/main/java/com/naviveylin/ui/favorites/FavoritesViewModel.kt` — new action on the existing `orderWriteInFlight` guard, snackbar message on failure.
- `app/src/main/java/com/naviveylin/ui/favorites/FavoritesSheet.kt` — grid state plus `rememberReorderableLazyGridState`, group cards wrapped in `ReorderableItem` with a long-press drag handle, working-order state and the deferred commit.
- New `app/src/main/java/com/naviveylin/ui/favorites/GroupOrder.kt` — the pure index helpers for the group order (move, and the index to commit or `null`), mirroring `FavoriteOrder.kt`, so the grid's rules are unit-testable without a gesture.
- `app/src/main/res/values/strings.xml` and the other `values-*/strings.xml` locales — the drag handle's content description and the failure message.
- `app/src/test/java/com/naviveylin/ui/favorites/GroupOrderTest.kt` (new) — the pure index rules (bounds, no-op, disappeared group).
- New Compose test for the grid drag alongside the module's existing `FavoritesSheet*ComposeTest.kt` pattern.
- `app/src/test/java/com/naviveylin/ui/favorites/FavoritesViewModelTest.kt` — the action, the failure message and the in-flight order-write guard, now covering all three order writes.
- `app/src/test/java/com/naviveylin/data/FavoriteRepositoryTest.kt` — move-to-position persistence, failure, and serialisation against another write.
- `app/src/test/java/com/framstag/libosmscout/client/FakeOSMScoutClient.kt` — override `moveGroup` in the in-memory fake (mirroring the C++ semantics: clamp, negative = first, `false` for an unknown group) plus a call log, so the repository and ViewModel tests can assert the delegation.
- `TODO.md` — the entry that lists the natives the local override is missing loses `moveGroup`; `moveStarredFavorite`, `getStarredFavorites`, `getFavoriteFileFormatVersion` and `isFavoriteFileFormatSupported` stay out of scope here.

**Not touched**
- libosmscout submodule (no C++ change, no commit; stays on its current SHA).
- `NaviVeylinCarAppService`, `AutoClientProvider`, the car screen stack and its templates (the car surface keeps its rows, actions and browsing; it gains no reorder affordance and no new screen).
- The favorites JSON format and its version — groups are already written as an ordered list.

**:core and :auto (the order channel's plumbing, not a new feature)**
- `core/src/main/java/com/naviveylin/core/AutoFavoritesProvider.kt` — new `groupOrder(): StateFlow<List<String>>`, with KDoc on why the order is not read from the group map.
- `core/src/main/java/com/naviveylin/core/GroupOrdering.kt` (new) — `orderedGroupNames(order, known)`: the one tested rule both surfaces use to pair the order channel with the groups their map holds, so a group cannot disappear while the two channels are observed a moment apart.
- `auto/src/main/java/com/naviveylin/auto/FavoritesScreen.kt` — `combine`s the two flows (one observation, no second collector) and orders its section headers by `orderedGroupNames`.
- Tests: `core/src/test/java/com/naviveylin/core/GroupOrderingTest.kt` (new), `auto/src/test/java/com/naviveylin/auto/FavoritesScreenTest.kt` (header order, the order-only reorder, the unnamed-group fallback, a vanished name).

**Guidelines**
- `guidelines/UI.md` — the group grid's card affordance (long-press drag handle, tap-to-open preserved) and the phone-only-gesture statement extended from the favorite reorder to the group order.

**Specs modified**
- `openspec/specs/fav-service/spec.md`, `openspec/specs/group-grid-display/spec.md`, `openspec/specs/auto-favorites/spec.md` — via the deltas in this change; `openspec/specs/group-ordering/spec.md` is created by it. `fav-ordering` and `fav-management-ui` are deliberately untouched (favorite-within-group ordering and the favorites-row actions are unchanged).
