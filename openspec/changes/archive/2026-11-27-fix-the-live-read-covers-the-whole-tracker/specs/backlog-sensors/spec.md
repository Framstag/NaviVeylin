## ADDED Requirements

### Requirement: A live read of the tracker covers every item, or it is not a whole read

A read of the tracker SHALL stop only where the list ends. A fixed window over a newest-first list SHALL NOT stand in
for the tracker: the population a report prints SHALL be the items the read held, and a per-item invariant, a row's
condition list and a fingerprint lookup SHALL each reach every item of the tracker rather than the newest ones a
window happened to hold.

#### Scenario: A tracker longer than one window is read to its last item

- **WHEN** the per-item half reads a tracker whose items outnumber the window the read used to ask for, and the only
  disagreement in it (a band label over a marker of another band) sits on the oldest item
- **THEN** the read holds every item, the report names that item's disagreement, and the population it prints is the
  tracker's own — where a fixed window reports a clean tracker over the newest items alone
- **Case**: `metadata#theLiveReadDoesNotTruncateTheTracker`
