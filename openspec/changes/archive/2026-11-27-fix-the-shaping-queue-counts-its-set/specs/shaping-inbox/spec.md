## MODIFIED Requirements

### Requirement: The queue names the verdict that put the item there

The queue verb SHALL list every open item carrying the shaping label and SHALL print, for each, the verdict word and
the clause that produced it from the item's own record — never a summary of it — so a person reads what the run
found rather than reconstructing it.

The verb SHALL count the items the shaping label selects — all states, label-scoped — and SHALL ask the list for a
window one item larger than that count, so the window is the inbox's own size rather than a constant of the tool. A
read that comes back as full as the window SHALL be refused as `shaping-set-uncovered`, naming the counted set and
the read, instead of being reported as coverage of the set. Every listing SHALL print the counted set it was taken
over, so an inbox that found nothing states what it covered.

#### Scenario: The queue prints the item's own statement

- **WHEN** the queue verb runs and an item carries the label with a comment stating its verdict and clause
- **THEN** the item appears with that verdict and clause as the tracker holds them, and the verb exits 0
- **Case**: `shaping#theQueuePrintsTheVerdictAndClause`

#### Scenario: An item carrying the label with no statement is named as such

- **WHEN** an item carries the label and no comment states a verdict
- **THEN** the queue names it as carrying no statement, rather than presenting it as a finding
- **Case**: `shaping#anItemWithNoStatementIsNamed`

#### Scenario: An item outside the newest page is listed

- **WHEN** the tracker holds more items than the window the queue used to ask for, and the oldest of them carries the
  shaping label
- **THEN** that item is listed beside the newest one, the count is the labelled set's own number, and the queue prints
  the set it read
- **Case**: `shaping#anItemOutsideTheNewestPageIsListed`

#### Scenario: A read the counted set cannot cover is refused

- **WHEN** items carrying the label are filed between the count and the read, so the list comes back as full as the
  window it was asked for
- **THEN** nothing is listed and the refusal names `shaping-set-uncovered`, stating the counted set and the read
- **Case**: `shaping#aReadThatCannotCoverTheSetIsRefused`

#### Scenario: An empty queue is not a failure

- **WHEN** no open item carries the label
- **THEN** the verb prints the count as zero and exits 0
- **Case**: `shaping#anEmptyQueueExitsZero`
