# The checks a child runs by hand are bounded while it runs, not priced after it ends

## Why

`LOOP_GATE_RUNS_MAX` bounds the gate's own full runs — a second full run through `tools/loop/gate.sh` is refused with
exit `3` — and the **same work started outside `gate.sh` met no bound at all**. Issue #857 priced it: a check a child
runs by hand is the gate time, whoever started it, and the report now carries it as `metrics.gateRepeat` beside the
gate's own run. Price is not a bound: nothing in the run acted on the count, because the count was read from a stream
the child had already finished writing to.

The run of 2026-11-13T05:47:11Z measured what that costs: **226 hand-run checks holding 1.3 h of its 3.1 h gate
bucket**, with the walls of #853, #865 and #864 at 80 %, 60.4 % and 58.8 % gate. A child that iterates is bounded by
its turns and by the context a turn costs, and the widest spend inside those bounds is the gate's own work — a
focused suite, the mutation set, the structural checks — run once per idea and once per correction, so an item's
gate clock is paid several times over while the item's gate child has not started.

## What Changes

1. `LOOP_CHILD_CHECKS_MAX` — how many of the gate's own checks one child may start by hand. `loop.conf` records `10`,
   with the measurement and the reasoning beside it: `guidelines/BuildGate.md` §2a's inner loop (three focused
   attempts, a fourth being `blocked`), the affected group before and after the fix, the mutation set and the delta's
   structural checks are eight calls, with two spare. `0`, or no key, is no bound — the rule every other bound in the
   profile follows (D16).
2. The run counts the calls **while the child runs**, from the lines its stream has gained in the same whole second as
   the turn and context readings, and ends the child past the recorded number: the same `LOOP-TIMEOUT` shape, a line
   naming the key, the bound and the count that reached it, and the item recorded `cut` with `bound` as its stop
   reason, as every other bound the run enforces ends one.
3. What is counted is a call that **runs** one of the programs the gate's own checks start — the map `gate.sh --plan`
   reads: `LOOP_GATE_SUITES`, `LOOP_GATE_FIRST_STEPS`, `LOOP_GATE_COMPILED_CMD` — less the gate's own run and less a
   `--plan` call. A call that only names such a path (`sed -n 1,60p tools/loop/gate.sh`, `git show HEAD:…`) starts
   nothing and is not counted, and the focused case an item's own diagnosis owes is not in the map, so the iteration
   §2a gives a child keeps its room.
4. The classification is one set of definitions shared by both readers, the report's and the run's: the moved
   `gate_program`, the check map `gate_check_programs`, the jq definitions `gate_command_jq` and the incremental
   reader `child_check_calls` live in `tools/loop/lib.sh`, and `state.sh` reads them from there rather than carrying a
   second copy of the rule.
5. The run's header names the bound it enforces (`child-checks-max=…`), and the gate child's prompt states it.

## Impact

- `tools/loop/loop.conf` — one key recorded with its measurement and its reasoning.
- `tools/loop/lib.sh` — `gate_program` moved from `state.sh`, plus `gate_check_programs`, `gate_command_jq` and
  `child_check_calls`.
- `tools/loop/run.sh` — the bound's reading and normalisation, the wait's arm that ends the child, the `LOOP-TIMEOUT`
  line naming it, and the header field.
- `tools/loop/prompts/worker-gate.md` — the gate child's prompt states the bound beside `LOOP_GATE_RUNS_MAX`.
- `tools/loop/state.sh` — reads the shared definitions; the report's own arithmetic is unchanged.
- `tools/loop/tests/loop-cases.sh` — one new case.
- `tools/loop/tests/loop-mutations.sh` — two records for the new case (`882.1`, `882.2`), and four records re-pointed
  at `lib.sh` because the definitions they quote moved there (`272.1`–`272.3`, `855.1`).

Additive: a profile that records no `LOOP_CHILD_CHECKS_MAX`, or records `0`, bounds nothing, and the report's numbers
are the ones it already printed. Rollback is the revert of these files. No Android module, no native boundary and no
UI surface is touched, and no `guidelines/` document makes a claim this change moves — §2a's iteration is the bound's
own reason and stays as it is.
