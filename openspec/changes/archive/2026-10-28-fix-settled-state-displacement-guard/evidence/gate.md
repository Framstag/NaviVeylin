# The gate, quoted

Run once from the change root, 2026-10-29T06:18:50Z–06:20:38Z, on the final tree of this change: the delta lives
under `openspec/changes/archive/2026-10-28-fix-settled-state-displacement-guard/` and is merged into
`openspec/specs/issue-backlog/spec.md`, so the map's `openspec/*` rows are part of this diff and the checks they
owe are part of this run.

```
$ ./tools/loop/gate.sh
LOOP-GATE-PROFILE: /home/tim/projects/NaviVeylin/.pi/worktrees/loop/tools/loop/loop.conf
LOOP-GATE-STEP-BOUND: 1800s per step — a step that reaches it is ended, named and counted as an error
…
LOOP-GATE: tests=163 failures=0 errors=0 (tools/issue-tooling-selftest.sh 72s, tools/issues/check-metadata.sh 1s, tools/issues/metadata-mutations.sh --list 1s, tools/mutate.sh --mutations tools/status-mutations.json --list 0s, tools/check-portability.sh 1s, tools/check-portability-selftest.sh 26s, tools/mutate.sh --mutations tools/portability-mutations.json --list 0s, bash tools/gen-feature-list.sh --check-classification 4s, tools/check-doc-routes.sh 0s, tools/check-doc-routes-selftest.sh 1s, tools/mutate.sh --mutations tools/check-doc-routes-mutations.json --list 0s, tools/check-todo-references.sh 1s, tools/check-openspec-hygiene.sh 0s, tools/check-openspec-hygiene-selftest.sh 1s, tools/mutate-selftest.sh 0s)
```

Exit status 0, fifteen steps, on `HEAD 73223aaa` — the tree this change ships, after the rebase onto `main`
(`a86ff487`). The three `openspec/` rows are in it, which is what round 1 found missing:
`bash tools/gen-feature-list.sh --check-classification`, `tools/check-openspec-hygiene.sh` and
`tools/check-openspec-hygiene-selftest.sh`. The `163` is `128 + 29 + 6`: the issue tooling's suite grew by one case
in the rebase (`surface#theStubRefusesAnEditWithNoField`, #680).

Every step that ran, read from its own tally under `gate-tallies/` (`gate.sh` writes no aggregate tally;
`gate-summary.xml` is that same `LOOP-GATE:` line transcribed, and it carries no `timestamp` attribute — the
number is the gate's, and the file's own clock would be a synthetic execution time):

```
bash tools/gen-feature-list.sh --check-classification                    tests=0    failures=0  errors=0  time=4s
tools/check-doc-routes-selftest.sh                                       tests=0    failures=0  errors=0  time=1s
tools/check-doc-routes.sh                                                tests=0    failures=0  errors=0  time=0s
tools/check-openspec-hygiene-selftest.sh                                 tests=6    failures=0  errors=0  time=1s
tools/check-openspec-hygiene.sh                                          tests=0    failures=0  errors=0  time=0s
tools/check-portability-selftest.sh                                      tests=29   failures=0  errors=0  time=26s
tools/check-portability.sh                                               tests=0    failures=0  errors=0  time=1s
tools/check-todo-references.sh                                           tests=0    failures=0  errors=0  time=1s
tools/issues/check-metadata.sh                                           tests=0    failures=0  errors=0  time=1s
tools/issues/metadata-mutations.sh --list                                tests=0    failures=0  errors=0  time=1s
tools/issue-tooling-selftest.sh                                          tests=128  failures=0  errors=0  time=72s
tools/mutate-selftest.sh                                                 tests=0    failures=0  errors=0  time=0s
tools/mutate.sh --mutations tools/check-doc-routes-mutations.json --list tests=0    failures=0  errors=0  time=0s
tools/mutate.sh --mutations tools/portability-mutations.json --list      tests=0    failures=0  errors=0  time=0s
tools/mutate.sh --mutations tools/status-mutations.json --list           tests=0    failures=0  errors=0  time=0s
```

The two cases this change owns, from that run's own log:

```
PASS update#refusesToDisplaceASettledState
PASS close#namesTheSettledStateItDisplaces
issue-tooling-selftest: all 128 cases passed (JDK 27, offline, no token, HEAD 73223aaa, tools e3b0c44298fc, 2026-10-29T06:20:02Z)
```

No compiled file is touched (`tools/**`, `guidelines/**`, `openspec/**` — `guidelines/Build.md` §2, the scope
block), so `./gradlew test` is not owed and the gate's own path map selected the fifteen steps above: the issue
tooling's whole suite, the two checks that read the profile, the mutation lists that pin the sets this change
edits, and the checks that read `guidelines/Backlog.md`, the delta and the archive it moved to.

The two suites the pathspec does not owe, and that this change touches indirectly, were run beside the gate on
the same tree, after it (`tools/loop/loop.conf` maps them to `tools/resolve-stale*` and `tools/sensors/*`, not to
the files this change edits):

```
$ bash tools/resolve-stale-selftest.sh                                     # 06:20:45Z–06:21:31Z
resolve-stale-selftest: all 36 cases passed (JDK 27, offline, no token, no Gradle)
$ bash tools/sensor-selftest.sh                                            # 06:21:31Z–06:22:07Z
sensor-selftest: all 67 cases passed (JDK 27, offline, no token)
```

They cover the two writers that inherit the guard without a case of their own in the issue tooling's suite: the
resolver's restore writes through `update.sh`, and the sensor reopen writes through `reopen.sh` — the path this
change's second round gave its report to, which is why both were re-run after it.
