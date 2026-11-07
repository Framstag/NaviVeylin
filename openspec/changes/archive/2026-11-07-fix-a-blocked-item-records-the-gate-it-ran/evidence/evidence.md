# Evidence — fix-a-blocked-item-records-the-gate-it-ran (item #813)

The run's phase F, written by the tail process from the gate the first process ran once. Nothing here was re-run.

## The gate, quoted

    LOOP-GATE: tests=902 failures=0 errors=0 (tools/loop-selftest.sh --case driver#everyCaseDeclaresTheFilesItCanReach 11s, tools/loop-selftest.sh --from-git a3cefa934793b341e0d9290034a39b482e4e93dc 378s, bash tools/loop-mutations.sh --check 4s, tools/loop-mutations-selftest.sh 380s, bash tools/gen-feature-list.sh --check-classification 5s, tools/check-doc-routes.sh 1s, tools/check-doc-routes-selftest.sh 7s, tools/mutate.sh --mutations tools/check-doc-routes-mutations.json --list 0s, tools/check-todo-references.sh 1s, tools/check-openspec-hygiene.sh 1s, tools/check-openspec-hygiene-selftest.sh 1s, tools/check-portability.sh 1s, tools/check-portability-selftest.sh 23s, tools/mutate-selftest.sh 1s, tools/mutate.sh --mutations tools/portability-mutations.json --list 0s, tools/issue-tooling-selftest.sh 88s, tools/mutate.sh --mutations tools/status-mutations.json --list 0s, tools/mutate.sh --mutations tools/create-mutations.json --list 0s, tools/mutate.sh --mutations tools/close-mutations.json --list 0s, tools/mutate.sh --mutations tools/duplicates-mutations.json --list 0s, tools/mutate.sh --mutations tools/decision-question-mutations.json --list 0s)

Read from the gate child's own output (`.pi/issue-loop/items/813/worker-tail-report-gate-output.txt`); not re-run.
Its carrier is `evidence/gate.log` (the gate's log) plus the 21 per-step tallies under `evidence/gate-tallies/`,
whose `tests=`/`failures=` attributes sum to `tests=902 failures=0 errors=0`; `evidence/gate-sum.xml` is that sum
written as one `testsuite` — derived, labelled as such in the file, and not a run.

## The red cases, on the base `233cd286`

`run#aRedGateIsBlockedByTheTail` and `run#aBlockedItemRecordsTheRoundThatReadIt` — XML tally `tests=1 failures=1`
each, 2026-11-07T16:21:45Z. The runner's own output is `evidence/red-on-HEAD.log` and
`evidence/red-on-HEAD-round.log`; the transcription of each into a `testsuite` (the loop's shell runner writes no
JUnit XML) is `evidence/red-on-HEAD.xml` and `evidence/red-on-HEAD-round.xml`. The failure messages:

- `run#aRedGateIsBlockedByTheTail`: `the gate tally the tail read, not the emptiness of a field nobody wrote: expected 'tests=1 failures=2 (the gate)', got ''` — the two numbers inside the message are the case's expected string, not a tally of a run: not retained as one.
- `run#aBlockedItemRecordsTheRoundThatReadIt`: `the verdict of the round that read the diff, not the off of a round nobody read: expected 'PASS', got 'off'`.

## The green runs the tasks cite

| file | what it carries |
|---|---|
| `evidence/green-on-change.log` | both cases PASS on the change (task 2) |
| `evidence/declaration-check.log` | `--case driver#everyCaseDeclaresTheFilesItCanReach` → PASS (task 1, task 4) |
| `evidence/run-group.log` | `--group run` → all 131 cases passed (task 4) |
| `evidence/revert-check-97.1.log`, `evidence/revert-check-97.2.log` | the two mutation records, red-when-mutated, no restore failure (task 3) |
| `evidence/openspec-validate.log` | `openspec validate … --strict` → valid (task 5) |
| `evidence/openspec-hygiene.log`, `evidence/doc-routes.log` | the openspec hygiene check and the doc-route check (task 5) |
| `evidence/measure.txt` | the measured block: `LOOP-MEASURE-SUITE cases=358`, `LOOP-MEASURE-CHANGE … scenarios=2 cases=2 unnamed=0`, `LOOP-MEASURE-GATE seconds=- reason=no tally under the gate's directory carries a time` (task 4, tasks header) |

`LOOP-MEASURE-GATE` still reads `seconds=-`: its line was measured before the gate ran, and the gate's own clock is
the seconds in the `LOOP-GATE` line above (11s, 378s, 4s, 380s, …), not a re-measurement.

## The pointer check

`evidence/evidence-check.txt` — the run before the archive, from the change root:

    bash tools/loop/evidence-check.sh openspec/changes/fix-a-blocked-item-records-the-gate-it-ran --root .

## The archive

`evidence/archive.txt` — the archive's own record: the command, its output, the merged capability spec
(`openspec/specs/agent-issue-loop/spec.md:3869`), the check that the directory now exists under
`openspec/changes/archive/` and that the unarchived one is gone, and the archived-mode pointer check (`PASS`).
