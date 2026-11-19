# The gate's console identity for the run this change's record carries

`gate.log` in this directory is the step output the gate captured; the three lines that name the run and the tree
it measured are printed on the console, not written into the log. They are quoted here as they were printed,
and they are reproducible: `bash -c 'source tools/loop/lib.sh; loop_change_identity <tree> <base>'` prints the
same `head=`/`diff=` pair over the round's pathspec.

```
LOOP-GATE-BASE: main (from --base)
LOOP-GATE-TREE: head=e2f38592f42fbc0cf2bbeca2e6eaecff1aab653f diff=9c6cfd80b7df6af2
LOOP-GATE-TALLY: app/build/test-results/loop-gate run=2447687
LOOP-GATE-LOG: app/build/test-results/loop-gate/gate.log
LOOP-GATE: tests=46 failures=0 errors=0 (./gradlew test -PforceTests -PjvmTestsOnly --no-build-cache 622s, tools/check-compose-sandbox-rule.sh 0s, tools/tests/check-compose-sandbox-rule-selftest.sh 0s, bash tools/gen-feature-list.sh --check-classification 4s, tools/check-doc-values.sh 0s, tools/tests/check-doc-values-selftest.sh 0s, tools/check-doc-routes.sh 1s, tools/tests/check-doc-routes-selftest.sh 10s, tools/mutate.sh --mutations tools/tests/check-doc-routes-mutations.json --list 0s, tools/check-todo-references.sh 1s, tools/check-openspec-hygiene.sh 0s, tools/tests/check-openspec-hygiene-selftest.sh 0s)  (gate XMLs not retained)
```

The `tests=` figure is the count the shell suites' summary lines report (`tools/check-doc-values-selftest.sh` and
its siblings each contribute their case count); the Kotlin executions of this run are the per-case reports beside
this file — `TEST-com.naviveylin.ui.settings.SettingsControllerTest.xml` and
`TEST-com.naviveylin.ui.settings.SettingsRegistryTest.xml` (mobile, `12:19:24.671Z`/`12:18:06.578Z`, `tests="3"`/`tests="4"`),
their automotive twins (`12:13:35.283Z`/`12:12:36.121Z`), and `TEST-com.naviveylin.architecture.StructureReportTest.xml` (`12:09:17.698Z`). The
gradle step's own tally (`gradlew_test_-PforceTests_-PjvmTestsOnly_--no-build-cache.xml`) carries `tests="0"`
because the tally line counts what the suite's summary printed, and `./gradlew test` prints `BUILD SUCCESSFUL`
and a task count instead — the per-module reports are where its tests are counted, and they are cited above.

The digest `diff=9c6cfd80b7df6af2` was taken while the round's evidence refresh was still uncommitted, so three
states of the same non-evidence delta carry three engine digests: `9c6cfd80b7df6af2` at the gate,
`fea06a097a46cef6` recomputed at `e2f38592` in a pristine worktree, and `34fd73f28a58f347` at `11980300` after
the evidence commit. All three name the same diff text — `git diff main...HEAD ':(exclude,glob)**/evidence/**'`
is the 4816-line diff the round read — because the engine enumerates the change family's files beside the diff
pathspec, and this change's files under `evidence/` are the only thing the three states differ in.
`tools_tests_gen-feature-list-selftest.sh-run584422.xml` is the one report this run did not produce: the step was
not owed at this base, and its green is the earlier run's, kept under its run number rather than dropped.
