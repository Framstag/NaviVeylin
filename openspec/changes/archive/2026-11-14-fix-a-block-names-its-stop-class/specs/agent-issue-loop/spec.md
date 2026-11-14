# agent-issue-loop delta

## MODIFIED Requirements

### Requirement: A block records the mechanism that stopped it

An item recorded `blocked` SHALL carry the class of the arm that stopped it, from a closed vocabulary: `refused` when
a review round refused the change — the round before the gate, the last round at the cap, or a red gate a close records
no waiver over — `unmergeable` when the base would not take the branch, `bound` when the run ended a child at a bound
it enforces, `child-blocked` when the child's own answer is a block, `child-failed` when a child of the item exited
short of a bound or a phase of it answered nothing, `outside-write` when a child changed the project's own tree,
`reviewer-wrote` when a round was discarded because the item's own tree changed under its reviewer, `unanswered` when
a round's answer arrived cut twice, `uncommitted` when the report's claim is not what the tree shows, `unverified`
when the gate result the report claims is not in its tally or a cited result carries no evidence beside it, and
`contradicted` when a falsification's unchanged tree had moved by the end of its review. The arms a run records SHALL
each name one, and the result, the class and the reason SHALL be written in one place, so no arm blocks without a
class and the class cannot drift from the result it belongs to. `bound` is also the one class an item recorded `cut`
carries, because the same bound is the phase the next run resumes.

The vocabulary SHALL carry one second home: `unmergeable` SHALL be written beside the pre-screen's exclusion of an
item whose admissible finished branch the project tree, the base or the replay refused to merge, with the refusal's
own text, and no other class SHALL be written on a result that is not a block. A value outside the vocabulary, and one
on any other result than a block, that exclusion or a `cut` carrying `bound`, SHALL be refused before anything is
written, so a class's spend is totalled without the note's prose. An empty value SHALL stay accepted, because a state a
tree wrote before the field existed and a caller that is not the run are records the writer does not rewrite. An arm
that records a cut for the item's own worker, its gate child, its tail or the rework worker of a refusing round SHALL
leave `bound` beside the result when a bound the run enforces ended that child, so no arm of one rule turns the run's
own bound into a question its owner owes an answer.

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
  not one exclusion of this run and leaves the state unwritten — so the class's second home is the exclusion
  and not a free field
- **Case**: `state#anAdoptionRefusalIsRecordedBesideTheExclusion`

#### Scenario: A block the run's own bound caused is named as one

- **WHEN** a state a tree wrote before this change records an item `blocked` with `bound` as its stop reason
- **THEN** the item keeps `bound` beside its `blocked` result, and `state.sh next` keeps the pair out of the streak
  it stops on, because the class says the run's own bound ended the phase — the same fact this run records
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

- **WHEN** a stop reason is given that is not one of the vocabulary's classes, or `refused` or `unmergeable` is
  given for a result that is not a block, or `bound` is given for a result that is neither a block nor a cut
- **THEN** the record is refused as a usage error naming the classes it accepts, and the state is not written, so the
  field cannot turn into a second prose channel
- **Case**: `state#aBlockedItemNamesTheMechanismThatStoppedIt`

#### Scenario: An arm's block names the class that stopped it

- **WHEN** an arm of the run's outcome block stops an item short of a closure — here a change child whose answer claims
  the change while the tree is still at the run's base — and the item is recorded blocked
- **THEN** the item's record carries that arm's class — `uncommitted` here — beside the reason the arm already wrote,
  written with the result in one place, so no blocked item the run records carries a null mechanism
- **Case**: `run#anEmptyTreeIsRefusedBeforeTheGateChild`

#### Scenario: The report tables the mechanism beside the spend

- **WHEN** the run's report prints its per-item table
- **THEN** each row carries the class its block stopped on, or `-` when it carries none, in a column of its
  own, so a class's spend is totalled from the table
- **Case**: `report#wallTimeAndReasonInTheTable`

