The gate ran once, on the tree this change left, and it is red on one step the change does not touch.

Round 1's own gate line, quoted for the record — `LOOP-GATE: tests=706 failures=0 errors=1 (./gradlew test -PforceTests -PjvmTestsOnly --no-build-cache 297s, tools/loop-selftest.sh --from-git 4a4b3a95… 527s, bash tools/loop-mutations.sh --check 1s, … ) — failing: ./gradlew test` — not retained: it was round 1's gate, and its aggregate `gate-summary.xml` was removed with this round's gate, whose own `LOOP-GATE` line in `evidence/gate.log` is the record a reader should quote.

What each half is:

- **The step this change owes is green.** `tools/loop-selftest.sh` inside that gate run passed all 247 cases
  (`evidence/gate.log`, `evidence/gate-tallies/tools_loop-selftest.sh_--from-git_….xml`), and so did
  `loop-mutations.sh --check` (191 records) and every doc/hygiene/portability step. The change touches no
  compiled path, so by the profile's own rule (`guidelines/Build.md` §2, "verification follows the changed
  files") the compiled step is not owed by this delta.
- **The compiled step was selected by the gate's default base, not by this delta.** `gate.sh` resolves its base
  as `git merge-base HEAD origin/main`; in this worktree `origin/main` is `4a4b3a95`, while the run's own base
  is `2de16da0`. The paths since `4a4b3a95` include earlier items' `app/src/test` changes, so the gate ran
  Gradle. Nothing this change edits is compiled.
- **The failure is a network-dependent Robolectric test, and it passes alone.**
  `MapManagerSourceSwitchPlanTest > aSwitchThatKeepsTheTargetsBasemapKeepsItRegistered` failed in the full
  JVM with `java.lang.NoClassDefFoundError: Could not initialize class
  sun.security.ssl.NamedGroup$SupportedGroups` from a real `HttpsURLConnection` in
  `HttpUrlFetcher.text` ← `RepositorySource.availableRegions` (the tally:
  `evidence/gate-red-network-test.xml`, `tests=9 failures=1`). Rerun alone, the class passed:
  `./gradlew :app:testMobileDebugUnitTest -PforceTests --no-build-cache --tests
  com.naviveylin.ui.mapmanager.MapManagerSourceSwitchPlanTest` — `BUILD SUCCESSFUL in 4m 11s`
  (`evidence/gate-red-class-alone.log`). It is an environment/SSL-initialisation failure in a class this change
  neither adds nor edits, so it is a residual for its owner, not this change's red.
- **The gate was not re-run** (`guidelines/`/worker contract: the gate is evidence, not a thing to retry). The
  red line above is quoted as it was printed; the change's own red and green tallies are
  `evidence/red-on-base-*.xml` and `evidence/green-on-change-suite.xml`.

## Round 2 (2026-10-27T08:2xZ): the compiled step passed

This round's gate selected the same compiled step (`./gradlew test -PforceTests -PjvmTestsOnly --no-build-cache`,
321 s) and it **passed** — `evidence/gate-tallies/gradlew_test_-PforceTests_-PjvmTestsOnly_--no-build-cache.xml`
carries `tests="0" failures="0" errors="0"` (Gradle's tally line reports the step, not its cases). Round 1's
network residual therefore did not recur, and the hand-written `gate-summary.xml` that aggregated round 1's tallies
was removed with it: `evidence/gate.log`'s own `LOOP-GATE` line is this run's summary, and a file that quoted the
old run beside the new one is a number a reader cannot tell from a measurement (issue #244).

## Round 3 (2026-10-27, the round the decision's option 1 asked for): green

The item's decision was option 1 — re-run `./tools/loop/gate.sh` once on the tree being merged and close on that
tally. Two things differ from rounds 1 and 2: the branch is merged with `main` (so the delta is measured against
the base it will land on), and the base is passed explicitly.

- **`main` was merged in first.** The branch's base was `2de16da0`; `main` had moved to `de769144` (#320) and the
  only conflict was the `adapter#theChildIsStartedWithTheResultBound` hunk both items had written. It was resolved
  by keeping `main`'s text — the same behaviour, the shipped wording — so the change does not restate a
  correction another item landed.
- **Run 1 of this round: two failures, both inside the loop suite.** The gate's stream
  file is a `mktemp` its `EXIT` trap removes, so the two case names were lost with it; the same command re-run
  alone was green in full, `tools/loop-selftest.sh --from-git main` → `all 248 cases passed` (534 s,
  `evidence/loop-suite-green-rerun.log`). Recorded as the flake it is, not as a pass: the gate tally of that
  attempt is in this file's history (`evidence/gate-round2-red.log` is round 2's red; run 1 of this round is not
  retained, because it measured nothing the tree still has).
- **Run 2: green.** 20 steps, no failure — `tools/loop-selftest.sh --from-git main` 610 s
  (`evidence/gate-tallies/tools_loop-selftest.sh_--from-git_main.xml`, `tests="248" failures="0"`),
  `bash tools/loop-mutations.sh --check` 4 s (`tests="194" failures="0"`), `tools/loop-mutations-selftest.sh` 423 s
  (34), `tools/check-portability-selftest.sh` 44 s (29), `tools/issue-tooling-selftest.sh` 357 s (124), and the
  doc/hygiene/list steps. The transcript is `evidence/gate.log` and the tallies are `evidence/gate-tallies/`;
  round 2's red log and its tallies are kept beside them and named by their suffix.
- **No compiled step was selected**, and none is owed: with `main` as the base, no compiled path differs
  (`gate.sh --plan` lists the shell steps only), which is `guidelines/Build.md` §2's scope rule for a delta that
  touches `tools/**` and `*.md` alone. Rounds 1 and 2 saw Gradle only because their base was an older
  `origin/main`.
