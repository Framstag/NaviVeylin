# Tasks

Parent spec: `car-list-row-limit` (MODIFIED — "A car list never presents more rows than the host
displays", "Items beyond the capacity stay reachable"). Order: reproduce, fix, falsify the new bound, gate,
device follow-up, bookkeeping. Task 2.3 closes the scenario-to-case map.

## 1. Reproduce the defect (red on HEAD)

- [x] 1.1 Write the capacity case for the car search history in a new
  `auto/src/test/java/com/naviveylin/auto/SearchHistoryScreenTest.kt`
  (`theSearchHistoryListFitsTheHostsCapacity`, harness following `AddressBookScreenTest`: mock
  `EntryPoints.get`, a fake host answering `getContentLimit`, the screen driven through
  `dispatchLifecycleEvent`). The fake host reports **8**, deliberately not the library default of 6, so the
  case asserts that the row slice *and* the `More (n)` count follow the host's answer — a screen that used
  its own fallback would show six rows with `More (5)`. Spec: `car-list-row-limit` — The search history list
  fits the host's capacity. Verification: the red-on-HEAD run is retained at
  `openspec/changes/fix-car-search-history-cap/evidence/TEST-SearchHistoryScreenTest-red-on-HEAD.xml` —
  `tests="2" skipped="0" failures="2" errors="0"`, `timestamp="2026-10-13T20:37:54.926Z"`, the case failing
  as `java.lang.AssertionError: expected:<[query-1, query-2, query-3, query-4, query-5, query-6, query-7, query-8, More (3)]> but was:<[query-1, query-2, query-3, query-4, query-5, query-6, query-7, query-8, query-9, query-10, query-11]>`;
  command `./gradlew :auto:testDebugUnitTest --tests "com.naviveylin.auto.SearchHistoryScreenTest" -PforceTests --rerun-tasks`.
- [x] 1.2 Write the page case in the same file (`thePageRowOpensTheNextSliceOfTheHistory`): with the host
  reporting 8 and eleven stored searches, the page row carries `More (3)` and the page it opens begins with
  `query-9`, not with `query-1` and not with an unbounded list.
  Spec: `car-list-row-limit` — The search-history page continues the stored history.
  Verification: it is red in the same retained XML, failing as
  `java.lang.AssertionError: no row titled More (3) in [query-1, query-2, query-3, query-4, query-5, query-6, query-7, query-8, query-9, query-10, query-11]`.

## 2. Cap the search history list (`SearchHistoryScreen`)

- [x] 2.1 Add the capacity field (started at `CarListCapacityReader.LIBRARY_DEFAULT`) and the read on the
  screen's own scope off the host thread (`withContext(Dispatchers.Default) { CarListCapacityReader.read(carContext) }`,
  then publish and `invalidate()` when the answer differs). Spec: `car-list-row-limit` — The search history
  list fits the host's capacity; the capacity is taken from the host, and a host that cannot be asked uses
  the library's default. Verification:
  `./gradlew :auto:testDebugUnitTest --tests "com.naviveylin.auto.SearchHistoryScreenTest" -PforceTests --rerun-tasks`
  is green for `theSearchHistoryListFitsTheHostsCapacity`, which also carries the requirement's host-source
  half ("SHALL take that capacity from the host rather than from a fixed constant"): the case asserts one
  capacity query per screen start and that three template builds add none. `FavoritesScreenTest.theTemplateBuildPerformsNoHostQuery`
  still passes (the read did not move into a template build).
  — ran 2026-10-13: the field is `SearchHistoryScreen.kt:50` and the read is `:68-74` (`readRowCapacity`,
  joined the screen's own scope in `init`, `withContext(Dispatchers.Default)` at `:70`); the case passed
  (`tests="2" skipped="0" failures="0" errors="0"`, `timestamp="2026-10-13T20:39:33.685Z"`,
  forced), retained as
  `evidence/TEST-SearchHistoryScreenTest-green-focused.xml`.
  `FavoritesScreenTest.theTemplateBuildPerformsNoHostQuery` stayed green in the module run quoted in 4.1,
  carried by `evidence/auto-module-suite/TEST-com.naviveylin.auto.FavoritesScreenTest.xml`.
- [x] 2.2 Add `page: Int = 0`, slice the loaded entries with
  `FavoritesScreenMapper.page(entries, capacity = rowCapacity, page = page)` and, when the slice holds more,
  add the clickable final row (`R.string.more_items`) that pushes another `SearchHistoryScreen` with
  `page + 1`. Spec: `car-list-row-limit` — The final row opens the next page; the search-history page
  continues the stored history. The push is also what keeps the restated baseline scenario "Back navigation
  returns to the previous page" true (design D3: the host keeps the opening page on its own stack); no case
  in this change asserts that mechanism, so the delta names none for it — stated in 2.3.
  Verification: `thePageRowOpensTheNextSliceOfTheHistory` green; the pushed screen is a `SearchHistoryScreen`
  the fake `ScreenManager` recorded being pushed (not the same instance mutated).
  — ran 2026-10-13: the slice is `SearchHistoryScreen.kt:98`, the final row `:107-109` and `pageRow` `:131-140`,
  pushed through `armScreenPush` with `page + 1` (`:135`); both cases passed in the green forced run quoted in
  2.1 (`tests="2" failures="0" errors="0"`, `timestamp="2026-10-13T20:39:33.685Z"`), and the page case asserts
  the pushed screen's rows are `query-9 … query-11`.
- [x] 2.3 Confirm the delta's scenario-to-case map and state what the restated baseline keeps: every scenario
  in `specs/car-list-row-limit/spec.md` names exactly one case (the two new search-history cases, the
  address-book cases and the existing `FavoritesScreenTest`/`FavoritesScreenMapperTest` cases) — except the
  restated baseline scenario "Back navigation returns to the previous page", which names none: the push of
  2.2 delivers the mechanism, but no case asserts it, so the delta carries no `Case:` line for it rather than
  a false one, and the scenario keeps the baseline's claim strength from `car-favorites-group-level`. The
  favourites-side and address-book scenarios this fix does not re-deliver keep the baseline's claim strength
  too. Verification: `openspec validate fix-car-search-history-cap --strict` exits 0, and each `**Case**:`
  line names a case that exists in the named module's test sources (grep the case name in `auto/src/test`).
  — ran 2026-10-13: `openspec validate fix-car-search-history-cap --strict` printed "Change
  'fix-car-search-history-cap' is valid" (exit 0) with the back-navigation scenario carrying no `Case:` line,
  and all eleven `**Case**:` lines resolve to a `fun <case>(` in the named class's file under
  `auto/src/test/java/com/naviveylin/auto/` — ten distinct cases: `FavoritesScreenTest` for the four
  capacity scenarios and the final-row scenario, `FavoritesScreenMapperTest` for the partition and last-page
  scenarios, `AddressBookScreenTest` for the two address-book scenarios, `SearchHistoryScreenTest` for the two
  search-history scenarios.

## 3. Falsify the bound the fix introduces

- [x] 3.1 Revert-check (`.pi/skills/revert-check`): mutate the single production line that takes the host's
  answer into the bound — in `SearchHistoryScreen.buildTemplate()` pass `capacity = list.size` to
  `FavoritesScreenMapper.page` instead of `capacity = rowCapacity` — and confirm
  `theSearchHistoryListFitsTheHostsCapacity` fails for the right reason (the uncapped list:
  `… query-9, query-10, query-11` with no `More (3)` row). Restore the line, confirm the working tree carries
  no `REVERT-CHECK MUTATION` marker and `git diff --stat auto/src/main` lists only this change's
  `SearchHistoryScreen.kt`, then re-run the focused case green.
  Verification: the mutation run's XML tally and failure message, the clean restore, and the green re-run
  are recorded in this task's note, with the mutation XML copied into this change's `evidence/`.
  — ran 2026-10-13: with `capacity = list.size` at `SearchHistoryScreen.kt:98` (marked with a
  `REVERT-CHECK MUTATION` comment) both cases the guard protects failed at the bound's assertion —
  `theSearchHistoryListFitsTheHostsCapacity`:
  `java.lang.AssertionError: expected:<[query-1, query-2, query-3, query-4, query-5, query-6, query-7, query-8, More (3)]> but was:<[query-1, query-2, query-3, query-4, query-5, query-6, query-7, query-8, query-9, query-10, query-11]>`,
  and `thePageRowOpensTheNextSliceOfTheHistory`:
  `java.lang.AssertionError: no row titled More (3) in [query-1, query-2, query-3, query-4, query-5, query-6, query-7, query-8, query-9, query-10, query-11]`
  — XML `tests="2" skipped="0" failures="2" errors="0"`, `timestamp="2026-10-13T20:40:00.895Z"`, retained as
  `evidence/TEST-SearchHistoryScreenTest-revert-check-mutation.xml`; the line was restored exactly to
  `capacity = rowCapacity`, `grep -rn "REVERT-CHECK MUTATION" auto/src` returned nothing and
  `git diff --stat auto/src/main` lists only this change's `SearchHistoryScreen.kt`; the forced re-run after
  the restore was green (`tests="2" skipped="0" failures="0" errors="0"`,
  `timestamp="2026-10-13T20:40:30.977Z"`), retained as
  `evidence/TEST-SearchHistoryScreenTest-revert-check-green-after-restore.xml`.

## 4. Gate (one forced run for this change shape)

- [x] 4.1 Run the forced gate for this change's scope — the changed module's own suite,
  `./gradlew :auto:testDebugUnitTest -PforceTests --rerun-tasks` (`guidelines/Build.md` §2, the scope block:
  a production change runs the forced gate, scoped to what it touched), with
  `.pi/skills/fix-loop/scripts/evidence-check.sh` in live mode green before it. The whole-suite
  `./gradlew test -PforceTests --rerun-tasks` is **excluded**, not skipped silently: its `:app` half carries
  15 pre-existing `AppNotIdleException: Compose did not get idle … in 60 SECONDS` failures in
  `BasemapSectionComposeTest` (2) and `MapManagerSourceSelectorComposeTest` (13) — a module this change does
  not touch — and the run was killed before any `BUILD` marker, so it is no gate result either way; the
  attribution is carried by
  `openspec/changes/archive/2026-10-13-fix-car-list-row-capacity/evidence/gate-full-suite-app-compose-preexisting.txt`.
  Verification: every XML cited is copied into `evidence/` (never a build directory or `/tmp`); the module
  run's tally is quoted from the run's copied XMLs under `evidence/auto-module-suite/`.
  — ran 2026-10-13: `BUILD SUCCESSFUL in 37s`, `59 actionable tasks: 59 executed`, no `FAILED` line; the run's
  82 XMLs sum to 82 classes, 842 cases, 0 skipped, 0 failures, 0 errors (XML timestamps
  `2026-10-13T20:41:19.594Z`–`2026-10-13T20:41:46.821Z`); `SearchHistoryScreenTest`
  `tests="2" skipped="0" failures="0" errors="0"` at `timestamp="2026-10-13T20:41:43.841Z"`, and
  `FavoritesScreenTest` `tests="37"`, `FavoritesScreenMapperTest` `tests="9"`, `AddressBookScreenTest`
  `tests="2"`, `TemplateFaultIsolationTest` `tests="21"`, `SearchScreenTest` `tests="12"`, `RootScreenTest`
  `tests="2"`, all `failures="0"`. The run log is `.pi/bugfix-loop/gate-auto-module-2026-10-13-178.log` and
  every XML is copied into `evidence/auto-module-suite/`; the run's tally line is
  `evidence/gate-auto-module-suite.txt`. The known `MapCanvasViewModelModeTest` flake (`TODO.md` §148) did not
  occur in this run (the class is `:app`-side and the excluded suite is where it lives), so nothing was
  attributed as a flake. `.pi/skills/fix-loop/scripts/evidence-check.sh` in live mode was green before this
  run (C1–C5 PASS). The suite's only other reference to `SearchHistoryScreen` is
  `auto/src/test/java/com/naviveylin/auto/TemplateFaultIsolationTest.kt:219` (the error-template case, which
  needs no update because the new parameter is defaulted); it is green in this run (`tests="21" failures="0"`).
  No test in the suite asserts the old unbounded list, so no expectation had to be updated.

## 5. Device follow-up (pending — no device attached in this session)

- [ ] 5.1 On the AAOS emulator, open the car search history with more stored searches than the host's
  reported capacity and read (a) the coordinate-free line
  `Diag/SCREEN: list capacity N rows (host, car API level M)`, (b) the `More (n)` row on the list and the page
  it opens, (c) that the host renders the page row itself (the (capacity + 1)-th row, design "Risks").
  Verification: the recorded logcat lines and the counts. State explicitly that no device is attached if that
  is still the case — a stationary emulator is enough for this property, but a head unit or projection host
  is not available.
  — **pending 2026-10-13**: no device was attached in this session, so the capacity line and the `More (n)`
  row were never observed on a host. Left `[ ]`, and named as the change's device gap in the report rather
  than claimed.

## 6. Bookkeeping

- [ ] 6.1 `TODO.md`: §178 → `**status:** fixed-by \`fix-car-search-history-cap\`` (the entry is kept), and
  §179, §180 stay `open` with their reasons recorded — §179 one combined decision with the search's
  candidate count, §180 a `PaneTemplate`, i.e. a different mechanism. This task is performed when the change
  is archived (the loop's phase F), because the status names the archived change; it is left unchecked by the
  apply pass, which does not archive.
  Verification: `grep -n "^## 178\." -A 1 TODO.md` shows the new metadata line; the `##` count still equals
  the `**id:**` count and no id collides.
- [x] 6.2 Guideline check: `guidelines/UI.md` §3's `ListTemplate` bullet already states the capacity rule
  screen-generically and names the shipped implementation (`CarListCapacityReader` +
  `FavoritesScreenMapper.page`), so no sentence contradicts this fix and no edit is owed.
  Verification: the bullet is read and the conclusion stated in the report; edit it only if a sentence
  contradicts the applied behaviour.
  — ran 2026-10-13: `guidelines/UI.md:295-298` says "A car list screen MUST ask the host for the capacity and
  present **at most** that many rows, ending with its own page row, because rows past the limit disappear with
  no signal (spec `car-list-row-limit`; the shipped implementation is `CarListCapacityReader` +
  `FavoritesScreenMapper.page`, change `car-favorites-group-level`)" — screen-generic, and the search history
  is one more `ListTemplate` screen the sentence covers, so no sentence contradicts the applied behaviour and
  no guideline edit is owed.
