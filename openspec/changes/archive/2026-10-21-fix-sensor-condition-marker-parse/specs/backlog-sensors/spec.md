# backlog-sensors — deltas

## MODIFIED Requirements

### Requirement: A check reports one of three outcomes, and only clearing closes

A check's run SHALL end in exactly one of three outcomes — the condition is present, the condition has
cleared, or the check failed to determine anything — and SHALL NOT close an issue unless the condition has
demonstrably cleared. A clearing finding SHALL carry the identity the item's own body record declares, and
SHALL name the check's own identity only when no item records a condition.

#### Scenario: The condition is present

- **WHEN** a check measures past its bound for the required number of consecutive runs
- **THEN** its issue is created, or the existing one is reopened or annotated, with the measurement

#### Scenario: The condition has cleared

- **WHEN** a check measures inside its bound for the required number of consecutive runs
- **THEN** the measurement is recorded on the issue and the issue is closed, naming the clearing
  measurement as its evidence, and the finding's identity is the one the issue's own condition record
  declares rather than the identity of the measurement that found it green
- **Case**: `sensors#aRecordedConditionParsesToItsObject`

#### Scenario: A row whose condition is simply absent says so

- **WHEN** a row's command finds no condition to report — the build is green, no change of the owner's is in
  conflict
- **THEN** it reports a clear naming its own identity, so "the condition is absent" reaches the tracker's
  history as a verdict rather than as the row's silence

#### Scenario: The check itself failed

- **WHEN** a check crashes, cannot reach its input, or produces an unreadable result
- **THEN** its run reports failure, nothing is written to the tracker, and no issue is closed — a broken
  check must not read as a cleared condition

#### Scenario: Failing is not clearing

- **WHEN** two runs end, one with a failed check and one with a cleared condition
- **THEN** their effects on the tracker differ, and the report distinguishes them

#### Scenario: A red build is not a failed check

- **WHEN** the record published for the commit under test comes from a build that did not complete
  successfully
- **THEN** the rows that read the build's own outputs SHALL NOT be attempted for it, so no such row reports
  the third outcome and the run fails for a reason that belongs to a check rather than to the build
- **Case**: `workflow#aRedRecordRunsNoRecordRow`
