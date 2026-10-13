# Proposal — fix-car-search-history-cap

## Why

Root cause: `SearchHistoryScreen` builds one `Row` per entry `historyProvider.load()` returns into a
single `ItemList` and never asks the connected host for its row capacity, so the car search history
holds every stored search — the store keeps up to `MAX_ENTRIES` = 50 — while the Car App Library's build
contract has the host ignore every row past its limit with no signal; the library's own default for a
list is 6.

Evidence:    `com.naviveylin.auto.SearchHistoryScreenTest#theSearchHistoryListFitsTheHostsCapacity` fails
on HEAD — XML `tests="2" skipped="0" failures="2" errors="0"`,
`timestamp="2026-10-13T20:37:54.926Z"`, failure
`java.lang.AssertionError: expected:<[query-1, query-2, query-3, query-4, query-5, query-6, query-7, query-8, More (3)]> but was:<[query-1, query-2, query-3, query-4, query-5, query-6, query-7, query-8, query-9, query-10, query-11]>`.
The same run's second case, `thePageRowOpensTheNextSliceOfTheHistory`, fails as
`java.lang.AssertionError: no row titled More (3) in [query-1, query-2, query-3, query-4, query-5, query-6, query-7, query-8, query-9, query-10, query-11]`.
Retained as `evidence/TEST-SearchHistoryScreenTest-red-on-HEAD.xml`. The fake host in the first case
reports a capacity of 8, i.e. deliberately **not** the library default of 6, so the expected rows and the
`More (3)` count cannot be satisfied by a screen that ignores the host and uses its own fallback.

Repro:       `./gradlew :auto:testDebugUnitTest --tests "com.naviveylin.auto.SearchHistoryScreenTest" -PforceTests --rerun-tasks`

Spec:        `car-list-row-limit` / A car list never presents more rows than the host displays
(added by `car-favorites-group-level`; its requirement text already reads "A car list screen", so the
search history was in breach of it while the capability was applied to the favourites lists and, by
`2026-10-13-fix-car-list-row-capacity`, to the address book)
             Guideline: `guidelines/UI.md` §3 "Car-host template constraints (platform knowledge)", the
`ListTemplate` bullet — "A car list screen MUST ask the host for the capacity and present **at most** that
many rows, ending with its own page row, because rows past the limit disappear with no signal".

Authority — the tree today:

- **The screen renders every stored search.** `auto/src/main/java/com/naviveylin/auto/SearchHistoryScreen.kt:50-70`
  adds one `Row` per entry of `entries` into one `ItemList.Builder`, and the file contains no reference to
  `CarListCapacityReader`, no page parameter and no page row.
- **The store's bound is 50.** `app/src/main/java/com/naviveylin/data/SearchHistoryRepository.kt:151`
  (`const val MAX_ENTRIES = 50`), the repository the `:app`-implemented
  `core/src/main/java/com/naviveylin/core/AutoSearchHistoryProvider.kt` reads.
- **The shipped seam exists and is screen-generic.** `auto/src/main/java/com/naviveylin/auto/CarListCapacityReader.kt:50`
  (`read(carContext)`, `LIBRARY_DEFAULT = 6` at `:40` — the library's packaged
  `androidx.car.app.R.integer.content_limit_list`) and
  `auto/src/main/java/com/naviveylin/auto/FavoritesScreenMapper.kt:49` (`page(rows, capacity, page)`) are
  pure functions of the host's answer and an ordered sequence; `FavoritesScreen.kt:227` applies them and
  adds the final row at `:237-239` with the wording `R.string.more_items`
  (`auto/src/main/res/values/strings.xml:66`), and `AddressBookScreen.kt:168`/`:179` applies the same
  seam to the address book (`pageRow` at `:193`).
- **The capacity facts.** The AAOS templates host measured **100 rows at car API level 8**
  (`car-favorites-group-level` task 1.1); the library's packaged default and this app's fallback is **6**
  (`CarListCapacityReader.kt:40`);
  the library's contract is that the host ignores every item over the limit (`guidelines/UI.md` §3).

## What Changes

- **The car search history caps its list at the host's row capacity and pages the remainder.** The screen
  asks the connected host for its `CONTENT_LIMIT_TYPE_LIST` capacity once per screen, off the host thread,
  presents at most that many entry rows and ends with the clickable `More (n)` row that opens the next
  slice of the same list — the shipped `CarListCapacityReader` + `FavoritesScreenMapper.page` +
  page-row seam, the same one `FavoritesScreen` and `AddressBookScreen` apply. Nothing new is introduced:
  this is the existing capability `car-list-row-limit` applied to one more car list.
- **The page row pushes the screen with a page index.** `page: Int = 0`, following `AddressBookScreen`
  (its design D3): the row pushes another `SearchHistoryScreen` carrying `page + 1`, so the host's own back
  navigation returns to the page that opened it.
- **The screen keeps its shape.** Still a `ListTemplate` with the same header and rows, still one file, no
  call-site change: `MapScreen.kt:579` constructs it without the page argument, whose default is page 0. No
  empty-state change, no new string (the `More (n)` wording exists in both locales).
- **Not this change:** the two sibling entries of `TODO.md` §177 stay open with their reasons — §179
  (`PoiResultsScreen`) needs one combined decision (the search's candidate count and the row cap are one
  design decision, as its own entry says); §180 (`DiagnosticsScreen`) is a `PaneTemplate`, i.e. a different
  mechanism, and its entry asks for an extra shown-of-total wording. Applying the capability at those sites
  would be two further deltas, not this one. The store's `MAX_ENTRIES = 50` in `:app` is the phone's
  history, which is correct there and is not touched.
- **Guideline and spec**: `guidelines/UI.md` §3 states the rule screen-generically and names the shipped
  implementation; nothing in it contradicts the fix, so no guideline edit is owed (stated as a task's
  verification, not assumed). `car-list-row-limit` gains the search-history scenarios it was already
  claimed to cover.

## Capabilities

### New Capabilities

(none)

### Modified Capabilities

- `car-list-row-limit`: the two existing requirements are restated with the scenarios the search history now
  satisfies, and each gains the search-history scenario naming its case. The requirements' claims are
  unchanged — the defect is the search-history screen, not the contract. The scenarios this fix does not
  re-deliver (favourites, address book, the back-navigation scenario that names no case) keep the baseline's
  claim strength from `car-favorites-group-level` and `fix-car-list-row-capacity`, and are exercised by
  their existing cases.

## Impact

- `auto/src/main/java/com/naviveylin/auto/SearchHistoryScreen.kt` — one additive constructor parameter
  (`page`, defaulted) plus the capacity read, the page slice and the page row (one file).
- `auto/src/test/java/com/naviveylin/auto/SearchHistoryScreenTest.kt` — new; the red case and the page case.
- `openspec/specs/car-list-row-limit/spec.md` — via this change's delta; `TODO.md` §178 → `fixed-by`.
- No change to `:core`, `:app`, resources, JNI/native code, the manifest or the AAOS flavour; no new
  dependency, no new string.
- Additive and non-breaking: the existing constructor call site keeps its behaviour (page 0). Rollback:
  revert the one production file and drop the new test file; the delta spec and the `TODO.md` status ride
  with the change.
