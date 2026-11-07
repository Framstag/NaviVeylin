# Spec Delta — agent-issue-loop

## MODIFIED Requirements

### Requirement: A block records the mechanism that stopped it

An item recorded `blocked` SHALL carry the mechanism that stopped it, from a closed vocabulary — `refused` when a
review round refused the change, `unmergeable` when the base would not take the branch, `bound` when the run ended a
child at a bound it enforces — and a block none caused SHALL carry none. `bound` is also the one mechanism an item
recorded `cut` carries, because the same bound is the phase the next run resumes. The vocabulary SHALL carry one
second home: `unmergeable` SHALL be written beside the pre-screen's exclusion of an item whose admissible finished
branch the project tree, the base or the replay refused to merge, with the refusal's own text, and no other mechanism
SHALL be written on a result that is not a block. A value outside the vocabulary, and one on any other result than a
block, that exclusion or a `cut` carrying `bound`, SHALL be refused before anything is written, so a mechanism's
spend is totalled without the note's prose. **Every** arm that records a cut leaves `bound` beside the result when a
bound the run enforces ended the child that arm was reading — the item's own worker, its gate child, its tail, and
the rework worker of a refusing round — so no arm of one rule turns the run's own bound into a question its owner
owes an answer.

#### Scenario: The refusing round is named

- **WHEN** the last review round at the cap refuses the change and the item is recorded blocked
- **THEN** the item's record carries `refused` as its stop reason, beside the note that names the round
- **Case**: `run#reviewRoundsCapAtTwo`

#### Scenario: The base's refusal is named

- **WHEN** the run cannot merge the item's branch onto the base and the item is recorded blocked
- **THEN** the item's record carries `unmergeable`, beside the note that names the branch
- **Case**: `run#doesNotCloseOnAMergeTheBaseCannotTake`

#### Scenario: The tree's refusal of an adoption is named on the exclusion

- **WHEN** the run records an adoption's refusal on an item the pre-screen excluded, and is asked to record one
  for an issue that is not one of the run's exclusions
- **THEN** the exclusion carries `unmergeable` beside the refusal's own text while its verdict, its reason and the
  branch stay as the pre-screen read them, and the second request exits with the refusal naming that the issue is
  not one exclusion of this run and leaves the state unwritten — so the mechanism's second home is the exclusion
  and not a free field
- **Case**: `state#anAdoptionRefusalIsRecordedBesideTheExclusion`

#### Scenario: A block the run's own bound caused is named as one

- **WHEN** a state a tree wrote before this change records an item `blocked` with `bound` as its stop reason
- **THEN** the item keeps `bound` beside its `blocked` result, and `state.sh next` keeps the pair out of the streak
  it stops on, because the mechanism says the run's own bound ended the phase — the same fact this run records
  with `cut`
- **Case**: `state#aBlockAtTheRunsBoundIsNoRefusal`

#### Scenario: A bound the run ended a phase with is named beside the result

- **WHEN** a child of the item reaches its role's turn bound, outlives the child clock or is ended by the run's
  deadline, and the run ends it
- **THEN** the item is recorded `cut` with `bound` as its stop reason, its note says the attempt is left for the next
  run rather than asked about, and no decision label is written for it — the item is work the next run resumes, so it
  spends no decision slot and stays eligible
- **Case**: `run#childPastItsTurnBoundIsCut`

#### Scenario: A rework worker the run's bound ended spends no decision slot

- **WHEN** a review round refuses the change, the run starts the rework worker of that round, and that worker
  reaches its role's turn bound or outlives the child clock so the run ends it
- **THEN** the item is recorded `cut` with `bound` as its stop reason and nothing else is written for it: no
  `agent/needs-decision` label, no `status/on-hold`, no comment, and no line in the report's decisions the owner
  owes, so the decision cap's population does not grow and the next run is not refused by it
- **Case**: `run#treeOfACapEndedReworkOfARefusingRoundIsNotCommitted`

#### Scenario: A mechanism outside the vocabulary is refused

- **WHEN** a stop reason is given that is neither `refused` nor `unmergeable` nor `bound`, or `refused` or
  `unmergeable` is given for a result that is not a block, or `bound` is given for a result that is neither a block
  nor a cut
- **THEN** the record is refused as a usage error and the state is not written, so the field cannot turn into a
  second prose channel
- **Case**: `state#aBlockedItemNamesTheMechanismThatStoppedIt`

#### Scenario: The report tables the mechanism beside the spend

- **WHEN** the run's report prints its per-item table
- **THEN** each row carries the mechanism its block stopped on, or `-` when it carries none, in a column of its
  own, so a mechanism's spend is totalled from the table
