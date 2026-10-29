## ADDED Requirements

### Requirement: A helper that awaits a state fails by naming it

A test helper that awaits an observable state SHALL NOT return the state it holds when its hang-guard window
expires: the expiry SHALL fail the case with a message that names the awaited state and the state that was
observed, so an unmet precondition is never reported at the call site as a count or a value mismatch.

#### Scenario: An awaited state that never arrives fails by name

- **WHEN** a case awaits an observable state through a helper and the helper's hang-guard window expires
  before the state arrives
- **THEN** the case fails with a message naming the awaited state and the state observed at expiry, rather
  than returning a state its call site compares as if the awaited state had arrived
- **Case**: `StartupScreensTest#awaitRowsNamesTheStateThatNeverArrived`
