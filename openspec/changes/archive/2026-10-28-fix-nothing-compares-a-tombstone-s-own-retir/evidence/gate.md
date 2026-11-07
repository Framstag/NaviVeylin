# Gate — fix-nothing-compares-a-tombstone-s-own-retir

Run from the change root on the tree that carries round 1's corrections (the corrections themselves, and the branch
merged with `main`, so the base the gate measured from is `main`'s tip). No compiled path is touched, so the shell
suites the gate's own path map names are the whole gate; the build is not owed. The six evidence files this record
gained after the run — three tallies, `review-round-1-corrections.md`, `resolve-stale-selftest.log`,
`evidence-check-archived.log` — are outside every glob the map reads and move no outcome of it.

```
$ ./tools/loop/gate.sh
LOOP-GATE: tests=231 failures=0 errors=0 (tools/issue-tooling-selftest.sh 118s, tools/issues/check-metadata.sh 0s, tools/issues/metadata-mutations.sh --list 2s, tools/sensor-selftest.sh 118s, tools/sensor-mutations.sh --list 0s, bash tools/gen-feature-list.sh --check-classification 9s, tools/check-doc-routes.sh 0s, tools/check-doc-routes-selftest.sh 3s, tools/mutate.sh --mutations tools/check-doc-routes-mutations.json --list 0s, tools/check-todo-references.sh 2s, tools/check-openspec-hygiene.sh 0s, tools/check-openspec-hygiene-selftest.sh 1s, tools/check-portability.sh 1s, tools/check-portability-selftest.sh 38s, tools/mutate-selftest.sh 1s, tools/mutate.sh --mutations tools/portability-mutations.json --list 0s, tools/mutate.sh --mutations tools/status-mutations.json --list 0s, tools/mutate.sh --mutations tools/create-mutations.json --list 0s, tools/mutate.sh --mutations tools/close-mutations.json --list 1s, tools/mutate.sh --mutations tools/duplicates-mutations.json --list 0s, tools/mutate.sh --mutations tools/decision-question-mutations.json --list 0s)
```

The lines the run's own measurement carries for this change, quoted from `evidence/measure.txt`:

```
LOOP-MEASURE base=ccbc309994e4ccd9585974ce7b67d3642ce09eb6 paths=48 insertions=866 deletions=30
LOOP-MEASURE-SUITE cases=277 cmd=tools/loop-selftest.sh --list
LOOP-MEASURE-GATE seconds=294 suites=21
LOOP-MEASURE-CHANGE 2026-10-28-fix-nothing-compares-a-tombstone-s-own-retir scenarios=5 cases=5 unnamed=0
```

The gate itself ran on the 41-path tree the corrections commit carried; the seven paths it predates are this
record's own evidence (`review-round-2-passed.md`, the two rewritten evidence files, the surface-line edit and so
on), outside every glob the map reads — and what those paths *do* owe was re-run on the final tree with its output
recorded: `check-doc-routes: all 155 section references resolve, all 4 skill references resolve`,
`check-doc-routes-selftest: 19 passed, 0 failed`, `check-openspec-hygiene-selftest: all 6 cases passed`,
`check-todo-references: 64 citation(s) resolve through the recorded table`, and
`gen-feature-list.sh --check-classification` ending in `car-only areas: 1 / highlights: 19`.

The per-suite tallies the gate wrote are beside this file under `evidence/gate-tallies/` (21 files). Four carry
cases: `tools_issue-tooling-selftest.sh.xml` (`tests="129" failures="0"`), `tools_sensor-selftest.sh.xml`
(`tests="67" failures="0"`), `tools_check-portability-selftest.sh.xml` (`tests="29" failures="0"`) and
`tools_check-openspec-hygiene-selftest.sh.xml` (`tests="6" failures="0"`); 129 + 67 + 29 + 6 = 231, while every
other step reports its own internal passes at `tests="0"` (`check-doc-routes-selftest` prints `19 passed`,
`mutate-selftest` `36 passed`) and contributes none of the 231. The gate's own wall clock is 294 s, the sum of the
`time` attribute of those 21 tallies.

`evidence/measure.txt` carries the whole block this run read: the base, the 41 paths, the 21 checks the pathspec
owed (the `openspec/**` and `*.md` paths this change touches pull `gen-feature-list --check-classification`,
`check-openspec-hygiene` + selftest, `check-doc-routes` + selftest and `check-todo-references` in, which the
round-1 gate did not run), and the change's own five scenarios against the five cases they name.

Round 1's reviewer noted that `tools/issues/resolve-stale.sh`'s delegation is owed by no mapped suite — the map's
`tools/resolve-stale*` glob is the top-level tool, not `tools/issues/resolve-stale.sh`. It was run by hand on this
same tree and is recorded whole in `evidence/resolve-stale-selftest.log`: `resolve-stale-selftest: all 36 cases
passed (JDK 27, offline, no token, no Gradle)`, exit 0.
