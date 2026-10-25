# fix-stale-citation-refusal-is-report-only — tasks

- [x] 1.1 (`stale-citation-resolution` — *One definition of what a citation is*) `prescreen.sh` stops
      assigning the `stale` verdict: the absent token lands in the item's record as `absentCitation` and the
      item stays eligible. The refusal-label clearing stays, so a historical `agent/excluded-stale` is shed.
- [x] 1.2 (`stale-citation-resolution` — *The staleness verdict is visible in the tracker*, removed) the
      `stale)` arm of the refusal write and the `exclude-stale` verdict it fed are removed with it.
- [x] 1.3 `tools/loop/report.sh` drops the "repair the citation" action source, which read the removed verdict.
- [x] 2.1 (`stale-citation-resolution` — *What cannot be repaired is reported, never guessed*)
      `resolve-stale.sh` writes no park for an unresolved citation: it counts it (`unresolved`), reports it,
      and spends no slot of the run's bound.
- [x] 2.2 (`stale-citation-resolution` — *A citation is repaired when the repair is mechanical*) the repair and
      its verification are unchanged; only an item already parked keeps `stillParked` and the state it had.
- [x] 2.3 `tools/sensors/metrics/stale-citations.sh` counts `unresolved + stillParked`.
- [x] 3.1 `case_prescreen_missingPathYieldsStale` becomes `prescreen#missingPathIsReportedNotRefused`: the item
      is `eligible`, its record names the absent token, and no `agent/excluded-stale` is written.
- [x] 3.2 `case_prescreen_refusalLeavesATraceInTheTracker` moves to the `class-feature` refusal, so the trace
      invariant (label written, `agent/eligible` untouched, dry run writes nothing) is kept for a verdict that
      still exists.
- [x] 3.3 `tools/resolve-stale-cases.sh`: the three parking cases become report-only cases — an unrepairable
      citation and a title token are reported by name and the item's labels are untouched.
- [x] 3.4 `tools/sensor-cases.sh` needed no change: the residue row is exercised by
      `pipeline#residueIsADeclaredRow` with an item that is not parked, which is exactly the new shape.
- [x] 4.1 `openspec validate fix-stale-citation-refusal-is-report-only --strict` passes.
- [x] 4.2 `bash tools/resolve-stale-selftest.sh` 33/33, the loop suite over the three changed engine scripts
      64/64, `check-portability` OK, `check-classification` OK.
- [x] 4.3 Falsification: `bash tools/mutate.sh --mutations tools/resolve-stale-mutations.json` — 15/15, each
      named case red with its mutation and green after the restore. Mutations 5.7–5.9 were re-aimed at the
      report-only rule (5.8 at `--revisit-parked`'s guard).
- [ ] 5.1 The **full** loop suite is owed before archive: the change owns `tools/loop/**`.
- [ ] 5.2 The residue commit this branch builds on (`0b5fe01f`) is the owner's; confirm it is intended to land
      in the same change or as its own.
