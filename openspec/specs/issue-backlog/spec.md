# issue-backlog Specification

## Purpose

The project's issue tracker is its backlog: each finding is one issue whose labels carry what kind it is,
where it lives and what state it is in, so a reader selects a working set by query instead of reading the
backlog, and so an automated actor can only ever act on an item its owner has explicitly authorised.

## Requirements

### Requirement: One issue per distinct finding

The tooling SHALL create exactly one issue per distinct finding, and SHALL refuse to create a second
issue for a finding whose computed fingerprint already matches one, naming the issue that already exists. A
finding's required `class` and `category` SHALL each be filed as a label of its namespace, so a finding cannot
state a kind or an area the tracker does not carry, and a value the vocabulary does not hold SHALL be refused
rather than dropped.

#### Scenario: A new finding is filed

- **WHEN** a finding is submitted with its kind, its area and its claim
- **THEN** one issue exists carrying that claim, and its labels record the kind and the area
- **Case**: `create#carriesTheFindingsClassAndCategory`

#### Scenario: The required fields are labels, not decoration

- **WHEN** a finding states its kind and its area only in its `class` and `category` fields — its `labels` array
  empty or absent — or states them in both places
- **THEN** the issue is created carrying `class/<value>` and `category/<value>`, each named once, and a value
  outside the closed vocabulary is refused before any write
- **Case**: `create#carriesTheFindingsClassAndCategory`

#### Scenario: A repeated finding is refused, not duplicated

- **WHEN** a finding whose fingerprint matches an existing issue is submitted
- **THEN** no issue is created, the call reports the outcome as a duplicate rather than as a failure,
  and it names the existing issue

#### Scenario: A deliberate duplicate is possible and recorded

- **WHEN** a caller asserts that a matching fingerprint describes a genuinely different finding
- **THEN** the issue is created and the override is recorded in the output

### Requirement: Labels carry the item's kind, area, state and provenance

The tooling SHALL express an item's kind, area, lifecycle state and provenance as labels in separate
namespaces, with exactly one value per namespace where the item has that fact, and SHALL draw every value
from a closed vocabulary held by the project. On the filing path — where the item is born and there is nothing
to displace — a label set that names two values of one namespace SHALL be refused before any write, with a
reason naming both values. The finding's `class` and `category` fields are one of the routes a value arrives
through, so a field that names a different value than the item's own labels for that namespace SHALL be refused
like any other conflicting route, never resolved in silence.

#### Scenario: Each fact has its own namespace

- **WHEN** an issue is created
- **THEN** its kind, its area, its state and its provenance are each a label in a distinct namespace,
  so selecting by one fact cannot select by another

#### Scenario: A value outside the vocabulary is refused

- **WHEN** a caller supplies a value that the project's vocabulary does not contain
- **THEN** the operation is refused with a reason naming the value, and no label is created, so a typo
  cannot hide an issue from every selector

#### Scenario: A field cannot contradict the labels it duplicates

- **WHEN** a finding's `labels` array names one class (or one category) and the finding's own `class` (or
  `category`) field names another
- **THEN** the filing is refused before any issue write, with exit 2 and a reason naming both values
- **Case**: `create#refusesAClassItsLabelsContradict`

#### Scenario: A priority namespace is reserved

- **WHEN** the taxonomy is created
- **THEN** it contains a priority namespace whose values are the project's bands in a fixed range, and the
  tooling that computes a band is not the tooling that defines what a band is

#### Scenario: Provenance is distinguishable

- **WHEN** an issue was imported, filed by an automated check, or filed by a person
- **THEN** its provenance label says which, so a reader can tell an imported item from a filed one

#### Scenario: create#refusesTwoValuesOfOneNamespace

- **WHEN** a filing's merged label set names two values of one namespace — the finding's own labels carrying
  one state and `--status` naming another, or a `--label` adding a second value of any other namespace
