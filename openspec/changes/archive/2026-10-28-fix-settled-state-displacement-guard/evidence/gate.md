# The gate, quoted

Run once from the change root, 2026-10-28T20:00:41Z–20:02:27Z, on the archived tree — the delta already under
`openspec/changes/archive/2026-10-28-fix-settled-state-displacement-guard/` and merged into
`openspec/specs/issue-backlog/spec.md`, so the map's `openspec/*` rows are part of this diff and the checks they
owe are part of this run:

```
$ ./tools/loop/gate.sh
LOOP-GATE-PROFILE: /home/tim/projects/NaviVeylin/.pi/worktrees/loop/tools/loop/loop.conf
LOOP-GATE-STEP-BOUND: 1800s per step — a step that reaches it is ended, named and counted as an error
…
LOOP-GATE: tests=162 failures=0 errors=0 (tools/issue-tooling-selftest.sh 71s, tools/issues/check-metadata.sh 1s, tools/issues/metadata-mutations.sh --list 1s, tools/mutate.sh --mutations tools/status-mutations.json --list 0s, bash tools/gen-feature-list.sh --check-classification 4s, tools/check-doc-routes.sh 0s, tools/check-doc-routes-selftest.sh 1s, tools/mutate.sh --mutations tools/check-doc-routes-mutations.json --list 0s, tools/check-todo-references.sh 1s, tools/check-openspec-hygiene.sh 0s, tools/check-openspec-hygiene-selftest.sh 0s, tools/check-portability.sh 1s, tools/check-portability-selftest.sh 25s, tools/mutate-selftest.sh 0s, tools/mutate.sh --mutations tools/portability-mutations.json --list 0s)
```

Exit status 0. Fifteen steps, and the three the reviewed tree did not owe are in it:
`bash tools/gen-feature-list.sh --check-classification`, `tools/check-openspec-hygiene.sh` and
`tools/check-openspec-hygiene-selftest.sh` — the rows `tools/loop/loop.conf` maps for an `openspec/` path. The
log is `<item>/gate.log` in the item's evidence directory.

Every step that ran, read from its own tally under `gate-tallies/` (`gate.sh` writes no aggregate tally;
`gate-summary.xml` is that same `LOOP-GATE:` line transcribed, and it carries no `timestamp` attribute — the
number is the gate's, and the file's own clock would be a synthetic execution time, which is what the round-1
review rejected):

```
bash tools/gen-feature-list.sh --check-classification                    tests=0    failures=0  errors=0  time=4s
tools/check-doc-routes-selftest.sh                                       tests=0    failures=0  errors=0  time=1s
tools/check-doc-routes.sh                                                tests=0    failures=0  errors=0  time=0s
tools/check-openspec-hygiene-selftest.sh                                 tests=6    failures=0  errors=0  time=0s
tools/check-openspec-hygiene.sh                                          tests=0    failures=0  errors=0  time=0s
tools/check-portability-selftest.sh                                      tests=29   failures=0  errors=0  time=25s
tools/check-portability.sh                                               tests=0    failures=0  errors=0  time=1s
tools/check-todo-references.sh                                           tests=0    failures=0  errors=0  time=1s
tools/issues/check-metadata.sh                                           tests=0    failures=0  errors=0  time=1s
tools/issues/metadata-mutations.sh --list                                tests=0    failures=0  errors=0  time=1s
tools/issue-tooling-selftest.sh                                          tests=127  failures=0  errors=0  time=71s
tools/mutate-selftest.sh                                                 tests=0    failures=0  errors=0  time=0s
tools/mutate.sh --mutations tools/check-doc-routes-mutations.json --list tests=0    failures=0  errors=0  time=0s
tools/mutate.sh --mutations tools/portability-mutations.json --list      tests=0    failures=0  errors=0  time=0s
tools/mutate.sh --mutations tools/status-mutations.json --list           tests=0    failures=0  errors=0  time=0s
```

The two cases this change owns, from that run's own log:

```
PASS update#refusesToDisplaceASettledState
PASS close#namesTheSettledStateItDisplaces
issue-tooling-selftest: all 127 cases passed (JDK 27, offline, no token, HEAD bfe7d690, tools e3b0c44298fc, 2026-10-28T20:01:52Z)
```

No compiled file is touched (`tools/**`, `guidelines/**`, `openspec/**` — `guidelines/Build.md` §2, the scope
block), so `./gradlew test` is not owed and the gate's own path map selected the fifteen steps above: the issue
tooling's whole suite, the two checks that read the profile, the mutation lists that pin the sets this change
edits, and the checks that read `guidelines/Backlog.md`, the delta and the archive it moved to.

The two suites the pathspec does not owe and that this change still touches indirectly were run beside the gate
on the same tree, after it (`tools/loop/loop.conf` maps them to `tools/resolve-stale*` and `tools/sensors/*`,
not to the files this change edits):

```
$ bash tools/resolve-stale-selftest.sh                                     # 20:02:52Z–20:03:05Z
resolve-stale-selftest: all 36 cases passed (JDK 27, offline, no token, no Gradle)
$ bash tools/sensor-selftest.sh                                            # 20:03:05Z–20:04:13Z
sensor-selftest: all 67 cases passed (JDK 27, offline, no token)
```

They cover the two writers that inherit the guard without a case of their own in the issue tooling's suite: the
resolver's restore writes through `update.sh`, and the sensor reopen writes through `reopen.sh`.
