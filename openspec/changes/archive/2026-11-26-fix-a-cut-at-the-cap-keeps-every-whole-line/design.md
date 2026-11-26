# Design — the findings quote is cut at the line boundary the cap falls on

## Root cause

`review_findings_part` decides whether the last retained line is complete from a byte count it does not have: `head -c
"$cap"` keeps a **partial** line at the cap, and `sed '$d'` then deleted the last retained line unconditionally. The
two cases the cap can land in — inside a line, on a newline — therefore got one treatment when only the first has a
partial line to drop, and the second lost a whole finding line of the reading with nothing on the item saying so. The
same unconditional drop left the opposite edge unmarked: a mid-line cut hands a headless fragment of the next finding,
indistinguishable from a short line.

## The single fix

`tools/loop/evidence.sh:760` — `review_findings_part` counts the newlines inside the bytes the cap kept
(`tools/loop/evidence.sh:771`). A newline inside those bytes is a line that **ended** before the cap, so the count is
the number of whole lines the cap carries, and `head -n "$kept"` hands exactly those: a cap that lands on a boundary
keeps the line it ends, a cap inside a line drops only the fragment it left, and a cap shorter than the first line
keeps nothing. The marker then names the count beside the report the whole of them stands in
(`tools/loop/evidence.sh:775`), which is the only account the record has of a line the cap left out.

Two alternatives were eliminated by the evidence rather than weighed:

- **A copy of the pipeline in the case**, asserting the pipeline's output: refused by this tree's own rule — a copy of
  the cutter proves nothing about the cutter — so the case drives `review_findings_part` through
  `tools/loop/evidence.sh` itself.
- **Widening the cap, or counting the dropped lines into `<round>.cut.md`**: the findings file is not a round's input
  and has no cut index; the report it names is the whole record, so a second file would be a second thing to keep in
  step rather than an account of the first.

## Blast radius

`review_findings_part` has two callers, both in `review_findings_evidence`
(`tools/loop/evidence.sh:806`, `:808`), so the only artifact whose bytes change is the `review-findings.md` a cut
round's item keeps: its quote now ends on a whole line and its marker carries the count. `run.sh` is untouched, the
item's state fields are untouched, and the report's columns are untouched. `layout.tsv` and `events.tsv` already
declare the function, so no declaration moves.

## Rollback

Reverting the three hunks restores the previous bytes: the function's body, the case's registration in
`tools/loop/tests/loop-cases.sh`, the `1046.1` mutation record. Nothing written by the change outlives a revert — the
archived change and the guideline paragraph are the only files that hold new text, and both are additive.

## Measurement

```
LOOP-MEASURE base=62d62e46d6370269d51018c2dfdecf072f424855 paths=4 insertions=67 deletions=4
LOOP-MEASURE-SUITE cases=564 cmd=tools/loop/tests/loop-selftest.sh --list
LOOP-MEASURE-CHANGE fix-a-cut-at-the-cap-keeps-every-whole-line scenarios=5 cases=5 unnamed=0
LOOP-MEASURE-SURFACE surface: 1 case added, 0 rewritten, 1 mutation record added, 1 guideline paragraph
```

The suite count moves by one case (`563` before the change) — the red case this change adds — and the five scenarios
are the three the requirement already carried plus the two edges this change asserts.
