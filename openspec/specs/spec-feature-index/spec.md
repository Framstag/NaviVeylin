# spec-feature-index Specification

## Purpose

The project derives a stable per-capability index of its shipped specs and the difference between two points in
time, so that every document built on it selects what to write about by set arithmetic over capability keys instead
of re-reading every spec.

## Requirements

### Requirement: A capability key identifies one requirement of one shipped spec

The index SHALL derive exactly one key per requirement of every spec in the project's shipped-spec directory, formed
as `<spec-id>#<requirement name>`. A key SHALL remain identical across runs while its requirement name and its spec
id are unchanged, and the index SHALL contain no key for a spec outside the shipped-spec directory.

#### Scenario: Keys are derived for every shipped capability

- **WHEN** the index runs over the project's shipped specs
- **THEN** it holds one key per requirement of every spec in the shipped-spec directory, and the count of keys equals
  the total requirement count reported for those specs

#### Scenario: A key survives other specs changing

- **WHEN** one spec changes and the index runs again
- **THEN** every key belonging to an unchanged spec is identical to its previous value

#### Scenario: An in-flight change contributes no key

- **WHEN** a change is in flight and its capability specs exist only inside the change directory
- **THEN** the index contains no key for those capability specs

### Requirement: The delta compares requirement text, not spec bytes

The index SHALL classify every capability key as added, changed, removed or unchanged by comparing the requirement
text recorded for that key with the text recorded for the same key in the baseline. A spec edit that alters no
requirement text SHALL produce no changed key.

#### Scenario: A prose-only edit is not a change

- **WHEN** a spec's purpose or wording outside its requirement texts is edited and nothing else changes
- **THEN** no key is classified as changed or added, and the delta reports no change

#### Scenario: A requirement text edit is a change

- **WHEN** a requirement's text is edited
- **THEN** that key is classified as changed and no other key is

#### Scenario: An added requirement is an addition

- **WHEN** a requirement is added to an existing spec
- **THEN** its key is classified as added and the spec's other keys are unchanged

### Requirement: An unclassified spec id stops the run

Every shipped spec id SHALL belong to exactly one feature area and SHALL be classified either user-visible or
internal, and SHALL also be classified for the surface it applies to. A shipped spec id that the classification does
not name SHALL be reported by id, SHALL make the run fail, and SHALL produce no regenerated document.

A capability id that an open change's delta specs introduce SHALL be classified before that change is archived. The
run SHALL read those delta specs from `openspec/changes/<change>/specs/<capability>/spec.md`, SHALL NOT read the
delta specs of a change under `openspec/changes/archive/`, SHALL report an id they name that the classification does
not name together with the open change that owes the line, SHALL make the run fail, and SHALL produce no regenerated
document. A classification entry for such an id SHALL be reported as `pending` — classified ahead of its archive, an
entry for work the archiving move publishes rather than for something gone — and SHALL NOT by itself produce an error.
A classification entry naming an id the tree no longer holds SHALL be reported as `stale`, which is an entry of its
own, and SHALL NOT by itself produce an error either. The run SHALL print the two figures apart, so that an entry for a
capability on its way cannot be read as an entry for one that has left.

#### Scenario: A new spec id fails the run

- **WHEN** a spec exists in the shipped-spec directory that the classification does not name
- **THEN** the run reports that spec id, exits non-zero, and no document is written or overwritten
- **Case**: `gate: an unclassified shipped spec id fails the run`

#### Scenario: A capability an open change introduces fails until it is classified

- **WHEN** an open change carries a delta spec under `openspec/changes/<change>/specs/` for a capability id that the
  classification does not name
- **THEN** the run reports that spec id, exits non-zero, and no document is written or overwritten
- **Case**: `gate: a capability an open change introduces must be classified`

#### Scenario: The failure names the id and the change that owes the line

- **WHEN** an open change introduces a capability the classification does not name
- **THEN** the failure names that capability id and the open change that owes the line, so that the missing entry is
  added in the change that introduced it rather than in a later one
- **Case**: `gate: the unclassified open capability is named`

#### Scenario: A delta classified before it ships passes the run

- **WHEN** an open change introduces a capability that the classification names, and no shipped spec id is
  unclassified
- **THEN** the run proceeds, reports nothing unclassified, and reports that entry as pending — classified ahead of its
  archive — without failing on it
- **Case**: `gate: a classified delta passes before it ships`, `gate: a delta-only classification is reported as
  pending, not as stale`

#### Scenario: An entry that is pending is not reported as stale

- **WHEN** the classification names a capability id an open change's delta introduces, and that change has not been
  archived
