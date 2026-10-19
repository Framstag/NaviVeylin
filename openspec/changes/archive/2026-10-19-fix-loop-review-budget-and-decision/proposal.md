# fix-loop-review-budget-and-decision

## Why

Two findings about what a blocked item hands to a person.

`while [ "$round" -le 2 ]` was the whole review budget and no flag or `loop.conf` key raised it (#187). In
the run of 2026-10-19T07:12:47Z both #117 and #119 reached round 2 with scenario coverage, root cause, gate
reality, tasks and revert-check at PASS and only `## 6. Evidence pointers` (plus #119's `## 4`) at FAIL —
every finding a citation, a count or a wording correction, with the reviewer writing "no third review is
needed". Both were blocked anyway, so 61 of the run's 66 minutes ended in a handover a third pass closed.

The escalation those failures produced read `the change failed its second review round — a decision is
needed, not a third attempt` plus an evidence line, and nothing else (#186). The reviewer's own report had
already answered the question; an owner reading only the tracker could choose nothing: merge as it stands,
apply the corrections, re-run the change, or abandon it were all unnamed.

## What changed

- The round budget is a recorded value: `LOOP_MAX_REVIEW_ROUNDS_DEFAULT` in `tools/loop/loop.conf`,
  overridable with `run.sh --max-review-rounds N`, recorded as `run.maxReviewRounds` by `state.sh init`,
  printed beside the other budgets by `report.sh` and `state.sh show`. Zero is refused — a review that never
  runs would block an item and leave a `review-failed-round-0` refusing its branch for ever.
- A refusal whose findings are strictly subtractive (`LOOP-CORRECTIONS-ONLY: yes`) earns exactly one more
  round; a second refusal, or a first one that adds a claim, case or assertion, still blocks. The extension
  is a one-shot flag, so the worst case is `max + 1` rounds.
- `LOOP-DECISION` is read from the reviewer's final report and rendered into the escalation as a
  `**The decision:**` paragraph: the question, the options and the reviewer's recommendation.
- `tools/loop/prompts/reviewer.md` asks for both fields and says what each must contain.

## Spec

`openspec/specs/agent-issue-loop/spec.md`, scenarios "Review rounds are bounded, and a corrective refusal
earns one more" (replacing "Review rounds are bounded") and "An escalation names the decision", applied in
the same commit (910a016). `.pi/skills/fix-loop/SKILL.md` gains `--max-review-rounds` in its usage block and
flag table and the corrective-round rule in its failure table.

## Cases and evidence

`run#reviewBudgetIsARecordedValue`, `run#aCorrectiveRefusalEarnsOneMoreRound`, `run#aZeroRoundBudgetIsRefused`,
`run#escalationCarriesTheDecision`, and the reviewer-prompt assertions in
`prompt#reviewerCarriesTheEightItems`; mutations 19.1–19.3 and 20.4.

- `evidence/green-on-fix.txt`, `evidence/revert-check.txt`, `evidence/gate.txt`

## Impact

`tools/loop/loop.conf`, `tools/loop/run.sh`, `tools/loop/state.sh`, `tools/loop/report.sh`,
`tools/loop/prompts/reviewer.md`, `tools/loop-cases.sh`, `tools/loop-mutations.sh`,
`openspec/specs/agent-issue-loop/spec.md`, `.pi/skills/fix-loop/SKILL.md`.

## Open

`LOOP-DECISION` is read with `report_field` (the last matching line). A decision written over several lines
keeps only its last line, and nothing validates that `LOOP-CORRECTIONS-ONLY` is `yes|no`; both are prompt
contracts today, not checks.
