# Spec Delta — agent-issue-loop

## ADDED Requirements

### Requirement: The question a block carries is the one the run holds

A block that escalates an item SHALL carry the question the run holds: the reviewer's `LOOP-DECISION` when its report carries one, the worker's otherwise. The run's own two-way choice SHALL be stated only when neither report carries a question.

#### Scenario: A block before any review round carries the worker's question

- **WHEN** a worker's report states a blocked verdict and a `LOOP-DECISION`, and no review round runs on the item
- **THEN** the comment the escalation posts carries that decision — the question, the options and the recommendation — and not the run's own retry/abandon sentence, so an owner answering the queue decides the fork the child found
- **Case**: `run#aBlockedItemCarriesTheWorkersDecision`

#### Scenario: A block that holds no question states its own

- **WHEN** a block escalates an item and neither the reviewer's report nor the worker's carries a `LOOP-DECISION`
- **THEN** the run states the one two-way choice a block leaves — retry the item, or abandon it — because a reason alone is not something a person can answer
- **Case**: `run#blockedItemAsksItsOwnQuestion`

#### Scenario: A review round's question is preferred to the worker's

- **WHEN** a review round ran and its report carries a `LOOP-DECISION` while the worker's report carries another
- **THEN** the escalation quotes the reviewer's, because that round read the diff the block is about, and the worker's is not quoted at all
- **Case**: `run#aReviewersDecisionOutranksTheWorkers`
