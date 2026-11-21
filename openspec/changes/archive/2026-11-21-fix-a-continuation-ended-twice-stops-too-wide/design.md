# Design — a continuation the same ceiling ends is the item's shape

## Root cause

The reading a second child the ceiling ended is a measurement of the **item's** size, and the run reads it as a bound
it enforced on a phase: `child_ended_by_context_bound` sets `cut` with `bound` as the stop reason, so the vocabularies
that say "this item does not fit one child" — `too-wide`, `agent/needs-shaping`, `roles.shapingVerdicts` in
`tools/tooling.json` — exist, are wired, and are never written on this arm (`tools/loop/run.sh:3043` before this
change, the continuation block that ends at `cut`). The run of 2026-11-17T10:58Z → 16:25Z is that reading's price:
five items (#883, #917, #950, #894, #911) were continued from a killed run's kept patch, of which the four that
stayed blocked paid 78.7 Mtok of the run's 120.3 Mtok, closed none, and each reached 142 576–143 943 against the
recorded 143 124.

## The single fix

One reading, taken where the continuation's own `LOOP-TIMEOUT` line already tells the run which bound ended it: when
the continuation's exit status is the child timeout **and** `child_ended_by_context_bound` holds for its report, the
run sets `continuation_too_wide`, and the outcome arms record `skipped` with the verdict `too-wide` instead of letting
`last_exit`/the guards decide `blocked`, `refused` or `child-blocked`. `too-wide` is already a shaping verdict in the
profile, so `labels.sh` writes the shaping label and the hold and no decision label: a hold is not a question (spec,
"A hold is not a decision").

Beside it, `continuation_listing` reads the last line the child itself wrote — every `LOOP-*` line is the run's and is
excluded — because a child the run ended writes no report block. That line is the phases the child listed, which is
what a person reshapes the item from. It is empty when the child wrote no prose, and the note then names the ceiling
alone.

The ceiling one continuation per item is unchanged: no third child starts, and the meaning of that ceiling moves from
"the item's `cut`" to "the item's shape".

## What stays as it was, and why

- **A turn-bound or deadline-ended continuation stays `cut`.** Those two measure the phase and the run, not the item:
  a turn bound says the child took too many turns, a deadline says the run ran out of clock. Neither is evidence that
  the item does not fit one child, so neither is `too-wide`.
- **A kept patch the applier does not take stays `cut` with the non-apply recorded.** The item's size is not what is
  known there; the patch's base is.
- **No prompt skeleton and no fixture changes the continuation's own prompt.** `worker-prompt-continuation.md` already
  names the bound it is continued under (`BOUND_LINE`), so the child needs nothing new to be readable here.

## Blast radius

`tools/loop/run.sh`: one helper beside `child_ended_by_context_bound`, two locals reset per item, the block that sets
them where the continuation is started, and one outcome arm before the `last_exit` guard. Nothing outside the run's
own item loop reads `continuation_too_wide` or `continuation_phases`. The item's record shape is the one every other
`too-wide` item already has (`skipped` + verdict + no stop reason), so no reader, report column or tracker label
learns a new shape.

## Alternatives the evidence eliminated

- **A third child.** The run of 2026-11-17 spent 78.7 Mtok over four items that each needed more than a child's
  context; a third child buys the same ceiling again. Rejected by the 120.3 Mtok the run had.
- **`blocked` with a note naming the size.** `blocked` is the verdict a *failure* carries, and its guards already
  record it — that is exactly the state the four items ended in: each stopped at the recorded 143 124 checkpoint and
  was blocked `refused`, `outside-write` or `child-blocked`, with `cut 0` and `bound 0 of 14` in the run's report, so
  nothing there names the item's size. The shape the rule covers is a continuation a context ceiling ends —
  `run#childPastItsContextBoundIsCut`. The shaping verdict is the one that keeps the item out of the queue until a
  person reshapes it.
- **`too-wide` for every continuation the run ends.** `too-wide` says the item is too large, which a turn bound does
  not say: a child ended on its turn bound may be inefficient rather than oversized, and reshaping it would be the
  wrong answer. The `bound` key on the item is the record that tells a person which of the two happened.
- **Reading the child's whole report for the phases.** A child the run ended writes no report block — the run's own
  `LOOP-TIMEOUT` line is appended after its prose — so the whole report is the child's prose plus the run's lines.
- **A new token key for the shaping verdict.** The profile already declares `roles.shapingVerdicts` and
  `tools/tooling.json` already maps `too-wide` → `agent/needs-shaping`; a second vocabulary would be two names for
  one reading.

## Rollback

Revert the four files: the outcome arm and the helper in `tools/loop/run.sh`, the case in
`tools/loop/tests/loop-cases.sh`, the record in `tools/loop/tests/loop-mutations.sh`, and the paragraph in
`guidelines/Backlog.recipes.md`:1690. Nothing is migrated and no state file gains a key, so a reverted tree reads the
items this change left `too-wide` as ordinary held items; the labels are the tracker's and stay on the items.

## Surface

`surface: 1 case added, 1 case rewritten, 1 mutation record added, 1 guideline paragraph realigned, 1 spec
requirement modified (1 scenario added, 1 clause corrected) — tools/look.sh 'second context cut'` (on the base, 3
files asserted the rule: `tools/loop/run.sh` (the `cut` arm), `guidelines/Backlog.recipes.md:1688` (the paragraph, at
`:1690` after this change) and the archived design of `2026-11-11-fix-a-cut-child-is-continued-by-its-run`, which is a
record and is not edited; this change rewrites the first two and leaves the record standing) and `tools/look.sh
'childPastItsContextBoundIsCut'` (the case whose tail asserted the `cut`). One case added (`run#aContinuationEndedOnTheSameCeilingStopsTooWide`), one rewritten
(`run#childPastItsContextBoundIsCut`), one mutation record (`956.1`), one guideline paragraph
(`guidelines/Backlog.recipes.md:1690`), one spec requirement (`agent-issue-loop`, "The bound on a child is priced in
what it spends, not in how many turns it takes").

## Verification

Red on the base: `run#aContinuationEndedOnTheSameCeilingStopsTooWide` fails on `8e3861c4c0c8bff22a36ff291e30385b7497f420`
with `expected 'skipped', got 'cut'` (`evidence/red-on-HEAD-8e3861c4.xml`, `tests=1 failures=1`,
2026-11-21T02:54:53Z). Green on the change: the same case plus the three cases the delta reaches
(`run#childPastItsContextBoundIsCut`, `run#aContextCutWorkerIsContinuedByItsOwnRun`,
`run#aWorkerAtItsCheckpointIsContinuedBeforeTheBound`), each copied into `evidence/` as it was produced. The mutation
record `956.1` puts the guards back in charge of that reading, the tree this change was filed on.

## Open decisions

none
