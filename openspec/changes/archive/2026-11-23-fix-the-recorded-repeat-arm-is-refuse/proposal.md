# Proposal — the recorded repeat-read arm is the refusal

## Why

**The condition, verified on the base.** `tools/loop/loop.conf:543` records `LOOP_REPEAT_READ=measure` on base
`2e6b1d52`, and that one line is the whole of the project's decision: `tools/pi-child-events.sh:101-104` reads it out
of the file (never sourcing it), exports it into every child it starts, and `tools/pi-trim-result.mjs:682` applies it
— `measure` counts a second read of a path this child was handed and hands the text over anyway, `refuse` answers
that read with the earlier result's line range. Nothing is broken: the arm the file records is the arm every child
applies. What is wrong is the value, and it is wrong in the direction the rule's own measurement already reported.

**Root cause, one sentence.** #925 built the counting rule, the refusal arm and its cases, and left the recorded value
at `measure` — the arm that pays the text over — so the measurement the arm exists to move was never applied.

**Evidence.** The item's own census over the 21 item event streams under `.pi/issue-loop/items` — 1 787 `ctx_read`
calls — gives second reads at 25–76 % of an item's reads, median **66 %** (#964 184 of 280, #931 95 of 147, #883 100 of
158, #892 22 of 29), and #925's own figure over the run behind it was 2 167 of 2 987 (72.5 %). Two thirds of the file
text a child is handed is text that item already had, and `metrics.dupChars` prices it per item (#917 72 593 characters
over 35 duplicate reads). The numbers are the item's, restated here as the claim this change acts on and not as a
measurement this change took.

**The red test.** `adapter#theRecordedRepeatArmIsRefuse` (new, `tools/loop/tests/loop-cases.sh`): the case reads
`LOOP_REPEAT_READ` out of the configuration the suite was handed and asserts the value is `refuse`. On the base the
file records `measure`, and the case is red before this change's own line moves:
`loop.conf records measure for LOOP_REPEAT_READ … expected 'refuse', got 'measure'`, `loop-selftest: 1 of 1 cases
failed` (`evidence/red-on-HEAD.log`; the run set the recorded value back to `measure` for its own length and restored
`refuse` after it, so what it measures is the new case against the value it replaces).

**What the arm still does not do.** It decides what a result is *answered with*, never an item's verdict: no key gates
a run and no item outcome reads the arm, which is what keeps this a configuration change and not a gate. A re-read that
is needed is not lost — one whose content changed is the session's own edit and is answered with the changed region
(`VERIFICATION_READ_BOUND`), and an unchanged one is answered with the earlier turn and line range the notice names —
and the count bound `LOOP_REPEAT_READ_REFUSALS=3` (#989) already answers the tail whatever arm is recorded.

## What Changes

- `tools/loop/loop.conf:543` — `LOOP_REPEAT_READ=measure` → `=refuse`, and the key's comment states the recorded value
  and the archive's 66 % median as the measurement that moved it, keeps `measure` as the fallback for a word nobody
  declares and for a project that records no key, and keeps the arm read as an answer and never as a verdict.
- `tools/loop/tests/loop-cases.sh` — one case added: `adapter#theRecordedRepeatArmIsRefuse`, in the case list, the
  dispatch table, and beside `case_adapter_theChildIsStartedWithTheRepeatReadArm`; that neighbour's own comment stops
  calling `measure` the recorded value.
- `tools/loop/tests/loop-mutations.sh` — mutation record `976.1`, rewriting the line back to `measure`, so the case
  notices the recorded value rather than the arm's code.
- The prose that called `measure` the recorded value: `tools/pi-trim-result.mjs` (header and `recordedRepeatArm`'s doc),
  `tools/pi-child-events.sh`, `tools/loop/prompts/worker.md` and `guidelines/Backlog.recipes.md` §7.
- `specs/agent-issue-loop/spec.md` — one `MODIFIED` requirement, "A second read of a path a child was handed is counted,
  and the arm decides its answer": its body and its three shipped scenarios kept whole, one scenario added for the
  recorded value and the case that names it.

Surface, with the command that counted it: `tools/look.sh 'LOOP_REPEAT_READ' --path tools` names the one line that
records the value, the adapter's read of it (`tools/pi-child-events.sh:101`), the trimmer's arm
(`tools/pi-trim-result.mjs:682`), the gate's `item_session_vars` hand-over (`tools/loop/gate.sh:671`), one record in
`tools/loop/tests/loop-mutations.sh` and the cases that set the arm themselves. The cases that set their own arm are
untouched: they drive both arms and do not read the recorded value, which is why the value can move in one line.

## Out of scope

- Re-taking the item's census, or adding a row to `tools/loop-spend.py`'s sweep: this change moves a recorded value that
  a measurement already priced, and a second model would be a second number for one decision.
- `LOOP_REPEAT_READ_REFUSALS` (#989): a count bound on the same rule, whose recorded `3` is unchanged and whose case
  `adapter#aPathIsRefusedAfterItsRepeatBound` drives the arm `measure` itself.