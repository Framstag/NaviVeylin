# Design — a start the worktree refuses mid-run names the item whose work the tree holds

## The root cause

`run.sh` decides what a start refusal means from its **text**, in a `case` with three arms
(`base moved`, `carries commits the base does not`, and a catch-all) and the catch-all records
`$(printf '%s' "$start_out" | head -1)` and breaks. Two facts the run already has, and does not use on that
path:

- the worktree's own branch marker (`.pi/loop-branch`), which names the branch — and so the item — the tree is on:
  `start-item` writes it (`tools/loop/worktree.sh:179`) and the start-time pre-flight already reads it to
  attribute leftovers (`tools/loop/run.sh:380`);
- the tree's own `git status --porcelain`, which is the refusal's whole content and is printed once and then
  dropped.

So the run ends on the one path where the tree still holds somebody's work, and the account of *whose* it is is in
the tree, unread.

## The single fix

One arm, `*"not clean"*`, placed after the two arms the tree can already distinguish and before the catch-all:

1. read the marker and the item it names (`owner_of_marker`, the pre-flight's own reading, now defined once and
   used twice — the pre-flight and this arm);
2. read `git status --porcelain` and flatten it to a file list;
3. record the leftovers under that item and escalate it, in exactly the shape the start-time pre-flight uses
   (`loop_state done <owner> --result blocked --verdict blocked --note … --decision-label …`), because a person
   answering it needs the branch as the evidence and the tracker needs the label;
4. `stop_run` with a reason that names the candidate, the owner item, the branch and the files, then `break`.

Plus one correction in `state.sh` that the arm cannot do without: `done` keeps **one record per item**. A second
account of an item this run already recorded (the normal case — the owner is recorded before the tree is
inspected) joins that record's note and writes the escalation label it earned, instead of appending a second entry.
A duplicate entry is not cosmetic: `report.sh` derives four tallies and `budgetLeft` from `.items` (`report.sh:60`–
`63`, `:54`) and `state.sh next` reads `.items[-2:]` for "two consecutive items ended blocked" (`state.sh:260`), so
one duplicated item is a wrong count in five places and a run that stops for a reason it does not have.

## Why this is one change and not a fork

The item lists what a fix "has to decide", and the tree eliminates every option but one:

- **Skip the candidate and continue down the queue** — eliminated. The tree is still dirty, and neither the engine
  nor the run discards anything without `--reset-worktree` (`tools/loop/run.sh:396`, the repair line the pre-flight
  prints). Every later candidate would be refused identically, so one cause would leave one blocked item per
  candidate — the shape #212 measured: two children that never started, two items recorded `blocked`, two questions
  nobody had asked, and a run stopped on "two consecutive items ended blocked" 3 minutes into an 8 h budget.
- **Commit the leftovers**, which is what `end-item` does for a child's own tree — eliminated by "one writer per
  tree" (`tools/loop/worktree.sh:134`–`139`): the dirt is the *previous* item's unfinished work, and committing it
  onto the candidate's branch attributes two changes to one branch, which is the rule the refusal exists to keep.
- **Reset or clean the worktree in response** — eliminated by the same rule from the other side: the engine
  discards what it names only under `--reset-worktree`, and a refusal is not that flag.
- **Blame the candidate** (record it `blocked` and escalate it) — eliminated: nothing was done to it. It must be as
  eligible afterwards as it was before the pick, which is the reasoning the run already applies to a child that
  never started (`tools/loop/run.sh:930`–`938`, #212).
- **A separate state field for "leftovers of item N"** — eliminated: the report's tallies, its budget-left figure
  and `next`'s "two consecutive blocked" all read `.items`, so the invariant is *one record per item*, not a second
  place to write one.

What remains is one change: attribute, then stop.

## Blast radius

- `tools/loop/run.sh` — one `case` arm (the body and a comment), one helper (`owner_of_marker`) and the pre-flight's
  two lines that now call it. No other arm, no other phase.
- `tools/loop/state.sh` — `cmd_done`'s one `jq` filter: `.items += [ … ]` becomes "append when the item has no
  record, join the note when it has". The nine other `done` call sites are unchanged in behaviour: none of them
  records one issue twice.
- `tools/loop-cases.sh` — one case (array + dispatch + function).
- `tools/loop-mutations.sh` — three records inside `MUTATIONS=( … )`.
- `guidelines/Backlog.md`, section "7. The loop" — one paragraph, and the spec delta's one requirement with one
  scenario.

Nothing reaches `worktree.sh`'s refusal itself: the refusal is right and stays ("one writer per tree"), which is
why the case asserts the *run's* account of it and not the refusal.

## Rollback

The arm and the `state.sh` filter are independent and each is one edit: reverting the arm restores the catch-all
(the case goes red on the stop reason, mutation `53.3`); reverting the filter restores the duplicate entry (the
case goes red on `.items | length`, mutation `53.2`). No data migration: `run.json` is per run and replaced by the
next run (`state.sh init`).

## The case's own staging

The dirt is staged with a `pre-commit` hook the stub installs, so `end-item`'s commits cannot land and an item ends
with its work still in the tree — the state #284 measured (a round-2 correction left uncommitted). How a tree comes
to be dirty is not what the case decides; the sibling change `fix-loop-ended-child-tree-is-not-committed` (open at
this base, not applied here) is what makes that state the *expected* one for a capped child, which is why this arm
had to exist before that one lands.
