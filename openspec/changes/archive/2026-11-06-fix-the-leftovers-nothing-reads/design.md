# Design — the set-aside branch and the unkept worktree, judged and named

## Root cause

Both removal paths build their candidate set out of the names the loop is still writing to:

- `loop_own_branches` (`tools/loop/lib.sh:407`) is `git branch --list 'fix/issue-*' 'loop-*'`, read by
  `cmd_prune_landed` (`tools/loop/worktree.sh:770`) and by `loop-clean.sh`'s branch arm
  (`tools/loop/loop-clean.sh:212`);
- `loop-clean.sh` knows exactly one tree, the one the configuration resolves to
  (`tools/loop/lib.sh:130`), and enumerates no other.

So the two things a run *sets aside* — the branch it renamed to `loop/leftover/issue-<n>-<slug>-<utc>`
(`tools/loop/worktree.sh:298`) and the worktrees of the runs that kept one tree per item — are in no candidate set at
all, and the readings that already exist (patch identity for a branch, what a tree holds) are never asked about them.
A set-aside branch is therefore not protected by the rule; it is invisible to it, and the report that answers "what
did the loop leave" answers nothing about either artefact.

## The single fix

**One reading, applied to the names that were missing.**

1. `loop_own_branches` gains a second `git branch --list` for the set-aside namespace, in a block beside the first
   so the two patterns a sweep already names are untouched:

       {
         git -C "$1" branch --list 'fix/issue-*' 'loop-*' --format='%(refname:short)' 2>/dev/null
         git -C "$1" branch --list 'loop/leftover/*' --format='%(refname:short)' 2>/dev/null
       }

   Nothing else changes in either caller: a set-aside branch is then a candidate of the landed test
   (`branch_is_landed`, four readings, patch identity first), of the refusal a reviewer's verdict is
   (`branch_carries_refused_review`, which only reads `fix/issue-*` and so answers "no marker" for it), and of the
   checked-out refusal — and both tools print the line they already print for a branch they keep or remove.
2. `loop-clean.sh` gains one arm after the branch arm. It walks the worktrees git registers
   (`git worktree list --porcelain`), keeps only those under the directory the run's own worktree lives in
   (`dirname "$LOOP_WORKTREE"`, so an overridden key moves the arm with it), skips the tree the run keeps and the
   tree it was pointed at (`loop_same_dir`, so the two spellings of one directory are one), and asks each of the rest
   what it holds: uncommitted changes, or a HEAD the base does not carry, keep the tree and are named as the reason;
   anything else is named with its size and removed with `git worktree remove --force` only under `--apply`.

## Blast radius

- `tools/loop/lib.sh`: `loop_own_branches` (4 lines) and one comment on the remote arm naming why it stays two
  patterns.
- `tools/loop/loop-clean.sh`: one arm (36 lines with its comment), between the branch arm and the consumed-change
  arm.
- `tools/loop-cases.sh`: 3 cases, their three list entries and their three dispatch entries.
- `tools/loop-mutations.sh`: record `88.1`.
- `guidelines/Backlog.recipes.md`: the sections "Refusals, records and the landing" and "Leftovers, the report and the
  queue", one paragraph each.
- `openspec/`: this change and its delta on `agent-issue-loop` (two requirements modified).
- No configuration key, no state field, no new command, no label and no remote write changes. `--apply` is still the
  only path that removes anything, and a second run still finds nothing.

## Why the worktree arm runs after the branch arm

A landed branch that a tree nobody keeps holds is named `kept … — checked out` by the branch arm and then released by
this one, so in one `--apply` run a reader sees it kept and then the tree removed. That is the honest order: the
other way round the branch arm would delete a branch a live tree holds in the same pass, which is the guarantee
`clean#sweepsOnlyWhatIsInTheBase` asserts, and it would do so under a tree that an item may still be standing in. The
next run finds the branch landed and removes it.

## Alternatives the evidence eliminated

- **A recorded disposition file for a set-aside branch (the item's "superseded" half).** It only ever releases a
  commit nothing has reviewed, which the item's own evidence says is the wrong thing to throw away, and the branch's
  name already carries the whole disposition: the issue, the slug and the UTC the run set it aside. What was missing
  is a reader of that name, not a second file — one mechanism, and the item's own two owed cases are satisfied by it.
  A disposition record would also need a writer in the run's state, a reader in the sweep (which today reads only
  `run.base`), and a migration for the eight branches that carry none.
- **Deleting a set-aside branch whenever the item it names is `done`.** The sweep is deliberately state-free apart
  from the base it reads (`prune_base`, `sweep_base`), so a person's `prune-landed` works in a clone with no run
  state; and a re-landed-as-a-different-commit patch is what `git cherry` already answers.
- **Reporting the plain directories under `.pi/worktrees/` too.** Five of the ten are not worktrees at all, and
  removing a directory tree nothing registers needs the four refusals `loop_scratch_remove` owns for the build
  paths — a second rule, a second case and a second destructive path for one item. They are named in the proposal's
  "what this does not do" instead.
- **Extending the remote arm's patterns.** A set-aside branch is renamed locally and never published
  (`push_branch` publishes an item's branch, not a leftover), so a remote candidate would be a ref the loop never
  made, which the remote arm's own rule refuses. Its two patterns stay two, and the comment now says so.

## Rollback

Revert `loop_own_branches` to its single `git branch --list`, drop the worktree arm, the three cases, the mutation
record and the two guideline paragraphs: both tools enumerate what they enumerated before, the report says nothing
about `.pi/worktrees/`, and no set-aside branch is named. Nothing reads a new field or a new file, so no state has to
be migrated back.

## Open decisions

none
