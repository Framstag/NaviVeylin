# Tasks — a pre-gate round that answered nothing is recorded, not refused

## 1. The red case first

- [x] Re-cut `run#aPreGateReviewThatIsCutLeavesTheChangeForTheNextRun` as
      `run#aPreGateReviewCutTwiceIsUnansweredAndTheGateDecides`: only the pre-gate prompts answer with no `VERDICT:`
      line, the item ends on the gate child's own block, and the case asserts the gate child is started, the round is
      recorded `unanswered` and counted, the step names it, and no reason says a reviewer refused the change.
      Its declaration and its dispatch lines move with it.
- [x] Run it against `HEAD`'s `run.sh`/`state.sh`/`prompts/reviewer.md` and record the failure:
      `and the diff it never answered is gated: the gate child is started: expected '1', got '0'`.

## 2. The fix

- [x] `tools/loop/run.sh`: `pre_gate_unanswered=0` beside the other per-item round fields, with the measurement it
      answers in its comment.
- [x] `tools/loop/run.sh`: the cut-twice arm sets that flag instead of `pre_gate_refusal`/`pre_gate_refusal_verdict`.
- [x] `tools/loop/run.sh`: the round's verdict becomes `unanswered` when nothing was read, the `pre-gate-review` step's
      note says so, and the gate child's findings placeholder says the round handed nothing forward.
- [x] `tools/loop/state.sh`: `unanswered` joins the `--review-verdict` vocabulary, its usage line and the comment
      above the validation.
- [x] `tools/loop/prompts/reviewer.md`: the #954 clause's cost sentence says the round is recorded as unanswered
      rather than that the item is refused.

## 3. The mutation the invariant owes

- [x] `tools/loop/tests/loop-mutations.sh`: record `953.1` puts the refusal back into the cut-twice arm.
- [x] `tools/loop/tests/loop-mutations.sh`: record `79.4` re-pointed at `run#aPreGateReviewEndedAtItsBoundNamesIt`,
      the case that still takes the outcome arm a bound-ended round records.

## 4. The documents

- [x] `openspec/specs/agent-issue-loop/spec.md`, through this change's delta: 5 requirements, 29 scenarios — the
      transport cut leaves the bound/deadline scenario and gets its own; the bound-cut requirement, the blocking-count
      requirement, the restart requirement and the non-interactive requirement are scoped to the round each sentence
      is true of.
- [x] `guidelines/Backlog.recipes.md`: the sentence that says two cut answers in one round block the item is
      scoped to the round after the gate, with the pre-gate answer named beside it.

## 5. Verification

- [x] The re-cut case and the two cases that share its block: `run#aPreGateReviewCutTwiceIsUnansweredAndTheGateDecides`,
      `run#aPreGateReviewEndedAtItsBoundNamesIt`, `run#thePreGateReviewRunsBeforeTheGate` — PASS.
- [x] The cases the scoped requirements name:
      `run#aReportCutTwiceBlocksAsTheTransportsFault`, `run#aCutReportIsRetriedInsteadOfRefused`,
      `run#aBlockedItemRecordsTheRoundThatReadIt`, `prompt#reviewerCarriesTheEightItems` — PASS.
- [x] `bash tools/loop/tests/loop-mutations.sh --check --from-git HEAD` — `all 199 cases passed`.
- [x] `bash tools/loop/tests/loop-mutations.sh --only "953.1 79.4"` — both red when mutated, both green after
      the restore.
- [x] The gate for `tools/loop/**`: `bash tools/loop/tests/loop-selftest.sh --from-git HEAD --jobs 8` over the
      delta — the set the delta owes is the whole 486-case suite (`run.sh` and `loop-cases.sh` are core files), and
      it ran green before this change was archived.
- [x] The gate over the delta: `bash tools/loop/tests/loop-selftest.sh --from-git HEAD --jobs 8` —
      `all 486 cases passed (JDK 21, offline, no token, no sandbox) — 897.92 s`
      (`evidence/gate-full.log`); `tools/loop/evidence-check.sh` on this change — `PASS`
      (`evidence/evidence-check.txt`).
