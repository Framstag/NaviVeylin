# agent-issue-loop delta

## MODIFIED Requirements

### Requirement: An item the run's own bound cut is recorded `cut`, not `blocked`

An item a bound the run enforces ended SHALL be recorded `cut`: the run's deadline or a role's turn bound
ending a review round, and the role's turn bound, the context bound, the child clock, a vanished writer or the run's deadline ending the item's own child.
It SHALL carry `bound` as its stop reason, with the key the bound is recorded in, whenever a bound the run enforces
ended it — a review round as much as the item's own child, because the bound is the run's and the item is what it
ended — so the mechanism's spend is totalled beside the result, SHALL write no
decision label and no comment, SHALL NOT enter the two-blocked streak `state.sh next` stops on, and SHALL be named on
a line of its own in the report and counted there as a cut rather than a block, so no reader needs the mechanism's
vocabulary to tell work from a refusal. The work an ended child left SHALL stay where the child left it, SHALL be
named in the item's own record, and SHALL NOT be committed.
The round a review bound ended SHALL be written on the branch as a cut with its announcement withdrawn, and SHALL stay
named in the item's own record as its own fact — the round, the key and the figure — beside the class it now shares
with the item. A state a
tree wrote before `cut` carried a bound keeps that `blocked` + `bound` pair out of the streak, while a pre-mechanism
pair with no stop reason still stops the run, and an answer the *transport* cut twice is not this case: no bound
ended it, so it stays a block with its reason.
The reason a bound-ended round leaves on the item SHALL name the bound that ended it and the figure it reached,
quoted from the child's own `LOOP-TIMEOUT` line, and SHALL NOT offer the two bounds in general as the answer: a
person reading the item tells the turn bound from the context bound, and how far past it the child ran, without
a transcript they were not handed (issue #829).
The class SHALL name the bound it stands for, as `boundKey` and `boundValue` beside it, and SHALL carry neither where
no configuration records a value for what ended the child — the item's own clock, the run's deadline and a vanished
writer are the class alone (issue #923).
A round the run ended SHALL record its own ending in the round's own field beside the item's class — the round, the
key and the figure, taken from the round's own `LOOP-TIMEOUT` line where the item's class carries no key of its own —
and the report SHALL count those rounds on a line of its own rather than folding them into the item's rate.

#### Scenario: A round the turn bound ended leaves no decision behind

- **WHEN** an item's tail writes `LOOP-VERDICT: closed`, its gate answered and its change is archived, and the run
  then ends review round 1 at the reviewer's role's turn bound
- **THEN** the item is recorded `cut` with `bound` as its stop reason and its round marked cut, no
  `agent/needs-decision` label is written for it
  and the report's decision list does not hold it, the branch carries `review-round-1-cut.md` and no
  `review-round-1-requested.md`, and `branch-state` reads `adoptable` — work the next run finishes, not a question
  only its owner can answer
- **Case**: `run#aReviewerEndedAtItsTurnBoundIsNotARefusal`

#### Scenario: A child the turn bound ended is recorded cut, not blocked

- **WHEN** an item's own child reaches its role's recorded turn bound and the run ends it there
- **THEN** the item is recorded `cut` with `bound` as its stop reason and the count in its note, the report counts it
  as a cut and not as a block and names it on the run's own bound line, no `agent/needs-decision` label and no
  retry-or-abandon comment is written for it, and nothing is closed
- **Case**: `run#childPastItsTurnBoundIsCut`

#### Scenario: A child the item's own wall clock ended is recorded cut, not blocked

- **WHEN** the wall-clock cap ends an item's worker that has left changes in the change root
- **THEN** the item is recorded `cut` with `bound` as its stop reason, the run commits none of the work — the change
  root's `HEAD` is still the base commit — the tree still carries what the child left, the item's note names the
  patch it was written to and says the attempt is the next run's to resume, and no decision label is written for it
- **Case**: `run#childOutlastingTheWallClockIsCut`, `run#treeOfAChildTheCapEndedIsNotCommitted`

#### Scenario: A child the run's own deadline ended is recorded cut

- **WHEN** the run's deadline passes while an item's child runs and the run ends it there
- **THEN** the item is recorded `cut` with `bound` as its stop reason and the `LOOP-TIMEOUT` transcript it left, no
  decision label is written for it, and the run records the deadline as its own stop reason
- **Case**: `run#childIsEndedAtTheRunsDeadline`

#### Scenario: Two consecutive cuts do not stop the run

- **WHEN** two items in a row are recorded `cut` and the queue still holds a candidate
- **THEN** `state.sh next` hands out that candidate, a block that follows a cut is the first block of its own pair,
  and two blocks in a row still stop the run
- **Case**: `state#nextHonoursEveryStopCondition`

#### Scenario: A block at a bound holds no pair

- **WHEN** two items in a row carry a bound — recorded `blocked` with `bound` as their stop reason by a state a tree
  wrote before `cut`, or `cut` with `bound` by this run — and the queue still holds a candidate
- **THEN** `state.sh next` hands out that candidate, because what the pair is read for is a refusal's prediction
  about the next item — the same gate, the same provider, the same tree — and a bound predicts nothing about an
  item that starts a fresh worker on a fresh tree; a refusal after a bound cuts is the first block of its own pair,
  and a state written before the mechanism existed, whose two `blocked` items carry no stop reason, still stops the
  run as it always did
- **Case**: `state#aBlockAtTheRunsBoundIsNoRefusal`

#### Scenario: A review round the turn bound cut names the bound and its figure

- **WHEN** the run ends review round 1 at the reviewer's role's turn bound of 3 turns, after the child took 3 turns
- **THEN** the reason on the item names the turn bound, the figure it reached and the key it is recorded in
  (`LOOP_CHILD_TURNS_MAX_REVIEWER`), quoted from the round's own `LOOP-TIMEOUT` line, beside the round, the cut and
  the sentence that leaves the change for the next run, and the item records that bound as its stop reason
- **Case**: `run#aReviewerEndedAtItsTurnBoundIsNotARefusal`

#### Scenario: The round before the gate names the bound that cut it

- **WHEN** the round before the gate is ended at the reviewer's turn bound and its transcript carries the bound,
  the key and the count
- **THEN** the item is recorded `cut` with `bound` as its stop reason and that reason naming the turn bound, the
  figure and the key, no gate child is started on the unread diff, and no round after the gate is asked
- **Case**: `run#aPreGateReviewEndedAtItsBoundNamesIt`

#### Scenario: A bound-ended item records the key and the figure it was cut at

- **WHEN** an item's own worker takes three turns past a worker turn bound recorded as 3 in `loop.conf`, and the run
  ends it there
- **THEN** the item is recorded `cut` with `bound` as its stop reason and `boundKey` `LOOP_CHILD_TURNS_MAX_WORKER`
  with `boundValue` `3`, read from the child's own `LOOP-TIMEOUT` line, the state's own table prints
  `bound LOOP_CHILD_TURNS_MAX_WORKER=3` in the record's `stop` cell, and the report names `#21` on its line of the
  items a bound cut short — so the archive can be totalled by the bound and not only by the class
- **Case**: `run#aBoundEndedItemRecordsItsBound`

#### Scenario: A round the run ended records its own ending beside the item's class

- **WHEN** the reviewer takes its third turn past a reviewer turn bound recorded as 3 and the run ends review
  round 1 there
- **THEN** the item is recorded `cut`, `metrics.roundBound` carries the round (`1`, or `pre-gate` for the round
  before the gate), the key `LOOP_CHILD_TURNS_MAX_REVIEWER` and the figure `3`, the record's `round` column prints
  `round 1 ended at LOOP_CHILD_TURNS_MAX_REVIEWER=3`, and the report prints
  `rounds a bound ended: 1 of 1 item(s) · #21 1 LOOP_CHILD_TURNS_MAX_REVIEWER=3` beside `bound: 1 of 1 items` —
  two figures, neither folded into the other
- **Case**: `run#aRoundTheRunEndedRecordsItsBound`

#### Scenario: A record written before the two fields is read by the bound its note names

- **WHEN** the report reads a state written before `boundKey` and `roundBound` existed — a record carrying `bound`
  with its figure in the note, and a record carrying `cut` with `cut-short` and the reviewer's key in the note
- **THEN** the first prints the bound it was cut at in its `stop` cell and the second prints its round in the `round`
  column, both read by the one derivation `state.sh` and `report.sh` share, which tells a round from the item's own
  cut by the reviewer's role in the key and the round's own verdict rather than by the fields being absent
- **Case**: none — no case pins the derivation: it was read on the committed archive when `cdf0648c` landed, and the
  commit records that it is pinned by no case