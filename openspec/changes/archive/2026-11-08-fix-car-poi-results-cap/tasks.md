# Tasks

Parent specs: `car-list-row-limit` (MODIFIED — "A car list never presents more rows than the host displays",
"Items beyond the capacity stay reachable") and `auto-search` (MODIFIED — "Search result limit"). Order:
reproduce, cap and page, take the fetch budget from the host, falsify both bounds, close the scenario-to-case
map, bookkeeping. The item's gate is deferred: the run's gate child runs it after the reviewer, so no task here
runs `./tools/loop/gate.sh`.

## 1. Reproduce the defect (red on HEAD)

- [x] 1.1 Write the three cases in a new `auto/src/test/java/com/naviveylin/auto/PoiResultsScreenTest.kt`,
  harness following `PoiResultsScreenSearchLifecycleTest` (the provider seam) and `SearchHistoryScreenTest`
  (the fake host answering `getContentLimit`): `thePoiResultsListFitsTheHostsCapacity` (host reports **8**,
  deliberately not the library default of 6, so both the slice and the `More (n)` count must follow the host),
  `thePoiSearchAsksForTwiceTheHostsCapacity` (the candidate count the walk is asked for is `2 × 8 = 16`) and
  `thePageRowCarriesTheAlreadyFetchedRows` (the page the row opens slices the opener's rows and pays for no
  second walk). The fake client records the candidate count and caps its answer at it, so a walk cannot return
  more than it was asked for. Specs: `car-list-row-limit` — The POI results list fits the host's capacity, The
  POI results page continues the searched rows; `auto-search` — The POI walk is asked for twice the rows the
  host shows. Verification: the red-on-HEAD run is retained as
  `openspec/changes/fix-car-poi-results-cap/evidence/TEST-PoiResultsScreenTest-red-on-HEAD.xml` —
  `tests="3" skipped="0" failures="3" errors="0"`, `timestamp="2026-11-08T01:10:21.004Z"`, the cases failing as
  `expected:<[POI 1, …, POI 8, More (8)]> but was:<[POI 1, …, POI 20]>`,
  `the walk is asked for twice the host's capacity expected:<[16]> but was:<[20]>`, and
  `no row titled More (8) in [POI 1, …, POI 20]`; command
  `./gradlew :auto:testDebugUnitTest --tests "com.naviveylin.auto.PoiResultsScreenTest" -PforceTests --no-build-cache --rerun-tasks`.

## 2. Cap the list at the host's capacity and page the remainder

- [x] 2.1 Add `rowCapacity` (started at `CarListCapacityReader.LIBRARY_DEFAULT`), read it on the screen's own
  scope off the host thread (`withContext(ioDispatcher) { CarListCapacityReader.read(carContext) }`), slice
  the rows with `FavoritesScreenMapper.page(results, capacity = rowCapacity, page = page)` and add the
  clickable final row (`R.string.more_items`) pushing another `PoiResultsScreen` with `page + 1` and the
  opener's rows as `prefetched`, which the pushed screen slices instead of searching again. `MAX_RESULTS` is
  deleted. Spec: `car-list-row-limit` — The POI results list fits the host's capacity; The POI results page
  continues the searched rows. Verification:
  `thePoiResultsListFitsTheHostsCapacity` asserts one capacity query per screen start and none across three
  later template builds; `thePageRowCarriesTheAlreadyFetchedRows` asserts the page's rows and
  `client.poiSearchGenerations.size == 1`.
  — ran 2026-11-08: the field is `PoiResultsScreen.kt:114`, the slice `:238` and the page row `:288`; the three
  cases passed (`tests="3" skipped="0" failures="0" errors="0"`,
  `timestamp="2026-11-08T01:11:33.512Z"`, forced), retained as
  `evidence/TEST-PoiResultsScreenTest-green-focused.xml`. The screen's pre-existing class stayed green in the
  same run (`PoiResultsScreenSearchLifecycleTest`, `tests="3" failures="0" errors="0"`,
  `timestamp="2026-11-08T01:11:26.609Z"`), retained as
  `evidence/TEST-PoiResultsScreenSearchLifecycleTest-green-focused.xml`.
- [x] 2.2 Take the walk's candidate count from the host's answer: `fetchBudget = 2 * rowCapacity`
  (`PoiResultsScreen.kt:121`), passed at `:199`; the capacity is read **before** the request is issued (`:145`)
  because the budget derives from it, so the page and the page its row opens are always inside the fetched set.
  Spec: `auto-search` — The POI walk is asked for twice the rows the host shows. Verification:
  `thePoiSearchAsksForTwiceTheHostsCapacity` asserts `client.poiSearchCandidateCounts == [16]` with the host
  reporting 8; green in the forced run quoted in 2.1.

