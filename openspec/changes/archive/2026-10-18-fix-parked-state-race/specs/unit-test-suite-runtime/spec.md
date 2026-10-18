# unit-test-suite-runtime — deltas

## ADDED Requirements

### Requirement: A published work state is true when it is read

A component that publishes a state for a test to await SHALL publish a state that is true of the moment the
case reads it: work handed to the component SHALL take it out of the state a case treats as idle, the
transition SHALL be made under the lock the reader takes, and a case that must know a piece of work is
*finished* SHALL await the finished work's own state rather than the component's idleness.

#### Scenario: Handed work is not an idle worker

- **WHEN** a case hands the component work that does not signal the worker, after parking it, and then asks
  whether it is parked
- **THEN** the answer is not parked, so a case that reads the component's output on that answer reads it
  after the work was taken up rather than before

#### Scenario: A reader and the transition share one lock

- **WHEN** the transition out of the state and the reader's question are inspected
- **THEN** both happen under the same lock, so no reader observes the state of a moment the worker has
  already left

#### Scenario: A flush is awaited as a flush

- **WHEN** a case must know that a flush has happened before it reads the file
- **THEN** it awaits the flush's own published state — a count of finished batches, which only grows — rather
  than the worker being idle, which is also false while the flush's tail is still pending and which a stale
  flag used to answer before this requirement
