# agent-issue-loop — deltas

## ADDED Requirements

### Requirement: A stale skip asks its own question

A skip whose verdict is `stale` — the item's claim no longer holds in the tree — SHALL be escalated, not parked
on a bare hold: the run SHALL write the decision label and the hold together with one comment carrying the
verdict's reason and the question a person answers, and SHALL NOT close the item, because a stale skip carries
neither the measurement nor the review a falsification closure requires. The label writer SHALL keep no `stale`
verdict of its own: what it can still write as a hold SHALL be a verdict that states no question.

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

#### Scenario: The label writer refuses the verdict the run no longer writes

- **WHEN** the label writer is passed `stale` as a verdict of its own
- **THEN** it stops as a usage error naming the verdict unknown, and writes nothing on the item
- **Case**: `labels#verdictToLabelMatrix`

## MODIFIED Requirements

### Requirement: A state the run writes is readable from the item

Every state label the run writes SHALL be accompanied, on the same item and in the same call, by the one line
that accounts for it — the verdict and the clause of evidence that produced it — because the run's own state is
machine-local and is replaced wholesale by the next run, so a state whose reason lives only there cannot be
answered, re-checked or even understood by the item's reader (#192).

#### Scenario: A hold verdict carries its reason

- **WHEN** a worker skips an item with a hold verdict — `unspecifiable`, `too-wide`, `not-spec-shaped` or
  `needs-target` — and the run puts it on hold
- **THEN** the item carries a comment naming the verdict and the one clause of evidence the worker gave, and no
  decision label is added for it
- **Case**: `labels#holdCarriesItsReason`

#### Scenario: Nothing is written when the reason is missing

- **WHEN** a call that must record a reason and its evidence is given neither
- **THEN** it stops as a usage error before it writes anything, so no item is left in a state nobody can account
  for
- **Case**: `labels#holdCarriesItsReason`

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
- **THEN** only items carrying the decision label are counted, so an item nobody asked a question about cannot
  stop a run
- **Case**: `run#decisionCapRefusesToStart`, `prescreen#aHoldWithNoDecisionIsNotADecision`

### Requirement: A skip that falsifies the premise is closed as mis-diagnosed

A skip SHALL distinguish an item whose premise the run disproved from the items it could not finish: the
former is a finding the run holds the evidence for, and SHALL be closed with that evidence when closure is
allowed; a falsification without a measurement SHALL be escalated as a question, and a skip on a hold verdict
(`unspecifiable`, `too-wide`, `not-spec-shaped`, `needs-target`) SHALL keep the bare hold it writes. A `stale`
skip writes no such hold: it is escalated as the requirement "A stale skip asks its own question" states.

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

#### Scenario: The skips this change does not re-route are unchanged

- **WHEN** an item is skipped because it is too large for one fix, is not spec-shaped, names no target, needs a
  device, or needs diagnosis
- **THEN** each leaves the state it wrote before this change existed — the bare hold with its one comment for
  the hold verdicts, `agent/needs-device` with the hold for the device verdict, and `agent/needs-decision` with
  the hold for `needs-diagnosis` — and none of them is closed
- **Case**: `labels#verdictToLabelMatrix`, `prescreen#deviceItemIsAVerdictNotWork`

#### Scenario: The reviewer reads the claim, not only the diff

- **WHEN** a falsification reaches the review round with an empty diff
- **THEN** the review still happens on that empty diff, and an item whose tree no longer stands where the claim
  said it stood is escalated instead of closed
- **Case**: `run#falsificationWithAChangedTreeIsBlocked`