- **Case**: `report#wallTimeAndReasonInTheTable`

### Requirement: A review round is a record on the branch, from its announcement to its answer

A review round SHALL be readable from the item's branch in both directions: the round SHALL be recorded on the
branch before its reviewer starts, and its answer SHALL be recorded when the answer arrives, so that a round the
reviewer never answered is a round a later run can see is unanswered rather than one that reads like a pass. A
refusal the run's own bound then cut the rework of SHALL be recorded as that: the announcement stays, and beside it
`review-round-<n>-rework-cut.md` says the round was asked and answered and what did not finish is the answer to the
refusal — neither `cut` (the run ended the round, not its rework) nor silence (which reads as a question) describes
it.

#### Scenario: The rework of a refusal the bound ended is recorded beside the round

- **WHEN** the reviewer refuses the change in round `<n>` and the run starts the rework worker of that round
- **AND** the run ends that worker at a bound it enforces
- **THEN** the branch carries the round's announcement beside `evidence/review-round-<n>-rework-cut.md`, whose text
  says the round was asked and answered and the rework cut, and the round reads as neither a pass nor a silence
- **Case**: `run#treeOfACapEndedReworkOfARefusingRoundIsNotCommitted`

#### Scenario: The round is announced before the reviewer runs, and its answer is recorded when it arrives

- **WHEN** a review round of an archived change ends `PASS`
- **THEN** the item's branch carries the round's announcement, written as a commit before the reviewer started, and
  the pass as a second commit written when the answer arrived — both inside the change's own evidence directory —
  so a stranger to the run reads the round's whole life from the branch and not from a machine-local directory
- **Case**: `run#announcesAndRecordsTheReviewRound`

#### Scenario: A branch whose last review round never answered is not adopted

- **WHEN** an item's branch carries one archived change of its own with a green verification record, and the
  highest review round the branch names was announced and holds no answer — neither a pass nor a refusal
- **THEN** the branch is refused, named and escalated as the owner's decision exactly as a refused review is, and
  the refusal names the round that stayed silent and the announcement it reads, so a change no reviewer graded is
  never merged and its item never closed on it
- **Case**: `worktree#refusesABranchWhoseReviewRoundNeverAnswered`

### Requirement: A blocked attempt's branch is kept under a name of the run's own, and its item is worked

An item whose branch carries commits the base does not have and adds no archived change of its own SHALL not be
recorded as awaiting a decision; nor SHALL one whose branch adds an archived change whose last round was answered by
a refusal and whose rework the run's own bound ended, because nothing on that branch states a question and no
reviewer graded the phase the bound cut. Its branch SHALL be renamed outside the `fix/issue-<N>-` prefix, so its
commits stay reachable and no later run reads it as an attempt, and the item SHALL be worked on a fresh branch. A
branch the run cannot classify SHALL be the owner's, never work; and an item that carries a stated question of its
own remains its owner's whatever its branch holds.

#### Scenario: The rework-cut branch is queued as work

- **WHEN** an eligible item's branch carries an archived change, a green verification, and a round whose rework the
  run's own bound ended, and the item awaits no decision of its own
- **THEN** the pre-screen's verdict is not a decision, the item is queued as work with the branch as its evidence,
  and `branch-state` reads `rework-cut` rather than the refusal that round recorded
- **Case**: `prescreen#aReworkCutBranchIsWork`

#### Scenario: The branch whose rework the bound ended is set aside and its item worked

- **WHEN** an item starts and its branch carries an archived change whose last round was refused and whose rework
  the run's own bound ended
- **THEN** the branch is renamed under the run's leftover prefix with its commits still reachable there, and the
  item is worked on a fresh branch from the run's base with the set-aside printed instead of a refusal
- **Case**: `worktree#setsAsideAReworkCutBranchAndStartsFresh`

#### Scenario: The pre-screen queues the item and names the branch it will set aside

- **WHEN** an eligible item's branch carries commits the base does not have and adds no archived change of its
own — a change another item's commit added does not count
- **THEN** the verdict is not a decision, the item is queued as work, and the run's record names the branch as
  the evidence and the set-aside as the action
- **Case**: `prescreen#aBlockedAttemptBranchIsWork`

#### Scenario: The start keeps the commits and works the item

- **WHEN** an item starts and its branch is in that state, whether the branch was there before the run or
  appeared while the run was open
- **THEN** the branch is renamed under the run's leftover prefix, its commit is still reachable there, the item
  is worked on a fresh branch from the run's base, and the run prints the rename instead of refusing
- **Case**: `worktree#setsAsideABlockedAttemptAndStartsFresh`,
  `run#branchThatAppearsWhileTheRunIsOpenIsSetAside`
