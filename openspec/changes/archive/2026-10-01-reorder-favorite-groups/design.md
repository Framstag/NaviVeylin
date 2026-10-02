# Design

## Context

See `proposal.md` — Why. The current state that shapes the approach:

- The native side is complete and pinned: `FavoriteStore::MoveGroup` → JNI `Java_..._OSMScoutClient_moveGroup` (`app/src/main/cpp/libosmscout/libosmscout-client-java/src/OSMScoutClient.cpp:7238`), declared in the submodule's Java source (`.../libosmscout-client-java/java/com/framstag/libosmscout/client/OSMScoutClient.java:895`) and covered by the submodule's `JavaScout` tests. The local bridge override (`osmscout-client-java/src/main/java/com/framstag/libosmscout/client/OSMScoutClient.java`) shadows that file and does **not** declare `moveGroup`, so Kotlin cannot reach it. The same override was brought one method forward for `moveFavoriteToGroup` by the in-flight change `move-favorite-between-groups`, which established the pattern.
- Group order already flows end to end: `FavoriteRepository.refreshState()` (`app/src/main/java/com/naviveylin/data/FavoriteRepository.kt`) fills a `LinkedHashMap` in `getFavoriteGroups()` array order, the UI state exposes it as `Map<String, List<FavoriteLocation>>`, the phone grid renders `state.groups.keys` in that order (`FavoritesSheet.kt:335`), and the car screen iterates the same map (`auto/src/main/java/com/naviveylin/auto/FavoritesScreen.kt:102`). Native persistence writes the groups as an ordered list, so no format work is needed.
- The house drag pattern exists: `GroupDetailList` (`FavoritesSheet.kt:507-611`) keeps a composable-local working copy, wraps rows in `ReorderableItem`, starts the drag from a leading `longPressDraggableHandle`, computes the position to commit with the pure `FavoriteOrder` helper, and defers the commit to a `LaunchedEffect` so a sheet dismissed mid-drag writes nothing. `sh.calvin.reorderable:3.1.0` is already a dependency and also supports `LazyVerticalGrid`.
- `FavoritesViewModel` already owns the one order-write guard (`orderWriteInFlight`) used by the favorite reorder and the cross-group move; `FavoriteRepository` owns the single-writer `writeMutex`.
- `guidelines/Design.md` §4 forbids native work on the main thread; §11 fixes the test conventions (Robolectric default sandbox for anything touching `FakeOSMScoutClient`). `guidelines/UI.md` already documents the "data shared, gesture phone-only" split for the favorite order and for move-to-group.

## Goals / Non-Goals

**Goals**

- The user can change the group order from the phone and see it persisted, restored and reflected by every surface that enumerates groups.
- The grid keeps a single interaction vocabulary with the favorite rows: long-press drag, commit on release, no-op and aborted drags write nothing.
- No new dependency, no native rebuild, no favorites-file format change, no `:auto` code change.

**Non-Goals**

- Reordering **favorites** (already covered by spec `fav-ordering`) and reordering the starred chip bar (native `moveStarredFavorite`/`getStarredFavorites` stay unexposed, as recorded in `TODO.md`).
- Any reorder affordance on the car surface, and any change to what the car shows beyond the group-header sequence it now inherits.
- Moving groups between "sections", sorting profiles, or automatic ordering (alphabetical/last-used) — the order stays purely manual.
- A separate group-management screen; the drag stays in the existing grid.

## Decisions

### D1 — Long-press drag on the group card (chosen) vs. menu up/down vs. a dedicated reorder mode

Chosen: a long-press drag handle on each card inside the existing `LazyVerticalGrid`, mirroring the favorite rows.

- *Long-press drag* (chosen) — one gesture vocabulary in the sheet; no new dependency (v3.1.0 covers grids); the released position is the committed one, which is what users already learned from the favorite rows; `guidelines/UI.md` already states drag-based reordering as the phone pattern.
- *"Move up"/"Move down" in the card's overflow menu* — trivial to test and impossible to trigger accidentally, but clunky for many groups, and it introduces a second reorder vocabulary beside the drag rows, against the UI.md intent of one pattern per subject.
- *Dedicated "Reorder groups" mode* (list with handles plus Done/Cancel) — safest against accidental drags and easiest to explain for accessibility, but it adds a mode, its entry/exit affordances, its dismissal rules and its own states: the most code and the most tests for a management action the grid can already host.

Risk of the chosen option — an accidental drag on a card the user meant to tap — is mitigated by requiring a **long** press on a **dedicated handle** (D4), and by committing nothing when the card ends where it started.

