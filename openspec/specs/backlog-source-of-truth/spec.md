# backlog-source-of-truth Specification

## Purpose

The issue tracker is the project's only live backlog: a finding is recorded in one place, the references
that pointed at the record it came from keep resolving after that record is gone, and history keeps its
own text so that what was known when a change landed can still be read.

## Requirements

### Requirement: The tracker is the single live backlog

Instructions that tell a reader or a process what to work on SHALL name the tracker and never a file, and
the record the backlog was migrated from SHALL be removed only after every such instruction has been
rewired and after the reference check below is green.

#### Scenario: A workflow instruction names the tracker

- **WHEN** a reader follows a rule, a skill or an entry document that says how to find or record open work
- **THEN** it names the tracker, so no instruction leads to a file that no longer exists

#### Scenario: The old record is removed last

- **WHEN** the migration's steps are executed
- **THEN** the record is removed only after the rewiring and the passing reference check, so the tree is
  never left with an instruction pointing at a file that is gone

#### Scenario: History is not rewritten

- **WHEN** an archived change, an immutable artifact or a citation names the old record
- **THEN** its text is preserved, because it records what was known when it was written

### Requirement: Every historical reference resolves

A reference that identifies an item of the migrated backlog SHALL resolve to exactly the issue that
carries that item, and a reference that does not resolve SHALL fail a check rather than pass unnoticed.

#### Scenario: A citation resolves

- **WHEN** a document cites an item of the migrated backlog by its identifier
- **THEN** the issue carrying that item is found, without the migrated record existing

#### Scenario: A live mention without an identifier fails the check

- **WHEN** a live file names the old record without citing an item identifier
- **THEN** the check fails and names the file and the line, so a surviving instruction is found
  mechanically rather than by a reader

#### Scenario: An unresolvable citation fails the check

- **WHEN** a citation names an identifier no issue carries
- **THEN** the check fails and names the citation

#### Scenario: The check's exclusions are visible

- **WHEN** the check passes
- **THEN** it prints which files it excluded and why, so an exclusion cannot hide a failure

### Requirement: The migrated backlog's shape is recorded

The migration SHALL record what it produced — the number of items, their distribution across kind, area
and state, and the mapping from each item's identifier to its issue — and SHALL produce a backlog in
which every imported item is open and none is authorised for automated work.

#### Scenario: Every item became exactly one issue

- **WHEN** the migration completes
- **THEN** the number of issues equals the number of items the migration previewed, and the recorded
  mapping accounts for every item

#### Scenario: Imported items are not pre-authorised

- **WHEN** the imported backlog is inspected
- **THEN** none of its items carries the automation opt-in, so importing cannot authorise work

#### Scenario: Imported items keep their recorded state

- **WHEN** an imported item's source record stated a state
- **THEN** the issue carries the corresponding state, and an item whose record stated that work remained
  is not closed by the migration

#### Scenario: Re-running the migration changes nothing

- **WHEN** the migration is run again against the same source
- **THEN** it creates and edits nothing and reports the items as already present

### Requirement: The backlog is selectable without reading it

The backlog SHALL be selectable by the facts its labels carry — kind, area, state, provenance and
priority — so that a reader can ask for a working set without reading the items themselves.

#### Scenario: A working set is selected by query

- **WHEN** a reader asks for the items of one kind, or one area, or one state
- **THEN** the selection is answered from the labels, without reading the items' bodies

#### Scenario: The former index is not maintained by hand

- **WHEN** the migrated record's generated index is considered
- **THEN** no replacement index is maintained by hand, because a maintained list is what drifted before

### Requirement: The tooling keeps working with no automated actor present

The commands that read and write the tracker SHALL run for a person, on the host, with no automated actor,
no sandbox and no dependency on one being installed.

#### Scenario: A person runs a tracker command directly

- **WHEN** a person invokes create, list, update, close or the reference check
- **THEN** it works with only the repository and an authenticated forge client

#### Scenario: No command requires an automated actor

- **WHEN** the tooling is inspected
- **THEN** no command requires an agent, a sandbox or a pipeline to be present

### Requirement: One fingerprint, one item

The tracker SHALL NOT keep two open items that carry the same fingerprint marker, and a duplicate that the
filing path did not catch SHALL be found by a sweep that names the surviving item rather than by a person
remembering to look.

#### Scenario: A duplicate is found after the fact

- **WHEN** two open items carry the same `<!-- fingerprint: … -->` marker
- **THEN** the sweep names both, keeps the older item, and closes the younger one with the survivor named in
  the reason and the evidence
- **Case**: `duplicates#youngerDuplicateIsClosedWithAPointer`

#### Scenario: The sweep never widens the authorisation

- **WHEN** the younger item of a duplicate group does not carry the label that authorises automated work
- **THEN** the sweep writes the pointer comment only and leaves the item open; the closure stays a person's,
  and `close.sh`'s refusal (`missing-eligibility-label`) is not worked around
- **Case**: `duplicates#unauthorisedDuplicateIsCommentedNotClosed`

#### Scenario: A duplicate the fingerprint cannot see stays a person's call

- **WHEN** two items describe one finding but their fingerprints differ
- **THEN** the sweep reports nothing about them — the fingerprint is the only duplicate criterion it applies,
  and a judgement merge stays with `cleanup-todo`
- **Case**: `duplicates#differentFingerprintsAreNotMerged`

#### Scenario: The identity markers are read, never rewritten

- **WHEN** the sweep reads `<!-- fingerprint: … -->` and `<!-- nvid: N -->`
- **THEN** it writes neither, on either item, in either outcome — the fingerprint is what makes a duplicate
  findable and the `nvid` is what a `§N` citation resolves through
- **Case**: `duplicates#collisionIsReportedAndNothingIsWritten`
