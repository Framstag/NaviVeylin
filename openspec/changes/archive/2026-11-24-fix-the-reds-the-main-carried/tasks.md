# Tasks

One task edits a compiled production file (§7) and runs the gate; three tasks edit compiled test files and run
their suites. The full build is not re-run beyond the gate, and the structural checks and the affected suites
are. Each task states its own verification.

## 1. The 429 in the Robolectric fetch (proposal §1, design D1)

- [x] 1.1 Add `~/.m2/repository` to the "Cache Gradle" path list in `.github/workflows/build.yml`, and give
  the key a versioned `m2` component (`…-m2-v1-${{ hashFiles(…) }}`), because `actions/cache` saves a key only
  when it is absent: a key unchanged since before this change would restore the old, `.m2`-less entry.
  *Verified:* `yq -e . .github/workflows/build.yml` exits 0; the path is in the step's `path:` block and the
  key carries `m2-v1` (`evidence/gate-workflows.txt`).
- [x] 1.2 Add the step "Warm the Robolectric SDK jars" to `.github/workflows/build.yml`, before the Gradle run:
  a `for` loop over the three coordinates, a skip when the jar is present, `curl --fail --retry 8
  --retry-all-errors --output "$jar.part"`, a **guarded** `mv` into place, and a `::warning::` on failure — the
  guard because `run:` steps execute under `bash -e`, so an unguarded move would abort the build on a read-only
  cache volume. The comment names the modules that resolve each coordinate and the run the failure came from.
  *Verified:* the extracted step is 34 lines as `yq -r` emits it — 33 lines of step text at `build.yml:275–307`,
  plus the block scalar's trailing blank line — and `bash -n`-clean; run over the real `~/.m2` it prints `present:`
  for all three and exits 0; run over a scratch base with the same text it fetches all three (408 MB, 55.7 s),
  exits 0 and prints `present:` on the second run; all three URLs answer HTTP 200 to `curl --head`
  (`evidence/gate-workflows.txt`).
- [x] 1.3 Mirror the cache path, the key's `m2-v1` component and the warm step in `.github/workflows/sonar.yml`,
  whose "Scan main" step runs the same unit tests. *Verified:* `bash tools/check-sonar-workflow.sh` exits 0
  (5 modules, 17 exclusion groups); `yq -e .` on both files exits 0.
- [x] 1.4 Confirm the action pins and runner images are unaffected. *Verified:*
  `bash tools/check-action-runtimes.sh` exits 0 (22 pins across 4 workflows, Node 24, images pinned).
- [x] 1.5 Record the evidence under `evidence/gate-workflows.txt`. *Verified:* the file quotes each command's
  output and the exit statuses it prints (`EXIT_SONAR`, `EXIT_RUNTIMES`, `EXIT_WARM`, `EXIT2`).

## 2. The three rule routes (proposal §2, design D2)

- [x] 2.1 Add the `UI.md` §8d row to both routes whose globs reach the document (`app/src/main/java/**` and
  `guidelines/UI*.md`). *Verified:* `jq -e . tools/doc-routes.json` exits 0.
- [x] 2.2 Add the `BuildGate.md` §2b row to the `guidelines/BuildGate.*` route. *Verified:* as 2.1.
- [x] 2.3 Add the `MapFrames.md` §13 row to both routes whose globs reach the document. *Verified:* as 2.1.
- [x] 2.4 Run the route check. *Verified:* `bash tools/check-rule-routes.sh` prints "every glob matches the tree
  and all 658 named rule(s) are reached" and exits 0.
- [x] 2.5 Run the route check's own selftest. *Verified:* `bash tools/tests/check-rule-routes-selftest.sh` prints
  "all 13 cases passed" and exits 0.
- [x] 2.6 Record the evidence under `evidence/gate-rule-routes.txt`.

## 3. The reader-column order in the loop suite (proposal §3, design D3)

