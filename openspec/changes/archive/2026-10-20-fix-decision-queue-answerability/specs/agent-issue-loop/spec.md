# agent-issue-loop — deltas

## MODIFIED Requirements

### Requirement: Closure follows a passing review and carries evidence

An item SHALL be closed only after its review passed and its change was archived, with one line of root
cause and the evidence attached as a comment first; the run SHALL be able to be started so that closure is
left to a person instead. A closure SHALL end the item's life as an open question, so the label that asks for
a decision is taken off in the same pass.

#### Scenario: A passing item is closed with evidence

- **WHEN** the review passed and the change was archived
- **THEN** the item is closed after a comment carrying the root cause and the evidence

#### Scenario: Closure can be left to a person

- **WHEN** the run was started with closure disabled
- **THEN** the item keeps its fixed state, the evidence comment is still posted and the item stays open

#### Scenario: A closure takes the decision label off the item

- **WHEN** an item that carries the decision label is closed, by any path — the run, a person, a check
- **THEN** the closure removes that label in the same write that records the settled state, and an item that
  does not carry it is written exactly as before
- **Case**: `labels#closeRemovesTheDecisionLabel`

#### Scenario: A blocked item is never closed

- **WHEN** an item ended blocked — for any reason the run determined, not only a failed review round
- **THEN** it is not closed, and the reason is written on the item in the same pass, while the run still holds
  it — the decision label, the status that holds it, and one comment naming the reason and the work already
  done, with the evidence directory and the branch as the evidence
- **Case**: `run#blockedItemIsEscalatedWithItsReason`, `run#childClaimWithoutCommitIsBlocked`

## ADDED Requirements

### Requirement: A block asks its own question

An escalation SHALL hold a decision field that a person can answer: when the reviewer wrote no decision, the
run SHALL state the choice the block leaves — retry the item or abandon it — rather than escalating a reason
and calling it a decision.

#### Scenario: A block with no reviewer decision states the choice

- **WHEN** an item is blocked and the reviewer's report carries no `LOOP-DECISION` — the run stopped before a
  review round, or the cause is the run's own child
- **THEN** the escalation's comment carries a `The decision:` field naming both options and the run's reason
  as the ground for either, so the decision queue holds something answerable
- **Case**: `run#blockedItemAsksItsOwnQuestion`

#### Scenario: A reviewer's own decision is not replaced

- **WHEN** the reviewer's report carries a `LOOP-DECISION`
- **THEN** that text is the escalation's decision field, unchanged, and the run states no choice of its own
- **Case**: `run#escalationCarriesTheDecision`

#### Scenario: A block's reason is still on the item

- **WHEN** any block is escalated
- **THEN** the reason stays in the comment beside the decision field, because the state the run keeps is
  replaced wholesale by the next run
- **Case**: `run#blockedItemIsEscalatedWithItsReason`
