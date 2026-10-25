# Deltas — `backlog-sensors`

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
  history as a verdict rather than as the row's silence: a clear whose identity no open condition issue
  carries is recorded on the row's own record issue, under the row's label and the state the registry declares
  for records, and never closed by the lifecycle — so the report reads the row as one that ran and was green
  rather than as one that never ran
- **Case**: `run#aClearWithNoIssueReachesTheRecord`

#### Scenario: The report tells a green row from one that never ran

- **WHEN** the rows are reported and one row's measurements are on its record issue while another row has no
  measurement anywhere
- **THEN** the first prints its newest measurement with its verdict and is not flagged as stale, and the
  second is the one reported as never measured, so the two are distinguishable
- **Case**: `report#aGreenRowIsNotNever`

#### Scenario: A row's record is not a condition

- **WHEN** a check reads the conditions its issues record, so that it can report a cleared condition under the
  identity that issue holds
- **THEN** the row's own record issue is not among them, because its marker is not a condition's fingerprint
  and the lifecycle never reopens or closes it
- **Case**: `lib#theRecordIsNotACondition`

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
