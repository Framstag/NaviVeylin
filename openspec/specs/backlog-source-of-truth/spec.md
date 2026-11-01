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

### Requirement: A finding is filed in one of two shapes, and neither is optional

The filing path SHALL accept a finding in exactly one of two shapes and SHALL refuse a finding that carries
neither: **tier A**, which owes the cause that proves it, or **tier B**, which owes the change it will be worked
as. A finding that carries both SHALL be refused rather than resolved, because which shape the work takes is not a
decision the writer may make silently.

#### Scenario: A finding with neither shape is refused

- **WHEN** a finding carries no `cause` and no `change`
- **THEN** `tools/issues/create.sh` exits 3 with `shape-missing`, names both shapes and what each owes, and creates
  no issue
- **Case**: `create#aShapelessFindingIsRefused`

#### Scenario: A finding with both shapes is refused

- **WHEN** a finding carries a `cause` and a `change`
- **THEN** the writer exits 3 with `shape-ambiguous`, because a proven fix and a planned change are different work
  and the item may carry only the one it will be worked as
- **Case**: `create#aFindingWithBothShapesIsRefused`

#### Scenario: Either shape files

- **WHEN** a finding carries exactly one shape, complete per the requirements below
- **THEN** the issue is created, and the writer reports which shape it filed
- **Case**: `create#aTierAIsFiled`, `create#aTierBIsFiled`

### Requirement: The shape, and the change a tier-B finding names, are recorded in the body

The writer SHALL record the shape as a body-record marker — `<!-- tier: a -->` or `<!-- tier: b -->` — and, for tier
B, the change's name as `<!-- change: <name> -->`, through the one writer the body record already has, so that a
reader and a writer cannot spell the key differently.

#### Scenario: The marker is written once and read back

- **WHEN** a finding is filed in either shape
- **THEN** its body carries exactly one `tier` marker, the reader returns that value, and a tier-B body carries the
  `change` marker beside it
- **Case**: `create#theShapeIsInTheBodyRecord`

#### Scenario: A change name is written without its change

- **WHEN** a finding carries `change: <name>` and no change directory of that name exists
- **THEN** the writer exits 3 with `change-not-found`, naming the path it looked for and the command that creates a
  change, because a name no tree holds is a plan nobody wrote
- **Case**: `create#aNamedChangeThatDoesNotExistIsRefused`

### Requirement: A tier-A finding carries the cause that proves it, and the pointer resolves

A tier-A finding SHALL carry the cause as a structured field: the `file:line` of the mechanism, the case that
exercises it, and the tallies the case reported before the fix. The path SHALL resolve in the tree, under the
citation rule the body already passes through.

#### Scenario: A cause whose pointer resolves is filed

- **WHEN** a finding carries `cause.file`, `cause.line` and a red case with its tallies, and the path exists
- **THEN** the item is filed as tier A
- **Case**: `create#aCauseWithAResolvingPointerIsFiled`

#### Scenario: A cause whose pointer does not resolve is refused

- **WHEN** the `file:line` a cause names does not resolve in the tree
- **THEN** the writer exits 3 with `cause-unresolved`, naming the token, because the loop's first gate is that the
  claim holds in the tree today and a reader cannot check a pointer that is already gone
- **Case**: `create#aCauseWhosePointerIsGoneIsRefused`

#### Scenario: A tier-A finding names no case

- **WHEN** a tier-A finding states its cause without the case that exercises it
- **THEN** the writer exits 3 with `cause-without-case`, because a cause with no red case is a hypothesis and the
  only thing that distinguishes it from a proven one is the case
- **Case**: `create#aCauseWithoutACaseIsRefused`

### Requirement: A tier-B finding names a change that is ready, and its forks are answered on the item

A tier-B finding SHALL be refused unless its change passes the project's readiness check, and its answered forks
SHALL be written to the item as comments before any state label is written, so that the worker's package carries
the answer beside the question it settles.

#### Scenario: A change that is not ready is refused

- **WHEN** the named change's readiness check reports any refusal
- **THEN** the writer exits 3 with `change-not-ready`, quotes the checker's verdict line, and creates no issue
- **Case**: `create#aChangeThatIsNotReadyIsRefused`

#### Scenario: A ready change with answered forks is filed

- **WHEN** the named change reports `ok` and the finding carries its questions with their decisions
- **THEN** the item is created, one comment carries the question and its answer in the form
  `tools/issues/answer.sh` records, and the item carries no decision label
- **Case**: `create#aTierBIsFiledWithItsDecisions`

#### Scenario: A tier-B finding with no recorded fork is refused

- **WHEN** a tier-B finding carries no `questions`, or a question with no decision
- **THEN** the writer exits 3 with `decision-missing`, because a worker that reads `recorded: none` re-derives the
  fork it was supposed to be handed
- **Case**: `create#aTierBWithNoDecisionIsRefused`

### Requirement: The readiness check is reached through configuration, and an empty path refuses the shape

The path of the readiness check SHALL be read from the project's profile, and an **empty** value SHALL refuse the
tier-B shape rather than filing it unchecked, because an empty value is a declaration that the project has no such
check and not a licence to skip it.

#### Scenario: A project that declares no readiness check cannot file tier B

- **WHEN** the profile's `checks.changeReady` is empty and a finding carries a change
- **THEN** the writer exits 3 with `gate-not-configured`, naming the key, and the finding can be filed only in
  tier A
- **Case**: `create#anEmptyGatePathRefusesTierB`

#### Scenario: The declared path is the one that runs

- **WHEN** the profile declares a readiness check and a tier-B finding is filed
- **THEN** the writer runs that path against the named change and quotes its verdict, and the path is resolved from
  the profile rather than written into the writer
- **Case**: `create#theDeclaredGatePathIsTheOneRun`
