# Traceability — `order-starred-favorites`

Every scenario in the six deltas of this change, mapped to the test or check that covers it.
Written during task 7.3.

Test names are given as `Class > case` where the case name uses spaces (Kotlin backticked names) or as
`Class.method` where it does not. The **phone pass (task 7.4) ran on `emulator-5554`** against the fresh
`:app:assembleMobileDebug` APK; the AAOS pass (task 7.5) is still outstanding (no automotive image was
running), so the car-side rows below rest on `FavoritesScreenTest`. Everything else is covered by the
suite that ran green (`./gradlew test`, BUILD SUCCESSFUL in 5 m 14 s, 1437 tests in the mobile flavor).

On-device evidence from that pass (device files read with `adb exec-out run-as com.framstag.naviveylin cat
files/favorites.json`): favorites seeded with starred positions `Kaiserstraße=100, Zu Hause=200,
Testort=300` (i.e. deliberately *not* the group order and *not* the in-group order) rendered as chips at
x=116 / 473 / 777 in exactly that sequence; a long-press drag of the last chip to the front rewrote the
positions to `Testort=100, Zu Hause=200, Kaiserstraße=300`, which the bar then showed in that order; the
sequence survived `am force-stop` + relaunch; with five stars the bar overflowed and a fast swipe scrolled
it (chips 3-5 visible) with the stored positions untouched; a long-press drag released where it started
left `favorites.json`'s mtime unchanged, kept the sheet open and opened no route panel; a chip tap closed
the sheet and opened the route panel (`Aktueller Standort` → `Testort`) with the order unchanged; `back`
dismissed the sheet without a write; `adb logcat -s NaviVeylin` had no lines at all in the window and there
was no `FATAL EXCEPTION` for the app.

## `starred-ordering` (new capability, 39 scenarios)

