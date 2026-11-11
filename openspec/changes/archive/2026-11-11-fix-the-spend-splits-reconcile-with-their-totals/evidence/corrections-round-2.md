# Review round 1 — the corrections

The reviewer's own report is `review-round-1-requested.md`; its decision names the arm this round took: *apply the
corrections*, subtractive, one worker pass.

## The blocking finding

The published aggregate did not match the cited file: the roles total was given as **55 680 163** and the gap it
derives as **17 540 309**, while the run file yields **55 676 163** and **17 544 309** (24 % unchanged). Corrected in
`guidelines/Backlog.recipes.md` (section 7b), in this change's `proposal.md` and in the comment of
`tools/loop/tests/loop-cases.sh`; the measurement is carried by `measure-run-totals.log`.

## The notes

- the phase line's denominator is over the items that recorded a wall, not over the numerator's set — the claim is
  narrowed in `guidelines/Backlog.recipes.md`, in the comment of `tools/loop/report.sh` and in `design.md`;
- `tasks.md` 5.1 names the paragraph this change added (`:641`–`:658`) instead of the pre-existing one;
- `design.md`'s "byte-identical" claim names the columns that move (`metrics.roles`, `metrics.roleModels`) and the
  ones that do not;
- the comment of `tools/loop/state.sh` no longer claims the orphan region's role is a fact the stream no longer
  holds — a region no `session` record opened has no role, and the prompt it may still carry is not read as one;
- `tasks.md` 6.2 names the fourth file `structural-checks.log` checks;
- the measurements that were named but carried by no artifact are carried now (`measure-run-totals.log`,
  `measure-stream-timestamps.log`) or dropped (the base's case count and the `tools/look.sh` match counts, the
  intermediate role line in `design.md`, the two instants in `tasks.md` 1.2 and 6.1);
- the gitignored runtime paths the guideline cited now name the versioned log beside them.

No production behaviour changed: the corrections touch a guideline paragraph, two comments in the shell readers, one
comment in the cases file and this change's own documents.
