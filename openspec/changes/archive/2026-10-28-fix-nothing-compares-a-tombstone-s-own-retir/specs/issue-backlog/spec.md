# issue-backlog — deltas

## ADDED Requirements

### Requirement: A settled record in an item's body agrees with its state label

An item whose body carries the profile's `settledRecordMarker` SHALL carry a `status/*` label whose value is one
of `roles.settledStatuses`, and a check over the tracker's per-item invariants SHALL report every item that does
not, naming the item, the state it carries and the invariant it breaks. A project that declares no marker SHALL
check nothing, and an item with no record SHALL never be a violation — a state label alone is how an ordinary
closure is recorded. The check SHALL report and SHALL NOT repair.

#### Scenario: check#aDisplacedSettledStateIsReported

- **WHEN** an item's body carries the profile's settled-record marker and its state label is a value outside
  `roles.settledStatuses`
- **THEN** the check fails, naming that item, the state it carries and the invariant it breaks, while the same
  item carrying a settled state passes and an item with no record is no violation at all
- **Case**: `check#aDisplacedSettledStateIsReported`
