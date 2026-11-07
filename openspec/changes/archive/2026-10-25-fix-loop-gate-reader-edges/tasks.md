# Tasks — the gate's suite map closes the reader path side too

## 1. The test that reproduces the defect

- [x] 1.1 The failing case, on the base, recorded before any map edit:
      `bash tools/loop-selftest.sh --case gate#aPathACheckReadsOwesThatStepsCommandSet` from a linked working
      tree at base `85d0294336a3a5c10c60275510be1288841d82fe` → `1 of 1 cases failed`, the first assertion
      `'tools/check-portability.sh' not found in: …` (`evidence/red-on-HEAD.log`, exit 1). The case is the
      test; the defect is that the plan owes no command of the step whose tool reads the path.
      Verify: `evidence/red-on-HEAD.log`.
      Parent: `agent-issue-loop` — "The gate's suite map covers every script command the pipeline's check
      steps run", scenario *A path a check reads owes that step's commands*.
- [x] 1.2 The precondition's own red, measured on HEAD: running
      `bash tools/check-portability-selftest.sh` from a linked working tree under `.pi/` exits 2 with the
      machine-local-state refusal (`evidence/plan-before.txt` §3, the worktree probe).
      Verify: `evidence/worktree-run.txt`, the HEAD half.
      Parent: `tooling-portability` — "Discovery is exercised where it breaks".

## 2. The fix

- [x] 2.1 `tools/loop/loop.conf`: the seven measured reader edges added to `LOOP_GATE_SUITES`, each mapping to
      the whole command set of the step that reads the path, with the paragraph that records the measured edge
      set and the residual a later step adds.
      Verify: `bash tools/loop-selftest.sh --case gate#aPathACheckReadsOwesThatStepsCommandSet` → `PASS`
      (`evidence/green-case.log`).
      Parent: the same scenario.
- [x] 2.2 `tools/check-portability-selftest.sh`: the `.pi/` precondition asks
      `git -C "$root" rev-parse --show-prefix` instead of the path; a copy nested in someone else's tree is
      refused, a linked working tree is accepted.
      Verify: `bash tools/check-portability-selftest.sh --case preconditions#runsInALinkedWorkingTree` →
      `PASS`; `--case preconditions#refusesUnderMachineLocalState` → `PASS`.
      Parent: `tooling-portability` — *preconditions#runsInALinkedWorkingTree*.
- [x] 2.3 `tools/portability-cases.sh`: the new precondition case, and `foreign#fixtureIsVersioned` asking git
      whether the fixture is tracked rather than testing its path for `.pi/`.
      Verify: `bash tools/check-portability-selftest.sh` → `all 29 cases passed` (`evidence/full-suite.log`).
      Parent: the same scenario.

## 3. The invariants, made permanent

- [x] 3.1 `tools/loop-mutations.sh`: records `54.5` (the loop-config reader edge) and `54.6` (the engine-glob
      reader edge) — each deletes one pair and `gate#aPathACheckReadsOwesThatStepsCommandSet` must go red,
      restore, green again.
      Verify: `bash tools/loop-mutations.sh --only 54.5`, `--only 54.6` → `RED` / `restore … green again`
      (`evidence/revert-check.log`).
      Parent: the same scenario.
- [x] 3.2 `tools/portability-mutations.json`: record `6.6` — the worktree allowance in the precondition,
      whose named case `preconditions#runsInALinkedWorkingTree` must go red.
      Verify: `bash tools/mutate.sh --mutations tools/portability-mutations.json --only 6.6` → `RED(exit 1)` /
      `GREEN(exit 0)` (`evidence/revert-check.log`).
      Parent: `tooling-portability` — *preconditions#runsInALinkedWorkingTree*.

## 4. The rule and the prose

- [x] 4.1 `guidelines/Build.md` §3 states both sides of the map as pinned, names the seven measured reader
      edges, and keeps the two residuals (an inline-shell/Gradle step; a step or reader edge added later).
      Verify: `bash tools/check-doc-routes.sh` (`evidence/doc-gates.log`).
      Parent: both capabilities.
- [x] 4.2 `.pi/skills/portability-check/SKILL.md` and `.pi/skills/mutation-runner/SKILL.md` state the refined
      precondition and the record count kept in step (`15` → `16`).
      Verify: the diff of the two files, and `bash tools/check-doc-routes.sh` (§4.1).

## 5. The suites and the gate

- [x] 5.1 The loop's suite and its mutation set, the gate `gate.sh` maps for `tools/loop/**` — this change
      touches `tools/loop/loop.conf`, `tools/loop-cases.sh` and `tools/loop-mutations.sh`:
      `bash tools/loop-selftest.sh --from-git <base>` and `bash tools/loop-mutations.sh --from-git <base>`.
      Verify: `evidence/green-case.log`, `evidence/revert-check.log`.
      Exclusion: the compiled gate is not owed — no compiled file changes.
- [x] 5.2 The portability step, which the new reader pairs make this change owe:
      `bash tools/check-portability.sh`, `bash tools/check-portability-selftest.sh`,
      `bash tools/mutate-selftest.sh`, `bash tools/mutate.sh --mutations tools/portability-mutations.json --list`.
      Verify: `evidence/full-suite.log`, `evidence/doc-gates.log`, every exit 0.
- [x] 5.3 The command side and the step-is-a-unit rule did not regress:
      `bash tools/loop-selftest.sh --case gate#theMapCoversEveryPipelineStep` and
      `--case gate#aPipelineCheckIsOwedByItsPaths` → `PASS`.
      Verify: `evidence/green-case.log`.

## 6. Spec and archive

- [x] 6.1 `openspec validate --specs` → 177 passed, 0 failed; `openspec doctor` → ok.
      Verify: `evidence/openspec-validate.log`.
- [x] 6.2 Independent read-only review of the diff against every scenario, per `.pi/skills/revert-check` and
      the `agent-issue-loop` archive rule. The reviewer's verdict was *OK with notes* and it found one P1 and
      six P2s, all of which this change resolved in a second round: the `Check issue tooling` reader edge is
      mapped too (its case greps `tools/loop/*.sh` and `tools/sensors/*.sh`, its taxonomy case reads
      `tools/sensors/sensors.json`); the case and its mutation set now cover every measured config and engine
      edge (records `54.5`–`54.10`); the stale comment above the pin case, the count in `Build.md` §3 and the
      precondition's widening (a `.pi/` top that is not this project's checkout, pinned by `6.7`) are fixed;
      the evidence files carry their real exit codes and full outputs.
      Verify: `evidence/review.md`, `evidence/revert-check.log`, the second gate run in `evidence/gate.log`.
- [x] 6.3 Archive the change and close issue #289 naming the archived change.
      Verify: the closure comment on the issue.
