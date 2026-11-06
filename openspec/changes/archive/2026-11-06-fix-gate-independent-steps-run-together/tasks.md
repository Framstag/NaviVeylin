# Tasks — fix-gate-independent-steps-run-together

## 1. The red case (do first)

- [x] 1.1 Add `gate#independentStepsRunTogether` to `tools/loop-cases.sh` and register it in `LOOP_SHELL_CASES`
      and the dispatcher: a throwaway profile owing three stub suites that each sleep 3 s and touch their own output
      file, asserting the wall clock is nearer the longest than the sum, that all three tallies carry their own
      numbers, and that the verdict carries all three. Verify: red on the base (`4d270814`) — the serial gate spends
      9 s of wall clock; log and tally in `evidence/red-on-base-independent-steps.*`.
- [x] 1.2 Add the declaration guard to each new case (`# files:` naming `tools/loop/gate.sh tools/loop/loop.conf
      tools/pi-child-events.sh tools/tooling.json`) and run `driver#everyCaseDeclaresTheFilesItCanReach` before
      anything else — the gate's plan puts that case first for a delta that writes a case (issue #792).

## 2. The pool (`agent-issue-loop` — "The gate runs the steps that share nothing together")

- [x] 2.1 `tools/loop/loop.conf`: record `LOOP_GATE_POOL_JOBS=4` with its rationale beside
      `LOOP_GATE_STEP_MAX_SECONDS`, and say what `1` means. Verify: `gate#independentStepsRunTogether` passes with
      the profile's value, and the third SHALL's measurement — a pool of one is the serial gate — is
      `evidence/pool-probe.log`'s `PROBE pool=1 wall=10s rc=0`, the same three-step profile through the real gate,
      beside `PROBE pool=3 wall=3s rc=0`. No case pins a pool of one; the probe is what stands under that SHALL.
- [x] 2.2 `tools/loop/gate.sh`: owed entries carry the kind of the list that owed them; `start_step`, `wave_wait`
      and `finish_step` replace `run_step`'s shared `$stream`/`$status_file`/`$step_overran`; the run loop walks
      first steps alone, the build alone, the suites in a wave of at most `LOOP_GATE_POOL_JOBS`, and a wave of one
      keeps the live follower. Verify: the 24 existing `gate#` cases pass unchanged —
      `tools/loop-selftest.sh --case 'gate#*'`.
- [x] 2.3 `tools/loop/gate.sh`: name the pool on the console (`LOOP-GATE-POOL:`) beside the step bound, so a log
      says which clock and which pool were in force. Verify: `assert_contains` in
      `gate#independentStepsRunTogether`.
- [x] 2.4 `gate#aFailingSuitesTallyIsUnchangedByItsNeighbours`: three pooled steps, the middle one failing with its
      own summary, asserting each tally's own counts, the summed verdict, the single `— failing:` entry and exit 1.
      Verify: green with 2.2 in place; on the base the case is green too, by construction — it is the guard that the
      pooled path does not mix one step's numbers into its neighbour's.

## 3. The consumers of the log's shape

- [x] 3.1 `tools/loop-mutations.sh`: re-anchor the records whose anchor lines moved (49.3, 63.2, 84.1), so
      each still mutates the same fact. Verify: `bash tools/loop-mutations.sh --check` applies every record and
      names none as `MUTATION-DID-NOT-APPLY`.
- [x] 3.2 `tools/gate-attribute.sh`: unchanged, and its cases (`attribute#*`) pass — a pooled wave still writes one
      `gate: <command>` … `— <n>s` block per step. Verify: `tools/loop-selftest.sh --case 'attribute#*'`.

## 4. The rule beside its measurement

- [x] 4.1 `guidelines/Backlog.recipes.md`:471 — the paragraph "The steps that share nothing run together." inside
      §7b "What an item costs, and the bounds on it" (`:212`; the pool's own line is `:477`): state the pool, the
      two lists that run alone, and the measured shape the red case carries. Verify:
      `tools/check-doc-routes.sh` and `tools/check-doc-routes-selftest.sh` pass.

## 5. The measurement the change owes

- [x] 5.1 Re-run `./tools/loop/measure.sh --change fix-gate-independent-steps-run-together` once the change and its
      pathspec are final, and quote its block in `evidence/measure-block.log`: the case count
      (`LOOP-MEASURE-SUITE cases=`), the checks this pathspec owes and the gate's own timing line.
