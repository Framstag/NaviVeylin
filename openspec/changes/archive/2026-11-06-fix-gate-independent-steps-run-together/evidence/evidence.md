# Evidence — fix-gate-independent-steps-run-together

## The red case, on the base `4d270814`

`gate#independentStepsRunTogether` — three stub suites that share nothing (each sleeps 3 s and touches an output
file of its own) — `tools/loop-selftest.sh --case gate#independentStepsRunTogether` on the base `4d270814`:

- `red-on-base-independent-steps.xml` — `tests=1 failures=1`
- `red-on-base-independent-steps.log` — the case's own output: `the gate spent 10s on three steps of 3 s each —
  nearer their sum than the longest, so they ran one at a time`. The sum is 9 s; the serial engine spent 10 s.

`gate#aFailingSuitesTallyIsUnchangedByItsNeighbours` is the guard the item asks for beside it, and it is green on the
base by construction (one step at a time shares nothing either) — `green-failing-tally.log`, run on the base as
`base-guard-tally.log` before the engine changed.

## The measurement the fix's own claim rests on

`pool-probe.sh` runs the same three 3 s suites through the real gate twice, on this host, from a throwaway
repository — `evidence/pool-probe.log`, which keeps every line the probe printed:

- `PROBE pool=1 wall=10s rc=0` — the serial engine, the red run's shape.
- `PROBE pool=3 wall=3s rc=0` — the wave, the longest step rather than the sum.
- both probes — `PROBE pool=<n> tallies=suite-a.xml suite-b.xml suite-c.xml`: each step's own tally, and both
  verdict lines carried all three suites with no failure.

The probe's tally directory goes with its throwaway repository, so the probe's own XMLs are not retained; the
verdict lines its gate printed stay in `pool-probe.log`.

## The change, green

- `green-independent-steps.xml` (`tests=1 failures=0`) and `green-independent-steps.log`
- `green-failing-tally.xml` (`tests=1 failures=0`) and `green-failing-tally.log`
- `green-gate-cases.log` — all 26 `gate#` cases, the 24 that existed before this change included (that log predates
  the third case; the change's own gate log carries 27, `gate#aPooledWavePrintsEachStepWholeWhenItEnds` among them):
  `loop-selftest: all 26 cases passed (JDK 27, offline, no token, no sandbox) — 19.53 s, selection: --case gate#*`
- `full-suite.log` — the selection the gate itself would run for this delta, over the final diff:
  `loop-selftest: all 341 cases passed (JDK 27, offline, no token, no sandbox) — 413.44 s, selection: --from-git
  4d270814718dceccc5cb9894c4a590afc1d18a17`. No case in the tree went red on this change.

The declaration guard was run first, as the gate's plan puts it first for a delta that writes a case:
`PASS driver#everyCaseDeclaresTheFilesItCanReach` (before the red run).

## The mutation records whose anchor lines moved

`bash tools/loop-mutations.sh --check` → `all 260 cases passed — every selected record applies to the file it names`
— `evidence/gate-results/bash_tools_loop-mutations.sh_--check.xml` (`tests="260" failures="0"`), whose `system-out`
is that line. Three records' anchors moved and were re-pointed (the other two, 46.1 and 63.1, still name the lines
they mutate, because the step's start and its follower kept their local names): `49.3`, `63.2`, `84.1` in
`tools/loop-mutations.sh`. What the record shows of them is the `--check` run above applying every one of the 260
records; the per-record red→green pairs were run but their output was not kept, so no line here claims one.

## The measuring command's block

`measure-block.log` — `./tools/loop/measure.sh --change fix-gate-independent-steps-run-together`:

```
LOOP-MEASURE-SUITE cases=341 cmd=tools/loop-selftest.sh --list
LOOP-MEASURE-GATE seconds=- reason=no tally under the gate's directory carries a time — the gate of this tree has not run
LOOP-MEASURE-CHANGE fix-gate-independent-steps-run-together scenarios=2 cases=2 unnamed=0
LOOP-MEASURE-SURFACE surface: 2 cases added, 1 profile key added, 3 mutation records re-anchored, 1 guideline section touched —
LOOP-MEASURE-END
```

The block above was taken before the gate, over the diff as it then stood, and is kept as it printed. The gate has
since run over this diff — `LOOP-GATE: tests=866 failures=0 errors=0` over 23 steps, a summed line **not retained** as
a tally of its own, because it is the sum of the 23 per-step tallies in `evidence/gate-results/` (each of those XMLs
carries its own counts; they add up to 866 tests, 0 failures, 0 errors). The run's own log is
`evidence/gate-round2.log`. The block re-taken after the gate (`evidence/measure-block-round2.log`, the same
command):

```
LOOP-MEASURE-SUITE cases=342 cmd=tools/loop-selftest.sh --list
LOOP-MEASURE-GATE seconds=1222 suites=23
LOOP-MEASURE-CHANGE 2026-11-06-fix-gate-independent-steps-run-together scenarios=7 cases=7 unnamed=0
LOOP-MEASURE-END
```
