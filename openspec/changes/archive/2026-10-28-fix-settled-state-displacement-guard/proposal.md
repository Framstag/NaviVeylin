# fix-settled-state-displacement-guard

## Why

**Root cause** — one mechanism. `tools/issues/update.sh` replaces the item's state by removing every other
value of the namespace — the scan is `issues_status_displaced` (`tools/issues/lib.sh:331-336`), called at
`tools/issues/update.sh:188-190` — and it has no notion of a *settled* state. A caller that means "take this
item off the settled state" and a caller that merely means "park this candidate" therefore issue the same
write, and the second erases the first without refusing and without reporting it. The profile already declares
which values are settled — `roles.settledStatuses` = `fixed-by`, `retired` (`tools/tooling.json:53`) — and the
pre-screen reads it to drop a settled item from the queue (`tools/loop/prescreen.sh:258`: only a status in
`settledStatuses` is `settled`). Nothing in the write path reads it.

The item's own evidence, re-verified against the tree rather than the tracker: on #146 one write removed
`status/retired` and added `status/on-hold` (tracker timeline, 2026-10-20T23:46:54Z), and the same write shape
is what a `stale`/`needs-decision`/`needs-device` verdict makes (`tools/loop/labels.sh:91`, `:107`, `:111`,
`:119`, its `holdStatus` writes) and what the resolver's park made before #281. #681 fixed the *read* half — a reversal now
restores the state the item's record keeps (`openspec/changes/archive/2026-10-27-fix-parked-tombstone-keeps-its-settled-state/proposal.md`)
— but the write that made the drift possible is unchanged on HEAD: it neither refuses nor says what it removed.

**The red test on HEAD** — `update#refusesToDisplaceASettledState` (`tools/issue-tooling-cases.sh`). The stub
tracker holds one item carrying `status/retired`; the case runs `update.sh 11 --status on-hold`. On HEAD the
write exits 0 and the recorded call log carries the edit that removes `status/retired`:

```
$ bash tools/issue-tooling-selftest.sh --case update#refusesToDisplaceASettledState
  a hold over a settled state is refused: stub gh: recorded `gh issue edit`: expected '3', got '0'
FAIL update#refusesToDisplaceASettledState
```

XML `tests="1" failures="1" errors="0"`, `2026-10-28T19:45:44Z` — `<change>/evidence/settled-displacement-red.xml`
and `<item>/settled-displacement-red.xml`.

**The single fix** — one guard, in the same home as the scan it qualifies, and one flag that names the
transition. `tools/issues/lib.sh` gains `issues_settled_statuses` (the profile's `roles.settledStatuses`, read
once) and `issues_status_settled_guard` (the settled subset of a write's displacement,
refused unless the call names the transition);
`tools/issues/update.sh` refuses such a write with exit 3 and `status-settled`, naming the value it would have
displaced, unless the call passes the new `--displace-settled`, and on the write that does pass it the call
names the settled value it removed. The two writers whose *purpose* is the transition name it:
`tools/issues/close.sh` (a closure writes `closeStatus`/`retiredStatus` deliberately) passes the flag to
`update.sh`, and `tools/issues/reopen.sh`, which has its own write path, passes `named` to the same guard — a
reopen is only ever a completed closure reversed, carrying the returning measurement
(`tools/issues/reopen.sh:73-86`).

**Owning capability** — `issue-backlog`. The requirement the defect contradicts is "A state transition
replaces the item's state label" (`openspec/specs/issue-backlog/spec.md:207-211`): it fixes *when* a write
replaces a state and says nothing about *which* states a routine write may replace. The change ADDS one
requirement beside it and leaves the existing text untouched.

**Owning guideline** — `guidelines/Backlog.md` §4 "The invariants" (`:135`, I16 "a state transition replaces
the item's state"): the invariant the defect satisfies while breaking the rule that a settled state is a claim
about the item's premise and a hold is a claim about a run's capacity. The change adds I18.

**Surface** — the count the estimate is set against, with the command that produced it:

surface: 2 cases added, 0 rewritten, 1 case file realigned, 3 writers and 1 profile reader edited, 1 guideline
section and 1 spec requirement added — `grep -rln 'issues_status_displaced\|settledStatuses' tools/issues/
tools/*-cases.sh tools/tooling.json guidelines/ openspec/specs/` (10 files, re-measured on the archived tree,
2026-10-28; the rule is asserted by 15
`status` add/remove assertions in `tools/issue-tooling-cases.sh`, `grep -c "assert_contains.*--\(add\|remove\)-label status/"
tools/issue-tooling-cases.sh`).

## What changes

- `tools/issues/lib.sh` — two helpers beside `issues_status_displaced`: `issues_settled_statuses` and
  `issues_status_settled_guard`; the refusal is the guard's own `named` argument, and the settled labels it
  found are left in `ISSUES_SETTLED_DISPLACED` for the caller to report.
- `tools/issues/update.sh` — `--displace-settled`; the refusal (`status-settled`, exit 3) before the write; the
  report of the settled value the write removes; the usage line and header comment.
- `tools/issues/close.sh` — passes `--displace-settled` (its closure is the transition).
- `tools/issues/reopen.sh` — passes the named transition to the guard on its own write path, and reports the
  settled value the guard found: a clause on its line and a `removed` list in its `--json` document.
- `tools/issue-tooling-cases.sh` — the red case and one regression case for the closure.
- `openspec/specs/issue-backlog/spec.md` — the ADDED requirement (through this change's delta).
- `guidelines/Backlog.md` §4 — invariant I18.

Additive: no CLI change to `close.sh` or `reopen.sh` — `reopen.sh` gains one clause on a line it already prints
and one field in a document it already prints, which no reader of it parses positionally (`tools/sensors/run.sh`
captures the text, `tools/sensor-cases.sh` matches a substring, `tools/portability-cases.sh` reads a field by
name) — and every call that displaces a *non-settled* state is unchanged. Rollback is the revert of one commit —
the guard writes nothing and stores nothing, so a revert restores the old write path with no data to migrate.

## Impact

- Affected files: `tools/issues/lib.sh`, `tools/issues/update.sh`, `tools/issues/close.sh`,
  `tools/issues/reopen.sh`, `tools/issue-tooling-cases.sh`, `guidelines/Backlog.md`,
  `openspec/specs/issue-backlog/spec.md` (through the delta).
- No Android module, manifest, resource or Gradle file is touched: `tools/**`, `guidelines/**` and
  `openspec/**` hold no compiled file (`guidelines/Build.md` §2, the scope block).
- Capability: `issue-backlog` (tooling), not the app's UI, rendering or native surface.