- **THEN** the filing is refused before any issue write, with exit 2 and a reason naming both values, and an
  item whose state is named only through `--status` or only through the labels still files

### Requirement: Only an authorised actor's item may be acted on

The tooling SHALL act on an issue only when its author is on a committed allowlist and the issue carries
the project's explicit opt-in label, and SHALL refuse to run at all when the identity it is running as is
not on that allowlist. The opt-in authorises unattended **work**, so a closure that records a person's
decision not to act — the not-planned closure, which writes the settled status a retired item already
carries — SHALL NOT require it: that closure SHALL proceed when the gate's only refusal is
`missing-eligibility-label`, and every other refusal (the author allowlist, a reserved label, a missing
reason or missing evidence) SHALL still stop it. A completed closure of an item that no declared check owns
SHALL still require the opt-in, because that one asserts work which landed; a declared check's closure of its
own issue is authorised by that check's row instead, so the `--sensor` path needs no opt-in either.

#### Scenario: An unauthorised author is not acted on

- **WHEN** an issue whose author is not on the allowlist is considered
- **THEN** it is excluded and the exclusion names the author as the reason

#### Scenario: The opt-in is required and is not the author's decision alone

- **WHEN** an issue by an allowlisted author does not carry the opt-in label
- **THEN** it is not eligible, so filing an item and authorising work on it remain two acts

#### Scenario: The tooling cannot grant the opt-in to itself

- **WHEN** any operation of the tooling is given the opt-in label as an input
- **THEN** it is refused with a reason, so no script, check or automated pass can authorise its own work

#### Scenario: A foreign identity stops the tooling rather than narrowing it

- **WHEN** the identity the tooling runs as is not on the allowlist
- **THEN** it refuses before acting and says so, rather than reporting an empty working set

#### Scenario: A not-planned closure is a record write, not an act of work

- **WHEN** an issue by an allowlisted author carries a settled state (`status/retired`) and no opt-in label,
  and a not-planned closure of it is requested with a reason and its evidence
- **THEN** the closure proceeds — the reason and its evidence are posted and the CLI receives the `not planned`
  state reason — while the completed closure of that same item is still refused with
  `missing-eligibility-label` and posts nothing
- **Case**: `close#notPlannedNeedsNoOptIn`

### Requirement: Identity survives its source

An issue imported from an existing record SHALL carry a machine-readable marker naming that record's
identifier, and the tooling SHALL be able to find an issue by that identifier.

#### Scenario: A historical reference resolves

- **WHEN** a reference to an imported item's source identifier is looked up
- **THEN** exactly the one issue carrying that marker is returned

#### Scenario: A partial identifier does not match

- **WHEN** a lookup is given an identifier that is a substring of another item's identifier
- **THEN** the other item is not returned, because the match is on the marker and not on its text

#### Scenario: Re-importing is a no-op

- **WHEN** the same source record is imported again
- **THEN** no issue is created or edited, and the outcome is reported as already present

### Requirement: Closure is the only terminal state, and deletion is not offered

The tooling SHALL close an issue only together with a stated reason and the evidence for it, SHALL record
whether the closure was a completed fix or a decision not to act, and SHALL expose no operation that
deletes an issue.

#### Scenario: A closure without evidence is refused

- **WHEN** a closure is requested without a reason or without evidence
- **THEN** nothing is written and the call reports what is missing

#### Scenario: The reason is recorded before the closure

- **WHEN** a valid closure is requested
- **THEN** the reason is attached to the issue before it is closed, so the closure can be audited and
  reversed

#### Scenario: No operation deletes an issue

- **WHEN** the whole tooling surface is inspected
- **THEN** it offers no deletion, so the only record of a finding cannot be destroyed by a call

### Requirement: The tooling runs against the current project without being told

The tooling SHALL determine the project it is operating on from the working tree it is invoked in, SHALL
read every project-specific fact from a single committed profile, and SHALL NOT require a project name, a
repository or a path as an argument.

