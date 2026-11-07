## ADDED Requirements

### Requirement: An item the tracker does not answer for is not the run's end

The item the run goes to read SHALL be one candidate, not the run's precondition: a read that does not answer SHALL
be recorded in the run's own state as unreadable — by number, with the tracker's own words — SHALL leave the
queue for this run, SHALL NOT be counted as an item the run spent, and SHALL NOT end the run, which takes the next
candidate and stops only on the queue and the clock it records.

#### Scenario: One unreadable candidate is recorded and the next one is worked

- **WHEN** the tracker refuses to answer for the candidate the run goes to read
- **THEN** the run records that candidate as unreadable with the CLI's own words as its reason, works the candidate
  after it, and ends on its own item count rather than on the failed read
- **AND** the candidate the tracker did not answer for is not counted as an item the run spent, and is no longer
  in its queue
- **Case**: `run#oneUnreadableItemDoesNotEndTheRun`
