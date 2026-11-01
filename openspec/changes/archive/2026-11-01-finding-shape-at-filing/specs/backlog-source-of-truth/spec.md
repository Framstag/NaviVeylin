# Spec Delta — backlog-source-of-truth

## ADDED Requirements

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
