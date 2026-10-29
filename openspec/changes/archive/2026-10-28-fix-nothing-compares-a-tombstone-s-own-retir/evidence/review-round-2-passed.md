The review round 2 of this change, recorded after the round answered.

```text
LOOP-BLOCKING: 0
VERDICT: PASS
```

What it read and reproduced, in its own words (condensed to the findings it recorded; the round's full answer is
the run's `reviewer-report-2.md`):

- **Blocking 1 closed.** The narrowed scenario names exactly what `sensors#metadataDriftRow` supplies — items 278
  (`prio/p3`, no marker), 279 (marker, no band label) and 280 (both) — in both the shipped requirement and this
  archive's delta, byte-identically. The requirement's body keeps the delegation clause, which
  `tools/sensors/metrics/metadata-invariant.sh` honours: it loops `.violations[]?` with no code filter and
  `check-metadata.sh` emits `settled-record-displaced`.
- **Blocking 2 closed, and the numbers reproduce.** 21 tallies for 21 suites; `129 + 67 + 29 + 6 = 231`; Σ `time` =
  294 s = `LOOP-MEASURE-GATE seconds=294 suites=21`; `base=ccbc3099…` is the merge's second parent, and
  `git diff --stat ccbc3099 7b1551ca` is 41 files / 709 insertions / 30 deletions, exactly the measurement's
  `paths=41 insertions=709 deletions=30`. The `openspec/**` and `*.md` checks the round-1 gate lacked are in the
  run. The merge kept both sides: `issues_records_settled_state` beside `issues_status_settled_guard` in
  `tools/issues/lib.sh`, and both requirements in `openspec/specs/issue-backlog/spec.md`.
- **Residuals it measured, closed after it**: both requirement bodies crossed the documented 500-character bound
  (513 and 705 → now 491 and 476), and the `proposal.md` `surface:` line was a hand-written count that
  `LOOP-MEASURE-SURFACE` merely re-prints — the count and its nine names are gone, and the line now states what
  the command prints.
