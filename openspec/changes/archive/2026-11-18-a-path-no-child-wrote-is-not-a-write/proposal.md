# Proposal — a path no child wrote is not a write

## Why

Two readings answer "the project tree changed" and neither asks whether a child of this run could have written what
changed.

- **#958:** `run.sh`'s outside-write comparison diffs two `git status` readings of the project tree and reports every
  line. An editor's swap file is such a line. Measured 2026-11-17T10:58Z: #950 ended `outside-write` after 3 670 s
  (61 min) on `?? .mise.local.toml.swp`, 15.9 Mtok spent and 2 commits left unlanded — an open editor window stopped an
  item and the record blamed the child.
- **#962:** `worktree.sh`'s two landing paths refuse to merge while `git status --porcelain` is non-empty, and that read
  counts untracked files. Measured 2026-11-17: `merge-item` refused #960's landing on `?? architecture/bin/` while the
  Java extension was still compiling, and `mv`-ing the directory aside failed because it was recreated inside the same
  command. The landing ran as a hand-written `git merge --ff-only`, which skips the check that exists to stop another
  item's unfinished work being merged over.

Both are the same rule half-applied: the readings exist to catch a *write*, and an editor's temporary or a language
server's output is not one any child of the run made.

## What Changes

1. **One filter, one list.** `lib.sh` gains `loop_is_ignorable_write` / `loop_drop_ignorable_writes` /
   `project_state_differs`, reading `LOOP_WRITE_IGNORE` from the profile with the engine's own default
   (`*.swp *.swo *.tmp *~ .#* .DS_Store */bin */bin/*`), so a project can widen or narrow it.
2. **The run's comparison uses it** (#958): `project_state_diff` filters, and — the half that decides, not only
   describes — the difference is taken as the filter leaves it (`project_state_differs`), so a swap file alone no
   longer produces an `LOOP-OUTSIDE-WRITE-FIRST` line and no longer decides the item.
3. **The landing uses it** (#962): both landing reads are `git status --porcelain -uall` filtered through the same
   function, and only **untracked** entries can be dropped — a tracked file a child modified is a write whatever its
   name. `-uall` because git otherwise collapses an untracked directory to its root and hides the `bin/` inside it.
4. **Three cases and five records.** `run#anEditorSwapFileIsNotAChildsWrite`,
   `worktree#anIdeBuildDirectoryDoesNotBlockTheMerge` and `worktree#aTrackedEditStillBlocksTheMerge` (the guard against
   the filter becoming a licence), with mutation records `29.7` (the filter at the run's comparison), `29.8` (the landing's read), `29.9` (the run's `-uall`) and `29.10` (the landing dropping only untracked entries).

## Scope

`tools/loop/lib.sh`, `tools/loop/run.sh`, `tools/loop/worktree.sh`, `tools/loop/loop.conf` and the self-test. No app
code, no native code, no dependency, no Gradle change, no persisted data.

surface: 2 shipped requirements modified and 1 added — 24 scenarios in 1 delta file (5 added or changed) —
4 helpers and 1 default in `tools/loop/lib.sh`, 1 key in `tools/loop/loop.conf`, 1 row in `tools/loop/shared.tsv`,
5 call sites (`run.sh`'s three, `worktree.sh`'s two), 3 cases, 4 mutation records. Retake with
`bash tools/loop/tests/loop-selftest.sh --from-git 69be8dd5` (484 cases) and
`bash tools/loop/tests/loop-mutations.sh --check` (420 records), or with the profile's gate:
`bash tools/loop/gate.sh --base 69be8dd5`.
