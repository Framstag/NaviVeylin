# Traceability — `reorder-favorite-groups`

Every scenario in the four deltas of this change, mapped to the test or check that covers it.
Written during task 5.5. Uncovered scenarios are listed at the bottom and recorded in `TODO.md` §102.

Test names are given as `Class > case` where the case name uses spaces (Kotlin backticked names) or as
`Class.method` where it does not.

## `group-ordering` (new capability, 21 scenarios)

| Scenario | Evidence |
|---|---|
| Move a group to a new position | `FavoriteRepositoryTest.moveGroupReordersExposedStateAndKeepsEveryGroupsFavorites`; `FavoritesSheetGroupReorderComposeTest > dragging a card to the next cell commits that position once`; on-device drag (emulator-5554, AAA moved to the front) |
| Moving a group to the first position | `GroupOrderTest > move to the front`; on-device drag onto the first cell |
| Moving a group to the last position | `GroupOrderTest > move to the back`; `FavoriteRepositoryTest.moveGroupClampsTheTargetIndexAndAcceptsANegativeOne` (index 100) |
| No change when the position is unchanged | `GroupOrderTest > a move onto the same index changes nothing`; `FavoritesSheetGroupReorderComposeTest > a drag that ends where it started commits nothing` |
| Reordered groups after a restart | On-device: force-stop + relaunch, first card still `AAA`, `favorites.json` unchanged |
| A group added after a reorder is appended | On-device: the new group `AAA` was appended last to `favorites.json`; `FavoriteRepositoryTest.allConcurrentWritesSurvive` (group add order) |
| The group grid follows the stored order | `FavoritesViewModelTest > moveGroup reorders the groups and shows no error` (`uiState.groupOrder`); on-device: the first card's detail title read `AAA` after the drag |
| A group used as a single-group default is the first stored group | **gap** → `TODO.md` §102 |
| Reordering groups that hold no favorites | `FavoritesViewModelTest > moveGroup reorders the groups and shows no error` (all-empty groups); on-device: `AAA` was empty when dragged |
| A surface that lists groups renders the new sequence | `FavoritesScreenTest > aGroupReorderUpdatesTheHeadersInPlace` (car); `FavoritesSheetGroupReorderComposeTest` (phone grid) |
| Unknown group | `FavoriteRepositoryTest.moveGroupForAnUnknownGroupFails` |
| Target beyond the end of the order | `FavoriteRepositoryTest.moveGroupClampsTheTargetIndexAndAcceptsANegativeOne` |
| Negative target position | `FavoriteRepositoryTest.moveGroupClampsTheTargetIndexAndAcceptsANegativeOne` |
| Store holding a single group | `GroupOrderTest > a single group cannot be reordered`; `FavoritesSheetGroupReorderComposeTest > dragging the only group commits nothing` |
| Delete preserves relative order | `FavoriteRepositoryTest.deletingAGroupKeepsTheRelativeOrderOfTheRest` |
| Rename keeps the position | `FavoriteRepositoryTest.renamingAGroupKeepsItsPositionInTheOrder` (mirrors native `FavoriteLocationService::RenameGroup`, which renames in place) |
| Favorite and group-attribute changes keep the position | `FavoriteRepositoryTest.aFavoriteChangeDoesNotMoveItsGroup`; `FavoriteRepositoryTest.groupMoveOverlappingAWriteKeepsBothEffects` |
| One write per committed reorder | `FavoriteRepositoryTest.moveGroupPersistsExactlyOnce` |
| Failed move writes nothing | `FavoriteRepositoryTest.failedMoveGroupLeavesStateAndStoreUntouched` |
| Persistence failure keeps the app usable | **gap** → `TODO.md` §102 |
| Second move during an in-flight reorder | `FavoritesViewModelTest > a later group reorder is accepted after the in-flight one finished`, `> group reorder while a reorder is in flight is dropped` |

## `group-grid-display` (4 added requirements, 12 scenarios)

