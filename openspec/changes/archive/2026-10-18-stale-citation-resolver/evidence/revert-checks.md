# Falsification — the nine revert-checks (tasks 5.1–5.9)

One mutation each, the named case must go red, the file restored, the case green again. Reproduce with:

```
bash tools/mutate.sh --mutations tools/resolve-stale-mutations.json --out evidence/resolve-mutations.log
```

Measured 2026-10-18: **9 mutations, 9 red, 9 restored green, 0 problems**, exit 0. Every search is a single
line, because the runner's replacement is literal and line-based; a multi-line record is reported as
`MUTATION-DID-NOT-APPLY` rather than silently doing nothing.

| task | mutation | case that must fail | measured |
|---|---|---|---|
| 5.1 | `citations.awk`: drop the trailing-boundary check (`if (0) continue`) | `classifier#fragmentIsNotACitation` | red, exit 1; restored → green |
| 5.2 | `lib.sh`: a header extension counts wherever it appears | `classifier#bareHeaderIsNotAPath` | red, exit 1; restored → green |
| 5.3 | `lib.sh`: the suffix test becomes an exact-path test | `classifier#bareSourceNameResolvesBySuffix` | red, exit 1; restored → green |
| 5.4 | `lib.sh`: the submodule files never join the index | `classifier#submodulePathResolves` | red, exit 1; restored → green |
| 5.5 | `resolve-stale.sh`: rebuild the body instead of editing the token in place (drops the marker) | `resolver#markersSurviveTheRepair` | red, exit 1; restored → green |
| 5.6 | `lib.sh`: an ambiguous citation resolves to the first candidate | `resolver#ambiguityIsReportedNotRepaired` | red, exit 1; restored → green |
| 5.7 | `resolve-stale.sh`: park nothing, comment only | `resolver#unrepairableCitationIsParked` | red, exit 1; restored → green |
| 5.8 | `resolve-stale.sh`: drop the parked-item skip | `resolver#secondRunChangesNothing` | red, exit 1; restored → green |
| 5.9 | `prescreen.sh`: extract tokens itself again, demanding a file at the repository root | `prescreen#refusalIsVisibleInTheTracker` | red, exit 1; restored → green |

Three of the nine were *repaired while writing them*, and the reason is worth recording: a case can pass
without testing the invariant it names. `classifier#fragmentIsNotACitation` first asserted only that a
fragment is not a *citation* — with the boundary check removed the fragment still classified `not-a-path`, so
the case stayed green and the mutation looked harmless; the case now asserts on the extractor's candidate set,
where the boundary is decided. `resolver#secondRunChangesNothing` first faked a second tracker state whose
citation was already canonical, so the item was skipped as *not due* and the idempotence guard was never
exercised; the state now keeps the unresolvable citation and adds only the parked label, which is what the
guard exists for. `prescreen#refusalIsVisibleInTheTracker` captured the pre-screen's stderr together with its
JSON and failed on the pollution, so it was red on a clean tree — the mutation run's `RESTORE-STAYS-RED`
reported it, which is precisely the failure mode that guard is for.

The runner's own four guards are covered by `tools/mutate-selftest.sh` (27 checks): `MUTATION-DID-NOT-APPLY`,
`STAYS-GREEN`, `MUTATION-FILE-MISSING` and an exact restore, each made to happen on a toy tree.
