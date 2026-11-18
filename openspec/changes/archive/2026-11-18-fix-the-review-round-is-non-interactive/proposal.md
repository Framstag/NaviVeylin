# The reviewer round is stated non-interactive, and a question is a cut-short answer

## Why

`tools/loop/prompts/reviewer.md` states the shape of the answer's tail — the closing `VERDICT:` line and the
`LOOP-BLOCKING:` count — and nothing about the round being one process with nobody to ask. A child read the file
as a document to summarise and answered the run with a question:

```
Pick: (1) run review, (2) summary done, (3) edit prompt. Awaiting answer.
```

**The measurement:** the run of `2026-11-17T10:58Z`, items #916 and #917. Four review rounds — two reviews per
item, the round before the gate and the round after it — ended that way, and the run read each as an answer it had
no verdict from. The retry prompt is byte-identical to the one that earned the question, so the retry asks the
same question again and the item is refused for a question the run can never answer.

**Root cause, in one sentence:** the reviewer prompt names the shape of the answer and never states that the round
is non-interactive, so a child that reads a prompt as a document to discuss answers with a question the run reads
as a cut-short answer.

**Why the clause sits in `reviewer.md` and not in the pre-gate section:** the round before the gate is the same
reviewer prompt plus the section `tools/loop/prompts/pre-gate-review.md` (`tools/loop/run.sh:3185`), and the round
after it is the prompt unchanged (`tools/loop/run.sh:3801`) — one clause beside the output contract reaches both,
which is what the item asks for. A clause in the pre-gate section alone would reach the round that asked twice per
item and leave the other.

Owning capability: `agent-issue-loop`. Related: issue #952, the run's fault block, which asks no question of its
own — a different surface from the round's prompt.

surface: 1 clause in 1 prompt file (`tools/loop/prompts/reviewer.md`, +9 lines / +641 bytes over HEAD), 2 cases
whose assertion group gains the clause, 1 mutation record added, 1 spec requirement with 2 scenarios —
`grep -n '^case_prompt_.*[Rr]eviewer' tools/loop/tests/loop-cases.sh` → **4 cases**;
`bash tools/loop/tests/loop-mutations.sh --list --files tools/loop/prompts/reviewer.md` → **5 records**
(`37.1`, `37.2`, `61.4`, `258.1`, `954.1` — `selected 5 of 415 records`, 2026-11-17T17:33Z);
`grep -c 'render_reviewer_prompt' tools/loop/run.sh` → **6 matches**.

## What Changes

1. **`tools/loop/prompts/reviewer.md` states the round is non-interactive, beside the output contract** — the
   paragraph after "Your whole output is the review below." and before "Check these eight items…": there is
   nobody to ask and nothing to confirm while the round runs, so never ask a question of its own, never offer to
   proceed or to confirm before starting, never offer a summary in place of the round, and never end the round
   with a question instead of the lines the contract names; carry out the round and end with those lines. The
   refusal's `LOOP-DECISION` line stays part of the contract the clause points at, so the clause withdraws no line
   the contract owes.
2. **The same clause names the cost of an answer that is a question.** The run reads no verdict from it, spends
   its one retry on a byte-identical prompt that asks the same question again, and then refuses the item
   (issue #954) — the vocabulary the retry rule already uses, so the clause names a *cut-short answer*.
3. **`prompt#reviewerCarriesTheEightItems` asserts the clause** on the rendered reviewer prompt:
   `non-interactive`, `never ask a question`, and `an answer that is a question is a cut-short answer`.
4. **`prompt#thePreGateRoundRefusesOnlyWhatTheGateCannotSettle` asserts the same clause** on the rendered
   pre-gate prompt — the round of the two that asked twice per item — so a clause that stops reaching that round
   fails a case instead of only the round after the gate.
5. **Both cases declare `tools/loop/prompts/reviewer.md`** in their first-line `# files:` declaration, which
   neither did: both assert text that comes from that file, so a delta to the prompt has to select them.

## Impact

- Production: `tools/loop/prompts/reviewer.md` — one clause, +9 lines / +641 bytes; the file's placeholders
  (`{{REVIEW_INPUTS}}`, `{{CORRECTION_SCOPE}}`, `{{PRE_GATE}}`, `{{REVIEW_RESTART}}`, `{{REVIEW_WALL_CLOCK}}`)
  and the line the bound's placeholder sits on are untouched, which
  `prompt#reviewerInputsAreBoundedAndTheCutIsNamed` reads with `grep -qF`.
- Cases: `tools/loop/tests/loop-cases.sh` — one assertion group in `prompt#reviewerCarriesTheEightItems` and one
  pair in `prompt#thePreGateRoundRefusesOnlyWhatTheGateCannotSettle`, plus the two `# files:` declarations;
  `tools/loop/tests/loop-mutations.sh` — one record (`954.1`).
- Documents: `openspec/specs/agent-issue-loop/spec.md`, through this change's delta — one requirement, two
  scenarios.
- No engine code, no configuration key, no compiled file: what the run does with an answer that carries no closing
  `VERDICT:` line is unchanged (`run#reviewRoundsCapAtTwo`), and this change is what the prompt states, not what
  the run does with a question.
