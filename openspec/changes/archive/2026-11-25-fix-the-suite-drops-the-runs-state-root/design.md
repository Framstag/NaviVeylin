# Design: fix-the-suite-drops-the-runs-state-root

## Root cause

`tools/loop/run.sh:85` puts the run's identity into every child's environment, `LOOP_STATE_FILE` among it. A suite
started by a child — the gate's own step is this suite — begins in that environment, and at base `184376ec` the
`unset` line of `tools/loop/tests/loop-selftest.sh` (now `:179`) dropped three roots before the first case and not the
fourth:

```
unset LOOP_WORKTREE LOOP_TOOL_RESULT_LEDGER LOOP_WORKTREE_SCRATCH_PATHS
```

The state path survives into every case's environment. Its tree check cannot refuse it either: a case's scratch
project lives under `$LOOP_ROOT/.pi/tmp/…` (`tools/loop/lib.sh:223`), so `git rev-parse --show-toplevel` inside it
answers the repository itself and `loop_path_outside_tree` (`tools/loop/lib.sh:208-216`) — the rule issue #214 added
— sees the run's own `run.json` as a path *inside* the tree it is asked about. A case that runs `run.sh` therefore
initialises and writes the live record; the run of 2026-11-24 left fixture items `#22` and `#32` in `run.json` ahead
of `#984`, and `run-one-item-quota.sh:211` charged the attempt to `#22`.

## The fix

One variable on one line, plus the sentence that says why: `LOOP_STATE_FILE` joins the `unset` list at
`tools/loop/tests/loop-selftest.sh:179`, and the paragraph above it gains the reason the other three carry — the run's state file is a
root a case must not inherit, and a case that wants one sets it on its own command line.

The guard follows in the same shape it already has: the `for` loop at `tools/loop/tests/loop-cases.sh:18798` takes the fourth name, so
a case fails and names the variable when the driver hands it over, and the case starts one suite of its own under
`LOOP_STATE_FILE=<scratch>/run.json` so the drop is held where a case runs the loop rather than only in the shell the
suite was started from. Two mutation records carry the invariant: `tools/loop/tests/loop-mutations.sh:1607`'s `1009.3` pattern is
realigned to the extended line, because a mutation that no longer matches its target is a mutation that never landed,
and `1066.1` puts the three roots #1009 measured back and leaves the state file out, so the fourth name fails on its
own record rather than only beside the worktree arm.

## Blast radius

- `tools/loop/tests/loop-selftest.sh` — the drop, and its comment.
- `tools/loop/tests/loop-cases.sh` — one case's guard list and its comment, and the nested arm it starts.
- `tools/loop/tests/loop-mutations.sh` — record `1009.3`'s pattern and description realigned, record `1066.1` added.
- `openspec/changes/fix-the-suite-drops-the-runs-state-root/specs/agent-issue-loop/spec.md` — the requirement the rule
  lacked.

What changes in behaviour: a suite started from a child no longer hands its cases the run's record, so the gate's own
step and every case that runs the loop write into their own scratch trees. Nothing else reads the variable out of the
suite's environment: the cases that need a state path (23 of the 25 matches in `loop-cases.sh`; the other two are this
change's guard and the arm beside it) set it on the command line of the process they start, which is untouched by the
driver's drop.

## Alternatives the evidence eliminated

- **Let each case unset it.** The suite has 549 cases; the drop is one line and the rule is the driver's. A rule
  written 549 times is the shape issue #1009 already replaced for the three other roots.
- **Refuse an in-tree inherited state path in `lib.sh`.** `loop_path_outside_tree` cannot tell the run's record from
  the fixture's own tree: the fixture's tree *is* the repository when the suite's scratch root sits under it, and the
  same rule is what lets a fixture's run use its own `run.json` (`case_state_inheritedStatePathOutsideTheTreeIsIgnored`
  holds the outside case). Widening it would break the fixtures it protects.
- **Fix the chain's reader instead.** The item records it as a second shape of the same failure that belongs to
  whoever owns the chain: `run-one-item-quota.sh:211` reading `.items[0]` is a defect about the record's shape, and it
  stays open after this change (the record is now the item's own).
- **Drop it in `run.sh`'s export.** The export is what makes a child's own `run.sh` agree with the run about the state
  file; removing it would break the child, and the rule that a case inherits no root is the suite's, not the run's.

## Rollback

Delete `LOOP_STATE_FILE` from `tools/loop/tests/loop-selftest.sh:179`, from the guard list in `tools/loop/tests/loop-cases.sh:18798` and from the nested
arm beside it, restore record `1009.3`'s pattern and description, drop record `1066.1`, and remove the change
directory. Three lines and two records; no data moves.

## How it is proven

The extended case is red on the base commit in a shell the run exported (`evidence/red-on-HEAD.log`, transcribed into
the tally carrier in `evidence/red-on-HEAD.xml`), green after the drop (`evidence/green.log`), and both mutation
records redden that case and go green after the restore (`evidence/mutations.log`). The nested arm is not vacuous: with
the drop removed and an outer environment that exports no `LOOP_STATE_FILE`, the case reddens on the arm its own suite
holds and names a path under its own scratch tree (`evidence/nested-arm-red.log`).
