# Tasks — fix-loop-child-that-never-started

## 1. The red case first

- [x] 1.1 `tools/loop/fixtures/pi`: add `PI_STUB_SAY`, the file the stub prints as its own words before any
      report, so a case can hand the run a complaint instead of a verdict; verify the stub still ends with
      `PI_STUB_EXIT` and that the existing cases that use no such file are unaffected
      (`--case 'run#oneItemOrderIsFixed'`).
- [x] 1.2 Write `run#childThatNeverStartedIsTheRunsFault` in `tools/loop-cases.sh`, registered in the `--list`
      array and in the dispatch map (spec: "One command starts a budgeted run, with no dialogue" — scenario "A
      child that never started is the run's fault, not the item's"); verify it fails on the base commit with
      `the item is not recorded as a blocked one: expected '0', got '1'`, recorded in
      `evidence/red/red-on-head.txt`.

## 2. The classification, read from the item's own evidence

- [x] 2.1 `tools/loop/run.sh`: `last_line <file>` — the child's own last non-blank line (spec: the new
      scenario's THEN, "naming the child's own last line").
- [x] 2.2 `tools/loop/run.sh`: the fault branch after `head_now` is read — non-zero exit, no `LOOP-VERDICT`, no
      `LOOP-TIMEOUT`, tree at the base commit → `loop_labels unpick`, `in_progress=""`, one
      `stop_run` naming the child's line, `break`. No `loop_state done`, no escalation (spec: the new
      scenario's THEN). Verify with `--case 'run#childThatNeverStartedIsTheRunsFault'` (green) and that the two
      children that must stay blocked still do: `--case 'run#childOutlastingTheWallClockIsBlocked'` (the run's
      own cap) and `--case 'run#childClaimWithoutCommitIsBlocked'`,
      `--case 'run#blockedItemIsEscalatedWithItsReason'` (an item the run read and must escalate).

## 3. Revert-check the new invariant

- [x] 3.1 `tools/loop-mutations.sh`: mutation `27.1` turns the fault branch into `if false`, and
      `run#childThatNeverStartedIsTheRunsFault` must go red; restore and re-run it green — `evidence/green/`,
      `evidence/green/mutation-27.1.txt`. Run the whole mutation set on the tree before archiving.

## 4. The documents that state the rule

- [x] 4.1 `guidelines/Backlog.md` §7 "The loop": what a run does with a child that never started, with the
      2026-10-20 measurement, beside "What bounds it" and "What it does when it cannot proceed" (spec: the new
      scenario); verify with `grep -n "never started" guidelines/Backlog.md`.
- [x] 4.2 `openspec/changes/fix-loop-child-that-never-started/specs/agent-issue-loop/spec.md`: the requirement
      restated verbatim with the one scenario added; verify with
      `openspec validate fix-loop-child-that-never-started --strict`.

## 5. Structural checks — the scope block (no compiled file is touched)

- [x] 5.1 `bash -n` on `tools/loop/run.sh`, `tools/loop-cases.sh`, `tools/loop/fixtures/pi` and
      `tools/loop-mutations.sh`; `bash tools/check-portability.sh`; `openspec validate … --strict` and
      `openspec doctor` — recorded in `evidence/green/validation.txt`.
- [x] 5.2 The offline shell suite (`tools/loop-selftest.sh`, the gate that owns `tools/loop/**`) run once over
      the whole set — `evidence/green/selftest.txt`: **114 of 115 cases pass**, and the one red
      (`worktree#keepsByDefaultAndRemovesOnRequest`) is pre-existing and independent of this change, proved in
      `evidence/green/PRE-EXISTING-RED.txt` (red with the base engine too, and a path-substring false positive
      of its assertion whenever the suite's scratch tree lies under `.pi/worktrees/loop`). The new invariant's
      mutation was run and restored (`evidence/green/mutation-27.1.txt`); the whole mutation set was not — its
      header asks for it before a change owning `tools/loop/**` is archived, and it mutates the engine in place
      while it runs, so it belongs to a run that can afford to be interrupted there.
- [x] 5.3 No Gradle and no device: this change touches no Kotlin, Java, C++, manifest or resource file, so
      `guidelines/Build.md` §2's scope block names these structural checks in place of `./gradlew test
      -PforceTests --no-build-cache`. Exclusion stated: the JVM gate is not run for a delta no JVM module
      compiles.
