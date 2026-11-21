# Proposal — a step that is stopped is named while it is stopped

## Why

**Root cause.** `tools/loop/gate.sh:702-726` — the wave poll, as the base commit `8e3861c4` carries it and as
`:705-756` carry it now — ends a slot on exactly three conditions: the step's status file exists (base `:708`, now
`:721`), the slot already overran (base `:712`, now `:725`), or its clock reached `step_max` (base `:716`, now
`:729`). Every other look sets `done_all=0` (base `:722`, now `:752`) and sleeps 0.1 s (base `:725`, now `:755`),
and the poll has no other arm: a step whose processes are **stopped** satisfies none of the three, so nothing about
it is printed — no console line, no log line, no state change — until the bound. From outside, a stopped step and
a slow step are the same three facts, and the operator's only visible signal is a log file whose mtime stops.

**Evidence, verified against the tree (issue #948).** `tools/loop/gate.sh --base-red` of the run started
12:14:49Z on 2026-11-16: the step `tools/tests/check-dex-gate-graph-selftest.sh` entered state `T` at 13:03:30Z
and wrote its `gate: <cmd>` line then. Between 13:03:30Z and 13:33:31Z the base-red log did not grow a single byte
(193 458 B, read at 13:30Z and again at 13:32Z), and at 13:33:31Z the bound fired — `gate: tools/tests/check-dex-gate-graph-selftest.sh — 1800s — the step overran its bound of 1800s and was ended`
— after which 42, 54 and 59 steps drained inside two minutes. The 30 silent minutes were the whole cost of the
incident. The consequence outlives it: a stopped step is recorded as an **overrun**, which is a different fact —
an overrun is the step's own fault in the tally (`rc=124`, `LOOP-BASERED-STEP:…|1`), so that run's `run.baseRed`
carries the step as red although it never failed, and the report then subtracts a red no case backs.

**The red case.** `gate#aStoppedStepIsNamedBeforeItsBound` (`tools/loop/tests/loop-cases.sh`) starts a real
`gate.sh` in a throwaway repository whose one suite stops itself (`kill -STOP $$`) while the subshell the gate
started around it stays in `wait`, reads the gate's log for the line while the gate is still inside the step — six
seconds into a ten-second bound, so a line found there is a line printed *before* the bound — and then lets the bound
end the step. Taken before the fix it reported `FAIL gate#aStoppedStepIsNamedBeforeItsBound` / `the gate names a step
that is stopped while it is stopped, not at its bound: … the step overran its bound of 10s and was ended …`
(`red-on-HEAD.log`, whose suite still stopped the whole group `kill -STOP 0` before the case was narrowed to the
member that reads), and as the case stands it is red again at 2026-11-21T02:07:40Z once the arm this change adds is
disarmed (`mutation-948-1.log`, record `948.1`). Its tally, transcribed from the runner's own summary because the
loop's shell runner writes no JUnit XML, is `tests=1 failures=1 errors=0` (`red-on-HEAD.xml`).

**The requirement the item contradicts.** `openspec/specs/agent-issue-loop/spec.md:2815`, "A gate step is bounded,
and the step that overran is named": it states the bound, the overrun's console line and its `LOOP-GATE-STEP:`
tally line, and says nothing about a step that is neither finished nor overrun — which is the step this item is
about. This change ADDS one requirement beside it rather than rewriting it: the bounded-step requirement's body is
already at the length `openspec validate --strict` refuses (`requirement text is very long (>500 characters)`, and
the spec's own `requirements[155]` carries the warning today), so the new behaviour is stated as the one behaviour it
is, with the one scenario whose case reads it. The two do not overlap: that requirement owns what the bound does to a
step that reaches it, this one owns what the gate says about a step it is still inside.

**The guideline that owns the rule.** `guidelines/Backlog.recipes.md:950` (section 7b, "What an item costs, and the
bounds on it") is the paragraph that states the step bound, what the bound prints and what the default is priced
against; a reader who reaches `LOOP_GATE_STEP_MAX_SECONDS` from there is the reader this line is owed to, so that
paragraph gains one paragraph of its own.

surface: 23 matches in 6 files of `tools/loop` — `tools/look.sh 'overran its bound|LOOP_GATE_STEP_MAX_SECONDS'
--path tools/loop`: `loop.conf` 3, `gate.sh` 3, `tests/loop-cases.sh` 14, `prompts/worker-gate.md` 1,
`prompts/gate-own.md` 1, `run.sh` 1. Of those, this change rewrites one arm of `gate.sh` (+1 case, +1 mutation
record, +1 tally line); the prompts and `run.sh` state the *bound*, which does not move.

## What Changes

1. `tools/loop/gate.sh:707-751` — the wave poll reads the state of each slot's process group once a second, in one
   `ps -A -o pgid=,stat=` filtered by `awk` for the whole wave (`:712-716`), and one new arm (`:742-751`) prints
   `gate: <command> — the step is stopped, not running (state <letter>); the bound at <n> s is the only thing that
   will end it` once per slot when a member of its group reads `T`, `t` or `Z`. The bound is not moved: it is still
   the only arm that ends a step.
2. `tools/loop/tests/loop-cases.sh` — the case `gate#aStoppedStepIsNamedBeforeItsBound`, declared in
   `LOOP_SHELL_CASES` and in the dispatch table, with `# files: tools/loop/gate.sh tools/loop/loop.conf
   tools/pi-child-events.sh tools/tooling.json`.
3. `tools/loop/tests/loop-mutations.sh` — record `948.1` disarms the arm (`case "$stopped_groups" in` →
   `case "" in`), so the case reads the silence the item was filed about.
4. `openspec/changes/fix-a-stopped-step-is-named/specs/agent-issue-loop/spec.md` — the ADDED requirement and its
   one scenario, naming the case.
5. `guidelines/Backlog.recipes.md:963` (section 7b, beside the bound's own paragraph at `:950`) — one paragraph:
   the incident, the line's shape, and why the bound stays the only authority over a step's clock.

Nothing else moves: no profile key, no tally shape, no verdict line for a step that returns or overruns.

## Open decisions

None. The item settles the one fork it raises — ending a stopped step early is explicitly out of scope ("the bound
is the engine's one authority over a step's clock, and a second authority would be a second bound"), and the
recorded decision block carries `recorded: none`, so nothing else was answered for this item.
