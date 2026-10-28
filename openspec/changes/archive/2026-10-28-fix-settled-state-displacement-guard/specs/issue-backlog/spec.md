# issue-backlog — deltas

## ADDED Requirements

### Requirement: A settled state is displaced only by a call that names the transition

A state write SHALL NOT displace a value of the profile's `roles.settledStatuses` unless the call names that
transition. A write that would displace one SHALL be refused before any issue write, with exit 3 and the reason
`status-settled` naming the settled value it would have removed, so a hold — a claim about a run's capacity —
cannot erase a claim about the item's premise. The call that names the transition SHALL perform the
replacement in the same single write, and SHALL name the settled value it removed. The two verbs whose own
purpose is that transition — a closure, which records `closeStatus`/`retiredStatus`, and a reopen, which
reverses a completed closure and carries the returning measurement — SHALL pass it. A state write that
displaces no settled value SHALL be unchanged.

#### Scenario: A routine state write does not displace a settled state

- **WHEN** a state write names an item that carries a value of the profile's `roles.settledStatuses`, and the
  call does not name the transition
- **THEN** the call is refused with exit 3 and the reason `status-settled` naming that value, and nothing
  reaches the tracker
- **Case**: `update#refusesToDisplaceASettledState`

#### Scenario: The write that names the transition lands and reports it

- **WHEN** the same write is issued with the transition named
- **THEN** one issue write adds the new state and removes the settled one, and the call names the settled value
  it removed
- **Case**: `update#refusesToDisplaceASettledState`

#### Scenario: A closure displaces one settled state for the other

- **WHEN** a closure records the settled state a decision not to act carries on an item that already carries
  the other settled value
- **THEN** the closure is not refused: one issue write adds the state it records and removes the settled value
  it displaces
- **Case**: `close#namesTheSettledStateItDisplaces`
