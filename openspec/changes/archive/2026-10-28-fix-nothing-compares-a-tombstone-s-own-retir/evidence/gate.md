# Gate — fix-nothing-compares-a-tombstone-s-own-retir

Run once from the change root. No compiled path is touched, so the shell suites the gate's own path map names
are the whole gate; the build is not owed.

```
$ ./tools/loop/gate.sh
LOOP-GATE: tests=222 failures=0 errors=0 (tools/issue-tooling-selftest.sh 69s, tools/issues/check-metadata.sh 0s, tools/issues/metadata-mutations.sh --list 1s, tools/sensor-selftest.sh 67s, tools/sensor-mutations.sh --list 0s, tools/check-doc-routes.sh 0s, tools/check-doc-routes-selftest.sh 2s, tools/mutate.sh --mutations tools/check-doc-routes-mutations.json --list 0s, tools/check-todo-references.sh 1s, tools/check-portability.sh 1s, tools/check-portability-selftest.sh 24s, tools/mutate-selftest.sh 0s, tools/mutate.sh --mutations tools/portability-mutations.json --list 0s, tools/mutate.sh --mutations tools/status-mutations.json --list 1s, tools/mutate.sh --mutations tools/create-mutations.json --list 0s, tools/mutate.sh --mutations tools/close-mutations.json --list 0s, tools/mutate.sh --mutations tools/duplicates-mutations.json --list 0s, tools/mutate.sh --mutations tools/decision-question-mutations.json --list 0s)
```

The lines the run's own measurement carries for this change, quoted from `evidence/measure.txt`:

```
LOOP-MEASURE base=c29691865337325c0b293b57b72068d0468540da paths=9 insertions=111 deletions=24
LOOP-MEASURE-SUITE cases=273 cmd=tools/loop-selftest.sh --list
LOOP-MEASURE-GATE seconds=166 suites=18
LOOP-MEASURE-CHANGE fix-nothing-compares-a-tombstone-s-own-retir scenarios=5 cases=5 unnamed=0
```

The per-suite tallies the gate wrote are beside this file under `evidence/gate-tallies/` (18 files). The two
that carry cases are `tools_issue-tooling-selftest.sh.xml` (`tests="126" failures="0"`) and
`tools_sensor-selftest.sh.xml` (`tests="67" failures="0"`).
