# Tasks

## 1. Reproduce the defect as failing tests

- [x] 1.1 Add `historyChipReplayAfterSelectionPublishesResults` to
  `app/src/test/java/com/naviveylin/ui/map/MapCanvasViewModelSearchHistoryTest.kt`: search "Bochum",
  await results, `onSearchResultSelected(...)`, `onHistoryEntrySelected("Bochum")`,
  `advanceTimeBy(300)`, assert `uiState.searchResults` is not empty. Verification: the test FAILS on
  the current code with an empty `searchResults`; record the failure output. (spec: `search-history`
  — History selection replays the search)
- [x] 1.2 Add `repeatedQueryTextRunsAgain` to
  `app/src/test/java/com/naviveylin/ui/map/MapCanvasViewModelFreeTextSearchTest.kt`: two identical
  `onSearchQueryChanged("Bochum")` calls separated by more than the debounce, assert the fake client
  saw two `searchLocations` calls. Verification: the test FAILS on the current code (second request
  dropped). (spec: `location-search` — A repeated query text still runs the search)

## 2. One search trigger that carries the request

- [x] 2.1 Introduce `private data class SearchRequest(val query: String, val seq: Long)` in
  `app/src/main/java/com/naviveylin/ui/map/MapCanvasViewModel.kt` and replace `_searchQueryFlow` with a
  `MutableStateFlow<SearchRequest>`; the pipeline at `MapCanvasViewModel.kt:1079-1098` keeps
  `debounce(300)` and the `length >= 2 || isEmpty` filter but runs `mergeSearchResults(request.query)`.
  Verification: `./gradlew :app:testMobileDebugUnitTest --tests "com.naviveylin.ui.map.MapCanvasViewModel*"`
  is green and task 1.2 now passes.
- [x] 2.2 Route every query writer through the single publish path: `onSearchQueryChanged`,
  `onHistoryEntrySelected` (delegates to `onSearchQueryChanged`), `clearSearch`, and
  `onSearchResultSelected` (which must not leave a trigger value behind). Verification: task 1.1
  passes and the focused suite stays green.
- [x] 2.3 Add `sameTextFromSharedLocationStillRuns` — placed as `shared address repeating the
  committed query still runs the search` in
  `app/src/test/java/com/naviveylin/ui/map/MapCanvasViewModelSharedLocationTest.kt` (moved: only that class
  holds the `SharedLocationHandler` field and the `initMap()` helper this scenario needs; the covered
  behaviour is unchanged): search "Bochum", select a result, then publish a shared location whose query is "Bochum", assert
  results are published. Verification: test green (6 tests / 0 failures). (spec: `location-search` — Same text arriving from
  another entry point)
- [x] 2.4 Log one coordinate-free line per request (`search request: chars=<n> seq=<n>`, numbers only).
  Verification: the `checkNoCoordinatesInLogs` build gate passes in `preBuild` and the line is
  observable in logcat during 5.1.
- [x] 2.5 Verify no route-panel regression: `./gradlew :app:testMobileDebugUnitTest --tests "com.naviveylin.ui.route.*"`
  green (the route panel keeps its own search job).

## 3. One history entry per search text

- [x] 3.1 In `app/src/main/java/com/naviveylin/data/SearchHistoryRepository.kt`, make `record()` drop an
  existing entry with the same text before prepending, inside the existing mutex. Verification: new
  test `repeatingSearchMovesEntryToFront` (search "Essen", then "Bochum", then "Bochum"; assert one
  "Bochum" entry, first) plus the existing cap test stay green; run
  `./gradlew :app:testMobileDebugUnitTest --tests "*SearchHistory*"`. (spec: `search-history` — No
  duplicate entry for the same search text)
- [x] 3.2 Collapse repeated texts in `load()` (keep the newest date, preserve youngest-first order) and
  persist only when the collapse changed the list. Verification: new test
  `loadCollapsesPersistedDuplicates` seeds a `maps/search_history.json` with two "Bochum" entries and
  asserts one remains with the newer date, and that an unchanged file is not rewritten. (spec:
  `search-history` — Duplicate entries are collapsed when the history is loaded)
