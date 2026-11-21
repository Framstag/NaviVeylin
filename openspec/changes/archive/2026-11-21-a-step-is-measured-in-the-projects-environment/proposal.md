# Proposal: a-step-is-measured-in-the-projects-environment

## Why

**Root cause.** `tools/loop/gate.sh` starts every step in a subshell that inherits the gate's own environment, and the
gate is a child of the item — started through `tools/pi-child-events.sh`, which exports the result bound, the
repeat-read arm, the child's session name, its trim record and its repeat ledger, beside the item's event stream
`run.sh` names. `--base-red` adds `LOOP_BASE_RED=off` on top. A step whose command runs the loop's own suite therefore
ran *as that child*: the suite's cases read the inherited facts instead of the fixture's own, and the two arms below
went red on a tree nothing had changed. Measured 2026-11-21 on the leg
`tools/loop/tests/loop-selftest.sh --from-git {base}`, which the run records as red at every base it has measured —
all six of them in `.pi/issue-loop/base-red.log`, 859–944 s each, the newest 509 s.

**Evidence** ⊨:

- `adapter#theResultBoundExemptsGateEvidence` — `expected '6000', got '211'` (`.pi/issue-loop/base-red.log:9525`).
  The 6 000 characters are the case's plain result; the 211 are `alreadyReadNotice` for `items/816/gate.log` read out
  of the **item's** ledger, which the measured suite inherited from the child that started the gate. Reproduced on
  HEAD in this tree: `expected '6000', got '193'` without a session name, `211` with one.
- `gate#theBaseStateIsMeasuredWhenTheGateFirstNeedsIt` — `expected '0', got '1'`
  (`.pi/issue-loop/base-red.log:9041`). The case asserts that a gate measures its step's base state; the measured
  suite inherited `LOOP_BASE_RED=off` (`tools/loop/gate.sh:87`), so the nested gate under test declined to measure and
  the step's red stayed with it.
- `notes#aCaseThatWasRed` in the run's record is not a case at all: it is the line the failing case's own fixture
  suite prints (`tools/loop/tests/loop-cases.sh:6860`), captured because the case was red.
- Falsification, both directions, one command, `.pi/issue-loop/base-red.log`-shaped: with HEAD's `gate.sh` and the
  child's environment the two steps report `tests=2 failures=2`; with this change's `gate.sh`, the same command in the
  same environment reports `tests=0 failures=0` and both cases pass (`evidence/red-on-HEAD-980.log`,
  `evidence/green-with-the-change-980.log`).

**Consequence** ⊨: the leg cannot be green at any base, so every gate that owes it carries a `— base-red:`
subtraction for it — and only a delta under `tools/loop/**` owes that leg — and every such run pays the leg's wall
clock before it can subtract anything. That is the item filed from #935's decision D as #980. The subtraction itself
(base-red, #921/#944) is right and stays; what it was subtracting is a step this gate broke.

## What changes

- `tools/loop/gate.sh`: one list, `item_session_vars`, and the step launch removes them from the step's environment.
  The gate keeps them for its own reading — `LOOP_CHILD_EVENTS` names the child's session id in the run record,
  `LOOP_BASE_RED` is what a measurement sets for itself.
- `tools/loop/tests/loop-cases.sh`: `gate#aStepIsNotHandedTheItemsSession` — a step that reports what it was handed,
  run by a gate started in a child's full environment.
- `tools/loop/tests/loop-mutations.sh`: record `980.1` drops the removal, so the case must fail on a step that saw the
  ledger — and record `63.1`, which quotes the step launch, is rewritten to the launch's new shape.

## What does not change

The base-red rule: a step red on the run's base is still subtracted pair by pair and named (`.pi/issue-loop/base-red.log`
records what the measurement found, and `run.baseRed` stays keyed by the step command). No waiver is pre-applied from
the attribution pass, which is the arm #935's decision D rejected. What stops a measurement recursing is
`base_red_mode` in the measuring process itself, not the exported switch.
