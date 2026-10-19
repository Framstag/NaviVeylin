# decision-queue — deltas

## ADDED Requirements

### Requirement: The queue names what awaits a decision and what each item asks

The tooling SHALL list every item that carries the decision label and SHALL print, for each, the statement the
item itself carries — the question the escalation recorded — rather than a summary of it. An item whose escalation
left no statement SHALL be named as carrying none, so a boilerplate escalation is visible instead of passing as a
question.

#### Scenario: The queue prints the recorded question

- **WHEN** the queue is asked for the items that await a decision
- **THEN** one entry per item carrying the decision label is printed, each with the item's number, its title and
  the question its own last statement carries
- **Case**: `decisions#listsTheQueueAndItsQuestions`

#### Scenario: An item with no recorded statement is named as such

- **WHEN** an item carries the decision label but no comment
- **THEN** the queue prints it and says that no question is recorded for it
- **Case**: `decisions#listsTheQueueAndItsQuestions`

#### Scenario: An empty queue is not a failure

- **WHEN** no item carries the decision label
- **THEN** the queue says so and exits 0, because a stopped clock is not a failed clock
- **Case**: `decisions#emptyQueueIsNotAnError`

### Requirement: An answer is recorded on the item before any state changes

A decision SHALL be recorded as a comment on the item before the state that marks the item as awaiting it is
touched, and an answer that names no decision SHALL write nothing. The order is what makes the record readable when
a later step fails: the state alone is the defect, the answer alone is a decision nobody has applied.

#### Scenario: The answer lands first

- **WHEN** a decision is answered
- **THEN** the decision is posted as a comment on the item and only then are the labels changed
- **Case**: `answer#continueReturnsTheItemToTheQueue`

#### Scenario: An answer without a decision is refused

- **WHEN** the answer verb is called with no decision text
- **THEN** it stops as a usage error and writes nothing
- **Case**: `answer#requiresTheDecisionText`

### Requirement: An answer performs the transition it implies

The answer verb SHALL perform the transition the answer implies, and only that one: returning the item to the
queue, moving it to the device lane, or closing it as a decision not to act. A closure SHALL carry a reason and its
evidence, and every write SHALL go through the tooling's existing writers, so the reserved-label refusal and the
closed vocabulary apply unchanged.

#### Scenario: Continuing returns the item to the queue

- **WHEN** the answer continues the work
- **THEN** the decision label is removed and the hold is replaced by the queue's own state, so the next run may
  take the item
- **Case**: `answer#continueReturnsTheItemToTheQueue`

#### Scenario: A device answer moves the item to the device lane

- **WHEN** the answer says the missing evidence needs a device
- **THEN** the decision label is removed and the item carries the profile's device state, which the pre-screen
  reports as the device verdict rather than as work
- **Case**: `answer#deviceMovesToTheDeviceLane`

#### Scenario: A decision not to act closes the item

- **WHEN** the answer closes the item as not to be done
- **THEN** the decision label is removed and the item is closed in the settled state a person's decision records,
  with its reason and evidence
- **Case**: `answer#retireClosesAsRetired`

#### Scenario: A closure needs its evidence

- **WHEN** a decision not to act is answered without evidence
- **THEN** the answer verb refuses before writing anything
- **Case**: `answer#retireClosesAsRetired`

### Requirement: An item that awaits no decision cannot be answered

The answer verb SHALL refuse an item that does not carry the decision label, because answering is not the way to
re-open a refuted item or to close a fixed one — those have their own paths and their own gates.

#### Scenario: A hold is not answerable

- **WHEN** the answer verb is given an item that is on hold without the decision label
- **THEN** it refuses with the reason and writes nothing
- **Case**: `answer#refusesWhatIsNotADecision`

### Requirement: An escalation carries the question with its options

An escalation SHALL carry the question, so its reader can decide from the item alone. The worker's report SHALL
state the question, the options in their order and the recommendation, and the escalation the run writes SHALL
include it beside the reason and the evidence it already records.

#### Scenario: A skip that reaches a decision carries it

- **WHEN** a worker's skip verdict is one that needs a decision
- **THEN** the escalation comment on the item contains the question the worker stated
- **Case**: `run#skipEscalationCarriesTheDecision`

#### Scenario: The worker prompt requires the field

- **WHEN** a worker reads its prompt
- **THEN** the fields the run reads back include the decision, with what it must contain
- **Case**: `prompt#workerNamesTheDecisionField`
