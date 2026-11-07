# Design — fix-the-run-end-sweeps-the-branches-it-made

## Root cause

`end_run` (`tools/loop/run.sh:315`) reports, optionally removes the worktree, and strips the kept worktree's build
output. It never asks which branches the loop names have their patches in the base. The run releases the one item
branch it merged (`tools/loop/run.sh:1786`), but a `loop-*` run branch is left at the base when `start-item`
switches the worktree off it, and every earlier branch a manual landing left stays. The only remover,
`loop-clean.sh:170-213`, is a command a person has to remember.

## The single fix

Add `worktree.sh prune-landed [--apply]` and call it from `end_run` after the report and after the worktree step,
unless the run is a dry run or it ended before the pre-screen. The order is the sweep's own: `--clean-worktree`
releases the branch that tree held in the step above, so a sweep run before it would read the run's own `loop-*`
branch as held and keep it. The subcommand reads the base the run recorded (falling back to the base branch), lists
the loop's own refs (`loop_own_branches`), and decides one branch at a time:

1. **never the base branch** — the candidates are only `fix/issue-*` and `loop-*`, so the base is not in the set;
2. **never a branch a worktree holds** — `loop_worktree_branches`, the same test git itself applies;
3. **never a branch whose change a reviewer refused** — an added `openspec/changes/archive/<change>/evidence/`
   containing `review-failed-round-*.md` is its owner's verdict (issue #185);
4. **landed** by any of four independent readings: `merge-base --is-ancestor`, no `git cherry` `+` line, identical
   content in the base for every path the branch touched, or every archived change the branch adds already present
   on the base. Patch identity is what the loop reads, but the four together are what the item's measurement used,
   and the extra readings keep a branch that merely looks landed from being deleted.

Only `--apply` deletes, and it deletes exactly the branches it named. The summary is
`worktree: <n> landed branch(es) removed · <n> kept (carries work the base does not) · <n> kept (checked out)`.
A run that is a dry run removes nothing.

## Blast radius

`tools/loop/worktree.sh` gains one subcommand and two local helpers; no existing subcommand changes.
`tools/loop/run.sh` gains one hook in `end_run`; the item-branch release after a merge is unchanged. The sweep runs
last, so the report names the branches that stood when it printed and the sweep's own lines name what it removed
after it. `loop-clean.sh` keeps its current inline branch sweep; the two commands share `loop_unlanded_commits`,
`loop_own_branches` and `loop_worktree_branches`, and the new command is deliberately more conservative because it
runs unattended. Because it is a `git branch -D` path, its refusal for a branch the base does not carry is
falsified by one mutation record (`tools/loop-mutations.sh --only 13.13`,
`evidence/mutation-13.13.log`: red at 2026-10-27T16:03:18Z, restored green).

## Alternatives eliminated

- **Call `loop-clean.sh --apply` from `end_run`.** It removes scratch trees and ends processes as well as branches,
  and its output is a mixed path/branch report. The run's end needs a ref decision, not a disk sweep; the manual
  command stays the one command for both.
- **Move the item-branch deletion into `merge-item`.** The run already releases the branch after the merge
  (`tools/loop/run.sh:1786`). Moving it would make `merge-item` release a worktree it does not own and would not touch the
  `loop-*` branches that are the measured accumulation.
- **Test landed by ancestry alone.** The loop lands work as a fresh `fix: … (#N)` commit, so a landed branch is not
  an ancestor of the base; ancestry alone was exactly issue #228's defect.
- **Delete the current `loop-*` branch too.** A worktree holds it; git refuses, and the next run needs the warm
  checkout. The sweep therefore keeps it and says so (`kept … — checked out`), and it stays until a run switches the
  worktree off it and names it again.

## Rollback

Remove the `prune-landed --apply` call from `end_run`. The subcommand is additive; `loop-clean` and the item-branch
release are untouched, so an older engine behaves exactly as before.
