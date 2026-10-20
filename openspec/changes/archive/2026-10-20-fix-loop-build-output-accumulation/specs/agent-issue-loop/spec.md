# agent-issue-loop — deltas

## ADDED Requirements

### Requirement: A kept worktree carries the sources, not the build output

A run that keeps its worktree SHALL remove the module build directories the project records, so the tree the
next run inherits is a checkout whose next build regenerates what it needs — not a growing archive of every
build the loop has paid for. Which directories those are SHALL be recorded configuration, never a path in an
engine, and the removal SHALL stay inside the worktree: it SHALL NOT follow a symlink out of it and SHALL NOT
remove a path git tracks.

#### Scenario: The run removes the recorded build directories from the tree it keeps

- **WHEN** a run ends, keeps its worktree, and the tree carries a directory the project recorded in
  `LOOP_WORKTREE_SCRATCH_PATHS` (measured: 1.9 GB of `app/build` in a 3.2 GB kept worktree)
- **THEN** the run removes that directory before it reports the tree as kept, the change's source files and the
  item's branch and commit are still there, the tree is clean, and the run prints the bytes it removed
- **Case**: `worktree#keepsSourcesNotBuildOutput`

#### Scenario: The removal stays inside the worktree and leaves what is not build output

- **WHEN** a recorded path inside the kept worktree is a symlink whose target lies outside the tree, or a path
  git tracks
- **THEN** the path is left exactly where it is and the report says why, and the file outside the tree is not
  touched — so a kept worktree loses what its next build regenerates and nothing else
- **Case**: `worktree#keepsSourcesNotBuildOutput`

#### Scenario: The list of build directories is the project's, not the engine's

- **WHEN** the loop needs to know which directories a kept worktree loses
- **THEN** it reads them from `LOOP_WORKTREE_SCRATCH_PATHS` in its configuration, and no script or prompt under
  `tools/loop/` names one, so the same engine cleans another project's tree by that project's list
- **Case**: `run#engineNamesNoProjectFact`

### Requirement: A suite ended by a signal removes its scratch tree

The loop's self-test SHALL create its scratch tree under the loop's recorded temporary root and SHALL remove
it when a signal ends the suite, exactly as it does when a case fails — and it SHALL NOT report a run it did not
finish as a pass. `SIGKILL` cannot be trapped; the trees it leaves SHALL be the leftovers the clean command
reports.

#### Scenario: A suite interrupted in the middle of a case leaves nothing behind

- **WHEN** a case is running (a case that sleeps is what that looks like) and the suite receives `TERM` (and the
  same holds for `INT` and `HUP`)
- **THEN** the scratch tree under the recorded root is gone, and the suite's exit status is not 0 — an
  interrupted run never reads as a pass
- **Case**: `clean#interruptedSuiteRemovesItsScratchTree`

### Requirement: One command reports and removes what the loop left

The tooling SHALL have one entry point that answers what the loop left on disk and how much it is: without
`--apply` it SHALL name every path it would remove with its size and remove nothing, with `--apply` it SHALL
remove exactly those paths, and a second run SHALL find nothing. It SHALL remove only the recorded build paths
of a worktree and only scratch trees old enough that no suite can be writing into them, and it SHALL NOT touch
a worktree's tracked or untracked source.

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

- **WHEN** ` --apply` is run again on a tree and a scratch root it has already cleaned
- **THEN** it finds no path to remove, says `nothing to remove`, and exits 0
- **Case**: `clean#reportsWithoutApplyAndRemovesWithIt`
