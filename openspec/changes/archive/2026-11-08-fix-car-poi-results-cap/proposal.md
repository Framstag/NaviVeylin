# Proposal — fix-car-poi-results-cap

## Why

Root cause: `PoiResultsScreen` renders every row the POI walk returns into one `ItemList` — no capacity read,
no page row (`auto/src/main/java/com/naviveylin/auto/PoiResultsScreen.kt:198`) — and asks the walk for exactly
the same fixed number it renders (`MAX_RESULTS = 20`, `:239`, passed as the candidate count at `:159`), so
both the rows a driver can reach and the candidates fetched are decided by a constant: on a host that shows
fewer than 20 rows the tail is dropped with no signal (spec `car-list-row-limit` — the host "will ignore any
items over that limit", `guidelines/UICar.md` §3), and the candidate rule `auto-search` states
(`openspec/specs/auto-search/spec.md:62` — "at least twice the displayed maximum") is under-asked, because 20
candidates are what 20 rows display.

Evidence:    `com.naviveylin.auto.PoiResultsScreenTest` fails on HEAD — XML `tests="3" skipped="0"
failures="3" errors="0"`, `timestamp="2026-11-08T01:10:21.004Z"`:
- `thePoiResultsListFitsTheHostsCapacity`:
  `java.lang.AssertionError: expected:<[POI 1, …, POI 8, More (8)]> but was:<[POI 1, …, POI 20]>`
- `thePoiSearchAsksForTwiceTheHostsCapacity`:
  `java.lang.AssertionError: the walk is asked for twice the host's capacity expected:<[16]> but was:<[20]>`
- `thePageRowCarriesTheAlreadyFetchedRows`:
  `java.lang.AssertionError: no row titled More (8) in [POI 1, …, POI 20]`
Retained as `evidence/TEST-PoiResultsScreenTest-red-on-HEAD.xml`. The fake host in the first case reports a
capacity of 8, deliberately **not** the library default of 6, so the expected row slice and the count in the
page row cannot be satisfied by a screen that ignores the host's answer and uses its own fallback (it would
show `POI 1 … POI 6` with `More (10)`).

Repro:       `./gradlew :auto:testDebugUnitTest --tests "com.naviveylin.auto.PoiResultsScreenTest" -PforceTests --no-build-cache --rerun-tasks`

Spec:        `car-list-row-limit` / A car list never presents more rows than the host displays, Items beyond
the capacity stay reachable — the requirement already reads "A car list screen", so the POI results list was
in breach of it while the capability was applied to the favourites lists, the address book and the search
history; and `auto-search` / Search result limit (`openspec/specs/auto-search/spec.md:62`).
             Guideline: `guidelines/UICar.md` §3 "Car-host template constraints (platform knowledge)", the
`ListTemplate` bullet — "A car list screen MUST ask the host for the capacity and present **at most** that
many rows, ending with its own page row, because rows past the limit disappear with no signal".

