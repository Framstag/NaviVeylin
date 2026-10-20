# revert-check — mutation 5.11, `tools/resolve-stale-mutations.json` — 2026-10-20T09:17:38Z

$ bash tools/mutate.sh --mutations tools/resolve-stale-mutations.json --only 5.11
5.11	resolver#ambiguityDoesNotConsumeTheBound	the bound being spent only where a write follows — an item the ladder can only report costs no slot (#199)
  RED(exit 1)  1 occurrence(s) replaced in tools/issues/resolve-stale.sh
  GREEN(exit 0)  restored, and the case passes again

mutate: 1 mutation(s) run, 1 went red and restored green, 0 problem(s)
mutate: the whole set is falsified — every named case failed with its mutation in place and passed again after the restore
exit=0

## The mutated run's own failure message — recorded in round 2, before the 09:50:34Z suite run

(recorded 2026-10-20, before the 09:50:34Z suite run; the timestamp in the title above is round 1's run of the
same mutation)

Recorded in round 2, because the summary above proves red by exit status but not by reason. The same mutation
(`5.11`: the guard line replaced by `  due=$((due + 1))`, i.e. the pre-fix unconditional charge) was applied by
hand to `tools/issues/resolve-stale.sh`, the named case was run against it, and the tree was restored from a
copy (`cmp` byte-identical, `git status --porcelain` empty before and after):

```
$ bash tools/resolve-stale-selftest.sh --case 'resolver#ambiguityDoesNotConsumeTheBound'
  the one slot the bound allowed went to the item a run can write: expected '1', got '0'
FAIL resolver#ambiguityDoesNotConsumeTheBound
MUTANT-EXIT=1
RESTORED-IDENTICAL
$ bash tools/resolve-stale-selftest.sh --case 'resolver#ambiguityDoesNotConsumeTheBound'
PASS resolver#ambiguityDoesNotConsumeTheBound
RESTORED-EXIT=0
```

The reason is the same one `evidence/red.txt` records on HEAD, as it must be: the mutation restores exactly the
charge the base commit had, so the ambiguous first item consumes the one slot and the writable second item is
never reached.
