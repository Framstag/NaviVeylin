# Tasks — the diff is read before the gate

## 1. The change child ends at the diff

- [x] `tools/loop/prompts/worker.md`: Phase D's gate paragraphs are replaced by `{{GATE_STEP}}`; the report block gains
      `LOOP-DIFF-READY`; the verdict rule says the verdict is the following child's whenever a gate line *or* the diff
      marker ended the session. Verified by `run#thePreGateReviewRunsBeforeTheGate` (the rendered prompt carries
      "The gate is not yours this item") and `79.1`; on the base the case first fails on the order it asserts
      (`evidence/red-on-HEAD.log`).
- [x] `tools/loop/prompts/gate-deferred.md` (new) — the session ends at the diff, no gate, `LOOP-DIFF-READY`.
- [x] `tools/loop/prompts/gate-own.md` (new) — the older Phase D text, for the folded shape.
- [x] `tools/loop/run.sh`'s `gate_step_text`, substituted **first** in both render sites; the rework round of a
      post-gate refusal is rendered with `gate-own.md` (the gate is that child's, its judge comes after it).

## 2. The round before the gate

- [x] `tools/loop/prompts/pre-gate-review.md` (new) and `{{PRE_GATE}}` in `tools/loop/prompts/reviewer.md`, substituted
      first like `{{CORRECTION_SCOPE}}`.
- [x] `render_reviewer_prompt` takes the section as its third argument; every post-gate call site passes nothing.
- [x] `tools/loop/run.sh`: the block after the change child commits the diff, writes `review-pre-gate.diff`, runs the
      round (with the retry, the read-only guard, the deadline and the turn bound), and records the step
      `pre-gate-review`. Verified by `run#thePreGateReviewRunsBeforeTheGate` and `79.2`.
- [x] `--pre-gate-review on|off` (`LOOP_PRE_GATE_REVIEW_DEFAULT=on`), folded to `off` by `--review off`, named in the
      run's header.

## 3. The gate child

- [x] `tools/loop/prompts/worker-gate.md` (new): phase E — apply the findings in one pass, run the gate once, copy what
      it cites, repair nothing, print the four lines verbatim plus the gate line.
- [x] `tools/loop/run.sh`: the findings are **appended** to that prompt, the child is started, and
      `gate_report`/`gate_exit`/`gate_child`/`gate_write_*` are replaced from it — so the tail, the report fields, the
      commit and the outcome arms read the child that actually ran the gate. Verified by
      `run#childUsageIsRecordedPerItem`, `run#aRedGateIsBlockedByTheTail` and `79.3`.
- [x] `clear_stale_test_results` skips `worker-gate*`; the gate child's `outside-write` reading is recorded as
      `outside-write-gate`.
- [x] `tools/loop/phase.sh`: phase E's checklist is the gate child's prompt.

## 4. A round the run cut before the gate

- [x] `tools/loop/run.sh`: the outcome arm that names the pre-gate round, and the tail's condition that leaves it
      alone — an item whose pre-gate round was cut is blocked with the round's own reason, and the round *after* the
      gate is never started. Verified by `run#aPreGateReviewThatIsCutLeavesTheChangeForTheNextRun` and `79.4`.

## 5. Fixtures and cases

- [x] `tools/loop/fixtures/pi`: `PI_STUB_REPORT_GATE` and the `worker-gate*` arm before `worker*`.
- [x] `tools/loop-cases.sh`: `STUB_CHANGE_REPORT`, `stub_write`'s gate report and its two action arms,
      `stub_per_item_worker`'s arm.
- [x] `run#thePreGateReviewRunsBeforeTheGate` — the order, the marker, the section, the findings, one gate, no rework.
- [x] `run#aPreGateReviewThatIsCutLeavesTheChangeForTheNextRun` — the retry, no gate child, the reason, the branch.
- [x] `phase#oneCallPrintsThePhaseState` realigned (phase E has a checklist; `prescreen` is the phase without one) and
      `prompt#thePhasePromptsCarryTheirOwnRoute` extended to `worker-gate.md`.

## 6. Falsification and the gate

- [x] `tools/loop-mutations.sh`: `79.1`–`79.4` added; `76.1`, `9.5` and `40.3` re-pointed at the lines this change
      moves. `bash tools/loop-mutations.sh --check` → all 235 records apply; `--only '79.1 79.2 79.3 79.4'` → 4 red,
      0 not noticed, 0 restore failures (`evidence/loop-mutations.txt`).
- [x] `bash tools/loop-selftest.sh --case …` over the affected and neighbouring cases, and
      `--files tools/loop/run.sh tools/loop/prompts/ tools/loop/phase.sh tools/loop/fixtures/pi tools/loop-cases.sh
      tools/loop/loop.conf` (`evidence/loop-cases.txt`).
- [x] `bash tools/check-rule-routes.sh --diff main` — the reading set for this delta (`Backlog.md` §1, §4,
      `BacklogTooling.md` §9, `BuildGate.md` §2a), read.

## 7. Documents

- [x] `guidelines/Backlog.recipes.md`, section "Running one item: the script, the child and the package" — the phase
      diagram and the paragraph with the measurement, the folded shape and the budget it does not spend.
- [x] `tools/loop/loop.conf` — `LOOP_PRE_GATE_REVIEW_DEFAULT` with its record.
- [x] This change's delta against `agent-issue-loop`, and `openspec validate --strict`.

## 8. Evidence

- [x] `evidence/red-on-HEAD.log`, `evidence/loop-cases.txt`, `evidence/loop-mutations.txt`,
      `evidence/openspec-validate.txt`.
