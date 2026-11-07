## ADDED Requirements

### Requirement: The run's end sweeps the branches it made

When a run ends it SHALL remove every branch the loop names whose patches the base already holds, unless a
worktree holds it or the change it carries has a reviewer's refusal. It SHALL keep and name the branches that
still carry work, and SHALL print what it removed and what it kept.

#### Scenario: A landed branch no longer outlives the run that made it

- **WHEN** a run ends and one of its own `loop-*` branches, or an earlier `fix/issue-*` branch, carries no patch the
  base does not have
- **THEN** the run removes that branch, keeps and names a branch that still carries work, a branch a worktree
  holds, and a branch whose archived change carries a reviewer's refusal, and prints the number it removed
- **Case**: `run#theEndSweepsTheLandedBranchesItMade`
