# Evidence — the gate of fix-report-splits-the-spend-by-phase

Every number below is carried by a JUnit XML in this directory (`gate-tallies/`, plus the failing class's own
carrier at the top level); the gate's own transcript is `gate.log` beside this file. Nothing here was re-derived
in the tail: the gate was run once, in the gate child, and its verdict line is quoted below where it printed.

## Part 1 — The gate, run once, and its verdict

`./tools/loop/gate.sh` in the change root, on the tree carrying this change (`aac503e6`, base
`83d8cea34fb539ae28059ecc7bcd926206ee528e`). Transcript: `gate.log` (1 792 lines, 189 kB). Its verdict line,
verbatim, at `gate.log:1792`:

```
LOOP-GATE: tests=876 failures=0 errors=1 (tools/loop-selftest.sh --case driver#everyCaseDeclaresTheFilesItCanReach 11s, ./gradlew test -PforceTests -PjvmTestsOnly --no-build-cache 162s, tools/loop-selftest.sh --from-git 6ad5c485e6e3bc206a311ee7cc5fa2c70d3d59db 542s, bash tools/loop-mutations.sh --check 4s, tools/loop-mutations-selftest.sh 506s, tools/issue-tooling-selftest.sh 181s, tools/issues/check-metadata.sh 1s, tools/issues/metadata-mutations.sh --list 1s, tools/check-compose-sandbox-rule.sh 1s, tools/check-compose-sandbox-rule-selftest.sh 0s, bash tools/gen-feature-list.sh --check-classification 4s, tools/check-doc-routes.sh 1s, tools/check-doc-routes-selftest.sh 8s, tools/mutate.sh --mutations tools/check-doc-routes-mutations.json --list 0s, tools/check-todo-references.sh 1s, tools/check-openspec-hygiene.sh 0s, tools/check-openspec-hygiene-selftest.sh 0s, tools/check-portability.sh 1s, tools/check-portability-selftest.sh 23s, tools/mutate-selftest.sh 1s, tools/mutate.sh --mutations tools/portability-mutations.json --list 0s, tools/mutate.sh --mutations tools/status-mutations.json --list 0s, tools/mutate.sh --mutations tools/create-mutations.json --list 0s, tools/mutate.sh --mutations tools/close-mutations.json --list 0s, tools/mutate.sh --mutations tools/duplicates-mutations.json --list 0s, tools/mutate.sh --mutations tools/decision-question-mutations.json --list 0s) — failing: ./gradlew test -PforceTests -PjvmTestsOnly --no-build-cache
```

The line sums 876 cases over the gate's suites, **no failures and one error**; the error is the Gradle step,
whose own tally is `gate-tallies/gradlew_test_-PforceTests_-PjvmTestsOnly_--no-build-cache.xml`:

| step | tally | time |
|---|---|---|
| `./gradlew test -PforceTests -PjvmTestsOnly --no-build-cache` | tests="0" failures="0" errors="1" | 162 s |

(Gradle's tally reports the step, not its cases — which is why the failing class's own carrier, part 2, is the
number this red is read on.)

Every other step of the gate passed; their names and seconds are the `gate:` lines of `gate.log`.

**The gate was not re-run by this tail, and no step of it was retried.** `gate.log:254` reads `BUILD FAILED in
2m 41s`.

## Part 2 — The one red, attributed to work this change does not touch

Read mechanically with `tools/gate-attribute.sh gate.log`:

```
verdict: tests=876 failures=0 errors=1 — 1 failing step
step: ./gradlew test -PforceTests -PjvmTestsOnly --no-build-cache
case: MapManagerSourceSwitchPlanTest > aSwitchThatKeepsTheTargetsBasemapKeepsItRegistered
file:line: SSLConfiguration.java:191
error: java.lang.NoClassDefFoundError: Could not initialize class sun.security.ssl.NamedGroup$SupportedGroups
flake: not recorded — this tree keeps no flake list
rerun: ./gradlew :app:testAutomotiveDebugUnitTest --tests "MapManagerSourceSwitchPlanTest" -PforceTests -PjvmTestsOnly --no-build-cache
```

Where it printed: `gate.log:179` (`MapManagerSourceSwitchPlanTest > aSwitchThatKeepsTheTargetsBasemapKeepsItRegistered
FAILED`), `gate.log:241` (`2250 tests completed, 1 failed`) and `gate.log:243`
(`> Task :app:testAutomotiveDebugUnitTest FAILED`). The class's own carrier in this directory,
`TEST-automotive-com.naviveylin.ui.mapmanager.MapManagerSourceSwitchPlanTest.xml`:
tests="9" failures="1" errors="0", `2026-11-07T02:24:22.804Z` — the other eight cases of the same class are green
in the same file, and the one failure carries the `NoClassDefFoundError` above.

