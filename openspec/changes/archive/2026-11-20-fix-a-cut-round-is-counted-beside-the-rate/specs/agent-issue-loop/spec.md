# Spec Delta — agent-issue-loop

## MODIFIED Requirements

### Requirement: The report states whether the loop is getting better

A run's report SHALL also state what one run cannot show: a run that cost what the last one cost prints the same
shapes as one that is improving. It SHALL total the share of items whose review round 1 ended in a pass — read
from the verdict the round recorded, never from the round count alone — and the item wall median, minimum and
maximum, from the state it already reads, and SHALL print the share of its landings that reddened `main` from the
score the state records for each landing, never from the tracker (issues #247, #709).

It SHALL also name, beside that share, how many of the run's review rounds were handed a part the run's own input
cap cut — read from the scale the round's record carries (`metrics.reviewInput`) — so that a round which decided on
the head of its diff is not counted as a round that read all of it (issue #936).

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

#### Scenario: A round the input cap cut is counted beside the rate

- **WHEN** a run's report is printed for a state whose items record the scale the input cap cut from their round
  (`metrics.reviewInput`, written by `state.sh done --review-input`) on some rounds and nothing on others
- **THEN** the `review:` line prints the round-1 pass rate with the number of items whose round was handed a cut
  part in parentheses after it — `3/3 first rounds passed (2 truncated)` — beside the undecided and never-reviewed
  buckets it already printed, and a run no cap cut prints the rate with no parenthetical at all, because an empty
  record is no cut and a `0` there would be a measurement nobody made
- **Case**: `report#aCutRoundIsCountedBesideTheRate`

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
