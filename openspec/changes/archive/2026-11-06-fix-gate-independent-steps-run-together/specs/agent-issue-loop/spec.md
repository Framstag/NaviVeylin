# Spec Delta — agent-issue-loop

## ADDED Requirements

### Requirement: The gate runs the steps that share nothing together

The gate SHALL keep the order the profile's lists give it, SHALL run one first step and one compiled step at a time,
and SHALL run the suite steps a delta owes up to `LOOP_GATE_POOL_JOBS` at a time. `LOOP_GATE_POOL_JOBS=1` SHALL be
the serial gate.

#### Scenario: The suites a delta owes run together

- **WHEN** a profile owes three suite steps that share nothing, each of which spends a known wall clock, and the pool
  names a size above one
- **THEN** every step keeps its own output, status, clock and tally, the gate's wall clock is nearer its longest step
  than the sum of the three, and the verdict carries all three steps' numbers in the profile's order
- **Case**: `gate#independentStepsRunTogether`

#### Scenario: A pooled step's numbers are its own

- **WHEN** a pooled wave carries one failing suite between two passing ones
- **THEN** each step's tally carries that step's own case and failure counts, the verdict is their sum, only the
  failing command is named in the `— failing:` list, and the gate exits non-zero
- **Case**: `gate#aFailingSuitesTallyIsUnchangedByItsNeighbours`

## MODIFIED Requirements

### Requirement: The gate streams a suite's output while it runs

A gate SHALL print a step's output as the step writes it and derive the numbers it reports and keeps from that
stream, because a captured output is silent for the suite's whole wall clock, and a red suite's failure text
survives only as the tail of a tally. That stream SHALL be live where nothing can interleave with it — a step the
engine runs alone, and a wave of one — and a wave of several steps SHALL print each step's output whole, under that
step's own `gate: <command>` header, once the wave has drained: two writers on one console interleave their bytes,
and a failing case would then belong to the step that happened to print next. A step SHALL end with the command it
ran, not with a writer that outlived it, and the suite's exit status SHALL stay the verdict, never the stream's.

#### Scenario: A suite's output reaches the console before the suite exits

- **WHEN** the gate runs a suite that writes a line at once and keeps working after it, alone
- **THEN** that line is on the gate's console while the suite is still running, and a line the suite has not
  written yet is not — so a suite that runs for minutes shows its own progress instead of a two-line log an
  operator cannot tell from a stopped one
- **Case**: `gate#theSuitesOutputReachesTheConsoleWhileItRuns`

#### Scenario: A wave of several prints each step whole when it ends

- **WHEN** the gate runs two or more suites in one wave, each writing a line at once and keeping working after it
- **THEN** nothing of either step's output is on the console while the wave runs, and when the wave has drained each
  step's output is there whole, between that step's own `gate: <command>` header and its own `— <n>s` line, in the
  profile's order — so a failure and the step that spent it are read from one block no neighbour's bytes crossed
- **Case**: `gate#aPooledWavePrintsEachStepWholeWhenItEnds`

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
