# Design — fix-merge-committed-branch-before-closing

## Root cause

The success path commits the item on its own branch and closes the item without merging that branch:

- `tools/loop/run.sh:723` — `worktree.sh end-item` commits the fix and the archive on `fix/issue-<n>-<slug>`.
- `tools/loop/run.sh:941-943` — the `closed)` outcome writes the closure through `labels.sh close` and records
  the item; no path between them merges.
- `tools/loop/worktree.sh:230-274` — `adopt-item` is the loop's only merge, and it is called from
  `run.sh:212` (the pre-screen, for a branch it recorded as an exclusion) and `run.sh:653` (a later
  `start-item` refusal). Both need the item to be visited again, and a closed item is never picked
  (`prescreen#settledItemIsDropped`), so the branch is stranded by construction.

Measured 2026-10-20 on #201 (issue #229): two commits on the branch, a one-round PASS, the closure recorded,
the base unmoved, a person fast-forwarding `main` 35 minutes later.

## The single fix

1. `tools/loop/worktree.sh` gains `merge-item <issue> <branch>`: it checks the recorded base is the current
   base (`check_base`), that the branch exists and carries commits the base does not, that the base is an
   ancestor of it, and that the project tree — where the base branch is checked out — is clean; then
   `git merge --ff-only`. One printed line: `merged: branch <name> -> <sha> · commits <a,b>`.
2. `tools/loop/run.sh` gains `merge_committed_branch <issue> <branch>`: it calls that command, advances the
   run's recorded base to the merge, and parses the branch and its commits out of the printed line.
3. On the `closed` outcome, immediately before the closure, the run calls it. Success records
   `merged.branch` / `merged.commits` in the item and names the merge in the evidence comment; a refusal
   (exit 5) turns the outcome into `blocked` with the refusal as `blocked_reason`, which the existing blocked
   path already puts on hold, escalates with the branch as evidence and records.

## Alternatives the evidence eliminated

**Route the success path through `worktree.sh adopt-item`** (the item's own candidate). Impossible as the
same action: `adopt-item` decides a branch on what a stranger to the run can see — exactly one archived change
of its own, a green verification record *inside that change's* `evidence/`, and no reviewer-failure marker —
because for a previous attempt the branch is the only input (`worktree.sh:189-223, 241-254`). The run has
already decided its own item on its own inputs (`run.sh:745-791`: the archive it saw in the diff, the gate's
XML, the evidence the change carried, its own review rounds), so reusing `adopt-item` would block items the
run legitimately closes: a project with no gate (`run#noGateStillRuns`), a change whose evidence carries no
XML, a docs-only item with no gate at all. That is a second rule with a different bar, not this fix.

**Cherry-pick the commits onto a moved base.** Impossible: `start-item` cuts the branch at the recorded base
and `end-item` only commits on it, so `base..branch` always has the base as its ancestor; a base that moved
under the item is `check_base`'s recorded refusal, and replaying the work onto a tree nobody reviewed is
`adopt-item`'s decision (`worktree.sh:262-271`), taken on evidence the run's item does not need.

**Let the pre-screen adopt the strands of items the loop itself closed** (the second guard the item names).
Not a fix for this defect: it repairs the residue by visiting the item again — the very mechanism that makes
the strand, since the pre-screen only queues open, unsettled items. An existing strand stays hand-repairable
with `worktree.sh adopt-item`, and after this change no new one is produced.

## Blast radius

- `tools/loop/worktree.sh` — one command and one dispatch line; no existing command changes.
- `tools/loop/run.sh` — one helper beside `adopt_finished_attempt`, and one call on the `closed` outcome; the
  `blocked`, `skipped` and `falsified` outcomes are untouched, and so is the adoption of a previous attempt.
- `tools/loop-cases.sh` — two cases; the existing 148 shell cases are re-run unchanged, two of them adjusted
  where the fix moved what they read:
  - `run#commitsWithoutAnAmbientIdentity` counted the two commits since `main`; the base now carries them, so it
    counts them from the base the run started on. Same assertion, same number, a reference that survives the
    merge.
  - `worktree#keepsByDefaultAndRemovesOnRequest` matched `worktrees/loop` as a substring of the worktree list.
    The loop runs its own suite from `.pi/worktrees/loop`, so every scratch path contains that segment and the
    case failed on the run's own root (reproduced on HEAD at a copy whose path held it, green where it did
    not). It now matches the listed worktree path exactly (`--porcelain`, `-xF`); it is an assertion narrowed
    to what it meant, not a behaviour change.
- `guidelines/Backlog.md` §7 — one paragraph stating what the run does with the branch it committed.
- `openspec/specs/agent-issue-loop/spec.md` — one requirement merged at archive.

Nothing compiled, no Android component, no native boundary, no tracker write: the loop's shell engine and its
suite. The run's own commit identity, the review rounds, the wall clock and the label matrix are untouched.

## Risk and rollback

- *A dirty project tree blocks an item that used to close.* That is the requirement's own answer, not a
  regression: the fix would otherwise be committed on a branch and the closure would lie. The existing rule
  for a dirty tree is already a refusal (`worktree.sh:257-261`), reused here.
- *A second item's base.* After a merge the run advances its recorded base; without that the next
  `start-item` would refuse the run's own merge as a stale base (`adopt_finished_attempt` does the same at
  `run.sh:175-178`).
- Rollback: revert the `fix-*` commits. Branches created by runs are not part of the fix, so a revert restores
  today's behaviour with no tree state to unwind.

## Verification

- Red on HEAD: `run#mergesItsCommittedBranchBeforeClosing` (recorded in `evidence/red-on-HEAD.txt`).
- Green after the fix: `run#mergesItsCommittedBranchBeforeClosing`,
  `worktree#refusesToMergeOverADirtyProjectTree`, then the whole loop suite, offline:
  `bash tools/loop-selftest.sh` — that is the project's gate for `tools/loop/**` (`AGENTS.md` "Agent iteration
  loop"); the Gradle gate is not owed because the change touches no compiled file (`guidelines/Build.md` §2,
  scope block).
- Revert-check: remove the `merge_committed_branch` call from `run.sh`, the red case must fail again, restore,
  re-run green.
- No device, emulator or pixel measurement applies: this is unattended shell tooling.
