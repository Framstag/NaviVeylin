# Spec Delta

## Purpose

The project derives a stable per-capability index of its shipped specs and the difference between two points in
time, so that every document built on it selects what to write about by set arithmetic over capability keys instead
of re-reading every spec.

## ADDED Requirements

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

#### Scenario: A new spec id fails the run

- **WHEN** a spec exists in the shipped-spec directory that the classification does not name
- **THEN** the run reports that spec id, exits non-zero, and no document is written or overwritten

#### Scenario: A classified spec id passes

- **WHEN** every shipped spec id is named by the classification
- **THEN** the run proceeds and reports no unclassified id

#### Scenario: A removed spec id is reported without failing on it

- **WHEN** the classification names a spec id that no longer exists in the shipped-spec directory
- **THEN** the run reports that id as stale and continues, and the stale entry does not by itself produce an error

#### Scenario: A surface outside the known set fails the run

- **WHEN** a classification assigns a spec id a surface that is not one of the surfaces the catalogue can render
- **THEN** the run reports the spec id and the unknown surface, exits non-zero, and produces no regenerated document

#### Scenario: A capability's surface is reported

- **WHEN** the index reports a capability
- **THEN** it reports the surface its spec is classified for, so that a surface cannot be inferred from a spec id's name alone

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
