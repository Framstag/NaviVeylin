# Spec Delta — decision-queue

## ADDED Requirements

### Requirement: An option states what the owner gets, whether it solves it, and its risk

Every option an answer lays out SHALL state the deliverable it produces, whether it closes the observation the item
states — full, partial or no, with the residue named — and the observable that would show it worked. Its risk SHALL be
a level with the clause that sets it, plus what happens if it is wrong and unnoticed, who it hits, whether it can be
undone and what would catch it. Deciding nothing SHALL be priced beside the options.

#### Scenario: An option states what the owner ends up with

- **WHEN** an option is laid out for the owner
- **THEN** it states the deliverable in the owner's words, before the mechanism that produces it
- **Case**: `decisions#everyOptionSaysWhatTheOwnerGetsAndWhatItRisks`

#### Scenario: An option says whether the observation is closed

- **WHEN** an option is laid out
- **THEN** it says full, partial or no, and names what remains broken, parked or asked
- **Case**: `decisions#everyOptionSaysWhatTheOwnerGetsAndWhatItRisks`

#### Scenario: A risk is a level with its clause and the four fields

- **WHEN** an option's risk is stated
- **THEN** it carries the level, the clause that sets it, what happens if it is wrong and unnoticed, who it hits,
  whether it can be undone and what would catch it
- **Case**: `decisions#everyOptionSaysWhatTheOwnerGetsAndWhatItRisks`

#### Scenario: Deciding nothing is priced

- **WHEN** the options' risks are compared
- **THEN** a row for deciding nothing carries what continues while the item waits
- **Case**: `decisions#everyOptionSaysWhatTheOwnerGetsAndWhatItRisks`

### Requirement: The answer is presented once, verdict first, and the listing answers what waits

The reader SHALL present one sheet in one message after a single batched read pass, with the fork, the
recommendation and what would prove it solved above the item's state and options, and SHALL report no progress between
the reads. It SHALL ask once, in a question whose choices name what each option gets and whose context carries the
risks. Asked which decisions wait, it SHALL print the listing and ask nothing; one waiting item SHALL be answered
without a question.

#### Scenario: The sheet leads with the verdict

- **WHEN** the sheet is laid out
- **THEN** the fork, the recommendation and what would prove it solved stand above the item's state and its options
- **Case**: `decisions#theSheetLeadsWithTheVerdict`

#### Scenario: The sheet is the first thing after the reads

- **WHEN** the reader has read the item and verified its claims
- **THEN** the next thing the owner sees is the sheet, and no turn reports progress in between
- **Case**: `decisions#theSheetIsTheFirstThingAfterTheReads`

#### Scenario: The listing is the answer to what waits

- **WHEN** the owner asks which decisions are waiting, or exactly one item waits
- **THEN** the queue is printed and stopped at, or the single item's sheet is presented, and no question is asked
  about which item
- **Case**: `decisions#theListingIsTheAnswerToWhichAreWaiting`
