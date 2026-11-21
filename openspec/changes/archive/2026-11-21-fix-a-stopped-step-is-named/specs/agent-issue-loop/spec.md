# agent-issue-loop delta

## ADDED Requirements

### Requirement: A step that is stopped is named while it is stopped

While a step is neither finished nor overrun, a gate SHALL read the state of the process group it started, at most
once a second and in one reading for the whole wave, and SHALL name a step whose group carries a member that is not
running in one console line, written to the gate's log as well, naming the command, the state it read and the bound
that will end it. That line SHALL appear once per step per wave, and the bound SHALL remain the only thing that ends
a step.

#### Scenario: A stopped step is named before its bound, and the bound still ends it

- **WHEN** a step's process group carries a member whose state is `T`, `t` or `Z` rather than a state that is making
  progress, while the gate is still inside that step
- **THEN** the gate prints one line naming the command, the state it read and the bound, and writes it to its log as
  well, exactly once for that step in that wave, before the bound is reached — and the bound still ends the step with
  everything it started, so the step's numbers and the gate's verdict are what they were and no second authority
  ends a step
- **Case**: `gate#aStoppedStepIsNamedBeforeItsBound`
