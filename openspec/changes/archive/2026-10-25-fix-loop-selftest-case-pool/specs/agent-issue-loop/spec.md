## ADDED Requirements

### Requirement: The self-test runs its cases in a bounded pool, and what a case leaves ends with it

The loop's self-test SHALL run its selected cases in a bounded pool — the worker count the invocation names, else the
profile's, else the host's CPU count — with each case in a process group and a scratch root of its own. A process a
case leaves behind SHALL be ended when that case ends, before its slot is handed on, and the `PASS`/`FAIL` line, the
counts, the summary and the exit status SHALL keep their shape.

#### Scenario: Two cases run at the same moment

- **WHEN** a suite of four stub cases that each record when they start and stop runs without a worker count given
- **THEN** at least two of them were running at the same moment, every case prints its own `PASS` line, and the
  summary counts the whole selection
- **Case**: `driver#theSuiteRunsItsCasesInAPool`

#### Scenario: The pool is bounded by the worker count

- **WHEN** the same suite is run with the pool bounded to one worker
- **THEN** one case runs at a time — the log shows no two of them overlapping — and the same four `PASS` lines and
  the same summary are printed
- **Case**: `driver#theSuiteRunsItsCasesInAPool`

#### Scenario: A process a case leaves behind ends with the case

- **WHEN** a case leaves a process in a group of its own, at a working directory inside the scratch root the driver
  gave it, and the next case then looks for that process
- **THEN** the process is gone before the next case runs, and the suite still ends normally
- **Case**: `driver#aCaseThatLeavesAProcessBehindIsSweptWithIt`
