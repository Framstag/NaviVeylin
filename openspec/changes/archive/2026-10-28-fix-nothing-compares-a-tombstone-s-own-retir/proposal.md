# fix-nothing-compares-a-tombstone-s-own-retir

## Why

**Root cause** — one mechanism. An item's settled state is stated twice and nothing compares the two halves: the
label that makes it settled for the pre-screen (`roles.settledStatuses`, read at `tools/loop/prescreen.sh:258`)
and the record a tombstone keeps in its own body (`roles.settledRecordMarker`, read only by
`tools/issues/resolve-stale.sh:179-182`, and only when `--revisit-parked` is asked for). The check that reads
every item's labels and body — `tools/issues/check-metadata.sh:124-186`, the per-issue half — asserts the band
label against the band marker and the opt-in write log, and reads nothing about the status namespace, so a park
that displaced `status/retired` with `status/on-hold` left no command able to notice. #146 is that item: the
migration gave it `status/retired` on 2026-10-17, a citation park wrote `status/on-hold` over it on 2026-10-20,
and the loop spent a worker phase on it on 2026-10-27 to conclude `stale`.

**Measured on the tree today** — the drift is not history. Every item whose body carries the profile's
`settledRecordMarker` and the settled state its `status/*` label carries, read through the tracker with the
profile's own vocabulary:

```
$ gh issue list --state all --limit 400 --json number,state,labels,body | jq -r \
    --arg m "$(jq -r '.roles.settledRecordMarker' tools/tooling.json)" \
    --argjson settled "$(jq -c '.roles.settledStatuses' tools/tooling.json)" \
    '[ .[] | select((.body // "") | contains($m))
       | {n: .number, state: .state,
          settled: [ .labels[]?.name | select(startswith("status/")) | sub("^status/"; "")
                     | select(. as $v | $settled | index($v)) ]} ]
     | "tombstones=\(length) settled=\([.[] | select((.settled|length) > 0)] | length)
        displaced=\([.[] | select((.settled|length) == 0) | "\(.n)/\(.state)"] | join(","))"'
tombstones=21 settled=20 displaced=148/OPEN
```

