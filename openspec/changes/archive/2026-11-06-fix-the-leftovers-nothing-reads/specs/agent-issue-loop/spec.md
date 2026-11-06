# Spec Delta — agent-issue-loop

## MODIFIED Requirements

### Requirement: A branch the loop made is removed when its work is in the base

A branch the run committed for an item and merged SHALL be removed once no worktree holds it, a branch whose
patches the base already holds SHALL be named as removable, and a branch that still carries unlanded work
SHALL be left alone and named as work to land. A branch the run renamed aside as a previous attempt's leftovers
SHALL be judged by that same reading — a candidate of both removal paths, never a name nothing enumerates — so it
SHALL be removed once the base carries its patches and SHALL be kept, and named with the work it carries, while the
base does not.

#### Scenario: A branch the run merged is removed

- **WHEN** the run merges the branch it committed for an item
- **THEN** the worktree is released from that branch and the branch is removed, so a closed item leaves no
  branch behind, and the next item still starts its own branch from the base
- **Case**: `run#aMergedBranchIsRemoved`

#### Scenario: The merged branch is gone before the run's report, not by the run's end sweep

- **WHEN** the run merges the branch it committed for an item and prints the report it ends with — the report
  being printed before the run sweeps the branches it left behind
- **THEN** that report names no branch of the item, because the branch is already removed at the merge, so the
  end-of-run sweep is not what a reader is observing and the release's own deletion is falsifiable by itself
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

#### Scenario: A set-aside branch whose work the base carries is swept

- **WHEN** a branch the run set aside as a blocked attempt's leftovers carries only patches the base already holds
- **THEN** both removal paths name it as removable, a read-only run removes nothing, and `--apply` removes it — so a
  set-aside branch whose work was landed by hand does not stay for ever
- **Case**: `worktree#aSetAsideBranchWhosePatchesLandedIsRemoved`

#### Scenario: A set-aside branch that still carries work is kept and named

- **WHEN** the sweep of what the loop left behind meets a set-aside branch whose patches the base does not have
- **THEN** the branch is kept and named as work the base does not have, and its commit is still reachable from it, so
  the attempt's only copy is never the thing a leftovers rule throws away
- **Case**: `worktree#aSetAsideBranchThatCarriesWorkIsNamedNotRemoved`

### Requirement: One command reports and removes what the loop left

The tooling SHALL have one entry point that answers what the loop left on disk and how much it is: without
`--apply` it SHALL name every path it would remove with its size and remove nothing, with `--apply` it SHALL
remove exactly those paths, and a second run SHALL find nothing. It SHALL remove the recorded build paths of a
worktree, the branches whose patches the base already holds, the worktrees under the loop's own tree that the run
does not keep, and the scratch trees old enough that no suite can be writing into them — in **both** the scratch
root the invocation resolves and the one the kept worktree resolves — and it SHALL NOT touch a worktree's tracked
or untracked source. A worktree it keeps SHALL be named with the reason it is kept: uncommitted changes, or a commit
the base does not carry.

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

#### Scenario: A worktree no run keeps is reported, and removed only with `--apply`

- **WHEN** the report runs on a tree that holds the one worktree the run keeps and another worktree under
  `.pi/worktrees/` that no configuration, no state field and no marker names
- **THEN** the tree no run keeps is named with its size and removed only with `--apply`, the tree the run keeps is
  not touched, and a tree holding uncommitted changes is kept in both runs and named with that reason
- **Case**: `clean#reportsAWorktreeNoRunKeeps`

#### Scenario: The kept worktree's own scratch root is scanned beside the invocation's

- **WHEN** the kept worktree carries a stale `loop-selftest.*` tree under its own `.pi/tmp` — where a gate run
  inside the worktree creates one — and a process stands inside it, while the invocation's own scratch root is
  empty
- **THEN** the report names that root as its own line, names the stale tree and the process, keeps a tree of that
  root younger than `LOOP_TMP_STALE_MINUTES`, and `--apply` removes the stale tree and ends the process
- **Case**: `clean#reportsTheWorktreesScratchTreesToo`

#### Scenario: `--worktree .` scans the one root it names once

- **WHEN** `loop-clean --worktree .` is run from the project, so the tree it is pointed at is the one the invocation
  resolves and the two scratch roots are one directory written two ways
- **THEN** the report prints one heading for that root, lists each scratch tree once and counts its bytes once — a
  tree is never listed or counted twice
- **Case**: `clean#worktreeDotSweepsTheOneRootOnce`
