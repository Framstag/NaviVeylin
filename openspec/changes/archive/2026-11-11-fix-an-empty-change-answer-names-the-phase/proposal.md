# An empty change answer is the phase's failure, not the item's tree

## Why

Item #85 of the run 2026-11-07T19:53:44Z → 2026-11-08T02:35:28Z answered nothing: `worker-report.md` is 0 B and the
child's own stream ends `auto_retry_end` with `success=false`. Its branch carried a commit, and the item was recorded
`blocked` with the reason `reported success but no archived change appears in the run's diff` — a reason that claims a
child reported success for a child that reported nothing. `.pi/issue-loop/items/85/` holds no `pre-gate-review.md`, no
`review-pre-gate.diff`, no `worker-gate-report.md` and no `worker-tail-report.md`, and the run's log goes from the
child's commit straight to `unpick #85`: three phases were never started, and the record does not say so.

The same run read item #95's identical silence as its own stop — `the child could not start — the worker exited 0 with
an answer of 0 B and no verdict line` — because that child left its tree at the base. One silence, two readings.

The mechanism is two readings of one fact. `run.sh` enters the phase that runs the gate on the change report's
`LOOP-DIFF-READY` alone, and every arm the item is decided from reads a `LOOP-GATE` or a `LOOP-VERDICT`. A report that
carries none of the three is neither: the diff marker is absent so the gate phase is skipped without a word, and the
verdict is absent so the outcome block cannot read it as a skip or a block, and falls through to the arms that name the
item's own tree. The empty-answer rule issue #810 added is asked of the gate child (with its one retry) and of the
review rounds; the change child was never asked it.

## What changes

`run.sh` asks whether the change child's report carries any line its own arms read (`LOOP-DIFF-READY`, `LOOP-GATE`,
`LOOP-VERDICT`, or a bound line) — `change_answered()` beside `gate_answered()`. A report that carries none is read
where the reading is still cheap, before the round before the gate: the phase's failure is recorded in the item's steps
(`worker-answer`, `fail`, note `exit 0 and no answer`), the run prints what it did not do, and the outcome names the
phase. The tree at the base keeps the run's own stop it already had — the arm for it asks the same question of the tree
and is reached first, because it is the stronger statement about the child — and the branch a silent child left behind
is still committed and kept, which is why the outcome must not name the item's diff.

The change is the phase's own answer and nothing else: no bound, no retry of the change child (its prompt asks for the
diff marker, and a retried change child is issue #752's retry class, not this one), and no change to what a report with
a diff marker, a gate line or a verdict does.

## Cases

- `run#anEmptyAnswerOverACommitIsTheChangePhasesFailure` (new) — the 0-byte answer over a branch: one child, no gate
  child, no round before the gate, no tail, the item `blocked` with a reason that names the change phase.
- `run#aChildThatExitsZeroWithAnEmptyAnswerIsTheRunsFault` (unchanged) — the same silence at the base stays the run's
  own stop, which the new reading must not take over.
- Mutation `273.1` drops the reading and reddens the new case on the run's printed line.

## Evidence

- `.pi/issue-loop/run.json` `.items[] | select(.issue==85)`, `.pi/issue-loop/items/85/{worker-report.md,events.jsonl}`,
  `.pi/issue-loop/run-20261107T195343Z.log:1727-1735` — the run the item was filed from.
- `openspec/changes/archive/2026-11-10-fix-an-empty-change-answer-names-the-phase/evidence/gate-run-line.md` — the gate
  the change itself ran, and its numbers.
