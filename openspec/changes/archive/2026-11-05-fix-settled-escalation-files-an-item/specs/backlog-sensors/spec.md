# backlog-sensors — deltas

## MODIFIED Requirements

### Requirement: A repeating condition escalates instead of reopening forever

A check SHALL stop reopening past a declared number of recurrences and SHALL mark the issue as awaiting a
person's decision, because a condition that keeps returning is a specification or design problem. When the
issue that carries the condition cannot be marked — the writer refuses to displace a state the profile
declares settled, which is where a returned condition lands when it was closed as fixed once — the check SHALL
file the returned condition as a new item, under the same bound on the items one run of it may file, and SHALL
NOT report the escalation as written (issue #780). When that bound leaves no room for the filing, the check
SHALL fail the run rather than report an escalation or a filing the writer did not make.

#### Scenario: The recurrence bound is reached

- **WHEN** a condition recurs past the declared bound
- **THEN** the check stops reopening and marks the issue as awaiting a decision, recording why
- **Case**: `run#escalatesPastKRecurrences`

#### Scenario: Escalating does not authorise work

- **WHEN** an issue is escalated
- **THEN** it does not become eligible for automated work, so a repeating condition reaches a person
  rather than a machine
- **Case**: `run#escalationNeverSetsEligibility`

#### Scenario: A flapping condition does not churn

- **WHEN** a condition alternates between present and cleared faster than the consecutive count requires
- **THEN** neither a close nor a reopen happens until the count is met in one direction
- **Case**: `run#hysteresisBothDirections`

#### Scenario: A settled issue's escalation files the returned condition

- **WHEN** a condition recurs past the bound on an issue the profile declares settled, so the writer refuses
  the escalation's state write
- **THEN** the check files a new item carrying the condition's own fingerprint, reports the item it filed
  rather than an escalation, and leaves the settled issue untouched
- **Case**: `run#anEscalationOnASettledIssueFilesANewItem`

#### Scenario: A refused escalation past the row's bound fails the run

- **WHEN** a condition recurs past the bound on an issue the profile declares settled, the writer refuses the
  escalation's state write, and the row's bound on the items one run may file is already spent
- **THEN** the check files nothing, reports the condition as unrecorded, and fails the run so the tick is red
  instead of reporting an escalation or a filing that did not happen
- **Case**: `run#aRefusedEscalationPastTheBoundFailsTheRun`
