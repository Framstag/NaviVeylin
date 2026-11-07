# Proposal — a cut item is named to the caller as work to resume, and not as a gate to re-run

## Why

A run that ends one of an item's children at a bound records the item `cut` (issue #788, landed on main as
`2026-11-07-fix-a-bound-ended-item-is-recorded-cut`). It writes no decision label, and it asks the owner nothing —
which is right, because the bound is the run's own clock and not a finding about the item.

What it did not do is tell the caller the item is waiting. `tools/loop/report.sh`'s "actions the caller owes" list
read six sources, and a `cut` item fell into none of them: the list named no branch, no decision, no worktree — and
the one arm that did look at a cut item read it as *no gate result*, printing `re-run the gate for #N` for an item
whose phase the run had ended before any gate was owed. So the item a bound cut was the one outcome a caller had to
find by hand, in a list whose whole purpose is that they do not have to.

Measured on the state `items/788` carries: an item recorded `result=cut` with `metrics.gate` empty produced
`re-run the gate for #21` and no line saying the item is resumable.

## What changes

1. `tools/loop/report.sh` — the "no gate result" arm skips a `cut` item: the run ended its child before the gate was
   owed, so asking for that gate is a request the item cannot answer.
2. `tools/loop/report.sh` — a seventh source: an item recorded `cut` becomes `resume #N — the run ended a child of it
   at a bound, so no decision is owed — <the note the run recorded> · transcript <dir>`. The note is the run's own
   sentence about which bound ended which child, so the caller reads the mechanism rather than a category.
3. The `none` line names the seventh source, so an empty list still says what it covered.

## What this does not change

The item's record. A `cut` item keeps the result, the mechanism, the patched leftovers and the branch it always had;
nothing here writes a label, asks a question or re-runs a phase. The change is the report's reading of the state,
which is the only thing a caller has after the run's process is gone.

## Blast radius

One engine file and one case file: `tools/loop/report.sh` (three hunks) and `tools/loop-cases.sh` (one case extended
with two assertions, its `local` list gains one name). No compiled file is touched, so no build and no Gradle suite is
owed; the gate is the loop's own suite over the paths this change touches.

## Provenance

The report's half of issue #788. The branch-state half of that item — a branch a cut *rework* worker leaves, which the
pre-screen queues as work rather than as a decision — is not here: it collides with the restructure #790 landed on
the same cases, so it is filed separately rather than folded in.
