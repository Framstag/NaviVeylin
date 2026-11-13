# Spec Delta — issue-backlog

## ADDED Requirements

### Requirement: The tracker's own classification is a second axis

An item's issue type and its parent issue SHALL be written and read as the tracker's own fields, never as labels; a
type name the account does not declare SHALL be refused before anything is written; and an item whose type the
profile declares a container SHALL NOT be worked, because its children carry that work.

#### Scenario: An issue type and a parent reach the tracker's own fields

- **WHEN** a caller files a finding with `--type Feature --parent 88`, or retypes an existing item with
  `update.sh --type Feature`, or names `.type`/`.parent` in the finding's own JSON
- **THEN** the writer passes them to `gh` as `--type` and `--parent` — the tracker's fields, not labels — so no label
  is created, no namespace is consulted and the vocabulary gate is not entered
- **AND** a call that names neither writes neither, so every existing caller of both writers is unchanged
- **AND** a type name containing a space and a parent that is not an issue number are refused before any write
- **Case**: `create#writesTheTypeItWasGiven`

#### Scenario: An undeclared type name is refused before the item exists

- **WHEN** a caller names a type the account does not declare, or names one the account declares disabled
- **THEN** the writer refuses with `type-opaque`, names the set the account declares, and writes nothing
- **AND** the refusal does not rest on `gh`'s own, because `gh issue create --type <unknown>` files the item and
  reports the allowed set afterwards (measured 2026-11-12: issue #874 is that half-write)
- **AND** a tracker that cannot be read yields the seam's unreachable status rather than an empty list of types, so an
  outage is not read as "no such type"
- **Case**: `create#writesTheTypeItWasGiven`

#### Scenario: A container is not work

- **WHEN** an item is open, authorised and labelled like any candidate, and its issue type is the one the profile
  declares as its container
- **THEN** the pre-screen gives it the verdict `container` and names the type as the reason, before the authorisation,
  state and tier gates, and it reaches no queue
- **AND** an item of any other type in the same tree is work exactly as before
- **AND** a profile whose `roles.containerType` is empty, or that omits the key, refuses nothing on this axis — the
  verdict then belongs to the tracker's shape and not to the project's judgement
- **Case**: `prescreen#aContainerTypeIsNotWork`
