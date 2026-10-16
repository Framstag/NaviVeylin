# Spec Delta

## ADDED Requirements

### Requirement: A search request carries a generation

The bridge SHALL let a caller begin a search for a named slot, obtain that request's generation, run the search
with it, and cancel it by generation. Cancelling a generation that is not current SHALL report that it aborted
nothing. An aborted search SHALL return an empty result array rather than its partial result.

#### Scenario: Beginning a search returns its own generation

- **WHEN** two searches are begun for the same slot
- **THEN** the second SHALL carry a generation different from the first
- **AND** the first SHALL be aborted

#### Scenario: Cancelling the current generation aborts it

- **WHEN** the caller cancels a search by the generation that is current for its slot
- **THEN** that search SHALL be aborted at its next abort check
- **AND** the cancellation SHALL report that it aborted one search

#### Scenario: Cancelling a stale generation aborts nothing

- **WHEN** the caller cancels a generation that a newer request of the same slot has already replaced
- **THEN** the newer search SHALL keep running
- **AND** the cancellation SHALL report that it aborted nothing

#### Scenario: An aborted search returns no entries

- **WHEN** a search is aborted by a newer request of its slot
- **THEN** the bridge SHALL return an empty array to the aborted caller

#### Scenario: One slot's search does not abort another slot's

- **WHEN** a search is begun for one slot while a search of another slot is running
- **THEN** the running search of the other slot SHALL keep running

#### Scenario: The structured form search is abortable

- **WHEN** a structured form search is aborted while it walks the installed databases
- **THEN** it SHALL stop at its next abort check
- **AND** the databases not yet visited SHALL NOT be searched

#### Scenario: The free-text pass is abortable

- **WHEN** a free-text pass is aborted between two databases
- **THEN** the remaining databases' free-text passes SHALL NOT run
