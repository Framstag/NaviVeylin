# Design

## The root cause

The run decides a review round from lines it reads out of the child's report, and every one of those readers is
anchored at column 0: `report_field` matches `^<FIELD>: ` (`tools/loop/run.sh:901`, base `5e034a24`: `:902`),
`answer_complete` greps a caller-supplied `'^VERDICT: (PASS|FAIL)'` (`:939`, base `:917`), and the two places the
verdict's word is read beside the count grep `'^VERDICT: FAIL'` / `'^VERDICT: PASS'` (`:2070`, `:2421`; base `:2048`,
`:2399`). The prompt shows the closing block as a fenced code block, so a child that copies the line writes it as
inline code: `` `LOOP-BLOCKING: 3` ``, `` `VERDICT: FAIL` ``. That line exists, and every anchor refuses to see it.

Two consequences, and the second is the one that cost the item: the completeness reading reports the answer as cut
(so the round is asked again, and a second fenced answer blocks the item as the transport's fault), and the count is
empty (so the report would refuse whatever its verdict said). Item #788 hit both — the report it had carried three
blocking findings and the escalation says no reviewer refused.

## The single fix

One reader, `report_lines <file>`: the report's own lines with one backtick taken off each end. `report_field` and
`answer_complete` read through it, so every field the run decides anything by — `LOOP-BLOCKING`, `LOOP-DECISION`,
`LOOP-CORRECTIONS-ONLY`, `LOOP-TIMEOUT`, `LOOP-DEADLINE`, `VERDICT` — and the closing-line match itself see the line
the child wrote. A second reader, `report_verdict <file>`, gives the word on the verdict line, and the two
`grep '^VERDICT: …'` sites use it instead of their own anchor.

Nothing about the *meaning* of a report changes. The newline check in `answer_complete` is untouched; a report with no
closing line at all is still cut, and a report with no count line is still a refusal (`blocking_count` returns empty
and the round keeps the conservative reading).

## Blast radius

- `tools/loop/run.sh` — two new functions, two readers rewritten onto them, two anchors replaced. 46 `report_field`
  call sites and 4 `answer_complete` call sites are affected, all of them by gaining the fence strip and nothing else.
- `tools/loop/prompts/reviewer.md` — one sentence, so the accepted shape is stated where the block is shown.
- `guidelines/Backlog.recipes.md`:685 "What it does with an answer that arrived cut" — the paragraph that owns an
  answer's shape names the fence.
- `tools/loop-cases.sh` — one case (`run#aFencedClosingBlockIsACompleteAnswer`, declared over the engine, the adapter
  and the scenario fixtures), the list entry and the dispatch line.
- `tools/loop-mutations.sh` — record `83.1`, which drops the un-fencing from `answer_complete` and must turn the case
  red.
- `openspec/specs/agent-issue-loop/spec.md` — the added requirement, archived with this change.

Not in the blast radius, deliberately: `review_notes` (`tools/loop/run.sh:958`, base `:938`) reads `NOTE:` findings,
which the prompt asks for as prose above the closing block and never as a fenced one-liner; fencing it would change
what a note is without a measurement behind it.

## Rollback

Revert the change. The engine's readers go back to their anchors and the case goes red again; nothing the change
writes is part of any item's record, and no spec or state field is added, so no state needs migrating.

## Alternatives the evidence eliminated

- **Say in the prompt that a fenced line does not count.** The measured cost stays: the reviewer writes the shape its
  prompt shows it, so the answer is still read as cut, still blocked, and the item is still put on hold — the same
  escalation with a rule that blames the child for copying the block. The item's own required case also asks for the
  fenced report *to be read as complete*.
- **Strip fences from the whole report before anything reads it.** Same effect on the readers and one more pass over
  a file that already reaches 100 kB; `report_lines` is that pass, taken once per read.
- **Read the field with an optional leading backtick only.** Leaves the closing backtick inside the value, so
  `LOOP-BLOCKING: 3`` is malformed and `blocking_count` returns empty — the item's count would still be lost.
- **Widen `answer_complete`'s regex at its four call sites.** The fence is a property of every field the run reads,
  not of one regex: the count (`blocking_count`) and the verdict's word (`:2070`, `:2421`) would still be invisible,
  which is the half of item #788's cost the escalation's wording does not show.
