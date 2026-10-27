# agent-issue-loop — deltas

## ADDED Requirements

### Requirement: A stale skip asks its own question

A skip whose verdict is `stale` — the item's claim no longer holds in the tree — SHALL be escalated, not parked
on a bare hold: the run SHALL write the decision label and the hold together with one comment carrying the
verdict's reason and the question a person answers, and SHALL NOT close the item, because a stale skip carries
neither the measurement nor the review a falsification closure requires.

#### Scenario: A stale skip leaves a question, not a bare hold

- **WHEN** a worker skips an item with the sub-verdict `stale` and the run records that verdict
- **THEN** the item carries the decision label and the hold, its one comment names the worker's reason and the
  choice between retiring it as stale and re-diagnosing it — the worker's own question when it stated one, the
  run's otherwise — and nothing is closed
- **Case**: `run#staleSkipAsksItsQuestion`

#### Scenario: The worker's own question is carried unchanged

- **WHEN** a stale skip's report carries a `LOOP-DECISION`
- **THEN** that text is the escalation's decision field, unchanged, and the run states no choice of its own
- **Case**: `run#staleSkipKeepsTheWorkersQuestion`

## MODIFIED Requirements

### Requirement: A skip that falsifies the premise is closed as mis-diagnosed

A skip SHALL distinguish an item whose premise the run disproved from the items it could not finish: the
former is a finding the run holds the evidence for, and SHALL be closed with that evidence when closure is
allowed; a falsification without a measurement SHALL be escalated as a question, and every other skip SHALL
keep the state it writes today.

#### Scenario: A measured falsification closes the item

- **WHEN** the worker reports that the mechanism the item blames does not hold, names that mechanism, records
  the number it measured instead together with how the measurement was taken, and leaves the tree unchanged
- **THEN** the item is closed as mis-diagnosed with that measurement as its evidence, its comment names the
  contradicted mechanism, and the run does not escalate it
- **Case**: `run#falsifiedSkipClosesWithItsMeasurement`

#### Scenario: A falsification without a measurement is a question, not a finding

- **WHEN** the worker reports a falsification but leaves the measured field empty
- **THEN** the item is escalated exactly as before — the decision label, the hold, the reason on the item —
  and nothing is closed
- **Case**: `run#falsificationWithoutAMeasurementIsEscalated`

#### Scenario: The claim is read again after the review

- **WHEN** a falsification reaches its closure and the tree no longer stands where the claim said it stood —
  a corrective review round's worker committed
- **THEN** the item is blocked instead of closed, the note says the tree moved, and the reason is written on
  the item
- **Case**: `run#falsificationWithAChangedTreeIsBlocked`

#### Scenario: The four question-shaped skips are unchanged

- **WHEN** an item is skipped because it is too large for one fix, is spec-shaped, needs a device, or needs
  diagnosis
- **THEN** it is held or escalated exactly as before this requirement existed, with its own verdict
- **Case**: `run#aSkipClearsTheInProgressMark`, `prescreen#deviceItemIsAVerdictNotWork`

#### Scenario: The reviewer reads the claim, not only the diff

- **WHEN** a falsification reaches the review round with an empty diff
- **THEN** the review still happens, and a measurement that does not cover the blamed mechanism is a
  blocking finding, which returns the item to the escalation path instead of closing it
- **Case**: `run#falsificationWithAChangedTreeIsBlocked`
