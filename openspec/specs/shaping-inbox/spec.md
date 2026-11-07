# shaping-inbox Specification

## Purpose
The state an item carries when a run's verdict was that a person must reshape it rather than answer a question: one
label, one queue that names the verdict and the clause behind it, and no count against the decision cap — so an
item the loop cannot work is visible as work a person owes, instead of ending in a hold nobody can answer.

## Requirements

### Requirement: A shaping verdict carries the shaping label

The run SHALL write the project's shaping label, in the same call as the hold and the one line accounting for it,
for every verdict its profile names as a shaping verdict — the answers "this is not a falsifiable acceptance
condition", "this exceeds one change" and "this is not spec-shaped". A verdict the profile names as a shaping
verdict is one the run holds rather than escalates, so a verdict that states a question is never one: `needs-diagnosis` is escalated
with the decision label, writes no shaping label, and the labels that skip leaves are the set the profile names for
it. A verdict the profile does not name SHALL write no such label.

#### Scenario: A shaping verdict writes the label beside the hold

- **WHEN** the run skips an item with a verdict the profile's `roles.shapingVerdicts` names
- **THEN** the item carries the hold state **and** the shaping label, and the comment written in the same call states
  the verdict and the clause of evidence that produced it
- **Case**: `run#aShapingVerdictWritesTheLabel`

#### Scenario: A verdict the profile does not name writes nothing

- **WHEN** the run skips an item with a verdict outside `roles.shapingVerdicts`
- **THEN** the item keeps the bare hold it writes today, and no shaping label appears
- **Case**: `run#aVerdictOutsideTheListWritesNoLabel`

#### Scenario: The labels a skip leaves are the set the profile names

- **WHEN** the run skips an item with `needs-diagnosis`, which this project's `roles.shapingVerdicts` does not name
- **THEN** the write that records the skip adds exactly the labels the profile's list implies for that verdict —
  the decision label and the hold, and a shaping label only if the list names it — so the labels the item carries and
  the declaration are one answer
- **Case**: `run#aNeedsDiagnosisSkipCarriesTheLabelsTheProfileNames`

#### Scenario: A project that names no verdict declares the list empty

- **WHEN** the profile declares `roles.shapingVerdicts` as an empty list
- **THEN** no run writes the label, and the queue verb reports an empty queue rather than an error
- **Case**: `shaping#anEmptyListIsAnEmptyQueue`

### Requirement: The queue names the verdict that put the item there

The queue verb SHALL list every open item carrying the shaping label and SHALL print, for each, the verdict word and
the clause that produced it from the item's own record — never a summary of it — so a person reads what the run
found rather than reconstructing it.

#### Scenario: The queue prints the item's own statement

- **WHEN** the queue verb runs and an item carries the label with a comment stating its verdict and clause
- **THEN** the item appears with that verdict and clause as the tracker holds them, and the verb exits 0
- **Case**: `shaping#theQueuePrintsTheVerdictAndClause`

#### Scenario: An item carrying the label with no statement is named as such

- **WHEN** an item carries the label and no comment states a verdict
- **THEN** the queue names it as carrying no statement, rather than presenting it as a finding
- **Case**: `shaping#anItemWithNoStatementIsNamed`

#### Scenario: An empty queue is not a failure

- **WHEN** no open item carries the label
- **THEN** the verb prints the count as zero and exits 0
- **Case**: `shaping#anEmptyQueueExitsZero`

### Requirement: The shaping state is neither a hold nor a decision

The shaping label SHALL NOT be the project's decision label, SHALL NOT count against the run's decision ceiling, and
SHALL NOT be written by the escalation path, because the item states no question and an answer to a question nobody
asked is the defect the decision queue was built to end.

#### Scenario: A shaping item does not stop the next run

- **WHEN** the number of items carrying the shaping label exceeds the run's configured decision ceiling
- **THEN** the run starts, because the ceiling counts only the decision label
- **Case**: `run#theShapingInboxDoesNotCountAgainstTheCeiling`

#### Scenario: The decision queue does not list a shaping item

- **WHEN** an item carries the shaping label and not the decision label
- **THEN** `tools/issues/decisions.sh` does not list it
- **Case**: `shaping#theDecisionQueueIgnoresTheShapingLabel`

#### Scenario: Answering an item that awaits no decision is refused

- **WHEN** `tools/issues/answer.sh` is called on an item carrying the shaping label only
- **THEN** it refuses as it does for any item that awaits no decision, and points at the queue that does hold it
- **Case**: `shaping#answeringAShapingItemIsRefused`

### Requirement: Leaving the inbox is an existing writer's act, and takes the label off

Each way out of the inbox SHALL go through the tooling's existing writers, and the write that records the outcome
SHALL remove the shaping label in the same call: an item reshaped into a tier-B filing, an item closed as a decision
not to act, or an item whose verdict a person answers as a decision instead.

#### Scenario: A reshaped item loses the label as it is filed

- **WHEN** a session reshapes an item into a tier-B filing and updates it
- **THEN** the label is removed by the same `update.sh` call that records the shape, and the queue no longer lists it
- **Case**: `shaping#aReshapedItemLeavesTheQueue`

#### Scenario: A closed item leaves no label behind

- **WHEN** an item carrying the label is closed
- **THEN** `tools/issues/close.sh` removes the label in the write that records the settled state, so the queue does
  not keep a closed item for ever
- **Case**: `shaping#aClosedItemLeavesNoLabel`

#### Scenario: A label the vocabulary does not hold is refused

- **WHEN** a write names the shaping label and the profile does not declare it
- **THEN** the write is refused with `label-opaque` by the gate every writer already passes, and no label is created
- **Case**: `shaping#anUndeclaredLabelIsRefused`

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