- [x] 3.1 Write the case that reproduces the defect: `driver#theReaderOrderIsNotPartOfTheComparison`, a
  `shared.tsv` row written `shared_thing\ta.sh\td.sh c.sh b.sh`, asserting no refusal.
  *Red-on-HEAD:* `/tmp/red-proof.sh` slices the comparison out of HEAD's file and out of the working tree and
  runs both over the case's fixture: HEAD prints two refusals (`shared_thing is read by b.sh, c.sh, d.sh and no
  row declares it` and `shared_thing is declared and no file a second one reads assigns it`), the fixed
  comparison prints nothing (`evidence/gate-loop-selftest.txt`).
- [x] 3.2 Start the derivation's selection sort at the first reader, and normalise the declared reader column into
  the same order. *Verified:* both named cases pass —
  `PASS driver#theReaderOrderIsNotPartOfTheComparison`, `PASS driver#theSharedNamesAreDeclared`.
- [x] 3.3 Revert-check the invariant the fix introduces: mutate the normalisation away, the new case must fail,
  restore, and the case must pass again. *Verified:* the mutation made the case FAILED (`1 of 1 cases failed`);
  the mutation reverts the declaration-side sort only — the derivation's `i = 1` has no mutation of its own,
  because reverting it cannot fail locally (the fixture's row is already in the derivation's order);
  the restored file's `sha256sum` is `880af467a8672086e6135e74894084fd758558cdf8ad5588a22eb84e2c79e6cf`
  (re-measured 2026-11-24; the earlier `59132532…` value is the file before the follow-up commit's comment
  edit) and the case passes (`evidence/gate-loop-selftest.txt`).
- [x] 3.4 Run the whole loop suite. *Verified:* `bash tools/loop/tests/loop-selftest.sh` prints "all 548 cases
  passed (JDK 21, offline, no token, no sandbox) — 543.37 s"; the run is quoted in
  `evidence/gate-loop-selftest.txt`.
- [x] 3.5 Confirm no bound regression. *Verified:* `PASS driver#noEngineFileExceedsItsBound`. That case does
  not bound this file — `tools/loop/layout.tsv` has no row for `tools/loop/tests/loop-cases.sh` — so the bound
  is unchanged because none is declared, not because the case measured it.
- [x] 3.6 Record the evidence under `evidence/gate-loop-selftest.txt`.

## 4. The settings case's read (proposal §4, design D4)

- [x] 4.1 Give the controller's storage in `setUpMap()` the compose rule's dispatcher, with the comment naming
  the race and the two runs it cost. *Verified:* the file compiles in the test source set.
- [x] 4.2 Drain the scheduler on every poll in `awaitStored`, so the row's write lands before the file is read.
  *Verified:* as 4.1.
- [x] 4.3 Run the changed cases. *Verified:* `:app:testMobileDebugUnitTest --tests
  '*SettingsSurfaceTest'` reports 23 cases, 0 failures.
- [x] 4.4 Meet `unit-test-suite-runtime` "A formerly flaky case is decided by its subject" by measurement, since
  this is a formerly load-dependent case: three forced repeats of the class (`-PforceTests --no-build-cache`) and
  the full `:app` module suite once. *Verified:* each repeat is `tests=23 failures=0 errors=0`; the full suite is
  `tests=2321 failures=0 errors=0` — the same executed count the red scan reported — with every XML timestamp
  after the fix's commit.
- [x] 4.5 Record the evidence under `evidence/gate-settings-surface.txt`.

## 5. The hotspot row's bound (proposal §5, design D5)

- [x] 5.1 Raise the `hotspot` row's bound to 2 000 000 and re-measure its comment: the largest watched file is
  1 685 967 bytes (`tools/loop/tests/loop-cases.sh`, 2026-11-24), it grew 266 610 bytes in the eight days since
  the bound was last taken, and the bound stays below the churned fixture's score (2 800 378). *Verified:*
  `jq -e . tools/sensors/sensors.json` exits 0; the comment names each figure and its date
  (`evidence/gate-sensors.txt`).
- [x] 5.2 Point mutation `8.31` at the bound the row now carries, and at a replacement that fails the intended
  assertion. *Verified:* `bash tools/sensors/tests/sensor-mutations.sh --only 8.31` prints "MUTATION 8.31: … red
  under the mutation, green after the restore" and "sensor-mutations: 1 behaved, 0 did not", exits 0. The old
  search text matched nothing: HEAD's registry carries no `"threshold": 1300000,` (`git show
  HEAD:tools/sensors/sensors.json | grep -c` → 0). The replacement moved from `5000` to `1000000`, because at
  5 000 the case's first assertion fires first (the 6 000-byte `once.txt`); at 1 000 000 the case fails with the
  CI message's own words — `the row's bound (1000000) is not above the largest file it watches (1685967 bytes)`
  — and the red run's own message line is appended to `evidence/gate-sensors.txt`.
