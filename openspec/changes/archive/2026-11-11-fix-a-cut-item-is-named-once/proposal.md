# A cut item is named once, by the cause its record states

## Why

`tools/loop/report.sh` reads its action list from seven sources, and two of them select the same item: the resume arm
takes every record whose result is `cut` (`report.sh:693-696` as HEAD has it) and the recovery arm takes every record
that carries a kept patch (`report.sh:701-705` as HEAD has it). A `cut` child that left work satisfies both, so one
item is printed twice — and the second line asserts a cause the record denies.

Measured on the run of `2026-11-11T11:37:08Z` (`.pi/issue-loop/run.json`, issue #850): #847 and #839 are `result: cut`,
`stopReason: bound`, with the reason *the worker process exited 124 — the child reached the recorded context bound of
200000 tokens (LOOP_ITEM_CONTEXT_MAX) at 200174 / 200869 tokens*, and both carry a `leftover`. The report prints them
as actions 10 and 11 (`resume #847`, `resume #839`) and again as 12 and 13 (`recover #847`, `recover #839`) — 2 of the
12 actions it lists — while the run's own window closed at 15:14:07Z against a 23:05:00Z deadline, so no clock ended
either child.

**Root cause, in one sentence:** the recovery arm's guard is the presence of a kept patch, which is a *consequence* of
a cut and not its cause, so an item the run ended at a bound is named a second time under a line that says the run's
clock ended it.

**What the record can say about the cause.** A `cut` carries `bound` and no other mechanism: `state.sh` refuses every
value outside `refused|unmergeable|bound` (`tools/loop/state.sh:666`) and refuses a mechanism on a result that is not
a block, except `bound` on a `cut` (`state.sh:668-671`, the requirement `openspec/specs/agent-issue-loop/spec.md:2486`,
"A block records the mechanism that stopped it"). The run's own deadline is one of the bounds that word covers:
`run.sh:2808` turns every child the wait ended into `bound`, the deadline arm included (`run.sh:1839`), and
`run#childIsEndedAtTheRunsDeadline` reads the deadline's own record as `bound` (`tools/loop/tests/loop-cases.sh:6915`).
So the deadline is not a second cause the state carries per item, and a report that read one would be inventing it.
What the state does carry is which bound it was, in the item's own note — the sentence the resume line already prints
(the context bound's 200174 tokens above).

**The red test.** `report#aCutItemIsNamedOnceAndItsKeptWorkWithIt`, written in the shape
`report#aCallThatNamesTheGateIsNotAGateRun` sets: a state holding the record of a bound-cut item that kept a patch and
the record of an item whose kept work has no bound cut (`blocked` with a `leftover`, the shape a tree wrote before
`cut` existed). On HEAD `8243410e` it fails on `'recover #21' should not appear in:` — the item is listed twice, with
the second line naming a clock the record does not carry (`evidence/red-on-HEAD.log`, `tests=1 failures=1`,
2026-11-11T17:12:16Z).

**Surface**, counted before the plan was fixed:

- `tools/look.sh 'recover #'` — `tools/loop/report.sh:704` (the arm's line, as HEAD has it) and
  `tools/loop/tests/loop-cases.sh:7490` (the case that today asserts the recovery line for a cap-ended item; it is
  rewritten, not deleted).
- `tools/look.sh 'resume #'` — `tools/loop/report.sh:695`, `tools/loop/tests/loop-cases.sh:7216` (unchanged in
  substance: a cut item is still named for resume) and `openspec/specs/agent-issue-loop/spec.md:3931` (the
  requirement's one scenario).

surface: 1 case added, 1 case rewritten (3 assertions and a comment), 1 guideline section realigned, 1 spec scenario
added — `./tools/look.sh 'recover #'`, `./tools/look.sh 'resume #'`.

## What changes

1. `tools/loop/report.sh` — the recovery arm's guard is the record's result, not its patch: `select(.result != "cut")
   | select((.leftover // "") != "")`. One item, one line.
2. `tools/loop/report.sh` — the resume line carries the kept patch (`${patch:+ · its work is kept at $patch}`), so the
   fact issue #674 put in the action list survives on the line that names the item.
3. `tools/loop/tests/loop-cases.sh` — the new case, and `run#treeOfACapEndedChildIsKeptAsAPatch` rewritten from
   `recover #21` to `resume #21` plus the patch path and the absence of the recovery line.
4. `guidelines/Backlog.recipes.md`:1369 "a named action list" — the action-list paragraph names the two lines, says one item is one line, and
   counts the seven sources the report reads (it says five, and five was last true before #788's resume line and
   #674's recovery line were added).
5. The `agent-issue-loop` delta — one scenario on the existing requirement, pinning the one-line rule and the recovery
   line's own record.

## What this does not change

The item's record. No engine script writes anything differently: the result, the mechanism, the patch and the note stay
what `run.sh` and `state.sh` already write, and the closed vocabulary `refused|unmergeable|bound` is untouched — a
second cause word for the deadline is exactly the widening `state.sh:666` refuses, and it would reverse #790's recorded
rule that the run's own deadline is one of the bounds a `cut` carries. The recovery arm keeps its sentence and its
patch path for the record it now alone owns.

## Blast radius

Two hunks in one engine file (`tools/loop/report.sh`), one new case and one rewritten case in
`tools/loop/tests/loop-cases.sh`, one paragraph of `guidelines/Backlog.recipes.md`:1369 "a named action list", one scenario in the change's
spec delta, and two mutation records. No compiled file is touched, so no build is owed; the gate for this delta is the
loop's own suite over the paths it changes.

## Provenance

Issue #850, filed from the run of `2026-11-11T11:37:08Z`. The direction it carries — select on the cause the state
records rather than on the presence of a patch — is implemented as the result selects it: the state records *that* the
run ended the child, and the note records *which* bound, which is the only split the closed vocabulary allows.
