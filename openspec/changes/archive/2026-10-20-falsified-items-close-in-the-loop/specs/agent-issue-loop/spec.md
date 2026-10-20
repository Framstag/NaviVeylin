# agent-issue-loop — deltas

## MODIFIED Requirements

### Requirement: Closure follows a passing review and carries evidence

An item SHALL be closed only after its review passed and its change was archived — or, when the review
confirmed that the item's premise does not hold on an unchanged tree, after that confirmation — with one line
of root cause and the evidence attached as a comment first; the run SHALL be able to be started so that closure
is left to a person instead. A review that confirms the item's premise does not hold SHALL close it on the same
terms — reviewed, with its measurement as the evidence, and only when closure is allowed.

#### Scenario: A passing item is closed with evidence

- **WHEN** the review passed and the change was archived
- **THEN** the item is closed after a comment carrying the root cause and the evidence

#### Scenario: Closure can be left to a person

- **WHEN** the run was started with closure disabled
- **THEN** the item keeps its fixed state, the evidence comment is still posted and the item stays open

#### Scenario: A reviewed falsification is closed on the same terms

- **WHEN** the worker answered that the item's premise does not hold, the tree is unchanged, the measurement
  is recorded with how it was taken, and the review did not make it blocking
- **THEN** the item is closed with the contradicted mechanism as the reason and the measurement as the
  evidence, and the run's state records the item with its own result so the report can count it apart from a
  fix and from a skip
- **Case**: `run#falsifiedSkipClosesWithItsMeasurement`

#### Scenario: A closure takes the decision label off the item

- **WHEN** an item that carries the decision label is closed, by any path — the run, a person, a check
- **THEN** the closure removes that label in the same write that records the settled state, and an item that
  does not carry it is written exactly as before
- **Case**: `labels#closeRemovesTheDecisionLabel`

#### Scenario: Closure disabled keeps a falsification open

- **WHEN** the run was started with closure disabled and the item's falsification was reviewed
- **THEN** the evidence comment is posted, the item stays open, and no decision label is written — the run
  does not reopen the question it already answered
- **Case**: `run#mayCloseNoKeepsAFalsificationOpen`

#### Scenario: A blocked item is never closed

- **WHEN** an item ended blocked — for any reason the run determined, not only a failed review round
- **THEN** it is not closed, and the reason is written on the item in the same pass, while the run still holds
  it — the decision label, the status that holds it, and one comment naming the reason and the work already
  done, with the evidence directory and the branch as the evidence
- **Case**: `run#blockedItemIsEscalatedWithItsReason`, `run#childClaimWithoutCommitIsBlocked`

## ADDED Requirements

### Requirement: A skip that falsifies the premise is closed as mis-diagnosed

A skip SHALL distinguish an item whose premise the run disproved from the items it could not finish: the
former is a finding the run holds the evidence for, and SHALL be closed with that evidence when closure is
allowed; a falsification without a measurement, and every other skip, SHALL keep the existing escalation.

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
