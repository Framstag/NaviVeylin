# Evidence — fix-a-repeat-read-is-a-counted-number (item #814)

The run's phase F, written by the tail process from the gate the first process ran once. Nothing here was re-run.

## The gate, quoted

    LOOP-GATE: tests=899 failures=0 errors=0 (21 entries)

Read from the gate child's own output (`.pi/issue-loop/items/814/worker-tail-report-gate-output.txt`); not re-run.
Its carrier is `evidence/gate.txt` (the gate's log) plus the 21 per-step tallies under `evidence/gate-tallies/`,
whose `tests=`/`failures=` attributes sum to that line; `evidence/gate-sum.xml` is that sum written as one
`testsuite` — derived, labelled as such in the file, and not a run.

## The red case, on the base `49afe102`

`report#anItemRecordsItsRepeatReads` — XML tally `tests=1 failures=1`, 2026-11-07T15:51:44Z. The runner's own
output is `evidence/red-on-HEAD.log`; the transcription into a `testsuite` (the loop's shell runner writes no JUnit
XML) is `evidence/red-on-HEAD.xml`. The failure message: `the item's file reads are counted from its event stream,
read and ctx_read alike: expected '5', got 'null'`.

## The green runs the tasks cite

| file | what it carries |
|---|---|
| `evidence/green-on-change.log` | `report#anItemRecordsItsRepeatReads` and the trend line, PASS on the change |
| `evidence/report-group.log` | `--group report` → all 23 cases passed |
| `evidence/state-group.log` | `--group state` → all 27 cases passed |
| `evidence/driver-group.log` | `--group driver` → all 23 cases passed |
| `evidence/run-group.log` | `--group run` → all 130 cases passed |
| `evidence/revert-check-96.1.log`, `evidence/revert-check-96.2.log` | the two mutation records, red-when-mutated |
| `evidence/mutations-check.log` | `loop-mutations.sh --check`, every record applies |
| `evidence/openspec-validate.log` | `openspec validate … --strict` → valid |
| `evidence/openspec-hygiene.log`, `evidence/portability.log`, `evidence/doc-routes.log` | the three document checks |
| `evidence/measure.txt` | the measured block (`LOOP-MEASURE-SUITE cases=357`, surface lines) |

## The pointer check

`evidence/evidence-check.txt` — first run FAIL (5, all C1 reading the guideline sub-section numbers as backlog
citations), corrected by repointing the five guideline pointers at `guidelines/Backlog.recipes.md`:254 / `:892`,
second run PASS.

## Archive

`archive.txt` — the archive's own record: the command, the merged capability spec, and the check that the directory
exists under `openspec/changes/archive/`.
