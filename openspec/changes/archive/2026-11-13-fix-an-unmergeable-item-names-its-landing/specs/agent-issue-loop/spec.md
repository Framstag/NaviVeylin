# agent-issue-loop delta

## ADDED Requirements

### Requirement: An item a merge refused names its landing

The run records `stopReason: unmergeable` when the branch it committed for an item could not be merged onto the
base, and the work is then finished and a landing rather than a question. The report's action list SHALL name that
item's landing as its own action: the branch the refusal wrote, what that branch carries, and the command that lands
it — `git -C <root> merge --ff-only <branch>` — and, where the refusal listed the paths that refused it, those paths
and the `git -C <root> status --porcelain` that lists them now. The landing SHALL be printed once: the branch that
landing line names SHALL NOT be printed again by the branch arm, and an item the run escalated keeps the decision
line it wrote, because a decision is a question beside the repair and not a second landing.

#### Scenario: A refused landing is an action and not only a decision

- **WHEN** a run ends holding an item recorded `result: blocked` with `stopReason: unmergeable`, whose note carries
  the branch the merge refused and, for a tree that was not clean, the paths `git status --porcelain` printed
- **THEN** the action list prints one line for that item naming the branch, what it carries (`<n> commit(s) the base
  does not have`, and that the branch is not in this tree when the tree no longer holds it — never a count of zero),
  and the command `git -C <root> merge --ff-only <branch>`, with the paths the refusal named and the
  `git -C <root> status --porcelain` that lists them when the refusal listed any, and that branch is not printed a
  second time by the branch arm
- **Case**: `report#anUnmergeableItemNamesItsLanding`
