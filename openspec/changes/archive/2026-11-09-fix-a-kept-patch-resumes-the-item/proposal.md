# Proposal — the patch a bound-ended child left is the next attempt's resume input

## Why

**Root cause.** A child the run's own bound ends is never committed (`run.sh`'s cap path, the requirement "A child the
run ended itself leaves no commit"), so the item's branch stands at the base and `keep_leftovers`
(`tools/loop/run.sh:587`, base `0fc36948`) writes the tree it left to `<item dir>/leftover-from-killed-run.patch`. The
item's record names that file (`#674`), the report prints `recover #N` with its path, and **nothing ever read it**: the
next start cut the item's branch from the base again and the same reading was paid for from zero. 44 kept patches sat
unread in `.pi/issue-loop/items/`, #791's 7728 lines among them.

**Evidence, measured in this tree (2026-11-08, issue #754).** The patches exist and are non-empty for every item the
bound ended; the branch of each of them is at the base (`git -C <worktree> rev-parse HEAD` = the run's base). Three of
the kept patches no longer apply to the base they would be applied to — `git apply --check` on
`items/791/leftover-from-killed-run.patch` exits 1, on `items/729` exits 1, on `items/16` exits 128 (`no valid patches in
input`) — which is what the non-applying half of the step is measured against.

**The decision this change implements (owner, 2026-11-08, option 1).** Keep the no-commit rule and make the kept patch
the resume input: apply it onto a branch cut from the base, before the worker starts, and commit nothing the child did
not claim. Option 2 (commit the cut tree onto `fix/issue-<n>-<slug>`) is rejected because it reverses
`openspec/specs/agent-issue-loop/spec.md` "A child the run ended itself leaves no commit" and its three cases.

**The red test.** `run#aBoundEndedItemsKeptWorkIsResumedByTheNextStart` and
`run#aKeptPatchThatDoesNotApplyIsRecordedAndTheItemStartsFromTheBase`, both in `tools/loop/tests/loop-cases.sh`. On the
base, the first fails with the worker's tree carrying none of the kept work, the second with the item's note carrying
no sentence about the patch that did not apply (`evidence/red-on-HEAD.log`).

**The single fix.** `tools/loop/worktree.sh`'s `start-item` gains `resume_from_leftover`: the item's patch, applied with
`git apply` and, when the base moved under the diff, with `git apply --3way`, on the branch just cut from the base and
before a worker exists. When neither applies, the tree is put back on the base and the non-apply is recorded; `git
state.sh record-resume` writes the outcome beside the run state and `done` folds it into the item's note, the same way
every other fact a start learns before the item exists reaches the record.

## Blast radius

- **A start that resumes.** The tree a worker is handed carries the previous attempt's tracked diff and its untracked
  files. The branch is the item's and its `HEAD` is still the base commit: what is in the tree is applied, never
  claimed, and `end-item` still commits only what the child that finishes writes.
- **A start that does not resume.** A patch that neither `git apply` nor `--3way` takes leaves the tree on the base and
  the item is worked exactly as it was before this change, with one sentence added to its note. The patch file is not
  removed: it is the record the run that wrote it owes (#308), and the report keeps printing `recover #N` for it.
- **`run.sh`.** `leftover_patch_of` moved to `lib.sh` as `loop_leftover_patch_of`; the writer, the reader and the new
  applier derive the path from one place (three call sites, one spelling).
- **`state.sh`.** One new command, `record-resume`, writing `.resumes[<issue>]`; `done` reads it when it writes the
  item's first note. Nothing else in the record shape changes.
- **Not touched.** The bound is not raised, `--reset-worktree` still guards a kept worktree another run left dirty, and
  no case the requirement above names changes its assertion.

surface: 2 cases added, 0 rewritten, 4 code sites, 3 mutation records added, 1 requirement added in
`openspec/specs/agent-issue-loop/spec.md` — `grep -c 'resume_from_leftover' tools/loop/worktree.sh` → 2 (definition
and call); `grep -rn 'loop_leftover_patch_of' tools/loop/*.sh` → 5 lines in 2 files; `grep -c 'record-resume'
tools/loop/state.sh` → 7; `bash tools/loop/tests/loop-mutations.sh --list | grep 266` → 3 lines
