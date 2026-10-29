# backlog-sensors — deltas

## MODIFIED Requirements

### Requirement: The priority pair is re-checked on a schedule

A project's checks SHALL include a scheduled re-read of the tracker's priority invariant, and of every per-item
invariant the command that owns them reports, so that an item whose label and its own record disagree becomes a
check's verdict rather than something only a hand run sees. The measurement SHALL read that command rather than
restating a rule, identify a drift by the item and the disagreement it breaks, and name each condition by the
invariant its own code breaks.

#### Scenario: A drifted item is one condition, per item and disagreement

- **WHEN** the scheduled re-read reports an item whose band label and stored marker disagree, and another item
  whose two halves agree
- **THEN** the row reports one violation per drifted item, whose fingerprint is that item and the disagreement it
  carries, and the agreeing item is no condition at all
- **Case**: `sensors#metadataDriftRow`

#### Scenario: The measurement is the command that owns the rule

- **WHEN** the row measures the tracker
- **THEN** it reads `tools/issues/check-metadata.sh --live` and reports that command's own violations, restating
  no part of the marker grammar and no vocabulary value, so the row and a person's hand run cannot disagree
  about what a drift is — and a read that reports no item at all is the third outcome rather than a clean
  tracker
- **Case**: `sensors#metadataDriftRow`

#### Scenario: The row runs in the pipeline that writes

- **WHEN** the checks' pipeline selects the rows it runs
- **THEN** the row is selected by the step that runs for any record, because it reads the tracker and not the
  build, so a red build does not hide a drifted pair
- **Case**: `sensors#metadataDriftRow`

#### Scenario: A repaired pair clears under the identity the item holds

- **WHEN** a recorded drift's item no longer disagrees
- **THEN** the row reports a clear naming that item and that disagreement, so the issue carrying the drift
  closes on the identity it holds, while a pair that still disagrees is never reported as cleared
- **Case**: `sensors#metadataDriftClearsUnderItsOwnIdentity`
