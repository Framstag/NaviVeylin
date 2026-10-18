# Tasks

Parent spec: `car-list-row-limit` (MODIFIED — "A car list never presents more rows than the host displays",
"Items beyond the capacity stay reachable"). Order: reproduce, fix, falsify the new bound, gate, device
follow-up, bookkeeping. Task 2.3 closes the scenario-to-case map.

## 1. Reproduce the defect (red on HEAD)

- [x] 1.1 Write the capacity case for the car diagnostics screen in
  `auto/src/test/java/com/naviveylin/auto/DiagnosticsScreenTest.kt`
  (`theDiagnosticsListFitsTheHostsCapacity`): a fake host answering `getContentLimit` with **8**
  (`ConstraintManager` mock, `carAppApiLevel` 7), a recording `ScreenManager`, 30 logged entries on top of the
  setUp entry, and the template read through a kind-agnostic row helper so the case compiles against HEAD's
  `PaneTemplate` and fails on its assertions. The fake host's 8 is deliberately neither the library's default
  6 nor the screen's own constant 20, so the assertion cannot be satisfied by a screen that keeps its
  constant or ignores the host. Spec: `car-list-row-limit` — The diagnostics list fits the host's capacity.
  Verification: the red-on-HEAD run is retained at
  `evidence/TEST-DiagnosticsScreenTest-red-on-HEAD.xml` — `tests="5" skipped="0" failures="2" errors="0"`,
  `timestamp="2026-10-18T14:47:27.099Z"`, failing as
  `java.lang.AssertionError: the diagnostics screen must be a car list to page its entries, was PaneTemplate (spec: car-list-row-limit)`;
  command `./gradlew :auto:testDebugUnitTest --tests "com.naviveylin.auto.DiagnosticsScreenTest" -PforceTests --no-build-cache`.
- [x] 1.2 Write the page case in the same file (`thePageRowOpensTheNextSliceOfTheWindow`): the page row carries
  `More (12)` for the 20-entry window at capacity 8, and the page it opens presents the next 8 rows of the same
  newest-first window and then names `More (4)`.
  Spec: `car-list-row-limit` — The diagnostics page continues the recent-entries window.
  Verification: red in the same retained XML with the same failure
  (`… must be a car list to page its entries, was PaneTemplate`).

## 2. Present the window as a car list at the host's capacity (`DiagnosticsScreen`)

- [x] 2.1 Change the template to `ListTemplate` through `carListTemplate` and keep the disclosure as the list's
  first item, in front of the window. Spec: `car-list-row-limit` — The diagnostics list fits the host's
  capacity ("it SHALL be a car list rather than a pane"); `auto-diagnostics` — Export and viewers disclose the
  log contents. Verification: `theTemplateOpensWithTheDisclosure` stays green and
  `loadedEntriesReachTheTemplate`, `onGetTemplateDoesNotReadTheFile` stay green in the focused run.
