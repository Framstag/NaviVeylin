# Spec Delta — agent-issue-loop

## ADDED Requirements

### Requirement: The suite states what it cost as well as what it ran

The self-test SHALL report the elapsed time of each group and of the whole selection in its summary, so a case set
that grows is visible where it is paid rather than at the next hand-run. A passing case's `PASS` line SHALL NOT
carry a figure unless the invocation asks for one. The report SHALL be that run's own measurement and SHALL NOT be
compared against a recorded baseline.

#### Scenario: The summary names what each group cost

- **WHEN** a suite of stub cases in two groups is run to completion
- **THEN** the summary carries one line per group with that group's elapsed time and its case count, and one line
  for the selection, and the counts and the exit status are what they were
- **Case**: `driver#theSummaryNamesWhatEachGroupCost`

#### Scenario: A passing case's line keeps its shape

- **WHEN** the same suite runs without `--times`, which is how the mutation harness and the leftovers cases invoke
  the driver
- **THEN** each case's line is its `PASS` and its name and nothing else, and with `--times` the figure is added
  without changing the name the line is read for
- **Case**: `driver#theTimesFigureIsAskedForNotAssumed`

#### Scenario: The figure is this run's own

- **WHEN** a run finishes on a host whose previous run was slower, or faster, by minutes
- **THEN** the summary states the elapsed time this run measured, and no line of it compares that number with a
  stored one — a baseline's disagreement is a disagreement about the host
- **Case**: none — a negative: nothing in the report may read a stored figure, and the summary's own text is the
  reading (`design.md`, "Why not the alternatives")
