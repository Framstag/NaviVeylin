# Gate — the correction round's run, once

`./tools/loop/gate.sh`, from the change root, once, after the two corrective edits and with no retry. 28 suites,
`2026-10-27T21:04Z` to `2026-10-27T21:28Z`. The console log is the item's `gate-r2.log` (1622 lines) and the
per-suite tallies are in `gate-tallies-round2/` beside this file.

```
LOOP-GATE: tests=803 failures=0 errors=1 (./gradlew test -PforceTests -PjvmTestsOnly --no-build-cache 128s, tools/loop-selftest.sh --from-git 6f61cffe4f82e91c38349f5a6787c28011466387 435s, bash tools/loop-mutations.sh --check 1s, tools/loop-mutations-selftest.sh 333s, tools/issue-tooling-selftest.sh 70s, tools/issues/check-metadata.sh 1s, tools/issues/metadata-mutations.sh --list 1s, tools/check-portability-selftest.sh 25s, tools/resolve-stale-selftest.sh 15s, tools/mutate.sh --mutations tools/resolve-stale-mutations.json --list 0s, tools/check-compose-sandbox-rule.sh 0s, tools/check-compose-sandbox-rule-selftest.sh 1s, tools/gen-feature-list-selftest.sh 16s, bash tools/gen-feature-list.sh --check-classification 4s, tools/check-doc-routes.sh 1s, tools/check-doc-routes-selftest.sh 1s, tools/mutate.sh --mutations tools/check-doc-routes-mutations.json --list 0s, tools/check-todo-references.sh 1s, tools/check-openspec-hygiene.sh 0s, tools/check-openspec-hygiene-selftest.sh 0s, tools/check-portability.sh 1s, tools/mutate-selftest.sh 1s, tools/mutate.sh --mutations tools/portability-mutations.json --list 0s, tools/mutate.sh --mutations tools/status-mutations.json --list 0s, tools/mutate.sh --mutations tools/create-mutations.json --list 0s, tools/mutate.sh --mutations tools/close-mutations.json --list 0s, tools/mutate.sh --mutations tools/duplicates-mutations.json --list 1s, tools/mutate.sh --mutations tools/decision-question-mutations.json --list 0s) — failing: ./gradlew test -PforceTests -PjvmTestsOnly --no-build-cache
```

The eleven shell suites' own summaries sum to the 803 the run reports; `tools/resolve-stale-selftest.sh` is the
change's own, `tests="36" failures="0" errors="0"` in the tally beside this file.

## The one error, and its attribution

`errors=1` is the compiled step alone: its tally is `tests="0" failures="0" errors="1"`, because the gate reads a
suite's numbers from the shell summary line (`<name>: all N cases passed` / `M of N cases failed`) and Gradle
prints `885 tests completed, 1 failed` instead — a failed step with no read summary is counted as an error, never
as a case (`tools/loop/gate.sh:281-287`).

The failing case, verbatim from `gate-r2.log:168-171`:

```
> Task :auto:testDebugUnitTest FAILED
SearchHistoryScreenTest > thePageRowOpensTheNextSliceOfTheHistory FAILED
    java.lang.AssertionError: no row titled More (3) in [query-1, query-2, query-3, query-4, query-5,
    query-6, More (5)]
```

Attribution, by the ritual in `guidelines/Build.md:568` (4d, “Attributing a red run”) — a failure that passes
alone may not be this change's, so both numbers are quoted:

- alone: `./gradlew :auto:testDebugUnitTest --tests '*SearchHistoryScreenTest*' -PforceTests --no-build-cache`
  → `BUILD SUCCESSFUL in 11s`.
- at module scope: `./gradlew :auto:testDebugUnitTest -PforceTests --no-build-cache` → `BUILD SUCCESSFUL in 34s`;
  the class's own report then read `tests="2" skipped="0" failures="0" errors="0"
  timestamp="2026-10-27T21:27:29.291Z"`, copied beside this file as `round2-searchhistory-green.xml`.

The observed row set is the fallback row count (six rows and `More (5)`), not the host's eight, so the case read a
render that landed before the host's capacity answer — the shape of the flake the archived change
`2026-10-27-fix-a-red-diagnostics-count-awaits-its-capacity` repaired for its sibling
(`openspec/changes/archive/2026-10-27-fix-a-red-diagnostics-count-awaits-its-capacity/proposal.md:13`), and that
change's diff does not reach `auto/src/test/java/com/naviveylin/auto/SearchHistoryScreenTest.kt`, nor does this
one.

Not this change's pathspec either: the correction touches `tools/issues/resolve-stale.sh`, the shipped spec and
the archived delta, and no compiled path — the compiled step runs because the gate's base
(`6f61cffe4f82e91c38349f5a6787c28011466387`, the merge-base with the base branch) carries earlier commits'
`app/src` and `auto/src` files, which is the same reason the round-1 run paid it.

## Residual

The failing case is a pre-existing timing defect in a test file this change does not touch. It is not repaired
here (out of the change's one capability delta and pathspec, and the round is corrections-only) and it is not
filed on the tracker, which this child does not write. It is recorded for a person: the run's own shell suites
and the change's 36 cases are green, and the compiled step is red only under the whole-project invocation.
