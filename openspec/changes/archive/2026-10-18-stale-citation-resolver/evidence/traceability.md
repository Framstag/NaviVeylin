# Scenario → case → task

Every scenario in `specs/stale-citation-resolution/spec.md` with the case that exercises it and the task in
`tasks.md` that owns that case. The row count equals
`grep -c '^#### Scenario:' specs/stale-citation-resolution/spec.md` (20).

The gate is `tools/resolve-stale-selftest.sh` (20 cases, offline); `revert-checks.md` proves nine of them fail
when the invariant behind them is removed.

| # | requirement | scenario | case | task |
|---|---|---|---|---|
| 1 | One definition of what a citation is | A fragment is not a citation | `classifier#fragmentIsNotACitation` | 1.1 |
| 2 | One definition of what a citation is | A header name without a path separator is not a citation | `classifier#bareHeaderIsNotAPath` | 1.1 |
| 3 | One definition of what a citation is | A path written without its prefix resolves | `classifier#bareSourceNameResolvesBySuffix` | 1.2 |
| 4 | One definition of what a citation is | A submodule path resolves inside the submodule | `classifier#submodulePathResolves` | 1.2 |
| 5 | One definition of what a citation is | A generated artefact is not a missing file | `classifier#generatedArtefactIsNotMissing` | 1.2, 1.3 |
| 6 | One definition of what a citation is | Only an absent citation is stale | `classifier#absentIsTheOnlyStale` | 1.2 |
| 7 | The staleness verdict is visible in the tracker | A refused item is marked | `prescreen#refusalIsVisibleInTheTracker` | 2.1, 2.2 |
| 8 | The staleness verdict is visible in the tracker | A repair does not need a second decision | `resolver#suffixedCitationIsCanonicalised`, `pipeline#scheduledRunRepairs` | 2.2, 3.2, 4.2 |
| 9 | A citation is repaired when the repair is mechanical | A suffixed path is canonicalised | `resolver#suffixedCitationIsCanonicalised` | 3.2 |
| 10 | A citation is repaired when the repair is mechanical | A renamed file is followed | `resolver#renamedFileIsFollowed` | 3.2 |
| 11 | A citation is repaired when the repair is mechanical | A removed symbol is re-anchored | `resolver#removedSymbolIsReAnchored` | 3.2 |
| 12 | A citation is repaired when the repair is mechanical | The item's record survives the repair | `resolver#markersSurviveTheRepair` | 3.3 |
| 13 | A citation is repaired when the repair is mechanical | A repair is recorded | `resolver#repairPostsOneComment` | 3.4 |
| 14 | What cannot be repaired is parked, never guessed | An unrepairable citation parks the item | `resolver#unrepairableCitationIsParked` | 3.5 |
| 15 | What cannot be repaired is parked, never guessed | An ambiguous match is reported, not repaired | `resolver#ambiguityIsReportedNotRepaired` | 3.5 |
| 16 | What cannot be repaired is parked, never guessed | The resolver cannot authorise or close | `resolver#neverWritesTheAuthorisation` | 3.6, 3.7 |
| 17 | The resolver runs on its own, bounded and idempotent | A scheduled run repairs without a person | `pipeline#scheduledRunRepairs` | 4.2 |
| 18 | The resolver runs on its own, bounded and idempotent | The bound holds | `resolver#boundHoldsAndReportsTheRest` | 3.1 |
| 19 | The resolver runs on its own, bounded and idempotent | A second run changes nothing | `resolver#secondRunChangesNothing` | 3.4 |
| 20 | The resolver runs on its own, bounded and idempotent | The residue is a declared check | `pipeline#residueIsADeclaredRow` | 4.1 |

One case carries no row because it tests a rule the spec states inside scenario 2's rule set rather than a
scenario of its own: `classifier#externalLibraryPathIsNotAPath` (the `externalPrefixes` half of "not a citation
at all" — `androidx/car/app/Screen.java` was the measured shape). It is listed here so the count of cases (20)
and of scenarios (20) is not mistaken for a coincidence.

## Row count

```
$ grep -c '^#### Scenario:' openspec/changes/stale-citation-resolver/specs/stale-citation-resolution/spec.md
20
```

Rows in the table above (excluding the header and the separator):

```
20
```
