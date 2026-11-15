# Proposal — what a review round is handed is bounded, and the cut is named

**This change describes work that has already landed.** The whole of it is commit `290c3d8c` (its ref decor being
`pi-subagents/924-32852af-1564-s0-t0`), merged as `25e90bfa` and carried by the base `929b9e8f` this delta is written
on. The delta records what the tree does; it does not ask for code to be written. The commit carries the change's
case, its one mutation record and its reasoning, and no suite line — `evidence/gate.txt` states that rather than
quoting a verdict that does not exist.

## Why

**Root cause.** A review round was handed everything: the change's diff, the report the round before the gate wrote,
and the inventory of the change's own files. No key bounded any of it, so the reviewer paged the diff until its
context ran out, and the bound the loop already had — `LOOP_ITEM_CONTEXT_MAX_REVIEWER` — could only end the round
after the spend had happened.

**Evidence, from the item (issue #924, read 2026-11-15).** Two of the run's eight review rounds ended at
`LOOP_ITEM_CONTEXT_MAX=250000` with no verdict at all: `#883` at 250 418 tokens and `#898` at turn 110 under the turn
bound, with `review-1.diff` at 45 kB and 262 411 B respectively — `#898`'s diff 8.2× the cap this change records. The
six rounds that did report cost 5.8 M tokens each; the reviewer was 46.1 M of the run's 204.7 M (22.5 %), and
`#915` — a 20 kB diff — passed its round in 5.1 M with a report. So the round that reported was priced by the size of
the change rather than by the judgement it was asked for, and the round that did not report left the item as a
person's work.

**The single fix, as it landed.** (All line anchors are in `evidence/anchors.txt`, read from the base tree.)
`LOOP_REVIEW_INPUT_MAX_BYTES=32000` sits in the configuration's project block (`tools/loop/loop.conf:916`) with the
measurement it was priced from — above the 20 kB change that was read and below the 45 kB one that was not.
`bound_review_inputs` (`tools/loop/run.sh:1685`) caps each of the round's three parts. A part over the cap is handed
as its head, cut at a line boundary (`review_input_head`, `run.sh:1669`) and marked on its last line by
`review_input_mark` (`:1678`); everything the cut left out is named by path and line range in
`<round>.cut.md` — `review-1.cut.md` in the change directory — which the round's prompt names and reads by path, so a
finding can still point at a line the round was not handed. A round every part of which is under the cap is handed
exactly what it was handed before: the `{{REVIEW_INPUTS}}` placeholder sits on the bullet's own line in
`tools/loop/prompts/reviewer.md:31`, so an under-cap prompt is byte for byte the old one, no index is written and
no line about a bound appears.

**The scale travels with the cut.** The prompt states the bound and the figure it was priced at, and the scale —
`2 of 3 parts over the 32000 B cap · … cut (…) · review-1.cut.md` — is written to `<round>.scale`
(`run.sh:1782`) because the run calls the function in a `$( )` whose variables never reach the round's shell;
`state.sh done --review-input` records it as `metrics.reviewInput` (`state.sh:702`, `:850`, `:955`, one line and
nothing else) and `report.sh:365` prints it against the item, `none` where no item records a scale. The reviewer's
turn bound (`LOOP_CHILD_TURNS_MAX_REVIEWER=110`, `loop.conf:179`) is untouched: this bound is on what the round is
handed, not on how many turns it takes to read it.

**The red test.** `prompt#reviewerInputsAreBoundedAndTheCutIsNamed` (`tools/loop/tests/loop-cases.sh:4604`,
registered `:150`, dispatched `:895`) sets a 64-byte cap and asserts the marker on the headed part's own last line,
the index's paths and line ranges, the prompt's statement of the scale, the item's `metrics.reviewInput`, the
report's line, and — the other half — that an under-cap round carries no bound line, no marker and no index.
Mutation record `924.1` (`tools/loop/tests/loop-mutations.sh:1315`) returns from the cap gate
(`[ "$cap" -gt 0 ] || return 0`) so nothing is capped, and the case must go red.

**Owning requirement.** Nothing shipped owned this. No requirement of `openspec/specs/agent-issue-loop/spec.md`
bounds what a review round is handed: the shipped bounds are on turns (`LOOP_CHILD_TURNS_MAX_*`) and on the item's
context (`LOOP_ITEM_CONTEXT_MAX_*`), and the reviewer's prompt said only that a cut is not a missing artefact. The
delta therefore **adds** the requirement under `agent-issue-loop`, whose subject the review round is.

**Owning guideline section.** `guidelines/Backlog.recipes.md` §7b, which carries the rule beside the run's other
bounds.

## Surface

surface: 9 files, +355 −13 (`290c3d8c`'s own diffstat, in `evidence/surface.txt`): `tools/loop/run.sh` +209,
`tools/loop/tests/loop-cases.sh` +83, `tools/loop/loop.conf` +24, `guidelines/Backlog.recipes.md` +19,
`tools/loop/state.sh` +14, `tools/loop/tests/loop-mutations.sh` +10, `tools/loop/report.sh` +5,
`tools/loop/shared.tsv` +2, `tools/loop/prompts/reviewer.md` +1 −1. One key, one function and its three helpers,
one case, one mutation record, one added requirement with three scenarios.

## What Changes

1. **`LOOP_REVIEW_INPUT_MAX_BYTES`** (`loop.conf:916`): one figure, per part, with `0` or no value meaning no bound.
2. **`bound_review_inputs`** (`run.sh:1685`): the three parts capped, the head written at a line boundary and
   marked, the remainder indexed by path and line range, the scale written to `<round>.scale`.
3. **The prompt** (`prompts/reviewer.md:31`, `run.sh:1778-1785`): a bounded round says so, and names the index it
   reads by path; an unbounded round says nothing.
4. **The record and the report**: `metrics.reviewInput` one line long, printed against the item, `none` when no item
   records one.
5. **The case, the mutation record, the guideline and the delta** as above.

## What this does not do

- It does not cover the cap's `0` arm with a case. `prompt#reviewerInputsAreBoundedAndTheCutIsNamed` sets a positive
  cap and the default; the `0` or absent-value arm (`run.sh:1692-1693`) is read from the tree and pinned by no
  assertion, and the delta's scenario says `Case: none` rather than claiming one.
- It does not cover the index's own write. A second mutation record (`} > "$cut_file"` changed to `> /dev/null`) was
  "written twice and dropped twice" — the mutation file says so at `tools/loop/tests/loop-mutations.sh:1310-1314`,
  where the case stayed green in the harness and in a hand-run against a mutated engine copy. `924.1` falsifies the
  cap gate, not the write.
- It does not touch the reviewer's turn bound or its model keys, and it does not change a round the run's own
  deadline ends.
- It does not re-run or re-price the two rounds the item cites; their figures are the item's evidence, quoted, not
  re-measured here.