- [x] 5.3 Run the whole sensor selftest. *Verified:* `bash tools/sensors/tests/sensor-selftest.sh` prints "all 94
  cases passed (JDK 21, offline, no token)" and exits 0, including `PASS hotspot#theBoundIsAboveOneChangedFile`.
- [x] 5.4 Record the evidence under `evidence/gate-sensors.txt`.

## 6. The unit-test classpath's 429 (proposal §6, design D6)

- [x] 6.1 Write `tools/gradlew-retry.sh`: run the command, re-run it only when the failed attempt's log names a
  Maven Central `429`, bounded in attempts and backoff, and return the command's own status otherwise.
  *Verified:* `bash -n tools/gradlew-retry.sh` exits 0 and the five stub runs in
  `evidence/gate-gradle-retry.txt` behave as specified.
- [x] 6.2 Run both workflows' "Run unit tests" step through the wrapper. *Verified:* `yq -e .` on both workflows,
  `tools/check-sonar-workflow.sh` and `tools/check-action-runtimes.sh` all exit 0; the two step lines are quoted
  in `evidence/gate-gradle-retry.txt`.
- [x] 6.3 Record the evidence under `evidence/gate-gradle-retry.txt`.

## 7. The diagnostics line that became five rows (proposal §7, design D7)

- [x] 7.1 Fold a logged message to one line at the log's own seam. *Verified:* the new case
  `DiagnosticsLogTest#aMessageThatSpansLinesIsOneEntry` fails on HEAD (`8 tests completed, 1 failed`) and passes
  after the fold; `:core:testDebugUnitTest --tests '*DiagnosticsLogTest'` is green
  (`evidence/gate-diagnostics-line.txt`).
