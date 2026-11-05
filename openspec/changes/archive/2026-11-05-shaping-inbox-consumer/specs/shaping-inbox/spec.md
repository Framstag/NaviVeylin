# shaping-inbox Specification

## ADDED Requirements

### Requirement: The inbox is read by a consumer that proposes and never decides

The project SHALL provide a named consumer of the inbox that reads `tools/issues/shaping.sh` and, for every open
item it finds, proposes either the one mechanism the item lacks or the children it splits into, each with a
`file:line` verified in the tree, and the cost of taking it. It SHALL write nothing until the owner chooses, and
then SHALL write only through the existing writers, so the shaping label leaves in the same write that records the
new shape.

#### Scenario: The consumer proposes from the item's own statement

- **WHEN** the consumer runs and the inbox holds an item whose comment states a verdict and its clause
- **THEN** it proposes one mechanism or a set of children for that item, each proposal naming the `file:line` it
  read, and repeats the verdict and clause the queue printed rather than a summary of its own
- **Case**: `shaping#theConsumerProposesFromTheItemsOwnStatement`

#### Scenario: Nothing is written before the owner chooses

- **WHEN** the consumer has proposed a reshape and no owner has chosen
- **THEN** no label, comment, state and no child item exists that the proposal alone did not ask for, and the item
  still carries the shaping label and its statement
- **Case**: `shaping#theConsumerWritesNothingBeforeTheOwnerChooses`

#### Scenario: The approved reshape leaves through the writers that already exist

- **WHEN** the owner chooses a proposal
- **THEN** each child is filed with `tools/issues/create.sh` — as a tier-A finding when it carries a cause, never
  shape-less — and the item's new shape is recorded by `tools/issues/update.sh`, whose write takes the shaping
  label off, so the queue no longer lists it
- **Case**: `shaping#theApprovedReshapeLeavesThroughTheExistingWriters`
