# Tasks

## 1. The red case

- [x] 1.1 `tools/loop-cases.sh`: add `run#staleSkipAsksItsQuestion` to the declared list and the dispatch — a
      stub worker answering `skipped` / `stale` must leave `agent/needs-decision` and `status/on-hold` on the
      item, one comment carrying the worker's reason and a stated choice, and no closure.
      On HEAD it fails on the decision label. Verification: `tools/loop-selftest.sh --case
      'run#staleSkipAsksItsQuestion'` — red before the fix (`XML tests="1" failures="1"`,
      `evidence/stale-skip-red.xml`), green after (`evidence/revert-check.txt`).
- [x] 1.2 `tools/loop-cases.sh`: add `run#staleSkipKeepsTheWorkersQuestion` — a stale report that carries its own
      `LOOP-DECISION` gets that text on the item and not the run's fallback. On HEAD the hold branch writes the
      root cause alone, so the case is red there. Verification: both new cases green after the fix.

## 2. The run's skip path

- [x] 2.1 `tools/loop/run.sh`: admit `stale` to the skip path's escalation branch. Verification: task 1.1's case
      green; `run#aSkipClearsTheInProgressMark` (a `stale` skip) still green.
- [x] 2.2 `tools/loop/run.sh`: when the stale report carries no `LOOP-DECISION`, state the run's own fork — retire
      the item as stale, or re-diagnose it — beside the existing block fallback. Verification: task 1.1's case
      asserts `**The decision:**` in the one comment; `run#skipEscalationCarriesTheDecision` still carries the
      worker's own question unchanged.

## 3. The label writer's vocabulary

- [x] 3.1 `tools/loop/labels.sh`: remove the `stale` verdict from the hold list, its dispatch and the usage
      message; say in the header why it is not a hold. Verification: `labels#verdictToLabelMatrix` (its `stale`
      row removed), `labels#holdCarriesItsReason` rewritten to `unspecifiable`, both green; a `labels.sh stale`
      call now exits 2 as an unknown verdict (the matrix case's rewritten row is gone, the vocabulary's refusal
      is unchanged for every other value).

## 4. The worker prompt

- [x] 4.1 `tools/loop/prompts/worker.md`: the stale stop names what the run does with the verdict, and
      `LOOP-DECISION` is required for a `stale` skip. Verification: `prompt#workerNamesTheDecisionField` gains
      the assertion (green); `prompt#workerNamesEveryPhaseAndSkill` unchanged.

## 5. Falsification

- [x] 5.1 `tools/loop-mutations.sh`: record `21.3` — remove the `stale` clause from the escalation condition; the
      new case must go red on that mutation and green on the restore. Verification:
      `bash tools/loop-mutations.sh --only 21.3` → `red-when-mutated: 1 · not-noticed: 0 ·
      restore-failures: 0`; `bash tools/loop-mutations.sh --check` applies the new record to the file it names.

## 6. The gate and the archive

- [x] 6.1 No compiled file changed, so the build is not owed: the gate's `tools/loop/**` and `*.md` rows are
      the loop's self-test, `tools/loop-mutations.sh --check`, the loop mutations, the portability checks, the
      doc-route checks, the openspec hygiene checks and the issue tooling. Verification: `./tools/loop/gate.sh`
      once, its tallies copied into `evidence/`.
- [x] 6.2 Evidence: the red XML and its run, the mutation's red/green log, the gate's tallies; then
      `bash tools/loop/evidence-check.sh openspec/changes/archive/2026-10-27-fix-stale-skip-asks-its-question
      --root .` reports no dangling pointer, and the change is archived with its delta merged into
      `openspec/specs/agent-issue-loop/spec.md` (verified: the requirement and both scenarios are there).
