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
