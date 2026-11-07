# fix-loop-close-names-its-transition

## Why

**Root cause** — one mechanism. The loop's `close` verdict writes the closure's settled state through
`tools/loop/labels.sh`'s generic state write (`args+=(--status "$status_value")`, `tools/loop/labels.sh:137`,
sent at `:156`) and names no transition on it, so `issues_status_settled_guard` — the guard #684 added, which
refuses a write that displaces a settled value unless the call says it means to (`tools/issues/lib.sh:366`) —
refuses the write whose whole purpose *is* the closure. On an item that carries the other settled value
(`status/retired` on a fixed item, `status/fixed-by` on a retired one) the first of the closure's two writes
exits 3 `status-settled` and `labels.sh` exits there, so `close.sh` (`tools/loop/labels.sh:165`, itself the
call that passes `--displace-settled`, `tools/issues/close.sh:182`) never runs and the closure is left half
attempted. The verdict's own writer is not a routine writer; it inherits the refusal that was written for one.

The item's claim, re-verified against the tree rather than the tracker: `labels.sh` reaches the closure in two
writes — the status write at `tools/loop/labels.sh:156` and `close.sh` at `tools/loop/labels.sh:165` — and it
passed the flag nowhere before this change (`grep -c 'displace-settled' tools/loop/labels.sh` → `0` on HEAD).
The write is not reachable from a run today, because the pre-screen drops a settled item from the queue
(`tools/loop/prescreen.sh:98`, reading the profile's `roles.settledStatuses`); it is the write an operator's
`labels.sh close <n>` makes when a person decides, and the refusal is loud rather than silent.

**The red test on HEAD** — `labels#closeNamesTheSettledTransition` (`tools/loop-cases.sh`), on the fixture
scenario `tools/loop/fixtures/scenarios/retired/` whose item 21 carries `status/retired`. On HEAD's write path
the case prints, exit 1:

```
$ bash tools/loop-selftest.sh --case labels#closeNamesTheSettledTransition
FAIL labels#closeNamesTheSettledTransition
  a closure over the other settled state was refused: status-settled: #21 carries status/retired, a settled
  state of the project profile — pass --displace-settled to name the transition
loop-selftest: 1 of 1 cases failed
```

XML `tests="1" failures="1" errors="0"` at `2026-10-29T08:26:49Z` — `evidence/red-on-head.xml` and
`evidence/red-on-head.log` (and the same two under `/home/tim/projects/NaviVeylin/.pi/issue-loop/items/699/`).

**The single fix** — one flag, on the one write that is the transition. `tools/loop/labels.sh` passes
`--displace-settled` when the verdict is `close` (`tools/loop/labels.sh:144`): the write that records the
closure's settled state names the transition, exactly as `close.sh`'s own status write does
(`tools/issues/close.sh:182`), so the value it displaced is said out loud
(`update: #21 removed status/retired — a settled state, displaced by --displace-settled`) and the second write
runs. Nothing else changes: the routine writers keep the guard, which is the point of #684.

**Owning capability** — `agent-issue-loop`. The requirement the defect contradicts is "Closure follows a
passing review and carries evidence" (`openspec/specs/agent-issue-loop/spec.md:516`): the closure is one act,
and this change ADDS the requirement that the write making it names the transition it makes, leaving the
existing text untouched.

**Owning guideline** — `guidelines/Backlog.md` §4 "The invariants", I18 (`guidelines/Backlog.md:137`): the
invariant that a routine write does not remove a settled state names the verbs whose purpose the transition is
— a closure, a reopen — and the loop's own `close` verdict is the third. The row now names it and its case;
the invariant's text is unchanged.

**Surface** — the count the estimate is set against, with the command that produced it:

surface: 1 case added, 0 cases rewritten, 1 fixture scenario added, 1 engine write edited, 1 mutation record added, 1 guideline row realigned — `grep -rn 'run_labels "$p" close\|--add-label status/fixed-by' tools/loop-cases.sh` (3 direct `labels.sh close` calls and 4 assertions of the fixed-by write on HEAD+this change; **0** of them asserted the settled displacement the new case asserts), and `grep -c 'displace-settled' tools/loop/labels.sh` → `0` on HEAD against `1` after the fix.

## What changes

- `tools/loop/labels.sh` — the `close` verdict's state write passes `--displace-settled`, with the comment
  that says why the verdict is the transition; the header's transition table says it too.
- `tools/loop-cases.sh` — the red case `labels#closeNamesTheSettledTransition`, and its two entries in the
  declared list and the dispatch.
- `tools/loop/fixtures/scenarios/retired/issues.json` — one item carrying `status/retired`, the other settled
  value, so the case closes a settled item through the loop's own writer.
- `tools/loop-mutations.sh` — record `70.1` for the invariant: take the flag off the closure's write and the
  case goes red with the guard's refusal.
- `guidelines/Backlog.md` §4 — I18's "held by" names the loop's `close` verdict and the new case.
- `openspec/specs/agent-issue-loop/spec.md` — the ADDED requirement (through this change's delta).

Additive: no CLI change, no new flag, no profile key. Every write that displaces a non-settled state is
byte-for-byte what it was, and the guard still refuses a hold or a park that would erase a settled claim.
Rollback is the revert of one commit — the flag writes nothing and stores nothing.

## Impact

- Affected files: `tools/loop/labels.sh`, `tools/loop-cases.sh`,
  `tools/loop/fixtures/scenarios/retired/issues.json`, `tools/loop-mutations.sh`, `guidelines/Backlog.md`,
  `openspec/specs/agent-issue-loop/spec.md` (through the delta).
- No Android module, manifest, resource or Gradle file is touched: `tools/**`, `guidelines/**` and
  `openspec/**` hold no compiled file (`guidelines/Build.md` §2, the scope block).
- Capability: `agent-issue-loop` (the loop's own tooling), not the app's UI, rendering or native surface.
