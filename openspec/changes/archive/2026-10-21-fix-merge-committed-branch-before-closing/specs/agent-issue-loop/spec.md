# agent-issue-loop — deltas

## ADDED Requirements

### Requirement: The item is closed on a base that carries its fix

A run SHALL merge the branch it committed an item on onto the base it recorded before closing that item, and a
merge it cannot take SHALL leave the item open: `status/fixed-by` and "the fix is in the tree" SHALL be one
claim, so a closure the tree does not back is a blocked item with its branch named, never a settled one.

#### Scenario: The branch the run committed is merged before the item is closed

- **WHEN** an item passed its review with an archived change and the run had already committed that change and
  its evidence on the item's own branch
- **THEN** the branch's commits are on the base before the item is closed, the base the run recorded advances
  to the merge, and the branch with its commits is named in the run's record and in the closure's evidence
- **Case**: `run#mergesItsCommittedBranchBeforeClosing`

#### Scenario: A merge the base cannot take is refused rather than forced

- **WHEN** the merge of an item's branch cannot be taken — the project tree the base branch is checked out in is
  not clean, or the branch does not descend from the base the run recorded
- **THEN** the action refuses with the reason it found and the base stays where it was: it does not merge over
  work it would attribute to itself, and it does not replay the branch onto a tree nobody reviewed, so the
  branch's commits stay off the base and the closure that follows a merge is never reached
- **Case**: `worktree#refusesToMergeOverADirtyProjectTree`
