# Tasks — a suite that ends normally leaves no process of its cases behind

## 1. The case that proves the normal exit path

- [x] 1.1 `tools/loop-cases.sh`: the case `clean#suiteThatEndsNormallyEndsTheProcessItsCaseLeft` — a nested suite
      whose case starts a process in a process group of its own at a working directory inside the suite's own
      scratch root and returns while it polls; the suite is run to its normal end (no signal) and the process
      must be gone. Registered in the case list and the dispatcher, declared for `tools/issues/`,
      `tools/loop-cases.sh`, `tools/loop-selftest.sh` and `tools/loop/`.
      Verify: `bash tools/loop-selftest.sh --case clean#suiteThatEndsNormallyEndsTheProcessItsCaseLeft` → `PASS`
      (`evidence/green-case.log`).
      Parent: `agent-issue-loop` — "A suite ended by a signal ends the case it was in, and its processes",
      scenario *A suite that ends normally ends the process its case left*.
- [x] 1.2 `tools/loop-mutations.sh`: record `53.11` — drop the sweep line (`loop_end_pids_under "$tmp"`) in
      `../loop-selftest.sh`, naming the new case as the one that must notice.
      Verify: `bash tools/loop-mutations.sh --check --only 53.11` → the record applies
      (`evidence/mutation-check-53.11.log`); `bash tools/loop-mutations.sh --only 53.11` → `RED` for the named
      case, restore green (`evidence/revert-check-53.11.log`).
      Parent: the same scenario — the record is what makes the sweep's presence an asserted invariant.

## 2. The requirement

- [x] 2.1 The `agent-issue-loop` delta: one `MODIFIED` requirement whose body states the invariant for every exit
      path (489 characters, under the validator's 500), the existing scenario repeated verbatim, one scenario
      added for the normal exit naming the new case.
      Verify: `openspec validate fix-loop-suite-normal-exit-leftover --strict`
      (`evidence/openspec-validate.log`).
      Parent: `agent-issue-loop` — the modified requirement itself.

## 3. The suites

- [x] 3.1 The new case, alone, on the change's tree.
      Verify: `evidence/green-case.log`.
- [x] 3.2 The neighbours it must not break: `clean#interruptedSuiteEndsItsProcessGroup`,
      `clean#endsARunWhoseTreeIsAlreadyDeleted`, `clean#interruptedSuiteRemovesItsScratchTree`,
      `driver#everyCaseDeclaresTheFilesItCanReach` (the last one is what reads the new declaration).
      Verify: `evidence/green-neighbours.log`, all four green.
- [x] 3.3 Revert-check: `bash tools/loop-mutations.sh --only 53.11` — the named case red, restored green,
      byte-identical restore (`git diff --stat` names only the two intended files).
      Verify: `evidence/revert-check-53.11.log`.
- [x] 3.4 The whole engine suite, owed for a change that owns `tools/loop/**`:
      `bash tools/loop-selftest.sh --from-git 49dfd7db` (210 of 210 cases; `tools/loop-cases.sh` is a core file,
      so `--from-git` selects everything). **It is not green and is not claimed green**: 3 of 210 fail —
      `prompt#workerNamesEveryPhaseAndSkill`, `prompt#workerCarriesTheFeaturePath` and
      `run#aWriterTheRunCanPlaceBlocksTheItem`. Verify: `evidence/full-suite.log`, its summary line quoted, and
      `evidence/attribution.md` for the attribution.
- [x] 3.5 The whole declared record set: `bash tools/loop-mutations.sh --check`.
      Verify: `evidence/mutation-check.log`, its summary line quoted.
- [x] 3.6 The delta's own gate, from the change root: `bash tools/loop/gate.sh --base 49dfd7db`.
      Verify: `evidence/gate.log`, its `LOOP-GATE:` line quoted.
- [x] 3.7 The red set attributed (`guidelines/Build.md` §4d), with the control that makes it attributable:
      the same suite on pristine `main` (49dfd7db, detached worktree, same host and day) reddens
      `run#prescreenAnnouncesItselfBeforeItReturns`, `run#childOutlastingTheWallClockIsBlocked` and
      `run#aWriterTheRunCanPlaceBlocksTheItem` — a *different* set, one case common, and that one is open item
      #288's documented red. The two `prompt#` cases pass 11 of 11 alone.
      Verify: `evidence/baseline-main-full-suite.log`, `evidence/flake-isolation.log`,
      `evidence/attribution.md`, `evidence/concurrent-suites.log`.
- [x] 3.8 The residual filed rather than argued away: **#295** — two suites in one repository redden a moving set
      of cases.
      Verify: `https://github.com/Framstag/NaviVeylin/issues/295`.

## 4. The record

- [x] 4.1 `bash .pi/skills/fix-loop/scripts/evidence-check.sh openspec/changes/fix-loop-suite-normal-exit-leftover --root .` — every pointer resolves.
      Verify: `evidence/evidence-check.log`.
- [x] 4.2 Archive the change and confirm the delta merged into `openspec/specs/agent-issue-loop/spec.md`.
      Verify: archived as `2026-10-24-fix-loop-suite-normal-exit-leftover`; the shipped requirement at
      `openspec/specs/agent-issue-loop/spec.md:979` now carries the body and the new scenario, its existing
      scenario byte-identical.
- [x] 4.3 Close #233 naming the archived change and the evidence.
      Verify: `evidence/closure.md` — `closed #233 (completed)`, `status/fixed-by`, `state=CLOSED
      reason=COMPLETED`; the landed commit is `f947886a` on `main`.
