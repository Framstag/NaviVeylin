## MODIFIED Requirements

### Requirement: The base a run records is the base branch's commit

A run SHALL record the base branch's commit as the base every item descends from, never the tree's checked-out
commit. `LOOP_BASE_REF` SHALL be a key of the project's own configuration, `main` when it names none, written so
an inherited value wins over it. A run whose project tree is on any other branch, or on a detached HEAD, SHALL be
refused before the pre-screen. A stop on a base that no longer matches SHALL name the two commits and SHALL make
no claim about when the base moved.

#### Scenario: A checkout off the base branch is refused before the pre-screen

- **WHEN** a run starts and the project tree is checked out on a branch other than the base branch, or on a
  detached HEAD
- **THEN** it is refused with the branch the tree is on, the base branch and the command that switches to it, and
  the pre-screen is not run, so no item is worked on a tree the landing step cannot merge into
- **Case**: `run#aCheckoutOffTheBaseBranchIsRefusedBeforeThePreScreen`

#### Scenario: A checkout on another branch is not a moved base

- **WHEN** the project tree is checked out on a branch other than the base branch, whose commit is not the base
  branch's commit, and an item starts
- **THEN** the base the run recorded is the base branch's commit, the worktree is cut from it, and the item starts
  instead of being refused as a stale base
- **Case**: `state#recordsTheBaseBranchNotTheCheckout`

#### Scenario: A stale base names both commits and nothing it cannot know

- **WHEN** an item starts and the base branch has moved past the commit the run recorded
- **THEN** the item is refused, the recorded reason names the base branch's commit and the run's recorded base,
  and it states nothing about when the base moved
- **Case**: `run#staleBaseStopsTheRunWithItsReason`

#### Scenario: The base branch is the project's to name

- **WHEN** a project whose base branch is not `main` records it, and an unrelated reader of the engine is
  inspected
- **THEN** the branch is read from that project's `loop.conf` key, no engine file names a branch, and the base the
  run records is that branch's commit
- **Case**: `state#baseBranchIsAConfigurationKey`

#### Scenario: An inherited base branch wins over the recorded one

- **WHEN** a run starts with `LOOP_BASE_REF` set in its environment, and the configuration records another branch
- **THEN** the base the run records is the inherited branch's commit, because the configuration is sourced after
  the environment and fills only what the environment left unset
- **Case**: `state#baseBranchIsAConfigurationKey`