#### Scenario: Invoked from a subdirectory

- **WHEN** the tooling is invoked from anywhere inside the project's tree
- **THEN** it resolves that project's root, that project's repository and that project's profile

#### Scenario: Invoked inside a separate working tree of the project

- **WHEN** the tooling is invoked inside a linked working tree of the project
- **THEN** it uses that working tree's own profile and never the main checkout's

#### Scenario: The environment does not decide the project

- **WHEN** an environment variable names a different repository than the working tree's
- **THEN** the working tree wins, and only an explicit argument can override it

#### Scenario: Invoked outside a project

- **WHEN** the tooling is invoked where there is no project
- **THEN** it reports that and stops, rather than inventing a default

#### Scenario: A project fact lives in one place

- **WHEN** a project-specific value is needed — the repository, an allowlisted author, a vocabulary value,
  a bound
- **THEN** it is read from the project's profile, so adapting the tooling to another project is an edit to
  that file alone

### Requirement: A state transition replaces the item's state label

A call that sets an item's lifecycle state SHALL leave exactly one `status/*` label on that issue: it SHALL
remove every other value of that namespace in the same write that adds the new value, so a single call cannot
leave two states and a caller needs no second call to complete the transition.

#### Scenario: update#statusReplacesTheOldStatus

- **WHEN** a state write names an item that carries a different state
- **THEN** one issue write removes the old state label and adds the new one, and the item carries exactly one
  `status/*` label afterwards

#### Scenario: update#stateNamedAsALabelReplacesTheOldState

- **WHEN** a state is named as the namespaced label itself rather than through the state flag — the spelling a
  declared check's escalation uses — on an item that carries a different state
- **THEN** the same single write removes the state the item had and adds the new one, the other labels of the
  call are kept, and a call naming two states on that spelling is refused with a reason naming both

#### Scenario: update#statusReplacementIsIdempotent

- **WHEN** a state write names the state the item already carries as its only state
- **THEN** the item still carries exactly one `status/*` label, and the write does not both add and remove the
  same label

#### Scenario: update#statusRejectsTwoStatesInOneCall

- **WHEN** a call names the item's state twice with conflicting values, through the state flag and through a
  state-namespace label flag together
- **THEN** it is refused before any write, with a reason naming the two values, and no label on the item changes

### Requirement: A state transition reports what it replaced

A state write SHALL report the state labels it removed, including the ones it removed because they were
displaced by the new state, so a caller can see the whole transition and not only the flags it passed.

#### Scenario: update#statusReportNamesImpliedRemoval

- **WHEN** a state write displaces an existing state and the caller asks for machine-readable output
- **THEN** that output names the displaced state label among the removed labels

### Requirement: The suite's stub refuses what the real CLI refuses

The suite SHALL run the issue tooling against a stub `gh` that refuses a shape the real CLI refuses, so a
malformed invocation is red in a case instead of waiting for a person to run the client by hand. An
`gh issue edit` that carries no field to edit SHALL be refused by the stub — a non-zero exit and a message
naming the flags that would make the call legal — and SHALL NOT be recorded as a write, while a call carrying
one of those flags SHALL be recorded exactly as before.

#### Scenario: A bare `gh issue edit` is refused by the stub

- **WHEN** the stub `gh` is asked for `gh issue edit <n>` with none of `--add-label`, `--remove-label` or
  `--body-file`, with the write log enabled
- **THEN** the stub exits non-zero, prints a message naming those flags, and records nothing
- **Case**: `surface#theStubRefusesAnEditWithNoField`

#### Scenario: A legitimate edit is still recorded

- **WHEN** the stub `gh` is asked for `gh issue edit <n> --add-label <label>`
- **THEN** the call exits 0 and the invocation is recorded in the write log unchanged
- **Case**: `surface#theStubRefusesAnEditWithNoField`
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
