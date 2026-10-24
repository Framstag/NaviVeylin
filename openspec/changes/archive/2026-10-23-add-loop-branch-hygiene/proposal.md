# add-loop-branch-hygiene

## Why

The loop creates a branch per item and never removes one. Measured on this machine, 2026-10-22: 53 local
branches, **43 of them already merged into `main`** — pure garbage — plus 5 that still carry unlanded work and
2 `loop/leftover/*` branches that are a blocked attempt's evidence.

**Root cause, one sentence:** no path in the loop deletes a branch it made — `worktree.sh` creates them
(`switch -c`, `worktree add -b`) and its removal path keeps them on purpose (`worktree.sh:442`: `worktree
removed (branch <name> kept)`), and the report names a branch only when it still carries unlanded work
(`report.sh:196-214`), so a branch whose work is already in the base is not named as an action *and* not named
as removable either: it is invisible in a flat list of 53 names.

**Why it matters beyond the disk.** The report's own rule is that a branch is an action by what it *carries*
(issue #228) — and the same question answers "is this branch garbage?". The machinery to ask it exists
(`loop_unlanded_commits`, patch identity by `git cherry`) and is used in two places; the third place — the
deletion — was never written. The one command that answers "what has the loop left behind" (`loop-clean.sh`)
removes build output and killed suites' scratch trees and never looks at a branch.

**The constraint that kept it from being trivial.** The kept worktree `.pi/worktrees/loop` is *checked out* on
the item's branch, and git refuses to delete a branch a worktree holds. So the deletion has to release the
worktree from the branch first — which the run can do, because it owns both.

## What Changes

- `tools/loop/worktree.sh` — a `release-branch <branch>` subcommand: the worktree is detached from the branch
  it holds and the branch is then removed with git's own safe delete, which refuses a branch whose work is not
  in the base.
- `tools/loop/run.sh` — a branch the run merged is released and removed: the run created it, the run merged
  it, and the run leaves no branch behind for an item it closed.
- `tools/loop/loop-clean.sh` — the sweep: every branch the loop names whose patches are all in the base and
  that no worktree holds is removed with `--apply`; the ones that carry work and the ones a worktree holds are
  kept and named.
- `tools/loop/report.sh` — a second branch line names the branches that carry nothing and can be removed, so
  the flat list of names says which are garbage.
- `tools/loop-cases.sh` — the three cases; `tools/loop-mutations.sh` — the mutations for the new invariants.
- `guidelines/Backlog.md` §7 "The loop" — the paragraph that states who owns a branch and when it goes.

## What this change leaves out

- **A branch that carries unlanded work is never removed** — by the run and by the sweep alike. It is named as
  work to land, and the person's `git branch -d` after landing it is the end. The sweep removes only what the
  base already holds.
- **`loop/leftover/*`** — a blocked attempt's branch is evidence for an item the loop parked; it is not in the
  sweep's scope and stays until the item is worked or abandoned.
- **The remote** — nothing here pushes or deletes a branch on the remote. The run's `--push yes` pushes the
  base, not the branches.
