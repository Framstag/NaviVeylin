# Spec Delta — agent-issue-loop

## ADDED Requirements

### Requirement: An item's kept event stream is bounded

The adapter that keeps a child's event stream SHALL bound the file it appends to at `LOOP_CHILD_EVENTS_MAX_BYTES`.
When the file outgrows its bound the oldest events SHALL be dropped and the newest event SHALL survive. `0`, or a
project that records no key, SHALL keep every line.

#### Scenario: A stream past its bound is rotated and the rotation is recorded

- **WHEN** a child writes more events than `LOOP_CHILD_EVENTS_MAX_BYTES` allows
- **THEN** the file stops growing, its first line is a `stream_rotation` record naming the lines, bytes and
  `turn_end` events that were dropped, the newest event is still the last line, and the dropped plus the remaining
  turns are every turn the child took
- **Case**: `adapter#theKeptStreamIsBoundedAndItsRotationIsRecorded`

#### Scenario: A stream under its bound is kept whole

- **WHEN** a child writes fewer events than the bound allows
- **THEN** every line is kept and no rotation record is written
- **Case**: `adapter#theKeptStreamIsBoundedAndItsRotationIsRecorded`

### Requirement: The count a rotation reports never falls

A rotation SHALL be recorded in the stream itself — how many lines, how many bytes and how many `turn_end` events
went — so a reader can tell a rotated stream from a short one. The count of turns a stream reports SHALL NOT fall
when the writer rotates: the turn bound a child is ended by SHALL count the turns the record says were dropped as
well as the ones the stream still holds.

#### Scenario: A rotated stream still counts the turns it dropped

- **WHEN** the run bounds a child's turns and the child's stream carries a rotation record plus the turns still
  present, and their sum reaches that bound
- **THEN** the run ends the child at the recorded bound, its transcript names the count it reached, and the item is
  recorded `blocked` with the run's own bound as the mechanism
- **Case**: `run#aRotatedStreamStillCountsTheTurnsItDropped`

## MODIFIED Requirements

### Requirement: One command reports and removes what the loop left

The tooling SHALL have one entry point that answers what the loop left on disk and how much it is: without
`--apply` it SHALL name every path it would remove with its size and remove nothing, with `--apply` it SHALL
remove exactly those paths, and a second run SHALL find nothing. It SHALL remove the recorded build paths of a
worktree, the branches whose patches the base already holds, the worktrees under the loop's own tree that the run
does not keep, the scratch trees old enough that no suite can be writing into them — in **both** the scratch
root the invocation resolves and the one the kept worktree resolves — and the item directories no open issue still
cites, and it SHALL NOT touch a worktree's tracked or untracked source. A worktree it keeps SHALL be named with the
reason it is kept: uncommitted changes, or a commit the base does not carry. An item directory SHALL be released
only when nothing inside it has been written for `LOOP_ITEM_ARCHIVE_DAYS` **and** the run recorded the item landed
and its issue closed, and SHALL be kept and named with that reason otherwise; a `0` retention SHALL release none.

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

#### Scenario: An item directory is reported by its age and by what its issue still says

- **WHEN** the report runs over item directories older than `LOOP_ITEM_ARCHIVE_DAYS`, one whose item landed and was
  closed, one whose item landed but could not be closed, one whose item never landed, and one written a minute ago
- **THEN** the landed and closed directory is named with its size and removed only with `--apply`, the other three
  are kept and named with the reason, and a `0` retention releases nothing
- **Case**: `clean#reportsAnAgedItemDirectory`
