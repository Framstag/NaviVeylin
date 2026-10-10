# Spec Delta

## Purpose

The project publishes, for each release version, a note of the user-visible capabilities that changed since the
previous version, derived from the spec index so that the note cannot drift from the specs.

## ADDED Requirements

### Requirement: One entry per version, derived from that version's baseline

A run SHALL take the release version as an input and SHALL produce at most one entry carrying that version. The entry
SHALL be derived from the baseline snapshot, the current specs and the version, so that re-running the same version
replaces its entry with an identical one rather than adding a second.

#### Scenario: Re-running a version replaces its entry

- **WHEN** a run for a version is repeated with no spec change in between
- **THEN** the notes document holds exactly one entry for that version, byte-identical to the first, and no entry is
  appended

#### Scenario: A new version adds one entry

- **WHEN** a run requests a version newer than every existing snapshot
- **THEN** the notes document gains exactly one entry, carrying the requested version, and existing entries are
  unchanged

#### Scenario: The entry names its version

- **WHEN** an entry is written
- **THEN** it states the release version it was generated for

### Requirement: Only user-visible capabilities appear

A changed capability SHALL appear in an entry only when its spec is classified user-visible. A capability whose spec
is classified internal SHALL NOT be named, summarised or counted in the entry.

#### Scenario: An internal change produces no entry line

- **WHEN** a version's only changed capabilities belong to specs classified internal
- **THEN** the entry lists no capability and no internal spec is named anywhere in the notes document

#### Scenario: A mixed version lists only the user-visible part

- **WHEN** a version changed capabilities of both classifications
- **THEN** the entry lists the user-visible ones and omits the internal ones

### Requirement: Nothing user-visible moved means no entry, with a verdict

When a version's changed user-visible capability set is empty, the run SHALL write no entry for that version, SHALL
exit successfully, and SHALL report explicitly that nothing user-visible changed in that version. It SHALL NOT emit a
placeholder, a generic statement or a summary of internal work.

#### Scenario: A maintenance-only version reports and writes nothing

- **WHEN** a version's changed capabilities are all classified internal
- **THEN** the run writes no entry, exits successfully, and reports that nothing user-visible changed in that version

#### Scenario: The notes document is not created empty

- **WHEN** no version has yet produced a user-visible change
- **THEN** the notes document holds no entry, and the run's report says so rather than an entry saying nothing happened

#### Scenario: The refusal does not invent filler

- **WHEN** a version's user-visible change set is empty
- **THEN** no language model is invoked to produce text for that version

### Requirement: The first run establishes a baseline instead of an entry

When no snapshot sorts before the requested version, the run SHALL record that version's snapshot, SHALL write no
entry for it, and SHALL report that the version established the baseline rather than reporting no change.

#### Scenario: The first ever run writes no entry

- **WHEN** the notes are generated for the first time, for a version with no earlier snapshot
- **THEN** the snapshot for that version is recorded, no entry is written, and the report says the baseline was
  established

#### Scenario: The run after the baseline writes an entry

- **WHEN** a version is generated after a baseline exists and user-visible capabilities changed since it
- **THEN** an entry is written for that version

### Requirement: Every vanished capability key is accounted for

For every capability key present in the baseline and absent from the current index the run SHALL account for the
disappearance, and SHALL report each disappearance in the run report, so that no key can leave the index unnoticed.

#### Scenario: No disappearance goes unreported

- **WHEN** a version's baseline holds a key that the current index does not
- **THEN** the run report names that key and how it was accounted for

#### Scenario: An accounted disappearance is not an error

- **WHEN** every vanished key is accounted for
- **THEN** the run completes successfully and writes the entry and the snapshot

### Requirement: A renamed requirement is a change, not a removal

A requirement that is renamed within a spec that still exists SHALL be reported as a change to that capability, and
SHALL NOT be reported as a removal and an addition.

#### Scenario: A renamed requirement is a change

- **WHEN** a requirement keeps its spec and its meaning but its name changes
- **THEN** the entry reports the capability as changed and does not report a removal

#### Scenario: The renamed capability keeps its place

- **WHEN** a requirement is renamed and its spec is user-visible
- **THEN** the changed capability appears under its feature area in the entry, as it would for any other change

### Requirement: A requirement that is gone is reported as a removal

A requirement absent from a spec that still exists in the shipped set SHALL be reported as a removal when its spec
is user-visible, and SHALL NOT be presented as a change.

#### Scenario: A deleted requirement is a removal

- **WHEN** a user-visible spec still exists and one of its requirements is gone
- **THEN** the entry reports that capability as removed

#### Scenario: An internal removal stays out of the entry

- **WHEN** a requirement is gone from a spec classified internal
- **THEN** the run report accounts for it and the entry does not mention it

### Requirement: A spec leaving the shipped set is reported, not published

A spec present in the baseline that no longer exists in the shipped-spec directory SHALL be reported in the run
report, and its capabilities SHALL NOT appear in an entry as changes.

#### Scenario: A spec leaving the shipped set is reported, not published

- **WHEN** a spec present in the baseline no longer exists in the shipped-spec directory
- **THEN** the run report names the removed spec and the entry does not present its capabilities as changes

### Requirement: The note is grouped by feature area and derived without a model

An entry's changed capabilities SHALL be grouped under the feature-area headings used by the catalogue. Which
capabilities count as changed SHALL be determined without invoking a language model; a model SHALL be used only to
phrase the resulting lines.

#### Scenario: Changed capabilities are grouped by area

- **WHEN** an entry is written for a version whose changed capabilities span two areas
- **THEN** the entry presents them under those two area headings

#### Scenario: The changed set is reproducible without a model

- **WHEN** the run reports which capabilities changed for a version
- **THEN** that set is the same as one obtained by comparing the version's baseline snapshot with the current index,
  with no language model involved

### Requirement: The version is supplied, never read from the build's version state

The run SHALL require the release version as an input and SHALL NOT read the build's release version-state file to
obtain it, because that file is not present in a fresh checkout and not shared between machines. A run without a
version SHALL fail with a usage error before writing anything.

#### Scenario: A run without a version fails

- **WHEN** the run is invoked with no release version
- **THEN** it exits non-zero with a usage error and writes neither an entry nor a snapshot

#### Scenario: The run works where the version-state file is absent

- **WHEN** the run is invoked with a version on a machine where the build's version-state file does not exist
- **THEN** it completes and produces the entry and the snapshot for that version
