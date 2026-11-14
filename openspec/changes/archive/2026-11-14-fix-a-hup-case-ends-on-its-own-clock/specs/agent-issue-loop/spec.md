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
