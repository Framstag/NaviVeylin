# Design: a-step-is-measured-in-the-projects-environment

## The seam

`start_step` (`tools/loop/gate.sh`) runs a step as `( $cmd; … ) >"$stream" 2>&1 < /dev/null &` — the gate's whole
environment, minus nothing. That is deliberate for the two facts the gate *wants* a step to have (the worktree's
`.pi/loop-env.sh`, sourced by the gate before it starts anything) and wrong for the facts that describe the child the
gate itself is running under. The two classes are not distinguished anywhere, so the second leaked.

## The rule

A step is a **project command**. The class is closed at the seven facts: what `tools/pi-child-events.sh` exports for
one child — the result bound and its arm, the child's session name, its trim record, its repeat ledger — the item's
event stream (`run.sh` names it per child), and the switch a base-red measurement sets for itself. They are the
*session's*, not the command's. They are removed in one place, from one list:

```
item_session_vars=(LOOP_TOOL_RESULT_MAX_CHARS LOOP_REPEAT_READ LOOP_CHILD_SESSION LOOP_TOOL_RESULT_TRIM_RECORD
  LOOP_TOOL_RESULT_LEDGER LOOP_CHILD_EVENTS LOOP_BASE_RED)
```

The list is the declaration, and `gate#aStepIsNotHandedTheItemsSession` reads it out of `gate.sh` rather than copying
it, so a fact added to the list is asserted the moment it is declared. The case's second check is the other side of
the class: a fact the adapter exports for every child and the list does not carry is named before any step runs.

`run.sh` hands a child two more (`LOOP_STATE_FILE`, `LOOP_WORKTREE`) and they stay in a step's environment. Dropping
them is a second change: with the switch no longer reaching a step, a nested gate a step starts can measure, and an
inherited `LOOP_STATE_FILE` is what decides whether that measurement may write into the run's own record.

## Why not the two narrower arms that were on the table

- **Pin the failure to the case.** Editing the two cases to unset what they inherited would make the suite green at
  base and leave the defect: every other case that reads a bound, a ledger or the base-red switch keeps reading the
  child's, and the base-red measurement keeps paying 859–944 s to re-learn the leg.
- **Drop `export LOOP_BASE_RED=off` outright.** That half is real — it is what stopped the nested gate under test
  from measuring — but it is one of seven facts, and the adapter failure is the other six. Dropping the export would
  also have removed the measurement's own belt (the same value read at line 95 into `base_red_on`), and the switch
  must stay for that process; what must not happen is the switch reaching a *step*. Recursion stays bounded because a
  measuring gate cannot measure (`measure_step_base` returns at `[ "$base_red_mode" -eq 0 ]`), so a nested gate a
  measured suite starts is at most one measurement deeper, over a fixture whose suites are seconds.

## The cost the change removes

The leg `tools/loop/tests/loop-selftest.sh --from-git {base}`, measured on the tree at the run's base, runs the
**whole** suite there — a clean tree at base has no delta for `--from-git` to select with — so a green base-red
measurement is still one suite at the base. What the change removes is not that cost but its being *paid every run and
red every time*: with the leg green at base, `measure_step_base` records nothing for it and the base-red machinery
never fires for this step again. That a `--from-git` selection with an empty delta runs the whole suite instead of
naming the emptiness is a second question and is not this change's.

## Test shape

`case_gate_aStepIsNotHandedTheItemsSession` starts a gate in exactly the environment a child of the item hands it
(every variable `tools/pi-child-events.sh` exports for one child, the item's event stream, plus `LOOP_BASE_RED=off`),
with a profile whose one step writes `<var>=<value>` per variable into a canary file — the gate keeps a suite's stdout
to itself, so a file is the one place a case can read a step's output from. The case asserts the step ran (`RAN=1`),
and that each fact of the class is `<unset>` in what it saw; the mutation `980.1` drops the removal, so the step sees
them again.
