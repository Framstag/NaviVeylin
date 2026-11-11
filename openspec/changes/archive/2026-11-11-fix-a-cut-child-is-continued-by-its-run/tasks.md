# Tasks — a cut child is continued by the run that cut it

## 1. The applier: the tree the run that cut the child is still standing in

- [x] 1.1 `tools/loop/worktree.sh`: `continue_from_leftover` (the `continue-item` command) — put the worktree back on
      the branch's `HEAD` (`reset --hard` + `clean -qfd`), remove the **ignored** record-directory paths the kept patch
      carries, then call `resume_from_leftover` unchanged and record the answer with its own `--detail` sentence. The
      three exit codes are the applier's own (`0` applied, `1` no patch, `2` no apply), and `record_resume` grew the
      optional detail argument that carries the sentence.
- [x] 1.2 `tools/loop/worktree.sh`: the usage block names `continue-item`, and the help range still covers it.

## 2. The run: one continuation, its own bound, its own step

- [x] 2.1 `tools/loop/run.sh`: after the worker child, a context-bound cut applies the kept patch itself and starts one
      continuation child; `worker_report`/`worker_exit` are set from it, so every arm below reads the continuation's
      answer with no second path through the outcome block. The block is guarded by the run's own deadline (design D3)
      and by the ceiling (it is in the item body, which runs once per item).
- [x] 2.2 `tools/loop/run.sh`: `child_ended_by_context_bound` — the run reads which bound ended the child from the
      transcript `run_child` already wrote, so nothing about the child's stream is read a second time.
- [x] 2.3 `tools/loop/run.sh`: the context bound is read from the child's **own** lines (`ctx_lines` taken from the
      event file before the fork), so a fresh session does not inherit the number that ended its predecessor.
- [x] 2.4 `tools/loop/run.sh`: the step is recorded as `worker-continuation` with the patch and the `HEAD` it was
      applied onto, and the run prints one line naming the patch's file and hunk counts.
- [x] 2.5 `tools/loop/prompts/worker-continuation.md`: the prompt — the work package, the bound line, the patch and its
      summary, and the route to `worker.md` for the phases, the project's routing, the shell's refusals and the report
      block.
- [x] 2.6 `guidelines/Backlog.recipes.md` §7b: the continuation, its three limits and its measurement, beside the
      bound it belongs to.

## 3. Tests — the cases and the mutations

- [x] 3.1 `tools/loop/tests/fixtures/pi`: `PI_STUB_REPORT_CONTINUATION`, so a case reads *whose* answer ended the item.
- [x] 3.2 `tools/loop/tests/loop-cases.sh`: `run#aContextCutWorkerIsContinuedByItsOwnRun` — exactly one continuation,
      the tree it starts on carries the patch's work and no commit, the resume is `applied`, the step is recorded, and
      the item's result is the continuation's verdict.
- [x] 3.3 `tools/loop/tests/loop-cases.sh`: `run#aCutWhosePatchDoesNotApplyIsLeftCut` — a patch with no hunk in it is
      the applier's `no-apply`, no continuation starts, and the item stays `cut` with the sentence in its note.
- [x] 3.4 `tools/loop/tests/loop-cases.sh`: `run#childPastItsContextBoundIsCut` rewritten — its child now leaves work,
      and the case asserts the run continues it once, the continuation is cut at the same bound, and no third child
      starts; the rendered prompt carries no unfilled placeholder.
- [x] 3.5 `tools/loop/tests/loop-mutations.sh`: `276.1` (the block that starts the continuation never runs) and `276.2`
      (the context bound read from the whole stream again), both noticed by
      `run#aContextCutWorkerIsContinuedByItsOwnRun`; record `9.3` re-quoted onto `run_child`'s new report argument.

## 4. Evidence

- [x] 4.1 `evidence/red-on-HEAD.log` — the two new cases on the tree before this change: the first fails because no
      continuation child starts, the second because `run.resumes` carries no `no-apply`.
- [x] 4.2 `evidence/mutations-276.log` — both records red-when-mutated and green after the restore, then the record
      check over the whole set (`338 of 338 records apply`).
- [x] 4.3 `evidence/selftest-files.log` — the case set the delta owes (`--files` over the engine files it changes):
      245 of 246 green, and the one that is not is the HUP case, which is a pool-timing case this change does not touch.
- [x] 4.4 `evidence/gate.log` — the whole set (413 cases) at 4 in flight: 412 PASS, and the 413th is the same HUP case,
      which had not answered when this session ended the suite. `evidence/pool-head.log` is the same pool on the
      unmodified engine: 51 PASS, this change's two cases red as expected, and no verdict for that case either.
