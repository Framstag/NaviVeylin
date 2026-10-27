# stale-citation-resolution — deltas

## ADDED Requirements

### Requirement: A reversal restores the state the item's own record keeps

The reversal `--revisit-parked` owns SHALL restore the settled state an item's own record keeps, not the queue
state, when that record is present. A body carrying the profile's `settledRecordMarker` SHALL be reversed to
`roles.retiredStatus` and never to `openStatus`; a body carrying no such record SHALL keep the reversal to
`openStatus`. The run SHALL report the two outcomes apart (`restored` beside `reopened`) and SHALL still never
write the authorisation label.

#### Scenario: A parked tombstone is restored to its settled state

- **WHEN** a parked item the resolver reverses carries the settled-record marker in its body
- **THEN** its status is the profile's `retiredStatus` and not `openStatus`, the loop's stale refusal is
  cleared, the authorisation label is not written, and the run counts it as restored
- **Case**: `resolver#parkedTombstoneKeepsItsSettledState`

#### Scenario: An item without the record returns to the open state

- **WHEN** a parked item whose body carries no settled-record marker is reversed
- **THEN** its status is the profile's `openStatus`, as before, and the run counts it as reopened rather than
  restored
- **Case**: `resolver#cleanParkedItemIsReopened`

#### Scenario: A restore target that is not a settled state is refused

- **WHEN** the run asks for the reversal and the profile declares `retiredStatus` outside its
  `settledStatuses`
- **THEN** the run stops before it writes anything, naming both roles, so no item is reversed into a state the
  pre-screen would read as work
- **Case**: `resolver#tombstoneRestoreNeedsASettledState`
