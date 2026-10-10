# Design

## Context

See `proposal.md` — Why. The state that shapes the approach:

```
libosmscout submodule (pinned, upstream)              app (Kotlin)
+-----------------------------------------+   +--------------------------------+
| FavoriteLocationService                 |   | FavoriteRepository             |
|   starredPosition per starred favorite  |   |   _favorites: Map<group,List>  |
|   SetStarred(true) -> append at end     |   |   refreshState(): reads        |
|   GetStarred() -> flat order, all groups|   |     favoriteGroups only         |
| FavoriteStore::MoveStarred/GetStarred   |   |   getAllStarredFavorites():    |
| JNI: moveStarredFavorite/getStarredFavs |   |     map iteration + filter     |
+-----------------------------------------+   +--------------------------------+
                    |                                       ^
                    |  submodule Java declares both          |
                    v                                       |
        osmscout-client-java/.../OSMScoutClient.java  (local override)
                    X  2 declarations missing -> app cannot call
```

Facts the decisions rest on (verified in the tree):

- `FavoriteLocationService.cpp` holds the whole order: `SetStarred(true)` writes `starredPosition = max + step` (`:723`), unstarring erases both keys (`:709`), `MoveStarred` clamps and treats "unknown / not starred" as a refusal (`:756`), `GetStarred` returns `CollectStarred`'s sorted order (`:739`), and the **writer** normalizes a file whose positions are missing or duplicated (`:325`). File format version 1 already carries the key, so nothing about the format changes.
- `StarOrderLess` (`:114`) defines the fallback: known positions ascending, then unpositioned entries by **group index, then favorite name**. A file with no positions therefore yields a defined total order, and the next write stores explicit positions.
- The JNI implementations exist and are compiled into the current `.so` (`OSMScoutClient.cpp:7311`, `:7337`); the submodule's Java source declares them (`:931`, `:940`) and `StarredFavoriteLocation` exists. Only the override file (which shadows the submodule's `OSMScoutClient.java`) lacks the declarations — `TODO.md` §79.
- `FavoriteRepository.persist()` hands the whole Java snapshot to `saveFavoriteLocations`; the C++ side copies every favorite attribute back (`OSMScoutClient.cpp:7057`) and `ReplaceAndSave` "keeps the starred order of the snapshot: the starred favorites carry their place with them". So the order survives an unrelated write.
- `FavoriteLocation` has no `equals`/`hashCode`, and `refreshState()` builds fresh instances — the map flow re-emits on every write. The order still needs its own channel, because the order is not a property the group map can express (see Decision 2).
- `sh.calvin.reorderable:3.1.0` is already a dependency (`app/build.gradle.kts:1382`) and its `rememberReorderableLazyListState` is already used for the group detail list (`FavoritesSheet.kt:522`); `rememberReorderableLazyGridState` for the grid (`:782`).

## Goals / Non-Goals

**Goals:**

- The one starred order that spans all groups is readable by the app, reorderable by the user on the phone, and rendered unchanged by every starred-favorites surface.
- One source of truth for the order: the native store. No Kotlin re-derivation of the ordering rule.
- The existing write-serialisation and single-write-per-reorder guarantees extend to the new write unchanged.

**Non-Goals:**

