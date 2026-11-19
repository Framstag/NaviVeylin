# Spec Delta — issue-backlog

## ADDED Requirements

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
- **THEN** the pass says nothing about the child's type, so a plain parent-child between ordinary issues is not this rule's subject
- **Case**: `metadata#aChildOfAnOrdinaryIssueIsOutOfScope`

### Requirement: The type axis has a reader in every mode that sees an issue

The invariant SHALL be checked in each mode of the pass that reads individual issues, and SHALL be carried in its `--json` output under the same shape as the other invariants. A field the tracker's client cannot answer SHALL be named as unread rather than read as a clean tracker.

#### Scenario: Both per-item modes check it

- **WHEN** the pass runs over a JSON array and when it runs live
- **THEN** both modes report the same violation for the same item, and neither adds a call to the tracker
- **Case**: `metadata#bothPerItemModesCheckIt`

#### Scenario: The JSON carries the violation

- **WHEN** a run finds an untyped child and is asked for `--json`
- **THEN** the document carries the violation with the item, the container and the invariant's name, beside the other invariants' violations
- **Case**: `metadata#theJsonCarriesTheViolation`

#### Scenario: An unreadable field is named

- **WHEN** the tracker's document carries no type field at all
- **THEN** the pass prints one line saying the type axis could not be read, and does not count it as consistency
- **Case**: `metadata#anUnreadableFieldIsNamed`
