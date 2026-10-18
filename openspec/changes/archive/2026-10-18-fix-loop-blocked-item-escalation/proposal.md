# Escalate a blocked item in the pass that blocked it

## Why

Issue **#174**. Measured on #114: the item was blocked at 2026-10-18T15:02Z with "the worker wrote outside
the worktree — the project's own tree changed while it ran", and afterwards its labels were
`agent/eligible, category/car, class/bug, prio/p2, source/todo-import, status/open` — no
`agent/needs-decision`, no `status/on-hold`, no comment. The reason survived only in
`.pi/issue-loop/run.json` `.items[]`, which `tools/loop/state.sh init` replaces wholesale on the next run.

**Root cause: the blocked exit path escalates only the one block that is a failed review round.**
`tools/loop/run.sh`'s outcome dispatch has three arms — `closed`, `skipped`, and the `*)` arm every blocked
item falls into. The `skipped` arm escalates its decision verdicts (`loop_labels escalate … --reason …`.
`run.sh:518-525`); the `*)` arm removes the in-progress mark and, since #177, records the label it wrote, but
it writes that label only when `review_verdict` is `FAIL` (`run.sh:534-547`, guarded by
`if [ "${review_verdict}" = "FAIL" ]`). Every other reason the run blocks an item for — a child that exited
non-zero, a write outside the worktree, a claim with no commit, no archived change, no test-results XML,
evidence that does not travel with the change, a discarded review round — therefore ends the item with the
tracker untouched.

Measured on HEAD (`evidence/red-on-head.log`, the new case run against `tools/loop` as committed):

```
  a blocked item is marked as awaiting a decision: 'agent/needs-decision' not found in: gh issue edit 21
  --add-label agent/in-progress
  gh issue edit 21 --remove-label agent/in-progress
FAIL run#blockedItemIsEscalatedWithItsReason
tally: 1 of 1 red on HEAD
```

The tracker record holds exactly the two writes the pick and the block make, and no decision label, no
status and no comment.

**The requirement the defect contradicts** is `agent-issue-loop`'s *"Closure follows a passing review and
carries evidence"*, scenario *"A blocked item is never closed"*: "it is not closed, and the reason is
recorded on it". The reason is not recorded on the item — it is recorded in the run's own state, and the
requirement *"The run survives interruption"* says what that state is: a record the next run rewrites. Its
sibling scenario *"State is written only between agent processes"* is why the fix writes the tracker entry
in the dispatch, after the child exits, and not from inside a phase.

## What changes

**One fix: the blocked exit path escalates the item with the reason it holds, exactly as the skip path
already does.**

1. **`run.sh`** — the `*)` arm of the outcome dispatch calls `loop_labels escalate` for every blocked item,
   with `--reason` the blocked reason the run determined and `--evidence` the item's evidence directory plus
   its branch. The `if [ "${review_verdict}" = "FAIL" ]` guard and its `needs-decision` call are gone: the
   failed second review round is one blocked reason among several and is escalated by the same call, so it
   gains the comment it did not have. The label the escalation wrote is recorded with the item
   (`--decision-label`), as every other escalation site already does (issue #177).
2. **One case** red on HEAD — `tools/loop-cases.sh` `run#blockedItemIsEscalatedWithItsReason` — and **two
   mutations**: `14.5` for the new label write, and `14.3` re-pointed at the reworded recording line.

Nothing else changes: the in-progress mark comes off on the same path as before, the run's state records the
same fields, and a failed tracker write still does not end the run.

## Impact

| file | change |
|---|---|
| `tools/loop/run.sh` | the `*)` (blocked) arm of the outcome dispatch escalates the item with its reason and evidence |
| `tools/loop-cases.sh` | `run#blockedItemIsEscalatedWithItsReason` |
| `tools/loop-mutations.sh` | `14.5` new, `14.3` re-pointed at the recording line the fix reworded |
| `openspec/specs/agent-issue-loop/spec.md` | *"Closure follows a passing review and carries evidence"* (the blocked scenario, now naming its cases) and *"The run survives interruption"* (one scenario added) |

No compiled file changes: the gate is `tools/loop-selftest.sh`, offline, no token, no sandbox. No guideline
section states what the blocked path writes, so none is updated — `guidelines/Build.md` §1 and §2 name the
loop's gate and its scope block, and `Backlog.md` names the tracker's write rules, neither of which this
change alters.

**Additive**, not breaking: an extra label, status and comment on an item that previously got none. The
decision cap now counts blocked items too — which is the defect's own point: a run that accumulates blocks
must stop before producing more.

**Rollback**: restore the `if [ "${review_verdict}" = "FAIL" ]` guard and its `needs-decision` call; the new
case then fails exactly as recorded in `evidence/red-on-head.log`, and `14.3`/`14.5` go back to their
previous shape (the fix reworded the line `14.3` matches).

**Not in scope** (the issue's own boundary): carrying the previous attempt's note into the re-queued work
package, and excluding an item whose branch carries work in the pre-screen rather than one round-trip later
(#170). Neither is measured here.
