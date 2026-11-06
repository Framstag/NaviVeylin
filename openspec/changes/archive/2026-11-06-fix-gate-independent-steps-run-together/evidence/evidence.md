# Evidence — fix-gate-independent-steps-run-together

## The red case, on the base `4d270814`

`gate#independentStepsRunTogether` — three stub suites that share nothing (each sleeps 3 s and touches an output
file of its own) — `tools/loop-selftest.sh --case gate#independentStepsRunTogether`, 2026-11-06T20:13:29Z:

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

- `green-independent-steps.xml` (`tests=1 failures=0`, 2026-11-06T20:16:30Z) and `green-independent-steps.log`
- `green-failing-tally.xml` (`tests=1 failures=0`, 2026-11-06T20:16:33Z) and `green-failing-tally.log`
- `green-gate-cases.log` — all 26 `gate#` cases, the 24 that existed before this change included:
  `loop-selftest: all 26 cases passed (JDK 27, offline, no token, no sandbox) — 19.53 s, selection: --case gate#*`
- `full-suite.log` — the selection the gate itself would run for this delta, over the final diff:
  `loop-selftest: all 341 cases passed (JDK 27, offline, no token, no sandbox) — 413.44 s, selection: --from-git
  4d270814718dceccc5cb9894c4a590afc1d18a17`. No case in the tree went red on this change.

The declaration guard was run first, as the gate's plan puts it first for a delta that writes a case:
`PASS driver#everyCaseDeclaresTheFilesItCanReach` (2026-11-06, before the red run).

## The mutation records whose anchor lines moved

`bash tools/loop-mutations.sh --check` → `all 260 cases passed — every selected record applies to the file it names`.
Three records' anchors moved and were re-pointed, and each was run (the other two, 46.1 and 63.1, still name the
lines they mutate, because the step's start and its follower kept their local names):

- `49.3` — `RED 49.3 gate#theSummaryNamesWhatEachSuiteCost` … `restore 49.3 … green again` (2026-11-06T20:26:36Z)
- `63.2` — `RED 63.2 gate#aStepIsBoundedAndTheOverrunNamesIt` … `restore 63.2 … green again` (2026-11-06T20:15:32Z)
- `84.1` — `RED 84.1 gate#theDeclarationCaseIsOwedFirst` … `restore 84.1 … green again` (2026-11-06T20:16:04Z)

## The measuring command's block

`measure-block.log` — `./tools/loop/measure.sh --change fix-gate-independent-steps-run-together`:

```
LOOP-MEASURE-SUITE cases=341 cmd=tools/loop-selftest.sh --list
LOOP-MEASURE-GATE seconds=- reason=no tally under the gate's directory carries a time — the gate of this tree has not run
LOOP-MEASURE-CHANGE fix-gate-independent-steps-run-together scenarios=2 cases=2 unnamed=0
LOOP-MEASURE-SURFACE surface: 2 cases added, 1 profile key added, 3 mutation records re-anchored, 1 guideline section touched —
LOOP-MEASURE-END
```

The gate's own timing line is absent because the gate is not this worker's: the run deferred it to a fresh child,
which runs `./tools/loop/gate.sh` over this diff. Nothing here is a gate claim.