- **Not on this change's paths.** The change touches five files (`git diff --stat
  83d8cea34fb539ae28059ecc7bcd926206ee528e..HEAD`): `tools/loop/state.sh`, `tools/loop/report.sh`,
  `tools/loop-cases.sh`, `tools/loop-mutations.sh`, `guidelines/Backlog.recipes.md` — plus this change's own
  `openspec/` delta. The red class
  `app/src/test/java/com/naviveylin/ui/mapmanager/MapManagerSourceSwitchPlanTest.kt:90` and the frames that
  raised it (`HttpUrlFetcher.kt:65`, `RepositorySource.kt:26`, `MapManagerViewModel.kt:1043`) are none of them,
  and no file of this change compiles into the `:app` module at all.
- **The mechanism is the JVM's TLS initialisation, not the case's logic.** The throwable is an
  `NoClassDefFoundError` for `sun.security.ssl.NamedGroup$SupportedGroups`, raised while a real
  `HttpsURLConnection` builds its `SSLConfiguration` on the region-list fetch the case starts at
  `MapManagerSourceSwitchPlanTest.kt:96` (`viewModel.selectSource(repository)` →
  `RepositorySource.availableRegions`). The class's own eight other cases — including the four that assert the
  same switch's plan and dialog — pass in the same run.
- **It was red before this work.** The same case with the same `NoClassDefFoundError` is recorded red before
  this item's first gate in this tree:
  `openspec/changes/archive/2026-10-27-fix-the-tail-is-admitted-on-the-cheapest-measured-outcome/evidence/gate-attribution.md:17`
  (same case, same `NamedGroup$SupportedGroups`, and the same class green when rerun alone at
  `:23`), `openspec/changes/archive/2026-10-18-fix-parked-state-race/report.md:82`, which names it a local JDK
  artifact, and the change archived the day before this item's base,
  `openspec/changes/archive/2026-11-06-motorway-destination-hint/evidence/evidence.md:26` — part 2 of that
  change's evidence is this exact case, this exact error, in a gate run that took the same step red.
- **It stays red after this work, for the same reason**, and this gate is the measurement of it: the run above
  was taken on the tree carrying this change, and the red is what it read. The diff adds no network path, no JDK
  class and no map-source code, so nothing this change does can initialise — or fail to initialise — that class.

So the red is named here rather than repaired, and it does not block: the affected cases are this change's own
loop cases (part 3), which the gate's own legs ran green.

## Part 3 — The change's own results, as the gate and its runs produced them

Fresh from this gate run, in `gate-tallies/`:

| carrier | tally | time |
|---|---|---|
| `tools_loop-selftest.sh_--case_driver#everyCaseDeclaresTheFilesItCanReach.xml` (the working tree's own loop case) | tests="0" failures="0" | 11 s |
| `tools_loop-selftest.sh_--from-git_6ad5c485e6e3bc206a311ee7cc5fa2c70d3d59db.xml` | tests="347" failures="0" | 542 s |
| `bash_tools_loop-mutations.sh_--check.xml` | tests="265" failures="0" | 4 s |
| `tools_loop-mutations-selftest.sh.xml` | tests="34" failures="0" | 506 s |

The `--from-git` leg runs the loop suite at the gate's pinned ref `6ad5c485`, which is an ancestor of this
change (`git log --oneline -1 6ad5c485` — `fix: a-child-has-a-working-search (#796)`) and therefore does not carry
this change's two new cases. Those were measured by the runs this change kept beside this file:

| result | carrier | tally | timestamp |
|---|---|---|---|
| the two new cases green on the change | `after-fix-retry.log` | `PASS report#theSpendIsSplitByPhaseRoleAndGate`, `PASS report#aStreamWithoutDurationsPrintsDash`, tests=1 failures=0 each | `2026-11-07T02:16:40Z` |
| the same two cases red on the base `83d8cea3` | `red-on-HEAD-retry.xml` | tests=1 failures=1 each | `2026-11-07T02:14:38Z` |

`red-on-HEAD-retry.xml:6` carries the first case's failure (`expected '7500', got 'null'`) and `:12` the second
(`'- · no item recorded a phase duration' not found in: `) — the red the item's `LOOP-RED-TEST` names
(`report#theSpendIsSplitByPhaseRoleAndGate`, XML tests=1 failures=1, `2026-11-07T02:14:38Z`).

## Part 4 — What this tail did not measure

The gate was read, not rerun: the rerun `tools/gate-attribute.sh` derived
(`./gradlew :app:testAutomotiveDebugUnitTest --tests "MapManagerSourceSwitchPlanTest" …`) was **not** executed,
because a step of the item's one gate run is not the tail's to spend and a green solo rerun would not change the
attribution in part 2.
