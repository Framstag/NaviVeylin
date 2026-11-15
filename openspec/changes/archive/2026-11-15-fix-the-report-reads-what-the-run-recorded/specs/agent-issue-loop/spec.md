# agent-issue-loop delta

## MODIFIED Requirements

### Requirement: The report states whether the loop is getting better

A run's report SHALL also state what one run cannot show: a run that cost what the last one cost prints the same
shapes as one that is improving. It SHALL total the share of items whose review round 1 ended in a pass — read
from the verdict the round recorded, never from the round count alone — and the item wall median, minimum and
maximum, from the state it already reads, and SHALL print the share of its landings that reddened `main` from the
score the state records for each landing, never from the tracker (issues #247, #709).

It SHALL also state the two figures the state holds and no line printed: the gate share of the items' own wall —
each item's recorded gate bucket divided by its own recorded wall, in tenths, with the worst two items named — and the
window that lies outside every item, taken from the run's recorded start and stop and given as the time and the share
of the window it is. Both SHALL be read from fields the run wrote, and the sums the report takes over them SHALL be
stated as its own arithmetic over recorded numbers, so that a figure the record does not hold prints as absent with the
reason it is absent, never as a span dated from the clock the report ran at.

#### Scenario: The two figures the state holds are totalled

- **WHEN** a run's report is printed for a state whose items record the verdict their last review round ended on
- **THEN** it prints the share of the items whose recorded verdict is a pass at round 1 against the items whose
  round ended in a verdict, and the median, minimum and maximum of the item wall times, both over the run's own
  items, and it says so, because a rate is not settled by one run
- **Case**: `report#trendFiguresFromState`

#### Scenario: A refused round 1 is not a pass

A round's count says the round was attempted, not how it ended, so an item whose round 1 refused and which the
run then blocked carries the same count as one whose round 1 passed.

- **WHEN** an item's round 1 ended in a refusal and it is the last round the item got, or the round after it
  passed
- **THEN** the first counts as a refusal and the second as no first-round pass, because the verdict decides and
  the count does not
- **Case**: `report#trendFiguresFromState`

#### Scenario: A round that ended without a verdict is in no rate

- **WHEN** a round ended without a verdict — the reviewer's answer was cut, the run's clock ended it, it wrote, or
  the state was written before the verdict was recorded — while another round of the run did end in one
- **THEN** that item is named apart as undecided and is in neither the numerator nor the denominator, and a state
  whose items carry no verdict at all prints `-` with the reason instead of a rate
- **Case**: `report#trendFiguresFromState`, `report#noRoundOrWallPrintsDashNotZero`

#### Scenario: A never-reviewed item is not a refused round

- **WHEN** an item ended before a review round ran — the run blocked it, or review was off — and its record holds
  `reviewRounds` 0
- **THEN** that item is counted as never reviewed and is not in the denominator of the round-1 pass rate
- **Case**: `report#trendFiguresFromState`

#### Scenario: A figure nobody measured prints as absent, not as zero

- **WHEN** no item of the run recorded a wall time
- **THEN** the report prints `-` with the reason beside it rather than a zero that reads as a measurement that was
  made
- **Case**: `report#noRoundOrWallPrintsDashNotZero`

#### Scenario: The figure the state does not hold is named where it is

- **WHEN** the report prints the landings that reddened `main` for a state whose landings record no commit — a state
  written before a landing kept one
- **THEN** it prints no rate over those landings and names that they record no commit, and it makes no tracker read
  of its own, because the report is still built from the state file and from git
- **Case**: `report#landingsAreTheStoredNumber`

#### Scenario: The landings that reddened main are the stored score

- **WHEN** the report prints the number of landings that reddened `main`
- **THEN** it prints the number of landings whose record carries the score `true` against the landings that carry
  a score at all, names apart the landings that carry a commit and no score yet and those that carry no commit,
  and makes no tracker read of its own — the report is still built from the state file and from git
- **Case**: `report#landingsAreTheStoredNumber`

#### Scenario: The trend block names the gate share and the outside window

- **WHEN** a run's report is printed for a state whose items record a gate bucket and a wall, and whose run records the
  start and the stop it happened between
- **THEN** the trend block prints `gate share:` as the sum of the items' gate buckets over the sum of their walls — in
  tenths, with the worst two items named, so that two runs that differ by 1.6 points are told apart — and
  `outside items:` as the recorded window minus those walls, with the share of the window that time is, and neither
  figure is read from a log or dated from the moment the report ran
- **Case**: `report#theTrendBlockNamesTheGateShareAndTheOutsideWindow`

#### Scenario: A figure the record does not hold is absent with its reason

- **WHEN** the report prints `gate share:` for items that recorded no phase duration at all, or that recorded phases
  without spending time in a gate, or for a state written before the items recorded phases; or `outside items:` for a
  state that records no stop, or for a window whose items recorded no wall
- **THEN** each line prints `-` naming which of those it is — no phase duration recorded, no gate ran, this state records
  no phase durations, this state records no run window, a recorded window and no item wall inside it, two records that
  disagree — and never `0 %` and never a span dated from the clock the report ran at
- **Case**: `report#theTrendBlockNamesTheGateShareAndTheOutsideWindow`

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
