# Spec Delta — agent-issue-loop

## ADDED Requirements

### Requirement: An item the run's own bound cut is recorded `cut`, not `blocked`

An item whose review round the run ended at a bound it enforces — the run's deadline or the child's role's turn
bound — SHALL be recorded `cut`: no decision label, no entry in the two-blocked streak `state.sh next` stops on,
and a line of its own in the report. The round SHALL be written on the branch as a cut with its announcement
withdrawn, so the attempt stays work the loop finishes rather than a question only its owner can answer.

#### Scenario: A round the turn bound ended leaves no decision behind

- **WHEN** an item's tail writes `LOOP-VERDICT: closed`, its gate answered and its change is archived, and the run
  then ends review round 1 at the reviewer's role's turn bound
- **THEN** the item is recorded `cut` with its round marked cut, no `agent/needs-decision` label is written for it
  and the report's decision list does not hold it, the branch carries `review-round-1-cut.md` and no
  `review-round-1-requested.md`, and `branch-state` reads `adoptable` — work the next run finishes, not a question
  only its owner can answer
- **Case**: `run#aReviewerEndedAtItsTurnBoundIsNotARefusal`

#### Scenario: Two consecutive cuts do not stop the run

- **WHEN** two items in a row are recorded `cut` and the queue still holds a candidate
- **THEN** `state.sh next` hands out that candidate, a block that follows a cut is the first block of its own pair,
  and two blocks in a row still stop the run
- **Case**: `state#nextHonoursEveryStopCondition`

## MODIFIED Requirements

### Requirement: A child the run ended itself leaves no commit

The run SHALL commit an item's tree only after the child that produced it has finished by its own exit. A child
the run ended — the wall-clock cap's status, or the `LOOP-TIMEOUT` line its transcript carries — SHALL leave the
branch as it stood: the item is recorded blocked with that transcript, and the tree is kept exactly as the child
left it, because nothing is discarded unrecorded: `--reset-worktree` discards it, and so does this run before it
starts its next item, the patch having been written first. It holds for the rework worker a refusing review
round starts as well. The tree that child left SHALL be readable without the tree itself: before the run moves to
its next item, the changes it left — the tracked diff and each untracked file — SHALL be written to
`<item dir>/leftover-from-killed-run.patch`, and the transcript the ended child leaves SHALL carry a
`LOOP-LEFTOVERS:` line naming that file. The item's own state record SHALL name that file as well, so the report
can point a caller at the work an ended child left (issue #674).

An item whose child was ended by the **run's own deadline** SHALL NOT be escalated as a decision about the item:
the clock that cut it is the run's budget, not a finding, so no decision label is written for it and the item is
left for the next run. That holds for every child an item starts — its worker, the rework worker a refusing round
starts, and the reviewer of a review round — and the run SHALL take no further pass over the queue once the
deadline has ended one, because a pass that admitted work the spent budget cannot pay would name a decision about
the item the clock cut. A child the item's own wall-clock cap ended is unchanged and is escalated. An item whose
**review round** a bound ended — the deadline, or the role's turn bound — SHALL be recorded `cut` rather than
`blocked`, and the run SHALL take no further pass over the queue after it either, for the same reason.

#### Scenario: A worker the cap ends leaves the branch at the base

- **WHEN** the wall-clock cap ends an item's worker and that worker has left changes in the change root
- **THEN** the run commits none of them — the change root's `HEAD` is still the base commit — the item is recorded
  `blocked` with its `LOOP-TIMEOUT` transcript, the tree still carries what the child left (kept, not discarded),
  and the run's own line says the commit was skipped
- **Case**: `run#treeOfAChildTheCapEndedIsNotCommitted`

#### Scenario: The cap ends the rework worker of a refusing review round

- **WHEN** a review round refuses the change and the wall-clock cap ends the rework worker that round starts, after
  that worker has written into the change root
- **THEN** the run commits none of it — the branch carries only what the item's own worker had already earned — the
  item is recorded `blocked` with the round's `LOOP-TIMEOUT` transcript, the tree still carries what that worker
  left, and the blocked reason names the tree it did not commit
- **Case**: `run#treeOfACapEndedReworkOfARefusingRoundIsNotCommitted`

#### Scenario: The changes a cap-ended child left are kept as a patch

- **WHEN** the wall-clock cap ends an item's worker that has left modifications and an untracked file in the tree
- **THEN** `<item dir>/leftover-from-killed-run.patch` exists, carries the worker's modifications (staged and
  unstaged, against `HEAD`) and that untracked file's content, the child's transcript carries a
  `LOOP-LEFTOVERS:` line naming it, the item's state record names it, and the report's action list names the
  recovery — so the work is readable after `--reset-worktree` discards the tree
