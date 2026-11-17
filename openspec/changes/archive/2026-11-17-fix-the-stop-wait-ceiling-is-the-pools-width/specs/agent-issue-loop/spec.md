# agent-issue-loop delta

## MODIFIED Requirements

### Requirement: A self-test case is held to a bound of its own

The self-test SHALL bound every case it starts with `LOOP_CASE_MAX_SECONDS` (default `900`), measured from the
case's own start. A case that exceeds it SHALL have its process group ended, be recorded
`FAIL <case> (exceeded <n> s)` with its own log kept, and let the suite run the cases after it, so one case that
never returns cannot hold the suite — and the gate behind it — for ever. The value SHALL be validated like the
profile's other bounds.

A case that starts a process and waits for it SHALL bound that wait itself, at a clock of its own, so a process that
never takes the signal the case sent is answered by the case — naming that process and what the case read — instead of
by the suite's bound, which is minutes times the pool's width.

That clock SHALL be derived from the pool's width and from no duration the host measured: `30` seconds for every case
the suite starts beside the one that waits, so a case in a four-case pool allows `120` seconds and one in a
sixteen-case pool `480`, and a case that runs with no pool named gets the `30` a quiet host needs. The wait SHALL be
decided by the state the run writes before it: once the run has recorded both the stop the case asserts and its item's
result, the rest of the run's end gets a grace of its own and the wait answers with the status the process ended with,
so a run that is already ending is not held to a deadline. The clock SHALL never exceed the bound this requirement
gives the case in the same pool, and every wait such a case spends ending a run it started SHALL use it too, so a run
that is still recording the item the case asserts is not killed at a grace priced on an idle host. On the deadline the
state SHALL be read once more, after the last thing the run writes, and the wait SHALL answer naming that state, so
that a run which recorded everything and did not end is told apart from a trap that wrote nothing.

#### Scenario: A case that never returns is failed by its own bound

- **WHEN** a case in the pool does not return within `LOOP_CASE_MAX_SECONDS`
- **THEN** the suite ends that case's process group, prints `FAIL <case> (exceeded <n> s)` naming the bound and
  the case, keeps that case's log, runs the cases queued after it, and ends with its own summary and a non-zero
  status — instead of waiting for the case
- **Case**: `driver#aCaseThatBlocksIsEndedByItsOwnBound`

#### Scenario: A case that returns inside its bound is not bounded away

- **WHEN** a case finishes before `LOOP_CASE_MAX_SECONDS`
- **THEN** its result is reported as its own exit status decided — `PASS` or `FAIL` from the case, never the
  bound's line — and the suite's own wall clock is not lengthened by the bound
- **Case**: `driver#aCaseInsideItsBoundKeepsItsOwnVerdict`

#### Scenario: A case's own wait ends on the case's own clock

- **WHEN** a case waits on a run it started and the signal it sent reaches no process, so the run goes on and the
  case's own wait would return only when the run ended by itself
- **THEN** the case's own wait answers at a bound of its own within the case, naming the process it waited on and
  the stop and item it read, releases what it held and ends the run so nothing of it outlives the case — waiting for
  the state that run writes under the same clock, so the item's record is not cut short by a grace priced on an idle
  host — and the suite reads that case's verdict long before its own case bound
- **Case**: `run#aHupTheRunNeverTookIsAnsweredAtTheCasesOwnClock`

#### Scenario: The stop wait is decided by the state the run writes

- **WHEN** a case waits on a run it signalled, under the clock the requirement derives from the pool's width
- **THEN** a case with no pool named and one whose pool is reported as `0` allow the 30 s a quiet host needs, a pool of
  four allows 120 s and a pool of sixteen 480 s whatever staging the case measured, and each of those figures stays
  inside the `LOOP_CASE_MAX_SECONDS` bound the case itself is given; a run that recorded its stop and its item's result
  and then ended answers the wait with the status it ended with, naming the stop and the item the case read, inside a
  fraction of the clock; a run that ended having recorded neither answers at once with its own status and the empty
  state, so the caller's assertion names the hole; and a run that recorded both and was still standing is answered
  `124` with the state it read after the tail grace, because the wait was decided by what the run wrote and not by how
  many ticks elapsed
- **Case**: `run#theStopWaitIsDecidedByTheStateTheRunWrites`
