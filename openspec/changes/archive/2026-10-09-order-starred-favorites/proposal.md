# Proposal

## Why

The favorites sheet lets the user order favorites inside a group (`fav-ordering`) and order the groups themselves (`group-ordering`), but not order the **starred** favorites — the one list the user reaches in a single tap. The native store already treats the stars as **one order spanning all groups**: `FavoriteLocationService` keeps a `starredPosition` per starred favorite (`SetStarred(true)` appends at the end, unstarring drops the position, the writer normalizes a hand-edited file into a self-consistent order), `FavoriteStore::GetStarred()`/`MoveStarred()` expose that order, the JNI bridge implements `getStarredFavorites`/`moveStarredFavorite`, and the submodule's Java source declares both. The app reaches none of it: the **local bridge override** is missing the two declarations (`TODO.md` §79, logged while landing the cross-group move), and `FavoriteRepository.getAllStarredFavorites()` re-derives the list by iterating the group map, so it renders group order and in-group order and **discards `starredPosition` entirely**. The chip bar spec even says what it does today — grouped by group, in-group order within a block. The feature is one bridge declaration and one drag affordance away.

## What Changes

- Declare `moveStarredFavorite(String groupName, String favName, int newIndex)` and `getStarredFavorites()` in the **local bridge override** `osmscout-client-java/src/main/java/com/framstag/libosmscout/client/OSMScoutClient.java`, with the KDoc the submodule's source carries. The JNI implementation, the C++ service methods and the submodule's own declarations already exist and are pinned; the override shadows the submodule file and trails it by one API family. **No C++ change, no submodule commit, no native rebuild.** (The two file-format declarations the same TODO entry names — `getFavoriteFileFormatVersion`, `isFavoriteFileFormatSupported` — stay out of scope; nothing in this change reads the format version.)
- `FavoriteRepository` gains `moveStarredFavorite(groupName, favName, newIndex)` in the shape of `moveGroup`: write lock, one JNI call, `refreshState()`, one `persist()`; and it gains a **`starredOrder` state channel** (`StateFlow<List<StarredFavoriteLocation>>`) filled in `refreshState()` from the native starred order. `getAllStarredFavorites()` — the map-derived list — goes away; the car and the phone both read the channel, so the stored order is the only order either renders.
- `FavoritesViewModel` gains a `moveStarred` action, reports failure through the existing snackbar channel and takes part in the existing in-flight **order write** guard (`orderWriteInFlight`), which now covers four order writes.
- The starred chip bar becomes the reorder surface: a **long-press drag on a chip** picks it up and drops it at another position in the bar, spanning groups. Same shape as the two existing reorders — a working order owned by the composable, the released position committed through a pure index helper (nothing to persist when the chip lands where it started), and the commit deferred to a `LaunchedEffect` so a sheet dismissed mid-drag writes nothing. Short-press tap still routes to the favorite; horizontal scrolling still works, because only a long press lifts a chip.
- **Chips stop being grouped by group.** One order spanning groups cannot be rendered as group blocks: the bar becomes one flat sequence in the stored starred order, with the group name remaining as the chip's secondary line (it already shows it). This supersedes the grouping wording of `fav-starred-chip-bar` and the "…and of the starred chip bar" purpose line of `fav-ordering`.
- The stored starred order becomes the starred order on **every** surface: the chip bar, the next opening of the sheet, and the car's starred-favorites screen (`FavoritesScreen(starredOnly = true)`), which renders it as **one ordered list** instead of group sections (the all-favorites mode keeps its group headers).
- **Scope is phone only for the gesture, shared for the data.** The car surface gains no reorder affordance — Car App Library templates have no drag — and keeps rendering the stored order read-only, exactly as `guidelines/UI.md` already states for the favorite order and the group order.
- **Additive, no breaking change, no file-format change.** The favorites file already carries `starredPosition` (format version 1); a file written by this build is read by an older build as-is, and an older build keeps rendering its own derived order, so **rollback** freezes the order rather than losing anything. Stars created before the position attribute existed (or hand-edited files) have no stored position; the store's defined fallback orders them by group, then by name, and the next write normalizes the file into explicit positions — so an upgrading user's chip bar may settle into that fallback order **once**.
- The change is a **local override in the bridge module**, not a submodule patch: the native side is already upstream and pinned.

## Capabilities

### New Capabilities

- `starred-ordering`: the position of a starred favorite in the one starred order that spans all groups is user-defined, persisted in the favorites JSON file and restored unchanged after a restart, is what every surface that lists starred favorites renders (phone chip bar, car starred list), and carries the order's lifecycle and invalid-target semantics — starring appends at the end, unstarring removes the entry, an unknown group/favorite or an unstarred favorite fails without changing stored state, a target position outside the order is clamped, a negative position means the first, an order of one entry has nothing to change, one committed reorder is at most one persistence write, a failed one writes nothing, and overlapping commits are serialised.

`starred-ordering` is a **new capability rather than a delta to `fav-ordering`**: `fav-ordering` is scoped by its own purpose and its "Order is scoped to a group" requirement to *where a favorite sits inside its group*, while this is the sequence of the starred favorites across groups — a different subject, whose order survives operations that the in-group one ignores (unstarring removes an entry, starring appends). The two must stay independently readable, and the chip bar's order must stop being a side effect of the in-group order.

### Modified Capabilities

