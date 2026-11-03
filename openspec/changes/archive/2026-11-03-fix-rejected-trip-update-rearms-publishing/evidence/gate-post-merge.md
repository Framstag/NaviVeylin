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

The merge added only main's own paths (`tools/feature-list/specs.json`, `tools/loop/**`, two `app/src/test`
files and `openspec/specs/unit-test-suite-runtime/spec.md`); this change's own files are unchanged by it, so the
focused cases the change's record carries still stand beside this run.
