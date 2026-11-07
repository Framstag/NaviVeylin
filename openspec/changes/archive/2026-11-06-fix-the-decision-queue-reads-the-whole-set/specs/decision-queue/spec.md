## ADDED Requirements

### Requirement: The queue reads the whole labelled set, and refuses a set it could not cover

The queue SHALL read every item carrying the decision label rather than a fixed number of the tracker's newest
items, and, in the human form, SHALL state the set its count was taken over beside the number of items it lists.
It SHALL refuse, printing no count, a read that came back full at the window it asked for — such a read may have
had items cut off it — and the refusal SHALL name the set the tracker counted and the read it got instead.

#### Scenario: An item older than any fixed page is still listed

- **WHEN** the tracker holds more items than the window the queue used to name, and the oldest item carrying the
  decision label sits outside that window
- **THEN** the queue lists it with the question it carries, beside the newest one, and prints the set its count was
  taken over
- **Case**: `decisions#theOldestQueuedItemIsListed`

#### Scenario: A read that could not cover the set is refused

- **WHEN** the items carrying the decision label outnumber the count the tracker reported before the read
- **THEN** the queue refuses with exit 3, naming the count and the read it got instead, and prints no count of items
  awaiting a decision
- **Case**: `decisions#aReadThatCannotCoverTheSetIsRefused`
