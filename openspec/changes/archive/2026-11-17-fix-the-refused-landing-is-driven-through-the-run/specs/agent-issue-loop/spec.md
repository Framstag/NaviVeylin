# agent-issue-loop delta

## MODIFIED Requirements

### Requirement: An item a merge refused names its landing

The run records `stopReason: unmergeable` when the branch it committed for an item could not be merged onto the
base, and the work is then finished and a landing rather than a question. The report's action list SHALL name that
item's landing as its own action: the branch the refusal wrote, what that branch carries, and the command that lands
it — `git -C <root> merge --ff-only <branch>` — and, where the refusal listed the paths that refused it, those paths
and the `git -C <root> status --porcelain` that lists them now. The landing SHALL be printed once: the branch that
landing line names SHALL NOT be printed again by the branch arm, and an item the run escalated keeps the decision
line it wrote, because a decision is a question beside the repair and not a second landing.

The refusal SHALL be a record and not a sentence: the run SHALL write the branch it could not merge and every path the
refusal named as `mergeRefused` on the item's record, refused half-formed — a path with no branch, and a branch on a
record whose mechanism is not `unmergeable` — and the report SHALL print the branch and the paths from that record
verbatim. Only where the state carries no refusal record — a state written before the field existed — SHALL the block
fall back to the note, and it SHALL say on the line that what it printed is re-derived and not read from a record.

#### Scenario: A refused landing is an action and not only a decision

- **WHEN** a run ends holding an item recorded `result: blocked` with `stopReason: unmergeable`, whose note carries
  the branch the merge refused and, for a tree that was not clean, the paths `git status --porcelain` printed
- **THEN** the action list prints one line for that item naming the branch, what it carries (`<n> commit(s) the base
  does not have`, and that the branch is not in this tree when the tree no longer holds it — never a count of zero),
  and the command `git -C <root> merge --ff-only <branch>`, with the paths the refusal named and the
  `git -C <root> status --porcelain` that lists them when the refusal listed any, and that branch is not printed a
  second time by the branch arm
- **Case**: `report#anUnmergeableItemNamesItsLanding`

#### Scenario: The refused landing is read from the record

- **WHEN** an item recorded `stopReason: unmergeable` also records `mergeRefused` — the branch the merge refused and
  every path the refusal named — beside a note that names a different branch and other paths
- **THEN** the action line's branch, its commit count and its paths are the recorded ones, the note's branch and paths
  are printed nowhere, the line does not say its landing was re-derived, and the item is one landing line; a state
  written before the field existed prints the note's refusal with the line saying it was re-derived from the note
- **Case**: `report#theRefusedLandingIsReadFromTheRecord`

#### Scenario: The refusal the record carries is the run's own merge attempt

- **WHEN** a run finishes an item whose merge the base cannot take — `merge-item` refuses the project's own tree,
  because it carries paths no reading of *that* item differs on — and the run blocks the item
- **THEN** the item's record carries `result: blocked` with `stopReason: unmergeable`, `mergeRefused.branch` is the
  branch the run asked the merge for, and `mergeRefused.paths` holds every path the refusal printed after
  `onto the base:` — not the first of them — with no `merged` record beside it, the item closed nowhere and escalated
- **Case**: `run#doesNotCloseOnAMergeTheBaseCannotTake`
