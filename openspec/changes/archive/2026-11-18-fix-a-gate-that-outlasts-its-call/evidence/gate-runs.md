# The three gate runs, and what the one remaining red is

Round 1 (`evidence/gate-round-1.log`, 2026-11-18T20:13Z) — `LOOP-GATE: tests=1157 failures=4 errors=0`, four reds, all
in `tools/loop/tests/loop-selftest.sh --from-git 8b59ce84…`:

- `run#aCorrectiveRoundIsHandedTheCorrections` — `every placeholder in the round-2 worker's prompt is substituted:
  '{{' should not appear in …`, the rendered text carrying `cd … {{GATE_STEP}}{{GATE_STEP}} nohup … 2>{{GATE_STEP}}1
  {{GATE_STEP}}` where the new section's `&&` and `2>&1` were. **This change's own defect**, found by its gate: bash
  5.2's `patsub_replacement` reads `&` in a replacement as the matched text. Fixed in `tools/loop/lib.sh`'s
  `loop_render`, covered by `prompt#aValueArrivesAsTheTextItWasHanded`.
- `run#childPastItsContextBoundIsCut`, `run#aWorkerAtItsCheckpointIsContinuedBeforeTheBound` — the same mangled section,
  in the two other cases that render `worker.md`.
- `run#aHupEndedRunRecordsItsStopAndUnpicksItsItem` — its own pool-scaled deadline (`30 s for each of the 16 case(s) the
  pool starts beside this one`), exhausted under the pooled step.
- `LOOP-GATE-BASERED-UNMEASURED:tools/loop/tests/loop-selftest.sh --from-git 8b59ce84… — no clean tree at 8b59ce84 to
  measure it in, so its red stays with the item`: the base's red could not be subtracted, so every one of the four was
  charged to this delta and had to be attributed by hand.

Round 2 (`evidence/gate-round-2.log`, 2026-11-18T20:36Z) — `LOOP-GATE: tests=1158 failures=1 errors=0`: the three
renderer reds are gone, the HUP case is the only red. The three are green individually on this tree
(`evidence/gate-round-1-attribution.log`).

Round 3 (`evidence/gate-round-3.log`, 2026-11-18T20:57Z, over the **archived** change) — `LOOP-GATE: tests=1165
failures=1 errors=1`, the same HUP case red.

## The HUP case is the host, not this change

- Alone: `PASS run#aHupEndedRunRecordsItsStopAndUnpicksItsItem` twice on this tree (2026-11-18T20:36Z and the round-1
  attribution run) — the case's own `# files:` declaration is
  `tools/loop/tests/fixtures/scenarios/ tools/loop/tests/fixtures/pi tools/loop/run.sh tools/pi-child-events.sh
  tools/tooling.json`, none of which this delta touches.
- On the **base tree with no delta at all**: `git worktree add --detach <scratch>/base-957 8b59ce84`, then
  `bash tools/loop/tests/loop-selftest.sh --group run` → `FAIL run#aHupEndedRunRecordsItsStopAndUnpicksItsItem`,
  `loop-selftest: 1 of 166 cases failed — 636.47 s` (`evidence/base-run-group.log`, 2026-11-18T21:31Z).
- The case's own header says the same thing in its own words: its deadline is the pool's width and not a duration this
  host measured (issues #928, #949), and the wait it fails on is the run's own staging under a sixteen-way pool.

So the round-3 red is a pre-existing, load-sensitive case on this host, reproduced on a tree this change never touched.
