# agent-issue-loop delta

## ADDED Requirements

### Requirement: A step is measured in the project's environment, not the item session's

The gate SHALL remove from a step's environment the facts the loop hands one child of the item — the result bound and
its arm, the child's session name, its trim record, its repeat ledger, its event stream — and the switch that starts no
measurement of its own, so a step whose command runs the loop's own suite is measured as the project's command and not
as that child. The facts the gate reads itself SHALL stay in the gate's environment.

#### Scenario: A step is handed none of the item session's facts

- **WHEN** the gate runs a step while it was started in the environment a child of the item hands it — the result
  bound and its arm, the session's name, its trim record, its repeat ledger, its event stream, and the switch a
  base-red measurement sets
- **THEN** the step is handed none of them, while the gate's own reading of the facts it keeps is unchanged
- **Case**: `gate#aStepIsNotHandedTheItemsSession`, `gate#aThirdFullRunInOneSessionIsRefused`

#### Scenario: The loop's own suite is measured as the project's command

- **WHEN** a gate started in a child's environment measures the loop's own suite on a tree at the run's base
- **THEN** the case that holds a plain result read from a gate log path is handed all of it, and the case that asserts
  a nested gate measured its step's base state passes — the two names the leg was red for at every base the run
  recorded
- **Case**: `adapter#theResultBoundExemptsGateEvidence`, `gate#theBaseStateIsMeasuredWhenTheGateFirstNeedsIt`
