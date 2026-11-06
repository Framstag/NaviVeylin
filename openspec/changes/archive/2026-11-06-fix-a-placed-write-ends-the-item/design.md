# Design — where the reading decides, and the one place a child may write

## Root cause

The placement the heartbeat takes is consulted only in the outcome block, and only through an arm that follows the
gate: `outside_write_decides` (`tools/loop/run.sh:1132`) is read at `run.sh:2301`, after the pre-gate round and the
gate child. The gate child is in fact skipped for such an item — `run.sh:2025`'s pre-gate branch carries
`[ "$worker_write_decides" -eq 0 ]` — but that conjunct was added for the pre-gate round (#758) and nothing states it
as a rule, pins it, or names it in a guideline. And no directory is offered to a child as a place to write, so a
project-relative path — which resolves against the project's own tree, because that is where the child starts — is
the only way to leave a file beside the change.

## The single fix

1. `tools/loop/run.sh`, immediately after `worker_write_decides` is set: the worker's reading is recorded as the step
   `outside-write`, and the note says the item ends at that reading, no gate child follows it, and which child the
   placed processes were tied to (`worker_write_attributed`, the same pids the outcome block's reason already
   carries). The arm that recorded the same step only when the difference did **not** decide (`run.sh:2264`) is
   deleted rather than kept beside it, so one reading is recorded in one place.
2. `tools/loop/run.sh`: `scratch_dir="$item_dir/scratch"` is created where the item's own directory is made, and
   passed as `SCRATCH_DIR` to the **four** renders that hand a child its prompt — `worker.md` twice (`run.sh:1977`
   for the item's worker and `run.sh:2513` for the round a refusal buys), `worker-gate.md` and `worker-tail.md`;
   each of the three templates names it beside the change root and says what a relative path resolves to.

Nothing else changes: the outcome arm that writes the item's reason, the landing rule, the strict mode and the
reading's cadence are as they were.

## Blast radius

- `tools/loop/run.sh` — four render calls and the reading's record; no new process, no new file per item beyond
  `scratch/`, and no change to what a child is started in.
- `tools/loop/prompts/{worker,worker-gate,worker-tail}.md` — one item in each child's work package.
- `tools/loop-cases.sh` — one case rewritten (`run#aWriterTheRunCanPlaceBlocksTheItem`), one added
  (`run#aChildIsGivenOneScratchDirectory`), each registered in the case list and the dispatcher.
- `guidelines/Backlog.recipes.md`, section 7a — two paragraphs stating both halves.
- `openspec/changes/fix-a-placed-write-ends-the-item/specs/agent-issue-loop/spec.md` — two added requirements.

No compiled file is touched, so no build is owed: the structural checks of the files edited are `bash -n` on the
shell, `tools/loop-selftest.sh --case driver#everyCaseDeclaresTheFilesItCanReach` for the case declaration,
`bash tools/loop-mutations.sh --check` for the mutations that name the lines this change moved, and the doc gates
for the two `.md` files.

## Alternatives the evidence eliminated

- **Widen the placement to the child's own standing shell** (the write in the tracker entry's title shape). Eliminated
  by measurement, not by preference: `run#aPeerWriteWhileAChildStandsDoesNotDecideTheItem` and
  `run#theWorkersOwnShellWriteIsRecordedAndNotPlaced` pin that a run marking its own chain decides every difference in
  a tree that the run's own landings, a peer session and every landing write (#288, #704, 2026-10-24). The tracker
  entry itself defers that half to a decision.
- **Keeping the outcome arm and adding the step**: two owners for one reading, and the item's reason would still be
  written after a child had been started. Rejected.
- **A scratch directory under the worktree** (`$worktree/.scratch`): it would be a path the item's own commit and
  the archived change carry, and the run already clears build scratch there (`strip-scratch`). The item's own
  directory is where the run's evidence goes and is not part of the tree git status reads, which is what makes a
  write there no difference.
- **Removing the project-relative hazard by starting children in the worktree**: refused by the project's own policy,
  which resolves where the child starts — the reason `run_child` starts in the project (`D4`).

## Open decisions

**Whether a child's write the placement cannot tie to it should end an item whose branch and gate are sound.** The
item's own record asks for it — "A second half is worth a decision rather than a change: whether the worker's own
writes into the project tree, with the paths named and the item's change untouched, should end an item whose branch
and gate are sound" — and the recorded decision on the item is `none`, so this change takes neither side: the arm
the placement decides is what it moves, and `run#theWorkersOwnShellWriteIsRecordedAndNotPlaced` (the worker's own
shell standing in the project, #704) keeps today's behaviour. What is open: (1) leave the rule as it stands — a
write the placement cannot tie to the child is noted and does not end the item; (2) end the item for the worker's
own shell too, on the paths the heartbeat names, with the item's change untouched. Recommended: (1) until a person
records otherwise, because a run that marks its own chain decides every difference in a tree its own landings, a
peer session and a landing write also touch (measured 2026-10-24, #288), and this change's evidence eliminates that
widening rather than deferring it.

## Rollback

Revert the commit: the two requirements, the two cases and the guideline paragraphs go with it. Nothing is
migrated, no evidence moves, and a run that never reads `SCRATCH_DIR` behaves exactly as before (the directory is
created and unused).
