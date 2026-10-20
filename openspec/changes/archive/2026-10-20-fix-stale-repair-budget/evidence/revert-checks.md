# revert-check — mutation 5.11, `tools/resolve-stale-mutations.json` — 2026-10-20T09:17:38Z

$ bash tools/mutate.sh --mutations tools/resolve-stale-mutations.json --only 5.11
5.11	resolver#ambiguityDoesNotConsumeTheBound	the bound being spent only where a write follows — an item the ladder can only report costs no slot (#199)
  RED(exit 1)  1 occurrence(s) replaced in tools/issues/resolve-stale.sh
  GREEN(exit 0)  restored, and the case passes again

mutate: 1 mutation(s) run, 1 went red and restored green, 0 problem(s)
mutate: the whole set is falsified — every named case failed with its mutation in place and passed again after the restore
exit=0
