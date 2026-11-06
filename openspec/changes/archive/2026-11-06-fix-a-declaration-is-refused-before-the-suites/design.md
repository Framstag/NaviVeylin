# Design — the cheap step first, and the child told about it

## Root cause

`tools/loop/gate.sh` assembles the owed list in one order: the profile's compiled command (`:158`), then the
`LOOP_GATE_SUITES` pairs (`:164`). The case that reads a case's `# files:` declaration is
`driver#everyCaseDeclaresTheFilesItCanReach` (`tools/loop-cases.sh:10585`), and the only command that runs it is the
suite (`tools/loop-selftest.sh --from-git {base}`, the profile pair at `tools/loop/loop.conf:527`). So the cheapest
assertion in the suite is paid for at the price of the whole suite, and for a delta that also touches a compiled file,
after the build as well — which is what #790 paid for twelve minutes and then a whole attempt.

## The fix, and why it is the whole fix

One mechanism: the gate has no way to express "this step is cheap and refuses this delta early". Two seams, both of
them small, and both are needed for the item because they save different things:

1. `LOOP_GATE_FIRST_STEPS` (`tools/loop/loop.conf:535`) — a pair list of the same shape as the suite map, read by the
   gate before the compiled step and before the suites (`tools/loop/gate.sh:185`). The matching itself is not
   duplicated: the loop that reads a pair list was extracted as `owe_pairs` (`tools/loop/gate.sh:163`) and both lists
   are read through it, so the `{base}` substitution and the trailing-newline rule stay in one place.
2. `tools/loop/prompts/worker.md:215` — the child is sent to the plan's first line and runs it before anything else
   when its diff writes or narrows a declaration; the template names neither the case nor the command, because it is
   an engine and a project value written there arrives in another project's child. The gate's order saves the gate's
   tail; only this saves the attempt, because a `blocked` item's branch is set aside by `tools/loop/worktree.sh` and the
   change is worked again from the base.

The reach table itself is untouched: `loop_case_helper_files()` and the driver case remain its one owner, so there is no
second derivation to drift from the case it describes.

## Blast radius

- A delta that touches `tools/loop-cases.sh` owes one command more, first in the plan — the item's own measurement is
   the case alone in 10 s on `528d8e2a`; here the same step reported `time="2"` in its tally
   (`evidence/green-on-change.xml`, the run of `tools/loop-selftest.sh --case gate#theDeclarationCaseIsOwedFirst`). The
   gate's tally directory gains the one file that step writes, whose own `tests` attribute is `0` (the case reports a
   `PASS` line rather than a suite child), so the aggregate `tests=` sum does not grow.
- Every other delta is unchanged: the key's glob names one path, and an empty key is a gate with no first step (a
   project that has no cheap check records an empty value, and `owe_pairs` returns on it).
- The prompt gains eight lines in Phase D, for every child of every project that uses this engine.

## Rollback

Empty `LOOP_GATE_FIRST_STEPS` in `tools/loop/loop.conf`. The gate then assembles exactly the list it assembled before
this change, the prompt's paragraph is inert without the plan line it points at, and the case stays in the suite as the
guard on the rule — it goes red, which is the honest state of a reverted rule.

## Alternatives the evidence eliminated

- **Add the case to `LOOP_GATE_SUITES` and stop there.** Eliminated by the red run's own output
  (`/home/tim/projects/NaviVeylin/.pi/issue-loop/items/792/red-on-HEAD.log`): the compiled command is emitted before the
  pairs, so a delta that touches a compiled file would still run the case after `./gradlew test`, and the item's cost is
  the gate's tail.
- **Emit the compiled step after the suites, so that every pair precedes it.** It would satisfy one assertion of the red
  case by reordering sixty-odd pairs for every delta, spending the build last for reasons that belong to one path. The
  red case asks that one command be first, not that the build be last.
- **A cheaper, second implementation of the reach table in the worker or in the gate.** Forbidden by the item's own
  constraint and by the capability: a copy of the derivation drifts from the case it claims to describe, and the drift
  is then invisible because the copy is the thing that runs.
- **The prompt alone.** It leaves the twelve-minute refusal in place for every child that skips an instruction, and the
  gate's order costs nothing.

## Open decisions

none
