# Design — fix-nothing-compares-a-tombstone-s-own-retir

## Root cause

An item's settled state has two homes and no comparison. The pre-screen reads the label: only a value in
`roles.settledStatuses` is `settled` (`tools/loop/prescreen.sh:100`, `:258`). A tombstone also states the same
fact in its own body, in the line the migration wrote (`roles.settledRecordMarker`), and the only reader of that
line is the reversal `tools/issues/resolve-stale.sh:179-182`, reached only when `--revisit-parked` is asked for.
The one command that reads every item's labels **and** body — `tools/issues/check-metadata.sh:124-186` — asserts
the band label, its stored marker, the opt-in write log and the marker grammar, and reads no `status/*` label at
all. A park's `--status` write displaced `status/retired` (`tools/issues/update.sh`, the state namespace is
replaced, not added to) and nothing in the tree could tell that the body still said settled.

## The single fix

One predicate and one invariant:

- `tools/issues/lib.sh` gains `issues_records_settled_state <body> <marker>`: the body carries the marker, and an
  empty marker means no body does. It is the one definition of the reading `resolve-stale.sh` already performs,
  and `resolve-stale.sh` now calls it instead of keeping its own copy.
- The per-issue half of `check-metadata.sh` resolves `roles.settledRecordMarker`, `roles.settledStatuses` and the
  status namespace prefix once, then for every item whose body carries the record requires one `status/*` value
  in `settledStatuses`, reporting `settled-record-displaced` with the states the item carries.
- The violation reaches the tracker through the existing row `metadata-invariant`, whose claims and whose
  requirement are widened from "the priority pair" to the invariants the command owns.

## Alternatives the evidence eliminates

- **Put the check in `tools/check-todo-references.sh`**, the item's other candidate. That check's subject is a
  `§N` citation in a file and its resolution through the `§ → issue` table (`tools/check-todo-references.sh:2-16`);
  it reads issue bodies only through `list.sh --id`. A tracker-state invariant there would need the whole tracker
  and the label vocabulary that this check has no seam for, and it would run in CI where the tracker is not
  authenticated for a full read. Impossible without making it a different command.
- **Repair instead of report.** Writing the settled label when the body says settled would decide which half of a
  displaced pair is right, and the tree states the rule the other way (`guidelines/Backlog.md` §9, the row
  "reports and repairs nothing: which half of a drifted pair is the wrong one is a judgement"). It would also let
  a hand edit of a body settle an item, which is the failure mode the labels exist to prevent.
- **A violation code on a new command with its own row.** The item's complaint is that nothing *noticed*; a new
  command that nothing schedules reproduces it, and a new row means a registry row, a workflow step, a
  credential doc and a bound — a capability, not this fix.
- **Require the label to be `roles.retiredStatus` exactly**, rather than any settled value. Wrong: a tombstone
  that a person closes as not-planned carries the same settled family, and the pre-screen's own settled test is
  membership in `settledStatuses`; a stricter rule would report items nothing reads as work.
- **Anchor the record to a line start** (the shape `issues_prio_marker_line` uses after issue #293). Rejected on
  measurement: on the live tracker (2026-10-28) the marker occurs in 21 bodies and no others — the 20 tombstones
  that carry a settled state and #148, the displaced one this change's check reports — so an anchor buys nothing
  today; the reading
  is also the one `resolve-stale.sh` already makes, and two different readings of the same marker is the defect
  this change is about.
- **Leave `backlog-sensors` alone.** The row's claims are literal strings ("the priority pair has drifted apart")
  and a `settled-record-displaced` violation would be filed under a false claim; the shipped requirement names
  the priority pair as the row's whole subject. Two small deltas with one mechanism is the honest cost; a
  reviewer who reads the rule as "one delta" can keep the code and drop the second delta, leaving only the row's
  strings corrected.

## Blast radius

- `tools/issues/check-metadata.sh` — three role reads and the invariant; the header comment's list of facts.
- `tools/issues/lib.sh` — the predicate (no behaviour on its own).
- `tools/issues/resolve-stale.sh` — one function body delegates.
- `tools/issues/check-metadata-selftest.sh`, `tools/issue-tooling-cases.sh` — the case and its registration.
- `tools/issues/fixtures/metadata/tombstone-displaced.json` — the fixture tracker.
- `tools/issues/metadata-mutations.sh` — one mutation (`settled-record-not-checked`): the invariant removed, and
  the case must notice.
- `tools/sensors/metrics/metadata-invariant.sh` — two claim strings and two clear strings; the fingerprint, the
  bound, the exit-status handling and the conditions bookkeeping are untouched.
- `guidelines/Backlog.md` §9's `metadata-invariant` paragraph; the two capability deltas.

Not reached: anything compiled, the app, the car path, the native boundary, any writer. The live mode reads the
tracker through the same `gh issue list --state all` call it already makes; the self mode (the token-free check
CI runs) sees no new read, because the invariant needs bodies and labels and self mode has neither.

## Risk

- *A false positive on a body that quotes the marker*: a prose quotation would be read as a record and a body
  with no settled label would be reported. Bounded by measurement (`tombstones=21`, all of them the migration's)
  and by the fact that the same reading already decides which state a reversal restores.
- *The row's first live run is not clean*: #148 (OPEN, `status/open`, body carries the record) is a violation
  today, so the row files one item and repairs nothing. That is the row's job, and the item it files is the one
  #681 left behind.
- *A profile that declares a marker but no `settledStatuses`*: every tombstone becomes a violation. Accepted as
  loud rather than silent — such a profile's pre-screen has no settled status at all, so it reads every
  tombstone as work; the resolver refuses the same combination (`tools/issues/resolve-stale.sh:152-157`).

## Verification

No compiled file is touched, so the change owes the shell suites its pathspec names and no build:
`tools/issue-tooling-selftest.sh`, `tools/sensors/sensors-selftest` (the row's cases), `tools/check-portability.sh`
and its self-test (the predicate reads the profile, never a literal), `tools/mutate.sh`'s `--list` forms, and
`tools/check-doc-routes.sh` / `tools/check-todo-references.sh` for the guideline and the specs. The red case is
`check#aDisplacedSettledStateIsReported`; the mutation `settled-record-not-checked` proves the case notices the
invariant's removal. `./tools/loop/gate.sh` runs once and its tally is the record.
