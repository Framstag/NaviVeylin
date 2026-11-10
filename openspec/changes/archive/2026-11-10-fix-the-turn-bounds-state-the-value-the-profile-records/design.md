# Design

## Root cause

A guideline section that states a bound the profile records states the measurement that set it, in the same list and
the same tense as the value in force, so the two cannot be told apart — and no check reads the values at all. The
prose therefore keeps the numbers of the day it was written (`guidelines/Backlog.recipes.md` section 7b :406-408 and
:446-448, `guidelines/BacklogTooling.md` :137-139) while `tools/loop/loop.conf` :176-180, :214 records the six the
run applies.

## The single fix

State, in section 7b, one table: the role's key, the value `tools/loop/loop.conf` records, and the measurement that set it —
the shape #833 landed for `LOOP_ITEM_CONTEXT_MAX` (a value beside its measurement, in one place). The two prose lists
become that table and a pointer to it; `guidelines/BacklogTooling.md`'s key row stops restating values and points at
the table, so the fact has one home in `guidelines/`. The prose around the table points at the row it means and states
no figure of its own, so every value the section states for a bound is one the check compares.

Then a check that reads both sides and fails when they differ: `tools/check-doc-values.sh` takes every
`LOOP_CHILD_TURNS_MAX_<ROLE>` key from the profile, finds the row that names it in the section 7b table, and prints
`LOOP-CHILD-TURNS-MAX: <role> prose <n> config <n>` (exit 1) when the two figures differ, when the profile records a
key the table has no row for, or when the table has a row the profile records no key for. It is wired into
`LOOP_GATE_SUITES` for the paths that can drift it (`*.md`, `tools/loop/loop.conf`, and its own files), so a future
value change reddens the gate instead of waiting for a reader.

## Blast radius

- 2 guideline documents, 3 passages (prose only; no rule is added to section 7b, one statement of each value replaces two),
  and in round 2 the four sentences of section 7b that still stated the rework round's bound, which now point at the
  table's row.
- 1 new check, 1 new self-test — no engine line changes: `tools/loop/loop.conf` gains suite pairs only, and
  `tools/check-portability.sh` reads the engine, not the profile's values.
- 1 requirement added to `documentation-ownership`; `agent-issue-loop` is untouched — it owns the header token that
  reports the bounds, not the prose that states them.
- Cases that read the bounds (`tools/loop/tests/loop-cases.sh`, 22 matches) read the profile, never the guideline, so
  none changes.

## Rollback

Revert the commit: the check and its self-test leave with it, `loop.conf`'s suite pairs leave with it, and the three
prose passages and the four sentences that point at the table's row return to the measurement-as-bound shape they had. Nothing the run executes reads the check, so a
reverted tree runs exactly as before.

## Alternatives the evidence eliminated

- **Fix the prose only.** Rejected: without the check the next value change recreates the finding, which is the
  item's own "what it owes".
- **Extend `tools/check-doc-routes.sh`.** Rejected: that check's claim is that a reference resolves and its price is
  measured; a value compared against the profile is a different claim with a different failure line, and a second rule
  in it would buy a larger self-test and mutation set for no fact in common.
- **Have the check read the run's header token** (`child-turns-max=worker:200,…`) instead of the profile. Rejected:
  the header is a rendering of the profile, so a check comparing it with prose reports a rendering's drift and needs a
  run to exist; the profile is the record the prose is written against.
- **State the values in the key table of `guidelines/BacklogTooling.md` and compare there.** Rejected: that document
  answers "what does this key mean", and every value it restates is a second home for a fact section 7b already owns.
