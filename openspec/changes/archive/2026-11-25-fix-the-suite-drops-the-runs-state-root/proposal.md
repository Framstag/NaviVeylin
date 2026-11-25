# Proposal: fix-the-suite-drops-the-runs-state-root

## Why

**Root cause.** `tools/loop/run.sh:85` exports `LOOP_STATE_FILE` and `LOOP_WORKTREE` to every child of a run, and
the suite a child runs to verify itself does not drop the first of them: at the run's base `184376ec`, the `unset` line of
`tools/loop/tests/loop-selftest.sh` (the line this change extends, now `:179`) names
`LOOP_WORKTREE`, `LOOP_TOOL_RESULT_LEDGER` and `LOOP_WORKTREE_SCRATCH_PATHS` — the three roots issue #1009
measured — and not `LOOP_STATE_FILE`. The suite's scratch tree is
`$LOOP_ROOT/.pi/tmp/loop-selftest.XXXXXX` (`tools/loop/lib.sh:223`, `tools/loop/tests/loop-selftest.sh:236`), which sits inside the
repository, so a case that runs `run.sh` resolves the same toplevel and the inherited
`<repo>/.pi/issue-loop/run.json` is *inside* that tree: `loop_path_outside_tree`
(`tools/loop/lib.sh:208-216`) does not drop it, and the case's fixture writes its items into the live run's record.

**Evidence** ⊨ (change root at the run's base `184376ec`, `.pi/issue-loop/items/1066/work-package.md`):

- **the unset line names three roots and the run exports a fourth** ⊨ the `unset` line of
  `tools/loop/tests/loop-selftest.sh` at base `184376ec` (now `:179`)
  (`unset LOOP_WORKTREE LOOP_TOOL_RESULT_LEDGER LOOP_WORKTREE_SCRATCH_PATHS`) against
  `tools/loop/run.sh:85` (`export LOOP_STATE_FILE="$LOOP_STATE_FILE" LOOP_WORKTREE="$LOOP_WORKTREE"`). The
  environment this change was written in carries both (`LOOP_STATE_FILE=/…/.pi/issue-loop/run.json`,
  `LOOP_WORKTREE=/…/.pi/worktrees/loop`), so the suite a child runs today hands its cases the run's record.
- **the cost, from the run of 2026-11-24** ⊨ that run (item #984, 18:23–19:12) left three item records in
  `.pi/issue-loop/run.json`: `items[0] {issue:22, result:done}` at `17:56:53Z`, `items[1] {issue:32}` at
  `17:57:18Z`, `items[2] {issue:984}` at `18:12:04Z`. #22 and #32 are the suite's own fixture numbers
  (`grep -c '"issue":32' tools/loop/tests/loop-cases.sh` is 4), and the chain read `.items[0].issue` to charge an
  attempt (`tools/loop/run-one-item-quota.sh:211`), printing `spent #22` at `19:12:14` while #984's counter stayed at
  1 of 2 — a spent attempt refunded, and a bogus `22 1` row in `.pi/scratch/pin-attempts.tsv` removed by hand.
- **red on HEAD** ⊨ `driver#theSuiteDoesNotInheritTheRunsRoots` extended by this change: started from this child's
  shell, `bash tools/loop/tests/loop-selftest.sh --case 'driver#theSuiteDoesNotInheritTheRunsRoots'` ends
  `loop-selftest: 1 of 1 cases failed`, first failure
  `the driver handed the run's own root to the suite: LOOP_STATE_FILE=/…/.pi/issue-loop/run.json`
  (`evidence/red-on-HEAD.log`). A shell suite carries no JUnit XML of its own (`.pi/skills/revert-check`, "When the
  gate is a shell self-test, not Gradle"): the tally is the suite's own line.
- **the owning guideline owes no edit** ⊨ `tools/look.sh 'LOOP_STATE_FILE' --path guidelines --files` answers
  `LOOK: 0 matches in 0 files` — no section of `guidelines/` states which roots a case may inherit, so the requirement
  this change adds is the shipped text the rule lacked, and the comment beside the drop stays its statement
  (`tools/loop/tests/loop-selftest.sh:168`).

## What changes

- `tools/loop/tests/loop-selftest.sh:179`: `LOOP_STATE_FILE` joins the `unset` list, and the paragraph above it
  says why the run's state file is a root a case must not inherit — the sentence the other three already carry.
- `tools/loop/tests/loop-cases.sh` (`case_driver_theSuiteDoesNotInheritTheRunsRoots`): the guard's `for` list takes
  the fourth variable, so a case fails and names the root when the driver hands over the run's record; the case also starts a
  suite of its own under `LOOP_STATE_FILE=<scratch>/run.json`, so the drop is held one level down, where a case runs the
  loop.
- `tools/loop/tests/loop-mutations.sh:1607`: record `1009.3`'s pattern follows the line it mutates, its
  description names the state root the same `unset` drops, and record `1066.1` is added — it puts the three roots
  #1009 measured back and leaves the state file out, so the fourth name has a mutation of its own.
- `openspec/changes/fix-the-suite-drops-the-runs-state-root/specs/agent-issue-loop/spec.md`: one ADDED requirement,
  "A case inherits none of the run's own roots", with one scenario naming the extended case.

## Surface

surface: 1 `unset` line extended, 1 comment paragraph extended, 1 case's guard list and comment extended plus 1 nested
arm and 1 declaration line extended (`tools/loop/run.sh`), 1 mutation record realigned, 1 mutation record added,
1 requirement added, 1 scenario added —
`tools/look.sh 'LOOP_STATE_FILE'` over this tree and `tools/look.sh 'theSuiteDoesNotInheritTheRunsRoots'` over it, plus
the case's own declarations (`evidence/surface.log`).

`tools/look.sh 'LOOP_STATE_FILE' --files` answers 30 files (`evidence/surface.log`) — the change's own
`openspec/changes/` directory and its evidence are among those files — and the matches in
`tools/loop/tests/loop-cases.sh` are cases that *type* the path on their own command line
(`LOOP_STATE_FILE="$state" bash …`) beside the one new guard: the shape this change keeps legal is a case that wants a
root sets it. The rule that changes is the driver's, and it is asserted in three places: the `unset` line
(`tools/loop/tests/loop-selftest.sh:179`), the guard (`tools/loop/tests/loop-cases.sh:18798`) and the mutation records
(`tools/loop/tests/loop-mutations.sh:1607` and `:1611`).

## Consequence

A suite started from a child of a run — the gate's own step, or any case that runs the loop — no longer hands its
cases the live `run.json`, so a fixture's items stay in the fixture's tree, the chain charges the attempt to the item
that spent it, and no bogus row reaches the item's attempt record. A case that wants the run's state path still sets
it on its own command line, which is how every case that reads one is written today.
