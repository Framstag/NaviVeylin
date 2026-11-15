# Design — a review round a bound ended is the item's stop class

## Root cause

`run.sh` writes the item's outcome in one place, and the class that goes with it in another. #865 gave every block
an arm that names its own class (`block()`, `stop_reason="$1"`), and the bound arms that could not be a single call
kept a separate write: a phase the run's bound ended is `cut`, and the class it carries is set beside it. The
review-round arms did not set it, because the shipped requirement says a round the run ended "carries none", and
#923 answered that sentence by recording the round's ending where the sentence allowed it — in the round's own field
(`metrics.roundBound`). The class stays empty, so `state.sh`'s `stop` column, which prints a class only when the
record carries one, prints `-` for every review-round cut, and the item's record cannot answer which bound ended it.

The bound is not a phase's property. `LOOP_CHILD_TURNS_MAX_REVIEWER` and `LOOP_ITEM_CONTEXT_MAX` are values the run
enforces on a child it started; whether that child is the item's worker or its reviewer changes which child spent the
budget, not whose budget it was. The clause was a statement about where the field was allowed to be written, and the
two issues are its cost measured.

## The single fix

A review-round ending writes the item's class, through the same field and the same reader the item's own child's
ending uses:

- **Before the gate** (`run.sh`, the `pre_gate_cut` arm): `stop_reason="bound"` beside the `blocked` reason the
  pre-gate cut already writes, which the cut flip at the end of the item turns into `cut`. The key is derived by the
  existing reader from that reason's quoted `LOOP-TIMEOUT` line, exactly as the item's own child's is.
- **After the gate, at a bound** (`run.sh`, the `reviewer_timeout_line` arm): `stop_reason="bound"` beside the
  `outcome="cut"` it already sets, and the round's key and figure are read once, by the same reader, and carried on
  the record through the existing `--bound-key`/`--bound-value` arguments.
- **After the gate, at the run's deadline** (the `reviewer_deadline` arm): the same class and no key — the deadline is
  a clock, not a value `loop.conf` records, which is the rule the item's own child's deadline cut already follows.

The key cannot be promoted where the review rounds run: they are after the block that derives the item's key from its
reason, and moving that derivation is a change to the sequence every other arm depends on. So one conditional stands
between the last arm and the outcome write: when the item is a `cut`, its class is a bound and it has no key, the key
and figure the round recorded are the ones the item carries. Its guard is what keeps it from speaking for an arm
that has its own key, and for the deadline arm, which has none.

`metrics.roundBound` keeps its shape and its meaning: the item now says *which bound ended it*, and the round's field
says *which round it happened in*. Two fields, two questions, no folding — the reason the field was made in #923.

## Blast radius

- The item's class gains one value on paths that previously carried none: a review-round cut. `state.sh`'s `stop`
  column prints it (no code change: the guard reads the class and the key the run writes), the `decide` list is
  unaffected (a `cut` writes no label and does not enter the streak), and `state.sh next` reads the result, not the
  class, so the two-blocked rule is untouched.
- `report.sh`'s bound rate already counts every `cut` by its result, so its figures do not move; the mechanism half
  of the disjunction now answers for a review-round cut too, which is what the wider half was for.
- Records a tree wrote before this change keep their empty class. `lib.sh`'s `bound_class_jq` still refuses to infer
  one from a round's fields, because a derived class on a round would give the archive's older cuts a key that a
  person could mistake for the run's own write.
- The mutation set: three records (`898.1`, `898.2`, `898.3`). `898.1` drops the after-gate class, `898.2` the
  pre-gate one, `898.3` the promotion of the round's key; the first and third red
  `run#aReviewRoundBoundIsTheItemsStopClass`, the second reds `run#aPreGateReviewEndedAtItsBoundNamesIt`.

## Rollback

Restore the two `stop_reason="bound"` lines on the review-round arms and the promotion conditional; the state
records written in between keep a class the spec would then call one too many, which a later run reads as any bound
cut — a `cut` with `bound`, its work left for the next run. Nothing else in the record changes, and
`metrics.roundBound` is unaffected by the rollback.

## Alternatives the evidence eliminated

- **Deriving the class in `state.sh` from the round's ending** — rejected: that is the read-side inference
  `lib.sh` explicitly refuses, and it would give every older record a class the run never wrote.
- **A new field for a round's bound** — rejected: the vocabulary is `bound` and the key, and a second class field
  would make `state.sh`'s `stop` column and the bound rate read two shapes.
- **Leaving the class off and widening the report's `bound:` line only** — rejected: that is #923, and the item's own
  record still cannot answer which bound ended it.
- **Naming the class at the moment the round is recorded** — rejected: the round's record is written before the
  item's outcome is known (a round can end and the item still pass on a correction round), so the class would be
  written for a phase the item did not end at.

## Open decisions

None.