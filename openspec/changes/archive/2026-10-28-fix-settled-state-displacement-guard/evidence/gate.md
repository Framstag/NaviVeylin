# The gate, quoted

Run once, from the change root, 2026-10-28: `./tools/loop/gate.sh` (log: `<item>/gate.log`). Exit status 0.

```
LOOP-GATE-PROFILE: /home/tim/projects/NaviVeylin/.pi/worktrees/loop/tools/loop/loop.conf
LOOP-GATE-STEP-BOUND: 1800s per step — a step that reaches it is ended, named and counted as an error
LOOP-GATE: tests=156 failures=0 errors=0 (tools/issue-tooling-selftest.sh 72s, tools/issues/check-metadata.sh 1s, tools/issues/metadata-mutations.sh --list 1s, tools/mutate.sh --mutations tools/status-mutations.json --list 0s, tools/check-doc-routes.sh 0s, tools/check-doc-routes-selftest.sh 2s, tools/mutate.sh --mutations tools/check-doc-routes-mutations.json --list 0s, tools/check-todo-references.sh 1s, tools/check-portability.sh 1s, tools/check-portability-selftest.sh 25s, tools/mutate-selftest.sh 1s, tools/mutate.sh --mutations tools/portability-mutations.json --list 0s)
```

Every suite that ran, from its own tally under `gate-tallies/` (`gate-summary.xml` carries the aggregate the
line above states, because `gate.sh` writes no aggregate tally of its own):

```
tools/check-doc-routes-selftest.sh                                                      tests=0     failures=0  errors=0  time=2s
tools/check-doc-routes.sh                                                               tests=0     failures=0  errors=0  time=0s
tools/check-portability-selftest.sh                                                     tests=29    failures=0  errors=0  time=25s
tools/check-portability.sh                                                              tests=0     failures=0  errors=0  time=1s
tools/check-todo-references.sh                                                          tests=0     failures=0  errors=0  time=1s
tools/issues/check-metadata.sh                                                          tests=0     failures=0  errors=0  time=1s
tools/issues/metadata-mutations.sh --list                                               tests=0     failures=0  errors=0  time=1s
tools/issue-tooling-selftest.sh                                                         tests=127   failures=0  errors=0  time=72s
tools/mutate-selftest.sh                                                                tests=0     failures=0  errors=0  time=1s
tools/mutate.sh --mutations tools/check-doc-routes-mutations.json --list                tests=0     failures=0  errors=0  time=0s
tools/mutate.sh --mutations tools/portability-mutations.json --list                     tests=0     failures=0  errors=0  time=0s
tools/mutate.sh --mutations tools/status-mutations.json --list                          tests=0     failures=0  errors=0  time=0s
```

No compiled file is touched (`tools/**`, `guidelines/**`, `openspec/**` — `guidelines/Build.md` §2, the scope
block), so `./gradlew test` is not owed and the gate's own path map selected the twelve steps above: the issue
tooling's whole suite, the two checks that read the profile, the mutation lists that pin the sets this change
edits, and the documentation checks that read `guidelines/Backlog.md` and the delta.

The suites the pathspec does not owe and that this change still touches indirectly were run beside the gate,
before it, so a red pipeline is not the first reader: `tools/resolve-stale-selftest.sh` (36 cases green — the
resolver's restore writes through `update.sh`) and `tools/sensor-selftest.sh` (67 cases green — the sensor
reopen writes through `reopen.sh`).
