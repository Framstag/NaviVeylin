# fix-parked-tombstone-keeps-its-settled-state

## Why

**Root cause** — one mechanism. `reopen_item` writes the profile's `openStatus` unconditionally
(`tools/issues/resolve-stale.sh:170`, `:175`), and the historical park it reverses had already replaced the
settled state with `holdStatus` through `update.sh --status`, which *replaces* the status namespace
(`tools/issues/update.sh:14-18`). So by the time a parked tombstone is revisited, its `status/retired` is gone
from the labels and the reversal writes a queue state instead of the settled one — the reversal restores one
half of a two-part write and the settled half is lost.

**The symptom the item records** (verified against the tree, not the tracker): #147 was the `§80` tombstone the
migration created with `status/retired` (`openspec/changes/archive/2026-10-17-migrate-todo-to-github-issues/tombstones.sh:84`);
a citation-hold run wrote `status/on-hold` (`git show a378325c^:tools/issues/resolve-stale.sh`, the park's
`--status "$hold_status"` write); the revisit then reopened it to `status/open`
(`tools/issues/resolve-stale.sh:170`); the pre-screen read it as eligible
(`tools/loop/prescreen.sh:258` — only a status in `settledStatuses` is `settled`) and a loop run spent a worker
phase concluding `stale`.

**The red test on HEAD** — `resolver#parkedTombstoneKeepsItsSettledState`
(`tools/resolve-stale-cases.sh`). A tracker item is parked the way this tool parked one (on-hold, the loop's
stale refusal, the parking comment) and its body carries the migration's tombstone record — the
`- **Retired** ℹ: removed from the backlog by commit …` provenance line. Run through
`resolve-stale.sh --apply --revisit-parked`, HEAD writes `status/open`:

```
$ bash tools/resolve-stale-selftest.sh --case resolver#parkedTombstoneKeepsItsSettledState
  a tombstone is not reopened into the queue: expected '0', got '1'
FAIL resolver#parkedTombstoneKeepsItsSettledState
```

XML `tests="1" failures="1" errors="0"`, `2026-10-27T20:39:14Z` — `<change>/evidence/parked-tombstone-red.xml`
and `<item>/parked-tombstone-red.xml`.

**The single fix** — the reversal restores the state the item's own record keeps, when the profile declares the
record's marker: a body carrying `roles.settledRecordMarker` is reversed to `roles.retiredStatus` (which the
profile must declare inside `settledStatuses`), and an item without that record keeps today's reversal to
`openStatus`. Nothing else about the reversal changes — the stale refusal still clears, `agent/eligible` is still
never written, and the park a person wrote is still untouched.

**Owning capability** — `stale-citation-resolution`. The shipped spec has no requirement for the reversal at all
(its four requirements end at the report-only rule), so the change ADDS one requirement and leaves the existing
ones untouched.

**Owning guideline** — `guidelines/Backlog.md`'s resolver paragraph (`:747-757`, the section on what the pre-screen and the loop do with an item), which states what
`--revisit-parked` reopens and says nothing about a settled state. Corrected in this change.

**Surface** — the count the estimate is set against, with the command that produced it:

surface: 2 cases added, 1 mutation added, 1 profile role declared, 2 documents corrected — `grep -rln 'revisit-parked\|reopen_item\|openStatus' tools/*-cases.sh tools/issues/ tools/loop/ guidelines/ openspec/specs/ .github/workflows/` (6 files)

The six files are the implementation, the cases, the mutation set, `guidelines/Backlog.md`, the
`cleanup-todo` skill and the sensors workflow's comment; the last two need no edit (the skill's recipe still
holds, the workflow still says it does not pass the flag).

## What changes

- `tools/issues/resolve-stale.sh` — reads `roles.settledRecordMarker` and `roles.retiredStatus` when the run asks
  for the reversal; reverses a parked item whose body carries the marker to `retiredStatus` instead of
  `openStatus`, and reports it as `restored` beside `reopened`.
- `tools/tooling.json` — `roles.settledRecordMarker` declares the migration's record line, so the marker is a
  project value in the profile and not a literal in an engine (`tools/check-portability.sh` reads `roles`).
- `tools/fixtures/foreign-project/tools/tooling.json` — the same key, empty: a project without tombstones
  declares it empty rather than omitting it.
- `tools/resolve-stale-cases.sh` — the new case, plus the existing `resolver#cleanParkedItemIsReopened` kept as
  the other half (an item without the record still returns to `openStatus`).
- `tools/resolve-stale-mutations.json` — one revert-check on the new guard.
- `guidelines/Backlog.md`'s resolver paragraph — the reversal paragraph states the restored state.
- `openspec/specs/stale-citation-resolution/spec.md` — one ADDED requirement, two scenarios, one case each
  (`openspec/changes/…/specs/stale-citation-resolution/spec.md`).

Additive: no existing requirement changes; a profile that declares no marker behaves exactly as today.
Rollback: revert the commit — the change touches no compiled file and writes no data, so the tree before and
after differs only in the files above.

## Impact

`guidelines/Backlog.md` §5 (who may write what) is untouched: the reversal still writes through
`tools/issues/update.sh`, so the reserved-label refusal and the closed vocabulary apply to it unchanged. No
label vocabulary value is added, removed or renamed; the change adds one role that names an existing body
record. No app, car or native code is reached, so no device verification applies.
