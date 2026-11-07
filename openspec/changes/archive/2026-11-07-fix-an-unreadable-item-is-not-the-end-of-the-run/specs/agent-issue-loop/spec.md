# Spec Delta — agent-issue-loop

## ADDED Requirements

### Requirement: An item the tracker does not answer for is unknown, not the end of the run

A read of the item a run is about to work SHALL be read the way the pre-screen reads the same seam's same failure:
as an *unknown* about that item, never as a verdict about the run. When the read fails, the run SHALL record the
item — `blocked`, with the verdict `unreadable` and the CLI's own words — and SHALL continue with the next candidate
in its queue, and SHALL NOT end on that read. The failure it records is the one the seam has already retried and
already reported as `tracker-unreachable`, so a tracker that answers nothing SHALL still end the run, through the
blocked pair the run's own `next` reads, and never through the first item's socket.

#### Scenario: An unreadable item is recorded and the queue moves on

- **WHEN** the tracker fails the read of the item the run selected, with a failure the seam passes through rather than
  retries
- **THEN** the run records that item `blocked` with the verdict `unreadable` and the CLI's own words in the record,
  prints what the CLI said, and ends on its own item bound rather than on that read
- **Case**: `run#anUnreadableItemIsNotTheEndOfTheRun`
