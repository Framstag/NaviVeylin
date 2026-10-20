# Revert-checks — fix-create-files-class-and-category

Rule: `.pi/skills/revert-check` — one mutation per invariant, applied literally, the named case must fail for
the right reason, the file restored byte-for-byte, and the case green again.
Runner: `bash tools/mutate.sh --mutations tools/create-mutations.json` (`.pi/skills/mutation-runner`).

## The new invariant

| what | value |
|---|---|
| invariant | a finding's required `class` and `category` reach the tracker as labels |
| named case | `create#carriesTheFindingsClassAndCategory` (and, for the conflict half, `create#refusesAClassItsLabelsContradict`) |
| mutation `1.3` | `tools/issues/create.sh`: `labels+=("class${separator}${clazz}" "category${separator}${category}")` → `labels+=()` |
| why it is a falsification | with the join removed, the case that asserts the two labels on the recorded `gh issue create` invocation cannot pass; nothing else in the case is adjacent to the mutated line |

## Result (2026-10-20T20:25Z, `mutations-create-final.txt`)

```
1.3  create#carriesTheFindingsClassAndCategory
     RED(exit 1)   1 occurrence(s) replaced in tools/issues/create.sh
     GREEN(exit 0) restored, and the case passes again

mutate: 3 mutation(s) run, 3 went red and restored green, 0 problem(s)
```

The set's pre-existing records also re-verified in the same run: `1.1`
(`create#refusesTwoValuesOfOneNamespace`, the #179 refusal) and `1.2` (`create#refusesADoubledCitation`, the
#189 citation refusal) — both red with their search replaced, both green after the restore, which is what shows
the change did not disturb either refusal.
