# fix-a-fenced-closing-block-is-read

## Why

A reviewer's closing block written as inline code is read as an answer the transport cut, so a complete refusal is
escalated as no reviewer having refused the change (issue #793).

**The entry, verified in the tree and against the artifacts.** Item #788's two pre-gate reports are still on disk:

```
$ tail -c 60 .pi/issue-loop/items/788/pre-gate-review.md | od -c | tail -3
0000020   o r k . \n \n - - - \n \n ` L O O P - B L O C K I N G :   3 ` \n ` V E R D I C T :   F A I L ` \n
```

`.pi/issue-loop/items/788/pre-gate-review-retry.md` ends the same way, each file last byte `\n` — the newline the
child's adapter prints after every answer. The run read both as cut (`gh issue view 788`, comment
2026-11-06T06:21:16Z: *the pre-gate reviewer's report was cut short twice — the round's answer never arrived, so no
reviewer refused this change*), which blocked the item and put `status/on-hold` on it, while the report in its hand
carried three blocking findings and `VERDICT: FAIL`.

**Root cause, one sentence.** Every reader the run decides a report by anchors its field at column 0, so a closing
block the child wrote as inline code is not a field at all, and a complete answer with a verdict line is read as an
answer that carries none:

- `tools/loop/run.sh:902` (base `5e034a24`) — `report_field`: `sed -n "s/^${2}: //p"`, so `` `LOOP-BLOCKING: 3` ``
  is no `LOOP-BLOCKING` line, and `blocking_count` (`:948`) reads the report as one that states no count.
- `tools/loop/run.sh:917` — `answer_complete`: `grep -qE "$2" "$1"` against `'^VERDICT: (PASS|FAIL)'`, so the same
  fence hides the closing line, the answer is read as cut, and the round is asked again (`:2003`, `:2036`, `:2330`,
  `:2338` — four call sites).
- `tools/loop/run.sh:2048` and `:2399` — `grep -q '^VERDICT: FAIL'` / `'^VERDICT: PASS'`, the same anchor on the two
  places the verdict's word is read beside the count.

**Red test, on the base `5e034a24`** (shell gate, so the tally is the case, not a JUnit XML — `evidence/red-on-HEAD.log`,
2026-11-06T07:36:19Z→07:36:26Z):

```
run#aFencedClosingBlockIsACompleteAnswer| run: the round 1 answer was cut short (`VERDICT: FAIL`) — retrying the round once
FAIL run#aFencedClosingBlockIsACompleteAnswer: a fenced closing block is not an answer the transport cut:
  'answer was cut short' should not appear in: …
```

i.e. `bash tools/loop-selftest.sh --case 'run#aFencedClosingBlockIsACompleteAnswer'` → **1 of 1 cases failed**, the
run reaching the same *cut short twice* reading item #788's round reached.

**Owning requirement.** `agent-issue-loop`, "A review finding is blocking or it is not, and the round counts"
(`openspec/specs/agent-issue-loop/spec.md:855` at the base commit). Its scenario "A report that arrived cut is
retried, not read as a refusal" (`:894`) states what the shape of an answer decides; it read the shape from column 0
only, and this change adds the sibling requirement the incident measures.

**Owning guideline section.** `guidelines/Backlog.recipes.md`:674 "Leftovers, the report and the queue" — the paragraph
"What it does with an answer that arrived cut" in `guidelines/Backlog.recipes.md:685-700`, which said a report with no
`VERDICT:` line is incomplete and said nothing about a line that carries a fence.

**Surface, counted before the plan was fixed** (`grep -c 'answer_complete "' …`, `grep -c 'report_field "' …`,
`grep -l 'VERDICT: PASS|FAIL' tools/loop/prompts/*.md`, `bash tools/loop-selftest.sh --list | wc -l`):

surface: 1 case added, 0 rewritten, 0 fixtures realigned — 4 answer_complete call sites and 2 verdict reads go
through 2 new readers, and 46 report_field call sites inherit the fence strip; the case declares
`tools/loop/run.sh`, `tools/pi-child-events.sh` and `tools/loop/fixtures/scenarios/`; 325 cases after the addition.

## What Changes

- `tools/loop/run.sh`: one reader, `report_lines` (the report's lines with one backtick taken off each end), and one
  new reader over it, `report_verdict` (the word on the verdict line). `report_field` and `answer_complete` read
  through `report_lines`; the two `grep '^VERDICT: …'` sites read through `report_verdict`. `NOTE:` findings are
  prose and are unchanged.
- `tools/loop/prompts/reviewer.md`, "The count, then the verdict it licenses": the closing block is written last and
  may be written plain or as inline code, because the run reads the line where it stands.
- `guidelines/Backlog.recipes.md`:685 "What it does with an answer that arrived cut": the fence is named in the
  paragraph that owns an answer's shape.
- `tools/loop-cases.sh`: `run#aFencedClosingBlockIsACompleteAnswer`, and `tools/loop-mutations.sh` record `83.1`.
- `openspec/specs/agent-issue-loop/spec.md` (this change): one added requirement and its scenario.

## Impact

Nothing about what a report means changes: a report with no closing line at all is still an answer that arrived cut,
and a report with no count line is still a refusal. Only the fence a child may have copied out of its own prompt stops
being read as an absence.

## Open decisions

none — the item's own required case settles the contract it leaves open: it asks for a report whose closing block is
fenced **asserting the answer is complete**, which is the reader side; the other contract (the prompt says a fenced
line does not count) leaves the measured cost in place because the reviewer would still write the fence its prompt
shows it. The prompt states the accepted shape as well, so the two contracts no longer disagree.
