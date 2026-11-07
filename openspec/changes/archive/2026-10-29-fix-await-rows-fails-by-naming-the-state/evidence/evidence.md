# Evidence — fix-await-rows-fails-by-naming-the-state

Item **#700**. All runs on the change root `/home/tim/projects/NaviVeylin/.pi/worktrees/loop`, base commit
`c185e5b4` (the run's base), offline Gradle, one invocation per suite.

## The measurement the plan was set against

`./tools/loop/measure.sh --change fix-await-rows-fails-by-naming-the-state` (`measure.txt`):

```
LOOP-MEASURE-CHECK ./gradlew test -PforceTests -PjvmTestsOnly --no-build-cache
LOOP-MEASURE-CHECK bash tools/gen-feature-list.sh --check-classification
LOOP-MEASURE-CHECK tools/check-doc-routes.sh
LOOP-MEASURE-CHECK tools/check-doc-routes-selftest.sh
LOOP-MEASURE-CHECK tools/mutate.sh --mutations tools/check-doc-routes-mutations.json --list
LOOP-MEASURE-CHECK tools/check-todo-references.sh
LOOP-MEASURE-CHECK tools/check-openspec-hygiene.sh
LOOP-MEASURE-CHECK tools/check-openspec-hygiene-selftest.sh
LOOP-MEASURE-SUITE cases=276 cmd=tools/loop-selftest.sh --list
LOOP-MEASURE-GATE seconds=- reason=no tally under the gate's directory carries a time — the gate of this tree has not run
LOOP-MEASURE-CHANGE fix-await-rows-fails-by-naming-the-state scenarios=1 cases=1 unnamed=0
LOOP-MEASURE-SURFACE surface: 1 case added, 0 rewritten, 0 fixtures, 1 helper + 1 call site —
```

Surface, taken before the plan was fixed (`proposal.md` carries the commands with their counts):

```
surface: 1 case added, 0 rewritten, 0 fixtures, 1 helper + 1 call site —
tools/look.sh 'awaitRows' --files -> 2 files / 6 matches: 2 definitions (StartupScreensTest.kt:148,
DiagnosticsScreenTest.kt:154) and 4 call sites (StartupScreensTest.kt:117, DiagnosticsScreenTest.kt:255,
:291, :297); this change edits 1 definition and its 1 call site, the sibling's 3 are untouched.
tools/look.sh 'never published' -> 1 match (DiagnosticsScreenTest.kt:172), 0 of them in this class.
git show c185e5b4:openspec/specs/unit-test-suite-runtime/spec.md | grep -c '^#### Scenario:' -> 60
```

## Red on HEAD

`./gradlew --offline :auto:testDebugUnitTest -PforceTests --no-build-cache --tests
"com.naviveylin.auto.StartupScreensTest"` (`red-on-head.log`, `red-on-head.xml`):

```
StartupScreensTest > awaitRowsNamesTheStateThatNeverArrived FAILED
    java.lang.AssertionError: awaitRows returned rows for a state that never arrived, so the case fails later on a count
        at com.naviveylin.auto.StartupScreensTest.awaitRowsNamesTheStateThatNeverArrived(StartupScreensTest.kt:160)

7 tests completed, 1 failed
BUILD FAILED in 12s
```

`tests="7" failures="1" errors="0"` at `2026-10-29T08:09:13.343Z`. The red tree is HEAD plus the case and the
helper's parameter list: the helper's body is still HEAD's (`withTimeoutOrNull<Boolean>(windowMs) { … }`, then
`return titles`), so the case fails on exactly the assertion the fix adds. Its predicate is never satisfied and
`windowMs = 0L` takes the expiry path without waiting out the 5 s default.

## Green after the fix

