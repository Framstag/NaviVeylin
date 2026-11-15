# A review round a bound ended is the item's stop class

## Why

The shipped requirement "An item the run's own bound cut is recorded `cut`, not `blocked`" says that the item
carries `bound` as its stop reason *when the bound ended the item's own child, while a round the run ended carries
none, as it did before*. That second clause is the defect (the run's items #898 and #883, both recorded `cut` with
`stopReason: null` though a reviewer bound ended them): a bound that ends a review round is a
bound the run enforces, and the item is what it ended, yet the record says nothing about a mechanism — so the reader
who asks "how did this item end?" gets the same `cut` with an empty `stop` cell that an arm recording nothing would
leave.

Measured over the loop archive in the project tree, 2026-11-15 (`.pi/issue-loop/runs/*.json`: 169 state files,
511 items):

| reading | count |
|---|---|
| items recorded `cut` | 39 |
| `cut` with `bound` and the key | 24 |
| `cut` with no class at all | 15 |
| of those, whose reason names a review round the run ended | 15 |

The nine clean ones are #787 (round 2), #782, #790, #788 (pre-gate), #753, #819, #722 (round 1), #835 (round 2) and
#829 (round 1); six more (#857, #791 twice, #875, #861) carried the round's sentence behind a continuation that
decided the item. Every one of the fifteen is a round ending — and each is a `cut` whose `stop` cell reads `-` in the
table a person scans first, and which the item's own `bound:` rate counts by its result alone.

#923 answered the neighbouring question without reversing this clause: it recorded the round's ending as
`metrics.roundBound: {round, boundKey, boundValue}`, a field of the round and explicitly not of the item, and
`report.sh` counts the rounds a bound ended on a line of its own. That keeps the round's fact; it leaves the item's
one unanswered.

## Surface

`tools/loop/run.sh` (the arms that end a review round), `tools/loop/state.sh` and `tools/loop/lib.sh` (the prose
their report and their class predicate state), `tools/loop/report.sh` (the bound-rate prose),
`tools/loop/tests/loop-cases.sh` and `tools/loop/tests/loop-mutations.sh`, the shipped requirement above, and the
two paragraphs of `guidelines/Backlog.recipes.md` §7b and §7e that repeat the reversed clause.

## What Changes

- `run.sh` gives a review round's ending the item's class: the pre-gate arm and the two after-gate arms (a turn or
  context bound in the round's `LOOP-TIMEOUT` line, and the run's own deadline) set `stop_reason="bound"` beside the
  `cut` they already record, and the key the item carries is the round's own, read from the same `LOOP-TIMEOUT` line
  by the same reader (`bound_key_value`) the item's own child's key is.
- `metrics.roundBound` stays exactly as #923 writes it: the round, its key and its figure, beside the class now
  rather than instead of it. The report's `rounds a bound ended:` line and its `round` column do not change.
- `state.sh`'s `stop` column already prints `bound <key>=<value>` whenever the record carries the class and the key,
  so a review round's cut now reads that instead of `-`; no code path in `state.sh` changes, and its prose is updated
  to say so.
- `lib.sh`'s `bound_class_jq` keeps refusing to infer a class from a round's fields — the class is the run's write,
  and the records a tree wrote before this change must not be given one on read.
- The shipped requirement is modified: the clause that a round "carries none" becomes a clause that a round a bound
  ended carries the class and key, and two scenarios gain the assertion; the other six scenarios are carried
  verbatim.
- `guidelines/Backlog.recipes.md` §7b (the deadline/round cut) and §7e (the class, the class vocabulary and the
  `roundBound` field) state the new rule.
- A case, `run#aReviewRoundBoundIsTheItemsStopClass`, asserts the class and the key on the item for a review round
  the reviewer's turn bound ended; `run#aPreGateReviewEndedAtItsBoundNamesIt` gains the same assertion for the round
  before the gate. Three mutation records (`898.1`, `898.2`, `898.3`) restore the old behaviour — the after-gate
  class, the pre-gate class, and the key the round's line yields — and each reds its case.

## What this does not do

- It does not change the vocabulary: the class is `bound`, the key is the one the bound is recorded in, and no new
  field, result or label is added.
- It does not move a round's ending out of `metrics.roundBound`, and it does not change what the bound rate counts.
- It does not touch the continuation path (#851), whose records read `cut` because the continuation decides the
  item; that arm is a different rule, and `run#aBoundEndedItemsKeptWorkIsResumedByTheNextStart` keeps it.
- It does not give the archive's older records a class on read: `lib.sh` still derives none for them, and the
  `bound:` rate still counts their result.
- **It is not the implementation of issue #898.** That issue is "Settings writes have one owner: SettingsController
  and the SettingSpec registry" and its work is another session's, shipped and closed on 2026-11-15. The number in
  this directory's name, in `898.1`–`898.3` and in the archived issue comment is the *run's item id* — the item
  `stopReason: null` was found on — and the change answers the loop's accounting, not Settings. The citation was
  corrected on 2026-11-15 after the collision was found; the directory name stays, because a shipped archive is a
  record and a rename would make every reference to it a second wrong number.