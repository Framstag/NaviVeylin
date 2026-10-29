# Tasks — fix-diagnostics-read-retries-a-missed-drain

## 1. Red case

- [x] 1.1 Add `DiagnosticsLogWritePathTest#aMissedDrainIsRetriedAndRecorded`: park the worker on a 60 s
      deadline, log one entry below the high-water mark, read, and require both the marker and the entry. Run
      it on HEAD. Verify: `DiagnosticsLogWritePathTest > aMissedDrainIsRetriedAndRecorded FAILED`,
      `java.lang.AssertionError: the missed drain is recorded: [[2026-10-29 08:43:28.886] TEST warm-up]`,
      `tests="11" failures="1"` at `2026-10-29T07:43:26.770Z` (`evidence/red-on-head.xml`,
      `evidence/red-on-head.log`).
      *(spec: `auto-diagnostics` — A read that misses its drain is retried and recorded)*

## 2. The fix

- [x] 2.1 In `DiagnosticsLog.readEntries()` honour `awaitDrained()`: on `false`, log
      `read drain expired after ${READ_DRAIN_TIMEOUT_MS}ms — retrying once` on `TAG`, retry through
      `flushNow()`, and on a second expiry log `read drain expired again …` before reading. Verify:
      `./gradlew --offline :core:testDebugUnitTest -PforceTests --no-build-cache --tests
      "com.naviveylin.core.DiagnosticsLogWritePathTest"` → `BUILD SUCCESSFUL`, `tests="11" failures="0"` at
      `2026-10-29T07:46:21.054Z` (`evidence/green-after-fix.xml`); the module suites the two callers sit in
      are `:core` 643 tests, 0 failures, 0 errors and `:auto` 885 tests, 0 failures, 0 errors
      (`evidence/module-suites.log`).
      *(spec: `auto-diagnostics` — A read that misses its drain is retried and recorded)*
- [x] 2.2 The new line carries identity only. Verify: `./gradlew --offline :app:checkNoCoordinatesInLogs
      -PjvmTestsOnly` → `BUILD SUCCESSFUL in 1s`, `1 executed` (`evidence/coordinates-check.log`); the scanner
      reads `core/src/main/java` (`app/build.gradle.kts:415-446`).
      *(spec: `auto-diagnostics` — Diagnostics carry no coordinates)*
- [x] 2.3 One revert-check mutation: the discarded boolean restored (`awaitDrained()` with its result dropped,
      the guard deleted, marked `REVERT-CHECK MUTATION`). The new case fails at its marker assertion:
      `aMissedDrainIsRecordedAndRetriedOnce FAILED … the missed drain is recorded: [[2026-10-29 08:45:50.109]
      TEST warm-up]`, `tests="11" failures="1"` (`evidence/revert-check.log`, `evidence/revert-check-mutated.xml`).
      Restored: `grep -c 'REVERT-CHECK MUTATION' core/src/main/java/com/naviveylin/core/DiagnosticsLog.kt` → `0`;
      re-ran forced `--rerun-tasks` → `32 actionable tasks: 32 executed`, `tests="11" failures="0"` at
      `2026-10-29T07:46:21.054Z`, after the restore.
      *(spec: `auto-diagnostics` — A read that misses its drain is retried and recorded)*

## 3. Suite

- [x] 3.1 Run the project's gate once, from the change root: `./tools/loop/gate.sh` → exit 0,
      `LOOP-GATE: tests=6 failures=0 errors=0 (./gradlew test -PforceTests -PjvmTestsOnly --no-build-cache
      363s, bash tools/gen-feature-list.sh --check-classification 4s, tools/check-doc-routes.sh 0s,
      tools/check-doc-routes-selftest.sh 1s, tools/mutate.sh --mutations tools/check-doc-routes-mutations.json
      --list 0s, tools/check-todo-references.sh 2s, tools/check-openspec-hygiene.sh 0s,
      tools/check-openspec-hygiene-selftest.sh 0s)`. The Gradle step's own tallies: `:app` mobile 2135 tests,
      0 failures, 0 errors; `:app` automotive 2135 tests, 0 failures, 0 errors; `:auto` 885 tests, 0 failures,
      0 errors; `:core` 643 tests, 0 failures, 0 errors; `osmscout-client-java` 112 tests, 0 failures,
      0 errors — 5910 tests, 0 failures, 0 errors (`evidence/gate.log`, `evidence/gate-tallies/`, and the
      per-suite block in `evidence/evidence.md`).

## 4. Evidence and archive

- [x] 4.1 Copy every cited XML into the change's `evidence/` directory and into
      `/home/tim/projects/NaviVeylin/.pi/issue-loop/items/701`, then run
      `bash tools/loop/evidence-check.sh openspec/changes/archive/2026-10-29-fix-diagnostics-read-retries-a-missed-drain --root .`
      after the archive. Verify: both runs report `PASS` — `ok C1 §references`, `ok C2 test tallies: 10 pairs
      confirmed`, `ok C3 /tmp pointers: none cited`, `ok C4 mutations`, `ok C5 file:line: 389 resolved and in
      range` (`evidence/evidence-check.log` pre-archive, `evidence/evidence-check-archived.log` post-archive).
- [x] 4.2 Validate and archive the change; confirm the delta merged into
      `openspec/specs/auto-diagnostics/spec.md` and the directory exists under `openspec/changes/archive/`.
      Verify: `openspec validate fix-diagnostics-read-retries-a-missed-drain --strict` exits 0 with
      `Change 'fix-diagnostics-read-retries-a-missed-drain' is valid` (`evidence/openspec-validate.log`);
      `openspec archive … --yes` reports `auto-diagnostics: update`, `+ 1 added`, and the merged capability spec
      carries the requirement at `openspec/specs/auto-diagnostics/spec.md:293`.
- [x] 4.3 Leave the tree as the change leaves it: no commit, no push, no version control, no tracker write —
      the run's script owns those.