## 3. Falsify the two bounds the fix introduces

- [x] 3.1 Revert-check, mutation 1 (`.pi/skills/revert-check`, one mutation per run): remove the row bound —
  pass `capacity = results!!.size` to `FavoritesScreenMapper.page` at `PoiResultsScreen.kt:238`, marked with a
  `REVERT-CHECK MUTATION` comment — and confirm the protected cases fail for the right reason. Restore, confirm
  no marker is left, then re-run forced. Verification: mutation XML
  `evidence/TEST-PoiResultsScreenTest-revert-check-capacity-mutation.xml` —
  `tests="3" skipped="0" failures="2" errors="0"`, `timestamp="2026-11-08T01:11:58.887Z"`, failing as
  `expected:<[POI 1, …, POI 8, More (8)]> but was:<[POI 1, …, POI 16]>` and
  `no row titled More (8) in [POI 1, …, POI 16]` (the budget case passes: it asserts the asked-for count, which
  this mutation does not touch); `./tools/look.sh 'REVERT-CHECK MUTATION' --path
  auto/src/main/java/com/naviveylin/auto/PoiResultsScreen.kt` → `0 matches` after the restore.
- [x] 3.2 Revert-check, mutation 2: make the budget a constant again (`fetchBudget = 20`, marked), and confirm
  all three cases fail for the right reason. Restore, confirm no marker is left, then re-run forced. Verification:
  mutation XML `evidence/TEST-PoiResultsScreenTest-revert-check-fetch-budget-mutation.xml` —
  `tests="3" skipped="0" failures="3" errors="0"`, `timestamp="2026-11-08T01:12:25.401Z"`, failing as
  `the walk is asked for twice the host's capacity expected:<[16]> but was:<[20]>`,
  `expected:<[POI 1, …, POI 8, More (8)]> but was:<[POI 1, …, POI 8, More (12)]>` and
  `no row titled More (8) in [POI 1, …, POI 8, More (12)]`.
- [x] 3.3 The restored green run, forced, its XML `timestamp` after the restore: `tests="3" skipped="0"
  failures="0" errors="0"`, `timestamp="2026-11-08T01:12:47.117Z"`, retained as
  `evidence/TEST-PoiResultsScreenTest-green-after-restore.xml`. The working tree carries no
  `REVERT-CHECK MUTATION` marker (look.sh: 0 matches), and the tracked files `git diff --stat` lists are this
  change's `PoiResultsScreen.kt` and the fake client (the new test file is untracked at this point).

## 4. Close the scenario-to-case map and the ownership claims

- [x] 4.1 Every scenario in this change's two delta specs names a case that exists: the two new
  `car-list-row-limit` scenarios name `PoiResultsScreenTest` cases, the new `auto-search` scenario names the
  fetch-budget case, and the restated baseline scenarios keep the cases the baseline specs name. The
  back-navigation scenario carries no `Case:` line, as in the baseline (the push of 2.1 delivers the mechanism;
  no case in this change asserts it). Verification: `openspec validate fix-car-poi-results-cap --strict` exits
  0, and every `**Case**:` line resolves to a `fun <case>(` in the named class's file under `auto/src/test`.
- [x] 4.2 The owning guideline section is `guidelines/UICar.md` §3 "Car-host template constraints (platform
  knowledge)", the `ListTemplate` bullet: it states the capacity rule screen-generically and names the shipped
  implementation this diff reuses, so it owes no edit and the change carries none. Stated and verified in
  `design.md` ("Guideline and spec ownership"), together with what the two other statements of the candidate
  rule (`search-result-ranking:123`, `location-search:29`) bound. Verification:
  `./tools/look.sh '^## ' --path guidelines/UI.md` lists the file's current sections (1, 6, 8d, 9, 10, 20 — no
  car-template section), and `guidelines/UICar.md` §3's bullet already reads "A car list screen MUST ask the
  host for the capacity and present **at most** that many rows, ending with its own page row".

## 5. Bookkeeping

- [x] 5.1 Name the sibling defect instead of absorbing it: `PoiSearchScreen.kt:54-75` builds one row per
  supported POI category into the same kind of `ItemList` with no capacity read, so on a host that displays
  fewer rows than the category set has, its tail is dropped. It is a second screen and a second delta, outside
  this item; the report carries it as a `NOTE:` for the reviewer. Verification: the line range holds the loop
  and the `addItem` call, and this change's diff does not touch that file.
- [x] 5.2 The item's gate is deferred to the run's gate child (the run's worker prompt ends this session at the
  diff): no `./tools/loop/gate.sh` run is recorded here, and the evidence above is the focused `:auto` runs this
  change's pathspec owes before that gate.