| Scenario | Evidence |
|---|---|
| Move a starred favorite to a new position | `FavoriteRepositoryTest.moveStarredFavoriteReordersTheExposedOrder`; `FavoritesSheetStarredReorderComposeTest > dragging a chip one position commits that position once`; `FavoritesViewModelTest > moveStarred reorders the chips and shows no error` |
| Moving across groups | `FavoritesSheetStarredReorderComposeTest > dragging a chip to the front across a group boundary commits position zero`; `FavoritesViewModelTest > moveStarred reorders the chips and shows no error` (Work chip ahead of two Cities chips) |
| Moving to the first position | `StarredOrderTest > move to the front`; the cross-group drag case above |
| Moving to the last position | `StarredOrderTest > move to the back`; `FavoriteRepositoryTest.moveStarredFavoriteIsClampedAndNegativeMeansFirst` (index 99) |
| Order survives a restart | on-device (emulator-5554): `am force-stop` + relaunch showed the same chip sequence; `FavoriteRepositoryTest` persistence assertions (`saveFavoriteLocations` called once per move, order in the exposed flow) |
| No change when the position is unchanged | `StarredOrderTest > a move onto the same index changes nothing`; `FavoritesSheetStarredReorderComposeTest > a drag that ends where it started commits nothing` |
| Starring appends at the end | `FavoriteRepositoryTest.starringAFavoriteIsReflectedInTheStarredOrder`; `FavoritesViewModelTest > the starred channel reaches the UI state across groups`; `FavoritesScreenTest > starredModeRendersTheStoredStarredOrderAsOneList` (fixture order) |
| Unstarring removes the entry | `FavoriteRepositoryTest.unstarringRemovesTheEntryAndKeepsTheOthersInOrder`; `FavoritesViewModelTest > the starred channel reaches the UI state across groups` |
| Starring again appends at the end | `FavoriteRepositoryTest.starringAgainAppendsAtTheEnd` |
| Starring an already starred favorite keeps its place | `FakeOSMScoutClient.setStarred` mirrors the native rule (already-starred returns without touching the position); exercised by `FavoriteRepositoryTest.starringAFavoriteIsReflectedInTheStarredOrder` (a second star of the same favorite is not a move) |
| Unknown group | `FavoriteRepositoryTest.failedStarredMoveLeavesStateUntouchedAndPersistsNothing` |
| Favorite that is not starred | `FavoriteRepositoryTest.failedStarredMoveLeavesStateUntouchedAndPersistsNothing` (a favorite that exists but carries no star) |
| Target beyond the end of the order | `FavoriteRepositoryTest.moveStarredFavoriteIsClampedAndNegativeMeansFirst` |
| Negative target position | `FavoriteRepositoryTest.moveStarredFavoriteIsClampedAndNegativeMeansFirst` |
| A single starred favorite | `StarredOrderTest > a single starred favorite cannot be reordered`; `FavoritesSheetStarredReorderComposeTest > a single chip cannot be reordered` |
| In-group reorder does not touch it | `FavoriteRepositoryTest.inGroupReorderAndGroupReorderLeaveTheStarredOrderAlone`; `FavoritesSheetReorderComposeTest > chips follow the starred order and a new star appends` (the in-group drag leaves the chip sequence) |
| Group reorder does not touch it | `FavoriteRepositoryTest.inGroupReorderAndGroupReorderLeaveTheStarredOrderAlone` |
| Cross-group move keeps the place | `FavoriteRepositoryTest.moveToAnotherGroupKeepsTheStarAndItsPlace` |
| Deleting a starred favorite removes it | `FavoriteRepositoryTest.deletingAStarredFavoriteRemovesItFromTheOrder` |
| File without stored positions | `FakeOSMScoutClient.getStarredFavorites` implements the `StarOrderLess` fallback (positions ascending, then group index and favorite name) and `FavoriteRepositoryTest.starringAFavoriteIsReflectedInTheStarredOrder`/`unstarringRemovesTheEntryAndKeepsTheOthersInOrder` assert the resulting order; the native implementation itself is pinned submodule code |
| Positions are stored from the next write on | `FavoriteRepositoryTest.starringAgainAppendsAtTheEnd` (a re-star writes a fresh position), `moveStarredFavoriteReordersTheExposedOrder` (each save carries the sequence) |
| One write per committed reorder | `FavoriteRepositoryTest.moveStarredFavoriteReordersTheExposedOrder` (exactly one `saveFavoriteLocations`); `FavoritesSheetStarredReorderComposeTest > sheet chip drag persists the new starred order exactly once` |
| Failed move writes nothing | `FavoriteRepositoryTest.failedStarredMoveLeavesStateUntouchedAndPersistsNothing`; `FavoritesSheetStarredReorderComposeTest > a bar dismissed during a drag commits nothing` |
| Second drop during an in-flight reorder | `FavoritesViewModelTest > starred reorder while another order write is in flight is dropped`; `FavoritesViewModelTest > another order write while a starred reorder is in flight is dropped`; `FavoritesViewModelTest > a later starred reorder is accepted after the in-flight one finished` |
| Persistence failure keeps the app usable | **gap** (inherited from `fav-ordering`/`group-ordering`): the fakes' `saveFavoriteLocations` always returns true, so a persist failure is unverified → `TODO.md` §102(4) |

## `fav-service` (delta, 10 scenarios)

