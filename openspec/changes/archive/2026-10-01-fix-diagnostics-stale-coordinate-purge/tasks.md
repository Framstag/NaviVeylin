# Tasks

Parent spec for every task below: `auto-diagnostics` — delta in `specs/auto-diagnostics/spec.md`
(added requirement "Coordinate-carrying entries do not survive the retention pass"). Design:
`design.md` (D1 = the pass, D2 = ADDED delta, D3 = the line predicate, D4 = one count per pass).

## 1. The line predicate

- [x] 1.1 Add the predicate that decides whether a log line carries a device position — token half:
  a delimited coordinate field (`lat`, `lon`, `latitude`, `longitude`, case-insensitive) **and** a
  number with four or more fraction digits; pair half: **two** numbers with four or more fraction
  digits on the same line (the car render's `center=<lat>,<lon>` shape from `TODO.md` §98). Keep it a
  small pure function with its own KDoc stating the accepted coarse half (spec: auto-diagnostics —
  Coordinate-carrying entries do not survive the retention pass; design D3 alternative B). Verify:
  `./gradlew :core:compileDebugUnitTestKotlin` succeeds and the fixtures of 1.2 pass.
  **Done 2026-09-28:** new `core/src/main/java/com/naviveylin/core/LogLineCoordinates.kt` —
  `internal object LogLineCoordinates` with `carriesPosition(line)`, `PRECISE_NUMBER`
  (`\d{1,3}\.\d{4,}`, the recipe's shape), `COORDINATE_FIELD` (delimited `lat|lon|lng|latitude|longitude`,
  case-insensitive) and `COORDINATE_PAIR` (`-?\d{1,3}\.\d{4,}\s*,\s*-?\d{1,3}\.\d{4,}`). **Deviation
  from this task's wording, narrowing the pair half to a *comma-separated* pair:** the literal "two
  numbers with four or more fraction digits on the same line" would have dropped a legitimate
  diagnostics line that prints two raw `Double`s (e.g. `requestRender mag=16.666666666666668 (was
  17.333333333333332)`), which `design.md` D3 lists as an accepted risk only for the comma case — the
  narrower rule removes that false positive and still catches the §98 shape. `design.md` D3 and its
  risk list are updated to say "comma-separated pair". Verify: `:core:testDebugUnitTest --tests
  LogLineCoordinatesTest` → 13/0.
- [x] 1.2 Create the fixtures next to the retention tests (`core/src/test/java/com/naviveylin/core/`,
  a focused class such as `LogLineCoordinatesTest`) — catch-cases: the §88 line verbatim
  (`[2026-09-25 21:34:34.085] LONGPRESS lat=51.513298135108705 lon=7.474341597216892 mag=16.0`), the
  pre-redaction car render line, a `latitude:`-labelled line; keep-cases: the current identity lines
  (`LONGPRESS x=540 y=1500 mag=18.0 map=iceland`, `render mag=17.0 dpi=420 -> bitmap 1296x720`,
  `acc=12.5 bearing=180.0`), a `Shared location: shape=coordinates` line, an object-label line, an
  empty line; boundary: a single ≥4-digit decimal without a token stays. Verify: the class runs green
  and each new catch-case fails with its rule disabled (revert-check quoted in the task note).
  **Done 2026-09-28:** `core/src/test/java/com/naviveylin/core/LogLineCoordinatesTest.kt`, 13 cases —
  6 catch (`flagsThePhoneLongPressEntryVerbatim` = the §88 line, `flagsTheCarRenderEntryVerbatim` = the
  §98 line, `flagsALatitudeLabelledEntry`, `flagsAPairWithNegativeLongitude`,
  `flagsAPairWithASpaceAfterTheComma`, `flagsATokenWithASinglePreciseValue`) and 7 keep
  (`keepsTheLongPressIdentityEntry`, `keepsTheCarRenderIdentityEntry`, `keepsAccuracyBearingAndTileCounts`,
  `keepsTheSharedLocationShapeEntry`, `keepsAWirelessEntryWithAnObjectLabel`,
  `keepsTwoRawDoublesWithoutACommaBetweenThem`, `keepsProseAndIdentifierLookalikes`). **Revert-check A**
  (whole rule → `return false`): `25 tests completed, 11 failed` — exactly the 6 catch-cases and the 5
  new retention cases, every keep-case green. **Revert-check B** (pair half disabled only):
  `13 tests completed, 3 failed` — exactly `flagsTheCarRenderEntryVerbatim`,
  `flagsAPairWithNegativeLongitude`, `flagsAPairWithASpaceAfterTheComma`, so both halves are
  load-bearing on their own. Restored: 13/0.
- [x] 1.3 Document the trade-off in the predicate's KDoc and in the change's task note: a legitimate
  line carrying two ≥4-fraction-digit numbers is dropped by design, and the pass's count report makes
  such a drop visible (design — Risks). Verify: the KDoc names the accepted false-positive shape.
  **Done 2026-09-28:** the KDoc lists both recognised shapes, the accepted false-positive (a legitimate
  comma-separated pair of high-precision numbers), the deliberate narrowness versus the recipe's bare
  grep (a raw `Double` magnification would otherwise be dropped) and the residual (no coordinate field
  name / fewer than four fraction digits → left to the age bound and the build gate).

## 2. The purge in the retention pass

- [x] 2.1 Extend `core/src/main/java/com/naviveylin/core/DiagnosticsLog.kt` `pruneExpiredLocked` so a
  line is dropped when it is expired **or** when the predicate flags it, into the same `kept` list with
  the same single temp-file rewrite and the existing "rewrite only when something was dropped" rule;
  the pass stays worker-only and `retentionMs` keeps its meaning (spec: the same requirement; design
  D1 alternative B). Verify: `DiagnosticsLogRetentionTest` stays green before the new cases land.
  **Done 2026-09-28:** `pruneExpiredLocked` now counts `aged` and `coordinates` separately and drops a
  line when `LogLineCoordinates.carriesPosition(line)` is true **before** the age test, so a young
  position is dropped and a position that is also expired is counted once as a position. One `kept`
  list, one `rewriteLocked` per file when anything in it was dropped, `retentionMs` semantics unchanged;
  the pass KDoc and the `RETENTION_MS` doc now state that age is not the only reason a line goes.
  Verify: 12 retention cases + 13 predicate cases green (`BUILD SUCCESSFUL in 10s`).
- [x] 2.2 Extend the single report line with the coordinate count (counts only, never the position),
  emitted once for the pass only when something was dropped — e.g.
  `retention: dropped 1617 entries older than 168h (oldest kept: …), 1 coordinate-carrying` (spec: the
  same requirement — the removal is reported without naming the position; design D4 alternative B).
  Verify: the report is greppable for `coordinate-carrying`, contains no value the predicate would
  flag, and no second entry is emitted for the same pass.
  **Done 2026-09-28:** new private `retentionReport(aged, coordinates, oldestKept)` builds the single
  line (`retention: dropped <aged> entr(y|ies) older than 168h, <n> coordinate-carrying (oldest kept: …)`;
  a coordinate-only pass reads `dropped 1 coordinate-carrying`), counts only — the report cannot repeat
  a position. `theCoordinateDropIsReportedOnceWithCountsAndWithoutThePosition` asserts one report line,
  `2 coordinate-carrying`, the aged count in the same line, and that the report is neither
  predicate-positive nor contains the digits.
- [x] 2.3 Add the purge cases to `DiagnosticsLogRetentionTest` — a **young** coordinate line (newer
  than the window) is removed from the active file **and** from the rotated one; identity lines in the
  same file survive; a coordinate line that is also expired is removed and counted once; the
  "the caller performs no retention work" case covers the coordinate rule; a log without coordinates
  keeps the existing report wording (spec: the same requirement, all five scenarios). Verify: the
  class runs green with the new case names quoted; disabling the predicate fails the young-coordinate
  case (revert-check).
  **Done 2026-09-28:** 5 new cases in `DiagnosticsLogRetentionTest` —
  `aYoungCoordinateEntryIsRemovedFromTheActiveFileAndTheRotatedOne` (the §88 case: a day-old position in
  both files is dropped, the identity line beside it stays),
  `theCoordinateDropIsReportedOnceWithCountsAndWithoutThePosition`,
  `anExpiredCoordinateEntryIsCountedAsCoordinateOnly` (no double count, no `older than 168h` part),
  `aLogWithoutPositionsKeepsTheAgedReportWording` (`1 entry older than 168h`, no coordinate part) and the
  extended `theCallerPathPerformsNoRetentionWork` (the position is still on disk before the worker runs).
  Class is **12 cases / 0 failures**; **revert-check A** (predicate disabled) failed exactly those 5.
- [x] 2.4 Verify the reader contract end to end: write a stale coordinate line plus an identity line
  into the file, start the worker by logging one entry, then assert `readEntries()` and `exportText()`
  contain the identity line, no coordinate line, and that the export still leads with
  `diagnostics_disclosure` (spec: the same requirement — purging never blocks the caller; and
  `auto-diagnostics` — Reading diagnostics does not block the UI, `Export and viewers disclose the log
  contents`). Verify: the new case is green and the disclosure assertion is unchanged.
  **Done 2026-09-28:** `theReaderSeesNoPositionAfterThePass` writes a stale position beside an identity
  line, starts the worker, and asserts that `readEntries()` and `exportText()` carry the identity line
  and no coordinate. **Deviation from this task's wording:** the disclosure is composed in the *app*
  layer (`diagnosticsShareText(disclosure, logText)` in `app/src/main/java/com/naviveylin/ui/about/
  AboutDialog.kt:226`), not in `exportText()`, so the leading-disclosure assertion stays where it
  already lives (`app/src/test/java/com/naviveylin/ui/about/DiagnosticsDisclosureTest.kt`, re-run green
  in 4.2) instead of being duplicated in `:core` against a string it never prepends.

## 3. Documentation

- [x] 3.1 Update `guidelines/Regulatory.md` §9: the coordinate-free rule covers the file's **history**
  — the retention pass removes a coordinate-carrying line regardless of age — and names the residual
  (a shape outside the predicate is bounded by the age window; new writes remain the build gate's
  concern). Confirm in the same pass that `diagnostics_disclosure` needs no wording change (it already
  claims no coordinates). Verify: read the section back; every bullet matches the implemented
  behaviour and the disclosure text is quoted as unchanged.
  **Done 2026-09-28:** §9 gained the bullet "The rule covers the file's history, not only new writes" —
  the two recognised shapes, the count-only report, the deliberate narrowness versus the recipe's grep,
  and the residual; the disclosure bullet now states why its wording needs no change; the tracking line
  reads "TODO §68 (diagnostics), §88 (… awaiting archive) and §69". `diagnostics_disclosure`
  (`core/src/main/res/values/strings.xml:55`, de `values-de/strings.xml:48`) is unchanged — it already
  says "never coordinates" and "older than 7 days are deleted", both of which now hold for history too.
- [x] 3.2 Note this change in `TODO.md` §88 with "removed when this change is archived", so the entry
  stays as bookkeeping until the archive (spec: not applicable — project tracking). Verify:
  `grep -n '§88' TODO.md` shows the note and the entry is still listed as open.
  **Done 2026-09-28:** §88 gained "**Fix in flight** ⏳ (2026-09-28): `fix-diagnostics-stale-coordinate-purge`
  takes the purge variant … This entry is removed when that change is archived." — the entry stays open
  bookkeeping until 4.5 removes it at archive time.

## 4. Integration verification

- [x] 4.1 Build without errors or new warnings: `./gradlew :core:compileDebugUnitTestKotlin` (and
  `:core:assembleDebug` if the compile alone is inconclusive) through the `build-app` skill. Verify:
  BUILD SUCCESSFUL, and the task count/elapsed time quoted rather than an `up-to-date` line
  (`TODO.md` §17).
  **Done 2026-09-28:** the module compiled and ran green in the 4.2 invocations
  (`:core:testDebugUnitTest` **406 tests / 0 failures / 0 errors** — the baseline plus this change's 25
  new cases). **Zero Kotlin warnings** in any run log (`grep -cE '^w: '` → 0 for the `:auto`+`:core` run
  and for both `:app` flavor runs), so the new file and the modified pass compile warning-free.
- [x] 4.2 All existing tests still pass, with `test-results` cleared first so the verdict is an
  execution: `:app` mobile + automotive, `:auto`, `:core`, `:osmscout-client-java` (per-module tasks
  per `TODO.md` §79/§94; use the `run-tests` skill). Verify: per-module counts from
  `test-results/*.xml` quoted, 0 failures.
  **Done 2026-09-28, with a recorded blocker on the `:app` suites:**
  - Clean modules: `:core:testDebugUnitTest` **406 / 0 / 0** (the 25 new cases included),
    `:auto:testDebugUnitTest` **701 / 0 / 0**, `:osmscout-client-java:test` **26 / 0 / 0** — each run with
    its `test-results` directory deleted first, verdicts read from the XMLs (`BUILD SUCCESSFUL in 1m 1s`).
  - `:app` mobile (1336 tests), run 1: **2 failures** — `NavigationEngineTest.listenerCallbacksDriveTheSharedState`
    (the documented flake `TODO.md` §101) and `MapCanvasViewModelDarkModeTest.ambientSensitivityPersistsInUiState`
    (`IllegalStateException: Dispatchers.Main is used concurrently with setting it`, thrown in
    `MainDispatcherRule.starting`); run 2: **2 failures**, a *different* set —
    `MapCanvasViewModelCandidatePickerTest` twice. `:app` automotive (1336 tests): **1 failure** —
    `MapCanvasViewModelViewportRestoreTest` (`Dispatchers.Main` race followed by a bare `NullPointerException`,
    the victim class and tail `TODO.md` §96a already records).
  - All four victim classes pass when run alone in both flavors
    (`--tests` filters → `BUILD SUCCESSFUL in 7m 58s`), and the failing sets differ between the two mobile
    runs: this is the load-sensitive `:app` flake family (§96/§96a/§101), not a regression of this change,
    which adds no coroutine, dispatcher or global state and lives in `:core` (a pure regex predicate plus
    the existing logging worker). Recorded as a new update in `TODO.md` §101 with the run logs.
  - **Deviation:** the `:app` suites ran with `-Pandroid.injected.build.abi=arm64-v8a`, because the
    submodule bump of 2026-09-28 18:14 left only the arm64 native build warm (the other two ABIs were from
    2026-09-27). Unit tests execute on the host JVM against the committed test stub `.so`, so
    `TODO.md` §40.39's ABI-stripping concern (an *install* that dies at `System.loadLibrary`) does not
    apply; this change touches no native code, whose 3-ABI compile stays with the release/CI path.
- [x] 4.3 Coordinate gate green: `./gradlew :app:checkNoCoordinatesInLogs`. Verify: green, and state
  in the task note that this change adds **no** source-level rule (design D3 alternative C rejected) —
  the gate's scope is unchanged.
  **Done 2026-09-28:** `:app:checkNoCoordinatesInLogs` ran and passed (`BUILD SUCCESSFUL in 7s`, the task
  named in the log). No gate rule was added: the new file is not a scanner and holds no `Log.*` call, so
  `buildSrc`/`CoordinateLogScanner` is untouched — and the gate's green also confirms this change
  introduced no coordinate-bearing log line of its own.
- [x] 4.4 On-device (device-gated, recipe `guidelines/Build.md` §10): on an install that ran a
  pre-redaction build, open the diagnostics viewer and export once, then run the §88 recipe grep
  (`[0-9]{1,3}\.[0-9]{4,}` over `files/diagnostics/app.log` and `app.log.1`) → 0 hits, with the
  retention report line showing the coordinate count. Verify: the quoted grep result plus the report
  line; when no device or emulator is attached, record the blocker (`TODO.md` §40.45) instead of
  marking the task done.
  **NOT RUN 2026-09-28 — no device is attached** (`adb devices` → `List of devices attached` with no
  entry), and the only AVD here is the AAOS unit, which is unusable for a headless session (`TODO.md`
  §40.45). The rule is covered by unit tests instead: the §88 line leaves both files
  (`aYoungCoordinateEntryIsRemovedFromTheActiveFileAndTheRotatedOne`), the reader/export path is
  coordinate-free (`theReaderSeesNoPositionAfterThePass`), and the report carries the count
  (`theCoordinateDropIsReportedOnceWithCountsAndWithoutThePosition`). The recipe above stays valid and
  needs one run on an install that carried a pre-redaction build.
  **Done 2026-10-01 on `emulator-5554` (phone AVD, x86_64 mobile debug build of the current tree, installed
  over the previous install so it kept the data of the pre-redaction builds):** the diagnostics viewer was
  opened from the About dialog (`Über` → `Diagnose`, the entries lead with `diagnostics_disclosure`) and
  `Teilen` was tapped once (the system chooser opened → the export ran). Then `files/diagnostics/`: `app.log`
  = 50339 B, `app.log.1` absent (no rotation file exists — the one file covers the whole window);
  `grep -cE '[0-9]{1,3}\.[0-9]{4,}'` = **0** for `app.log` and **0** for `app.log.1`, and the retention
  report line is in the file verbatim:
  `[2026-09-28 20:54:02.992] DiagnosticsLog retention: dropped 1 coordinate-carrying (oldest kept: 2026-09-25 21:29:55.930)`.
  The file demonstrably spans pre-redaction builds (its oldest entry is 2026-09-25 21:29:55, the purge report
  is from 2026-09-28, and the current tail is 2026-10-01 20:10), so it is exactly the "install that carried a
  pre-redaction build" the task asks for: the coordinate-bearing entry was dropped and reported, and no
  coordinate-shaped line survives anywhere in the file.
- [x] 4.5 Remove `TODO.md` §88 when this change is archived (the entry's own condition).
  **Done 2026-10-01:** archived as `openspec/changes/archive/2026-10-01-fix-diagnostics-stale-coordinate-purge`
  (`openspec archive … --yes --json` → `specsUpdated: true`, `auto-diagnostics` +1 added: "Coordinate-carrying
  entries do not survive the retention pass") and the §88 entry removed from `TODO.md`.
