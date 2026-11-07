# Spec Delta — agent-issue-loop

## ADDED Requirements

### Requirement: A gate run in a kept worktree runs under the environment that worktree records

`tools/loop/gate.sh` SHALL read the environment file the worktree it runs in records, before it plans its steps, so a
gate a person runs by hand in that tree measures what the run would have measured.

#### Scenario: A hand-run gate sees the worktree's recorded environment

- **WHEN** a worktree carries `.pi/loop-env.sh` with a value in it, and the gate is invoked from that tree — or from a
  subdirectory of it
- **THEN** the gate's own compiled step runs with that value in its environment, and a tree that carries no such file
  gates exactly as before
- **Case**: `gate#runsUnderTheWorktreeEnvironment`
