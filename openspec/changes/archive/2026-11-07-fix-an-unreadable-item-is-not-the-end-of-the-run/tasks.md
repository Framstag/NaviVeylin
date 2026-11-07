# Tasks

Reference: `proposal.md` for the scope and the red run, `design.md` for the mechanism and the alternatives the
evidence eliminated, and `specs/agent-issue-loop/spec.md` for the contract.

## 1. The red case

- [x] 1.1 `tools/loop-cases.sh`: add `case_run_anUnreadableItemIsNotTheEndOfTheRun`, registered in `LOOP_SHELL_CASES`
      and in the dispatch table, driving `run_loop --items 1 --minutes 90 --review off` over the two-item scenario with
      the stub `gh` refusing the run's own read (`GH_STUB_READ_FAIL="author,state,comments"` — the one read that asks
      for the comments, so the pre-screen that built the queue is answered) and asserting the record the run leaves:
      the item recorded, `blocked` / `unreadable`, the CLI's own words in the note and on the console, and the run
      ending on `item cap reached (1/1)` rather than on that read. Its first line declares
      `tools/loop/fixtures/scenarios/`, `tools/loop/run.sh`, `tools/loop/state.sh`, `tools/loop/lib.sh`,
      `tools/pi-child-events.sh` and `tools/tooling.json`.
      Verify: with the arm reverted to the base's stop, the case fails at its first assertion —
      `expected '21', got 'none'`, `1 of 1 cases failed`, 2026-11-07T22:07:39Z–22:07:43Z (`evidence/red-on-HEAD.log`;
      tally `evidence/red-on-HEAD.xml`, `tests=1 failures=1 errors=0`).
- [x] 1.2 The first check the delta owes, run before the rest: `bash tools/loop-selftest.sh --case
      driver#everyCaseDeclaresTheFilesItCanReach` — `PASS` (`evidence/first-check.log`, one line, no timestamp), the
      case 1.1's declaration owes.

## 2. The arm

- [x] 2.1 `tools/loop/run.sh:1890–1899`: the per-item read keeps its call and replaces the `stop_run`/`break` with
      `loop_state done "$issue" --result blocked --verdict unreadable` — the CLI's own words in the note and on the
      console — and a `continue`, so the queue moves to the next candidate and the refusal streak `state.sh next` reads
      is what ends a run whose tracker answers nothing.
      Verify: `bash -n tools/loop/run.sh` and `bash -n tools/loop-cases.sh` (`syntax ok`,
      `evidence/syntax-ok.log`), and the case `PASS` (`evidence/green-on-change.log`).
- [x] 2.2 `tools/loop-mutations.sh`: one record, `100.1`, restores the base's stop *and* its `break`, so 1.1 goes red at
      its first assertion with the message the log carries. Verify: `bash tools/loop-mutations.sh --only 100.1` —
      `RED 100.1 run#anUnreadableItemIsNotTheEndOfTheRun` with the failing assertion on the line under it,
      `restore 100.1 … green again`, `red-when-mutated: 1 · not-noticed: 0 · restore-failures: 0`
      (`evidence/mutation-100.1.log`); and the gate's own `bash tools/loop-mutations.sh --check` step — every selected
      record applies to the file it names (`evidence/gate-tallies/bash_tools_loop-mutations.sh_--check.xml`).

## 3. The documents

- [x] 3.1 `specs/agent-issue-loop/spec.md`: one requirement added — "An item the tracker does not answer for is
      unknown, not the end of the run" — with the scenario "An unreadable item is recorded and the queue moves on",
      whose case is 1.1. Verify: `./tools/loop/measure.sh --change fix-an-unreadable-item-is-not-the-end-of-the-run`
      reports `LOOP-MEASURE-CHANGE … scenarios=1 cases=1 unnamed=0` and `LOOP-MEASURE-SUITE cases=370`
      (`evidence/measure-round2.log`).
- [x] 3.2 `guidelines/Backlog.recipes.md` section 7d: one paragraph — what the run does with an item the tracker does not
      answer for, why the read is the seam's own, and that the refusal streak is what ends a run when every read fails.
      Verify: `tools/check-doc-routes.sh` — `all 329 section references resolve, all 7 skill references resolve`, and
      `tools/check-openspec-hygiene.sh` — `OpenSpec artifacts are consistent: 8 open change(s) carry task markers`
      (`evidence/doc-routes.log`, `evidence/hygiene.log`).

## 4. Verification

- [x] 4.1 The loop suite for the delta, in one call:
      `bash tools/loop-selftest.sh --from-git fc9e0eb012dcfc781bf744d128acfac1cd9138f8` — `all 370 cases passed (JDK
      27, offline, no token, no sandbox) — 377.80 s` (`evidence/green-on-change.log`). That log is the suite over the
      tree as it stood when it was taken; the run that covers the **final** tree is this item's gate, whose
      `tools/loop-selftest.sh --from-git …` step is quoted in the gate line of this round's report
      (`evidence/gate-tallies/tools_loop-selftest.sh_--from-git_*.xml`). The delta reaches
      `tools/loop/lib.sh` and `tools/loop-cases.sh` through the driver's core-file rule, so the selection is the whole
      set rather than the arm's own cases.
- [x] 4.2 The change's own numbers, taken once with the pathspec fixed:
      `./tools/loop/measure.sh --change fix-an-unreadable-item-is-not-the-end-of-the-run`
      (`evidence/measure-round2.log`) — the paths it touches, the checks those paths owe, `cases=370`, the change's
      `scenarios=1 cases=1 unnamed=0`, and the tallies the change's evidence carries.
- [x] 4.3 The gate is run here, once: `./tools/loop/gate.sh`. The round that refused this change came *after* a gate,
      so there is nothing left to read before one, and the gate is this session's (`LOOP-GATE` in this round's report).