- `fav-service`: new requirement — `FavoriteRepository.moveStarredFavorite(groupName, favName, newIndex)` delegates to JNI `moveStarredFavorite`, re-emits state on success, persists the favorites file exactly once, persists nothing and changes nothing on failure, runs off the main thread and takes part in the existing single-writer serialisation; plus the repository exposing the stored starred order as its own flow instead of deriving it from the group map.
- `fav-starred-chip-bar`: the requirement "Chip order follows the stored favorite order" is replaced — the bar lists the starred favorites in **the stored starred order**, one flat sequence, updating in place on a reorder and after a restart; tap-to-route, the optional group line and the empty-bar rule are unchanged, and a long-press drag on a chip is added (phone only).
- `fav-star`: new requirement — starring a favorite enters it at the **end** of the starred order and unstarring takes it out of the order; the star icon behaviour and persistence of the flag are unchanged.
- `fav-ordering`: the purpose line loses "…and of the starred chip bar", and the surface enumeration of "Favorite position within a group is user-defined" narrows to the group detail list and the car place list, so the chip bar is no longer claimed as a surface of the in-group order (a scenario states that an in-group reorder leaves the starred order alone).
- `auto-favorites`: new requirement — in starred mode the car `PlaceListTemplate` renders the stored starred order as **one ordered list** with no group headers (the group headers requirement stays for the all-favorites mode), read-only, updating in place while the screen is open, with an explicit note that the mode renders an order that spans groups.

## Impact

**Bridge module (`:osmscout-client-java`)**
- `osmscout-client-java/src/main/java/com/framstag/libosmscout/client/OSMScoutClient.java` — add the `moveStarredFavorite` and `getStarredFavorites` native declarations with KDoc (contract, index clamping, negative index, `false` for an unknown or unstarred favorite, the empty array with no store). Nothing else in the file changes.

**App (`:app`)**
- `app/src/main/java/com/naviveylin/data/FavoriteRepository.kt` — new `starredOrder` channel (`StateFlow<List<StarredFavoriteLocation>>`) written in `refreshState()` from the native starred order; new `moveStarredFavorite` suspend write following the `writeMutex.withLock { …Locked }` + `refreshState()` + `persist()` pattern; `getAllStarredFavorites()` removed.
- `app/src/main/java/com/naviveylin/ui/favorites/FavoritesViewModel.kt` — the UI state's `starredFavorites` is fed from the repository channel; new `moveStarred(groupName, favName, newIndex)` action on the `orderWriteInFlight` guard with a failure snackbar; the in-flight guard's KDoc updated to four order writes.
- `app/src/main/java/com/naviveylin/ui/favorites/FavoritesSheet.kt` — `StarredChipBar` becomes a reorderable `LazyRow` (`rememberReorderableLazyListState`, the same library API already used by the group detail list), with a working-order state, `ReorderableItem` wrappers, a long-press drag handle and the deferred commit; the chip's key becomes the `(group, name)` pair.
- New `app/src/main/java/com/naviveylin/ui/favorites/StarredOrder.kt` — the pure index helpers for the starred order (move by `(group, name)` identity, and the index to commit or `null`), mirroring `FavoriteOrder.kt` and `GroupOrder.kt`, so the bar's rules are unit-testable without a gesture.
- `app/src/main/java/com/naviveylin/di/AutoFavoritesProviderImpl.kt` — implements the provider's new starred-order accessor by delegating to the repository.
- `app/src/main/res/values/strings.xml` and the other `values-*/strings.xml` locales — the chip drag handle's content description and the star-reorder failure message.
- `auto/src/main/java/com/naviveylin/auto/FavoritesScreen.kt` — in starred mode the rows come from the starred-order channel and are rendered as a single ordered list; the all-favorites mode is unchanged.
- `core/src/main/java/com/naviveylin/core/AutoFavoritesProvider.kt` — new starred-order accessor, with KDoc on why the order is not derived from the group map.

**Tests (`:app`, `:auto`, `:core`)**
- `app/src/test/java/com/framstag/libosmscout/client/FakeOSMScoutClient.kt` — implement `moveStarredFavorite` and `getStarredFavorites` in the in-memory fake, mirroring the native semantics (append on star, position dropped on unstar, clamp, negative = first, `false` for unknown or unstarred) plus a call log.
- New `app/src/test/java/com/naviveylin/ui/favorites/StarredOrderTest.kt` — the pure index rules (bounds, no-op, a favorite that vanished while it was being dragged).
- New Compose test for the chip drag alongside the module's existing `FavoritesSheet*ComposeTest.kt` pattern.
- `app/src/test/java/com/naviveylin/ui/favorites/FavoritesViewModelTest.kt` — the action, the failure message and the shared in-flight guard, now covering all four order writes.
- `app/src/test/java/com/naviveylin/data/FavoriteRepositoryTest.kt` — the starred order after a move, the failed move, the order after star/unstar, and serialisation against another write.
- `app/src/test/java/com/naviveylin/di/AutoProviderLazinessTest.kt` — keeps the provider's lazy-repository contract for the new accessor.
- `auto/src/test/java/com/naviveylin/auto/FavoritesScreenTest.kt` — starred mode renders one ordered list, follows a reorder, and keeps the empty state.

**Guidelines & tracking**
- `guidelines/UI.md` — a new parity-table row (reorder starred favorites: long-press drag on a chip on the phone, not offered on the car, same stored order read-only) and the "why reordering is phone-only" section extended from two to three reorders.
- `TODO.md` — §79's enumeration loses `moveStarredFavorite` and `getStarredFavorites`; the drift-gate fix candidate it names stays open and out of scope here.

**Not touched**
- libosmscout submodule — no C++ change, no commit, SHA unchanged.
- The favorites JSON format and its version — `starredPosition` is already written and read by the store.
- `NaviVeylinCarAppService`, `AutoClientProvider`, the car screen stack and its templates — the car keeps its rows, actions and browsing and gains no reorder affordance and no new screen.
- Group detail reordering, the cross-group move and the group order (`fav-ordering`, `group-ordering`, `fav-management-ui`, `group-grid-display` requirements themselves).
