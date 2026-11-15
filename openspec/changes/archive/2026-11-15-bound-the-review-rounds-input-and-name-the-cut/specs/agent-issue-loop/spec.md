## ADDED Requirements

### Requirement: What a review round is handed is bounded, and the cut is named

The run SHALL cap each part a review round is handed — the change's diff, the round-before-the-gate report
and the change's own files — at `LOOP_REVIEW_INPUT_MAX_BYTES`, where `0` or no value SHALL be no bound. A
part over the cap SHALL be handed as its head, cut at a line boundary and marked, the rest named by path and
line range in an index the round reads by path. A round under it SHALL be handed what it was handed before,
and a cut SHALL be recorded on the item and printed by the report.

#### Scenario: A part over the cap is handed as its head and the rest is named

- **WHEN** `LOOP_REVIEW_INPUT_MAX_BYTES` is 64 and the change's diff and the inventory of the change's files are
  both over it
- **THEN** the diff the round is handed is its head, cut at a line boundary, its last line a `[LOOP-CUT: …]`
  marker naming the file it heads and the cap, the prompt states the round's input is bounded this round and by
  which figure and names `review-1.cut.md`, that index names every path the cut left out with its line range, the
  item records the scale (`over the 64 B cap`, the index's name) under `metrics.reviewInput`, and the report
  prints it against the item — so a cut round does not read as a whole one
- **Case**: `prompt#reviewerInputsAreBoundedAndTheCutIsNamed`

#### Scenario: A round under the cap is handed what it was handed before

- **WHEN** every part of the round is under the cap
- **THEN** the prompt carries no line about a bound, no `[LOOP-CUT:` marker and no instruction to stop at one, no
  `<round>.cut.md` is written, the item's `metrics.reviewInput` is null, and the report prints `what the bound cut
  from a round's input: none` — a round the bound did not touch is the round the key found
- **Case**: `prompt#reviewerInputsAreBoundedAndTheCutIsNamed`

#### Scenario: A configuration that records no bound cuts nothing

- **WHEN** `LOOP_REVIEW_INPUT_MAX_BYTES` is `0`, or the project's `loop.conf` does not record it at all
- **THEN** no part is capped, no index is written, the round's prompt carries no line about a bound and the item
  records no scale, because a bound of zero bytes would refuse the round the input it is handed to read
- **Case**: none — no case pins the `0` arm: `prompt#reviewerInputsAreBoundedAndTheCutIsNamed` sets a positive cap
  and the default, so the arm is read from `run.sh` and pinned by no assertion