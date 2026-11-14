# agent-issue-loop delta

## MODIFIED Requirements

### Requirement: An item's record keeps what the run measured to decide it

An item's record SHALL carry the gate tally the run read for it and the verdict of the review round the run read its
diff in, whichever arm the item ended on — not the arm that closed it alone — and the report SHALL NOT name a gate to
re-run for an item whose record carries a tally, and SHALL NOT count an item whose diff was read as never reviewed.
When that tally is red, the record SHALL also carry which tree fails the case the gate named: `metrics.gateAttribution`,
one of `delta | flaked | base | unattributable` — `delta` a case that fails again when the run re-runs it in the item's
own worktree, `flaked` a case that passes on that one re-run, `base` a case that also fails at the run's base commit and
so is a pre-existing red rather than a finding about the delta, `unattributable` a red the gate's own log cannot answer.
The re-run SHALL be the one case the tally named, once, in the tree the item's work already warmed, and a second look
SHALL NOT be repeated nor carried into a flake list. `flaked` SHALL be the one answer that changes what a red tally does
to an item — the red reproduced nowhere, so the item is not blocked on it and the answer stands in the waiver's place
rather than requiring one clause. The field SHALL be read from the gate log `tools/loop/gate.sh` writes beside its
tallies, SHALL NOT pay a base tree for a case the item's own red test already names, and SHALL NOT be carried for a tally
that passed or an item whose gate never ran — a green gate has nothing to attribute, and the state refuses the field
beside it. The tally's prose SHALL NOT be rewritten by the field.

#### Scenario: A blocked item records the gate it ran, and no gate is re-run for it

- **WHEN** a change child stops at the diff, the gate child reports a tally for the gate it ran, and the tail refuses
  the item on that gate
- **THEN** the item's record carries the gate child's tally in `metrics.gate`, in place of the empty field it was
  written with, and the report's action list holds no `re-run the gate` line for that item
- **Case**: `run#aRedGateIsBlockedByTheTail`

#### Scenario: The round that read the diff is recorded on an item that then blocked

- **WHEN** the round before the gate answers `PASS` and the item then ends blocked on its gate
- **THEN** the record carries `metrics.reviewVerdict: PASS` and `metrics.reviewRounds: 1`, and the report's review line
  counts it as a first round that passed rather than as an item never reviewed
- **Case**: `run#aBlockedItemRecordsTheRoundThatReadIt`

#### Scenario: The case the item's own red test names is the delta's

- **WHEN** the gate's tally is red, its log names one failing case, and that case is a `<group>#<name>` token of the
  item's own `LOOP-RED-TEST` line
- **THEN** the run re-runs that case once in the item's own worktree and pays no detached tree at the run's base, the
  item's record carries `metrics.gateAttribution: delta` when it fails again, and the item takes the blocked path a red
  gate took before
- **Case**: `run#aRedGateIsAttributedToTheTreeThatFailsIt`

#### Scenario: A case that passes on the run's second look is recorded as a flake

- **WHEN** the gate's tally is red, its log names a failing case, and a rerun of that one case in the item's own
  worktree — the tree the item's work already warmed — exits `0`
- **THEN** the item's record carries `metrics.gateAttribution: flaked` beside the tally's own prose, the item is not
  blocked on that red and closes without a waiver clause, and the report counts the answer it recorded
- **Case**: `run#aFlakedCaseIsOffTheBlockedPath`

#### Scenario: A case that fails again in the item's own worktree is the delta's

- **WHEN** the gate's tally is red, its log names a failing case the item's red test does not name, that case passes in
  a detached tree at the run's base commit, and it fails again on the rerun in the item's own worktree
- **THEN** the item's record carries `metrics.gateAttribution: delta`, the detached tree is removed, and the item takes
  the blocked path a red gate took before
- **Case**: `run#aFlakedCaseIsOffTheBlockedPath`

#### Scenario: A case that also fails at the base commit is recorded as a pre-existing red

- **WHEN** the gate's tally is red, its log names a case the item's red test does not name, and the rerun command
  `tools/gate-attribute.sh` derived from the failing step fails in a detached tree at the run's base commit
- **THEN** the item's record carries `metrics.gateAttribution: base`, the detached tree is removed, and the item is
  still recorded `blocked` — the answer is readability, not a verdict on a gate that did not pass
- **Case**: `run#aRedGateIsAttributedToTheTreeThatFailsIt`

#### Scenario: A red gate with no readable log is recorded unattributable

- **WHEN** the gate's tally is red and the tree holds no gate log at the profile's tally directory, or the log's case
  cannot be selected at the base
- **THEN** the item's record carries `metrics.gateAttribution: unattributable`, and the item blocks exactly as it did
  before
- **Case**: `run#aRedGateIsAttributedToTheTreeThatFailsIt`

#### Scenario: A gate that passed records no attribution

- **WHEN** a record is written beside a gate tally that passed, or beside no tally at all
- **THEN** the record carries `metrics.gateAttribution: null`, the state refuses the flag with `there is nothing to
  attribute` rather than recording an answer the gate never produced
- **Case**: `state#aCloseOverARedGateWithoutAWaiverIsRefused`
