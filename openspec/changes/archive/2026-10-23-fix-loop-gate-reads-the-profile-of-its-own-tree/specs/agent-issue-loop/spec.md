## ADDED Requirements

### Requirement: The gate reads the profile of the tree it measures

The gate SHALL resolve its configuration from the profile beside its own engine — the tree whose paths it measures — and SHALL name that profile in the log it writes, calling a profile read from outside that tree pinned, beside the one the engine ships. A run SHALL resolve its own configuration and SHALL NOT hand it to a child that works in another tree.

#### Scenario: A gate names the profile it read

- **WHEN** the gate runs, once with the profile beside its own engine and once with a profile pinned from outside it
- **THEN** the log carries a `LOOP-GATE-PROFILE:` line naming the file each run read, and the pinned one says it is not the profile beside the engine
- **Case**: `gate#theGateNamesTheProfileItRead`

#### Scenario: A child's gate reads its own tree's profile

- **WHEN** a run starts a worker whose change root is a worktree carrying its own engine, and the worker runs that engine's gate there
- **THEN** the gate names the profile beside the worktree's engine, and the run's own profile is not in the child's environment
- **Case**: `gate#theChildReadsTheProfileOfTheTreeItWorksIn`
