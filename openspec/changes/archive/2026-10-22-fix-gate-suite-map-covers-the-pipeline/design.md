# Design — the map covers the pipeline's checks, and a case keeps it covered

## The root cause, one sentence

The profile's `LOOP_GATE_SUITES` is a hand-kept subset of the commands the pipeline's `Check …` steps run —
eight of thirty-one on `ee95d71` — and `tools/loop/gate.sh:95` reads a path no glob names as owing nothing and
exits 0, so a delta whose check the map never reaches is green in the run while the pipeline refuses it.

## The one fix

Two edits to the profile and one to the suite that reads it:

1. **`LOOP_GATE_SUITES` is completed**, 21 pairs naming 8 commands → 55 pairs naming all 31. The unit of a pair
   is a pipeline *step*, not a command: a path maps to every command that step runs (the check, its self-test,
   its mutation `--list`), because the pipeline runs the step as a unit and any of its commands can be the one
   that reddens. No path is mapped to a step whose tools do not read it — that would be option 2 of the item's
   recorded decision, which the owner rejected.
2. **`LOOP_GATE_PIPELINE=".github/workflows/build.yml"`** is recorded: the pipeline file the map is pinned to.
   It is a project fact like the gate command, so it lives in the profile and no engine names it.
3. **Two cases** in `tools/loop-cases.sh`: the pin (the map against the pipeline's `Check …` steps) and the
   behaviour (the plan for a path that step reads). The pin is the alarm the recorded decision's residual needs
   — the map stays a copy, and the copy now fails the loop's suite the day the pipeline grows a step.

## Why this and not the alternatives

- **Run every pipeline step for every item** (the decision's option 2): eliminated by the owner's recorded
  decision, and by the measurement that a doc-only delta would then pay the Gradle gate the pipeline runs beside
  it for no compiled path.
- **Derive the selection from the workflow at run time** (read the pipeline instead of repeating it): the
  workflow declares no per-step pathspec, so "which step does this path owe" is not derivable from `build.yml`;
  deriving it degenerates into running everything, i.e. option 2. Recorded by the item's earlier pass
  (`.pi/issue-loop/items/242/phase-a-verification.md`).
- **Have `gate.sh` refuse a map that does not cover the pipeline**: this would spend an engine's behaviour on
  the residual and would make every item's gate a coverage check of a file the item did not touch. The alarm
  belongs in the suite that owns the map (`.github/workflows/*` is mapped to it), where it costs one grep and
  lands on the change that moves the pipeline.
- **Read the pipeline in the engine rather than in the case runner**: the case runner is where the project's
  own assertions live (`run#engineNamesNoProjectFact` keeps project facts out of the engine), and the pin is an
  assertion, not a phase.

## Blast radius

- `tools/loop/loop.conf` — one key added, one value replaced. `gate.sh`, `measure.sh` and `run.sh` read it
  unchanged; `measure.sh`'s block simply names more checks for a delta that owes them.
- `tools/loop-cases.sh` — two cases added. Both read the profile; the pin reads the project's own pipeline file
  through `$LOOP_PROJECT_ROOT` and parses only the `bash`/`sh` lines under a step named `Check …`, so a publish
  step (`Publish the sensor inputs` runs `tools/build-inputs.sh --write`) is not one of them.
- `tools/loop-mutations.sh` — two records, 40.1 and 40.2, one per case, each removing a pair from the copied
  profile.
- `guidelines/Build.md` §3 — the gate row states the bound.
- Cost: a delta on `openspec/**` or a note now owes the whole documentation-routes step (two scripts and one
  mutation list) and `tools/check-todo-references.sh`; measured 0.14 s and 0.50 s on this tree. A
  `.github/workflows/*` delta owes the loop's suite as well as the action-runtimes suite, because the loop's
  suite is where the map is pinned against the workflow.
- No Kotlin, Java or C++ file is touched: `LOOP_GATE_COMPILED_CMD` is not owed by this change.

## Rollback

Revert the four files. The profile's map reverts to the 21 pairs, the two cases disappear, and the two mutation
records with them. Nothing reads `LOOP_GATE_PIPELINE` outside the pin case, so an older profile (without the
key) is still runnable by an older engine and vice versa — the key is additive.

## What the change does not decide

A new pipeline step still has to be added to the map by hand. The owner accepted that cost when it chose the
map over running every step; the pin makes the omission loud instead of silent, and the step that is loud is
the loop's own suite, whose case names every missing command.
