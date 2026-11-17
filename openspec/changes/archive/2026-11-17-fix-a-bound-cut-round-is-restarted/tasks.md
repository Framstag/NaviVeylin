# Tasks

## 1. The restart (spec `agent-issue-loop`, "A review round a bound ended is restarted in the run that cut it")

- [x] 1.1 `tools/loop/loop.conf`: record `LOOP_REVIEW_RESTART_MAX=1` beside `LOOP_REVIEW_INPUT_MAX_BYTES` with the
      measurement it is priced from (#861's 110 turns with no refusal, 21.08 M of 65.21 M cache-read, 70 m 26 s of
      209 item minutes), the reason a fresh session is the cheap half, and the arm not taken — a bound derived from
      the diff, which needs a reading rate per turn this archive does not carry and would return the capped input's
      own constant. — done: `grep -n '^LOOP_REVIEW_RESTART_MAX=' tools/loop/loop.conf`.
- [x] 1.2 `tools/loop/run.sh`: `review_restart_scope` (the section a restarted round reads: the round, the bound's
      `LOOP-TIMEOUT` line, the cut round's `BLOCKING:`/`NOTE:` lines at the input cap, the pre-gate report),
      `review_restart_before_deadline` (the run's own clock, read from `run.deadlineEpoch` as `#851`'s continuation
      reads it) and `review_restart_note` (the one line the record keeps). — done: `bash -n tools/loop/run.sh` clean.
- [x] 1.3 `prompts/reviewer.md` + `render_reviewer_prompt`: a `{{REVIEW_RESTART}}` placeholder, substituted first
      beside `CORRECTION_SCOPE` and `PRE_GATE` so the section is text the round reads and not text a later
      substitution rewrites. — done: the case asserts the restart's prompt holds no `{{REVIEW_RESTART}}`.
- [x] 1.4 The pre-gate arm (`run.sh`, the block before the gate child): a round a bound ended is restarted in a fresh
      session with the findings it wrote, and the arms below — the two guards, the deadline, the bound, the verdict —
      read the **final** report, so only a second bound is `pre_gate_cut`. — done: `run#aPreGateReviewEndedAtItsBoundNamesIt`
      asserts the `-restart` prompt, `metrics.reviewRestart` and no `-retry` prompt.
- [x] 1.5 The round after the gate (`run.sh`): an **attempts loop** over one round — `<round>.md`, `<round>-retry.md`,
      `<round>-restart.md`, `<round>-restart-retry.md` — with the digest `before` taken before the first attempt and
      read after the last, and `review_restarts_used` reset where a round starts (the count is per round, not per
      item). — done: `run#aBoundCutRoundIsRestartedWithItsFindings` closes the item on the restart's answer with
      `metrics.reviewRounds` at 1.
- [x] 1.6 `tools/loop/state.sh` and `tools/loop/report.sh`: `done --review-restart` (one line, refused if it carries
      a newline) stored as `metrics.reviewRestart`, printed by the report as
      `rounds a bound cut and the run restarted:`, `none` for a run whose rounds no bound cut. — done: the case
      asserts both the field and the line.
- [x] 1.7 `tools/loop/tests/loop-cases.sh`: the case `run#aBoundCutRoundIsRestartedWithItsFindings`, registered in the
      list and the dispatch; `run#aPreGateReviewEndedAtItsBoundNamesIt` and
      `run#eachChildIsBoundedByItsOwnRolesTurns` extended — the latter's stub now ends **every** reviewer prompt at
      its bound, because a second bound is what makes that item's cut. — done: all three `PASS`.
- [x] 1.8 `tools/loop/tests/loop-mutations.sh`: `918.1` (the guard that starts a restart), `918.2` (the findings the
      restart is handed), `918.3` (the line the record keeps), and the comment that says which half of the
      invariant is not in the set and why. — done: `--check` applies all three; a run reds each and restores green.
- [x] 1.9 `guidelines/Backlog.recipes.md` §7b (the rule beside the run's other bounds) and §7e (what a bound cut is,
      and what it is not) — the restart, what is handed over, what is not a restart, and the two cases that hold it.
- [x] 1.10 The delta: one added requirement under `agent-issue-loop` and the shipped pre-gate requirement modified,
      every scenario it already had carried verbatim. — done: `openspec validate
      fix-a-bound-cut-round-is-restarted --strict`.
- [x] 1.11 `tools/loop/tests/loop-mutations.sh`: the two records whose quoted line this change moved — `31.1` (the
      retry's own test, now inside the attempts loop) and `68.4` (the reviewer's `LOOP-DEADLINE` read) — take a
      whitespace-tolerant anchor (`^ *`) rather than the indentation they were written with. Left alone, both
      reported `MUTATION-PATTERN-STALE` and `mutation#everyDeclaredRecordApplies` went red on the full suite. —
      done: `--check` applies both, and each reds its case and restores green.

## 2. Verification

- [x] 2.1 The red case, on the tree before the change: the new case against `HEAD`'s `tools/loop/run.sh` —
      `evidence/pre-fix-red.txt`.
- [x] 2.2 The cases the change owns, focused — `run#aBoundCutRoundIsRestartedWithItsFindings`,
      `run#aPreGateReviewEndedAtItsBoundNamesIt`, `run#eachChildIsBoundedByItsOwnRolesTurns`,
      `run#aRoundTheRunEndedRecordsItsBound`, `run#aReviewRoundBoundIsTheItemsStopClass`,
      `run#aReviewerEndedAtItsTurnBoundIsNotARefusal`, `run#aPreGateReviewThatIsCutLeavesTheChangeForTheNextRun`,
      `prompt#reviewerCarriesTheEightItems` — `evidence/focused-cases.txt`.
- [x] 2.3 The revert-check the new invariant owes: `918.1`, `918.2`, `918.3` each red on the case that names it and
      green after its restore — `evidence/mutation-918.log`.
- [x] 2.4 The structural checks the delta owes (`guidelines/Build.md` §2, the scope block): `bash -n` over the four
      scripts, `openspec validate … --strict`, `tools/loop/tests/loop-mutations.sh --check` over the whole set, the
      `driver` and `prompt` groups of the self-test, `tools/check-doc-routes.sh` (356 section and 7 skill references),
      `tools/check-doc-values.sh` (6 roles, prose matches config), `tools/check-openspec-hygiene.sh` and
      `tools/check-rule-routes.sh` (651 named rules) — all green. No Gradle build and no Android suite is owed — the
      delta touches no compiled file — and none was run.
- [x] 2.5 The full self-test suite, because the change owns `tools/loop/**`: `all 478 cases passed (JDK 21, offline,
      no token, no sandbox) — 819.09 s` — `evidence/full-suite.txt`.
