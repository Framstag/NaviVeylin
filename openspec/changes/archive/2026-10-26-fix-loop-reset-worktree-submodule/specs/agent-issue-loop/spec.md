## ADDED Requirements

### Requirement: The repair a refusal names repairs the tree it was refused for

`--reset-worktree` SHALL leave a kept worktree that the next start accepts: a submodule whose worktree `HEAD` differs from the gitlink the superproject records SHALL be checked out at that gitlink, and dirt the reset cannot discard SHALL stop the run naming it instead of being reported as a clean tree.

#### Scenario: A submodule left on another commit is checked out at the recorded one

- **WHEN** the only dirt in the kept worktree is a submodule whose worktree `HEAD` differs from the gitlink the
  superproject records, and the run is asked for `--reset-worktree`
- **THEN** the module is checked out at that gitlink, the worktree's own status is empty, and the run starts
- **Case**: `run#resetWorktreeSyncsTheSubmoduleTheResetCannotDiscard`

#### Scenario: The repair names dirt it cannot discard

- **WHEN** dirt that the reset and the module's checkout cannot remove survives both
- **THEN** the run stops with the remaining paths named, and it does not print that the tree is clean
- **Case**: `run#resetWorktreeNamesTheDirtItCannotDiscard`
