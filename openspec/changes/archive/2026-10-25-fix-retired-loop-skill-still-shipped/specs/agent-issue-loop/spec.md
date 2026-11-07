# Spec Delta

## ADDED Requirements

### Requirement: The evidence check is the loop's own tool, and the paths it reads are the project's

The check a change's evidence pointers are verified with SHALL be a tracked tool in the loop's directory, named by
the worker prompt, so a worktree carries it from its checkout without a seed step. The source roots and the tally
directory it reads SHALL be the project's configuration and never a literal in the tool, which is an engine.

#### Scenario: The check scans the roots the configuration names

- **WHEN** the evidence check runs over a tree whose configuration names its own scan roots
- **THEN** a `REVERT-CHECK MUTATION` marker under a configured path is reported, and a configuration that names
  no scan root is refused instead of being read as clean
- **Case**: `evidence#scansWhatTheProfileNames`
