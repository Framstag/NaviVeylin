# Correction round 2 — the two blocking findings of review round 1

Corrections only, as the review asked (`LOOP-CORRECTIONS-ONLY: yes`): no new claim, no new case, no new
assertion, and no behaviour beyond the two findings. The round-1 evidence under `evidence/` is untouched; the
round-2 run's tallies are beside this file in `gate-tallies-round2/`.

## 1. The third scenario's WHEN was broader than the guard it names (review, section 1)

The scenario said the refusal holds whenever `retiredStatus` lies outside `settledStatuses`, while the guard
refuses only when the profile also declares the settled-record marker (`tools/issues/resolve-stale.sh:152-158`,
`if [ -n "$settled_record" ]; then`). The scenario is now narrowed to the predicate — the first of the two
corrections the review offered, the one that adds no behaviour:

- `openspec/specs/stale-citation-resolution/spec.md:242-243`
- `openspec/changes/archive/2026-10-27-fix-parked-tombstone-keeps-its-settled-state/specs/stale-citation-resolution/spec.md:29-30`

both now read:

```
- **WHEN** the run asks for the reversal, the profile declares the settled-record marker, and its
  `retiredStatus` lies outside `settledStatuses`
```

The guard and the case are unchanged: `resolver#tombstoneRestoreNeedsASettledState` keeps the marker declared,
as its own comment states (`tools/resolve-stale-cases.sh:781`).

## 2. A restored item's own record said `reopened: true` (review, section 4)

The per-item record of the no-token reversal hardcoded `"reopened": true` beside the new field, so a restored
tombstone's entry read `reopened:true, restored:true` — the opposite of the requirement's “report the two
outcomes apart” (`openspec/specs/stale-citation-resolution/spec.md:218-224`; the delta's copy at
`openspec/changes/archive/2026-10-27-fix-parked-tombstone-keeps-its-settled-state/specs/stale-citation-resolution/spec.md:10`).

Two edits, the derivation the review asked for:

- `tools/issues/resolve-stale.sh:193-200` — `did_reopen` is set in the branch that counts a reopen, beside
  `reopened=$((reopened + 1))`, and is no longer set unconditionally after the branch. A restored item carries
  `did_restore` alone.
- `tools/issues/resolve-stale.sh:335-337` — that record prints `reopened` from `did_reopen` instead of the
  literal `true`, from the same flag the summary counter is read from.

The repair path's record already printed both fields from the two flags (`tools/issues/resolve-stale.sh:484-485`),
so it carries the pair as well now that the restore branch leaves `did_reopen` alone. No assertion was added: the
review's correction names none, and the case that names scenario 1 reads the summary counter
(`tools/resolve-stale-cases.sh:764`).

## What was run after these two edits

- `tools/resolve-stale-selftest.sh` → `resolve-stale-selftest: all 36 cases passed (JDK 27, offline, no token,
  no Gradle)`, both new cases included; tally `gate-tallies-round2/tools_resolve-stale-selftest.sh.xml`
  `tests="36" failures="0" errors="0"` — the same count the round-1 run reported.
- `./tools/loop/gate.sh`, once, after these edits (no retry): `tests=803 failures=0 errors=1`, and the one error
  is described in `gate-round2.md` beside this file.
