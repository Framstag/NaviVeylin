# Agent issue loop

## MODIFIED Requirements

### Requirement: The gate streams a suite's output while it runs

A gate SHALL print each suite's output as the suite writes it, and derive the numbers it reports and keeps
from that same stream, because a captured output is silent for the suite's whole wall clock — up to the
child's bound — and a red suite's failure text survives only as the tail of a tally. The suite's exit status
SHALL stay the gate's verdict, never the stream's.

#### Scenario: A suite's output reaches the console before the suite exits

- **WHEN** the gate runs a suite that writes a line at once and keeps working after it
- **THEN** that line is on the gate's console while the suite is still running, and a line the suite has not
  written yet is not — so a suite that runs for minutes shows its own progress instead of a two-line log an
  operator cannot tell from a stopped one
- **Case**: `gate#theSuitesOutputReachesTheConsoleWhileItRuns`

#### Scenario: The console and the tally are one observation

- **WHEN** a suite fails and writes its failing case and its own summary
- **THEN** the gate's verdict is that suite's exit status, the summary's numbers are the `tests=`/`failures=` it
  reports, and the tally under the configured directory carries those numbers together with the failure text —
  so what an operator saw and what the run's own check reads are the same bytes, not two observations
- **Case**: `gate#aFailingSuiteIsReadFromTheSameStream`

#### Scenario: A suite's line is read from the stream, not at an instant

- **WHEN** a case that guards the stream reads the gate's console while the gate is still working, to assert that
  a suite's line has reached it
- **THEN** it waits, within a bound, for the line the suite itself prints, and does not read at an instant fixed
  before the gate can have reached the suite — because the profile reads, the `git` calls over the tree and the
  de-duplication of the owed commands all precede the suite, and a fixed instant reports their cost as a verdict
  about the stream
- **AND** the wait does not soften the guard: the console of a gate that captured the suite's output and replayed
  it after the command returned still fails the assertion, because the moment the waited-for line is read then
  already carries the summary the suite had not printed yet
- **Case**: `gate#theSuitesOutputReachesTheConsoleWhileItRuns`
