## Root cause

A repair is counted, and commented, from the fact that a rewrite was *attempted*. `tools/issues/resolve-stale.sh`
(base `648d2bc`) accumulates a human bullet in `repairs` for every token the ladder resolves (`:209`, `:225`), and
every consequence is gated on that variable being non-empty: the due charge (`:244`), the body write and the
`citation repaired` comment (`:249`–`:252`), the `writes` count (`:253`) and `repaired=$((repaired + 1))`
(`:255`). Nothing compares the produced body with the input, and nothing re-classifies the token.

The attempt becomes a *no-op* through the basename half. `tools/issues/rewrite.awk:18` refuses a match whose left
character is `[A-Za-z0-9_.-]` and whose right one is `[A-Za-z0-9_]`; `/` is deliberately outside both classes,
because a full path such as `old/Place.kt` legitimately follows a separator. So when the body cites both a
doubled-directory token `D/D/f` and the bare name `f`, the suffix loop rewrites `f` **everywhere** — including at
the tail of `D/D/f`, which becomes `D/D/D/f`. The absent loop then looks for `D/D/f`, finds it starting at the
second segment of `D/D/D/f` (preceded by `/`, which the same rule admits), collapses it, and writes `D/D/f`
back. The body leaves the loops byte-identical to its input while `repairs` holds two bullets — the measurement
the apply run of 2026-10-19 shows: `repaired=14`, of which 4 bodies were never written.

One mechanism, not two: the run reports the attempt, never the change. The boundary gap is only the way an
attempt is manufactured that changes nothing; the accounting is what makes it a false report.

## The single fix

Two guards, both of them the same rule — *a repair is a change to the record*:

1. `tools/issues/rewrite.awk` takes `-v standalone=1`, which additionally refuses a match preceded by `/`. The
   suffix loop passes it, so a basename repair rewrites only an occurrence that stands as its own citation and
   never the tail of a longer path. The absent loop writes full paths and keeps the rule unchanged, so nothing
   about its boundary behaviour moves.
2. The two loops record pairs (`old` → `new`) instead of bullets. After both loops, a pair is kept only when all
   three hold — the body differs from the input, `issues_citation_class "$new"` is not `absent`, and the old
   token is not present in the new body as an `absent` citation (asked of `issues_citation_matrix_of "$new_body"`,
   so a token that survives *inside another path* is caught while the target path that now legitimately contains
   its own name is not). Kept pairs compose `repairs`; a failing pair becomes an `unresolved` bullet naming what
   failed, so the item takes the park branch with its reason instead of the repair branch with a false claim.

Guards 1 and 2 are not alternatives: 1 alone leaves the accounting lying about any other no-op (a successor the
rename map names but the tree no longer holds — red case 3), and 2 alone turns a repairable item into a park
because the cancellation it detects was manufactured by the other half (red case 1, which asserts the *written*
body and would see no body write at all). The park is the honest fate: the script's header states that a refusal
recorded nowhere is the defect this tool exists to fix (#163), so an item whose citation stays stale is parked
with a reason rather than silently skipped.

The `due` guard keeps its place and now charges verified repairs only, which is `#199`'s rule unchanged in
substance: an item the run writes nothing for costs no slot.

## Blast radius

`tools/issues/resolve-stale.sh` (pair collection in both loops, one verification block, `repairs` composed after
it, comments), `tools/issues/rewrite.awk` (one flag and one condition), `tools/resolve-stale-cases.sh` (three
cases, one helper), `tools/resolve-stale-mutations.json` (one search string follows the changed line, three records
added), the spec delta. No compiled file, no change to the classifier, the ladder, the parking rule, the
ambiguity rule, the bound's semantics or the residue metric's measurement.

## What the evidence eliminated

- **Order the loops by specificity — longest token first, bare basename last** (the issue's first sketch, and
  the alternative a reader reaches first). Eliminated by measurement: the absent loop's collapse of `D/D/f`
  *creates* `D/f`, whose tail is the bare name `f`, so the trailing suffix pass would re-repair it into
  `D/D/f` again unless the basename rewrite refuses a `/`-preceded occurrence — i.e. the ordering is only
  correct together with guard 1, and guard 1 alone is then correct in either order. Two mechanisms where one
  suffices; the run also pays a second matrix pass for it.
- **Count a repair from the per-token text comparison** (`[ "$before" != "$after" ]` around each rewrite).
  Eliminated by the measurement in the root cause: each individual rewrite *does* change the text — `f` becomes
  `D/f`, and later `D/D/D/f` becomes `D/D/f` — the cancellation is only visible across both loops, so the
  comparison has to be item-level (the body against the input) and the resolution has to be asked of the token
  after the loops.
- **Detect the no-op with a substring test** (`case "$new_body" in *"$new"*)`) instead of the citation matrix.
  Eliminated by case 1: the repaired body legitimately contains `tools/alpha/alpha.sh`, so a substring test would
  reject the very repair that succeeded; the matrix asks whether the *citation* is absent, which is the question
  the pre-screen asks.
- **Refuse the whole item when one pair fails** (no park, `skipped`), reading the issue's "post no comment when
  nothing changed" literally. Eliminated: the item is then refused by the pre-screen on every later run with
  nothing on the tracker saying why — the state #163 was filed about. The park comment claims no repair; it
  states the failure, which is what the "nothing changed" clause protects.

Honest cost: an item whose repair does not verify now lands in `parked` instead of `repaired`, so the run's line
moves those items between two counters (`repaired` falls, `parked` rises). That is the report becoming true, not
a new state.

## Rollback

Revert the commit. A no-op counts as a repair again, and a `/`-preceded basename occurrence is rewritten inside
its longer path again — exactly the base behaviour. No tracker state exists to undo: this change writes nothing
to the tracker by itself, and the four hand-repaired bodies of 2026-10-19 stay repaired under either behaviour.
