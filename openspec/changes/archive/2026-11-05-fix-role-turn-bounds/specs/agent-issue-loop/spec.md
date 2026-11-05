# Spec Delta — agent-issue-loop

## ADDED Requirements

### Requirement: A child is bounded by its own role's turns

Each child SHALL be ended when the turns **it** has taken reach the bound its role records, and the item's own
turn total SHALL NOT end a child. The bound SHALL be read through the child's role, counted from the turns the
child added to the item's event stream, and named in the transcript with the role and the key — because one count
over the stream every child appends to decides which phase is discarded, not whether the item is converging.

#### Scenario: A phase's spend does not end the phase after it

- **WHEN** the worker takes ten turns, more than the item's old total but below its own role's bound, and the
  round after it takes the eight turns its role records
- **THEN** the worker's transcript carries no `LOOP-TIMEOUT`, the reviewer is ended at its own bound of eight turns
  with a line naming `LOOP_CHILD_TURNS_MAX_REVIEWER` and its role, and the item is recorded `blocked` with that
  round marked cut
- **Case**: `run#eachChildIsBoundedByItsOwnRolesTurns`

#### Scenario: A role whose key is `0` runs unbounded, and the item's total is still reported

- **WHEN** a role's key records `0` and its child takes three turns
- **THEN** no turn bound ends it, while the item's total is still counted over its children (`metrics.turns`) and
  printed by the report's `calls:` line beside the calls it was made of
- **Case**: `run#aRoleWithNoRecordedTurnBoundRunsUnbounded`

## MODIFIED Requirements

### Requirement: The diff is read before the gate, and a fresh child runs the one gate

An item's gate SHALL be run by a child of its own, after the diff has been read by a reviewer that is not the child
that wrote it, so a finding a reader can make from the diff is not paid for by a gate and a second full pass.

#### Scenario: The round is read before the gate child is started

- **WHEN** a run with the pre-gate review on reads a change child that ended at the diff (`LOOP-DIFF-READY`) and left
  no gate line
- **THEN** the run commits that diff, starts a reviewer on it, and starts the gate child only after that round
  answered — the reviewer's round is observable before the gate is reached, and the item still spends one gate child
- **Case**: `run#thePreGateReviewRunsBeforeTheGate`

#### Scenario: The findings reach the child that runs the gate

- **WHEN** the round before the gate refuses the diff with a blocking finding
- **THEN** the findings are appended to the gate child's prompt, which applies them in one pass before running the
  gate, and no rework worker is started for them because the item was not refused
- **Case**: `run#thePreGateReviewRunsBeforeTheGate`

#### Scenario: The round before the gate costs no round of the budget

- **WHEN** the round before the gate refuses the diff and the item's change then passes the round after the gate
- **THEN** the item records one review round, because `--max-review-rounds` bounds the rounds after the gate and the
  round before it refuses nothing
- **Case**: `run#thePreGateReviewRunsBeforeTheGate`

#### Scenario: A round the run cut before the gate is left for the next run

- **WHEN** the round before the gate never answers — cut by the deadline, ended at its role's turn bound, or cut
  twice on its way to the file
- **THEN** the item is recorded `blocked` with that round named as the one before the gate, no gate child is started
  on a diff nobody read, and the round after the gate is not asked either
- **Case**: `run#aPreGateReviewThatIsCutLeavesTheChangeForTheNextRun`

#### Scenario: A change child that ran the gate itself is read as it always was

- **WHEN** the change child's report carries a gate line and no `LOOP-DIFF-READY`
- **THEN** the run starts no round before the gate and no gate child, because a gate the run did not run is not
  re-run and a round before a gate that has already run has nothing to read
- **Case**: `run#childUsageIsRecordedPerItem`, `run#announcesAndRecordsTheReviewRound`

#### Scenario: A phase's checklist is the prompt of the child that performs it

- **WHEN** the phase state is printed for phase E
- **THEN** the checklist it prints is the gate child's own prompt and the call names that file, because the run
  defers the gate to that child
- **Case**: `phase#oneCallPrintsThePhaseState`
