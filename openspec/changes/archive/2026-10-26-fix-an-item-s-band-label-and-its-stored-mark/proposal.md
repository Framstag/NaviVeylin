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
`guidelines/Backlog.md` §9 "Checks" (the `metadata-invariant` row and its deferred prevention), with the label
gate's own contract in `guidelines/Backlog.md` §5 "Who may write what".

**Surface**, counted before the plan was fixed (Phase A step 5):

surface: 3 cases added, 0 rewritten, 1 mutation re-based, 0 fixtures added — `grep -n "band\|prio"
tools/issue-tooling-cases.sh | grep -c update` → 4, `grep -n 'the-writer-skips-the-marker'
tools/issues/metadata-mutations.sh` → 3; the fixtures are built in the cases (`band_tracker`), so no fixture file
changes. The cases and fixtures that assert the behaviour being changed are `update#removeLabelRunsThroughTheSameGate`,
`surface#reservedLabelRefusesInEveryWritePath`, `prioritise#writesLabelAndMarkerTogether` and
`metadata-mutations.sh`'s `the-writer-skips-the-marker` (re-based, because the drift can no longer be produced by
the writer it probes).

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
§9 keeps it a person's edit. This change makes the drift unproducible, not impossible to have happened.
