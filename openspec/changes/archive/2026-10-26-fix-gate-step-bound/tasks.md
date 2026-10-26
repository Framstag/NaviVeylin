# fix-gate-step-bound — tasks

- [x] 1.1 (`agent-issue-loop` — *A gate step is bounded, and the step that overran is named*) `tools/loop/gate.sh`:
      `run_step` starts the step under `set -m`, reads the step's status from the record the step writes, polls it
      against `LOOP_GATE_STEP_MAX_SECONDS` and ends the step's group with `loop_end_group` when the bound fires; the
      caller appends the `LOOP-GATE-STEP:` line to the stream the tally is derived from, suffixes the console's step
      line, and counts the step as an error; the bound in force is printed once, before the first step. Verify:
      `bash tools/loop-selftest.sh --case gate#aStepIsBoundedAndTheOverrunNamesIt` green, and the group's other 16
      cases green (`bash tools/loop-selftest.sh --group gate`) — `evidence/green-cases.log`.
- [x] 1.2 `tools/loop/loop.conf`: `LOOP_GATE_STEP_MAX_SECONDS=1800`, with the measurement it is set against and the
      900 s of a 2700 s child it leaves. Verify: the same run of 1.1 (the case sets the key to 2 s in its own
      profile and reads the outcome it causes) and `bash tools/check-portability-selftest.sh` green — the profile is
      the portability seam.
- [x] 1.3 `tools/loop-cases.sh`: the case `gate#aStepIsBoundedAndTheOverrunNamesIt`, in `LOOP_SHELL_CASES`, in the
      dispatch table and with its `# files:` declaration. It is red on the base (`evidence/red-on-head.log`, tally
      `evidence/red-on-head.xml`, `tests="1" failures="1"`) and green after 1.1. Verify:
      `bash tools/loop-selftest.sh --case driver#everyCaseDeclaresTheFilesItCanReach` and
      `driver#theListIsFilteredTheSameWayASuiteRunIs` green — `evidence/green-cases.log` for the gate group; the
      driver cases are in the full set the gate runs.
- [x] 1.4 `tools/loop-mutations.sh`: `63.2` makes the bound's guard never fire and is noticed by
      `gate#aStepIsBoundedAndTheOverrunNamesIt`; `63.1` and `46.1` are realigned to the launch line and the follower's
      line that `run_step` now carries, with their invariants unchanged. Verify: `bash tools/loop-mutations.sh --check`
      (173 records apply) and `bash tools/loop-mutations.sh --only '46.1 63.1 63.2'` →
      `red-when-mutated: 3 · not-noticed: 0 · restore-failures: 0` — `evidence/loop-mutations-check.log`,
      `evidence/loop-mutations-311.log`.
- [x] 2.1 `guidelines/Backlog.md`, section "The loop": a paragraph for the step bound beside the child's cap, the case bound and the
      gate's stream, with the default, the measurement and what a bound-ended step records; the stale `tee`d clause in
      the paragraph beside it corrected to the file the suite writes (issue #315). Verify:
      `bash tools/check-doc-routes.sh` (154 section references, 4 skill references resolve) and
      `bash tools/gen-feature-list.sh --check-classification`. No build is owed for a prose file.
- [x] 2.2 The spec delta `specs/agent-issue-loop/spec.md`: the added requirement with its two scenarios, each naming
      exactly one case. Verify: `openspec validate fix-gate-step-bound --strict` and the body's length (444 characters,
      the limit is 500) — `evidence/validate.log`.
- [x] 3.1 Structural checks for a delta that touches no compiled file: `check-portability` and its suite, the loop's
      own case set, the issue tooling, `check-doc-routes` and its suite, `check-openspec-hygiene` and its suite,
      `check-todo-references`, `check-compose-sandbox-rule`, `gen-feature-list.sh --check-classification`, the
      mutation lists and `mutate-selftest` — all of them are steps of `./tools/loop/gate.sh` for this pathspec, so the
      gate's own line is their evidence.
- [x] 3.2 Gate: `./tools/loop/gate.sh` once, from the change root. Result quoted in `evidence/gate.log`; its tallies
      copied from the profile's tally directory into `evidence/`.
- [x] 3.3 The change's own evidence pointers: `bash tools/loop/evidence-check.sh openspec/changes/fix-gate-step-bound
      --root .` PASS — `evidence/evidence-check.log`.
- [x] 3.4 Archive the change and confirm the delta merged into `openspec/specs/agent-issue-loop/spec.md`.
