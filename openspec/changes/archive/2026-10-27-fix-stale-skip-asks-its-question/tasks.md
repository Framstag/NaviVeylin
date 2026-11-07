# Tasks

## 1. The red cases

- [x] 1.1 `tools/loop-cases.sh`: add `run#staleSkipAsksItsQuestion` to the declared list and the dispatch — a
      stub worker answering `skipped` / `stale` must leave `agent/needs-decision` and `status/on-hold` on the
      item, one comment carrying the worker's reason and a stated choice, and no closure.
      Verification: `tools/loop-selftest.sh --case 'run#staleSkipAsksItsQuestion'` — red before the fix
      (`XML tests="1" failures="1"`, `evidence/stale-skip-red.xml`), green after (`evidence/revert-check.txt`).
- [x] 1.2 `tools/loop-cases.sh`: add `run#staleSkipKeepsTheWorkersQuestion` — a stale report that carries its own
      `LOOP-DECISION` gets that text on the item and not the run's fallback. Verification: on HEAD's skip path the
      case is red, measured with the `stale` clause removed from `run.sh`'s escalation condition (the edit mutation
      `21.3` makes) — `FAIL run#staleSkipKeepsTheWorkersQuestion`, `XML tests="1" failures="1"`
      (`evidence/stale-skip-keeps-question-red.xml`, 2026-10-27T20:07:09Z); green after the fix
      (`gate.log:881`).

## 2. The run's skip path

- [x] 2.1 `tools/loop/run.sh`: admit `stale` to the skip path's escalation branch. Verification: task 1.1's case
      green; `run#aSkipClearsTheInProgressMark` (a `stale` skip) still green.
- [x] 2.2 `tools/loop/run.sh`: when the stale report carries no `LOOP-DECISION`, state the run's own fork — retire
      the item as stale, or re-diagnose it — beside the existing block fallback. Verification: task 1.1's case
      asserts `**The decision:**` in the one comment; task 1.2's case asserts the worker's own question is kept and
      the run's is absent.

## 3. The label writer's vocabulary

- [x] 3.1 `tools/loop/labels.sh`: remove the `stale` verdict from the hold list (`:88`), its dispatch and the usage
      message (`:128`); say in the header (`:18-20`) why it is not a hold. Verification: `labels#verdictToLabelMatrix`
      asserts the four hold rows (`unspecifiable`, `too-wide`, `not-spec-shaped`, `needs-target`), the device and
      decision rows, and that `labels.sh stale 21` exits 2 as an unknown verdict writing nothing;
      `labels#holdCarriesItsReason` rewritten to `unspecifiable`; both green.

## 4. The worker prompt

- [x] 4.1 `tools/loop/prompts/worker.md`: the stale stop names what the run does with the verdict, and
      `LOOP-DECISION` is required for a `stale` skip. Verification: `prompt#workerNamesTheDecisionField` gains
      the assertion (green); `prompt#workerNamesEveryPhaseAndSkill` unchanged.

## 5. Falsification

- [x] 5.1 `tools/loop-mutations.sh`: record `21.3` — remove the `stale` clause from the escalation condition; the
      new case must go red on that mutation and green on the restore. Verification:
      `bash tools/loop-mutations.sh --only 21.3` → `red-when-mutated: 1 · not-noticed: 0 ·
      restore-failures: 0`; `bash tools/loop-mutations.sh --check` applies the new record to the file it names.

## 6. The gate and the evidence

- [x] 6.1 No compiled file changed, so the build is not owed: the gate's `tools/loop/**` and `*.md` rows are
      the loop's self-test, `tools/loop-mutations.sh --check`, the loop mutations, the portability checks, the
      doc-route checks, the openspec hygiene checks and the issue tooling. Verification: `./tools/loop/gate.sh`
      once, its tallies copied into `evidence/`.
- [x] 6.2 Evidence: the two red XMLs and their runs, the mutation's red/green log, the gate's tallies; then
      `bash tools/loop/evidence-check.sh openspec/changes/archive/2026-10-27-fix-stale-skip-asks-its-question
      --root .` reports no dangling pointer, and the change is archived with its deltas merged into
      `openspec/specs/agent-issue-loop/spec.md`.

## 7. The narrowed contract

- [x] 7.1 `.../specs/agent-issue-loop/spec.md`: restate the two requirements the old mapping rested on — "A hold
      is not a decision" (its hold writers no longer include the worker's `stale` sub-verdict) and "A state the run
      writes is readable from the item" (its hold scenario names a hold verdict, not a skip the run now escalates)
      — and restate the falsification requirement's "every other skip keeps the state it writes today" as the four
      hold verdicts. Also re-point the "skips this change does not re-route" scenario at
      `labels#verdictToLabelMatrix` + `prescreen#deviceItemIsAVerdictNotWork` (it had named a `stale` skip), and
      narrow the reviewer scenario's THEN to what its case asserts. Verification: the merged
      `openspec/specs/agent-issue-loop/spec.md` carries the same four blocks byte for byte, and
      `measure.sh --change …` prints `scenarios=12 cases=12 unnamed=0`.
