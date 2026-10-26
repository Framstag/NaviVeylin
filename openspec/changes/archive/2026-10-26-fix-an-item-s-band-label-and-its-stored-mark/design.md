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
- `issues_band_pair_gate <profile> <body|""> <added label>… -- <removed label>…` (`tools/issues/lib.sh:436`)
  refuses, before any read or write, with `band-label-unpaired` (a band label named with no body) or
  `band-label-disagrees` (the body's marker records another band than the label added; the label removed is the
  band the marker still records; or the call removes a band label, adds none, and the body it carries records a
  band at all — the two halves are then written apart in the `marker-without-label` direction). Both are exit 3,
  the tooling's policy status, printed on stdout like every other refusal.

`update.sh` calls it once (`tools/issues/update.sh:148`) after the vocabulary gate and before the state read, with
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

## The correction of round 1 — the removal half

Review round 1 accepted the added-label half and reproduced the other one: `update.sh 26 --remove-label prio/p1
--body <body whose marker records p3>` passed the gate, because the removal branch asked only whether the removed
band was still the recorded one (`[ "$label" != "$marker_band" ] && continue`) and returned nothing when no band
was added. The write it then issued leaves the item with a marker at `band=p3` and no band label —
`marker-without-label`, the mirror of the drift #304/#305 exhibit, produced by the one path the change declared
closed.

The branch is decidable without a read of the item: a removal, no addition, and a body whose marker records a
band can only end with the marker standing alone, so it is refused with `band-label-disagrees`. It is not the
alternative this design already rejected ("Refuse a `--body` whose marker band differs from the item's stored
label"), which needs a `gh issue view` per body write; the branch reads the call alone. The control it needs is
next to it, or the gate would refuse the demotion it must allow: a removal carrying a body with no marker passes
and one `gh issue edit` carries `--remove-label` with that body.

**What stays ungated, and why the change no longer says otherwise.** A `--body` that names no band label can
still move the marker away from the item's stored label, and a hand edit on the web UI is gated by nothing; both
need a read of the item or a write path this tooling does not own. `check-metadata.sh --live` remains the
detector for those, and the `metadata-invariant` row remains the thing that reports them
(`guidelines/Backlog.md` §9).

**The falsification of the branch.** `the-writer-skips-the-marker` disables the whole gate and the probe notices
it (`evidence/mutation-671-whole.log`, 7 of 7); the branch itself is falsified on its own by making its condition
unreachable (`&& [ -n "$marker_band" ]` → `&& false`), which turns exactly the new assertion red with
`expected '3', got '0'` and leaves it green after the restore (`evidence/mutation-671-removal.log`, 2026-10-26T20:48:27Z).

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

`tools/issues/lib.sh` (two new functions, and in round 2 one branch of the gate), `tools/issues/update.sh` (one
call and its comment), `tools/issues/create.sh` (one call), `tools/issue-tooling-cases.sh` (one helper, three
cases, and in round 2 two assertions inside the pair case), `tools/issues/metadata-mutations.sh` (one mutation
re-based, one probe function removed, one invariant sentence corrected), `guidelines/Backlog.md` §9 (one
paragraph). No Kotlin, no native code, no fixture file. The suites that exercise this path:
`tools/issue-tooling-selftest.sh` (124 cases), `tools/issues/metadata-mutations.sh` (7 mutations), and the
loop/sensor/resolve-stale suites that write through `update.sh`.

## Rollback

Revert the change: the gate is one call site in each write path and two functions in `lib.sh`, all additive, so a
revert restores the previous behaviour exactly (`update.sh` and `create.sh` then write a band label alone again).
The mutation's previous form — dropping the marker out of `issues_body_with_marker` — must be restored with it,
because with the gate in place that mutation can no longer produce a drift.
