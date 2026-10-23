# Evidence — `fix-jvm-test-run-needs-no-native-tree` (issue #270)

Base `f5f2a2a76e3adc73e377b0bf2747718ac17dca76`, worktree `.pi/worktrees/loop`, 2026-10-23.

## The measured block, as it was printed

`./tools/loop/measure.sh`, run once from the change root before the plan:

```
LOOP-MEASURE-PROFILE /home/tim/projects/NaviVeylin/.pi/worktrees/loop/tools/loop/loop.conf
LOOP-MEASURE base=f2d27735818acd6d05095a9b331f4ee2bae1bb74 paths=114 insertions=8668 deletions=67
LOOP-MEASURE-CHECK tools/loop-selftest.sh --from-git f2d27735818acd6d05095a9b331f4ee2bae1bb74
LOOP-MEASURE-CHECK bash tools/loop-mutations.sh --check
LOOP-MEASURE-CHECK tools/loop-mutations-selftest.sh
LOOP-MEASURE-CHECK bash tools/gen-feature-list.sh --check-classification
LOOP-MEASURE-CHECK tools/check-doc-routes.sh
LOOP-MEASURE-CHANGE ambiguous open=fix-loop-ended-child-tree-is-not-committed,fix-loop-label-write-failure-is-recorded,fix-loop-selftest-runs-cases-in-a-pool — 3 changes are open, so the block names them and measures none: pass --change <name>
LOOP-MEASURE-END
```

The block could not measure this change: three changes of other items were open in the worktree, so it named them
instead of measuring a stranger's delta, and the paths and tallies it printed belong to the newest *archived*
change, not to this one. The suite list it printed is nevertheless this delta's — `app/build.gradle.kts` is a
compiled path and `tools/loop/loop.conf` is an engine path — and the gate below ran exactly those commands.

Surface count taken before the plan (commands and their numbers are in `proposal.md`): 3 shell cases added, one
buildSrc class of 7 cases added, 0 cases rewritten, 0 fixtures realigned; 3 gate-recipe sites edited out of the 8
files that mention `forceTests`.

## Red on the base

`red-HEAD.log` — the decision's command, 26.6 s, exit 1:

```
$ VCPKG_ROOT=/tmp/i270/no-vcpkg-here ./gradlew test -PforceTests --no-build-cache
> Could not create task ':app:mergeNativeSbom'.
   > vcpkg installed-status file not found at /tmp/i270/no-vcpkg-here/installed/vcpkg/status — run ./setup-vcpkg.sh first.
BUILD FAILED in 26s
3 actionable tasks: 3 executed
```

## Green on this tree

- `green-full-run.log` — the same command with the property, **exit 0, 9m 52s**, and no
  `:app:generateLicenseAssets…` / `:app:generateSbom…` / `:app:mergeNativeSbom` task in the executed set:
  `VCPKG_ROOT=/tmp/i270/no-vcpkg-here ./gradlew test -PforceTests -PjvmTestsOnly --no-build-cache`.
- `suite.log` — `bash tools/check-jvm-test-graph-selftest.sh`: `all 3 cases passed` (5 s).
- `unit-jvm-only-test-run.xml` — `JvmOnlyTestRunTest`, `tests=7 skipped=0 failures=0 errors=0`,
  `timestamp="2026-10-23T20:16:27.713Z"`.
- `green-dry-run.log`, `green-refusal.log`, `green-merge-task.log` — the three probes on their own (exit 0;
  exit 1 with the refusal naming `:app:assembleMobileDebug`; exit 1 naming `installed-status file not found` and
  **not** `Could not create task`).
- `revert-check.md` — three mutations, each named case red for the stated reason, byte-identical restore
  (`sha256sum`), green again.

## The gate

`./tools/loop/gate.sh`, run once, `gate.log`. Its own summary line reports the suite set it ran with **one** red
case of the 372 shell cases: the failing suite is
`tools/loop-selftest.sh --from-git f2d27735818acd6d05095a9b331f4ee2bae1bb74` — its tally is `tests=202 failures=1`
(`tools_loop-selftest.sh_--from-git_f2d27735818acd6d05095a9b331f4ee2bae1bb74.xml`) — and the case is
`run#aWriterTheRunCanPlaceBlocksTheItem`, "expected 'blocked', got 'done'". Every other suite the gate owed is
green, including `tools/check-jvm-test-graph-selftest.sh` (`tests=3 failures=0`).

**That case is red at the base and no path this change touches decides it.** `tools/loop/run.sh:90` reads
`LOOP_STRICT_OUTSIDE_WRITE_DEFAULT`, which is `off` in the base's own profile
(`git show f5f2a2a:tools/loop/loop.conf` → line 37, `LOOP_STRICT_OUTSIDE_WRITE_DEFAULT=off`), while the case
(#286's, `tools/loop-cases.sh:3525`) expects `blocked` and never passes `--strict-outside-write yes`
(`tools/loop/run.sh:23` declares the flag). This change edits `loop.conf`'s `LOOP_GATE_COMPILED_CMD` and
`LOOP_GATE_SUITES` only. Three focused runs of the case, unchanged tree: 3 of 3 red. Repairing it — the flag on
the case, or the recorded default — is another item's decision (#286's case against #274's recorded default), so
this change does not "adjust the test" to go green.

The compiled command inside the gate ran green (`gradlew_test_-PforceTests_-PjvmTestsOnly_--no-build-cache.xml`,
403 s) and the module tallies are `module-tallies/`: `:app` mobile 2105/0/0, `:app` automotive 2105/0/0, `:auto`
880/0/0, `:core` 646/0/0, `:osmscout-client-java` 48/0/0, `buildSrc` 7/0/0 — 5 791 tests, 0 failures.

## What is not asserted

- The APK's `licenses/` assets are not re-asserted by a shell case: the refusal is a pure decision
  (`JvmOnlyTestRunTest#everyPackagingEntryPointIsRefused`) and CI's assemble/SBOM/policy steps run with the vcpkg
  tree present, which is the strict path §9 names.
- No device and no emulator: nothing here renders.
