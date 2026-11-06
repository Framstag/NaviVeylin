# Tasks

## 1. The two cases, red before the engine moves

- [x] Write `run#aChildIsGivenOneScratchDirectory` and extend `run#aWriterTheRunCanPlaceBlocksTheItem` in
      `tools/loop-cases.sh`, both registered in the case list and the dispatcher. Verify: `bash -n
      tools/loop-cases.sh`, then `tools/loop-selftest.sh --case run#aWriterTheRunCanPlaceBlocksTheItem` and `--case
      run#aChildIsGivenOneScratchDirectory` both fail on the base commit, the outputs kept as the item's
      `red-on-HEAD-placed-write.log` and `red-on-HEAD-scratch.log` (`evidence/red-on-HEAD-placed-write.log`,
      `evidence/red-on-HEAD-scratch.log`).

## 2. The reading decides where it is taken

- [x] `tools/loop/run.sh`: record the `outside-write` step beside `worker_write_decides`, its note saying the item
      ends at that reading and no gate child follows; delete the outcome arm that recorded the same step only when
      the difference did not decide. Verify: `bash -n tools/loop/run.sh`, and
      `tools/loop-selftest.sh --case run#aWriterTheRunCanPlaceBlocksTheItem` green (log `green-placed-write.log`,
      carried as `evidence/green-placed-write.log`).

## 3. A child's one place to write

- [x] `tools/loop/run.sh`: create `<item dir>/scratch` where the item's directory is made, and pass `SCRATCH_DIR` to
      the three renders that hand a child its prompt; `prompts/worker.md`, `worker-gate.md` and `worker-tail.md`
      name it beside the change root, with the sentence about a relative path. Verify:
      `tools/loop-selftest.sh --case run#aChildIsGivenOneScratchDirectory` (log `green-scratch.log`, carried as
      `evidence/green-scratch.log`) and `--case run#childUsageIsRecordedPerItem` green, which asserts the tail
      prompt carries no unsubstituted placeholder.

## 4. The rule stated where it lives

- [x] `guidelines/Backlog.recipes.md`, section 7a: the two paragraphs — the placement ends the item where it is read, and
      the scratch directory. Verify: `tools/check-doc-routes.sh` and `tools/check-todo-references.sh` both OK
      (`evidence/tools_check-doc-routes.sh.xml`, `evidence/tools_check-todo-references.sh.xml`).

## 5. The delta

- [x] `openspec/changes/fix-a-placed-write-ends-the-item/specs/agent-issue-loop/spec.md`: two added requirements,
      one scenario each, each naming exactly one case. Verify: `tools/check-openspec-hygiene.sh` and `bash
      tools/check-change-readiness.sh openspec/changes/fix-a-placed-write-ends-the-item` (`LOOP-CHANGE-READY: ok`).

## 6. The structural checks the delta owes

- [x] No compiled file is touched, so no build is owed and the gate is the gate child's (the run deferred it):
      `bash -n tools/loop/run.sh`, `bash -n tools/loop-cases.sh`, `tools/loop-selftest.sh --case
      driver#everyCaseDeclaresTheFilesItCanReach`, `bash tools/loop-mutations.sh --check`,
      `tools/check-portability.sh`, `bash tools/gen-feature-list.sh --check-classification`. All green
      (`evidence/tools_loop-selftest.sh_--case_driver#everyCaseDeclaresTheFilesItCanReach.xml`,
      `evidence/bash_tools_loop-mutations.sh_--check.xml`, `evidence/tools_check-portability.sh.xml`,
      `evidence/bash_tools_gen-feature-list.sh_--check-classification.xml`).

## 7. The engine's cases that reach the lines this change moved

- [x] `run#theWorkersOwnShellWriteIsRecordedAndNotPlaced`, `run#aPeerWriteWhileAChildStandsDoesNotDecideTheItem`,
      `run#childWriteOutsideTheWorktreeIsNoted`, `run#strictOutsideWriteBlocksTheItem`,
      `run#strictOutsideWriteDiscardsTheReviewRound`, `run#childUsageIsRecordedPerItem`,
      `run#thePreGateReviewRunsBeforeTheGate` — all green, kept as the item's `scoped-suite.log`
      (`evidence/scoped-suite.log`).
