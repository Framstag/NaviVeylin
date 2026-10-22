# Evidence — fix-updates-queue-failure-signal

All runs from the change root `/home/tim/projects/NaviVeylin/.pi/worktrees/loop`, host `dingens`, 2026-10-22.

## The case counts, in the order they were taken

| # | run | command | result | artifact |
|---|---|---|---|---|
| 1 | the class on HEAD, before the change | `./gradlew :app:testMobileDebugUnitTest --tests 'com.naviveylin.ui.mapmanager.MapManagerUpdatesQueueTest' -PforceTests --no-build-cache` | `tests="5" failures="0"`, `2026-10-22T00:45:17.203Z`, BUILD SUCCESSFUL | `head-focused.log`, `head-focused-TEST-MapManagerUpdatesQueueTest.xml` |
| 2 | the class with the hardened case and the new one | same command | `tests="6" failures="0"`, `2026-10-22T00:45:49.857Z`, BUILD SUCCESSFUL | `after-focused.log` |
| 3 | falsification: `MapManagerViewModel.kt:411-412` answers `R.string.map_download_failed` instead of `R.string.update_install_failed` | same command | `tests="6" failures="1"`, `2026-10-22T00:46:10.460Z`, BUILD FAILED — `aFailedSwapIsReportedAndKeepsTheInstallation` alone | `mutation-focused.log`, `mutation-TEST-MapManagerUpdatesQueueTest.xml` |
| 4 | the gate, after the mutation was restored (`git status` named only the test file) | `./tools/loop/gate.sh` | exit 0, `BUILD SUCCESSFUL in 3m 32s` | `gate.log`, `gate-tally.txt`, `gate-*.xml`, `gate-gradle-tally.xml` |

Run 2's XML was overwritten in the build directory by run 3, so the change's green numbers are run 4's — the gate
runs the same command with the same flags and produces the class' XML in both flavours
(`gate-mobile-…xml` `tests="6" failures="0"` `2026-10-22T00:48:44.326Z`; `gate-automotive-…xml`
`tests="6" failures="0"` `2026-10-22T00:48:32.215Z`). Run 2's own log is kept for the order of the four runs.

Run 3's message, quoted from the XML:

```
org.junit.ComparisonFailure: with the text a failed installation gets, not a failed transfer's
expected:<The [update could not be installed — the map you had is unchang]ed> but was:<The [map could not be download]ed>
```

## The gate's numbers

`LOOP-GATE: tests=210 failures=0 (…, tools/loop-selftest.sh, tools/sensor-selftest.sh,
tools/check-action-runtimes-selftest.sh, tools/check-doc-routes.sh)` — the 210 are the four shell suites' own
cases. The build's own tally is the gradle run, `5750 tests, 0 failures, 0 errors, 0 skipped`:
`:app` mobile 2097, `:app` automotive 2097, `:auto` 866, `:core` 642, `osmscout-client-java` 48
(`gate-tally.txt`, per-module XMLs under `app/build/test-results/`, the gate's own record
`gate-gradle-tally.xml`). The gate's own tally record for the gradle command carries `tests="0"` because a gradle
run prints no `all N cases passed` line — `tools/loop/gate.sh:114-135` reads that summary and records the run's
exit instead, which is why the per-module tallies above are the numbers this change quotes.

## What is not measured here

The item's own rate per configuration and its neighbour bisect (its option 2) are not re-taken: the earlier worker
of this run took them (`.pi/issue-loop/items/217/evidence.md`, 20/20 isolated green, three full-suite runs of
2086 tests failing 69/184/5 cases on `EDQUOT` disk writes), and the recorded owner decision chose the hardening
over them. Nothing in this change is red on HEAD: HEAD already reports a failed swap correctly, and the delta is
the case that says so.
