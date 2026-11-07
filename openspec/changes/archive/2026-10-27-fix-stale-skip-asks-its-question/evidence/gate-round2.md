# The gate, quoted — correction round

Run once, from the change root, 2026-10-27: `./tools/loop/gate.sh` (log: `<item>/gate-round2.log`). This is the
run that carries the correction round's edits to `tools/loop/labels.sh` and `tools/loop-cases.sh`; the first
round's own record stays untouched in `gate.md` / `gate-summary.xml` / `gate-tallies/`.

```
LOOP-GATE: tests=767 failures=0 errors=0 (./gradlew test -PforceTests -PjvmTestsOnly --no-build-cache 254s, tools/loop-selftest.sh --from-git 6f61cffe4f82e91c38349f5a6787c28011466387 448s, bash tools/loop-mutations.sh --check 1s, tools/loop-mutations-selftest.sh 337s, tools/check-portability-selftest.sh 25s, tools/check-compose-sandbox-rule.sh 0s, tools/check-compose-sandbox-rule-selftest.sh 0s, tools/gen-feature-list-selftest.sh 17s, bash tools/gen-feature-list.sh --check-classification 4s, tools/check-doc-routes.sh 0s, tools/check-doc-routes-selftest.sh 1s, tools/mutate.sh --mutations tools/check-doc-routes-mutations.json --list 0s, tools/check-todo-references.sh 1s, tools/check-openspec-hygiene.sh 0s, tools/check-openspec-hygiene-selftest.sh 1s, tools/check-portability.sh 1s, tools/mutate-selftest.sh 0s, tools/mutate.sh --mutations tools/portability-mutations.json --list 0s, tools/issue-tooling-selftest.sh 72s, tools/mutate.sh --mutations tools/status-mutations.json --list 0s, tools/mutate.sh --mutations tools/create-mutations.json --list 1s, tools/mutate.sh --mutations tools/close-mutations.json --list 0s, tools/mutate.sh --mutations tools/duplicates-mutations.json --list 0s, tools/mutate.sh --mutations tools/decision-question-mutations.json --list 0s)
```

The suites that carry cases, each from its tally under `gate-round2-tallies/` (the other 16 tallies in that
directory are the gate's `--list` and `--check` steps, which run no case):

```
tools/loop-selftest.sh --from-git <base>   tests="256" failures="0" errors="0" time="448"
bash tools/loop-mutations.sh --check       tests="198" failures="0" errors="0" time="1"
tools/loop-mutations-selftest.sh           tests="34" failures="0" errors="0" time="337"
tools/check-portability-selftest.sh        tests="29" failures="0" errors="0" time="25"
tools/gen-feature-list-selftest.sh         tests="119" failures="0" errors="0" time="17"
tools/issue-tooling-selftest.sh            tests="125" failures="0" errors="0" time="72"
tools/check-openspec-hygiene-selftest.sh   tests="6" failures="0" errors="0" time="1"
./gradlew test -PforceTests -PjvmTestsOnly --no-build-cache   tests="0" failures="0" errors="0" time="254"
```

The seven suites' case counts are the 767 the aggregate line states (256+198+34+29+119+125+6); the build step
contributes no case to the engine's count, because Gradle prints no `all N cases passed` line — its own result is
the `errors="0"` in the aggregate, against the first round's `errors="1"`, where
`BasemapSectionComposeTest > installedShowsDeleteButton` raised `CalledFromWrongThreadException` in a class
outside every touched path (`gate.md`). The two new cases are in this run:
`run#staleSkipAsksItsQuestion` and `run#staleSkipKeepsTheWorkersQuestion` pass inside the 256, and the label
vocabulary's new assertions pass inside the same case `labels#verdictToLabelMatrix`.

No per-suite tally carries a `timestamp` attribute, and `gate-round2.log` carries none either: the record's date
(2026-10-27) and the new cases appearing in the run are what say this run is after the fix, not a timestamp.

The pathspec: `LOOP_GATE_COMPILED_PATTERNS` (`tools/loop/loop.conf:291`) matches none of `tools/loop/*.sh`,
`tools/loop/prompts/*.md`, `tools/*.sh`, `*.md`, so no compiled path changed and the build ran only because the
gate's base is the loop's recorded base (`6f61cffe4f82e91c38349f5a6787c28011466387`).

`bash tools/loop/evidence-check.sh … --root .` printed `evidence-check: PASS`, with `C2 test tallies: 23 pairs
confirmed` and `C5 file:line: 502 resolved and in range, 11 unresolved or ambiguous` against the change after
these edits. The 11 are report-only basename ambiguities — a bare `run.sh:NNNN` (11 files of that name in the
tree) and `labels.sh:NN` (2) — not pointers into a file that does not hold the line.
