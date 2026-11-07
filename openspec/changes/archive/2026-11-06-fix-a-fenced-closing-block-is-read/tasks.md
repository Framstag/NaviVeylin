# Tasks

Reference: `proposal.md` for scope and the red run, `design.md` for why,
`specs/agent-issue-loop/spec.md` for the contract.

## 1. The red case, before any engine change

Requirement: "A closing line is read where it stands, fence or no fence", scenario "A closing block written as
inline code is a complete answer".

- [x] 1.1 Add `run#aFencedClosingBlockIsACompleteAnswer`, its entry in `LOOP_SHELL_CASES` and its dispatch line: the
      stub reviewer answers with a zero-blocking refusal whose closing block is inline code
      (`` `LOOP-BLOCKING: 0` `` then `` `VERDICT: FAIL` ``, the adapter's newline after it). The case asserts the run
      asks no retry (`reviewer-prompt-1-retry.md` absent, no `answer was cut short` in its output), spends one review
      round, closes the item, records the `NOTE:` beside the fenced block, starts exactly three children, and asks for
      no decision. Verify: on the base `5e034a24`, with `tools/loop/run.sh` at HEAD, it fails with
      `a fenced closing block is not an answer the transport cut: 'answer was cut short' should not appear in: …`
      (2026-11-06T07:36:19Z–07:36:26Z, `evidence/red-on-HEAD.log`; shell gate, so the tally is the case, not a JUnit
      XML).

## 2. The engine

- [x] 2.1 `tools/loop/run.sh`: `report_lines <file>` (the report's lines with one backtick taken off each end) and
      `report_verdict <file>` (the word on the verdict line). `report_field` and `answer_complete` read through
      `report_lines`; the two `grep '^VERDICT: FAIL'` / `'^VERDICT: PASS'` sites read through `report_verdict`.
      Verify: `bash tools/loop-selftest.sh --case 'run#aFencedClosingBlockIsACompleteAnswer'` PASS
      (2026-11-06T07:40:58Z–07:41:05Z, `evidence/green-on-change.log`).
- [x] 2.2 `tools/loop-mutations.sh`: record `83.1`, dropping the un-fencing from `answer_complete`. Verify:
      `bash tools/loop-mutations.sh --only 83.1` → `red-when-mutated: 1 · not-noticed: 0 · restore-failures: 0`
      (2026-11-06T07:36:44Z–07:36:58Z, `evidence/mutation-83.1.log`).
- [x] 2.2b `tools/loop-mutations.sh`: record `79.3` quoted the line this change replaced, so its expression moved to
      the verdict reader. Verify: `bash tools/loop-mutations.sh --only 79.3` → `red-when-mutated: 1 · not-noticed: 0 ·
      restore-failures: 0` (2026-11-06T07:39:55Z, `evidence/mutation-79.3.log`).
- [x] 2.3 `tools/loop/prompts/reviewer.md`: the closing block is written last and may be plain or inline code, because
      the run reads the line where it stands. Verify: `grep -n 'inline code' tools/loop/prompts/reviewer.md`.
- [x] 2.4 `guidelines/Backlog.recipes.md`:685 "What it does with an answer that arrived cut": name the fence with the
      2026-11-06 measurement and what the readers now do. Verify: `grep -n 'fence or no fence'
      guidelines/Backlog.recipes.md`.

## 3. Verification

- [x] 3.1 The cases the pathspec owes, run against the change: the cut/refusal cases, the pre-gate cases, the
      turn-bound cut, the escalation and the per-item metrics, plus
      `driver#everyCaseDeclaresTheFilesItCanReach` — Verify: `evidence/focused-cases.log` (2026-11-06T07:37:49Z→
      07:39:01Z; eight selections, 16 cases, every one PASS).
- [x] 3.2 `bash tools/loop-mutations.sh --check` for the records whose lines this change moved — Verify:
      `evidence/mutation-check.log`.
- [x] 3.3 Nothing else moves: `git diff` touches `tools/loop/run.sh`, `tools/loop-cases.sh`,
      `tools/loop-mutations.sh`, `tools/loop/prompts/reviewer.md` and `guidelines/Backlog.recipes.md`, and the change's
      own directory stays untracked until the archive moves it under `openspec/changes/archive/` (the `.gitignore`
      entry `openspec/changes/*/`, negated for `archive/`) — Verify: `evidence/diff-stat.log`.
