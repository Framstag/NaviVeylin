# Proposal: fix-the-base-red-is-measured-when-a-gate-needs-it

## Why

The run measures the base's red for its whole profile before it works anything, and most nights need two of those
steps (issue #944).

- **Observed** ✗: `take_base_red` (`tools/loop/run.sh:908-941` before this change) asked `tools/loop/gate.sh --base-red`
  for every `<glob>|<command>` pair of the profile at the run's start, whether or not any item of the night would
  consult a step. `run.baseRed` is only a lookup a gate reads (`tools/loop/gate.sh`, the subtraction beside the
  step's tally), so a step's base state is needed only when an item's gate runs that step.
- **Evidence** ⊨: `tools/loop/gate.sh --base-red --base c3545cfc07b6` on 2026-11-16 measured 59 steps in **1 772 s of
  step time**, five steps costing 1 540 s (87 %) and 46 steps under 10 s costing 54 s (3 %):
  `tools/loop/tests/loop-selftest.sh --from-git` 828 s, `./gradlew test -PforceTests -PjvmTestsOnly
  --no-build-cache` 423 s, `issue-tooling-selftest` 109 s, `sensor-selftest` 99 s, `loop-mutations-selftest` 81 s. The
  two giants are owed only by a change touching `tools/loop/tests/loop-cases.sh` and by a compiled path, so a night
  of docs, tracker and skill items consults neither and still pays both: **1 251 of the 1 772 s are steps no item will
  consult**, spent before the first item, outside every item's wall and tokens.
- **Second defect, found while reading the first** ⊕: a gate run in the item's own worktree could not read the record
  at all. `run_child` exports the run's `LOOP_STATE_FILE` and the child is told to `cd` into the change root, and
  `lib.sh`'s `loop_path_outside_tree` dropped that path because it lies outside *the worktree* — measured 2026-11-17:
  `state.sh get run.baseRed` from `.pi/worktrees/loop` answered `no run state at
  .pi/worktrees/loop/.pi/issue-loop/run.json`, the live run's `.run.baseRedUsed` was `null` although its record held a
  red step, and `state#inheritedStatePathOutsideTheTreeIsIgnored` only ever proved the opposite direction. So the
  subtraction #921 added had never fired for a gate: it was a written record no gate read.
- **Consequence** ⊨: 29 minutes of a 600-minute budget (4.8 %) are spent on a measurement most nights need two steps
  of, and the measurement the run does take is invisible in the item accounting — which is why it never appeared in a
  report until the window was printed.
- **The one fix** ➜: measure a step's base state the **first time an item's gate needs it**, and cache it. The gate
  takes the measurement itself, narrowed to the one step it has just found red (`gate.sh --base-red --step <command>`),
  in a clean tree at the base commit — the repository's main worktree, which is where the pre-flight measured and where
  the compiled step finds the build output the base already has — keeps its output beside the run, and merges it into
  `run.baseRed` through a new `state.sh base-red-step`, with `run.baseRedBase` naming the commit it was taken on. The
  subtraction is unchanged: same record shape, same pairs, same `LOOP-GATE-BASERED:<step>:<n>` line. A step the record
  holds costs one `jq`; a step the base is green at records nothing. The second defect is the same fix's precondition:
  `loop_path_outside_tree` honours an inherited state path that lies inside the main worktree of the repository the
  process resolved, which is what the run's own worktree is and what a scratch repository or a fixture is not.
- **Requirement contradicted**: `openspec/specs/agent-issue-loop/spec.md`, "A step red on the run's base is subtracted
  from the item's tally and named" — "A run SHALL measure, **before it works anything**, which of the gate's steps the
  base it starts from is already red at".

## What Changes

1. **The gate measures the step it needs** (`tools/loop/gate.sh`): `--step <command>` narrows a `--base-red` call to
   the one pair the profile owes under that command (a command no pair names is a refusal, because a measurement of
   nothing would read as a base that was green); `base_tree` answers a clean tree at `$base` (the gate's own tree when
   that is the base, otherwise the repository's main worktree); `measure_step_base` runs the narrowed measurement
   there, keeps its output in `<state dir>/base-red.log`, writes the record through `state.sh base-red-step`, and
   reloads `base_red_json` so the existing subtraction applies. It is called from the step's own finish path, only when
   the step is red and the record holds no state for it, so a green step measures nothing. A measurement that cannot be
   taken, read or written prints `LOOP-GATE-BASERED-UNMEASURED:<step>` and leaves the red with the item.
2. **The record gains a step at a time** (`tools/loop/state.sh`): `base-red-step --from <file> --base <sha> --step
   <command>` merges one measurement into `run.baseRed` (the shape `base-red` writes, so the subtraction is unchanged),
   sets `run.baseRedBase`, and refuses an output naming a step other than the one the call measures. The jq reader of
   the `LOOP-BASERED-STEP:` / `LOOP-BASERED-CASE:` lines moves into `base_red_read`, one reader for both writers.
3. **The start takes no measurement** (`tools/loop/run.sh`): `take_base_red` and its four guards are gone; the block
   prints which mode the run is in and nothing else. `LOOP_BASE_RED=off` keeps charging every red to its item, and its
   comment in `tools/loop/loop.conf` now states the lazy measurement and the measurement it replaced.
4. **A worktree of the run's own repository carries the run's identity** (`tools/loop/lib.sh`):
   `loop_path_outside_tree` treats a path inside the **main worktree** of the repository it resolved as this tree's
   own, through `loop_main_worktree`, so a gate run in the item's worktree reads and writes `run.baseRed`,
   `run.baseRedBase` and `run.baseRedUsed`. A scratch repository, a fixture and a path under a temporary root are
   still ignored, which is the case #214 measured.
5. **One case, two mutations** (`tools/loop/tests/loop-cases.sh`, `tools/loop/tests/loop-mutations.sh`):
   `gate#theBaseStateIsMeasuredWhenTheGateFirstNeedsIt` works in a worktree of a project that stays at the base, and
   holds that the gate measures only the step it found red, records it against the base commit, writes the run's own
   record from the worktree, and that a second gate measures nothing because the record already holds the step. `944.1`
   removes the measurement, `944.2` removes the worktree half of the identity rule; `921.1` moves onto the line this
   change leaves.
6. **The rule and its spec**: `guidelines/BuildEvidence.md`'s base-red rule states the lazy measurement; the spec delta
   modifies "A step red on the run's base is subtracted from the item's tally and named" and adds "A worktree of the
   run's own repository carries the run's identity".

Not changed: the subtraction itself (pairs, the `LOOP-GATE-BASERED:<step>:<n>` line, `— base-red: <step>:<n>` in the
verdict, `run.baseRedUsed`), the base-red lines `gate.sh --base-red` prints, `base-red` as the whole-set writer (a hand
run and the 921 case still use it), the step bound and the pool, and every other bound of the run.

## Impact

- Files: `tools/loop/gate.sh`, `tools/loop/state.sh`, `tools/loop/lib.sh`, `tools/loop/run.sh`, `tools/loop/loop.conf`,
  `tools/loop/tests/loop-cases.sh`, `tools/loop/tests/loop-mutations.sh`, `guidelines/BuildEvidence.md`, and this
  change's specs.
- Modules: none — no Kotlin, Java, C++, Gradle or pipeline file is touched, so no build and no Android suite is owed
  (`guidelines/Build.md` §2, the scope block). The shell suite that owns `tools/loop/**` is the gate.
- Specs changed: `agent-issue-loop` — MODIFIED "A step red on the run's base is subtracted from the item's tally and
  named", ADDED "A worktree of the run's own repository carries the run's identity".
- Additive, not breaking: `gate.sh --base-red` with no `--step` behaves exactly as before, and `--step` is refused
  outside `--base-red`. `state.sh base-red-step` is a new subcommand; `state.sh base-red` is unchanged in shape and
  behaviour. A run's record still holds `{step: [case names]}`.
- Rollback: revert the eight files. The run then measures the whole profile at its start again and every red a gate
  meets is charged to its item (the worktree identity stays broken, which is the state the tree was in before this
  change). `run.baseRed` written by a partially reverted engine is still the shape both halves read.