| Scenario | Evidence |
|---|---|
| Drag a group to the first position | `FavoritesSheetGroupReorderComposeTest > dragging a card to the next cell commits that position once`; on-device drag into the first cell |
| Dragged card follows the finger | **gap** (library contract, visual) → `TODO.md` §102 |
| Nothing is persisted during the drag | **gap** (only the release commits is asserted) → `TODO.md` §102 |
| Grid shows the stored order on open | On-device: the first card's detail title matched `favorites.json`'s first group after a relaunch |
| Dragging a group that holds no favorites reorders the grid | On-device (`AAA` was empty); `FavoritesViewModelTest > moveGroup reorders the groups and shows no error` |
| Same-position drag writes nothing | `FavoritesSheetGroupReorderComposeTest > a drag that ends where it started commits nothing` |
| Sheet dismissed during a drag | **gap** → `TODO.md` §102 |
| Dragged group deleted mid-drag | `FavoritesSheetGroupReorderComposeTest > a group that is no longer in the stored order commits nothing` |
| Single group cannot be reordered | `FavoritesSheetGroupReorderComposeTest > dragging the only group commits nothing` |
| Tap opens the group | `FavoritesSheetGroupReorderComposeTest > tapping a card body opens the group and starts no drag`; on-device tap → detail title |
| Menu actions still work | `FavoritesSheetGroupReorderComposeTest > the card menu still opens and runs its actions`; the existing sheet Compose tests stay green |
| Second drop during an in-flight reorder | `FavoritesViewModelTest > group reorder while a reorder is in flight is dropped` |

## `fav-service` (2 added requirements, 10 scenarios)

| Scenario | Evidence |
|---|---|
| The order flow carries the stored sequence | `FavoriteRepositoryTest.moveGroupReordersExposedStateAndKeepsEveryGroupsFavorites`; `FavoriteRepositoryTest.renamingAGroupKeepsItsPositionInTheOrder` |
| A reorder emits on the order flow | `FavoriteRepositoryTest.anOrderOnlyGroupChangeEmitsAStateThatDiffersFromThePreviousOne` (the conflation case this requirement exists for) |
| Order and contents are refreshed together | `FavoriteRepositoryTest.emittedGroupOrderMatchesTheNativeGroupOrder` |
| Move updates the exposed state | `FavoriteRepositoryTest.moveGroupReordersExposedStateAndKeepsEveryGroupsFavorites`; `FavoritesViewModelTest > moveGroup reorders the groups and shows no error` |
| Move preserves the favorites of every group | `FavoriteRepositoryTest.moveGroupReordersExposedStateAndKeepsEveryGroupsFavorites` (per-group assertions) |
| Move persists once | `FavoriteRepositoryTest.moveGroupPersistsExactlyOnce` |
| Failed move leaves state untouched | `FavoriteRepositoryTest.failedMoveGroupLeavesStateAndStoreUntouched` |
| Move before the repository is initialised | `FavoriteRepositoryTest.moveGroupBeforeInitReturnsFalse` |
| Native call runs off the main thread | `FavoriteRepositoryTest.moveGroupRunsOffTheMainThread` |
| Group move takes part in write serialisation | `FavoriteRepositoryTest.groupMoveOverlappingAWriteKeepsBothEffects` |

## `auto-favorites` (1 added requirement, 6 scenarios)

| Scenario | Evidence |
|---|---|
| AA headers follow a phone reorder | `FavoritesScreenTest > groupHeadersFollowTheStoredGroupOrder` |
| AA headers update in place | `FavoritesScreenTest > aGroupReorderUpdatesTheHeadersInPlace` |
| An order-only change reaches the car | `FavoritesScreenTest > aGroupReorderUpdatesTheHeadersInPlace` (same map instance, only the order moves) |
| A group the order does not name yet still appears | `FavoritesScreenTest > aGroupTheOrderDoesNotNameStillAppears`; `GroupOrderingTest > a group the order does not name is appended, not dropped` |
| No reorder affordance for groups on the car screen | `FavoritesScreenTest > rowsOfferNoReorderAffordanceAndSelectionStillNavigates`; no drag/move action exists in `FavoritesScreen`'s template |
| Group order change does not disturb the favorite order | `FavoritesScreenTest > listFollowsTheStoredFavoriteOrder` with `> aGroupReorderUpdatesTheHeadersInPlace` |

## Also verified, not a scenario

- Both flavors build with **no warnings** for all three ABIs (`:app:assembleMobileDebug :app:assembleAutomotiveDebug`).
- `./gradlew test` green for `:core`, `:auto` and both `:app` flavors (after the flake in `TODO.md` §101 was
  addressed in the new test's own drag distances).
- On-device: no crash, and no log line carrying a coordinate (`adb logcat -d` filtered on the app's tags).
- `TODO.md` §79 (bridge override trails the submodule) lost `moveGroup` from its list of missing natives.

## Uncovered scenarios

1. `group-ordering` — A group used as a single-group default is the first stored group
2. `group-grid-display` — Dragged card follows the finger
3. `group-grid-display` — Nothing is persisted during the drag
4. `group-grid-display` — Sheet dismissed during a drag
5. `group-ordering` — Persistence failure keeps the app usable
