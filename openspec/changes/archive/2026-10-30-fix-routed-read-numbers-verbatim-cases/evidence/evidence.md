# Evidence — fix-routed-read-numbers-verbatim-cases

## The measurement, quoted

```
LOOP-MEASURE base=2f954a04b3f75e138c529fffe7985ca011375866 paths=33 insertions=850 deletions=12
LOOP-MEASURE-CHECK bash tools/gen-feature-list.sh --check-classification
LOOP-MEASURE-CHECK tools/check-doc-routes.sh
LOOP-MEASURE-CHECK tools/check-doc-routes-selftest.sh
LOOP-MEASURE-CHECK tools/mutate.sh --mutations tools/check-doc-routes-mutations.json --list
LOOP-MEASURE-CHECK tools/check-todo-references.sh
LOOP-MEASURE-CHECK tools/check-openspec-hygiene.sh
LOOP-MEASURE-CHECK tools/check-openspec-hygiene-selftest.sh
LOOP-MEASURE-SUITE cases=284 cmd=tools/loop-selftest.sh --list
LOOP-MEASURE-TALLY app/build/test-results/loop-gate/tools_check-doc-routes-selftest.sh.xml tests=22 failures=0 time=2s
LOOP-MEASURE-TALLY app/build/test-results/loop-gate/tools_check-openspec-hygiene-selftest.sh.xml tests=6 failures=0 time=0s
LOOP-MEASURE-GATE seconds=9 suites=7
LOOP-MEASURE-CHANGE 2026-10-30-fix-routed-read-numbers-verbatim-cases scenarios=9 cases=7 unnamed=2
LOOP-MEASURE-TALLY openspec/changes/archive/2026-10-30-fix-routed-read-numbers-verbatim-cases/evidence/gate-summary.xml tests=28 failures=0
LOOP-MEASURE-TALLY openspec/changes/archive/2026-10-30-fix-routed-read-numbers-verbatim-cases/evidence/green-after-archive.xml tests=1 failures=0
LOOP-MEASURE-TALLY openspec/changes/archive/2026-10-30-fix-routed-read-numbers-verbatim-cases/evidence/red-on-head.xml tests=1 failures=1
LOOP-MEASURE-SURFACE surface: 1 case added (`routed-read#numbers`), 1 mutation record added (`guideline-body-reference`), 2 spec Case lines restated (`#numbers`, `#verbatim`), 0 cases rewritten, 0 fixtures realigned, 0 compiled files
LOOP-MEASURE-END
```

`measure-after-archive.txt` carries the block; the `unnamed=2` scenarios are the pre-existing *Numbers for
representative changes* and *An unmeasured saving is not claimed*, neither of which has a Case line.

## Red on HEAD

`evidence/red-probe.sh` ran at 2026-10-30T07:22:14Z against the base tree and reported `tests=1 failures=1`
(`red-on-head.xml`, `red-on-head.log`). The failing case is `routed-read#the-two-case-lines-resolve`; it named:

```
[declared: routed-read#numbers]
[run: routed-read#numbers: check-doc-routes-selftest: no case with the id 'routed-read#numbers']
[verbatim: names evidence/red-probe.sh]
[verbatim: says no harness asserts it]
```

## Green after archive

The same probe over the merged spec at 2026-10-30T07:24:18Z reports `tests=1 failures=0`
(`green-after-archive.xml`, `green-after-archive.log`). `routed-read#numbers` is declared at
`tools/check-doc-routes-selftest.sh:442` and its case function is at `:393`; the merged Case line is
`openspec/specs/documentation-ownership/spec.md:159`, and the verbatim Case line is at `:176`.

## The guard can fail

`tools/mutate.sh --mutations tools/check-doc-routes-mutations.json --only guideline-body-reference` replaces the
body-reference extraction in `tools/check-doc-routes.sh` with a no-op. `revert-check.log` records
`RED(exit 1)` for `routed-read#numbers` under the mutation, `GREEN(exit 0)` after the restore, and
`0 problem(s)`. The record is at `tools/check-doc-routes-mutations.json:22`.

## Gate

`./tools/loop/gate.sh` ran once after the archive and ended:

```
LOOP-GATE: tests=28 failures=0 errors=0 (bash tools/gen-feature-list.sh --check-classification 4s, tools/check-doc-routes.sh 1s, tools/check-doc-routes-selftest.sh 2s, tools/mutate.sh --mutations tools/check-doc-routes-mutations.json --list 0s, tools/check-todo-references.sh 2s, tools/check-openspec-hygiene.sh 0s, tools/check-openspec-hygiene-selftest.sh 0s)
```

`gate.log` carries the transcript, `gate-tallies/` the seven per-suite tallies and `gate-summary.xml` their sum.

## Structural pointers

- `openspec validate fix-routed-read-numbers-verbatim-cases --strict` — `openspec-validate.log`:
  `Change 'fix-routed-read-numbers-verbatim-cases' is valid`.
- `bash tools/loop/evidence-check.sh openspec/changes/archive/2026-10-30-fix-routed-read-numbers-verbatim-cases --root .`
  — `evidence-check.log`: `evidence-check: PASS`.