### D2 — A new pure `GroupOrder` helper vs. generalizing `FavoriteOrder` vs. inline index math

Chosen: a new `app/src/main/java/com/naviveylin/ui/favorites/GroupOrder.kt` with `move(groups, fromIndex, toIndex)` and `commitIndex(working, stored, groupName): Int?`, shaped like `FavoriteOrder`.

- *New helper* (chosen) — mirrors a proven, unit-tested pattern; the grid's rules (bounds, no-op, group that disappeared mid-drag) become testable without a gesture; no churn in the favorite code the in-flight `move-favorite-between-groups` change is still touching.
- *Generalize `FavoriteOrder`* — one object for both subjects, but the favorite variant carries the group-detail header offset (`favoriteIndex`) that the grid does not have; folding both in produces a helper whose two halves have different index bases, which is exactly the sort of thing a reviewer misses. `FavoriteOrder`'s own KDoc scopes it to the favorite list.
- *Inline in the composable* — least code, but the bounds/no-op rules would only be exercisable through Compose drag tests, which is how the current suite already has to work for the gesture; keeping the index math pure is what lets the failure paths be asserted cheaply.

Offset note for the implementation: the favorite list renders a leading "Add favorite" item, so its library indices are shifted by one. The grid has **no** leading item, so grid indices map 1:1 onto the group order — `GroupOrder` therefore has no `favoriteIndex` equivalent, and the `toIndex` it computes is exactly the value `moveGroup` receives.

### D3 — Working copy plus deferred commit (chosen) vs. committing in `onMove` vs. re-reading the store after each move

Chosen: the same shape as `GroupDetailList` — `rememberReorderableLazyGridState` whose `onMove` reorders a composable-local working list, a re-sync `LaunchedEffect` from the stored order while nothing is dragged, and a `pendingReorder` effect that performs the write while the sheet is still composed.

- *Working copy + deferred commit* (chosen) — one write per completed gesture, nothing written mid-drag, and a dismissal mid-drag cancels the commit by construction (the effect is cancelled with the composition). It is already the contract spec `fav-management-ui` records for favorites, so the same scenarios hold for groups by construction rather than by a second mechanism.
- *Commit inside `onMove`* — the library calls `onMove` for every cell the dragged card crosses; committing there would write the file several times per gesture, breaking the "one write per committed move" requirement (spec `group-ordering`) and hammering the native save path.
- *Optimistic commit then re-read the store* — the grid would flicker back to the stored order between the move and the repository's refresh, and a failure would leave the visible order wrong with no way to tell the user what happened.

Grid-specific index semantics: because the card order is an **order**, not a gallery, `onMove` applies `add(to.index, removeAt(from.index))` — the same move-shift that `FavoriteOrder.move` performs and the same basis as the native index ("the order after the group has been removed from its current position"). The library README's grid snippet shows a *swap* for a photo grid; that would renumber two positions instead of moving one group.

### D4 — A dedicated long-press drag handle sharing the card's interaction source

Chosen: the drag handle is its own affordance on the card (`Modifier.longPressDraggableHandle`, like the favorite rows), and the card's click and the handle share one `MutableInteractionSource` as the library's `Card` example prescribes.

- *Handle + shared interaction source* (chosen) — a tap on the card body still opens the group (the spec requires it), only a hold on the handle starts a drag, and the shared source keeps the card's ripple from firing under a drag.
- *`Modifier.longPressDraggableHandle` on the whole card* — fewer moving parts, but a long press anywhere on a card would start a drag, making a slow tap open-then-drag ambiguous, and it removes the visual affordance that tells the user the grid is reorderable.
- *`combinedClickable` on the card with a long-press drag* — one gesture surface, but Compose's `combinedClickable` long press and the library's drag start compete for the same gesture; the library's own handle modifier is the supported path.

Consequence: each card grows a handle, so `GroupCard` gains a `dragHandle: (@Composable () -> Unit)?` parameter, exactly as `FavoriteItem` has one now. Content description comes from a new string (`reorder_group`) alongside the existing `reorder_favorite`.

### D5 — Reuse the repository write path and the existing in-flight guard

Chosen: `FavoriteRepository.moveGroup(groupName, newIndex)` is one more `writeMutex.withLock { …Locked }` write (`client.moveGroup`, `refreshState()`, `persist()` — nothing new), and `FavoritesViewModel.moveGroup` takes the **existing** `orderWriteInFlight` flag rather than introducing its own.

