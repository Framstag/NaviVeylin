# agent-issue-loop — deltas

## MODIFIED Requirements

### Requirement: The report states whether the loop is getting better

A run's report SHALL also state what one run cannot show: a run that cost what the last one cost prints the same
shapes as one that is improving. It SHALL total the share of items whose review round 1 ended in a pass — read
from the verdict the round recorded, never from the round count alone — and the item wall median, minimum and
maximum, from the state it already reads, and SHALL print the share of its landings that reddened `main` from the
score the state records for each landing, never from the tracker (issues #247, #709).

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

## ADDED Requirements

### Requirement: A landing records the commit it landed, and a run scores it

A landing SHALL record the commit that carried it onto the base, and the score of the push run that commit caused
SHALL be read once per landing and stored on the item — the read belongs to the state's writer, never to the report.
A landing whose run is not answered for yet SHALL be recorded unscored and named, never read as green, and a value
that is not a commit SHALL be refused.

#### Scenario: The record carries the commit the landing made

- **WHEN** a run records a landing with the commit its merge produced
- **THEN** the item's record carries that commit beside the branch, the commits' count and the evidence, and a
  value that is not a commit is refused without writing the record
- **Case**: `state#doneRecordsTheCommitItLanded`

#### Scenario: The score is read once per landing and stored

- **WHEN** the run scores its landings and an item records a commit the tracker answers for
- **THEN** one read per unscored landing writes `true` when the push run that commit caused failed and `false`
  when it succeeded, and a second scoring pass reads nothing, because the scored item is no longer unscored
- **Case**: `state#theLandingScoreIsReadOnceAndStored`

#### Scenario: A landing with no run yet is unscored, not green

- **WHEN** the tracker answers for no push run of the landing's commit, or does not answer at all
- **THEN** the item stays unscored, the reason is printed beside it, and no false is written
- **Case**: `state#aLandingWithNoRunIsUnscored`

#### Scenario: The run records the commit of the merge it made

- **WHEN** a run merges the branch an item finished on and closes the item
- **THEN** the item's record carries the base it advanced to, which is the commit the landing made
- **Case**: `run#mergesItsCommittedBranchBeforeClosing`
