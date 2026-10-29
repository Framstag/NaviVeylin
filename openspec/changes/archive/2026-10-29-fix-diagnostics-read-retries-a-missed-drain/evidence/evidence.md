# Evidence — fix-diagnostics-read-retries-a-missed-drain

Item **#701**. All runs on the change root `/home/tim/projects/NaviVeylin/.pi/worktrees/loop`, base commit
`bbfd2a69` (the run's base), JDK 21, offline Gradle.

## The measurement the plan was set against

`./tools/loop/measure.sh --change fix-diagnostics-read-retries-a-missed-drain` (`measure.txt`):

```
LOOP-MEASURE-SUITE cases=276 cmd=tools/loop-selftest.sh --list
LOOP-MEASURE-CHANGE fix-diagnostics-read-retries-a-missed-drain scenarios=1 cases=1 unnamed=0
LOOP-MEASURE-CHECK ./gradlew test -PforceTests -PjvmTestsOnly --no-build-cache
LOOP-MEASURE-CHECK bash tools/gen-feature-list.sh --check-classification
LOOP-MEASURE-CHECK tools/check-doc-routes.sh
LOOP-MEASURE-CHECK tools/check-doc-routes-selftest.sh
LOOP-MEASURE-CHECK tools/mutate.sh --mutations tools/check-doc-routes-mutations.json --list
LOOP-MEASURE-CHECK tools/check-todo-references.sh
LOOP-MEASURE-CHECK tools/check-openspec-hygiene.sh
LOOP-MEASURE-CHECK tools/check-openspec-hygiene-selftest.sh
```

Surface, taken before the plan was fixed:

```
surface: 1 case added, 0 rewritten, 0 fixtures — grep -rn 'readEntries\|readEntriesAsync' --include=*.kt
*/src/main -> 7 lines, 2 production callers (auto/src/main/java/com/naviveylin/auto/DiagnosticsScreen.kt:178,
app/src/main/java/com/naviveylin/ui/about/AboutDialog.kt:210); grep -rl 'readEntries' --include=*.kt
*/src/test | wc -l -> 29 files, 51 call sites (50 before this case); grep -c '^#### Scenario:'
openspec/specs/auto-diagnostics/spec.md -> 42 existing scenarios
```

## Red on HEAD

`./gradlew --offline :core:testDebugUnitTest -PforceTests --no-build-cache --tests
"com.naviveylin.core.DiagnosticsLogWritePathTest"` (`red-on-head.log`, `red-on-head.xml`):

```
DiagnosticsLogWritePathTest > aMissedDrainIsRetriedAndRecorded FAILED
    java.lang.AssertionError: the missed drain is recorded: [[2026-10-29 08:43:28.886] TEST warm-up]
11 tests completed, 1 failed
BUILD FAILED in 20s
```

`tests="11" failures="1" errors="0"`, XML timestamp `2026-10-29T07:43:26.770Z`. The read waited its bound out,
dropped the entry the worker still held, and returned the one line that had already been flushed.

## Green after the fix

`./gradlew --offline :core:testDebugUnitTest --tests "com.naviveylin.core.DiagnosticsLogWritePathTest"
--rerun-tasks` (`green-after-fix.xml`, `revert-check-restored.log`): `BUILD SUCCESSFUL in 28s`,
`32 actionable tasks: 32 executed`, `tests="11" failures="0" errors="0"`, timestamp
`2026-10-29T07:46:21.054Z` — after the mutation was restored.

Module suites the two production callers sit in (`module-suites.log`) — the awk over their XMLs prints, per
directory: `:core` 643 tests, 0 failures, 0 errors and `:auto` 885 tests, 0 failures, 0 errors.

## The new line carries no coordinate

`./gradlew --offline :app:checkNoCoordinatesInLogs -PjvmTestsOnly` (`coordinates-check.log`):
`> Task :app:checkNoCoordinatesInLogs`, `BUILD SUCCESSFUL in 1s`, `1 executed`. The scanner reads
`core/src/main/java` (`app/build.gradle.kts:415-446`), so the two new `TAG` lines are inside the rule.

## Revert-check — one mutation

Mutation (restores HEAD's discarded boolean, marked in the source):

```kotlin
        // REVERT-CHECK MUTATION (task 2.3): the discarded awaitDrained result, as HEAD had it; restore after the run.
        awaitDrained()
```

`./gradlew --offline :core:testDebugUnitTest -PforceTests --no-build-cache --tests
"com.naviveylin.core.DiagnosticsLogWritePathTest"` (`revert-check.log`, `revert-check-mutated.xml`):

```
DiagnosticsLogWritePathTest > aMissedDrainIsRetriedAndRecorded FAILED
    java.lang.AssertionError: the missed drain is recorded: [[2026-10-29 08:45:50.109] TEST warm-up]
11 tests completed, 1 failed
BUILD FAILED in 10s
```

`tests="11" failures="1" errors="0"` — the failure is the marker assertion, not a premise. Restored, the
marker scan reads `0`, and the forced re-run above is green with a later timestamp.

## Gate — once

`./tools/loop/gate.sh` (`gate.log`, `gate-tallies/`), exit 0:

```
LOOP-GATE: tests=6 failures=0 errors=0 (./gradlew test -PforceTests -PjvmTestsOnly --no-build-cache 363s,
bash tools/gen-feature-list.sh --check-classification 4s, tools/check-doc-routes.sh 0s,
tools/check-doc-routes-selftest.sh 1s, tools/mutate.sh --mutations tools/check-doc-routes-mutations.json
--list 0s, tools/check-todo-references.sh 2s, tools/check-openspec-hygiene.sh 0s,
tools/check-openspec-hygiene-selftest.sh 0s)
```

The Gradle step's per-suite tallies (`gate-tallies/gradlew_test_-PforceTests_-PjvmTestsOnly_--no-build-cache.xml`
carries the step's `time="363"`; the module XMLs it produced carry):

| suite | tests | failures | errors |
|---|---|---|---|
| `:app` `testMobileDebugUnitTest` | 2135 | 0 | 0 |
| `:app` `testAutomotiveDebugUnitTest` | 2135 | 0 | 0 |
| `:auto` `testDebugUnitTest` | 885 | 0 | 0 |
| `:core` `testDebugUnitTest` | 643 | 0 | 0 |
| `osmscout-client-java` `test` | 112 | 0 | 0 |

## Validation

`openspec validate fix-diagnostics-read-retries-a-missed-drain --strict` (`openspec-validate.log`):

```
Change 'fix-diagnostics-read-retries-a-missed-drain' is valid
```