- [x] 7.2 Re-run the red case's suite against the fold. *Verified:* `:auto:testDebugUnitTest --tests
  '*StartupScreensTest'` is green; the case's own red is CI-only and its mechanism is quoted from run
  `45366310469`.
- [x] 7.3 Record the evidence under `evidence/gate-diagnostics-line.txt`.

## 8. The source-switch case's registry (proposal §8, design D8)

- [x] 8.1 Build the case's `MapSourceRegistry` with `OfflineRepositoryFetcher()` instead of `HttpUrlFetcher()`,
  and remove the now-unused import. *Verified:* `:app:testMobileDebugUnitTest --tests
  '*MapManagerSourceSwitchPlanTest'` and `:app:testAutomotiveDebugUnitTest --tests
  '*MapManagerSourceSwitchPlanTest'` each report `tests="9" skipped="0" failures="0" errors="0"`; the red run's
  failure title and stack are quoted in `evidence/gate-source-switch.txt`.

## 9. The change's own record

(The review's original `5.1`–`5.3` are this section's `9.1`–`9.3`: the fifth, sixth and seventh causes were found
after review round 2 and were inserted above as §5, §6 and §7, and the eighth after round 7 as §8, so the
review's numbering and this section's differ by four.)

- [x] 9.1 Run `openspec validate fix-the-reds-the-main-carried --strict`. *Verified:* the command exits 0 and
  prints `Change 'fix-the-reds-the-main-carried' is valid`; the output is filed in `evidence/gate-change.txt`.
- [x] 9.2 Have a read-only reviewer confirm each cause against the diff and the evidence. *Verified:* nine
  verdicts are filed under `evidence/` — `review-verdict.md` (round 1: four BLOCKING items, all corrected),
  `review-verdict-2.md` (round 2: PASS on the first four causes), `review-verdict-3.md` (round 3: three BLOCKING
  items against causes 5, 6 and 7, all corrected), `review-verdict-4.md` (round 4: all seven causes CORRECT; three
  BLOCKING items against the change's own record — B4, B5, B6 — plus NOTEs N6 and N7, all corrected),
  `review-verdict-5.md` (round 5: all seven causes CORRECT; four BLOCKING items — B7, B8, B9, B10 — all stale
  quotations in the change's own record, all corrected), `review-verdict-6.md` (round 6: all seven causes
  CORRECT and B7–B10 RESOLVED; one BLOCKING item — B11, the gate totals summed four modules and dropped the two
  the run also executed — corrected, plus NOTE N8 recorded) and `review-verdict-7.md` (round 7: B11, N8 and the
  six verdicts RESOLVED; one BLOCKING item — B12, the range counts still said eleven commits and ten files after
  the eighth cause's commit landed — corrected, plus NOTEs 1–5 answered), `review-verdict-8.md` (round 8:
  B12 and NOTE 1 RESOLVED; two BLOCKING items — B13 and B14, both stale quotations in `evidence/gate-change.txt`
  — corrected) and `review-verdict-9.md` (round 9: B13 and B14 RESOLVED, **PASS, no LOOP-BLOCKING item** — the
  archive gate's answer).
- [x] 9.3 Land the change and quote the first green run of both red workflows. *Verified:* at HEAD `c76b38f9`
  both workflows are green — `Build Debug APK` run `45385198727` and `SonarCloud` run `45385198606`, each
  `conclusion: success`, each started 2026-11-24T11:33:03Z for that `headSha`. The Build run's five jobs are all
  `success`; its three previously-red steps (`Check the JVM-only test graph`, `Run unit tests`, `Publish the
  sensor inputs`) are green; its step-23 census prints 6 449 tests across six result directories, 0 failures,
  0 errors; `grep -c 'status code 429'` and `grep -c 'NamedGroup'` over its log are both `0`. The run's facts,
  the step-by-step table and the named exclusions are `evidence/gate-green-runs.txt`.
- [x] 9.4 Run the full gate on the commit that carries the seventh cause's fix. *Verified:*
  `./gradlew test -PforceTests -PjvmTestsOnly --no-build-cache` prints `BUILD SUCCESSFUL in 7m 17s` and exits 0;
  the run's 860 result files hold 6 449 tests, 0 failures, 0 errors — all five modules the run executed,
  `:osmscout-client-java` and `:architecture` included (`evidence/gate-full.txt`).

## Named exclusions

- The gate runs once, for the one compiled production file the change edits (`DiagnosticsLog.kt`, cause 7).
  Before that file was added the change touched no compiled production file; the compiled test files it edits
  ran their own suites (`:app:testMobileDebugUnitTest`, `:core:testDebugUnitTest`, and the source-switch case's
  class in both `:app` flavors), and the structural checks named above stand in for the rest
  (`guidelines/Build.md` §2, the scope block).
- The new case asserts the no-refusal direction only. The refusal direction is covered by the existing
  `driver#theSharedNamesAreDeclared`, which fails when a row's readers disagree with the derivation.
- The `:app` module suite is measured; `testAutomotiveDebugUnitTest` — the same source set, which the workflow's
  `test` job also runs — is not run locally. The changed test file is shared, so the case is the same; the
  automotive run is the CI's to prove.
- No device verification: nothing here changes a pixel, a template, a frame or a string.
- The warm step's invariant (task 1.2) has no local falsifier — its absence is only observable as a 429 — so its
  evidence is task 9.3's run.
- The retry wrapper's *effect* in CI (a throttled fetch no longer reddens the run) is only observable there, so
  that sentence's evidence is task 9.3's run; its behaviour is falsified offline by task 6.1's stub runs.
- The diagnostics case's own red (task 7.2) is CI-only: it passes locally before and after the fold, so the
  fold's red-first proof is task 7.1's unit case and the case's mechanism is run `45366310469`'s title list.
- The settings case's race (task 4.1) cannot be reproduced on this machine, where the read wins; its red evidence
  is the two runs on the same tree, quoted in the proposal.
- The hotspot bound's red report (task 5.1) is quoted from CI, not re-run against the old bound: the case's red
  direction is the mutation's red cycle (task 5.2), and the CI message is the report that found the red.
- The source-switch case's red direction (task 8.1) is CI-only: the case passes here before and after the fix,
  because nothing on this machine throttles the request its real fetcher made. Its red evidence is run
  `45380057626`'s title and stack; its green evidence is the two filtered runs of the class.