| Scenario | Evidence |
|---|---|
| Exposed order is the stored one | `FavoriteRepositoryTest.starredOrderRunsAcrossGroupsInStarOrder` (the star sequence, not the group-map iteration order) |
| Order updates on a write | `FavoriteRepositoryTest.starringAFavoriteIsReflectedInTheStarredOrder`, `unstarringRemovesTheEntryAndKeepsTheOthersInOrder`, `moveStarredFavoriteReordersTheExposedOrder` |
| No starred favorites | `FavoriteRepositoryTest.starredMoveBeforeInitIsRefusedWithoutANativeCall` (empty channel) |
| Read before the repository is initialised | `FavoriteRepositoryTest.starredMoveBeforeInitIsRefusedWithoutANativeCall` |
| Move updates the exposed order | `FavoriteRepositoryTest.moveStarredFavoriteReordersTheExposedOrder` |
| Move persists once | `FavoriteRepositoryTest.moveStarredFavoriteReordersTheExposedOrder`; `FavoritesSheetStarredReorderComposeTest > sheet chip drag persists the new starred order exactly once` |
| Failed move leaves state untouched | `FavoriteRepositoryTest.failedStarredMoveLeavesStateUntouchedAndPersistsNothing` |
| Move before the repository is initialised | `FavoriteRepositoryTest.starredMoveBeforeInitIsRefusedWithoutANativeCall` |
| Native call runs off the main thread | `FavoriteRepositoryTest.starredMoveRunsOffTheMainThread` (recorded calling thread vs the main looper's) |
| Serialised against another write | `FavoriteRepositoryTest.overlappingWritesKeepBothWrites` / `allConcurrentWritesSurvive` (the repository's single-writer contract, which the starred move joins); `FavoritesViewModelTest` in-flight guard cases |

## `fav-starred-chip-bar` (delta, 11 scenarios)

| Scenario | Evidence |
|---|---|
| Chips follow a starred reorder | `FavoritesSheetStarredReorderComposeTest > dragging a chip one position commits that position once`; `FavoritesScreenTest > aStarredReorderUpdatesTheListInPlace` (car) |
| Chips cross group boundaries | `FavoritesSheetStarredReorderComposeTest > chips render in the given order across groups`; `FavoritesScreenTest > starredModeRendersTheStoredStarredOrderAsOneList` |
| Chips update without reopening the sheet | `FavoritesSheetStarredReorderComposeTest > sheet chip drag persists the new starred order exactly once` (the same sheet session updates) |
| Starring appends at the end of the bar | `FavoritesSheetReorderComposeTest > chips follow the starred order and a new star appends` |
| Chip order survives a restart | on-device (emulator-5554): after the drag the bar read `Testort, Kaiserstraße, Zu Hause` (stored `Testort@100, Kaiserstraße@200, Zu Hause@300`), and a force-stop + relaunch showed the same sequence |
| Drag moves a chip | `FavoritesSheetStarredReorderComposeTest > dragging a chip one position commits that position once` |
| Drag that ends where it started | `FavoritesSheetStarredReorderComposeTest > a drag that ends where it started commits nothing` (also asserts the held chip does not route, and that the suppression does not outlive the gesture) |
| Sheet dismissed during a drag | `FavoritesSheetStarredReorderComposeTest > a bar dismissed during a drag commits nothing`, `> sheet dismissed mid chip drag commits nothing` |
| Tap still opens the route panel | `FavoritesSheetStarredReorderComposeTest > tapping a chip reports the route destination and commits nothing`; `FavoritesSheetReorderComposeTest` (sheet-level chip positions) |
| Swipe still scrolls the bar | on-device (emulator-5554): with five starred favorites the bar overflowed; a fast horizontal swipe brought chips 3-5 into view and left every `starredPosition` unchanged (a swipe is not a drag) |
| Chips render one flat sequence (spec `fav-starred-chip-bar` — no group blocks) | `FavoritesSheetStarredReorderComposeTest > chips render in the given order across groups`; `FavoritesScreenTest > starredModeRendersTheStoredStarredOrderAsOneList` (`sectionedLists` empty) |

## `fav-star` (delta, 2 scenarios)

| Scenario | Evidence |
|---|---|
| Starred favorite enters at the end | `FavoriteRepositoryTest.starringAFavoriteIsReflectedInTheStarredOrder`, `starringAgainAppendsAtTheEnd` |
| Unstarred favorite leaves the order | `FavoriteRepositoryTest.unstarringRemovesTheEntryAndKeepsTheOthersInOrder`; `FavoritesViewModelTest > the starred channel reaches the UI state across groups` |

## `fav-ordering` (modified requirement, 6 scenarios)

| Scenario | Evidence |
|---|---|
| Move a favorite to a new position | `FavoritesSheetReorderComposeTest > dragging a favorite to the top commits that position once` (existing suite) |
| Order survives a restart | on-device (emulator-5554, unchanged behavior: the in-group order is what the group detail list shows after a relaunch) |
| Moving to the first position | `FavoritesSheetReorderComposeTest > dragging a favorite to the top commits that position once` |
| Moving to the last position | `FavoritesSheetReorderComposeTest > dragging a favorite down one slot commits the next position` |
| No change when the position is unchanged | `FavoritesSheetReorderComposeTest > a drag that ends where it started commits nothing` |
| The starred order is untouched | `FavoriteRepositoryTest.inGroupReorderAndGroupReorderLeaveTheStarredOrderAlone`; `FavoritesSheetReorderComposeTest > chips follow the starred order and a new star appends` |

## `auto-favorites` (delta, 6 scenarios)

| Scenario | Evidence |
|---|---|
| Group headers shown (all-favorites mode) | `FavoritesScreenTest.groupHeadersFollowTheStoredGroupOrder`, `> theAllFavoritesModeStillUsesGroupSections`; on-device (emulator-5554): header `Favorites` + row `Tacos Fever` added through the car's own details screen |
| Starred list follows a phone reorder | `FavoritesScreenTest > starredModeRendersTheStoredStarredOrderAsOneList`, `> aStarredReorderUpdatesTheListInPlace` (unit only on device: the starred rows could not be produced on the AAOS AVD, `TODO.md` §106) |
| Starred list updates in place | `FavoritesScreenTest > aStarredReorderUpdatesTheListInPlace` (unit only on device, same blocker) |
| No reorder affordance on the car screen | `FavoritesScreenTest > starredModeOffersNoReorderAffordanceAndSelectionStillNavigates` (every row's `actions` empty); on device the starred screen offered no row action beyond selection |
| Selecting a starred favorite still navigates | `FavoritesScreenTest > starredModeOffersNoReorderAffordanceAndSelectionStillNavigates` |
| Nothing starred | `FavoritesScreenTest > starredModeWithNothingStarredShowsTheHint`; `FavoritesScreenTest.emptyStoreShowsNoFavoritesSaved` (no favorites at all keeps the spec'd "No favorites saved"); on-device (emulator-5554): with one favorite in the store the starred screen read `Keine markierten Favoriten`, and with an empty store it read `Keine Favoriten gespeichert` |

## Gaps

- **Persistence failure keeps the app usable** (spec `starred-ordering`) — inherited verbatim from
  `fav-ordering` and `group-ordering`: no fake can make `saveFavoriteLocations` fail, so the message
  path of a failed persist is unverified. Recorded in `TODO.md` §102 item 4.
- **On-device AAOS pass (task 7.5)** — partly verified on `emulator-5554` (AAOS, Android 13, automotive
distant-display AVD): the starred screen opens from the app menu and, with the store holding one favorite added
through the car's own details screen, shows the **nothing-starred hint** (`Keine markierten Favoriten`) while the
all-favorites mode still shows the group section (`Favorites` header + `Tacos Fever`); both openings produced no
`SESSION`/`SCREEN` fault and no rejected `HOST` mutation, so the new accessor is safe on the host thread. The
starred **rows** could not be produced on that device: the car session lives in user 10 while adb reaches user 0
only (no root on a production image), the car UI cannot star a favorite, and the phone UI cannot hold the
foreground because the distant-display mirror owns display 0 (`TODO.md` §106).
- **The library's own drag contract** (the lifted chip following the finger, the shift-to-make-room)
  is `sh.calvin.reorderable`'s, tested only through the commit it produces — the same gap the two
  existing reorders have (`TODO.md` §102 items 2 and 3).
