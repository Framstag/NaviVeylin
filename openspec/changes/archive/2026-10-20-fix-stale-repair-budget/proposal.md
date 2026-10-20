# fix-stale-repair-budget

## Why

The scheduled repair (`.github/workflows/sensors.yml:118`, `bash tools/issues/resolve-stale.sh
"$SENSORS_MODE" --max-items 25`) spends its bound on items it can only report. Run 39296403233
(2026-10-19T17:42Z) ended `read=173 due=25 repaired=4 parked=5 ambiguous=22 skipped=82 waiting=66 writes=18`:
16 of the 25 budgeted items reached neither write branch — `due=25` is `repaired=4` plus `parked=5` plus those
16 — so nothing was written for them, and the 66 behind them never got a slot. The `ambiguous=22` in that line
counts ambiguous tokens, not items.

**Root cause.** `due` is incremented from *reachability for a repair*, not from the write that follows it:
`tools/issues/resolve-stale.sh:190` (base `9e7e969`) does `due=$((due + 1))` right after the parked/skip
filters, before the two token loops run; an ambiguous suffix token (`:203` `ambiguous=$((ambiguous + 1));
continue`) records nothing in `unresolved`, and both write branches (`:236` on `repairs`, `:248` on
`unresolved`) require a non-empty result. So one mechanism explains the symptom: **a slot of the bound is
spent before it is known whether the run can write anything for the item.** An ambiguous-only item therefore
consumes a slot of every run for ever, and `:168`'s `while … "$due" -lt "$max_items"` stops the walk at the
head of the queue.

**Red test on HEAD** (`9e7e969`), added as `resolver#ambiguityDoesNotConsumeTheBound` — an ambiguous item
(`a/Dup.kt` + `b/Dup.kt`) first in tracker order, a repairable one (`app/src/Foo.kt`) second, `--apply
--max-items 1` (2026-10-20T09:17:07Z, exit 1, `evidence/red.txt`):

```
  the one slot the bound allowed went to the item a run can write: expected '1', got '0'
FAIL resolver#ambiguityDoesNotConsumeTheBound
```

On HEAD that run reports `due=1 repaired=0 parked=0 ambiguous=1 writes=0 waiting=1`: the bound was spent, the
tracker was not changed, and the writable item was never reached.

**Owning capability:** `stale-citation-resolution`, requirement "The resolver runs on its own, bounded and
idempotent" — and the scenario "The bound holds", which promises that a bounded run *repairs* the first ones.
**Owning guideline section:** `guidelines/Backlog.md` §7 "The loop" (the paragraph that states what the
resolver repairs and parks); the gate is the self-test `tools/resolve-stale-selftest.sh`, the CI step "Check
stale citation resolver".

## What Changes

- `tools/issues/resolve-stale.sh`: the `due` increment moved from "the item has a citation the ladder will
  look at" to one guard above the write branches — `if [ -n "$repairs" ] || [ -n "$unresolved" ]; then
  due=$((due + 1)); else skipped=$((skipped + 1)); fi`. The bound now counts the items a run **acts on** — the
  ones it writes in an applying run, the ones it would write in the default dry run — so an item it can only
  report is visited, accounted as `skipped`, and costs no slot. The invariant that made the
  ambiguity non-actionable is untouched: nothing is rewritten, parked or commented for it, and the
  `ambiguous` counter still reports it per token (D4, "reported, not guessed").
- `tools/resolve-stale-cases.sh`: the red case above.
- `tools/issues/resolve-stale.sh`: the header comment of the `due` rule updated, beside the guard that now
  carries it.
- `tools/resolve-stale-mutations.json`: mutation `5.11` restores the unconditional `due=$((due + 1))` and the
  new case must go red — the revert-check for this invariant.

## Spec

`openspec/specs/stale-citation-resolution/spec.md`, requirement "The resolver runs on its own, bounded and
idempotent": MODIFIED — the bound is stated as a bound on the items a run *acts on* — a repair or a park, and
in a dry run the items it would act on, since the default mode writes nothing and a bound on writes alone would
be unsatisfiable there — and one scenario is added, "The bound is spent where the tracker changes". The other
four scenarios of that requirement are repeated unchanged and now each names its own case.

## Cases and evidence

- `resolver#ambiguityDoesNotConsumeTheBound` — red on HEAD, green after the fix.
- `bash tools/resolve-stale-selftest.sh` — 22 of 22, offline, no token, no Gradle (`evidence/green.txt`).
- Mutation `5.11` — red with the guard removed, green again after the restore (`evidence/revert-checks.md`).
- `evidence/red.txt`, `evidence/diff.txt`.

Pre-existing, not caused and not repaired by this change: mutation `5.7` of the same set searches
`--status on-hold`, a literal HEAD has not contained since the status became a profile value
(`resolve-stale.sh:248` passes `"$hold_status"`); it reports `MUTATION-DID-NOT-APPLY` on the base commit as
well. It is a stale falsification record, not a behaviour, and belongs to its own item.

## Impact

`tools/issues/resolve-stale.sh`, `tools/resolve-stale-cases.sh`, `tools/resolve-stale-mutations.json` and the
spec above. No compiled file is touched, so no Gradle gate applies (`guidelines/Build.md` §2, the scope
block); the structural gate is `tools/resolve-stale-selftest.sh`, which CI runs.

Behavioural change, visible in the run's own line: `due` now equals `repaired + parked`, the visited-but-
unwritable items appear in `skipped` (so `read = due + skipped + waiting` still accounts for every issue), and
a run with `--max-items 25` reaches items that were previously starved behind the ambiguous head of the queue.
`waiting` keeps its meaning — issues never visited. Nothing else changes: no label, no comment, no closure.

Rollback: revert the commit. The bound then counts visits again — exactly the old behaviour, with no state to
undo, because this change writes nothing to the tracker by itself.
