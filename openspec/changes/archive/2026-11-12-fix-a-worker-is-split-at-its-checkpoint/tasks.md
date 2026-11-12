# Tasks — the checkpoint splits the item's worker

## 1. The engine

- [x] 1.1 `tools/loop/run.sh`: `run_child` ends the item's first worker (`phase = worker-prompt`) when its own
      reading reaches `LOOP_ITEM_CONTEXT_WARN` below the role's stop, after the stop's check so a reading past the
      stop is named by the stop. Verification: `run#aWorkerBelowItsCheckpointIsNotSplit` and the existing
      `run#childPastItsContextBoundIsCut`.
- [x] 1.2 `tools/loop/run.sh`: the split is taken only when the worktree carries work at the crossing
      (`git status --porcelain`), so a worker with nothing to continue runs on to its recorded stop. Verification:
      `run#aWorkerAtTheCheckpointWithNoWorkRunsOn`.
- [x] 1.3 `tools/loop/run.sh`: the transcript line the split writes names the checkpoint and the reading:
      `LOOP-TIMEOUT: the child reached the recorded context checkpoint of <warn> tokens (LOOP_ITEM_CONTEXT_WARN) at
      <reading> tokens — the run ended it and everything it started`. Verification: the checkpoint case's assertion
      on `worker-report.md`.
- [x] 1.4 `tools/loop/run.sh`: `child_ended_by_context_bound` recognises the checkpoint's line, and the continuation
      block records `worker-checkpoint` for it (`worker-continuation` otherwise), passing the reason to the applier.
      Verification: the checkpoint case's step and `resumes` assertions.
- [x] 1.5 `tools/loop/worktree.sh`: `continue-item <issue> [checkpoint|bound]` — the default stays the bound's
      sentence byte for byte; the checkpoint's sentence says the run reached the checkpoint, not that the child
      passed the bound. Verification: both cases' note assertions.
- [x] 1.6 `tools/loop/prompts/worker-continuation.md`: the opening names the split the profile records
      (`LOOP_ITEM_CONTEXT_WARN` before the stop, `LOOP_ITEM_CONTEXT_MAX` at it) and defers which one fired to
      `{{BOUND_LINE}}`. Verification: the checkpoint case's "no unfilled placeholder" assertion.
- [x] 1.7 `tools/loop/loop.conf`: the checkpoint's paragraph says the split is taken (issue #856) and keeps the
      numbers (143 124 / 200 000) unchanged. Verification: `check-doc-values` and the structural read of the file.

## 2. The tests

- [x] 2.1 `tools/loop/tests/loop-cases.sh`: `run#aWorkerAtItsCheckpointIsContinuedBeforeTheBound` — the worker
      reports 150 000, the run ends it at 143 124, applies its kept work, starts exactly one continuation, records
      `worker-checkpoint`, and the item's result is the continuation's answer. Red on the tree before 1.1–1.4.
- [x] 2.2 `tools/loop/tests/loop-cases.sh`: `run#aWorkerBelowItsCheckpointIsNotSplit` — the worker reports
      100 000 for a few seconds and ends by itself; no checkpoint line, no `LOOP-TIMEOUT`, no continuation, no
      `worker-checkpoint` step, and the item keeps the worker's answer.
- [x] 2.3 `tools/loop/tests/loop-cases.sh`: `run#aWorkerAtTheCheckpointWithNoWorkRunsOn` — the worker reports
      150 000 and writes nothing; the checkpoint is read, the child is not ended, no continuation starts, and the
      item keeps the worker's answer rather than a cut at 143 124.
- [x] 2.4 `tools/loop/tests/loop-mutations.sh`: `278.1` (the checkpoint split removed), `278.2` (the split
      recorded as the bound's continuation) and `278.3` (the work condition removed), each noticed by the case that
      owns it. Verification: `--only '278.1 278.2 278.3'` red-when-mutated and green after the restore, then
      `--check` over the whole set.

## 3. The documents

- [x] 3.1 `guidelines/Backlog.recipes.md`:517 (section "What an item costs, and the bounds on it"): the paragraph
      that called the checkpoint a line "that never ends
      anything" states the split, its phase, its step name, the work condition and the one-continuation ceiling;
      the numbers and the refusal #833/#836 record stay. Verification: `check-doc-routes` and `check-doc-values`.
- [x] 3.2 `openspec/changes/fix-a-worker-is-split-at-its-checkpoint/`: proposal, the capability delta and this
      file, every scenario naming a case from §2. Verification: `LOOP-CHANGE-READY: ok` and the measure block's
      `scenarios=`/`cases=`/`unnamed=`.

## 4. Evidence

- [x] 4.1 `evidence/red-on-HEAD.log` — the checkpoint case red on the unmodified engine because no continuation
      child starts, and the below-checkpoint case green (it pins an absence the base already has). The no-work case
      is green on the base for the same reason: the base splits no child at all.
- [x] 4.2 `evidence/cases.log` — the three new cases green on the applied tree, plus the neighbouring context cases
      (`run#childPastItsContextBoundIsCut`, `run#aContextCutWorkerIsContinuedByItsOwnRun`,
      `run#aCutWhosePatchDoesNotApplyIsLeftCut`).
- [x] 4.3 `evidence/mutations-278.log` — the three records red-when-mutated and green after the restore, then
      `--check` over the whole set.
- [x] 4.4 `evidence/measure.log` — the `./tools/loop/measure.sh --change <this change>` block quoted, not
      re-derived.
- [x] 4.5 `evidence/structural-checks.log` — change-readiness, the case-declaration driver, doc routes, doc values,
      openspec hygiene. Portability and todo references are carried by the `LOOP-MEASURE-CHECK
      tools/check-portability.sh` and `LOOP-MEASURE-CHECK tools/check-todo-references.sh` rows of
      `evidence/measure.log` (4.4).
