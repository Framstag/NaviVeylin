# Design — the map covers the pipeline's checks, and a case keeps it covered

## The root cause, one sentence

The profile's `LOOP_GATE_SUITES` is a hand-kept subset of the commands the pipeline's `Check …` steps run —
ten of thirty-one on this change's base — and `tools/loop/gate.sh` reads a path no glob names as owing nothing
and exits 0, so a delta whose check the map never reaches is green in the run while the pipeline refuses it.

## The one fix

Two edits to the profile and one to the suite that reads it:

1. **`LOOP_GATE_SUITES` is completed**, 24 pairs naming 10 commands → 58 pairs naming all 31 (plus the
   `--check` record pass the map already carried). The unit of a pair is a pipeline *step*, and a pair carries
   the triple of the tool it belongs to — the check, its self-test and its mutation `--list` — while each other
   command a step runs is carried by the path of the tool that command belongs to (`tools/issues/*` owes the
   issue tooling's self-test; the five tracker sets owe their own `tools/mutate.sh … --list`), because the
   pipeline runs the step as a unit and any of its commands can be the one that reddens. No path is mapped to a
   step whose tools do not read it — that would be option 2 of the item's recorded decision, which the owner
   rejected.

   The map keeps the gate's own `--from-git {base}` narrowing on the loop pairs (issue #276), so the pin strips
   that suffix before comparing a map command with the pipeline's — the suffix is the gate's filter, not part
   of the command.
2. **`LOOP_GATE_PIPELINE=".github/workflows/build.yml"`** is recorded: the pipeline file the map is pinned to.
   It is a project fact like the gate command, so it lives in the profile and no engine names it.
3. **Two cases** in `tools/loop-cases.sh`: the pin (the map against the pipeline's `Check …` steps) and the
   behaviour (the plan for a path that step reads). The pin is the alarm the recorded decision's residual needs
   — the map stays a copy, and the copy now fails the loop's suite the day the pipeline grows a step. The
   behaviour case asserts all three commands the `Check the build inputs` step runs and, negatively, that no
   command of another step appears: a coarse glob (`tools/*`) satisfies every positive assertion while making
   the map claim every step for every path, which is the rejected option 2.

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
- `tools/loop-mutations.sh` — four records, 54.1–54.4, one per assertion: 54.1 and 54.2 remove a pair from the
  copied profile, 54.3 removes the mutation-list pair, 54.4 adds a pair that reaches the portability check
  through a coarse glob.
- `guidelines/Build.md` §3 — the gate row states the bound.
- Cost: a delta on `openspec/**` or a note now owes the whole documentation-routes step (two scripts and one
  mutation list) and `tools/check-todo-references.sh` — the two doc checks and the citation scan run in under a
  second each, the classification pass reads 177 specs in about two and a half — each timed in
  `evidence/after-final-edit.log`. A `.github/workflows/*` delta owes the loop's suite as well as the
  action-runtimes suite, because the loop's suite is where the map is pinned against the workflow.
- No Kotlin, Java or C++ file is touched: `LOOP_GATE_COMPILED_CMD` is not owed by this change.

## Rollback

Revert the four files. The profile's map reverts to the 21 pairs, the two cases disappear, and the two mutation
records with them. Nothing reads `LOOP_GATE_PIPELINE` outside the pin case, so an older profile (without the
key) is still runnable by an older engine and vice versa — the key is additive.

## What the change does not decide

A new pipeline step still has to be added to the map by hand. The owner accepted that cost when it chose the
map over running every step; the pin makes the omission loud instead of silent, and the step that is loud is
the loop's own suite, whose case names every missing command.

The pin's subject is narrower than "a step's every action", and that is deliberate: it reads the `run:` lines
that name a script, so a step whose check is inline shell or a Gradle task is outside it. The path side is
narrower still, and is stated as a residual rather than claimed: which paths a step reads is named by no
workflow, so a path a check reads but the map does not name is gated by no command of that step.
`tools/check-portability.sh` reads `tools/loop/loop.conf` (its `LOOP_GATE_CMD` value), `tools/tooling.json` and
`tools/sensors/sensors.json`, and none of the three maps to a command of `Check portability`; the item filed
for the residual owns closing it (**#289**). Three `Check …` steps
run inline shell on `ee95d71` (`Check libosmscout Android-free outside Android/`, `Check OpenSpec artifact
hygiene`, `Check daemon JVM criteria`) and one runs a Gradle task (`Check license policy`). The one with a
consequence a reader can hit today is the daemon JVM criterion over `gradle/gradle-daemon-jvm.properties`, a
path no `LOOP_GATE_COMPILED_PATTERNS` glob matches: a delta touching it is green in the run and red in the
pipeline. The recorded decision's option 1 accepted the map as a copy with an alarm; closing this last corner
would need either a `gradle/*.properties` compiled pattern or a parser for inline shell, and both are a new
claim on top of the decision rather than a correction of it.
