# The gate, quoted

Run once, from the change root, 2026-10-27: `./tools/loop/gate.sh` (log: `<item>/gate.log`). Exit status 0.

```
LOOP-GATE-PROFILE: /home/tim/projects/NaviVeylin/.pi/worktrees/loop/tools/loop/loop.conf
LOOP-GATE-STEP-BOUND: 1800s per step — a step that reaches it is ended, named and counted as an error
LOOP-GATE: tests=803 failures=0 errors=0 (./gradlew test -PforceTests -PjvmTestsOnly --no-build-cache 251s, tools/loop-selftest.sh --from-git 6f61cffe4f82e91c38349f5a6787c28011466387 428s, bash tools/loop-mutations.sh --check 2s, tools/loop-mutations-selftest.sh 334s, tools/issue-tooling-selftest.sh 72s, tools/issues/check-metadata.sh 1s, tools/issues/metadata-mutations.sh --list 1s, tools/check-portability-selftest.sh 25s, tools/resolve-stale-selftest.sh 14s, tools/mutate.sh --mutations tools/resolve-stale-mutations.json --list 1s, tools/check-compose-sandbox-rule.sh 0s, tools/check-compose-sandbox-rule-selftest.sh 0s, tools/gen-feature-list-selftest.sh 17s, bash tools/gen-feature-list.sh --check-classification 4s, tools/check-doc-routes.sh 0s, tools/check-doc-routes-selftest.sh 2s, tools/mutate.sh --mutations tools/check-doc-routes-mutations.json --list 0s, tools/check-todo-references.sh 1s, tools/check-openspec-hygiene.sh 0s, tools/check-openspec-hygiene-selftest.sh 0s, tools/check-portability.sh 1s, tools/mutate-selftest.sh 1s, tools/mutate.sh --mutations tools/portability-mutations.json --list 0s, tools/mutate.sh --mutations tools/status-mutations.json --list 0s, tools/mutate.sh --mutations tools/create-mutations.json --list 0s, tools/mutate.sh --mutations tools/close-mutations.json --list 0s, tools/mutate.sh --mutations tools/duplicates-mutations.json --list 0s, tools/mutate.sh --mutations tools/decision-question-mutations.json --list 0s)
```

Every suite that ran, from its own tally under `gate-tallies/` (`gate-summary.xml` carries the aggregate the
line above states, because `gate.sh` writes no aggregate tally of its own):

```
bash tools/gen-feature-list.sh --check-classification                                    tests=0     failures=0  errors=0  time=4s
bash tools/loop-mutations.sh --check                                                     tests=198   failures=0  errors=0  time=2s
./gradlew test -PforceTests -PjvmTestsOnly --no-build-cache                              tests=0     failures=0  errors=0  time=251s
tools/check-compose-sandbox-rule-selftest.sh                                             tests=0     failures=0  errors=0  time=0s
tools/check-compose-sandbox-rule.sh                                                      tests=0     failures=0  errors=0  time=0s
tools/check-doc-routes-selftest.sh                                                       tests=0     failures=0  errors=0  time=2s
tools/check-doc-routes.sh                                                                tests=0     failures=0  errors=0  time=0s
tools/check-openspec-hygiene-selftest.sh                                                 tests=6     failures=0  errors=0  time=0s
tools/check-openspec-hygiene.sh                                                          tests=0     failures=0  errors=0  time=0s
tools/check-portability-selftest.sh                                                      tests=29    failures=0  errors=0  time=25s
tools/check-portability.sh                                                               tests=0     failures=0  errors=0  time=1s
tools/check-todo-references.sh                                                           tests=0     failures=0  errors=0  time=1s
tools/gen-feature-list-selftest.sh                                                       tests=119   failures=0  errors=0  time=17s
tools/issues/check-metadata.sh                                                           tests=0     failures=0  errors=0  time=1s
tools/issues/metadata-mutations.sh --list                                                tests=0     failures=0  errors=0  time=1s
tools/issue-tooling-selftest.sh                                                          tests=125   failures=0  errors=0  time=72s
tools/loop-mutations-selftest.sh                                                         tests=34    failures=0  errors=0  time=334s
tools/loop-selftest.sh --from-git 6f61cffe4f82e91c38349f5a6787c28011466387               tests=256   failures=0  errors=0  time=428s
tools/mutate-selftest.sh                                                                 tests=0     failures=0  errors=0  time=1s
tools/mutate.sh --mutations tools/check-doc-routes-mutations.json --list                 tests=0     failures=0  errors=0  time=0s
tools/mutate.sh --mutations tools/close-mutations.json --list                            tests=0     failures=0  errors=0  time=0s
tools/mutate.sh --mutations tools/create-mutations.json --list                           tests=0     failures=0  errors=0  time=0s
tools/mutate.sh --mutations tools/decision-question-mutations.json --list                tests=0     failures=0  errors=0  time=0s
tools/mutate.sh --mutations tools/duplicates-mutations.json --list                       tests=0     failures=0  errors=0  time=0s
tools/mutate.sh --mutations tools/portability-mutations.json --list                      tests=0     failures=0  errors=0  time=0s
tools/mutate.sh --mutations tools/resolve-stale-mutations.json --list                    tests=0     failures=0  errors=0  time=1s
tools/mutate.sh --mutations tools/status-mutations.json --list                           tests=0     failures=0  errors=0  time=0s
tools/resolve-stale-selftest.sh                                                          tests=36    failures=0  errors=0  time=14s
```

The engine's 803 are the shell suites' own cases
(198 + 6 + 29 + 119 + 125 + 34 + 256 + 36); the build step contributes none of them, because Gradle prints no
`all N cases passed` line for the gate to sum, and it prints none here.

## Why the build ran although this change touches no compiled file

The gate derives its base from the loop's recorded base (`6f61cffe…`), whose delta carries `app/src`; this
change's own paths are `tools/issues/resolve-stale.sh`, `tools/tooling.json`,
`tools/fixtures/foreign-project/tools/tooling.json`, `tools/resolve-stale-cases.sh`,
`tools/resolve-stale-mutations.json`, `guidelines/Backlog.md` and the change directory — none of them compiled
(`guidelines/Build.md` §2 scope block). The build step therefore ran against a tree this change did not reach
and was green:

```
BUILD SUCCESSFUL in 4m 10s
166 actionable tasks: 17 executed, 149 up-to-date
```

The delta's own structural checks are the ones that decide it: `tools/resolve-stale-selftest.sh` (36 cases,
including the three the spec delta names), `tools/check-portability.sh` (109 declared values, no project value in
an engine) and `tools/check-openspec-hygiene.sh` (the delta's scenario-to-case map), all green above.
