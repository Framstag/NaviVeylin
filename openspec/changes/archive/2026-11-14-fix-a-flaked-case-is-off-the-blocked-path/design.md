# Design — a case that flaked once is off the blocked path

## The root cause

`tools/loop/run.sh:1264` reads a red tally with `attribute_red_gate`: the case the item's own `LOOP-RED-TEST` line names
answers `delta` without running anything (`:1284`), and any other case is re-run once in a detached tree at the run's base
commit (`:1295`) — `0` → `delta`, `2|124|137` → `unattributable`, any other status → `base`. One rerun cannot tell a case
the delta broke from a case that flaked once, and the record's vocabulary (`tools/loop/state.sh:712`) has no state for
"passes on both sides, at another time", so both answers block the item.

## The single fix

One probe, one answer, four files:

1. **`tools/loop/run.sh:1242` — `flake_probe <tree> <rerun> <bound>`.** It runs the one case the tally named once more,
   in `$root` — the item's own worktree, which already carries the build artifacts a fresh base tree does not (7.5 s
   measured on a shell case against the 800 820 ms of the gate steps #849 paid). Exit `0` → `flaked`; any other status
   → `delta`. Both arms that could answer `delta` now answer through it: the red test's own case (`:1271`, which pays
   no base tree and now costs one rerun) and the case whose base rerun exited `0` (`:1300`). `2|124|137` at the base
   still answers `unattributable`, and a tree that cannot run the probe exits non-zero, so it answers `delta` and the
   item blocks exactly as before. The bound is the profile's `LOOP_CASE_MAX_SECONDS`, read once before either rerun.

2. **`tools/loop/run.sh:3260` — the answer is taken before the close is refused, not after.** `flaked` is the one
   attribution value that stands in the waiver's place, so the attribution has to exist where the waiver is judged. The
   `attribute_red_gate` call site moved above the #811 refusal (it read the same fields, in the same turn, from the same
   place) and the refusal now reads `… && [ "$gate_attribution" != flaked ]`. A `flaked` red closes without a waiver
   clause; `delta`, `base` and `unattributable` refuse exactly as they did.

3. **`tools/loop/state.sh:712` and `:738`** — the vocabulary gains `flaked` (`delta|flaked|base|unattributable`), and
   the backstop that refuses a close over a red tally without a waiver takes the same exception, so a caller that is not
   `run.sh` reads one rule and not two. `metrics.gateAttribution` is written where it already was (`:801`), beside
   `gateWaivedSuite`; `tools/loop/report.sh` counts the new value with no change — its line is a count over the
   vocabulary, so `(flaked ×1)` is the same shape `(delta ×1)` already had.

4. **`guidelines/BuildEvidence.md:113` §4d** — the rule's closed vocabulary gains `flaked`, the probe's cost and its
   limit, and the statement of what a `flaked` answer does to the item: the red reproduced nowhere, so it is recorded
   instead of waived.

## The item's own sub-question, settled

The recorded decision leaves two things to this change: whether the probe re-runs once or to a bound, and whether a
found flake is ever carried into a flake list. Both are settled here, and neither is a fork:

- **Once.** The issue's own direction is "re-run the failing case once"; a case that fails twice for the same reason is
  the delta's, and a loop until green would let a broken case pass on a lucky run and would pay the case's full bound
  a second time. One probe keeps the arm's cost at one case, which is what makes it affordable against the block it
  removes.
- **No flake list.** `tools/gate-attribute.sh:371` says why: this tree keeps no flake list. The flake is recorded on
  the item's own record, where a reader of that block finds it, and the tally's prose is not rewritten — the same rule
  #853 gave the field.

## Alternatives the evidence eliminated

- **Probe only the delta answer, never the red test's own case** (the issue's literal direction): the item's own red
  test is a case the change wrote, and a flake in it is the same false block — #849's would have been caught by the
  broader arm only. Probing both is the same one case per red.
- **Bound the probe to a loop until green:** a broken case that passes on a retry would be recorded `flaked` and the
  item would close over a gate that did not pass — the one thing #811 exists to refuse.
- **A flake list in the tree** (`tools/gate-attribute.sh`'s own note): a list is a second record to keep green, with no
  owner and no expiry; the answer on the item's record is where the reading is used.
- **`unattributable` for a probe that cannot run:** it would keep the item blocked, which is today's behaviour for a
  tree that cannot rerun — `delta` says that in the one word the vocabulary already has, and the arm records the red as
  the delta's exactly as it does now.

## Blast radius

- `tools/loop/run.sh`: one new function, two call sites that used to print `delta`, and the attribution block moved
  above the waiver refusal — no other arm reads either.
- `tools/loop/state.sh`: one vocabulary member and one exception in the red-close backstop.
- `tools/loop/tests/loop-cases.sh`: one case (two runs), registered in the case list and the dispatcher, and the #853
  case's "no base tree is paid" assertion realigned to read the tree the case ran in — the probe makes a rerun in the
  item's own worktree the expected fact there.
- `tools/loop/tests/loop-mutations.sh`: three records (`861.1`–`861.3`), one per half of the rule.
- `guidelines/BuildEvidence.md`: §4d's rule sentence, extended; no other section states it.

## Rollback

Revert the five files. The field keeps its #853 vocabulary and a red gate blocks as it did before this change; no state
file is migrated, because `metrics.gateAttribution: flaked` is a value a reverted engine never writes and a reader of an
older record never sees.

## The measurement, and its limits

The probe's cost is one case in a warm tree; the case's own bound is `LOOP_CASE_MAX_SECONDS`, the bound every case in
this suite already carries. The probe cannot tell a flake whose cause is the tree from a case the delta broke *and*
that passes on a second run — a genuinely order-dependent break reads `flaked`. That is the item's own stated trade-off
(the direction says "re-run the failing case once ... `delta` when it fails again"), and the record keeps the answer so
a reader can see which clause stood in for the waiver.

## Open decisions

none
