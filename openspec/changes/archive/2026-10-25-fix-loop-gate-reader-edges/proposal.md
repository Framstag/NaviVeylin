# Proposal — the gate's suite map closes the reader path side too

## Why

Measured in `.pi/issue-loop/items/289/` (branch `fix/issue-289-the-loop-s-gate-map-closes-the-command-s`,
base `85d0294336a3a5c10c60275510be1288841d82fe`, 2026-10-24T23:2xZ):

- The map completed in #242 pins the **command** side: every `bash`/`sh` command a `Check …` step of
  `.github/workflows/build.yml` runs is a value of `LOOP_GATE_SUITES`, held by
  `gate#theMapCoversEveryPipelineStep`. Its **path** side is a hand-kept copy, so a path a step's *own* tool
  does not name but another step's tool opens is owed by no command.
- `tools/check-portability.sh` (the `Check portability` step) opens `tools/loop/loop.conf`,
  `tools/tooling.json` and `tools/sensors/sensors.json` and scans `tools/issues/*.sh`, `tools/sensors/*.sh`
  and `tools/loop/*.sh` as its engine set. The map routes none of those seven paths to any command of that
  step (`evidence/plan-before.txt`): a delta touching one of them answers `nothing owed` and exits 0.
- The reproduction is the 2026-10-21 shape: put a repository name into `LOOP_GATE_CMD` inside
  `tools/loop/loop.conf` (or a reserved label into `tools/tooling.json`) and the run's gate is green while
  `Check portability` reddens. **Root cause, one sentence: the map's path side is a hand-kept copy of which
  files each step's tools read, and `Check portability`'s seven reader edges are not in it, so the gate reads
  them as owing nothing and exits 0.**
- The red case is added before the map changes and fails on HEAD:
  `gate#aPathACheckReadsOwesThatStepsCommandSet` — `1 of 1 cases failed`, the first assertion
  `'tools/check-portability.sh' not found in: tools/loop-selftest.sh --from-git …`
  (`evidence/red-on-HEAD.log`, exit 1).
- One step's command cannot run in the change root: `tools/check-portability-selftest.sh` refuses any
  `/.pi/` root with exit 2 (`tools/check-portability-selftest.sh:38-43`), and the loop's gate runs in the
  linked worktree `.pi/worktrees/loop`. Mapping the whole step as it stands would turn the gate red for every
  delta touching a reader path on an environment refusal. A linked git worktree **is** a checkout, though —
  the `tooling-portability` requirement *Discovery is exercised where it breaks* already says so for the tools
  ("Invoked in a linked working tree") — so the precondition, not the map, is what has to change. Measured
  with the precondition relaxed: `all 29 cases passed` in `.pi/worktrees/` (`evidence/worktree-run.txt`).
- The requirement the defect contradicts is `agent-issue-loop` — *"The gate's suite map covers every script
  command the pipeline's check steps run"*, whose second scenario names the three configs as *"a residual the
  map cannot close"*. The precondition is `tooling-portability` — *Discovery is exercised where it breaks*.
  The owner's recorded answer to the item's four options is **option 1** (full fix), which this change is.

## What Changes

- **`tools/loop/loop.conf`**: `LOOP_GATE_SUITES` gains the measured reader edges, each mapping to the *whole*
  command set of the step whose tool reads the path: four commands for `Check portability` (three configs and
  three engine globs), three for `Check the build inputs` (`tools/sensors/sensors.json`), two for
  `Check issue metadata` (the profile and the registry), and — from the review round — all six commands of
  `Check issue tooling`, whose case `lib#onlyTheSeamCallsTheRealCli` greps `tools/loop/*.sh` and
  `tools/sensors/*.sh` and whose taxonomy case reads `tools/sensors/sensors.json`. The map's unit stays a step:
  the pairs are added beside the existing ones, never replacing a tool's own globs.
  `gate#theMapCoversEveryPipelineStep` still passes because every added command is already a map value.
- **`tools/check-portability-selftest.sh`**: the `.pi/` precondition asks git and the fixture instead of the
  path alone — a root under `.pi/` that is not the top of this project's working tree is refused, a linked
  working tree is not. The header states the refined precondition.
- **`tools/portability-cases.sh`**: the guard case is joined by `preconditions#runsInALinkedWorkingTree`, and
  `foreign#fixtureIsVersioned` asks git whether the fixture is tracked rather than testing the path for `.pi/`.
- **`tools/loop-cases.sh`**: one case, `gate#aPathACheckReadsOwesThatStepsCommandSet`, drives the plan for five
  deltas — the loop configuration, the profile, the registry, an engine `Check portability` scans and the issue
  tooling's case greps, and an issue-tooling engine.
- **`tools/loop-mutations.sh`** records `54.5`–`54.10` and **`tools/portability-mutations.json`** records `6.6`
  and `6.7`, so each new invariant has a mutation whose named case goes red.
- **`guidelines/Build.md` §3** and the two skill files state the path side as pinned and name the residual
  that stays: a *new* pipeline step and the reader edges it adds are hand-kept.
- **Spec deltas**: `agent-issue-loop` (MODIFIED requirement + one new scenario) and `tooling-portability`
  (MODIFIED requirement, precondition refined).

The change is additive to the shell tooling: no engine under `tools/loop/` reads a new key, no Kotlin, Java or
C++ file is touched, and the compiled-path gate is untouched. Rollback is the revert of the six files.

## Impact

- Affected files: `tools/loop/loop.conf`, `tools/check-portability-selftest.sh`,
  `tools/portability-cases.sh`, `tools/portability-mutations.json`, `tools/loop-cases.sh`,
  `tools/loop-mutations.sh`, `guidelines/Build.md`, `.pi/skills/portability-check/SKILL.md`,
  `.pi/skills/mutation-runner/SKILL.md`, the two spec deltas.
- Affected modules: none — no compiled file changes, so `LOOP_GATE_COMPILED_CMD` is not owed and the Gradle
  gate does not run (`guidelines/Build.md` §2, the scope block).
- Specifications changed: `agent-issue-loop`, `tooling-portability`.
- Cost per item: a delta touching `tools/loop/**`, `tools/issues/**`, `tools/sensors/**`,
  `tools/tooling.json` or `tools/sensors/sensors.json` now also owes the `Check portability` step. Measured:
  the portability self-test is 22 s, the whole step about 25 s (`evidence/worktree-run.txt`), against the
  loop's self-test at up to 723 s. This very change touches `tools/loop/**`, so its own gate pays it.
- Breaking: none for the project; a second project copies the refined precondition with the engine and edits
  no phase.