- **Case**: `run#treeOfACapEndedChildIsKeptAsAPatch`

#### Scenario: The item the run's own deadline cut is not a decision

- **WHEN** the run's deadline passes while an item's child is running, and the run ends that child
- **THEN** the item is recorded `blocked` with the `LOOP-TIMEOUT`/`LOOP-DEADLINE` lines of its transcript and its
  tree uncommitted, no decision label is written for it, and the report's decision list does not hold it — the
  item is left for the next run
- **Case**: `run#childIsEndedAtTheRunsDeadline`

#### Scenario: A reviewer the run's deadline cut is not a refusal

The deadline is consulted while a child runs, and a reviewer is a child: its transcript carried the same
`LOOP-DEADLINE` line as the worker's, and no part of the run read it — so a round the clock ended was read as one
the reviewer never answered, which bought a rework worker for a change nobody refused (the cut-short retry could not fire: `run_child` writes `LOOP-TIMEOUT` beside `LOOP-DEADLINE`, and the retry condition read that line), and the
item was escalated with the other blocks.

- **WHEN** the run's deadline passes while the reviewer of a review round is running, and the run ends that child
- **THEN** the round is recorded as cut rather than refused — the transcript's `LOOP-DEADLINE` line is read, no
  retry and no rework worker is started for it, and the item is recorded `cut` with no decision label and no
  entry in the report's decision list, exactly as the item whose worker the same clock ended
- **Case**: `run#reviewerEndedAtTheRunsDeadlineIsNotADecision`

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
- **THEN** the item is recorded `cut` when a bound the run enforces ended that round and `blocked` when the
  transport did, with that round named as the one before the gate, no gate child is started on a diff nobody read,
  and the round after the gate is not asked either
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

### Requirement: A child is bounded by its own role's turns

Each child SHALL be ended when the turns **it** has taken reach the bound its role records, and the item's own
turn total SHALL NOT end a child. The bound SHALL be read through the child's role, counted from the turns the
child added to the item's event stream, and named in the transcript with the role and the key — because one count
over the stream every child appends to decides which phase is discarded, not whether the item is converging.

#### Scenario: A phase's spend does not end the phase after it

- **WHEN** the worker takes ten turns, more than the item's old total but below its own role's bound, and the
  round after it takes the eight turns its role records
- **THEN** the worker's transcript carries no `LOOP-TIMEOUT`, the reviewer is ended at its own bound of eight turns
  with a line naming `LOOP_CHILD_TURNS_MAX_REVIEWER` and its role, and the item is recorded `cut` with that
  round marked cut
- **Case**: `run#eachChildIsBoundedByItsOwnRolesTurns`

#### Scenario: A role whose key is `0` runs unbounded, and the item's total is still reported

- **WHEN** a role's key records `0` and its child takes three turns
- **THEN** no turn bound ends it, while the item's total is still counted over its children (`metrics.turns`) and
  printed by the report's `calls:` line beside the calls it was made of
- **Case**: `run#aRoleWithNoRecordedTurnBoundRunsUnbounded`
