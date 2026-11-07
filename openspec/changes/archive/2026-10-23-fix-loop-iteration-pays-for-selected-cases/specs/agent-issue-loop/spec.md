# agent-issue-loop — deltas

## ADDED Requirements

### Requirement: A suite run selects what it was asked for and the gate records what it cost

The loop's self-test SHALL select over its declared cases: `--group <g>` for one group, `--case` for a name or a
pattern, both at once, and `--list` printing the same selection. A filter that names no declared case SHALL be
refused with exit 2 before any case runs. The gate SHALL record each suite's wall clock in seconds, on its own
line, in the `LOOP-GATE:` summary and in the tally.

#### Scenario: A group filter runs that group's cases and no other group's

- **WHEN** the driver is run with `--group <g>` against a declared set of cases in two groups, one of which fails
  whenever it is executed
- **THEN** only the group's cases run, the suite exits 0, and the selection is named in the summary line
- **Case**: `driver#aGroupFilterRunsOnlyItsOwnCases`

#### Scenario: A filter that names no case is refused before a case runs

- **WHEN** `--group <unknown>` or `--case <pattern matching nothing>` is passed
- **THEN** the driver exits 2 naming the group or the pattern it could not resolve, and no case has been executed
- **Case**: `driver#aFilterThatNamesNoCaseRefuses`

#### Scenario: The list answers the same question the run does

- **WHEN** `--list` is asked with a filter, and then without one
- **THEN** it prints exactly that filter's cases, and then the whole declared set, and runs nothing either time
- **Case**: `driver#theListIsFilteredTheSameWayASuiteRunIs`

#### Scenario: The gate's record names what each suite cost

- **WHEN** the gate runs a suite whose own wall clock is known, and it passes
- **THEN** the suite's line, the `LOOP-GATE:` summary beside the command and the tally's `time` attribute all carry
  that number of seconds
- **Case**: `gate#theSummaryNamesWhatEachSuiteCost`
