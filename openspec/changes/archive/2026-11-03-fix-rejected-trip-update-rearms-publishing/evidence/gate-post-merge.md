# The gate after the base moved: green on every step

The round-2 gate (`gate-round2-attribution.md`) carried one red step, `bash
tools/gen-feature-list.sh --check-classification`, attributed there to two unclassified delta spec ids that
belonged to other open changes. The base then moved: main's `00bdd407` *fix: classify the two spec ids the
classification gate owed* wrote those two rows (`auto/off-route-indicator`, `tooling-test-layout`) into
`tools/feature-list/specs.json`. This change's branch merged that base (`main` = `e63f6b45`; the item branch's
merge commit that carries it is the one this evidence file lands in), so the red step was re-run and the whole
gate with it.

Command, from the change root, against the merged base:

```
bash tools/loop/gate.sh --base main
```

Its one line, verbatim (`2026-11-03T19:46Z`):

```
LOOP-GATE: tests=41 failures=0 errors=0 (./gradlew test -PforceTests -PjvmTestsOnly --no-build-cache 514s, bash tools/gen-feature-list.sh --check-classification 4s, tools/check-doc-routes.sh 1s, tools/check-doc-routes-selftest.sh 10s, tools/mutate.sh --mutations tools/check-doc-routes-mutations.json --list 0s, tools/check-todo-references.sh 1s, tools/check-openspec-hygiene.sh 0s, tools/check-openspec-hygiene-selftest.sh 0s)
```

Every step is green, the previously red one included, and no step is `FROM-CACHE`: the compiled step ran with
`--no-build-cache`. The tallies are `gate-post-merge-gradlew-test.xml` (`tests="0" failures="0" errors="0"
time="514"`, system-out `BUILD SUCCESSFUL in 8m 34s 167 actionable tasks: 148 executed, 19 up-to-date`) and
`gate-post-merge-gen-feature-list.xml` (`tests="0" failures="0" errors="0"`, system-out
`stale: 5 car-only areas: 1 highlights: 19` — the stale rows are the three pre-existing entries main's own
commit message calls not-errors, and the run no longer reports a failed classification).

The merge added only main's own paths — `tools/feature-list/specs.json`, `tools/loop-cases.sh`,
`tools/loop-mutations.sh`, `tools/loop/loop.conf`, `tools/loop/run.sh`,
`openspec/specs/unit-test-suite-runtime/spec.md`, `openspec/changes/archive/2026-11-03-fix-coordinate-check-reads-the-records-fields/**`
and three `app/src/test` files (`NavigationEngineCalculationStateTest.kt`, `NavigationEngineRerouteTest.kt`,
`test/DiagnosticsRecords.kt`; measured with `git diff --name-only 4367cb4d..e63f6b45`); this change's own files
are unchanged by it, so the
focused cases the change's record carries still stand beside this run.

## The run after task 6.2's case and scenario

Task 6.2 adds one case to `NavigationManagerControllerTest` and one scenario to the delta and the merged
capability, so the gate was run once more over the final tree (the run's tallies are stamped
`2026-11-03T19:58:42Z`–`19:58:56Z`):

```
LOOP-GATE: tests=41 failures=0 errors=0 (./gradlew test -PforceTests -PjvmTestsOnly --no-build-cache 308s, bash tools/gen-feature-list.sh --check-classification 4s, tools/check-doc-routes.sh 1s, tools/check-doc-routes-selftest.sh 8s, tools/mutate.sh --mutations tools/check-doc-routes-mutations.json --list 0s, tools/check-todo-references.sh 1s, tools/check-openspec-hygiene.sh 0s, tools/check-openspec-hygiene-selftest.sh 0s)
```

Its compiled-step tally is `gate-final-gradlew-test.xml` (`tests="0" failures="0" errors="0" time="308"`,
system-out `BUILD SUCCESSFUL in 5m 7s`, 18 executed, `--no-build-cache`); the earlier run's tallies above stand
as the record of the merge itself.

## The run over the final tree, after the guideline rule

The final pass also states the bound where the guideline owns it (`guidelines/DesignCrossVariant.md` §8a,
"Rule: a rejected trip update re-arms publishing"), which moves that section's measured cost from 10.5 kB to
11.0 kB and so updates both `AGENTS.md` routing rows that price it. That is a documentation delta, and its
checks are `tools/check-doc-routes.sh` (329 references resolve) and its 34-case self-test; the whole gate was
run once more anyway, over the tree as it now stands (`2026-11-03T20:12Z`):

```
LOOP-GATE: tests=41 failures=0 errors=0 (./gradlew test -PforceTests -PjvmTestsOnly --no-build-cache 530s, bash tools/gen-feature-list.sh --check-classification 15s, tools/check-doc-routes.sh 4s, tools/check-doc-routes-selftest.sh 18s, tools/mutate.sh --mutations tools/check-doc-routes-mutations.json --list 0s, tools/check-todo-references.sh 3s, tools/check-openspec-hygiene.sh 1s, tools/check-openspec-hygiene-selftest.sh 0s)
```

Tally: `gate-final-tree-gradlew-test.xml` (`tests="0" failures="0" errors="0" time="530"`, `BUILD SUCCESSFUL in
8m 49s`, 18 executed, `--no-build-cache`).