- [x] 3.3 Assert the chip row receives each text once: `committingTheSameSearchThreeTimesYieldsOneChip`
  in `MapCanvasViewModelSearchHistoryTest.kt` asserts the list the chip row renders (the store's
  history flow) holds one "Bochum" entry after three commits. No compose-level duplicate case was
  added: the contract is one entry per text in the store, and a UI-level dedupe was explicitly rejected
  (design D2). Verification: test green (6 tests / 0 failures).

## 4. Falsify every new invariant, then gate

- [x] 4.1 Revert-check for the repeated-request invariant (task 1.2's guard). Triple named before the
  run: invariant = a request repeating the previous query text still runs; mutation = the sequence no
  longer advances (`SearchRequest(query, _searchRequest.value.seq)`); case that must fail =
  `MapCanvasViewModelFreeTextSearchTest.repeatedQueryTextRunsAgain`. — ran 2026-10-04: baseline (all
  three classes, forced) 110 tasks executed, 18 tests / 0 failures; with the mutation 25 tasks executed,
  `FreeTextSearchTest` tests="6" failures="1" — `message="java.lang.AssertionError: expected:<[Bochum,
  Bochum]> but was:<[Bochum]>"`, SearchHistoryTest and SharedLocationTest stayed green (finding: the
  chip-replay case is NOT sensitive to this mutation, because the sync path already makes the chip's
  text differ from the stale trigger — hence the separate single-mutation run 4.1b below); restored
  (`grep -c 'REVERT-CHECK MUTATION'` → 0), re-ran forced → 110 tasks executed, 18 tests / 0 failures,
  fresh timestamps 16:09:11–16:09:12Z.
- [x] 4.1b Revert-check for the chip-replay invariant (task 1.1's guard), second single mutation.
  Triple: invariant = tapping a recent-search chip runs the search; mutation = `onHistoryEntrySelected`
  fills the box without publishing a request (the pre-change behaviour); case that must fail =
  `MapCanvasViewModelSearchHistoryTest.historyChipReplayAfterSelectionPublishesResults`. — ran
  2026-10-04: with the mutation 20 tasks executed, `SearchHistoryTest` tests="6" failures="1" —
  `message="java.lang.AssertionError: tapping the chip must replay the search and publish its results"`,
  the other two classes green; restored (marker count 0), re-ran forced → 110 tasks executed, 18 tests /
  0 failures, fresh timestamps 16:11:33–16:11:43Z.
- [x] 4.2 Revert-check for move-to-front (task 3.1/3.3 guard). Triple: invariant = recording an existing
  text moves that entry to the front instead of duplicating it; mutation = unconditional prepend in
  `record()`; cases that must fail = `repeatingSearchMovesEntryToFront` and
  `committingTheSameSearchThreeTimesYieldsOneChip`. — ran 2026-10-04: baseline forced 110 executed,
  repo tests="10" and VM tests="6", 0 failures; with the mutation repo failure
  `expected:<[Bochum, Essen]> but was:<[Bochum, Essen, Bochum]>` while the VM case stayed green — its
  `history.first { it.isNotEmpty() }` sampled the first of the three writes, so the case was rewritten
  to read the settled state (`advanceUntilIdle()` + `history.value`), re-run with the same mutant →
  `expected:<1> but was:<3>`, both cases red; restored (marker count 0), re-ran forced → 110 tasks
  executed, repo tests="10" / VM tests="6", 0 failures, fresh timestamps 16:18:20–16:18:32Z.
- [x] 4.3 Revert-check for the load collapse (task 3.2 guard). Triple: invariant = a stored file with the
  same text twice collapses on load; mutation = skip the collapse at the call site
  (`val collapsed = entries`); cases that must fail = `loadCollapsesPersistedDuplicates`,
  `collapseKeepsTheNewestDateOfAnOutOfOrderFile`, `collapseIsPersistedAndACleanFileIsNotRewritten`. —
  ran 2026-10-04: with the mutation repo tests="10" failures="2" (`expected:<[Bochum, Essen]> but
  was:<[Bochum, Essen, Bochum]>`, `expected:<1> but was:<2>`) while the out-of-order case stayed green —
  its `associate { text to timestamp }` collapsed the duplicates itself, so the case was rewritten to
  assert the list shape and re-run with the same mutant → 3 failures, the missing one reading
  `expected:<[(Bochum, 300), (Essen, 200)]> but was:<[(Bochum, 100), (Essen, 200), (Bochum, 300)]>`;
  restored (marker count 0), re-ran forced → 110 tasks executed, repo tests="10" / 0 failures, fresh
  timestamp 16:21:37Z.
- [x] 4.4 Run the full both-flavor gate once with `--rerun-tasks` (`:app:testMobileDebugUnitTest`,
  `:app:testAutomotiveDebugUnitTest`, `:app:assembleMobileDebug`, `:app:assembleAutomotiveDebug`) and
  confirm green with no new build warnings. — ran 2026-10-04:
  `./gradlew :app:testMobileDebugUnitTest :app:testAutomotiveDebugUnitTest :app:assembleMobileDebug
  :app:assembleAutomotiveDebug --rerun-tasks --continue` → BUILD SUCCESSFUL in 8m57s, **189 actionable
  tasks: 189 executed**; `testMobileDebugUnitTest` and `testAutomotiveDebugUnitTest` each 215 classes /
  **1635 tests / 0 failures / 0 errors** (oldest XML timestamp 16:25:21Z for mobile, 16:28:43Z for
  automotive, run started 16:22 → both executed, none restored); both APKs written
  (`app-mobile-debug.apk`, `app-automotive-debug.apk`). Pre-gate sweep for values this change moves:
  `grep -rn 'history.value.size|searchQueries' app/src/test core/src/test auto/src/test` → only the two
  history classes (updated here) and the two `searchQueries` assertions in the class this change edited;
  no pinned duplicate-entry expectation exists. The 4 `warning:` lines in the log are 2 pre-existing
  manifest-merger notes on `AndroidManifest.xml:29` (an untouched file, unit-test manifest variant) and
  2 JVM CDS notices — no compile warning.
- [x] 4.5 Generate the coverage report (`./gradlew :koverHtmlReport`) and confirm the new paths are
  covered: the request publish path, `record()` dedupe, and `load()` collapse. — ran 2026-10-04 with
  `./gradlew :app:koverXmlReportMobileDebug` (13 s, reuses the gate's execution data; not run in the
  same invocation as the tests) → `app/build/reports/kover/reportMobileDebug.xml`. Source-file line
  counters: `SearchHistoryRepository.kt` covered=54 / missed=4 (93 %), `MapCanvasViewModel.kt`
  covered=1649 / missed=246 (87 %). Every line this change added reports `mi="0"`: the request publish
  (`MapCanvasViewModel.kt` 2269–2270, ci=13/14), the selection's sync publish (2397, ci=4), `clearSearch`
  (3735, ci=3), the pipeline (1097–1112), and the repository's collapse/record lines (85, 88, 100–112,
  128). The 4 uncovered repository lines are pre-existing and unreachable in a host test: the
  `SearchHistoryEntry` data-class synthetics (22, 29), the JSON-decode failure log (74) and
  `persist()`'s rename-failure fallback (138, 143, 146).

## 5. On-device verification and alignment

- [x] 5.1 On device (see the `device-check` skill): search "Bochum", select a result, reopen the dialog,
  tap the "Bochum" chip. Record the observable measurement: number of result rows shown, plus the
  `search request: chars=6 seq=<n>` line from `adb logcat -s NaviVeylin`. State explicitly that no
  pixel measurement (`pixel-check`) applies — the claim is "rows appear", not geometry. — ran
  2026-10-04 on `emulator-5554` (1080×2400, sdk 37) with the gate's `app-mobile-debug.apk` installed and
  `files/maps/search_history.json` removed first. Flow, all resolved from fresh `uiautomator` dumps:
  tap `Ort suchen` `[22,275][148,401]` → dialog open, empty query, `Suche in Regierungsbezirk Arnsberg`;
  tap the field `[0,153][1080,300]`, `input text Bochum` → rows listed ("Bochum", "Bochumer Weg",
  "Bochumer Straße", "Fanshop VfL Bochum", …) with distances; logcat
  `search request: chars=1..6 seq=1..6` then `searchLocations: query='Bochum', adminRegionHandle=1,
  candidates=60, limit=60, nativeMs=1606`. Tap the first row `[42,525][1038,778]` → details sheet
  (Kurt-Schumacher-Platz 13-15), dialog closed, `search request: chars=0 seq=7`, history file
  `{"entries":[{"text":"Bochum","timestamp":1791131922077}]}`. Back → map; tap `Ort suchen` →
  `Letzte Suchen` + one `Bochum` chip `[42,599][262,725]`; tap the chip → **the rows are listed again**
  (identical set), logcat `search request: chars=6 seq=8` then `searchLocations: query='Bochum',
  adminRegionHandle=2, candidates=60, nativeMs=993`. Second commit of the same search → dialog reopened
  → **exactly one** `Bochum` chip and the file holds **one** entry (timestamp 1791132025289). Fault-like
  lines under `MapCanvasVM`/`NaviVeylin`: **0**. No `pixel-check` applies: the claim is "result rows
  appear", and the measurement is the dump's node list (the map/canvas screens expose no other text).
  FAIL=0.
- [x] 5.2 On device or emulator with Android Auto: tap a recent-search row for the same text and confirm
  the search still runs and the history file holds one entry for that text (parity check, spec:
  `auto-search-suggestions` — History tap runs the search). — **partial, honest record**: the store half
  is verified on device (5.1: one entry per text in `maps/search_history.json`, the same file the car
  reads through `SearchHistoryRepository`). The **car surface itself was NOT exercised**: the Desktop
  Head Unit exists at `~/Android/Sdk/extras/google/auto/desktop-head-unit` and Android Auto
  (`com.google.android.projection.gearhead`) is installed on the emulator, but launching the DHU is
  refused by this session's shell policy and it needs a graphics display; no AAOS AVD is attached.
  Mitigating fact, not a substitute: the change touches no `:auto` file — the car screen pushes a
  prefilled `SearchScreen` (`SearchScreen.kt:247`) and its replay is covered by its own spec and tests,
  which stay green on both flavors (4.4). The automotive flavor compiles and tests the same shared
  classes (1635 tests, 0 failures).
- [x] 5.3 Verify documentation alignment: `guidelines/Design.md` §3 needs no text change (the change
  restores what it requires) — confirm and say so; update `AGENTS.md` only if a convention or file list
  changed; log any unrelated defect found during implementation as a new `TODO.md` entry. — done
  2026-10-04: `grep -rn '_searchQueryFlow|searchQueryFlow|SearchRequest' README.md AGENTS.md guidelines/*.md`
  → no match, so no document names the replaced mechanism and `guidelines/Design.md` §3 stands verified
  rather than superseded (the change removes the second mirror of the query; §3's "one truth per value"
  is what it restores, and §3's "prefer a state field over a separate flow for derived signals" is
  respected — the trigger is a request, not derived state). `AGENTS.md` needs no edit: its search
  section describes the history store's persistence and the favourite-write serialisation, neither of
  which changed. Two unrelated defects found while implementing were filed: `TODO.md` **§123** (test
  harness builds `SearchHistoryRepository` off the test dispatcher, so a selection-path assertion races
  a real-thread write — the misattribution this change had to work through) and **§124**
  (`openspec/config.yaml`'s `operations.apply.guidance` is silently ignored, so the project's apply
  rules never reach the agent).
- [x] 5.4 Run `openspec validate fix-search-history-chip-replay --strict` and confirm every scenario in
  the two spec deltas is traceable to a task above. — ran 2026-10-04: `Change
  'fix-search-history-chip-replay' is valid` (strict), all four artifacts `done`. Traceability:
  `search-history` — Selection takes over search string (1.1/2.2), Selection runs the search (1.1/2.2),
  Replaying the last committed search (1.1/4.1b/5.1), Chip after a dismissal without selection (2.2/4.1b),
  Repeating a search moves its entry to the front (3.1/4.2), Chip row shows each text once (3.3/4.2/5.1),
  Cap still enforced after a repeat (3.1), Persisted duplicates collapse on load (3.2/4.3), Order is
  preserved when collapsing (3.2/4.3), An out-of-order file cannot keep a stale date (3.2/4.3);
  `location-search` — Re-requesting the previous query text (1.2/4.1), Same text arriving from another
  entry point (2.3), Empty query after a committed search (2.2). One delta scenario was corrected during
  implementation (see the artifacts note in the report): the "Order is preserved when collapsing"
  premise described a file that cannot exist under the youngest-first write rule, and the out-of-order
  case it was meant to cover is now its own scenario.
