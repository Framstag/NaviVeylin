## Revert-check for this change

Mutation `5.15` of `tools/resolve-stale-mutations.json` — the `body_cites` predicate made to answer "yes"
unconditionally (`return 0`), which is the fix removed and the base behaviour restored.

Command: `bash tools/mutate.sh --mutations tools/resolve-stale-mutations.json --only 5.15`
Transcript: `evidence/revert-checks-5.15.txt`, `revert-check-5.15.txt` in the run's evidence directory.

```
5.15	resolver#titleSuffixBesideItsCanonicalPathIsSettled	a token the body does not carry being settled instead of rewritten into a no-op that parks the item (#226)
  RED(exit 1)  1 occurrence(s) replaced in tools/issues/resolve-stale.sh
  GREEN(exit 0)  restored, and the case passes again

mutate: 1 mutation(s) run, 1 went red and restored green, 0 problem(s)
```

The whole set, run once (`evidence/mutations-full.txt`): `15 mutation(s) run, 14 went red and restored green,
1 problem(s)`. The problem is the pre-existing `5.7`:

```
5.7	resolver#unrepairableCitationIsParked	an unresolvable citation parking the item, so it leaves the eligible queue visibly
  MUTATION-DID-NOT-APPLY  the search matches no line of tools/issues/resolve-stale.sh — the pattern no longer matches the code
```

Its search is `--status on-hold`, which no longer exists: the hold status became a profile value
(`roles.holdStatus`) in `2026-10-20-fix-stale-repair-verification`'s neighbourhood and that record was never
updated. It is named as an exclusion rather than repaired inside a fix for another defect, exactly as the
archived `2026-10-20-fix-stale-repair-budget` and `2026-10-20-fix-stale-repair-verification` changes record it.
Mutation `5.8` — the idempotence guard, the closest neighbour of this change's rule — goes red and green
normally, so the set is not blind around the invariant this change adds.
