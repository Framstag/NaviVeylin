# Spec Delta — agent-issue-loop

## MODIFIED Requirements

### Requirement: A pile of open decisions stops the run

The run SHALL refuse to start when more items awaiting a person's decision are open than the configured
cap allows, and SHALL report them instead of producing more work.

#### Scenario: The cap is reached

- **WHEN** the number of open items awaiting a decision reaches the cap
- **THEN** the run refuses to start and prints those items, so nothing new is produced while decisions
  accumulate
- **Case**: `run#decisionCapRefusesToStart`

#### Scenario: An item needing a decision is escalated, not abandoned

- **WHEN** an item turns out to need a decision while it is being worked
- **THEN** the work already done is preserved and named on the item — the root cause, the failing case and
  the branch — and the item is marked as awaiting a decision, so the work is not thrown away
- **Case**: `run#needsDecisionIsEscalatedWithTheWork`

#### Scenario: The report names the decisions the run itself wrote

- **WHEN** a run ends after escalating an item — a skip whose verdict means a decision, a branch a previous
  attempt left, or a second review round that failed
- **THEN** the report's list of decisions the owner owes names that item, read from the label the run's
  escalation wrote and recorded with the item rather than from the verdict the worker chose, and names no
  item this run did not escalate — so the run that fills the cap is the run whose report shows the cap
  coming
- **Case**: `report#escalatedSkipNamesTheLabelWritten`, `report#failedReviewEscalationIsNamed`, `report#aVerdictAloneIsNoEscalation`
