# agent-issue-loop delta

## MODIFIED Requirements

### Requirement: A self-test case is held to a bound of its own

The self-test SHALL bound every case it starts with `LOOP_CASE_MAX_SECONDS` (default `900`), measured from the
case's own start. A case that exceeds it SHALL have its process group ended, be recorded
`FAIL <case> (exceeded <n> s)` with its own log kept, and let the suite run the cases after it, so one case that
never returns cannot hold the suite — and the gate behind it — for ever. The value SHALL be validated like the
profile's other bounds.

A case that starts a process and waits for it SHALL bound that wait itself, at a clock measured in seconds of its
own, so a process that never takes the signal the case sent is answered by the case — naming that process and what
the case read — instead of by the suite's bound, which is minutes times the pool's width.

That clock SHALL be derived from the staging the same case measured, and the wait SHALL be decided by the state the
run writes before it: once the run has recorded both the stop the case asserts and its item's result, the rest of the
run's end gets a grace of its own and the wait answers with the status the process ended with, so a run that is already
ending is not held to a deadline. The deadline SHALL be a multiple of that staging, floored at the figure a quiet host
needs and capped, because a host four times slower derives four times the wait while a case may not hold its slot for
minutes. On the deadline the state SHALL be read once more, after the last thing the run writes, and the wait SHALL
answer naming that state, so that a run which recorded everything and did not end is told apart from a trap that wrote
nothing.

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
  the stop and item it read, releases what it held and ends the run so nothing of it outlives the case, and the
  suite reads that case's verdict long before its own case bound
- **Case**: `run#aHupTheRunNeverTookIsAnsweredAtTheCasesOwnClock`

#### Scenario: The stop wait is decided by the state the run writes

- **WHEN** a case waits on a run it signalled under a deadline derived from the staging that same case measured
- **THEN** a host that measured no staging and one whose staging came to 2.9 s get the 30 s a quiet host needs, a
  staging of 8 s derives ten times itself, and a staging of 60 s is held at the cap; a run that recorded its stop and
  its item's result and then ended answers the wait with the status it ended with, naming the stop and the item the
  case read, inside a fraction of the deadline; a run that ended having recorded neither answers at once with its own
  status and the empty state, so the caller's assertion names the hole; and a run that recorded both and was still
  standing is answered `124` with the state it read after the tail grace, because the wait was decided by what the run
  wrote and not by how many ticks elapsed
- **Case**: `run#theStopWaitIsDecidedByTheStateTheRunWrites`
