# Proposal — the decision answer carries a recommendation score

## Why

The sheet ranks nothing. An owner reads four options, each with a verdict line and a risk, and a criteria table whose
cells say *yes*, *partly* or *no* — and then has to work out whether the recommendation won by a hair or was never
contested. "Rejected because <one clause>" reads the same at both distances.

The owner asked for the missing number while #908 was answered (2026-11-20): one score per option, its **absolute
value** the strength of the recommendation and its **delta** the distance between two options. A delta is the thing
the prose could not carry: an option that lost by one point is a live choice that one clause broke, while an option
five points down was carried by the escalation rather than offered.

## What Changes

- `.pi/skills/answer-decision/SKILL.md` §7 gains **The score**: an anchored rubric (10 / 8–9 / 6–7 / 4–5 / 2–3 / 0–1),
  three rules that make a delta mean what it claims, `do nothing` scored on the same scale, and the rule that the
  recommendation holds the highest number.
- The same skill's ban on a single ranking is restated as **never one averaged score**, with the score read off the
  columns the sheet already fills — so a number that disagrees with them is a defect of the sheet, not a second
  opinion.
- `.pi/skills/answer-decision/references/answer-shape.md` gains `## The score (0–10, the last block)` in its skeleton,
  and its checklist grows from seven items to eight.
- `tools/issues/tests/issue-tooling-cases.sh` gains `decisions#everyOptionCarriesAScore`, which pins the six rubric
  anchors, the three delta rules, the table's header, and that the table is the sheet's **last** block.

## What this does not do

- It does not change the queue, the writer, the labels, the cap or `answer.sh`.
- It does not add a second fork, and it reopens nothing the item records as settled.
- It does not turn the score into an average of the two views: the case pins the sentence that forbids it, and the
  rubric reads its points off the sheet's own columns.
- It does not make the number mandatory evidence about the world — it is the sheet author's judgement, stated as one,
  which is why every row must name what set it.

## Impact

- Capability `decision-queue`: one requirement, three scenarios, added.
- Two skill documents and two cases; no compiled file, no engine script, no build, no gate leg.
- The check that can fail is `bash tools/issues/tests/issue-tooling-selftest.sh` (offline, 185 cases).

surface: 1 requirement and 3 scenarios in 1 delta file, 2 skill documents (`.pi/skills/answer-decision/SKILL.md`,
`references/answer-shape.md`) and 2 cases — re-taken with `openspec validate the-decision-answer-carries-a-score
--strict` and `bash tools/issues/tests/issue-tooling-selftest.sh --list | grep -c 'decisions#everyOptionCarriesAScore\|decisions#theScoreTableReadsItsDeltas'` (2).
