# The findings quote keeps every whole line at its cap

LOOP-ROUTE: guidelines/Backlog.recipes.md §7b

## Why

`review_findings_part` quoted one reading's `BLOCKING:`/`NOTE:` lines to the part cap with
`printf '%s\n' "$findings" | head -c "$cap" | sed '$d'`. `sed '$d'` deletes the last
retained line **whatever it holds** — its purpose is the partial line a mid-line cut leaves — so when the cap happens
to land on a newline the last retained line is a complete finding line and it is dropped: one `BLOCKING:`/`NOTE:` line
of the reading is missing from the item's record while the `[LOOP-CUT: …]` marker on the next line accounts for
nothing. The opposite edge is unmarked as well: a mid-line cut leaves a headless fragment of the next finding, and a
reader cannot tell a fragment from a short line.

Found by review round 1 of #987 — `LOOP-BLOCKING: 0`, `VERDICT: PASS`, this as a NOTE, with the truncation path
recorded as unasserted and unmutated
(`openspec/changes/archive/2026-11-23-fix-a-round-cut-twice-keeps-both-readings/evidence/review-round-1.md`).

## What changes

- `tools/loop/evidence.sh`: `review_findings_part` cuts at the line boundary the cap falls on — `head -c` keeps a
  partial line at the cap, and the newlines inside the bytes it kept are the lines that ended before the cap, so
  `head -n` hands exactly those and no fragment — and the `[LOOP-CUT: …]` marker names how many of the report's
  finding lines the quote carries beside the report the whole of them stands in.
- `tools/loop/tests/loop-cases.sh`: `evidence#aCutAtTheCapKeepsEveryWholeLine`, which drives the function through
  `tools/loop/evidence.sh` itself with a cap on a line boundary and a cap inside a line.
- `tools/loop/tests/loop-mutations.sh`: `1046.1`, which drops the line the cap ends as well and must be noticed.
- `guidelines/Backlog.recipes.md`, in the section the input bound is stated in: the paragraph records what a
  **findings** quote
  owes, which it never did.

## Root cause

The cut used `head -c`'s byte position to decide whether a line is complete, which it cannot: `head -c` retains a
partial line at the cap and `sed '$d'` then discarded the last retained line unconditionally, so the two cases — cap
inside a line, cap on a boundary — got the same treatment when only one of them had a partial line to drop.

## Red case

`evidence#aCutAtTheCapKeepsEveryWholeLine` on the base `62d62e46` with the engine unmodified:
`XML tests=1 failures=1 errors=0` (2026-11-26T02:17Z),
`evidence/red-on-HEAD-aCutAtTheCapKeepsEveryWholeLine.log` — the log's last line reads
`FAIL evidence#aCutAtTheCapKeepsEveryWholeLine`, and its first failure is
`'NOTE: two' not found in: BLOCKING: one`: the finding line the cap ends is dropped.

## Surface

surface: 1 case added, 0 rewritten, 1 mutation record added, 1 guideline paragraph — `./tools/look.sh 'review_findings_part'` (11 matches in 8 files: the engine's function and its two callers, its `layout.tsv` and `events.tsv` declarations, the case, and four archived records) and `./tools/look.sh 'the findings above are quoted to'` (1 match, `tools/loop/evidence.sh:775` — no case and no fixture asserted the marker's text before this change)

## Not the fix

`review_input_head` (`tools/loop/evidence.sh:546`) cuts a round's **input** with the same `head -c | sed '$d'`
pipeline and is not touched: that cut's lost line is named by the cut index `bound_review_inputs` writes beside it, so
a whole line dropped there is reported in `<round>.cut.md` and not lost. The findings quote has no index — the report
it names is the only record — which is why the two edges are unaccounted for here and not there.
