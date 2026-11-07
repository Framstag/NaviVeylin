# Proposal — fix-loop-landing-verifies-the-checkout

## What Changes

- `tools/loop/worktree.sh` — new `check_checkout` (`:137`), called by `cmd_adopt_item` (`:357`) and
  `cmd_merge_item` (`:410`): the landing step refuses a project tree that is not on the base branch, naming both
  branches, instead of merging into whatever branch that tree holds.
- `tools/loop/state.sh` — `cmd_set_base` (`:362`) refuses a commit the base branch does not carry, so the base a
  closure is recorded against is one the branch has.
- `tools/loop-cases.sh` — two cases (`:4397`, `:4441`), registered in the list and the dispatcher.
- `tools/loop-mutations.sh` — records `57.1`, `57.2`, one per new guard.
- `guidelines/Backlog.md:416` — the landing paragraph names the checkout it verifies and the ancestry the record
  requires.
- `openspec/specs/agent-issue-loop/spec.md` — the requirement "The item is closed on a base that carries its fix"
  gains two scenarios, each with its case.

## Why

One defect, measured on the base `db888dd8b7c509cfba82854a21427ae5c9c8e192`, issue #302, found by the read-only
review of #301's change on 2026-10-24: **the landing step asserts a precondition it never verifies — that the
project's own tree has the base branch checked out — and the base it then records is any commit the run
computes.** Both halves of that sentence are one mechanism: the merge acts on whichever branch the working tree
holds, while the run's state records a *ref*, and nothing ties the two.

**The gap in the tree, today.** `cmd_merge_item` (`tools/loop/worktree.sh:403`) and `cmd_adopt_item`
(`tools/loop/worktree.sh:350`) each `git merge` in the project's own tree and each says in a comment that the
base branch is checked out there; neither reads the checkout. `check_base` (`tools/loop/worktree.sh:115`)
compares the recorded base against the base *ref's* commit, which answers a different question, and no
`symbolic-ref` existed in the engine before this change outside `run.sh`'s start guard. `cmd_set_base`
(`tools/loop/state.sh:362`) accepted any commit `rev-parse` resolved, with no ancestry check at all.

**The two consequences, both measured as mechanism rather than observed on a live run.** With a person
switching the project tree while a run is open — the start guard landed for #301 covers the tree a run is
*opened* on, not one switched under it — a branch that cannot fast-forward gives a failed merge and a `loop_die`
after the item's whole budget (loud), and a checkout that happens to be fast-forwardable **absorbs** the merge:
the item's commits land on a third branch, `main` does not move, `run.sh` re-reads the unchanged base
(`tools/loop/run.sh:257`), `set-base` accepts it, and the item is closed with `status/fixed-by` on a fix the base
never carried (`tools/loop/run.sh:261`).

**The single resulting fix.** The two merge commands verify the checkout they land in (`check_checkout`,
`tools/loop/worktree.sh:137`) and refuse with both branches named — the one the tree is on and the base branch —
which is also the repair. `set-base` refuses a commit the base branch does not carry
(`tools/loop/state.sh:369`–`:373`), so no closure can be recorded against a base that never had the fix. A
precondition that is asserted in a comment becomes a check at the moment it is depended on, and the state's
record may only name a commit the branch it claims to be has.

**Red test.** Two cases, both failing on `db888dd8` (2026-10-24T20:12Z), transcript
`evidence/red-on-head.log`:

- `worktree#refusesToMergeIntoATreeOffTheBaseBranch` (`tools/loop-cases.sh:4397`) — an item's branch, a project
  tree switched to `a-persons-branch`, then `merge-item`: HEAD returns 0 and the merge is absorbed, the case
  expects 5, the branch the tree is on named, and neither `main` nor `a-persons-branch` moved. `--case` on HEAD:
  `a project tree off the base branch refuses the merge: expected '5', got '0'`.
- `state#setBaseRefusesACommitTheBaseBranchDoesNotCarry` (`tools/loop-cases.sh:4441`) — a commit off the base
  handed to `set-base` is accepted at HEAD, exit 0 where 5 is owed, and the recorded base moves off the branch.

Tally: `evidence/red-on-head.xml` (`tests="2" failures="2"`).

**The requirement the defect contradicts.** `agent-issue-loop`, "The item is closed on a base that carries its
fix" (`openspec/specs/agent-issue-loop/spec.md:1278`): `status/fixed-by` and "the fix is in the tree" are one
claim, so a closure the tree does not back is a blocked item, never a settled one. It gains the scenario its own
claim was missing: the merge verifies the tree it lands in, and the base it records is one the base branch has.

**Owning guideline.** `guidelines/Backlog.md:416`, its "The loop" section — "The branch is the whole of an
item's fix until the base has it …" — whose paragraph names the two refusals the merge already takes and gains the
third.

**Surface, before the plan was fixed.** `bash tools/loop-selftest.sh --case-files | grep -E 'worktree\.sh|state\.sh'
| wc -l` → 73 cases declare one of the two files; `grep -c 'merge-item\|adopt-item\|set-base'
tools/loop-cases.sh` → 17 existing references in the cases; `grep -rn 'checked out\|symbolic-ref\|merge --ff-only'
tools/loop/*.sh | wc -l` → 12; `grep -rln 'set-base\|merge-item\|adopt-item' --include='*.sh' --include='*.md'
--include='*.json' .` outside the archive, `tools/loop-cases.sh` and `tools/loop/` → one hit,
`guidelines/Backlog.md:422`. Surface: 2 cases added, 2 scenarios added, 1 guideline paragraph extended, 2
mutation records added, 0 fixtures realigned.