surface: 3 cases added, 0 rewritten, 1 fixture realigned (the fake client records and honours the candidate count it is asked for), 2 capability deltas — `./tools/look.sh 'twice the displayed maximum|MAX_RESULTS = 20'` (10 matches in 10 files: the constants and the four spec/change texts that state the candidate rule) and `./tools/look.sh 'searchPOIsByTypes'` (63 matches in 30 files, of which one production call site, one fake and one case file are this change's).

## Authority — the tree at this change's base commit

Line numbers below are the base commit's (before this diff); the `## What Changes` section names the lines the
diff writes.

- **The screen renders every fetched row.** `auto/src/main/java/com/naviveylin/auto/PoiResultsScreen.kt:198`
  adds one `Row` per entry of `results` into one `ItemList.Builder`; the file contains no reference to
  `CarListCapacityReader`, no page parameter and no page row before this change.
- **The fetch and the display are one constant.** `PoiResultsScreen.kt:239` (`private const val
  MAX_RESULTS = 20`), passed as the candidate count at `:159` and rendered whole at `:198`.
- **The shipped seam is screen-generic.**
  `auto/src/main/java/com/naviveylin/auto/CarListCapacityReader.kt:50` (`read(carContext)`, `LIBRARY_DEFAULT =
  6` at `:40`) and `auto/src/main/java/com/naviveylin/auto/FavoritesScreenMapper.kt:49`
  (`page(rows, capacity, page)`) are pure functions of the host's answer and an ordered sequence;
  `SearchHistoryScreen.kt:98` applies them (its capacity read on the screen's own scope, `pageRow` at `:132`
  with the wording `R.string.more_items`, `auto/src/main/res/values/strings.xml:72`), as `FavoritesScreen`
  and `AddressBookScreen` do.
- **The capacity facts.** The AAOS templates host measured **100 rows at car API level 8**
  (`car-favorites-group-level` task 1.1); the library's packaged default and this app's fallback is **6**
  (`CarListCapacityReader.kt:40`).
- **The recorded decision.** Issue #113's `ITEM_DECISION` (2026-11-06) answered the open pair — the row cap
  and the fetch budget — with its **Option 2**: the row cap becomes the host's reported capacity, the fetch
  budget twice that, the remainder behind the page row on already-fetched rows, one capability delta on
  `car-list-row-limit`, and the spec's "display at most 20 results" line amended.

## What Changes

- **The POI results list caps at the host's row capacity and pages the remainder.** The screen asks the
  connected host for its `CONTENT_LIMIT_TYPE_LIST` capacity once per screen, off the host thread, presents at
  most that many result rows and ends with the clickable `More (n)` row that opens the next slice of the same
  list — the shipped `CarListCapacityReader` + `FavoritesScreenMapper.page` + page-row seam. Nothing new is
  introduced: this is the existing capability `car-list-row-limit` applied to one more car list.
- **The walk is asked for twice the rows a page shows.** `fetchBudget = 2 * rowCapacity`, replacing the
  constant that was both the candidate count and the render bound (the decision's Option 2, and the floor
  `auto-search`'s "Search result limit" already states). The capacity is read before the request is issued,
  because the budget is derived from it.
- **The page row carries the rows already fetched, so no second native walk is paid for.** The pushed page is
  another `PoiResultsScreen` with `prefetched` set to the rows the opener holds and `page + 1`; it slices them
  instead of searching again, and the host's back navigation returns to the page that opened it (the push is
  the same mechanism `SearchHistoryScreen` and `AddressBookScreen` use).
- **The constants go.** `MAX_RESULTS` is deleted: the screen no longer carries a result count of its own.
  `SEARCH_RADIUS_M` stays.
- **Not this change:** `PoiSearchScreen.kt:54-75` builds one row per supported POI category into the same kind of
  `ItemList` with no capacity read — the same defect, one screen away, and outside this item; named as a `NOTE:`
  in the report so the reviewer meets it named. The phone's POI sheet (`openspec/specs/poi-search`) is not a car
  list and is untouched. `SearchScreenMapper.MAX_RESULTS = 20` remains the ranked car search template's own
  maximum, which the amended `auto-search` requirement still states and this change does not move.
- **Guideline and spec**: `guidelines/UICar.md` §3 states the capacity rule screen-generically, names the
  shipped implementation and is not contradicted by this fix, so no guideline edit is owed (verified in tasks
  4.1, not assumed). `car-list-row-limit` gains the POI scenarios it was already claimed to cover;
  `auto-search`'s "Search result limit" is restated once, because its "at most 20 results" is the line the
  owner's decision named.

## Capabilities

### New Capabilities

(none)

### Modified Capabilities

- `car-list-row-limit`: the two existing requirements are restated with the two POI-result scenarios and their
  cases. The requirements' claims are unchanged — the defect is the screen, not the contract. The scenarios
  this fix does not re-deliver (favourites, address book, search history, the back-navigation scenario that
  names no case) keep the baseline's claim strength and are exercised by their existing cases.
- `auto-search`: "Search result limit" is restated so the 20 is the ranked car search template's maximum and a
  car POI category result list takes both its row cap and its candidate count from the host's capacity, with
  one new scenario naming the fetch-budget case. The requirement's two existing scenarios keep the baseline's
  claim strength (neither names a case).

## Impact

- `auto/src/main/java/com/naviveylin/auto/PoiResultsScreen.kt` — two additive constructor parameters
  (`prefetched`, `page`; both defaulted), the capacity read and the capacity-derived fetch budget, the page
  slice and the page row; `MAX_RESULTS` deleted (one file).
- `auto/src/test/java/com/naviveylin/auto/PoiResultsScreenTest.kt` — new; the three cases.
- `auto/src/test/java/com/framstag/libosmscout/client/FakeMapScreenClient.kt` — the double records the
  candidate count it was asked for and caps its answer at it; no other case's expectation moves (the only
  other caller passes a count its fixture does not exceed).
- `openspec/specs/car-list-row-limit/spec.md`, `openspec/specs/auto-search/spec.md` — via this change's delta.
- No change to `:core`, `:app`, resources, JNI/native code, the manifest or the AAOS flavour; no new
  dependency, no new string.
- Additive and non-breaking: `PoiSearchScreen.kt:65` keeps calling the four-argument constructor, whose new
  parameters default to "first page, search afresh". Rollback: revert the one production file, its test file
  and the fake client's added lines; the delta specs ride with the change.
