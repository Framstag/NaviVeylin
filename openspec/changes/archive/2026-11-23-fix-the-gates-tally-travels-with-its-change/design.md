# Design — the gate's record is carried by the step that commits the change

## The root cause

The gate writes its own record where the profile sends it: `tools/loop/loop.conf:903` records
`LOOP_GATE_TALLY_DIR="app/build/test-results/loop-gate"`, `tools/loop/gate.sh:115` writes one tally per step and its
own `gate.log` there, and the next item's start clears that directory — `clear_stale_test_results`
(`tools/loop/evidence.sh:768`), called for every worker (`tools/loop/child.sh:340`) — so no item is blamed for a
previous item's results, and nothing that stands there outlives the item that wrote it.

That directory is not the change. `carried_verification` (`tools/loop/worktree.sh:554`) reads a branch's
verification out of `openspec/changes/archive/<change>/evidence/`, `cmd_end_item` (`tools/loop/worktree.sh:486`)
commits the change and its evidence, and **nothing joined the two**. The convention that the worker copies the
gate's tally into its own `evidence/` is written in neither the worker's prompt nor any check, so a finished attempt
whose gate passed and whose worker copied nothing carried its red-on-HEAD record and no green one — and the next
run read that branch `archived-not-green`, escalated it as an owner decision and spent one of the ten slots the
run's start is capped by (measured 2026-11-21, item #964: `archived-not-green` while its own gate log read
`LOOP-GATE: tests=342 failures=0` — one command's output, its XML not retained).

## The single fix

One function, `carry_gate_record` (`tools/loop/worktree.sh:456`), called first in `cmd_end_item`
(`tools/loop/worktree.sh:491`) — before either of the two commits it makes:

1. It reads the tally directory the profile names, and returns when the gate left no `gate.log` there or when that
   log's last `LOOP-GATE:` line is not green (`gate_tally_state`, `tools/loop/lib.sh:127`). A gate that did not pass
   carries nothing, so its red tally stays the record of the item it blocks and no unverified attempt is turned into
   a verified one.
2. It names the archived change this item just made: every directory under `openspec/changes/archive` holding a file
   git does not track — `end-item` is that change's only committer, so the set is this item's own, one entry per
   change however many files it holds. `--directory` cannot be used for that, because a change whose archive holds no
   tracked file collapses to the archive root itself.
3. When that change's `evidence/` carries no green record of its own, it copies the tally's `*.xml` and the gate's
   `gate.log` beside them, never over a file the worker already wrote. Both writes stay inside `evidence/`, the one
   place this project allows a write after the gate (issue #837).

The predicate "this record is green" moved into `verification_record_green` (`tools/loop/worktree.sh:429`), which
`carried_verification` now calls instead of carrying its own copy of the two `case` arms. Two readers of one rule
(a branch's git objects, and the working tree the copy walks) that drift apart are how a change the run wrote itself
would read back as unverified; `tools/loop/shared.tsv:62` records `gate_tally_state`'s new reader in `worktree.sh`.

## Blast radius

- `end-item` gains one step before its commits. It writes only under
  `openspec/changes/archive/<change>/evidence/`, and only when a green gate and a change without a green record are
  both present.
- `carried_verification` keeps its shapes, its refusals and `branch-state`'s vocabulary: a change whose only record
  is red still reads `archived-not-green`.
- `tools/loop/tests/loop-cases.sh:4380` adds one case, registered in the case list at `:130` and the dispatch table
  at `:965`.

## Rollback

Revert the two hunks in `tools/loop/worktree.sh` and the case — the copy is a leaf of `end-item`: no other command
reads what it writes, and a tree without it behaves exactly as the base does.

## Alternatives the evidence eliminated

- **Widen `carried_verification` to read the build output from the tree.** The reading happens on a later run, and
  the directory it would read is the one the next item's start clears (`tools/loop/evidence.sh:768`) and the next gate
  overwrites — a predicate reading a file that is gone by then measures nothing.
- **Ask the worker to copy the tally by convention.** The convention is what this item measured as broken: #964's
  worker copied nothing, and no check names the omission. A rule no check enforces is the state the item was filed
  about.
- **Copy at archive time, by the worker.** The archive is written *before* the gate (issue #837: the tree the gate
  measures and the tree a round reads have to be one), so at that moment there is no tally to copy. `end-item` is the
  first step after the gate that writes the change, and the only writer of the change's evidence the run has.
