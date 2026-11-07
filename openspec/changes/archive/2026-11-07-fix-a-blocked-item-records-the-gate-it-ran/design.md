# Design — a blocked item keeps the gate it ran

## The root cause

The `file:line` references in this document are the base `233cd286`'s, which is where the defect is, except where
`## The single fix` gives the tree this change leaves — the change inserts seven lines above every one of them.

`tools/loop/run.sh` computes two facts about an item's own gate and review before the outcome block, and the outcome
block has five arms. Only the `closed` arm (`run.sh:2767`–`2768`) passes the first:

- `gate_field`, `run.sh:2353`–`2357` — the child's `LOOP-GATE` line, else the count of test-results XMLs the tree
  holds, else `skipped (no gate configured)` for a project with no gate.
- the round before the gate, `run.sh:2197`–`2207` — read for its blocking count alone; its verdict is not kept, so
  `review_verdict` at `run.sh:2441` is `"off"` unless the round was *refused* (`reviewer-wrote`, `deadline`,
  `cut-short`).

`tools/loop/state.sh:614`–`616` writes what it is handed, so a non-closed item records `gate: ""` and
`reviewVerdict: "off"` / `reviewRounds: 0`. `tools/loop/report.sh` reads the empty gate as "the gate never ran"
(`:488`, the gate-rerun action) and the zero rounds as "never reviewed" (`:289`, the review line).

## The single fix

Two assignments, no new branch:

1. The four arms that do not close pass the field they already have: `--gate "$gate_field"` at `run.sh:2809`
   (`falsified`), `:2854` (`skipped`), `:2885` (`cut`) and `:2951` (`blocked`).
2. The round before the gate records its own verdict. `run.sh:2208`–`:2210` reads the round's answer into
   `pre_gate_verdict` — kept when it is one of `PASS`, `FAIL`, `NOTES` — and `run.sh:2448`–`:2455` seeds
   `review_verdict` from it (and `review_rounds` from `0` to `1`) **only** when no refusal took precedence, so the
   three refusal verdicts `state.sh:576` already validates are unmoved.

`tools/loop/state.sh` needs no edit: `--gate` and `--review-verdict` are accepted by every result, and the record's
schema does not change.

## Blast radius

- **Two fields of the record**, and only for items that ended `blocked`, `cut`, `skipped` or `falsified`:
  `metrics.gate` (was `""` on HEAD) and `metrics.reviewVerdict` / `metrics.reviewRounds` (were `off` / `0` when the
  pre-gate round answered and refused nothing).
- **`tools/loop/report.sh` reads both, and nothing else does.** `report.sh:488` stops emitting a gate-rerun action for
  an item whose tally is now recorded; `report.sh:190` prints a cut item's verdict where it printed `-`;
  `report.sh:284`–`295` counts a pre-gate round as a first round instead of an item never reviewed. No arm, label,
  escalation, branch, merge or closure reads either field (checked with `./tools/look.sh 'reviewVerdict'` — two
  matches outside `tools/loop-cases.sh`, both in `report.sh` — and `./tools/look.sh 'metrics.gate'`).
- **`tools/loop/state.sh report`** prints `gate` in its per-item line (`state.sh:312`) and now prints the tally instead
  of `-` for those items.
- **The suite**: one case rewritten, one added, two mutation records, and one record realigned (`79.3`, the site that
  decides which findings the gate child is handed, now reads `pre_gate_verdict`); no fixture changes, because the
  fixture the new assertions need — a change child stopping at the diff, a gate child with a tally, a tail that refuses
  the gate — is the one `run#aRedGateIsBlockedByTheTail` already stages.

## Rollback

Revert the two `run.sh` hunks; the four `--gate` arguments and the seed go back to the base's text and every case the
suite had before is green again. The archive, the branch and the record of an item already run keep the fields they
were written with, because the fields exist on both sides of the change.

## Alternatives eliminated

- **A `report.sh` filter for "a tally is on disk".** The issue's own second edit, and already written: `report.sh:488`
  selects on `.metrics.gate == ""` and excludes `cut` (issue #788). A filter that re-derived the tally from the tree
  would be the second reading of a fact the record already carries, and it would have to guess which item's XML is
  whose — eliminated by `report.sh:482`'s own comment, which says the transcript beside the item's id is what a caller
  reads.
- **Recording the pre-gate round by giving it a round number in the post-gate loop.** The loop's counter is what
  `--max-review-rounds` bounds (`run.sh:2453`), and an item that blocks does not reach it; a pre-gate round numbered
  there would spend the budget of rounds the gate has not seen. Eliminated by keeping the counter where it is and
  writing the one round's verdict beside it.
- **Treating a pre-gate `FAIL` as the item's refusal.** `run.sh:2203` already distinguishes a refusal with a count from
  a `FAIL` with none, and the run's own contract is that a `FAIL` with zero blocking findings is a pass whose notes are
  recorded (`run.sh:2199`–`2200`). A record that wrote `FAIL` for a zero-count round would contradict the reading the
  same block just made, so the verdict is recorded as the round answered it and the refusal stays the separate value it
  is.
- **A new field for the gate on a non-closed item** (`metrics.gateRan`, `metrics.tally`). The field exists and its
  three shapes are documented in the report and in the cases; a second one would leave two answers to "what did the
  gate say" in one record.

## The guideline section

`guidelines/Backlog.recipes.md`:710 "Refusals, records and the landing" owns what a run records of an item that did not
close, and it was read for this change (`:710`–`:786`). It states the *reasons* an item ends with — a child that never
started, an empty tree, a refused start, a merge the base would not take — and names no gate tally, no review verdict
and no field of the record beside them; the numbers it does name are the ones a stop reason quotes. So this change owes
it no sentence: the rule it adds is "the record carries what was computed", and the section's paragraphs would have to
restate a schema that `tools/loop/state.sh:614`–`616` already is. The report's side of it is `:892`, which owns
what the report prints and is untouched by this change.

## Open decisions

none
