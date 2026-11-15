# Tasks — what a review round is handed is bounded, and the cut is named

The items are ticked because the work is in the tree: this change was written after commit `290c3d8c` landed, and
every task below names the path and line that carry it.

## 1. Measure before changing code

- [x] 1.1 Read what a round was handed and what it cost: `#898`'s `review-1.diff` 262 411 B, `#883`'s 45 kB, `#915`'s
  20 kB; the reviewer 46.1 M of the run's 204.7 M over eight rounds, 5.8 M for the six that reported, and two rounds
  ended at `LOOP_ITEM_CONTEXT_MAX=250000` with no verdict at all (issue #924).
- [x] 1.2 Read the bounds the loop already had and why none of them reached this: the turn bounds end a round after
  its spend and `LOOP_ITEM_CONTEXT_MAX_REVIEWER` ends it when the context is gone — both bounds on the reader, neither
  on what it is handed.
- [x] 1.3 Price the cap: above the 20 kB change that was read with a report and below the 45 kB one that was not,
  rounded up to the next round figure — `loop.conf:905-916` states the measurement; 32 000 B is about 8 000 tokens,
  1/31 of the reviewer's 250 000-token context.

## 2. The bound

- [x] 2.1 `LOOP_REVIEW_INPUT_MAX_BYTES=32000` in the configuration's project block (`tools/loop/loop.conf:916`), with
  `0` or no value meaning no bound.
- [x] 2.2 The cap is read from the project's file and a non-number or a zero returns before anything is written
  (`tools/loop/run.sh:1691-1693`).

## 3. The three parts, the head and the index

- [x] 3.1 `bound_review_inputs` (`run.sh:1685`) caps the change's diff, the round-before-the-gate report and the
  change's own inventory, and totals what it cut.
- [x] 3.2 A part over the cap is handed as its head, cut at a line boundary (`review_input_head`, `:1669`) and marked
  on its last line by `review_input_mark` (`:1678`) — the marker names the file it heads, the cap and the index, and
  tells the round not to page past it.
- [x] 3.3 Everything the cut left out is named by path and line range in `<round>.cut.md` (`:1695`), from the diff's
  own `+++` and `@@` lines, and the prompt names it so the round reads it by path.
- [x] 3.4 A round under the cap is handed what it was handed before: `{{REVIEW_INPUTS}}` sits on the bullet's own line
  in `tools/loop/prompts/reviewer.md:31`, so an under-cap prompt is byte for byte the old one and no index is written.

## 4. The scale travels

- [x] 4.1 The scale is a string naming the parts over the cap, the bytes cut and the index (`run.sh:1778`), written to
  `<round>.scale` (`:1782`) because the run calls the function in a `$( )` whose variables never reach the round's
  shell, and stated by the prompt (`:1785`).
- [x] 4.2 `state.sh done --review-input` takes it as one line (`tools/loop/state.sh:702`, `:850`) and stores it as
  `metrics.reviewInput` (`:955`).
- [x] 4.3 `report.sh:365` prints it against the item, and `none` where no item records a scale.

## 5. What the key does not touch

- [x] 5.1 `LOOP_CHILD_TURNS_MAX_REVIEWER` is unchanged (`loop.conf:179`): the bound is on what the round is handed,
  not on how many turns it takes to read it.
- [x] 5.2 The round the run's own deadline ends is unchanged — the deadline is the run's, not the input's.

## 6. The case and the mutation record

- [x] 6.1 `prompt#reviewerInputsAreBoundedAndTheCutIsNamed` (`tools/loop/tests/loop-cases.sh:4604`, registered `:150`,
  dispatched `:895`): a 64 B cap over a diff and an inventory, the head's marker, the index's paths and line ranges,
  the prompt's line, `metrics.reviewInput` and the report — and, in the same case, that an under-cap round carries no
  bound line, no marker and no index.
- [x] 6.2 Mutation record `924.1` (`tools/loop/tests/loop-mutations.sh:1315`) makes the cap gate return early, so
  nothing is capped and the case must go red.

## 7. The guideline and the delta

- [x] 7.1 `guidelines/Backlog.recipes.md` §7b carries the key, the index and the scale beside the run's other bounds.
- [x] 7.2 The delta adds `agent-issue-loop`'s requirement "What a review round is handed is bounded, and the cut is
  named" — no shipped requirement of the capability owned this — with three scenarios.

## 8. Evidence

- [x] 8.1 `evidence/surface.txt` — the commit's own diffstat; `evidence/gate.txt` — why no suite verdict for this
  commit exists to quote, and what the commit does hold.
- [x] 8.2 `evidence/anchors.txt` — every anchor this change cites, as the command and its output read from the base
  tree.

## 9. What this change does not cover

- [x] 9.1 The cap's `0` and absent-value arm is recorded as uncovered: `prompt#reviewerInputsAreBoundedAndTheCutIsNamed`
  sets a positive cap and the default, the arm is read from `run.sh:1692-1693`, and the delta's scenario says
  `Case: none`.
- [x] 9.2 The index's own write is recorded as uncovered by any mutation: a second record (`} > "$cut_file"` changed to
  `> /dev/null`) was "written twice and dropped twice", the mutation file says so at
  `tools/loop/tests/loop-mutations.sh:1310-1314`, and `924.1` falsifies the cap gate rather than the write.
- [x] 9.3 Neither the reviewer's turn bound nor the item's context bound was changed, and no round the run's deadline
  ended is touched.
- [x] 9.4 The two rounds the item cites were not re-run: their sizes and costs are the item's evidence, quoted, not
  re-measured here.