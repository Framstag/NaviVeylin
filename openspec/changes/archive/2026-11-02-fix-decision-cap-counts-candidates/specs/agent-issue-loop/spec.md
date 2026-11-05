## MODIFIED Requirements

### Requirement: A pile of open decisions stops the run

The run SHALL refuse to start when more candidate items awaiting a person's decision are open than the configured
cap allows, and SHALL report them instead of producing more work. An item is a candidate only when it carries
both the decision label and the loop's opt-in, so a decision parked by removing `agent/eligible` SHALL NOT count
(#745).

#### Scenario: The cap is reached

- **WHEN** the number of candidate items awaiting a decision reaches the cap
- **THEN** the run refuses to start, prints those items and states the count it compared, so nothing new is
  produced while decisions accumulate
- **Case**: `run#decisionCapRefusesToStart`, `run#decisionCapCountsOnlyCandidates`

#### Scenario: An item needing a decision is escalated, not abandoned

- **WHEN** an item turns out to need a decision while it is being worked
- **THEN** the work already done is preserved and named on the item — the root cause, the failing case and
  the branch — and the item is marked as awaiting a decision, so the work is not thrown away
- **Case**: `run#needsDecisionIsEscalatedWithTheWork`

#### Scenario: The report names the decisions the run itself wrote

- **WHEN** a run ends after escalating an item — a skip whose verdict means a decision, a branch a previous
  attempt left, an item that already awaited a decision, or a review round that failed
- **THEN** the report's list of decisions the owner owes names that item, read from the label the run's
  escalation wrote and recorded with the item rather than from the verdict the worker chose, and names no
  item this run did not escalate — so the run that fills the cap is the run whose report shows the cap
  coming
- **Case**: `report#escalatedSkipNamesTheLabelWritten`, `report#failedReviewEscalationIsNamed`, `report#aVerdictAloneIsNoEscalation`

### Requirement: A hold is not a decision

An item SHALL count as awaiting a decision only when it carries the decision label an escalation writes. The hold
state has several writers — the worker's hold verdicts (`unspecifiable`, `too-wide`, `not-spec-shaped`,
`needs-target`), the device verdict, the citation repair's park and a person's own hold — and none of them states
a question, so a bare hold SHALL be reported as a hold, SHALL NOT be escalated, and SHALL NOT count against the
decision cap (#193). A `stale` skip SHALL write no bare hold: that verdict leaves a question, so the escalation
writes the decision label beside the hold, as the requirement "A stale skip asks its own question" states.

#### Scenario: A bare hold is reported for what it is

- **WHEN** the pre-screen meets an eligible item on hold that carries no decision label
- **THEN** it records the item as held, writes no label and posts no escalation, and the report prints it as held
- **Case**: `prescreen#aHoldWithNoDecisionIsNotADecision`, `run#awaitingDecisionIsNotEscalatedAgain`

#### Scenario: The cap counts the questions a person can answer

- **WHEN** the run counts the open decisions before it starts
- **THEN** only items that carry the decision label **and** the loop's opt-in are counted, so an item nobody
  asked a question about — and an item a person parked by removing `agent/eligible` — cannot stop a run, while
  the refusal still states how many items carry the label beside the count it compared
- **Case**: `run#decisionCapCountsOnlyCandidates`, `run#decisionCapRefusesToStart`,
  `prescreen#aHoldWithNoDecisionIsNotADecision`
