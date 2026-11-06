# Tasks

Reference: `proposal.md` for scope and the red run, `design.md` for why, `specs/agent-issue-loop/spec.md` for the
contract.

## 1. The red case

Requirement: "A step that refuses a delta in seconds is owed before the suites", scenario "A delta that writes a
declaration owes its reader first".

- [x] 1.1 Add `gate#theDeclarationCaseIsOwedFirst` (`tools/loop-cases.sh:10816`): one throwaway delta writes a case and
      touches a compiled file, the gate is asked what that delta owes (`--plan`), and the case asserts the declaration
      case is the plan's first line, before `tools/loop-selftest.sh --from-git <base>` and before the profile's
      compiled command — and that the worker prompt sends the child to the plan's first line and names no case of this
      project, because the prompt is an engine template. Verify: on `560edf23` the case fails with "a delta that writes a
      case owes the case that reads the declaration" (`evidence/red-on-HEAD.log`).
- [x] 1.2 Declare it: `# files:` names every path the body reaches, which the driver case found on the first run — the
      body names `tools/loop-cases.sh`, and `new_project` reaches `tools/pi-child-events.sh` and `tools/tooling.json`.
      Verify: `driver#everyCaseDeclaresTheFilesItCanReach` passes (2026-11-06T08:45:20Z).

## 2. The engine

- [x] 2.1 `tools/loop/gate.sh`: extract the pair loop that fills `owed` as `owe_pairs` and read the profile's new
      `LOOP_GATE_FIRST_STEPS` through it before the compiled step; name the key in the engine's own header list
      (`:16`) and widen the `--help` range with it (`:41`). Verify: `bash -n tools/loop/gate.sh`, and
      `gate#theDeclarationCaseIsOwedFirst` passes (2026-11-06T08:45:18Z).
- [x] 2.2 `tools/loop/loop.conf`: record `LOOP_GATE_FIRST_STEPS` with the one pair, its measurement and the reason
      (`:535`). Verify: `tools/loop/gate.sh --plan` names the case for a delta that touches `tools/loop-cases.sh`.
- [x] 2.3 `tools/loop/prompts/worker.md` Phase D: the child is sent to the plan's first line and runs it before
      anything else when its diff writes or narrows a declaration (`:215`), and the template names no case, path or
      command of this project. Verify: the case's second half asserts the prompt's pointer to the plan's first line and
      that it names no case of this project.
- [x] 2.4 `tools/loop-mutations.sh`: record `84.1`, which removes the first-step call, so the new rule has a mutation
      like every other invariant. Verify: `bash tools/loop-mutations.sh --only 84.1` — `RED` then "restore … green
      again" (2026-11-06T08:12:37Z, `evidence/mutation-84.1.log`), and `--check` reads all 246 records as applying.
- [x] 2.5 `guidelines/Backlog.recipes.md`, the section "The gate and its suite": the paragraph the declaration is
      described in now states where the case is owed and what it cost when it was not.

## 3. Verification

- [x] 3.1 The red case is green on the change: `PASS gate#theDeclarationCaseIsOwedFirst`, 2026-11-06T08:45:18Z
      (`evidence/green-on-change.log`), against `1 of 1 cases failed` on `560edf23` (`evidence/red-on-HEAD.log`).
- [x] 3.2 The cases the rule is read beside still pass: all 24 `gate#*` cases in one suite (2026-11-06T08:45:18Z), the
      driver case, `tools/check-portability.sh` (`OK — 56 engines and 7 templates name no project value`) and
      `bash tools/loop-mutations.sh --check` (246 records, 2026-11-06T08:45:48Z, `evidence/focused-cases.log`). The gate
      of this change is the loop's whole self-test, run once by the child that follows the diff review.
