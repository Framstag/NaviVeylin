# agent-issue-loop delta

## ADDED Requirements

### Requirement: A review round a bound ended is restarted in the run that cut it

A round a role's turn bound or the item's context bound ended SHALL be restarted once in the same run, in a fresh
session on the same bounded input, with what the cut round had written handed to it; only a second bound SHALL be
a `cut`. `LOOP_REVIEW_RESTART_MAX` SHALL bound the restarts one round takes (`0` or absent = none), a restart SHALL
NOT be started past the run's deadline and SHALL NOT count a second round, and it SHALL be recorded with the round,
the bound and the cut round's report.

#### Scenario: A round a bound ended is restarted and answers

- **WHEN** a review round ends at its role's turn bound with no `VERDICT:` and no refusal — an unfinished reading
  rather than an objection
- **THEN** the run starts the round again in a fresh session, its prompt carries the cut round's `BLOCKING:`/`NOTE:`
  lines, the bound's own line and the report of the round before the gate when one ran, the item closes on the
  restart's answer, and the round count stays at one because a restart is the same round read twice
- **Case**: `run#aBoundCutRoundIsRestartedWithItsFindings`

#### Scenario: A second bound is the cut, and the record names the restart

- **WHEN** the restart is ended by a bound as well, or the round before the gate is ended at its bound and restarted
- **THEN** only the second ending records the item `cut` with `bound` and the key the bound is recorded in, and the
  item's record carries `metrics.reviewRestart` naming the round, the bound it reached and the file the cut round
  had written — so a round that answered after a restart does not read as a round that answered first time
- **Case**: `run#eachChildIsBoundedByItsOwnRolesTurns`, `run#aPreGateReviewEndedAtItsBoundNamesIt`

#### Scenario: A round the transport cut twice is asked again and not restarted

- **WHEN** a reviewer's report arrives cut on its way to the file twice and no bound ended that round
- **THEN** the round is asked once more under its `-retry` name and is never given a `-restart` prompt, and the item
  is recorded `blocked` with that reason, because no bound ended it and nothing about it says the item is converging
- **Case**: `run#aPreGateReviewThatIsCutLeavesTheChangeForTheNextRun`

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

- **WHEN** the round before the gate never answers — cut by the deadline, cut twice on its way to the file, or ended
  at its role's turn bound or at the item's context bound both before **and** after the one restart this run gives it
- **THEN** the item is recorded `cut` when a bound the run enforces ended that round and `blocked` when the transport
  did, with that round named as the one before the gate, no gate child is started on a diff nobody read, and the
  round after the gate is not asked either — while a round the bound ended once is restarted first, in a fresh
  session with the findings it had written, and its answer is the round's
- **Case**: `run#aPreGateReviewThatIsCutLeavesTheChangeForTheNextRun`,
  `run#aPreGateReviewEndedAtItsBoundNamesIt`

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
