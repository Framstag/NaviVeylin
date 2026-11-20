## ADDED Requirements

### Requirement: The archive an item commits carries its own gate's tally

The archived change an item commits SHALL carry the XML the item's own gate wrote under `LOOP_GATE_TALLY_DIR`,
copied into its `evidence/` by `end-item` before the archive commit when that evidence records no passing run. A file
the evidence already holds SHALL NOT be replaced, and a change whose evidence already records a passing run SHALL be
left as it stands. A copy that cannot be written SHALL fail `end-item` rather than thin the record.

#### Scenario: A change whose proof is the gate's tally reads as verified

- **WHEN** an item's tree is committed by `end-item` and the change it archived carries only the red XML a worker
  copied, while the item's gate wrote its passing testsuites under the gate's tally directory
- **THEN** the archived change's `evidence/` carries the tally's XML in the same commit, and `branch-state` reads the
  branch as `adoptable` rather than `archived-not-green`, so a finished attempt whose gate passed is adopted instead
  of spending an owner's decision
- **AND** a tally file whose name the evidence already holds is not written over it, and a change whose evidence
  already records a passing run is left as it stands
- **Case**: `worktree#endItemCopiesTheGatesTallyIntoTheEvidence`
