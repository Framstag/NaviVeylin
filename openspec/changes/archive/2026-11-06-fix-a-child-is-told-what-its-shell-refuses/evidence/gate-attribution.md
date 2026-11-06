# The gate — read once, attributed, not re-run

`./tools/loop/gate.sh` from the change root, once, by the gate child this run started. The console log is
`evidence/gate.log` (1766 lines, `2026-11-06T23:01Z`, also `items/799/gate.log`), the per-suite tallies are
`evidence/gate-tallies/` (26 XMLs, also `items/799/*.xml`).

```
LOOP-GATE: tests=868 failures=0 errors=1 (tools/loop-selftest.sh --case driver#everyCaseDeclaresTheFilesItCanReach 11s, ./gradlew test -PforceTests -PjvmTestsOnly --no-build-cache 155s, tools/loop-selftest.sh --from-git 6ad5c485e6e3bc206a311ee7cc5fa2c70d3d59db 539s, bash tools/loop-mutations.sh --check 4s, tools/loop-mutations-selftest.sh 492s, tools/issue-tooling-selftest.sh 180s, tools/issues/check-metadata.sh 1s, tools/issues/metadata-mutations.sh --list 2s, tools/check-compose-sandbox-rule.sh 1s, tools/check-compose-sandbox-rule-selftest.sh 0s, bash tools/gen-feature-list.sh --check-classification 3s, tools/check-doc-routes.sh 1s, tools/check-doc-routes-selftest.sh 8s, tools/mutate.sh --mutations tools/check-doc-routes-mutations.json --list 0s, tools/check-todo-references.sh 1s, tools/check-openspec-hygiene.sh 0s, tools/check-openspec-hygiene-selftest.sh 0s, tools/check-portability.sh 1s, tools/check-portability-selftest.sh 23s, tools/mutate-selftest.sh 1s, tools/mutate.sh --mutations tools/portability-mutations.json --list 0s, tools/mutate.sh --mutations tools/status-mutations.json --list 0s, tools/mutate.sh --mutations tools/create-mutations.json --list 0s, tools/mutate.sh --mutations tools/close-mutations.json --list 0s, tools/mutate.sh --mutations tools/duplicates-mutations.json --list 0s, tools/mutate.sh --mutations tools/decision-question-mutations.json --list 0s) — failing: ./gradlew test -PforceTests -PjvmTestsOnly --no-build-cache
```

The 26 shell-suite tallies sum to the `tests=868` the line reports with `failures=0`, and the compiled step's own
tally is `tests="0" failures="0" errors="1"`: Gradle printed no shell summary (`919 tests completed, 1 failed`), so
`gate.sh` counts the failed step as an error and never as a case (`tools/loop/gate.sh:281-287`).

## The change's own case, in the gate's own tallies

The red test this change answers for, `prompt#thePhasePromptNamesWhatItsShellRefuses`, is carried green by the run:
`evidence/gate.log:623` is `PASS prompt#thePhasePromptNamesWhatItsShellRefuses`, and the suite that runs it tallied
`tests="344" failures="0" errors="0"` (`evidence/gate-tallies/tools_loop-selftest.sh_--from-git_6ad5c485e6e3bc206a311ee7cc5fa2c70d3d59db.xml`).
Its pre-change red is `evidence/red-on-HEAD.xml`, `tests="1" failures="1"`, `2026-11-06T22:44:43Z`.

## The one error, and its attribution

`errors=1` is the compiled step alone. The failing case, verbatim from `evidence/gate.log:185-186`:

```
SearchHistoryScreenTest > thePageRowOpensTheNextSliceOfTheHistory FAILED
    java.lang.AssertionError: the page begins with the first entry the previous page did not show expected:<[query-9, query-10, query-11]> but was:<[query-7, query-8, query-9, query-10, query-11]>
```

Not this change's pathspec: the change touches `tools/loop-cases.sh` and the three prompt skeletons
(`tools/loop/prompts/worker.md`, `worker-gate.md`, `worker-tail.md`) and no compiled path; the compiled step runs
because the gate builds the whole project. The failing case lives in
`auto/src/test/java/com/naviveylin/auto/SearchHistoryScreenTest.kt`, which this change does not open.

Attribution, by `guidelines/BuildEvidence.md:106` (4d, "Attributing a red run") — both numbers quoted:

- **alone**, after the gate: `./gradlew :auto:testDebugUnitTest --tests '*SearchHistoryScreenTest*' -PforceTests
  --no-build-cache` → `BUILD SUCCESSFUL in 11s`; the class's own report then read
  `tests="2" skipped="0" failures="0" errors="0" timestamp="2026-11-06T23:03:16.078Z"`, carried beside this file as
  `evidence/searchhistory-green-alone.xml`.
- **before this work**: the same case under the same whole-project step, in an earlier item
  (`openspec/changes/archive/2026-10-27-fix-parked-tombstone-keeps-its-settled-state/evidence/gate-round2.md:25`) —
  its gate line read 803 cases, no failures and the same one error, with
  `SearchHistoryScreenTest > thePageRowOpensTheNextSliceOfTheHistory FAILED`,
  green alone in 11s and green at module scope in 34s (`…/gate-round2.md:33`), whose diff, like this one, reached no
  compiled path (`…/gate-round2.md:41`).

The mechanism is the one that earlier entry named: the case reads the row set a paged history render produced, and
the set it read here began a slice earlier than the one it expected — the host's page answer landing after the
render, the same capacity-versus-render timing that change ruled out of its own pathspec
(`…/gate-round2.md:38-43`).

## Residual

A pre-existing paging flake in a test class this change does not touch. Not repaired here — out of this change's one
capability delta and pathspec — and not filed on the tracker, which the run's script owns. Recorded for a person: the
change's own case and the 26 shell suites are green, and the compiled step is red only under the whole-project
invocation.
