## ADDED Requirements

### Requirement: A case inherits none of the run's own roots

The loop's self-test SHALL drop every root a run exports to its children — the worktree, the tool-result ledger, the
scratch paths and the run's state file — before the first case runs, and SHALL state the drop where it happens. A
case that wants one of those roots SHALL set it on its own command line.

#### Scenario: The suite is started with the run's state file in its environment

- **WHEN** the suite is started with `LOOP_STATE_FILE` naming the record of the run that started it, a path inside the
  tree the suite resolves
- **THEN** no case of that run carries `LOOP_STATE_FILE` in its environment, and the case that holds the drop fails
  naming the variable when the driver hands it over
- **Case**: `driver#theSuiteDoesNotInheritTheRunsRoots`
