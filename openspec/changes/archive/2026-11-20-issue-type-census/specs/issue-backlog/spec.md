## ADDED Requirements

### Requirement: An item without an issue type is reported

The metadata check SHALL report every open item that carries no issue type, and the report SHALL NOT set the
check's exit status: the census measures the tracker's shape, it does not refuse an item for lacking the field.

#### Scenario: The census names the untyped items

- **WHEN** the check reads a tracker, live or from a fixture, in which some items carry an issue type and others do
  not
- **THEN** every untyped open item is named with its number, and the typed ones are not
- **AND** the exit status is the one the other invariants set, so 60 legacy items do not turn the check red
- **AND** `--json` carries the same set under one field, so the acceptance number is read with `jq` and not by eye
- **Case**: `check#untypedItemIsReported`

### Requirement: The skill a filer is routed to names the type axis

Every skill the entry document names as the tracker's filing surface SHALL name `--type` and `--parent` beside the
flags it documents, so a filing that follows the skill can carry the tracker's own classification.

#### Scenario: A skill that loses the pointer fails the suite

- **WHEN** `.pi/skills/create-issue/SKILL.md` or `.pi/skills/update-issue/SKILL.md` no longer names both `--type` and
  `--parent`
- **THEN** the issue tooling's suite fails with a case naming the file, not a session discovering it while filing
- **AND** the case reads the files in the tree and needs no token, so it runs in the offline suite
- **Case**: `docs#theFilingSkillsNameTheTypeAxis`

## MODIFIED Requirements

### Requirement: A container's child carries an issue type

An item whose parent is an issue of the profile's container type SHALL carry a non-empty issue type of its own. A child with no type SHALL be a violation the metadata pass reports, naming the child, its container and the type it lacks — because the tracker groups a container's children by type, and an untyped child does not appear under it.

#### Scenario: A typed child is consistent

- **WHEN** an item carries a parent whose type is the profile's `containerType`, and the item carries any declared type
- **THEN** the pass reports no violation for it
- **Case**: `metadata#aTypedChildIsConsistent`

#### Scenario: An untyped child is a violation

- **WHEN** such an item carries no issue type
- **THEN** the pass reports one violation naming the child, its container and the absent type, and exits 1
- **Case**: `metadata#anUntypedChildIsAViolation`

#### Scenario: A child of an ordinary issue is out of scope

- **WHEN** an item's parent is not of the container type
- **THEN** the pass reports no violation about the child's type; the census names it as an untyped item, so a plain
  parent-child between ordinary issues is not this rule's subject
- **Case**: `metadata#aChildOfAnOrdinaryIssueIsOutOfScope`
