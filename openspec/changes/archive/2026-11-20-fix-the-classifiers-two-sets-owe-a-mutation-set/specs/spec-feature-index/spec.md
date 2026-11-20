# Spec Delta — spec-feature-index

## ADDED Requirements

### Requirement: The classifier's two report-only states are held by a mutation set

The `pending` and `stale` sets the classification check derives, and the two figures it prints, SHALL be held by a
versioned mutation set at `tools/tests/gen-feature-list-mutations.json`, with one record per expression: which set a
not-shipped id falls in, and which set each figure counts. Every record SHALL name the one case it reds and SHALL be
runnable through `tools/mutate.sh`, whose `case-cmd` runs that case alone — so a suite whose cases could not be named
SHALL gain the ability to name one, rather than leaving a record to red on whatever the whole suite failed. A case
SHALL fail when a record's `search` no longer quotes exactly one line of `tools/gen-feature-list.sh`, because a record
the runner reports `MUTATION-DID-NOT-APPLY` reaches a run nobody owes. A `--case` filter naming no case of the suite
SHALL exit 2 rather than report a pass.

#### Scenario: The set names the two states and their two figures

- **WHEN** `tools/tests/gen-feature-list-mutations.json` is read
- **THEN** it holds five records — the not-shipped set (`tools/gen-feature-list.sh:204`), the two states (`:205`,
  `:206`) and the two figures that count them (`:209`, `:210`) — each naming `tools/gen-feature-list.sh` as the file
  it mutates, each naming the case it reds, and a `case-cmd` that runs `tools/tests/gen-feature-list-selftest.sh
  --case` with that record's `case`, so the record, the case and this delta quote one string
- **Case**: `mutation: the classification's two sets and their counts are held by a record set`

#### Scenario: A record a moved expression orphaned fails the case that pins the set

- **WHEN** a record's `search` no longer matches exactly one line of `tools/gen-feature-list.sh`
- **THEN** the case fails on the count it takes over the set's own searches, because `MUTATION-DID-NOT-APPLY` is
  reported only to a run nobody owes — the pin is an assertion in the suite the gate runs, not a note in the set
- **Case**: `mutation: the classification's two sets and their counts are held by a record set`

#### Scenario: A pending entry computed as the remainder, or its figure counted from the stale set, reds the case that names it

- **WHEN** `tools/gen-feature-list.sh:205` takes the remainder instead of the intersection, or `:209` counts `$stale`
  instead of `$pending`
- **THEN** `tools/mutate.sh` reports that record red against `gate: a delta-only classification is reported as
  pending, not as stale` and green again after the restore, so a capability on its way is never reported as one that
  has left
- **Case**: `gate: a delta-only classification is reported as pending, not as stale`

#### Scenario: A stale entry computed as the intersection, or its figure counted from the pending set, reds the case that names it

- **WHEN** `tools/gen-feature-list.sh:206` takes the intersection instead of the remainder, or `:210` counts
  `$pending` instead of `$stale`
- **THEN** `tools/mutate.sh` reports that record red against `gate: a delta-only classification is not a stale entry`
  and green again after the restore, so a capability on its way is reported as unfinished work and not as an entry
  for something gone
- **Case**: `gate: a delta-only classification is not a stale entry`

#### Scenario: The not-shipped set read as the intersection reds the stale-entry case

- **WHEN** `tools/gen-feature-list.sh:204` takes the intersection of the shipped and classified sets instead of the
  ids only the classification names
- **THEN** `tools/mutate.sh` reports that record red against `gate: a stale entry is reported` and green again after
  the restore, because both states are computed from that set and a stale entry it loses is a figure that reports
  nothing
- **Case**: `gate: a stale entry is reported`

#### Scenario: A filter that names no case runs no pass

- **WHEN** `--case` names a case this suite does not record
- **THEN** the suite exits 2 and prints no `ok` line, so a record whose `case` was renamed cannot report a red case
  that never ran
- **Case**: none — no case pins the refusal: reaching it would spawn the suite inside itself, so it is read from
  `tools/tests/gen-feature-list-selftest.sh` (the `case_matched` guard before the summary) and left unpinned.
