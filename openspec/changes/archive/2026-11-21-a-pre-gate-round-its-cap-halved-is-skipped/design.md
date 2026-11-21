# Design: a-pre-gate-round-its-cap-halved-is-skipped

## Context

`bound_review_inputs` splits the diff into three parts and caps each at `LOOP_REVIEW_INPUT_MAX_BYTES`, writing what
it removed to `<round>.cut.md` and composing one line for the round's prompt (`LOOP-REVIEW-INPUTS`), which names the
cap and the share it took (`review_input_cut_scale`). The pre-gate round is launched from the same function as
round 1 (`run.sh`, the round-before-the-gate call site), so both rounds read the same scale and only the pre-gate one
is optional: a verdict it cannot give is a verdict round 1 gives anyway, on the same diff.

## Decision 1 — the precondition is the share the cut already measured

The round is skipped when more than half of the whole input — the aggregate share the scale line records across
every bounded part — was removed. That figure needs no new measurement and no new
key: `review_input_cut_scale` is composed for the prompt and recorded on the item, so the decision and the record
are the same string, and a later reader can re-take it from the cut file.

Half, not "any cut": #963's 5 % cut is a diff a round reads whole enough to judge, and skipping it would drop a
round that was going to answer. The cuts this rule answers are the ones past half — #901's and #902's, both 67 % —
while #894's 47 % and #931's 38 % sit under it and still run, which is why the figure is the rule and not a list of
items.

## Decision 2 — the item records the skip, the report counts it apart

A skipped round is not a refusal and not a cut: no child ran, so it has no verdict and no wall clock. The item's
record carries the reason beside the other review fields, and `report.sh`'s `review:` line gains the class rather
than folding it into `undecided`, whose meaning is "a round ran and did not answer" — the distinction issue #923
already forced on the bound classes.

The rule's owning prose is `guidelines/Backlog.recipes.md` section 7a, which states that the diff is read before the item's
gate; the diff edits that section to carry the skip, because a section that still asserts an unconditional round is
the rule this change reverses.

## Decision 3 — what was rejected

- **Raise the cap for the pre-gate round only.** Two caps for one diff is two definitions of "readable", and the
  larger one still cuts #902's 149 838 B part by a third.
- **Skip the pre-gate round entirely.** It is the round that catches a diff the worker's own report misdescribes
  before the gate's 15 minutes are spent; on a diff it can read whole it is the cheaper of the two rounds.
- **Cut the round's turn bound instead.** It would convert a round that ends at 110 turns into one that ends at 80 —
  a slower refusal of the same diff, and the same spend.

## The falsification

The new case is red before the change: with no precondition, an input cut past half still launches the round, so the
case's assertion (`no child, one recorded reason`) fails on the launcher's own record. The second case is what keeps
the change from swallowing the rounds it should keep: an input cut by 5 % must still launch.

## Open decisions

none
