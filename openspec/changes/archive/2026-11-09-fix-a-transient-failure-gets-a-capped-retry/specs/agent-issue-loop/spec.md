# Spec Delta — agent-issue-loop

## ADDED Requirements

### Requirement: A child that never started is retried before the run stops

A failure whose cause lies outside the item SHALL NOT end the run on its first occurrence. The run SHALL spend a
recorded, capped retry on the class before it records its own stop, naming each attempt, the delay it waited and the
keys it read them from. The cap (`LOOP_RETRY_MAX`, `LOOP_RETRY_DELAYS`) SHALL be one budget per transient class per
run, and `LOOP_RETRY_MAX=0` a project that records no retry. The run's own `state.sh` stops SHALL stay final.

#### Scenario: A child that never started is retried and the item is then worked

- **WHEN** a child ends without a verdict line and leaves the item's tree at the run's base commit, and the retry cap
  has not been spent
- **THEN** the run waits the delay the configuration records for that attempt, re-runs the item with the same work
  package — the failed attempt read nothing of it — and names the attempt and the keys it read in the line it prints,
  so the item is worked instead of the window being spent on one transient refusal
- **Case**: `run#aChildThatNeverStartedIsRetriedBeforeTheRunStops`

#### Scenario: The retry cap is a cap

- **WHEN** the class keeps failing past `LOOP_RETRY_MAX` retries
- **THEN** the run stops exactly as it did before the retry existed — recording its own stop reason with no item
  record and no escalation — and that reason names the retries it spent and the keys it read them from
- **Case**: `run#theRetryCapIsSpentAndTheRunStops`
