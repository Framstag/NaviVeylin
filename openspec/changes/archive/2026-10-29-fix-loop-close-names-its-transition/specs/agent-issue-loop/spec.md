## ADDED Requirements

### Requirement: A closure names the settled-state transition it makes

The write that records the loop's closure SHALL name the settled-state transition it makes: the item it closes
may carry the other settled value of the profile, and the guard every routine writer meets refuses a write that
displaces a settled value without naming the transition. The closure SHALL displace the settled value it
replaces, SHALL report that value, and SHALL land as one act instead of being refused between its two writes.

#### Scenario: A closure over the other settled value lands and names it

- **WHEN** the loop closes an item that carries the other settled value of the profile's
  `roles.settledStatuses`
- **THEN** the write records the settled state the closure means, displaces the settled value it replaced, and
  reports that value, so the item is closed rather than left half attempted
- **Case**: `labels#closeNamesTheSettledTransition`