- Any reorder affordance on the Android Auto / AAOS surface.
- `getFavoriteFileFormatVersion` / `isFavoriteFileFormatSupported` (no consumer here) and the buildSrc/CI drift gate `TODO.md` §79 proposes — its own piece of work.
- Changing the in-group order, the group order or the favorites file format.
- A starred reorder inside the group detail list (that list's contract is the in-group order).

## Decisions

### 1. Reach the order through the bridge override, not a submodule patch

Declare `moveStarredFavorite`/`getStarredFavorites` in `osmscout-client-java/src/main/java/com/framstag/libosmscout/client/OSMScoutClient.java`, with the submodule's KDoc.

- *Chosen*: the native side is complete and pinned; the override already shadows the submodule file and trails it by one API family. Java-only compile change — no C++, no submodule commit, no native rebuild, no ABI risk on three ABIs.
- *Alternative — submodule patch*: rejected. There is nothing to implement; a patch would only move the same two declarations into a file the app does not own, and would force a gitlink bump.
- *Alternative — declare all four missing natives now*: rejected as scope. Two of them have no consumer in this change; `TODO.md` §79 keeps them.
- Risk: a runtime `NoSuchMethodError` if the pinned submodule ever dropped a method. Mitigated by tests against `FakeOSMScoutClient` and by the drift gate the TODO item tracks.

### 2. A `starredOrder` state channel on the repository, not a Kotlin re-derivation

`FavoriteRepository` gains `starredOrder: StateFlow<List<StarredFavoriteLocation>>`, written in `refreshState()` from `client.getStarredFavorites()`, i.e. under the same write lock and in the same function that publishes `_favorites` and `_groupOrder` — the three always describe one store. `getAllStarredFavorites()` is removed.

- *Chosen*: the order spans groups, names each entry's group, and its rule (fallback, clamping, duplicate positions) lives in native code that already normalizes it. A channel as a `List` compares ordered, so a reorder always emits.
- *Alternative — sort `attributes["starredPosition"]` in Kotlin*: rejected. It re-implements `StarOrderLess` (unpositioned entries, ties, unparsable values) in a second language, and the two would have to stay in step by hand. It also reads the position out of an attribute map the app is not supposed to interpret.
- *Alternative — keep `getAllStarredFavorites()` and only add the write*: rejected. The write would be invisible: the chip bar would keep rendering the derived order until a restart.
- *Alternative — fold the order into the existing `favorites` map flow*: rejected. The map's shape (group → favorites) cannot express a sequence that crosses groups, so a consumer would still need the order from somewhere else.

### 3. The chip bar becomes one flat sequence; a separate manage list is not built

- *Chosen*: the bar lists the stored starred order directly, group name as the chip's secondary line. The user reorders the thing they see, and one order has one representation.
- *Alternative — keep group blocks and reorder inside a block (the earlier shape)*: rejected. A starred favorite's place in its block *is* its in-group position, already reachable by dragging in the group detail list, so the feature would add no capability while claiming a second meaning for the same gesture.
- *Alternative — keep the bar read-only and add a vertical "Starred" management list in the sheet*: rejected for now. It needs a new entry point and surface for a list of one-tap shortcuts, and the two representations would compete for the same order. Kept as the fallback if the horizontal drag proves unusable on device (see Risk 2).

### 4. Drag on the chip bar: long-press, working order, deferred commit, pure index helper

`StarredChipBar` gets its own `LazyListState` + `rememberReorderableLazyListState`, chips wrapped in `ReorderableItem` keyed by the `(group, name)` pair, a working order held by the composable, and the commit deferred to a `LaunchedEffect`. New `StarredOrder.kt` holds the pure rules — `move(order, from, to)` (index basis: the order after removal, the basis `MoveStarred` and `GroupOrder.move` use) and `commitIndex(working, stored, groupName, favName)` returning `null` for a no-op and for an entry that vanished mid-drag.

- *Chosen*: it is the shape the group detail list and the group grid already use (`FavoritesSheet.kt:522`, `:782`), so the three reorders share one mental model and one set of edge-case rules, and the index math is unit-testable without a gesture.
- *Pair identity*: the same favorite name can exist in two groups, so `name` alone is not an identity in an order that spans groups; the chip's existing key `${group}_${name}` becomes the pair.
- *Alternative — a visible drag-handle icon on each chip*: rejected. A `FilterChip` is narrow; a handle costs horizontal room in a bar whose whole point is fitting several chips, and the library lifts on long press anyway. Long-press also leaves the tap and the horizontal swipe intact.
- *Alternative — commit on every drag movement*: rejected. It would write once per pixel of drag and lose the atomicity the other two reorders have.

### 5. The starred move joins the existing order-write guard

`FavoritesViewModel.moveStarred(groupName, favName, newIndex)` takes the same `orderWriteInFlight` flag as `moveFavorite`, `moveGroup` and `moveFavoriteToGroup` (now four), reports failure through the existing snackbar channel, and the repository write follows the `writeMutex.withLock { …Locked }` + `refreshState()` + `persist()` pattern.

- *Chosen*: the repository's `writeMutex` keeps each write whole (data integrity); the ViewModel flag keeps the visible sequence sane, because the star order, the in-group order and the group order are three views of one store and two racing commits could show an order the user did not ask for.
- *Alternative — no guard*: rejected; it is the same argument that produced the flag for the other three.

### 6. The car starred mode renders one ordered list, fed by a provider accessor

`AutoFavoritesProvider` gains `starredOrder()`; `AutoFavoritesProviderImpl` delegates to the repository (still through `Lazy<FavoriteRepository>`, so resolving the provider on the car host thread stays free of native work). `FavoritesScreen(starredOnly = true)` collects it and renders a single list.

- *Chosen*: the car holds no ordering logic and no attribute parsing; it renders whatever order the phone produced, exactly as it already does for the favorite order and the group order.
- *Alternative — sort the starred rows in the car screen by `starredPosition`*: rejected (Decision 2's second alternative, in a second module).
- *Alternative — keep group sections in starred mode*: rejected as contradictory. Sections cannot express a cross-group sequence.
- *Alternative — migrate the screen to `CarScreenObservations` first*: rejected as scope. The screen's observation lifetime is pre-existing debt (`TODO.md` §103), not something this change's specs ask for; touching it would fold a lifecycle refactor into one extra list.
- The all-favorites mode keeps its headers; the grouping requirement is scoped to that mode.
- Observation lifetime: the accessor is a third input of the screen's **existing single `combine`** (favorites map, group order, starred order) on `carScreenScope("FavoritesScreen")`, so the screen still holds one collector per instance rather than a second bare launch. `FavoritesScreen` is the one car screen that never moved to the `CarScreenObservations` + `<Screen>Observations` pattern (Map, Navigation and FreeDriving did); migrating it was rejected here as scope this change's specs do not require — the screen has no collector-accumulation bug, since the scope dies with the screen — and the deviation is recorded as its own `TODO.md` item (§103) with the migration as its fix candidate.

### 7. Threading and lifecycle

| Component | Thread |
|---|---|
| `moveStarredFavorite` write (JNI mutation + snapshot read + file write) | repository `defaultDispatcher` (`Dispatchers.Default`), inside `writeMutex` |
| `refreshState()`'s `getStarredFavorites()` read | the same background dispatcher, inside the write lock or on load |
| `starredOrder` collection in `FavoritesViewModel` | `viewModelScope` (main), state publication only |
| Chip drag commit | `LaunchedEffect` in the sheet; the write is dispatched by the ViewModel as above |
| Car screen's starred-order observation | the screen's started-period child scope; no native call on the host thread |

No new thread, no new scope, no native call on the main thread. The car host callback path is unchanged: the provider is resolved lazily and the observation is cancelled on stop like every other one.

### 8. No migration of stored data

Nothing in the format changes: `starredPosition` and the version key are already written and read by the pinned native code. An older build ignores the key and keeps rendering its derived order, so rollback freezes the order rather than losing it.

The one visible consequence is for files whose starred favorites carry no position (stars created before the attribute existed, or hand-edited files): the store's fallback order is group order, then favorite name, so the chip bar may settle into that sequence **once**, after which the next write stores explicit positions. This follows from the native rule, not from an app decision; the spec states it as the recovery contract.

## Risks / Trade-offs

- **[Horizontal drag in a horizontal scroller]** The bar scrolls horizontally and the sheet is a draggable surface; a drag gesture could either scroll while the user means to reorder or dismiss the sheet. → Only a long press lifts a chip (the library's own threshold), the drag is handled inside the bar's lazy state, and the on-device pass exercises drag vs swipe vs sheet drag explicitly. If it proves unusable, the fallback is Decision 3's alternative (read-only bar plus a vertical manage list), which is an additive UI change to the same channel and write.
- **[A reorder that is not visible until the write lands]** A commit is one JNI mutation plus one file write; the working order already shows the released position, and the refresh replaces it. → Same as the two existing reorders; the snackbar reports a failure, and a refused write leaves the stored order visible.
- **[Pre-position stars reorder once on upgrade]** → Documented in Decision 8 and stated as a recovery contract in the spec; the alternative (inventing positions for the app's own view) would mean a Kotlin ordering rule again.
- **[Bridge drift recurs]** The override can fall behind the submodule's Java source silently — no compiler error, only a `NoSuchMethodError` at the call site. → `TODO.md` §79's drift gate remains the fix; this change removes two of the four names from that list.
- **[The order and the chip list disagree for a moment]** A reorder that lands while a star is being toggled is excluded by the shared write lock (the star toggle is a repository write too). → Accepted: the star toggle does not take the ViewModel's order guard, so a star toggled mid-drag simply re-emits the store's order, which is the truth.

## Migration Plan

No data migration and no format change. Ship order: bridge declarations → repository channel and write → ViewModel action → chip bar drag → provider accessor and car screen. Rollback is a revert of the commits: an older build keeps reading the file and renders its own derived order, so the user's order is frozen, not lost.

## Verification

- **Unit tests**: `StarredOrderTest` (index math, no-op, vanished entry), `FavoriteRepositoryTest` (order after a move, failed move persists nothing, order after star/unstar, serialised against a concurrent write), `FavoritesViewModelTest` (action, failure message, shared in-flight guard), `FakeOSMScoutClient` mirrors the native rules (append on star, position dropped on unstar, clamp, negative = first, refusal for unknown/unstarred) and logs calls.
- **Compose tests**: `FavoritesSheetStarredReorderComposeTest` (drag commits the released position, no-op drag commits nothing, a dismissed sheet commits nothing, tap still routes), `auto` `FavoritesScreenTest` (starred mode: one list, stored order, follows a change, empty state).
- **Build**: `./gradlew :app:assembleMobileDebug` and `:app:assembleAutomotiveDebug` (no native rebuild expected — Java-only bridge change), plus `./gradlew test`.
- **On device (phone)**: star three favorites in two groups, drag the last chip to the front, verify the order in the bar, in a reopened sheet and after an app restart; check that a horizontal swipe still scrolls, that a long press released where it started commits nothing and still routes nothing, and that a chip tap still opens the route panel; `adb logcat -s NaviVeylin` clean of new errors. Note the favorites sheet is a **full-screen** composition with a top-app-bar back action, not a `ModalBottomSheet`: there is no drag-down dismissal to check, and `back` is what must dismiss without writing.
- **On device (AAOS AVD)**: open "Starred favorites" and confirm the rows follow the phone's order as one list, and that the list updates in place after a phone-side reorder (the car session stays alive).

## Open Questions

None. The two questions a later session could revisit — the drag vs manage-list fallback (Decision 3) and the drift gate (Decision 1) — are recorded as alternatives and as a TODO item rather than as unknowns blocking the specs or the tasks.
