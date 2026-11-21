## MODIFIED Requirements

### Requirement: The diff is read before the gate, and a fresh child runs the one gate

An item's gate SHALL be run by a child of its own, after the diff has been read by a reviewer that is not the child
that wrote it, so a finding a reader can make from the diff is not paid for by a gate and a second full pass — except
when the input cap removed more than half of the whole input the round would read, where that round SHALL NOT be
launched, the item SHALL record the share that decided it, and the gate and the item's first round SHALL run either
way.

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

- **WHEN** the round before the gate never answers because a bound the run enforces ended it — the run's deadline, or
  its role's turn bound or the item's context bound both before **and** after the one restart this run gives it
- **THEN** the item is recorded `cut` with that round named as the one before the gate, no gate child is started on a
  diff nobody read, and the round after the gate is not asked either — while a round the bound ended once is
  restarted first, in a fresh session with the findings it had written, and its answer is the round's
- **Case**: `run#aPreGateReviewEndedAtItsBoundNamesIt`

#### Scenario: A round the transport cut twice is unanswered, and the gate child decides

- **WHEN** the round before the gate answers nothing at all — its report arrives without the closing `VERDICT:` line
  twice, and no bound the run enforces ended either attempt (as two reviewer children of the run of
  2026-11-17T10:58Z did, answering the run with a question)
- **THEN** the round is recorded `unanswered` — neither a pass nor a refusal, and not the `cut-short` a bound leaves —
  its count is recorded, the step on the item says the round answered nothing, the gate child is started on the diff
  it never read and told the round handed nothing forward, and the item's outcome becomes the gate child's, which is
  the item's only verdict: no reason names a reviewer that refused a change nobody read, and no round after the gate
  is asked on an item the gate blocked
- **Case**: `run#aPreGateReviewCutTwiceIsUnansweredAndTheGateDecides`

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

#### Scenario: A halved diff launches no round

- **WHEN** `LOOP_REVIEW_INPUT_MAX_BYTES` removed more than half of the whole input the round would read
  (`review_input_cut_scale`, the share over every bounded part)
- **THEN** no pre-gate child is launched, the item records the share and the skip, and the gate and round 1 still run
- **Case**: `run#aPreGateRoundItsCapHalvedIsSkipped`

#### Scenario: A small cut still launches the round

- **WHEN** the cap removed less than half of that whole input (5 % is the share `#963`'s record carries)
- **THEN** the pre-gate round runs, and its verdict is the one the item records
- **Case**: `run#aPreGateRoundUnderHalfItsCapStillRuns`