Focused class (`green-after-fix.xml`): `BUILD SUCCESSFUL in 11s`, `tests="7" failures="0" errors="0"` at
`2026-10-29T08:09:38.126Z`. The helper now asserts
`assertNotNull("the startup screen never published $what: $titles", arrived)`
(`auto/src/test/java/com/naviveylin/auto/StartupScreensTest.kt:204`) and the case requires that message to name
the state it awaited (`three log rows`). The tree as left is verified by one more run of the class, after the
helper's KDoc stopped claiming "no wall-clock window" while taking one (`green-final.xml`, `green-final.log`):
`--rerun-tasks` → `60 actionable tasks: 60 executed`, `BUILD SUCCESSFUL in 26s`, `tests="7" failures="0"` at
`2026-10-29T08:14:42.907Z`.

Whole module, one invocation (`auto-suite.log`, `auto-suite-tally.txt`):
`./gradlew --offline :auto:testDebugUnitTest -PforceTests --no-build-cache` → `BUILD SUCCESSFUL in 37s`;
92 classes, 886 tests, 0 failures, 0 errors, XML timestamps `2026-10-29T08:09:56.422Z` …
`2026-10-29T08:10:30.206Z`. 886 against the 885 the module carried before the change
(`openspec/changes/archive/2026-10-29-fix-diagnostics-read-retries-a-missed-drain/evidence/evidence.md:105`: 
`:auto` `testDebugUnitTest` | 885 | 0 | 0) — exactly one case added, no case rewritten, no fixture realigned.

## The revert-check mutation

`revert-check.log`, `revert-check-mutated.xml`, `revert-check-restored.xml`: with the `assertNotNull` commented
out and the swallowed window restored (`REVERT-CHECK MUTATION`), the new case fails again at
`StartupScreensTest.kt:160` — `7 tests completed, 1 failed`, `tests="7" failures="1"` at
`2026-10-29T08:10:46.519Z`. Restored: `grep -c 'REVERT-CHECK MUTATION'` → `0`, `--rerun-tasks` →
`60 actionable tasks: 60 executed`, `tests="7" failures="0"` at `2026-10-29T08:11:29.442Z`.

## The gate, scoped by what the change touched

The delta is test source and openspec documents: no compiled file, so the whole-build gate is not owed and
`tools/loop/gate.sh` was not run. The structural checks stand in (`checks.log`):

| command | result |
|---|---|
| `openspec validate fix-await-rows-fails-by-naming-the-state --strict` | `Change '…' is valid`, exit 0 |
| `tools/check-openspec-hygiene.sh` | `OpenSpec artifacts are consistent: 1 open change(s) carry task markers …`, exit 0 |
| `tools/check-todo-references.sh` | `62 citation(s) resolve through the recorded table`, exit 0 |
| `tools/check-doc-routes.sh` | `all 155 section references resolve, all 4 skill references resolve`, exit 0 |
| `bash tools/gen-feature-list.sh --check-classification` | `specs read: 179, classified: 182, unclassified: 0, delta spec ids: 1`, exit 0 |

`gen-feature-list` reports `stale: 3` (`search-name-matching`, `search-result-row-icon`,
`start-map-selection`) — the names of specs that no longer exist, pre-existing on the base commit and untouched
by this change; the check exits 0 on them.

## What the change cites

| claim | file:line |
|---|---|
| the helper's window, before the fix | `auto/src/test/java/com/naviveylin/auto/StartupScreensTest.kt:154` (HEAD) |
| the swallowed return, before the fix | `auto/src/test/java/com/naviveylin/auto/StartupScreensTest.kt:166` (HEAD) |
| the sibling's assertion | `auto/src/test/java/com/naviveylin/auto/DiagnosticsScreenTest.kt:172` |
| the fix's assertion | `auto/src/test/java/com/naviveylin/auto/StartupScreensTest.kt:204` |
| the owning requirement | `openspec/specs/unit-test-suite-runtime/spec.md:146` |
| the guideline section | `guidelines/Build.md:996` |
| the CI record the item cites | `.pi/issue-loop/items/679/ci-red-40510522192.log:6` |
