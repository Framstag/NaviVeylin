# Proposal — fix-an-item-s-band-label-and-its-stored-mark

## Why

Item #671 (sensor `metadata-invariant`, condition `{"issue":"305","code":"label-without-marker"}`), verified in
this tree on 2026-10-26:

```
$ bash tools/issues/check-metadata.sh --live
#305 label-without-marker: prio/p3
#304 label-without-marker: prio/p3
check-metadata: 333 issue(s) checked · 2 violation(s)
```

The condition holds and it is not fixed: two items carry a `prio/p3` label and no marker at all. The metadata
pass would repair them — `bash tools/issues/prioritise.sh --max-rescore 100 --plan` reports
`metadata: read=333 skipped=262 due=71 …` and lists both in its due set — so the drift is repairable, and 40
other items carry neither half. What those two prove is the other direction: **the pair can be written apart.**

**Root cause, one sentence**: `tools/issues/update.sh` is the one write path that can set a band label, and it
validates that label against the closed vocabulary (`tools/issues/update.sh:132`, `issues_check_labels`) without
asking whether the body it writes — `tools/issues/update.sh:177` `--add-label`, `:186` `--body-file` — records the
same band, so one `update.sh 305 --label prio/p3` produces exactly the pair the check reports.

`guidelines/Backlog.md` §9 already names this as the deferred other half of the same root cause: the
`metadata-invariant` row "reports and repairs nothing" by the owner's recorded decision on #292 (2026-10-25,
option 1 of three), "the refusal of a band label in `update.sh` it was weighed against is a later item". The
item that is #671 is that later item.

The `--live` output above and the pass's plan line below are the round-1 readings, quoted from that run on
2026-10-26; neither can be re-derived offline (`check-metadata.sh --live` needs the tracker), and nothing in
the change depends on either value.

**The red tests**, three cases that fail on the base, log `evidence/red-on-head.log`, tallies beside it, run
2026-10-26T20:34:56Z (`HEAD 0814f28e`):

- `update#refusesABandLabelWithoutItsMarker` — `expected '3', got '0'`: `update.sh 25 --label prio/p2` on an
  item whose body carries no marker succeeds and reaches the tracker, `tests="1" failures="1"`
  (`evidence/red-on-head-update_refusesABandLabelWithoutItsMarker.xml`).
- `update#writesABandLabelWithTheBodysMarker` — the paired write passes on the base, its second half does not: a
  body whose marker records `p3` is accepted for `--label prio/p2`, `tests="1" failures="1"`
  (`evidence/red-on-head-update_writesABandLabelWithTheBodysMarker.xml`).
- `create#refusesABandLabelAtFilingTime` — `create.sh --label prio/p1` files an item whose body carries no marker
  at all, `tests="1" failures="1"`
  (`evidence/red-on-head-create_refusesABandLabelAtFilingTime.xml`).

**Owning capability and guideline**: `openspec/specs/issue-priority/spec.md` — its requirement "An item's
priority is stored, in a fixed range" states the detection half ("The label and the record agree", case
`check#labelWithoutMarkerFails`); this change adds the write half. The guideline section is
`guidelines/Backlog.md` §9 "Checks" — the `metadata-invariant` row and its deferred prevention, and the paragraph
the rule landed in.

**Surface**, counted before the plan was fixed (Phase A step 5), and re-counted for the round-2 correction:

surface: 3 cases added, 0 rewritten, 1 mutation re-based, 0 fixtures added — `git show
0814f28e:tools/issue-tooling-cases.sh | grep -cE "^    '[A-Za-z]+#"` → 95 at the base and the same command over
the tree → 98 (three case functions added); `grep -c 'the-writer-skips-the-marker'
tools/issues/metadata-mutations.sh` → 4 (the mutation id, its invariant sentence, its patch arm and its probe
arm); the fixtures are built in the cases (`band_tracker`), so no fixture file changes. Round 2 adds no case:
two assertions inside `update#writesABandLabelWithTheBodysMarker` (the refusal, and the demotion that must stay
allowed) and one branch in the gate — `git diff --stat 98135f1d` after the correction is that count. The cases
and fixtures that assert the behaviour being changed are
`update#removeLabelRunsThroughTheSameGate`, `surface#reservedLabelRefusesInEveryWritePath`,
`prioritise#writesLabelAndMarkerTogether` and `metadata-mutations.sh`'s `the-writer-skips-the-marker` (re-based,
because the drift can no longer be produced by the writer it probes).

## What changes

1. `tools/issues/lib.sh` — one home for the rule ("the band pair"): `issues_band_label_values` resolves the band
   namespace from the profile through the core, and `issues_band_pair_gate` refuses a band label that names no
   agreeing body.
2. `tools/issues/update.sh` — the gate runs after the vocabulary gate and before any read or write.
3. `tools/issues/create.sh` — a created item's body carries no marker, so no band label may be filed with it.
4. `tools/issue-tooling-cases.sh` — the three cases above.
5. `tools/issues/metadata-mutations.sh` — `the-writer-skips-the-marker` re-based onto the gate; its probe is the
   new case.
6. `guidelines/Backlog.md` §9 — the deferred prevention is now stated as landed, with the reason codes.

## Out of scope

Repairing #304/#305: that is a tracker write and a judgement about which half is wrong, and `guidelines/`
§9 keeps it a person's edit. This change makes the label half of the drift unproducible by this tooling — not
the drift impossible: a `--body` that names no band label, and a hand edit on the web UI, can still move the
marker away from the item's stored label, and those are what the row above reports. #305 keeps its
`label-without-marker` until the pass or a person rewrites it, so the sensor will re-emit the same fingerprint
against the closed item; that residue is visible in `check-metadata.sh --live` and is why the closure comment
names it.

## Round 2 — the correction the review asked for

Review round 1 (`evidence/review-round-1-requested.md`) accepted the added-label half and reproduced the
removal half: `update.sh 26 --remove-label prio/p1 --body <body whose marker records p3>` passed the gate and
wrote `--remove-label prio/p1` with that body, leaving `marker-without-label` — the drift in the direction
#305's own condition does not happen to be. The four sentences that called the pair unbreakable were therefore
false as written.

What the correction changes: one branch in `issues_band_pair_gate` (a removal that adds no band, carrying a body
that records one, is refused with `band-label-disagrees`), two assertions in
`update#writesABandLabelWithTheBodysMarker` (the refusal, and the demotion that must stay allowed), one sentence
in the requirement and its scenario, and the correction of those four sentences — `tools/issues/update.sh`'s
comment, `metadata-mutations.sh`'s invariant sentence, this change's `design.md` and `guidelines/Backlog.md`
§9 — each now saying what the gate actually refuses instead of that the pair cannot be written apart.

Evidence: `evidence/red-on-head-round2.log` and its tally (the new assertion red on HEAD
`98135f1d`, `tests="1" failures="1"`, 2026-10-26T20:46:47Z), `evidence/mutation-671-removal.log` (the branch
mutated away, the assertion red at `20:48:27Z`, green after the restore), `evidence/mutation-671-whole.log`
(7 of 7 mutations noticed), and `evidence/gate-round2.log:1204` — the gate's own verdict line, 611 tests, 0
failures, 0 errors, no compiled path in the diff — with its 22 per-suite tallies in `evidence/gate-tallies/`
(`tools/issue-tooling-selftest.sh`: `all 124 cases passed`, 2026-10-26T21:03:12Z).
