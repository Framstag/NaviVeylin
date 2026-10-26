# Design — fix-an-item-s-band-label-and-its-stored-mark

## The root cause

`tools/issues/update.sh` writes any label the profile's closed vocabulary declares, and a band label is in that
vocabulary: `issues_check_labels` (`tools/issues/update.sh:132`, `:135`) refuses a reserved label and a value
outside the vocabulary, and nothing else looks at what the call is about to write. The label and the marker
therefore travel together only by the *pass's* discipline — `prioritise.sh` happens to pass `--label` and `--body`
in one invocation — not by the tooling's rule: a `--label prio/p3` alone lands, which is the drifted pair the
`metadata-invariant` row reports (`check-metadata.sh --live`, 2026-10-26: `#305`, `#304`, both
`label-without-marker`).

The row repairs nothing on purpose (owner's recorded decision on #292, `guidelines/Backlog.md` §9: which half of
a drifted pair is wrong is a judgement), so detection alone leaves the drift producible for ever. `create.sh`
has the same hole from the other end: a created item's body holds no marker, so a band label passed at filing
time is a drifted pair by construction.

## The single fix

One home for the rule in `tools/issues/lib.sh`, "the band pair":

- `issues_band_label_values <profile> <label>…` (`tools/issues/lib.sh:406`) resolves the band namespace from the
  profile through the core and prints the band values among a label set. The namespace is data, so the engine
  names no vocabulary of its own, and a project that declares no bands prints nothing.
- `issues_band_pair_gate <profile> <body|""> <added label>… -- <removed label>…` (`tools/issues/lib.sh:430`)
  refuses, before any read or write, with `band-label-unpaired` (a band label named with no body) or
  `band-label-disagrees` (the body's marker records another band than the label added, or still records the band
  removed). Both are exit 3, the tooling's policy status, printed on stdout like every other refusal.

`update.sh` calls it once (`tools/issues/update.sh:146`) after the vocabulary gate and before the state read, with
the body text it is about to write (empty when the call carries none). `create.sh` calls it with an empty body
(`tools/issues/create.sh:171`), which is the `band-label-unpaired` branch: no band may be filed.

**Why the gate lives in `lib.sh` and not in `prioritise.sh`.** Every band write goes through `update.sh`
(`prioritise.sh:343`), and so does every hand write; a rule kept in the pass would leave the other writer free.The falsification shows the placement is load-bearing: the `the-writer-skips-the-marker` mutation disables the
namespace resolution in that one home and the new case goes red.

**Why the pass is unaffected.** `prioritise.sh` writes `--label prio/<new>` + `--body <body with the new marker>`
and, on a band transition, `--remove-label prio/<old>` in the same invocation
(`tools/issues/prioritise.sh:332-338`) — the shape the gate accepts by construction. `classify.sh`,
`resolve-stale.sh`, `close.sh` and the loop's `labels.sh` name no band label, so the gate returns 0 for them
before it reads anything.

## Alternatives the evidence eliminated

- **Refuse the band namespace in `update.sh` outright.** Killed by the write path itself: `prioritise.sh` writes
  the pair *through* `update.sh`, so an outright refusal would forbid the only legitimate writer. The gate is
  "a band label needs its body", not "no band label".
- **Refuse a `--body` whose marker band differs from the item's stored label.** Killed by cost and by what it
  buys: it needs a `gh issue view` on every body write — `resolve-stale.sh` repairs up to 20 bodies per run —
  and it covers a case a hand edit on the web UI already allows (the body is editable there, not gated by this
  tooling). The gate covers the label half, which is the half a hand cannot write without the tooling.
- **Repair the drift in the pass instead of preventing it.** Killed by the recorded decision: `prioritise.sh`
  already treats a missing marker as due and rewrites both halves (`prioritise.sh:125-128`), which repairs
  #304/#305 as soon as a bounded run reaches them (71 due, the bound is 20). Repair is what the pass does;
  this change is the half that was missing, and the two must not be conflated.

## Blast radius

`tools/issues/lib.sh` (two new functions), `tools/issues/update.sh` (one call), `tools/issues/create.sh` (one
call), `tools/issue-tooling-cases.sh` (one helper, three cases), `tools/issues/metadata-mutations.sh` (one
mutation re-based, one probe function removed), `guidelines/Backlog.md` §9 (one paragraph). No Kotlin, no native
code, no fixture file. The suites that exercise this path: `tools/issue-tooling-selftest.sh` (124 cases),
`tools/issues/metadata-mutations.sh` (7 mutations), and the loop/sensor/resolve-stale suites that write through
`update.sh`.

## Rollback

Revert the change: the gate is one call site in each write path and two functions in `lib.sh`, all additive, so a
revert restores the previous behaviour exactly (`update.sh` and `create.sh` then write a band label alone again).
The mutation's previous form — dropping the marker out of `issues_body_with_marker` — must be restored with it,
because with the gate in place that mutation can no longer produce a drift.