- *Existing guard* (chosen) — the three order writes (favorite reorder, cross-group move, group reorder) all mutate the visible order; one flag is what stops a group drag from landing between a favorite drag's JNI call and its state refresh, which is the same visible-sequence problem the flag was introduced for.
- *A second flag for groups* — narrower contention, but two order writes could then interleave their refreshes and the sheet could end up rendering an order the user did not ask for; the repository's lock would keep the data consistent, so the bug would be a UI-only surprise that is harder to reproduce.
- *No guard* — simplest, and the repository would still not lose data, but the last writer's `refreshState()` would decide what the user sees, which is the exact complaint the guard exists to answer.

Threading and lifecycle (per `guidelines/Design.md` §4): no new thread, scope or worker. The drag state is composable-local and dies with the sheet; the write runs on `viewModelScope` and the JNI call plus file write happen inside the repository's `withContext(defaultDispatcher)` (already `Dispatchers.Default`, injectable for tests). Nothing native runs on the main thread; a cancelled composition cancels the pending commit rather than orphaning it. No new logging is introduced, so the no-coordinates gate (`checkNoCoordinatesInLogs`) is untouched.

### D6 — Bridge declaration in the local override (chosen) vs. patching the submodule vs. a C++ change

Chosen: add `public native boolean moveGroup(String groupName, int newIndex);` with KDoc to the local override `osmscout-client-java/src/main/java/com/framstag/libosmscout/client/OSMScoutClient.java`.

- *Local override* (chosen) — the file already shadows the submodule's and already trails it; the method exists in the submodule's Java source and in the built JNI library, so this is a declaration, not new behavior. No submodule commit, no SHA bump, no native rebuild. Same precedent as `moveFavoriteToGroup` in `move-favorite-between-groups`.
- *Patch the submodule* — the change would be a no-op diff (the declaration is already there), and it would cost a submodule commit, a gitlink bump and a native rebuild for the CI, for nothing.
- *Change C++* — nothing to change; `FavoriteStore::MoveGroup` and its JNI entry point are upstream and tested by the submodule's `JavaScout` suite.

`TODO.md`'s "missing natives" entry then loses `moveGroup` and keeps the rest (`moveStarredFavorite`, `getStarredFavorites`, `getFavoriteFileFormatVersion`, `isFavoriteFileFormatSupported`).

### D7 — The order gets its own state channel (chosen) vs. an order-carrying state value type vs. an order-sensitive map subclass

Chosen: `FavoriteRepository` publishes `groupOrder: StateFlow<List<String>>` next to `favorites: StateFlow<Map<…>>`, written together in `refreshState()`. Consumers that list groups take their sequence from the list and the contents from the map, paired by `orderedGroupNames` (`:core`).

This decision replaced the original plan of relying on the map's insertion order, because the implementation showed that plan cannot work: **a `StateFlow` drops an emission equal to its current value, and `Map.equals` ignores order**, so a reorder that changes nothing but the sequence produces a map equal to the previous one and never reaches a collector. It is not a rare corner either — with groups holding no favorites the two maps are trivially equal, so dragging cards of empty groups would show no movement at all (found by `FavoritesViewModelTest`, which builds a store of empty groups and asserted the reordered key sequence). The same trap applies to the car screen, which iterates the same map.