21 items carry the migration's record; 20 carry a settled state; **#148 (§85, OPEN) carries `status/open`** — the
residue of the reversal #681 fixed, readable as work by the pre-screen until a person looks. The two tombstones
the item names as never queued (#140, #143) are among the 20: the same query, asked for the settled numbers,
printed `settled_numbers=138,139,140,141,142,143,…,158` on 2026-10-29 (the output is in
`evidence/tracker-read.txt`).

**The red test on HEAD** — `check#aDisplacedSettledStateIsReported`
(`tools/issues/check-metadata-selftest.sh`, registered in `tools/issue-tooling-cases.sh` as
`check#aDisplacedSettledStateIsReported`). It drives the check over a two-item fixture tracker: #41 is a
tombstone whose body carries the profile's record and whose label is `status/on-hold`, #42 carries no record and
`status/open`. On HEAD the check reads no status label at all:

```
$ bash tools/issues/check-metadata-selftest.sh --case check#aDisplacedSettledStateIsReported
  FAIL a tombstone whose state is not settled exits non-zero: expected [1] got [0]
  FAIL and names the item: [#41] not found in: check-metadata: 2 issue(s) checked · 0 violation(s)
```

XML `tests="1" failures="1" errors="0"`, `2026-10-28T20:17:31Z` — `evidence/settled-record-red.xml` and
`<item>/settled-record-red.xml`.

**The single fix** — the per-issue half gains one invariant: an item whose body carries the profile's
`settledRecordMarker` must carry a `status/*` value that is in `roles.settledStatuses`, and one that does not is
reported under `settled-record-displaced` naming the item and the states it carries. The predicate that reads the
record moves to `tools/issues/lib.sh` (`issues_records_settled_state`), where the resolver and the check share it
— one definition of "this body keeps a settled state". Nothing is repaired: which half is wrong is a judgement,
so a person's edit stays a person's edit. A project that declares no marker (the portability fixture does)
checks nothing.

**Owning capability** — `issue-backlog`, whose requirement *Labels carry the item's kind, area, state and
provenance* and *A state transition replaces the item's state label* own the state the drift displaces. The
change ADDS one requirement to it and leaves the existing ones untouched.

**The second delta, and why it is not a second feature** — the violation reaches the tracker through the
scheduled row `metadata-invariant` (`tools/sensors/metrics/metadata-invariant.sh`), whose registry row and claims
say "the priority pair". One command's report now carries a state invariant, so that row's two claim strings and
`backlog-sensors`' requirement *The priority pair is re-checked on a schedule* are widened to "the per-item
invariants the command owns" — a correction of the text the new violation would otherwise contradict, in the
same change (`openspec/config.yaml`: a spec and a document that disagrees with it are corrected together), not a
second capability's worth of work. If the reviewer reads that as two deltas rather than one mechanism, the
narrower fix is the row's claim strings alone and the spec text kept as it is; the choice is recorded in
`design.md`.

**Owning guideline** — `guidelines/Backlog.md` §9's `metadata-invariant` paragraph (`:909-924`), which says the
row re-reads "the tracker's own **priority invariant**", and the check's own header comment
(`tools/issues/check-metadata.sh:1-24`), which lists four priority facts. Both corrected.

**Surface** — the count the estimate is set against, with the command that produced it:

surface: 1 case added, 1 fixture added, 1 mutation added, 2 files rewritten, 1 row's claims generalised, 2 documents and 2 capability specs corrected — `grep -rln 'settledRecordMarker\|settledStatuses\|records_settled_state' tools/ guidelines/ openspec/specs/ .github/workflows/` prints 17 files (2026-10-29), the check the change adds (`tools/issues/check-metadata.sh`) and its selftest among them.

Of those nine, four are read and left alone (`tooling.json` and its fixture already declare the two roles,
`prescreen.sh` keeps reading `settledStatuses` as the settled test, `resolve-stale-cases.sh` and the mutation set
keep their cases); the change touches `lib.sh` (the predicate) and `resolve-stale.sh` (it calls the predicate now
instead of keeping its own copy). No compiled file is touched, so no build gate is owed and the structural
checks stand in.

## What changes

- `tools/issues/check-metadata.sh` — the per-issue half gains the settled pair: the profile's
  `roles.settledRecordMarker`, `roles.settledStatuses` and the status namespace prefix are resolved once beside
  the band's, and an item whose body carries the record and whose state is not settled is reported as
  `settled-record-displaced`. The header comment names the fifth fact.
- `tools/issues/lib.sh` — `issues_records_settled_state <body> <marker>`, the one definition of "this body keeps
  a settled state"; empty marker (a project without tombstones) means no item is one.
- `tools/issues/resolve-stale.sh` — `records_settled_state` delegates to the shared predicate instead of
  restating it.
- `tools/issues/check-metadata-selftest.sh` — the new case, both directions asserted.
- `tools/issues/fixtures/metadata/tombstone-displaced.json` — the two-item tracker the case reads.
- `tools/issue-tooling-cases.sh` — the case registered where every other `check#` case is.
- `tools/issues/metadata-mutations.sh` — one mutation: the new guard removed, and the case must notice.
- `tools/sensors/metrics/metadata-invariant.sh` — the two claim strings and the two clear strings name the item's
  record and its label rather than the priority pair, because the row now reports both invariants.
- `guidelines/Backlog.md` §9 — the `metadata-invariant` paragraph says which invariants the row re-reads.
- `openspec/specs/issue-backlog/spec.md` (delta) — one ADDED requirement, one scenario, one case.
- `openspec/specs/backlog-sensors/spec.md` (delta) — MODIFIED: the row re-checks the invariants the command owns.

Additive in behaviour: no existing violation changes, no label vocabulary value is added, nothing is written to
the tracker, and a profile that declares no marker behaves exactly as today. Rollback: revert the commit — the
tree before and after differs only in the files above, and no data or state is produced.

## Impact

`guidelines/Backlog.md` §5 (who may write what) is untouched: the change writes nothing to the tracker, and the
check only reads through `gh issue list` as the live mode already does. No app, car or native code is reached, so
no device verification applies. The scheduled row's first live run reports #148 — the row files one item for it
and repairs nothing, which is the row doing its job.
