# The gate, quoted

Run **once**, from the change root, on the archived tree (it ended `2026-10-29T08:50:33Z`; the delta lives
under `openspec/changes/archive/2026-10-29-fix-loop-close-names-its-transition/` and is merged into
`openspec/specs/agent-issue-loop/spec.md:2636`), so the map's `openspec/*` rows were selected as well:

```
LOOP-GATE: tests=676 failures=0 errors=0 (./gradlew test -PforceTests -PjvmTestsOnly --no-build-cache 265s,
tools/loop-selftest.sh --from-git 42d3e0004206ebfee39e11942db0bef375e1909e 527s,
bash tools/loop-mutations.sh --check 2s, tools/loop-mutations-selftest.sh 382s,
bash tools/gen-feature-list.sh --check-classification 4s, tools/check-doc-routes.sh 0s,
tools/check-doc-routes-selftest.sh 2s, tools/mutate.sh --mutations tools/check-doc-routes-mutations.json --list 0s,
tools/check-todo-references.sh 1s, tools/check-openspec-hygiene.sh 0s, tools/check-openspec-hygiene-selftest.sh 1s,
tools/check-portability.sh 1s, tools/check-portability-selftest.sh 28s, tools/mutate-selftest.sh 1s,
tools/mutate.sh --mutations tools/portability-mutations.json --list 0s, tools/issue-tooling-selftest.sh 91s,
tools/mutate.sh --mutations tools/status-mutations.json --list 1s,
tools/mutate.sh --mutations tools/create-mutations.json --list 0s,
tools/mutate.sh --mutations tools/close-mutations.json --list 0s,
tools/mutate.sh --mutations tools/duplicates-mutations.json --list 0s,
tools/mutate.sh --mutations tools/decision-question-mutations.json --list 0s)
```

Exit 0. The per-suite tallies are copied into `evidence/gate-tallies/` and the aggregate line is transcribed into
`evidence/gate-summary.xml` (`gate.sh` writes no aggregate file). What each number is:

| suite | tests | failures | time |
|---|---|---|---|
| `tools/loop-selftest.sh --from-git 42d3e00…` | 277 | 0 | 527 s |
| `bash tools/loop-mutations.sh --check` | 202 | 0 | 2 s |
| `tools/loop-mutations-selftest.sh` | 34 | 0 | 382 s |
| `tools/issue-tooling-selftest.sh` | 128 | 0 | 91 s |
| `tools/check-portability-selftest.sh` | 29 | 0 | 28 s |
| `tools/check-openspec-hygiene-selftest.sh` | 6 | 0 | 1 s |
| `./gradlew test -PforceTests -PjvmTestsOnly --no-build-cache` | 0 | 0 | 265 s |

Two facts a reader should not have to re-derive:

- **The loop suite is the selection, and it carries this change's case.** `--from-git` selected the 277 cases the
  delta's paths declare, and `labels#closeNamesTheSettledTransition` is green inside it. The suite's own count is
  `LOOP-MEASURE-SUITE cases=277` (`evidence/measure.txt`), one more than the 276 this tree carried before.
- **The compiled step ran and its tally says `tests="0"`.** The delta against the measured base
  (`42d3e00…`) carries compiled paths, so the map owed the JVM suite; the step exited 0 in 265 s (no `BUILD
  FAILED`, no failing suite named in the gate's line) and the tally is `0` because `gate.sh` reads the shell
  suites' summary shape (`all N cases passed`) and Gradle prints another. This change touches no compiled file:
  its own paths are `tools/**`, `guidelines/**` and `openspec/**` (`guidelines/Build.md` §2, the scope block).
