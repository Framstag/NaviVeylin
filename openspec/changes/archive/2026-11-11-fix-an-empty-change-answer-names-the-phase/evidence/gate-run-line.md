# The gate this change ran

Taken in an isolated worktree of this branch, with no other suite in the tree (the run of 2026-11-10T19:5xZ was
taken in the main tree while a loop landed items into it — two suites and a moving `main` in one tree — and its reds
are attributed there, not here).

```
LOOP-GATE: tests=953 failures=0 errors=0 (tools/loop/tests/loop-selftest.sh --case driver#everyCaseDeclaresTheFilesItCanReach 12s,\
 tools/loop/tests/loop-selftest.sh --from-git 4abbbc9afcf6fd9b48c84e0549c25d1efa6ff570 639s,\
 bash tools/loop/tests/loop-mutations.sh --check 4s, tools/loop/tests/loop-mutations-selftest.sh 660s,\
 tools/check-portability.sh 2s, tools/tests/check-portability-selftest.sh 30s, tools/tests/mutate-selftest.sh 1s,\
 tools/mutate.sh --mutations tools/tests/portability-mutations.json --list 0s, tools/issues/tests/issue-tooling-selftest.sh 97s,
 tools/mutate.sh --mutations tools/issues/tests/{status,create,close,duplicates,decision-question}-mutations.json --list 0s)
```

The checks the gate's plan does not select for this delta, run by hand because the change archives an artifact:

```
tools/check-openspec-hygiene.sh      Every capability spec states its Purpose. 1 open change(s) carry task markers.
tools/gen-feature-list.sh --check-classification  specs read: 187, unclassified: 4 (the four the loop-bounds change
                                     left, on `main`'s unarchived state; none is this change's), classified: 194
tools/check-doc-routes.sh            all 330 section references resolve, all 7 skill references resolve
tools/check-todo-references.sh       67 citation(s) resolve through the recorded table
```

`gen-feature-list --check-classification` is read again after the archive, on the archived tree; the four unclassified
delta ids are `main`'s, and the loop-bounds archive on `main` `8c726e15` is what clears them.

The change's own falsification, taken with `tools/loop/tests/loop-mutations.sh`:

```
273.1  run#anEmptyAnswerOverACommitIsTheChangePhasesFailure   RED, then restore, then GREEN
--files tools/loop/run.sh   118 records   red-when-mutated: 118 · not-noticed: 0 · restore-failures: 0
--check                     324 records   every selected record applies to the file it names
loop-selftest               400 of 400 cases
```
