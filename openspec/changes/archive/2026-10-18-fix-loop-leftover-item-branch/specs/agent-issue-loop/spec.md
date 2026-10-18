# fix-loop-leftover-item-branch — deltas

## MODIFIED Requirements

### Requirement: Version control belongs to the run's script, not to the agent

The run SHALL create and manage the working tree and the branch of each item itself, and no phase of an
item SHALL be able to invoke version control on its own behalf.

#### Scenario: One working tree per run

- **WHEN** a run starts
- **THEN** it prepares one working tree for the whole run and seeds it with what the project's build and
  test need, so the cold cost is paid once

#### Scenario: One branch per item, from the run's base

- **WHEN** an item starts
- **THEN** it is worked on its own branch from the base commit the run recorded

#### Scenario: A commit carries the run's own identity

- **WHEN** the run commits an item's work on a machine with no `user.name` or `user.email` configured
- **THEN** both commits carry the identity the run already acts as — the login its own gate preflight
  resolved, as that login's noreply address — so the run never depends on an ambient git identity and never
  invents one, and a login that cannot be resolved stops the run with a named reason

#### Scenario: A branch that carries no work of its own is reused

- **WHEN** an item starts and a branch of that item's name already exists, and every commit on it is
  already in the run's base history
- **THEN** the item is worked on that branch, started from the run's base, so an item a previous run left
  blocked can be taken up again

#### Scenario: A branch that carries a previous attempt is refused, named and recorded

- **WHEN** an item starts and a branch of that item's name carries a commit the run's base does not have
- **THEN** the item is refused with the branch and the commits it carries named, and it is recorded as
  awaiting a decision — a previous attempt's work is never thrown away, and a refusal is never silent

#### Scenario: A stale base stops the next item

- **WHEN** the main branch has advanced past the run's base commit
- **THEN** the next item is refused and both commits are named, rather than building on a tree nobody
  reviewed

#### Scenario: Nothing is pushed or opened by default

- **WHEN** a run does not ask to publish
- **THEN** no push and no change request happens, whatever the items did
