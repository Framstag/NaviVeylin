# Design — add-loop-branch-hygiene

## The rule

**A branch the loop made, whose work is already in the base, is removed — and the branches that can be removed
are named.** One rule, asked in the question the project already asks everywhere else: *what does this branch
carry?* (`loop_unlanded_commits`, patch identity by `git cherry` — never ancestry, because the loop lands work
as `fix: … (#N)`, so a landed branch is not an ancestor of the base; issue #228).

## D1 — who deletes a branch

**Chosen: whoever owns it, at the moment its work is in the base.**

| branch | owner | when it goes |
|---|---|---|
| `fix/issue-<n>-<slug>` the run committed and merged | the run | right after the merge, once the worktree no longer holds it |
| `fix/issue-<n>-<slug>` of a previous attempt (adopted and merged by the run) | the run | with the merge: the work is in the base, and the branch is the same leftover |
| `loop-<timestamp>` (the run's own worktree branch, one per run) | the run | the sweep; it carries nothing by construction |
| a branch that still carries unlanded work | its owner | never, by the tooling — it is named as work to land |
| `loop/leftover/*` (a blocked attempt) | the item's owner | not in scope: it is the evidence of a parked item |

**Alternative A — only the sweep deletes.** Eliminated by the evidence: the sweep is a *person's* command
(`loop-clean.sh --apply`), so between two sweeps the branches of every closed item pile up — that is the
current state, 43 merged branches. The automatic half has to be automatic.

**Alternative B — the run deletes every branch it sees.** Eliminated: a branch the run did not create (a
person's own) is not the run's to delete, and the run's own merge path already prints the branch it merged —
the owner decides what happens to that one.

## D2 — the worktree constraint

The kept worktree is checked out on the item's branch, and `git branch -d` refuses a branch a worktree holds.
So the deletion is two steps, in this order:

1. the worktree is released from the branch — `git switch --detach` leaves it at the branch's own HEAD, which
   is where the work is anyway, and the next `start-item` switches it to the next item's branch (a detached
   HEAD accepts `git switch -c` ✓). The worktree's branch marker (`.pi/loop-branch`) **stays**: it names the
   item whose work the tree carries, and a tree that is dirty when the next run starts is attributed by it
   (the start-time refusal, issue #173);
2. the branch is removed with git's **safe** delete (`git branch -d`), which refuses a branch whose work is
   not in the base — the guard is git's, not ours, and it is the reason the release is safe to automate.

**Alternative — delete with `git branch -D`.** Eliminated for the run's own path: `-D` removes a branch whose
work is *not* in the base, which is exactly the mistake the rule forbids. The sweep needs the force (a branch
whose work was *re-landed* as a different commit is not an ancestor of the base, so git's own guard would
refuse a branch that carries nothing) — and it forces only after the patch-identity question says it carries
nothing.

## D3 — where the sweep lives

**Chosen: `loop-clean.sh`, which is already "what the loop has left behind, in one report, and one way to
remove it".** It reports without `--apply` and removes with it, it is idempotent, and it already names what it
keeps and why. The branch sweep follows the same shape: one line per branch, `would remove` / `removed`, and
the kept ones named with their reason.

**Alternative — a new command.** Eliminated: a second entry point for the same question splits the answer in
two, and the spec already gives `loop-clean` that job ("One command reports and removes what the loop left").

## D4 — what the report says

The report already lists every branch and prints an action for the ones that carry work. It gains **one line**
naming the branches that carry nothing and can be removed — the same question, the other half of the answer.
A reader who sees `branches:` with 48 names and `landed, removable:` with 43 of them knows what is garbage
without running git.

## Verification

- The loop's own suite is the unit test (`tools/loop-selftest.sh`, offline): three cases, each red on HEAD
  before the fix.
- The mutation harness makes the new invariants falsifiable (`tools/loop-mutations.sh`, labels `42.x`).
- The project's gate for `tools/loop/**` (`tools/loop/gate.sh`) is the whole set.
- No compiled file is touched: the structural checks are the loop's own gate and `openspec validate --strict`.

## Risk

| risk | why it is bounded |
|---|---|
| the release detaches the worktree and the next item cannot start | `git switch -c` from a detached HEAD is the normal path; the case that works an item after a release proves it |
| the sweep deletes a branch whose work is not in the base | it asks the patch-identity question first, and never touches a branch a worktree holds |
| the run deletes a branch the owner wanted | it deletes only the branch it merged itself, and only after the merge landed the work in the base |
| a branch the run merged is deleted while a person is reading it | the work is in the base; the branch name remains in the report's own history and in the reflog |