- [x] 2.2 Add the capacity field (started at `CarListCapacityReader.LIBRARY_DEFAULT`) and the read on the
  screen's own scope off the host thread (`withContext(Dispatchers.Default) { CarListCapacityReader.read(carContext) }`,
  then publish and `invalidate()` when the answer differs). Spec: `car-list-row-limit` — the capacity is taken
  from the host rather than from a fixed constant, and a host that cannot be asked uses the library's default.
  Verification: `theDiagnosticsListFitsTheHostsCapacity` asserts one capacity query per screen start and that
  three template builds add none.
  — ran 2026-10-18: the field is `DiagnosticsScreen.kt:58` and the read is `:81-88` (`readRowCapacity`, joined
  the screen's `init` scope, `withContext(Dispatchers.Default)` at `:83`). The case passed in the focused
  forced run (`tests="5" skipped="0" failures="0" errors="0"`, `timestamp="2026-10-18T14:51:45.939Z"`),
  retained as `evidence/TEST-DiagnosticsScreenTest-green-focused.xml`.
- [x] 2.3 Add `page: Int = 0`, slice the published window with
  `FavoritesScreenMapper.page(loaded, capacity = rowCapacity, page = page)` and, when the slice leaves entries,
  add the clickable final row (`R.string.more_items`) that pushes another `DiagnosticsScreen` with `page + 1`
  through `armScreenPush`. Keep `MAX_ROWS = 20` as the window the pages partition. Spec: `car-list-row-limit` —
  The final row opens the next page; the diagnostics page continues the recent-entries window; the push is
  also what keeps the restated baseline scenario "Back navigation returns to the previous page" true (design
  D3). The baseline scenario names no case in the delta because no case asserts that mechanism — stated
  rather than promised. Verification: `thePageRowOpensTheNextSliceOfTheWindow` green; the pushed screen is a
  `DiagnosticsScreen` the fake `ScreenManager` recorded being pushed.
  — ran 2026-10-18: the slice is `DiagnosticsScreen.kt:126`, the final row `:135`, `pageRow` `:165-172`;
  both cases passed in the green forced run quoted in 2.2. The delta's "Back navigation returns to the
  previous page" scenario carries no `Case:` line (see 2.4).
- [x] 2.4 Confirm the delta's scenario-to-case map: every scenario in `specs/car-list-row-limit/spec.md` names
  exactly one case — the two new diagnostics cases, the search-history cases, the address-book cases and the
  existing `FavoritesScreenTest`/`FavoritesScreenMapperTest` cases — except the restated baseline scenario
  "Back navigation returns to the previous page", which names none (no case asserts it; the push of 2.3
  delivers the mechanism), so the delta carries no `Case:` line for it rather than a false one.
  Verification: `openspec validate fix-car-diagnostics-row-cap --strict` exits 0 and each `**Case**:` line
  resolves to a `fun <case>(` in the named class's test file.
  — ran 2026-10-18: `openspec validate fix-car-diagnostics-row-cap --strict` printed "Change
  'fix-car-diagnostics-row-cap' is valid" (exit 0); all twelve `**Case**:` lines resolve to a `fun <case>(` in
  `auto/src/test/java/com/naviveylin/auto/` (`FavoritesScreenTest` 4 + final-row, `FavoritesScreenMapperTest` 2,
  `AddressBookScreenTest` 2, `SearchHistoryScreenTest` 2, `DiagnosticsScreenTest` 2).

## 3. Falsify the bound the fix introduces

- [x] 3.1 Revert-check (`.pi/skills/revert-check`): mutate the single production line that takes the host's
  answer into the bound — `FavoritesScreenMapper.page(loaded, capacity = rowCapacity, page = page)` →
  `capacity = loaded.size` — and confirm the two cases fail for the right reason; restore the line, confirm no
  `REVERT-CHECK MUTATION` marker remains and `git diff --stat auto/src/main` lists only this change's
  `DiagnosticsScreen.kt`, then re-run the focused cases green.
  Verification: the mutation run's XML tally and failure messages, the clean restore and the green re-run are
  quoted here, with the mutation XML copied into this change's `evidence/`.
  — ran 2026-10-18: with `capacity = loaded.size` (marked `REVERT-CHECK MUTATION`) both cases failed at the
  bound's assertion — `theDiagnosticsListFitsTheHostsCapacity`:
  `java.lang.AssertionError: the page holds the host's 8 rows between the disclosure and its page row expected:<10> but was:<21>`,
  and `thePageRowOpensTheNextSliceOfTheWindow`:
  `java.lang.AssertionError: no row titled More (12) in [ … entry-30, …, entry-12]` (the uncapped 20-entry
  window) — XML `tests="5" skipped="0" failures="2" errors="0"`,
  `timestamp="2026-10-18T14:51:32.408Z"`, retained as
  `evidence/TEST-DiagnosticsScreenTest-revert-check-mutation.xml`. The line was restored exactly to
  `capacity = rowCapacity`; `grep -rn "REVERT-CHECK MUTATION" auto/src` returns nothing; the forced re-run
  after the restore was green (`tests="5" skipped="0" failures="0" errors="0"`,
  `timestamp="2026-10-18T14:51:45.939Z"`), retained as
  `evidence/TEST-DiagnosticsScreenTest-green-focused.xml`.

## 4. Gate

- [x] 4.1 Run the run's configured gate unchanged: `./gradlew test -PforceTests --rerun-tasks`. The change
  carries a compiled production file and four test files, so it is a code delta and the whole gate applies
  (`guidelines/Build.md` §2, the scope block). Verification: every cited XML is copied into `evidence/` (never
  a build directory or `/tmp`); the per-module tallies are quoted from those copies.
  — ran 2026-10-18: `BUILD SUCCESSFUL in 9m 26s` (log `/tmp/gate-114.log`), no `FAILED` line; tallies from the
  run's XMLs — `:app` mobile **2059 tests / 0 failures / 0 errors**, `:app` automotive **2059 / 0 / 0**,
  `:auto` **866 / 0 / 0**, `:core` **642 / 0 / 0**, `:osmscout-client-java` **46 / 0 / 0**
  (`evidence/gate-full-suite.txt`); `DiagnosticsScreenTest` `tests="5" skipped="0" failures="0" errors="0"` at
  `timestamp="2026-10-18T14:52:50.437Z"`, and the `:auto` suite's 85 XMLs (all green) are copied into
  `evidence/auto-module-suite/`.
- [x] 4.2 Adapt the three sibling cases that read this screen's template kind: `StartupScreensTest`
  (`diagnosticsScreenShowsNewestFirst`, its `awaitRows` helper), `GermanRenderingTest`
  (`diagnosticsScreenRendersTheGermanDisclosure`) and `TemplateFaultIsolationTest`
  (`diagnosticsScreenServesTheErrorTemplateWhenTheBuildFails` → `assertErrorList`). Verification: all three
  green in the gate run quoted in 4.1 (`StartupScreensTest`, `GermanRenderingTest`, `TemplateFaultIsolationTest`
  XMLs under `evidence/auto-module-suite/`).

## 5. Device follow-up (pending — no device attached in this session)

- [ ] 5.1 On the AAOS emulator, open the car diagnostics screen with more entries logged than the host's
  reported capacity and read (a) the coordinate-free line `Diag/SCREEN: list capacity N rows (host, car API
  level M)`, (b) the `More (n)` row on the list and the page it opens, (c) that the host renders the page row
  itself (the (capacity + 1)-th row, design "Risks"). Verification: the recorded logcat lines and the counts.
  State explicitly that no device is attached if that is still the case.
  — **pending 2026-10-18**: no device was attached in this session, so the capacity line and the `More (n)` row
  were never observed on a host. Left `[ ]` and named as the change's device gap rather than claimed.

## 6. Bookkeeping

- [x] 6.1 Guideline check: `guidelines/UI.md` §3 already states the capacity rule screen-generically
  (`:291-300`, naming `CarListCapacityReader` + `FavoritesScreenMapper.page`) and records why a pane cannot
  serve this screen (`:289-290`: pane rows are not actionable and do not scroll). No sentence contradicts the
  applied behaviour, so no guideline edit is owed.
  Verification: the two bullets are read and the conclusion stated; edit only if a sentence contradicts.
  — ran 2026-10-18: read `guidelines/UI.md:289-300`; both bullets describe exactly what the screen now does.
- [x] 6.2 Archive this change and confirm the delta merged into `openspec/specs/car-list-row-limit/spec.md`
  (the two requirements now carry the two diagnostics scenarios). The tracker entry #114 is closed by the run's
  script, not by this change, so nothing here writes a label.
  — ran 2026-10-18: `openspec archive fix-car-diagnostics-row-cap --yes` reported `car-list-row-limit: update`,
  `~ 2 modified`, `Specs updated successfully`, and archived the change as
  `openspec/changes/archive/2026-10-18-fix-car-diagnostics-row-cap`; the merged spec carries both scenarios at
  `openspec/specs/car-list-row-limit/spec.md:51` and `:98`, and `openspec validate --specs` reports
  `175 passed, 0 failed`. Task 5.1 (device) is the only task left open, as a recorded gap.
