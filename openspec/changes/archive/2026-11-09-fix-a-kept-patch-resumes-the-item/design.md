# Design — the kept patch is applied at the start, and a non-apply is recorded

## Where the step sits

`start-item` (`tools/loop/worktree.sh`) is the one place a tree becomes an item's tree: it refuses a dirty worktree,
copies a prepared change in, decides the item's branch by what that branch carries, and writes the tree's branch
marker. The apply belongs after the branch is settled and before the marker line — the branch is at the base there
(just cut, or reused because it carries no commit of its own), so the patch lands on the base exactly as the tree it was
taken from once stood on it. The pre-screen was the alternative the owner named; it is refused because a pre-screen
passes over items it does not start, and applying an item's work there would write into the worktree an item that may be
excluded in the same pass.

`resume_from_leftover` returns three answers rather than two: `0` the patch applied, `1` the item has no patch (the
ordinary case), `2` the patch does not apply. The start treats the first two as normal and prints the third, so an
unreadable patch can never be mistaken for a refusal of the item.

## The apply itself

- **Plain `git apply` first.** The patch is written by `keep_leftovers` against the same base the branch is cut from
  (`git diff HEAD` plus one `diff -u` per untracked file, both `-p1`), so it applies as it stands.
- **`--3way` second, and only second.** A base that advanced while the item waited moves the context under the diff;
  the 3-way merge absorbs that. It is second because it stages its result and a failed one leaves conflict state — the
  cheap path leaves the tree alone when it can.
- **Neither applies → put the tree back.** `git reset --hard` and `git clean -fd` (not `-x`: the prepared change under
  `openspec/changes/` is ignored and is not this step's to delete) return the tree to the base, including whatever
  `--3way` had already staged. Measured shapes on the base: `items/791` exit 1, `items/729` exit 1, `items/16` exit 128
  (`no valid patches in input`) — an arm that only reported the failure would leave a partially applied tree for the
  worker to build on.

## Where the outcome is recorded

The start runs before the run's `start` names the item and before any arm writes the item's record, so the outcome is
written beside the run state, keyed by the item (`state.sh record-resume` → `.resumes[<issue>]`), exactly as
`record-change-copy` writes the source an item consumed. `state.sh done` folds that sentence into the note of the item's
**first** record: one place, because every arm that records an item — closed, blocked, cut — writes its own prose, and a
second copy of the rule would be a second rule.

The sentence for a non-apply names the patch and the base it was tried against, because the reader's question is why
this attempt started from zero. The sentence for an apply names the patch too: the branch is at the base, so nothing on
it says that an earlier attempt's work is in the tree.

## Alternatives the decision eliminated

- **Commit the cut tree as a resume snapshot (owner's option 2).** Rejected by the owner: it reverses the shipped
  requirement "A child the run ended itself leaves no commit" and its three cases, and turns a half-applied tree into a
  claim nobody made (#271 measured what that costs).
- **Do nothing (option 3).** The item's symptom stands: 44 patches stay unread for ever.
- **A prompt pointer the child reads itself (option 4).** A step *before* this one, not a substitute: nothing gates a
  child that ignores the pointer.
- **Deleting the patch once it applied.** Refused: the patch is the record the run that ended the child owes (#308),
  and a later run that discards the applied tree would then have nothing to read. A stale patch replayed onto a base
  that already carries its work does not apply and is recorded by the arm above.

## Open decisions

none

## Residuals

- **The kept worktree is still between a bound kill and its resume.** A run that ends with a cap-ended child leaves the
  tree dirty, and the next run refuses it until `--reset-worktree` discards it — the repair the refusal and the report
  already name. This change does not widen that discard: what a patch does not carry (a file above the size bound, an
  ignored path outside the record directory) would be lost silently.
- **A resumed attempt's patch stays on disk** after the item lands; the next start of a *closed* item never happens, and
  a reopened one replays a patch that no longer applies, which the non-apply arm records.
- **`--3way` reports through git's own message**, which the step swallows: the item's note carries the outcome, not
  git's wording.
