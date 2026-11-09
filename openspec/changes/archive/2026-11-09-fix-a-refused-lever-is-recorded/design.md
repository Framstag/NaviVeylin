# Design — the refused lever is recorded where the bound's measurement lives

## The root cause

Section 7b of `guidelines/Backlog.recipes.md:254` derives the item context bound and states its value (`:456`–`:460`), and the
value is asserted in two more places — the profile's own comment (`tools/loop/loop.conf:202`–`:210`) and the key
inventory (`guidelines/BacklogTooling.md:148`). On the base commit none of the three said that raising it was measured
against the cuts it would remove and refused: `tools/look.sh 'refused lever|raise the stop|a wider bound|raising the
bound' --path guidelines` → `0 matches in 0 files`, and no file in the tree carried `#833`. The derivation was
therefore not falsifiable in the direction a reader pushes it — a pass that read `3.3 % reached it` met no account of
what a wider bound buys.

## The single fix

One paragraph in section 7b, after the paragraph that measures the bound and before the paragraph that prints its hit rate:
the lever above the stop is refused, with the run that shows why (three items recorded past the bound at 200 427,
200 733 and 201 981 tokens, each having already written its red-on-HEAD case and its change, each keeping a 509–554 kB
patch), and the value unchanged. The paragraph names #754's and #809's readings as what the bound owed instead,
because both landed and neither moves the number — the item's own relation, made checkable in the section a reader
meets.

The other two places that assert the value stay as they are: `tools/loop/loop.conf`'s comment states the key's
mechanics for every project that copies the profile, and `guidelines/BacklogTooling.md`'s table is a key inventory
with a column per key. A second statement of the measurement in either would be the rule stated twice —
`openspec/specs/documentation-ownership/spec.md:259` ("A rule that spans documents names one home": exactly one of
them states the rule normatively and the others reference it).

## Alternatives the evidence eliminated

- **Raise the bound** (the lever the item names). Eliminated by the item's measurement and by this change's own
  probe of the records: all three items the run of 2026-11-07T19:53Z recorded past the bound sat within two thousand
  tokens of it and each had already written its red-on-HEAD case and its change, so the bound is not ending an item
  before its work exists — a wider one buys more of the tail the cuts are made of. Not this change's decision to take
  either: `tools/loop/loop.conf:212` records the value, and an unattended worker may not move a value a person's
  analysis refused.
- **Leave the tree unchanged and let the tracker item be the record.** Eliminated: `tools/look.sh '#833'` → `0
  matches in 0 files`, and the dedup that refuses a re-proposal hashes `class|category|claim|anchor`
  (`tools/issues/create.sh:328`–`:334`), so a lever re-proposed in different words is a different fingerprint and is
  filed as a new item — the record has to be where the reader is.
- **A declared case in `tools/loop/tests/loop-cases.sh`.** Eliminated: the case would read the paragraph this change
  writes, which is a second reading of the record and a guard over prose rather than over behaviour; the suite's
  cases pin behaviour, and `fix-reversal-rules-name-their-record` refused the same shape for the same reason. The
  probe is evidence, runs at this change's verification, and is archived beside it.
- **A row in `guidelines/Backlog.why.md` §13 "Deferred, and why".** Eliminated: §13 is a table of deferrals and
  their reasons. A refused lever is neither deferred nor an adoption decision, and the bound's measurement, keys and
  derivation all live in section 7b, so a rationale-layer row would split one subject across two layers.
- **A row in `guidelines/Backlog.why.md` §12 "The decisions, and why".** Eliminated: §12 is the map of the
  backlog's own decisions, each row naming the change that made it, and this change decides nothing about the
  backlog — it records a refusal and leaves the bound at 200 000, so a row there would claim a decision this change
  did not take. The reasoning that answers the lever is this design's "Alternatives the evidence eliminated", which a
  reader reaches through the section that owns the bound.
- **A sentence in `tools/loop/loop.conf`'s comment instead.** Eliminated: it is the profile, copied into other
  projects (`PORTING.md`), and its comment already carries this project's measurement for a reader of the key. The
  refusal is about this project's bound and belongs in the section that routes the bound's derivation.

## Blast radius

One paragraph in one guideline section: `git diff --numstat 8a2f1adb -- guidelines/Backlog.recipes.md` → `10 0`, so
section 7b grows by 10 lines. The routed figure in `AGENTS.md` prices
`Backlog.md`'s section 7b stub at 0.2 kB, which does not change, and `tools/check-doc-routes.sh` reads references, not
sizes. No requirement, case, fixture or profile key changes, so no behaviour moves; the gate's own selection grows
only because this change's artifacts are in the tree — `LOOP-MEASURE-CHECK` names seven commands against the base's
four, and each of them is a document check the tree already runs. The change carries no `specs/` delta.

## Rollback

Revert the paragraph; the section returns byte for byte and `evidence/refusal-record-probe.sh` reports `tests=1
failures=1`, which is the signal the record is gone.

## Open decisions

none
