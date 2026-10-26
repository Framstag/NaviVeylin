# Spec Delta

## MODIFIED Requirements

### Requirement: The gate streams a suite's output while it runs

A gate SHALL print each suite's output as the suite writes it and derive the numbers it reports and keeps from that
stream, because a captured output is silent for the suite's whole wall clock, and a red suite's failure text
survives only as the tail of a tally. A step SHALL end with the command it ran, not with a writer that outlived it,
and the suite's exit status SHALL stay the verdict, never the stream's.

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

#### Scenario: A step ends with the command it ran

- **WHEN** a step's command prints its verdict, starts a writer that outlives it, and exits — the shape
  `LOOP_CASE_MAX_SECONDS`'s watcher has when a suite ends before its watchers do
- **THEN** the step ends with its command, so the gate's console loses nothing and the wall clock the step records
  covers the command it ran rather than the writer the command left behind
- **Case**: `gate#aStepEndsWithItsCommandNotWithItsWatchers`