- **THEN** the run counts the id in its pending figure and prints no stale figure for it — `stale: 0` where nothing
  else has left the tree — and names it in its report as classified ahead of its archive, never as a stale entry
- **Case**: `gate: a delta-only classification is not a stale entry`, `gate: the pending entry is named as one`,
  `gate: a pending entry is not reported as stale`

#### Scenario: The two figures are printed apart

- **WHEN** the run's classification check prints what it read
- **THEN** it prints `stale: <n>` for the entries whose spec id tree no longer holds and
  `pending (classified ahead of its archive): <n>` for the open changes' deltas, each on a line of its own, and its
  report names the pending id with the sentence `classified ahead of its archive` and the stale one as a `stale
  classification entry`
- **Case**: `gate: a stale entry is reported`, `gate: the stale entry is named`, `gate: the pending entry is named as
  one`

#### Scenario: An archived change is history

- **WHEN** a capability spec exists only under `openspec/changes/archive/` and the classification does not name it
- **THEN** the run reports nothing unclassified for it and does not fail, because the shipped set and the open
  changes are the only two sets it reads
- **Case**: `gate: an archived change's delta is not read`

#### Scenario: A classified spec id passes

- **WHEN** every shipped spec id is named by the classification
- **THEN** the run proceeds and reports no unclassified id
- **Case**: `gate: a delta-only classification leaves nothing unclassified`

#### Scenario: A removed spec id is reported without failing on it

- **WHEN** the classification names a spec id that no longer exists in the shipped-spec directory
- **THEN** the run reports that id as stale and continues, and the stale entry does not by itself produce an error
- **Case**: `gate: a stale classification entry does not fail the run`

#### Scenario: A surface outside the known set fails the run

- **WHEN** a classification assigns a spec id a surface that is not one of the surfaces the catalogue can render
- **THEN** the run reports the spec id and the unknown surface, exits non-zero, and produces no regenerated document
- **Case**: `gate: a surface outside the vocabulary fails the run`

#### Scenario: A capability's surface is reported

- **WHEN** the index reports a capability
- **THEN** it reports the surface its spec is classified for, so that a surface cannot be inferred from a spec id's
  name alone
- **Case**: `keys: the surface a spec is classified for is reported`

### Requirement: A snapshot records the state a version was generated from

A run given a release version SHALL record a snapshot keyed by that version, holding every capability key with the
classification that applied to it and a value that changes if and only if that key's requirement text changes. A run
for a version SHALL use as its baseline the most recent snapshot whose version sorts before the requested version.

#### Scenario: The baseline is the most recent earlier snapshot

- **WHEN** snapshots exist for `2026-10-08-1` and `2026-10-08-2` and a run requests `2026-10-08-3`
- **THEN** `2026-10-08-2` is the baseline

#### Scenario: Versions order as the release format orders them

- **WHEN** snapshots exist for `2026-10-08-1`, `2026-10-08-2` and `2026-10-08-10`
- **THEN** a run requesting `2026-10-09-1` uses `2026-10-08-10` as its baseline

#### Scenario: No earlier snapshot exists

- **WHEN** a run requests a version and no snapshot sorts before it
- **THEN** the run records that version's snapshot and reports that no baseline existed

#### Scenario: A snapshot carries what a later run needs to compare

- **WHEN** a run records a version's snapshot and a later run uses it as a baseline
- **THEN** the later run decides which keys changed without reading any file other than the snapshot and the current
  shipped specs

### Requirement: The index runs offline, without a model and without a build

The index SHALL derive the capability keys, the classification result, the delta and the snapshot without network
access, without invoking a language model, without a Gradle build and without a device.

#### Scenario: The index runs with no model available

- **WHEN** the index runs where no language-model provider is reachable
- **THEN** it completes and reports the same keys, classifications and delta it reports where a provider is reachable

#### Scenario: The index runs over the whole shipped set

- **WHEN** the index runs over the project's shipped specs
- **THEN** it completes in under 30 seconds on a developer workstation

### Requirement: A run reports what it read and what moved

Every run SHALL report the number of specs read, the number of capability keys in the index, the number of keys
classified added, changed, removed and unchanged, the number of dirty feature areas, and the number of
unclassified or stale spec ids.

#### Scenario: A no-op run is visibly a no-op

- **WHEN** the index runs twice with no spec change in between
- **THEN** the second run reports zero added, changed and removed keys and zero dirty areas

#### Scenario: A one-spec change is visibly one change

- **WHEN** the index runs after exactly one requirement's text was edited
- **THEN** the run reports one changed key and names the feature area that key belongs to

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