- *Order channel* (chosen) — a `List` compares ordered, so every order change emits by construction; the map contract stays exactly what it was for contents; the change is additive (one new flow, one shared pairing helper) and both surfaces are updated in the same shape.
- *Order-carrying state value* (`FavoriteGroups(order, byGroup)`) — one source of truth and the strongest contract, but it changes the type of `favorites`/`favoriteLocations` and therefore every consumer and test double; rejected for a change whose feature work was already done, and it buys nothing the channel does not.
- *Map subclass whose `equals` includes order* — smallest diff, no type changes anywhere, but it deliberately breaks the `Map` equality contract that the rest of the codebase (and Kotlin's own map helpers) assumes; a later `state.groups == otherMap` would compare differently from every other map.

Consequence for the two channels being observed a moment apart: their pairing must tolerate a lag, which is what `orderedGroupNames(order, known)` does — names in the stored order first, then any group the order does not name yet, so a group can never disappear from a list. Its rules are unit-tested in `:core`.

The phone ViewModel collects both flows (two collectors, not one `combine`: the map flow carries the contents and the starred list, the order flow the sequence) and exposes `FavoritesUiState.groupOrder`; the car screen `combine`s them in its single existing collector so it does not add a second observation.

### D8 — The car side gets the order channel, nothing else

Chosen: `AutoFavoritesProvider` gains `groupOrder()`, `FavoritesScreen` `combine`s it with the favorites flow in its existing collector and orders its section headers by `orderedGroupNames`. No reorder affordance, no new screen, no template change beyond the header sequence.

- *Order channel through the provider* (chosen) — the car renders the stored order by construction instead of by luck of the map flow not conflating; one new interface method and one ordered loop; the interaction stays phone-only, which `guidelines/UI.md` already argues for the favorite order and for move-to-group.
- *Leave the car on the map* — no `:core`/`:auto` change at all, but the header order would then be stale exactly in the conflation case the order channel exists for, making the `auto-favorites` requirement knowingly untrue.
- *A car-side reorder action* — rejected outright: Car App Library templates have no drag, and a driver-facing move-up/down strip duplicates a management task the phone owns.

This is the one place where the order channel is more than a phone concern, so the proposal's "not touched" list was corrected to name `:core` and `:auto` as lightly touched (interface method, one collection site and one ordering loop).

## Risks / Trade-offs

- [A drag in a scrollable grid can fight the scroll gesture] → the drag starts from a long press on a handle, which is the same gesture boundary the favorite list uses; verify on device with a multi-row group list.
- [Grid drag indices and the order index can drift if the library reports cell-local indices] → `GroupOrder` is pure and unit-tested; the Compose test asserts the committed index by drag direction, and the on-device check confirms the persisted file order.
- [The card's tap could regress into a drag start] → dedicated handle plus shared `MutableInteractionSource`; a Compose test asserts a tap still opens the group and starts no drag.
- [The order silently breaking under a future state refactor] → the order no longer depends on map equality at all (D7); the repository test asserts the order flow's sequence, and the car test asserts the header order for the same-contents case, so a regression fails loudly.
- [The two channels being observed a moment apart] → `orderedGroupNames` appends any group the order does not name, unit-tested in `:core`; a group can never vanish from a list because the order lagged.
- [A mid-drag dismissal writing a half-dragged order] → the deferred-commit effect is cancelled with the composition, as for favorites; the spec asks for a scenario and the Compose test covers it.
- [The `Add current location` default group becomes order-dependent] → intentional and spec'd (`group-ordering` — a group used as a single-group default is the first stored group); note that spec `fav-management-ui` describes that quick action as "prompting for group selection", which the current dialog does not do — a pre-existing spec/implementation mismatch this change neither introduces nor fixes.
- [Scope creep into starred-chip ordering] → explicitly a non-goal; `moveStarredFavorite` stays unexposed and recorded in `TODO.md`.

## Migration Plan

No data migration and no format version change: the favorites file already stores the groups as an ordered list, so an existing file loads unchanged and a file written after this change loads in the current build. Because only the mutation is new, rollback is a revert of the commits: an older build keeps rendering the stored order (it already iterates the `getFavoriteGroups()` array) and simply cannot change it. Verify after shipping that a store created by an older build still opens, which the existing `FavoriteRepositoryTest` load cases cover.

## Verification

- **Unit (host)**: `GroupOrderTest` (bounds, no-op, negative/over-range, group that disappeared), `GroupOrderingTest` (`:core` — order wins, unnamed group appended, vanished name skipped), `FavoriteRepositoryTest` (delegation, one persist per success, nothing on failure, not-initialised returns false without a native call, overlap with another write keeps both, the order flow's sequence equals the native array, and an order-only change emits a state that differs from the previous one), `FavoritesViewModelTest` (failure message, in-flight guard covering all three order writes, dropped second commit, `uiState.groupOrder` after a reorder of empty and of populated groups), `AutoProviderLazinessTest` (the new accessor is lazy like the favorites one).
- **Car (Robolectric)**: `FavoritesScreenTest` — headers follow the order channel, a reorder with an unchanged map updates them in place, an unnamed group still appears, a vanished name is skipped.
- **Compose (Robolectric, default sandbox)**: grid drag reorder commits the released position; same-position drag writes nothing; tap still opens the group; card menu still works; single-group drag commits nothing.
- **Build**: `:app` compiles for both flavors and all three ABIs via the `build-app` skill (Kotlin/Java only — no native rebuild expected; the bridge module still has to compile the new declaration against the pinned JNI library). Run the suite with the `run-tests` skill.
- **On device**: drag several groups on a phone install, restart the app, confirm the order survives; confirm the persisted `favorites.json` lists the groups in the dragged order; open the car favorites screen (DHU / AAOS AVD) and confirm the header order matches. `adb logcat -s NaviVeylin` for native-side save errors; no new log lines are expected from this change.
