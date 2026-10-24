# agent-issue-loop — deltas

## ADDED Requirements

### Requirement: A branch the loop made is removed when its work is in the base

A branch the run committed for an item and merged SHALL be removed once no worktree holds it, a branch whose
patches the base already holds SHALL be named as removable, and a branch that still carries unlanded work
SHALL be left alone and named as work to land.

#### Scenario: A branch the run merged is removed

- **WHEN** the run merges the branch it committed for an item
- **THEN** the worktree is released from that branch and the branch is removed, so a closed item leaves no
  branch behind, and the next item still starts its own branch from the base
- **Case**: `run#aMergedBranchIsRemoved`

#### Scenario: A branch that still carries work is never removed

- **WHEN** the sweep of what the loop left behind meets a branch whose patches the base does not have, or a
  branch a worktree holds
- **THEN** that branch is kept and named with the reason it was kept, and only a branch that carries nothing
  is removed
- **Case**: `clean#sweepsOnlyWhatIsInTheBase`

#### Scenario: The report names the branches that can be removed

- **WHEN** a run ends and the branches it sees carry no patch the base does not have
- **THEN** the report names those branches apart from the ones that carry work, so a reader can tell the
  leftovers from the work without asking git
- **Case**: `report#landedBranchesAreNamedAsRemovable`

## MODIFIED Requirements

### Requirement: One command reports and removes what the loop left

The tooling SHALL have one entry point that answers what the loop left on disk and how much it is: without
`--apply` it SHALL name every path it would remove with its size and remove nothing, with `--apply` it SHALL
remove exactly those paths, and a second run SHALL find nothing. It SHALL remove the recorded build paths of a
worktree, the scratch trees old enough that no suite can be writing into them, and the branches whose patches
the base already holds, and it SHALL NOT touch a worktree's tracked or untracked source.

#### Scenario: The report removes nothing

- **WHEN** `loop-clean` is run without `--apply` on a kept worktree with build output and a scratch root with a
  stale tree
- **THEN** it prints one line per path with its size and a total, and the build output and the stale tree are
  still on disk afterwards
- **Case**: `clean#reportsWithoutApplyAndRemovesWithIt`

#### Scenario: With `--apply` it removes what it reported, and nothing more

- **WHEN** `loop-clean --apply` is run on the same tree
- **THEN** the recorded build paths and the stale scratch tree are gone, a scratch tree younger than
  `LOOP_TMP_STALE_MINUTES` is left (a live suite may be writing into it), the worktree's source files are intact
  and its git state unchanged, and the bytes it removed are the bytes it reported
- **Case**: `clean#reportsWithoutApplyAndRemovesWithIt`

#### Scenario: A second run is a no-op

- **WHEN** a ` --apply` is run again on a tree and a scratch root it has already cleaned
- **THEN** it finds no path to remove, says `nothing to remove`, and exits 0
- **Case**: `clean#reportsWithoutApplyAndRemovesWithIt`

#### Scenario: The branches whose work is in the base are removed with it

- **WHEN** `loop-clean --apply` runs on a repository that holds a branch whose patches the base already has
- **THEN** that branch is removed, a branch that still carries unlanded work and a branch a worktree holds
  are kept and named, and a second run finds nothing left to remove
- **Case**: `clean#sweepsOnlyWhatIsInTheBase`
