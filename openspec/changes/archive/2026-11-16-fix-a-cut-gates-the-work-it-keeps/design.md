# Design — a cut gates the work it keeps

## The root cause

The cut arm records what a bound-ended child left and reads none of it. `keep_leftovers`
(`tools/loop/run.sh:697`) writes the patch inside `run_child`'s own timeout path, `run.sh:4133` names it in the item's
note, `run.sh:4151` records it, and nothing between them starts anything on the tree — so the most expensive thing a
run leaves behind (38.0 Mtok over two items of the run of 2026-11-13T05:47:11Z) reaches the next run as a patch and a
hope, on a base that has moved: 2 of 2 patches applied a run later failed (`run.sh:2908`).

## The single fix

`gate_the_kept_work` (`tools/loop/run.sh:763`) runs the profile's gate command in the change root, on the tree the cut
left, at the one moment that tree is still the tree the work was taken from, and the cut arm calls it before the item's
record is written (`run.sh:4144`). The result is `passed`, `failed` or `bound`, its log is `<item dir>/cut-gate.log`,
and both are recorded on the item by `state.sh done --cut-gate/--cut-gate-result` as `cutGate: {result, log}`.

The item's result is untouched: it is `cut`, its `stopReason` is the bound, no label is written and nothing is closed.
That is the item's own requirement — "the result is recorded attached to the cut, not as a verdict, so incomplete work
is not manufactured into `blocked`" — and it is what keeps the ceiling (#851's one continuation per item, the stop
itself) intact: this change adds no child session, no second gate and no new way for an item to continue.

## The guards, and why each is not optional

| guard | what it prevents | where |
|---|---|---|
| the kept patch carries a file | gating `keep_leftovers`' preamble — a patch with no hunk is not a tree to measure | `run.sh:775` |
| the change root carries the gate the profile names | recording `failed` for a command that never existed, a finding nobody made | `run.sh:779` |
| the run's deadline has not passed | a phase started past the clock is ended on its first second, which replaces the cut's own reason with the deadline's (the reason the #851 continuation is refused there too) | `run.sh:786` |
| the clock is `LOOP_GATE_STEP_MAX_SECONDS` or the time left, whichever is shorter | a gate handed the step bound with two minutes to the deadline, or the deadline alone on a host whose gate hangs | `run.sh:790` |

## Blast radius

`tools/loop/run.sh` (one function, one call, two globals), `tools/loop/state.sh` (two flags, one field on the item
record), one case added and one assertion added to a cut case that keeps no file, one mutation, one guideline paragraph,
one spec delta. Nothing reads the new field yet except the case and the guideline: `report.sh` is untouched, because
the note the item's `resume #N` line already prints is what carries the result to the caller.

## Rollback

Revert the two engine files and the record's field is gone with them; every state written by this tree keeps a field a
reverted tree does not read, and a reverted `state.sh` ignores `--cut-gate` only if the flag is dropped from the arm
with it — the two are one change and revert together.

## Alternatives the evidence eliminated

- **A gate child** (an agent session told to run the gate): 7 522 turns of the archive were spent on reviewer sessions
  already, and the profile's gate is a command a shell runs. A child buys nothing the command does not, and the item is
  cut at a bound — spending a session after the bound is the spend the bound just refused.
- **Recording the result as a verdict** (`blocked` on a failed gate): the item's direction rejects it, and it would
  spend a decision slot on work the run ended rather than finished.
- **Gating after the item ends** (a later pass, or at the next run's start): the tree is discarded before the next
  item starts (`run.sh:2380` writes the patch, `run.sh:2383` records the tree as one this run may discard), so the
  base the work was made against is gone by then. The one tree, one moment.
- **Naming the result only in the report** (`report.sh`): the caller's line is composed from the item's note, so a
  report-only change would print a fact the record does not hold — the shape #883 was filed about.
- **No deadline guard** (always gate): a gate started past the deadline would be killed on its first second and the
  transcript would name the deadline as the mechanism of a cut that a context bound made.

## What a reviewer should hold this diff to

1. Every `- **THEN**` clause of the delta against the case that names it: `run#aCutRunsItsGateOnTheKeptWork` asserts the
   gate ran in the change root with the kept files present (`demo-fix.txt`, `demo-fix-2.txt`), the record's
   `cutGate.result`/`cutGate.log`, the note's sentence, `result: cut` with `stopReason: bound`, and the child count;
   `run#aCutWhosePatchDoesNotApplyIsLeftCut` asserts `cutGate: null` for a patch that carries no file.
2. The prose against the hunks: the guideline paragraph's four figures (38.0 Mtok, 200 297 / 205 503, 235 / 260 files,
   2 of 2) are the run record's own (`jq` over `.pi/issue-loop/runs/2026-11-13T05:47:11Z.json`, quoted in the
   proposal), and its `--cut-gate/--cut-gate-result` spelling is `state.sh`'s.

## NOTE — one guard no case stages

The deadline guard (`run.sh:786`) is not reachable by a case: the run's deadline is `--minutes` from its start, and a
child ended by it is ended before it writes, so no case can hold a child with kept work past the run's own clock
without holding a slot for the minutes it would take. It is stated in the requirement, recorded here as the one clause
of the delta that no case names, and it is the same reading the #851 arm makes in the same words (`run.sh:2905`).
