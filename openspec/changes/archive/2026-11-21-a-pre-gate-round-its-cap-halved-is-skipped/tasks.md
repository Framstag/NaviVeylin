# Tasks: a-pre-gate-round-its-cap-halved-is-skipped

## 1. The precondition

- [x] 1.1 `tools/loop/run.sh`: read `review_input_cut_scale` before the round-before-the-gate is launched and skip it
      when more than half of the whole input was cut, recording the reason on the item beside the other review fields —
      `review_input_cut_halved`, the `pre_gate_skipped`/`pre_gate_skip_reason` fields, and the step written
      `--status skip` when the guard fires.
- [x] 1.2 Confirm the gate and round 1 still run in that order after the skip, and that nothing reads the skipped
      round's report as an empty answer — `pre_gate_report` stays empty on a skip, the gate child's `FINDINGS_PATH`
      says the round was skipped, and `run#aPreGateRoundItsCapHalvedIsSkipped` asserts `gate review` is the order the
      run started its children in.

## 2. The cases

- [x] 2.1 `run#aPreGateRoundItsCapHalvedIsSkipped` — a diff the cap cut past half launches no child and leaves one
      recorded reason.
- [x] 2.2 `run#aPreGateRoundUnderHalfItsCapStillRuns` — a diff cut 5 % launches the round.
- [x] 2.3 Declare both cases' files in the driver's own registry (`driver#everyCaseDeclaresTheFilesItCanReach`).

## 3. The report and the comment

- [x] 3.1 `tools/loop/report.sh`: count a skipped round as its own class on the `review:` line, not as `undecided`.
- [x] 3.2 `tools/loop/loop.conf`: state the half rule beside `LOOP_REVIEW_INPUT_MAX_BYTES`, with the five cut
      shares the items' scale files read (67, 67, 47, 38, 5 %).
- [x] 3.3 Mutate the precondition away and confirm 2.1 goes red; restore, then confirm the tree is back on the change
      (`git diff` against the base is the same 4 files and `guidelines/Backlog.recipes.md`), and run
      `tools/loop/tests/loop-mutations.sh --check` and `tools/check-doc-values.sh`, both green. The full
      `tools/loop/tests/loop-selftest.sh` is the **gate's** selection, not this phase's: the gate child runs it over
      this delta.

## 4. The prose that owns the rule

- [x] 4.1 `guidelines/Backlog.recipes.md` section 7a: state the skip beside the sentence that says the diff is read before
      the item's gate, with the measurement it answers and the report class it records.
