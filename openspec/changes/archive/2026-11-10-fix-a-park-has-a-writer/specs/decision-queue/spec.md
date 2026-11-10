# Spec Delta — decision-queue

## ADDED Requirements

### Requirement: A park writes its answer and moves no label

The answer verb SHALL accept a park as an outcome that records the decision through the same writer as every
other answer and performs no transition: no label, no status, no close. The item SHALL therefore keep the
decision label, stay answerable and stay in the queue, and the queue SHALL print `parked: <the decision's first
clause>` where the question stood, because the fork is settled and the condition is what a reader needs now.

#### Scenario: A park writes its answer and moves no label

- **WHEN** the answer verb is called with the park outcome and a decision
- **THEN** the answer is posted as a comment carrying the decision and the marker that binds it to the question
  it answers, the item keeps the decision label and the status it already carries, and no label or status write
  and no close is performed
- **Case**: `answer#parkWritesTheAnswerAndKeepsTheMode`

#### Scenario: A parked item prints the condition it waits for

- **WHEN** the queue is asked for an item whose last statement is such a park
- **THEN** the item is still listed and still counted, and its entry reads `parked: <the decision's first
  clause>` instead of the question the park has settled
- **Case**: `decisions#aParkedItemIsListedAsParked`
