# Design — the refused landing is driven through the run

## The question the item asks

The record a refused landing leaves is pinned at both of its reading ends (`report.sh:875-917` reads it, `883.2`;
`state.sh:994-1000` writes it, `883.3`) and at no point in between is the *run* asked to produce it. The change is
therefore not a change of behaviour — `run.sh`'s arm is correct as it stands — but the case the arm owes, and the
question it forces is how a case reaches that arm at all.

## Decision 1 — how a case reaches a real refusal of the landing

**Chosen: a two-item run, whose first item leaves the difference in the project's tree and whose own landing merges
over it.** The first item's readings see the difference and the run places it in no process of its own, so its landing
is handed `--outside-write-decides no` (`run.sh:502-507`) and merges over it, leaving the paths uncommitted in the tree
(`worktree.sh:651-663`). The second item's readings then *open* on those paths: `project_state_diff` of two equal
readings is empty (`run.sh:1482-1484`), so no reading is handed to its landing, `landing_decides` is empty, and
`landing_refuses_the_dirt` refuses (`worktree.sh:653`, `665`) — the refusal `run.sh:516-521` records. Nothing is timed,
nothing is raced: the state the refusal reads is one the run itself left standing two items earlier.

The alternatives the tree eliminated, each by a measurement and not by an argument:

- *The deleted case's shape: one item whose child dirties the project's tree* (`a30a3305^`,
  `case_run_doesNotCloseOnAMergeTheBaseCannotTake`). Eliminated — since #862 that difference is one the run places in
  nothing, and `run.sh:506-507` hands the landing `decides=no`: the merge is taken over it and the item closes. That
  arm is `run#mergesOverADifferenceItCannotPlace`, which is the case that measured the old shape away.
- *`--strict-outside-write yes` with the same single item.* Eliminated — `outside_write_decides` is true for that run,
  so `run.sh:3657` blocks the item with the mechanism `outside-write` *before* the merge is asked; the landing never
  runs. `run#strictOutsideWriteBlocksTheItem` asserts that note today.
- *A write the run can place — a process of the child's own tree standing in the project.* Eliminated — the same
  `run.sh:3657` arm, for the same reason (`run#aWriterTheRunCanPlaceBlocksTheItem`).
- *A branch that does not descend from the recorded base* (`worktree.sh:747-748`). Reachable, and it names a branch —
  but it names **no path**, so a case driving it could assert neither the paths half of the record nor "every path the
  refusal named", and `worktree#refusesToReplayABranchTheBaseDoesNotDescendFrom` already owns that refusal.

Blast radius: `tools/loop/tests/loop-cases.sh` (one case), `tools/loop/tests/loop-mutations.sh` (one record),
`guidelines/Backlog.recipes.md` section 7d (one sentence), and the delta spec. No engine file changes, so no behaviour moves.

Rollback: the case and the record are additive, and the guideline sentence names them; reverting all three leaves the
tree as it is at `7ecab8e6`, with `58.2` naming a case the tree does not hold — the defect the item records.

## Decision 2 — the name the case carries

**Chosen: the name record `58.2` already holds, `run#doesNotCloseOnAMergeTheBaseCannotTake`.** The record mutates
`run.sh:4099` (`block unmergeable "`), which is the one line the arm writes the mechanism with, and a record whose case
is absent is a red case that never ends green: on HEAD, `loop-mutations.sh --only 58.2` prints `RED` and then
`RESTORE-FAILED … stayed red after the restore`. Naming the new case after the record's own name repairs that record
without a second record for the same line — the item's cost estimate, one case and one record, is what the tree buys.

- *Alternative: name it after what it asserts and repoint `58.2` at the new name.* Refused — the record's line would
  then be one edit nothing forces, and its own `--check` pass does not read case names, so a second rename would
  silently strand it again. The name the record carries is the name the case has; the record and the case move
  together or not at all.
- *Alternative: leave `58.2` naming the deleted case and add a fresh record for `run.sh:4099`.* Refused — two records
  for one line, one of them stale, and the item's subject is a record that asserts nothing.

## Decision 3 — which half the new record mutates

**Chosen: `tools/loop/run.sh:4343`, the guard that hands the refusal to the state (`883.4`).** It is the one line
between the refusal's two values and the record, and mutating it to `if false; then` removes both halves at once, so
the case's branch assertion and its every-path assertion both go red. The mechanism word is `58.2`'s half and stays
`58.2`'s.

- *Alternative: mutate the path accumulation at `run.sh:519-521`.* Refused — it isolates the paths half while the
  branch half would then be pinned by no record at all, and the two halves are one fact: `state.sh:883-891` refuses a
  path without a branch beside it.
- *Alternative: mutate `worktree.sh`'s refusal itself.* Refused — that is the landing's own arm, already pinned by
  `worktree#theLandingDecidesByTheReadingItIsHanded`; what is unpinned is the run's hand-off, not the refusal.

## What the case cannot prove, and says so

The two-item shape is the only reachable one, so the case's first item proves the merge-over arm as the price of
reaching the second item's refusal. That is not a second subject: the case asserts the first item closed and that the
path it merged over is still uncommitted, because those two facts are what make the second landing refuse.
