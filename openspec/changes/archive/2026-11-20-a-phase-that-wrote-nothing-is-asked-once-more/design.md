# Design: a-phase-that-wrote-nothing-is-asked-once-more

## Context

`change_answered` reads the change child's report for any line the item's arms read (`LOOP-DIFF-READY`,
`LOOP-GATE`, `LOOP-VERDICT`, `LOOP-TIMEOUT`, `LOOP-DEADLINE`). It exists because a silent child once let the round
before the gate, the gate child and the tail be skipped without a word (issue #827). Its verdict is a fact about the
phase, not about the tree, and the run today turns it into the item's verdict: `blocked`, `stopReason: child-failed`.

Every other bound the run enforces already has a second chance: a checkpoint continues an item whose context filled,
a leftover patch is applied when it fits, a cut item is resumed. A child that exits 0 and writes twelve bytes too
few is the one failure with no recovery path, and it is the cheapest to recover from: the tree is where the child
left it and the phase's own prompt is on disk.

## Decision 1 — one relaunch, from the checkpoint prompt

The relaunch uses the checkpoint composition (`worker-prompt-continuation.md`'s shape), which already names the tree,
the leftover and what is still owed. Nothing new is composed: a second prompt form would be a second definition of
what a phase is asked.

One attempt, not a loop. Two silent children in a row is a finding about the phase — the provider, the prompt or the
tree — and the item is blocked with the count of attempts in its reason, which is strictly more than the run records
today.

## Decision 2 — a child the run ended is not retried

`LOOP_CHILD_TIMEOUT` in the report, and a non-zero `gate_exit`, both mean the run ended the child rather than the
child ending itself. Retrying those would double a spend the run had already decided to stop, which is the opposite
of this change's aim. The guard is the one issue #224 already installed for the gate's report, read by the same
function, so the two retries cannot disagree about which children are eligible.

A third predicate separates this silence from the one #752 already retries: the tree must carry work — a HEAD past the
run's base, or a dirty status under it, since a child's work is uncommitted until the run commits it. A tree at the base
with a clean status is a phase that read nothing at all (a provider that refused every call, #742), and its retry is the
item-level one that re-renders the **first** prompt. The checkpoint composition below tells the child that the tree it is
handed already holds the work, so it is only the honest prompt for a tree the phase moved. The tree at the base is
read in both halves on purpose: the item-level arm reads a HEAD at the base *after* the run has committed, so a dirty
tree at the base is work here exactly as a commit past it is.

## Decision 3 — what was rejected

- **Decide the item from the empty report.** #827's whole point: no arm may read an empty file as an answer.
- **Retry every phase, not just the change child.** The reviewer's answer is checked by `answer_complete` against a
  closing line the prompt asks for; a reviewer that wrote nothing has read nothing, and a second round on a diff is
  the spend this change is trying to stop, not to add.
- **A configurable attempt count.** A key with one observed value is a promise the project cannot keep measured. If a
  second attempt proves necessary the number is the line to change, with the next run's own figures beside it.

## The falsification

The new case is red before the change: with no retry arm, the empty report still ends the item, and the case's
assertion (`one relaunch, one answer`) fails on the item's own record. The second case asserts the negative —
a timed-out child is not relaunched — so a retry that swallowed the bound guard would fail it.

## Open decisions

none
