# agent-issue-loop — deltas

## ADDED Requirements

### Requirement: A hold is not a decision

An item SHALL count as awaiting a decision only when it carries the decision label an escalation writes. The hold
state has several writers — the worker's skip verdicts, the device verdict, the citation repair's park and a
person's own hold — and none of them states a question, so a bare hold SHALL be reported as a hold, SHALL NOT be
escalated, and SHALL NOT count against the decision cap (#193).

#### Scenario: A bare hold is reported for what it is

- **WHEN** the pre-screen meets an eligible item on hold that carries no decision label
- **THEN** it records the item as held, writes no label and posts no escalation, and the report prints it as held
- **Case**: `prescreen#aHoldWithNoDecisionIsNotADecision`, `run#awaitingDecisionIsNotEscalatedAgain`

#### Scenario: The cap counts the questions a person can answer

- **WHEN** the run counts the open decisions before it starts
- **THEN** only items carrying the decision label are counted, so an item nobody asked a question about cannot
  stop a run
- **Case**: `run#decisionCapRefusesToStart`, `prescreen#aHoldWithNoDecisionIsNotADecision`

