# Design — the base-red is measured when a gate needs it

## The question the item asks

A measurement is taken before the queue is known, or when the question is asked. The current design chose the first
deliberately, and the change is therefore not "measure less" but "measure when the question is asked".

## Decision 1 — when the measurement is taken

**Chosen: on the step's first red, inside the item whose gate hit it.** A step's base state is needed only to decide
whether the step's red is the base's or the delta's, so a green step needs none. The measurement therefore hangs off
the subtraction's own `if [ "$step_red" -eq 1 ]` path, where the record is read anyway, and is narrowed to the one
command the gate is holding. Cost: the item whose gate first meets a red step absorbs that step's measurement in its
own wall clock (828 s worst case in this project, 21 % of `LOOP_CHILD_MAX_SECONDS=3900`), and it is named on the
console as `LOOP-GATE-BASERED-MEASURE:<step>` so the wall is attributable.

- *Alternative: measure the profile's whole step set at the first red step.* Refused — that is the 1 251 s the issue
  measures, paid by the first item that is red at anything, including steps no item consults.
- *Alternative: keep the pre-flight but make it switchable per run.* Refused — it moves the cost between runs and not
  out of the nights that need two steps; the record is one map either way.
- *Alternative: measure every step an item's delta owes, at the gate's start.* Refused — the delta of a `tools/loop/**`
  change owes the two giants whether or not they are red, so it pays 1 251 s on a green gate; only a red asks.

## Decision 2 — which tree the measurement runs in

**Chosen: a clean tree at the base commit — the gate's own tree when that is the base, otherwise the repository's main
worktree** (`base_tree`). It is the tree the pre-flight measured in (so the compiled step finds the build output the
base already has: a fresh checkout under `TMPDIR` would pay a cold native build and blow the step bound), and it is the
tree the base branch is checked out in while an item works in its own worktree. A tree whose `HEAD` is not `$base`, or
which carries uncommitted work, is not the base and is refused.

- *Alternative: measure in the item's own worktree.* Impossible — the step re-run on the delta fails exactly as it
  failed a moment ago, so every red would be subtracted as the base's. This is the reason `base_tree` exists.
- *Alternative: a detached worktree at `$base`.* What `attribute_red_gate` does for one case. Impossible for a step:
  a fresh tree carries no build output and no `.pi/loop-env.sh`, so the compiled step would fail at configuration and
  a configuration failure recorded as base-red would mask the item's own red.

## Decision 3 — how the record is written and what it is keyed by

**Chosen: `state.sh base-red-step` merges one measurement into `run.baseRed`, the shape `base-red` already writes, and
sets `run.baseRedBase` to the commit measured.** The subtraction stays unchanged, because it reads `{step: [case
names]}`; a step the base is green at records nothing, because absence is what the gate already reads as "the base
carried no red here"; and the record's base name dates a record taken after the run started, exactly as the pre-flight's
name did. The measured command is the gate's own owed command, so the record is keyed byte-identically.

- *Alternative: a per-step map of base → cases.* Refused — it changes a shape two writers and the subtraction share, to
  answer a question (a step red at one base and green at another) that the shipped pre-flight did not answer either:
  its single record was read by every item's gate however far the base had moved.
- *Alternative: record measured-green steps too.* Refused — the only thing it buys is not re-measuring a step that the
  run's own gates keep breaking, and the item that breaks it fixes it or is blocked; nothing else can read the mark.

## Decision 4 — the run state a gate in the worktree reads

**Chosen: `loop_path_outside_tree` honours a path inside the main worktree of the repository it resolved.** The child
works in the run's own kept worktree, which is a linked worktree of the project, and the run's state lies outside it by
construction; #214's rule ("a path the environment names outside this tree is not this run's") was written for scratch
repositories and fixtures, which are other repositories. Measured 2026-11-17: without this, `state.sh get run.baseRed`
from `.pi/worktrees/loop` refuses, and `state.sh base-red-step` would write a state of its own — so the lazy record could
be neither written nor read.

- *Alternative: hand the gate the state path as a second, trusted variable.* Refused — a second identity variable is a
  second rule, and the fact the two paths share (one repository) is the thing that tells the run's tree from a
  fixture's.
- *Alternative: leave it and have the run pass the record to the gate in the worktree.* Refused — the record has to be
  written back, is read by later items' gates from other worktrees, and a copy beside the worktree would be a second
  copy of the run's record.

## Risk

- The first item that meets a red step pays its measurement. Bounded by the step bound (`LOOP_GATE_STEP_MAX_SECONDS`,
  1800 s) and named on the console; the run's own gate accounting records the item's wall as the item's.
- A measurement taken after the base has moved is applied to a gate whose base is older. This is the shipped behaviour
  of the pre-flight (one record, read by every item), and `run.baseRedBase` names the commit measured, so the record
  dates itself. `base_tree` additionally requires the measured tree to be *exactly* `$base`, so a measurement is never
  taken at a base the gate did not name.
- The measurement runs while the gate holds the tally lock. It writes no tally and takes no lock of its own, so the
  only effect is that the lock is held longer; a second gate in the same tree still refuses as it did.

## Verification

- `gate#theBaseStateIsMeasuredWhenTheGateFirstNeedsIt` — red on HEAD before the change (recorded:
  `tests=3 failures=1 … — failing: ./suite-one: expected '0', got '1'`), green after.
- `944.1` / `944.2` — the measurement removed, the worktree half of the identity rule removed: each reds the case and
  restores green. `921.1` moved onto the line this change leaves; `--check` over the three records applies all three.
- The `gate` group of the self-test (41 cases) and the full suite; `state#inheritedStatePathOutsideTheTreeIsIgnored`
  for the other direction of the identity rule.
- No compiled file is touched, so no Gradle build and no Android suite is owed (`guidelines/Build.md` §2).
- After the change, measured as the issue asks: the run's own start to first item (`measure.sh`), and `run.baseRed`
  keys against the steps its items actually ran.
