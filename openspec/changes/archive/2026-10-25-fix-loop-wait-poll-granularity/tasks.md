# Tasks — fix-loop-wait-poll-granularity

## 1. The red case

- [x] 1.1 Add `run#theWaitEndsWhenTheChildDoes` to `tools/loop-cases.sh` — registered in `LOOP_SHELL_CASES`, in
      the dispatch and as `case_run_theWaitEndsWhenTheChildDoes`, with the file declaration
      `tools/loop/fixtures/scenarios/ tools/loop/run.sh tools/pi-child-events.sh tools/tooling.json`. It drives one
      item whose worker answers in 150 ms, stamps every console line as it arrives (`date +%s%N`), and fails when
      the run's own `started` and `finished` lines are 900 ms or more apart.
      **Verify**: `bash tools/loop-selftest.sh --case 'run#theWaitEndsWhenTheChildDoes'` on base `cba31292` →
      `FAIL`, `the run took 2008 ms to notice a worker that answered in 150 ms`,
      `loop-selftest: 1 of 1 cases failed`, status 1 (`.pi/issue-loop/items/259/red-on-head-wait.log`,
      2026-10-25T01:27:49Z). The suite's own summary line is the one the gate parses into the red tally;
      a filtered run writes no XML, so that pair is **not retained** as evidence — the retained red artifact is
      `evidence/red-on-head-wait.log` and the gate's own tally in `evidence/gate/`.
- [x] 1.2 Add `run#theProgressLineKeepsItsInterval` in the same places: a 4 s child watched with
      `LOOP_PROGRESS_SECONDS=2` must print one to three `running` lines. It is green on the base — it is the guard
      the granularity change could otherwise break, and it goes red under mutation `60.2`.
      **Verify**: `bash tools/loop-selftest.sh --case 'run#theProgressLineKeepsItsInterval'` on the base →
      `PASS` (`2 running line(s) for a 4 s child at a 2 s interval`).

## 2. The fix

- [x] 2.1 `tools/loop/run.sh` — `run_child` gains `LOOP_POLL_SECONDS` (default `0.05`) with the validation the two
      bounds beside it have, and both sleeps use it (`run.sh:864-868`, `run.sh:894`, `run.sh:956`).
      **Verify**: `bash -n tools/loop/run.sh`, and `run#theWaitEndsWhenTheChildDoes` → `PASS`,
      `the wait for a 150 ms child cost 261 ms`.
- [x] 2.2 `tools/loop/run.sh` — the heartbeat reports elapsed seconds (`SECONDS` against `hb_base`) instead of
      counting `ticks`, and `fmt_elapsed` receives that number (`run.sh:890-916`).
      **Verify**: `run#theProgressLineKeepsItsInterval` → `PASS` (2 lines), and `run#childReportsWhileItWaits`
      (a `LOOP_PROGRESS_SECONDS=1` cadence assertion) → `PASS`.
- [x] 2.3 `tools/loop/run.sh` — the cap and the deadline stay whole-second clocks, read once per second behind a
      one-line guard, so the indentation the records `23.1` and `23.2` quote is unchanged (`run.sh:924-957`).
      **Verify**: `bash tools/loop-mutations.sh --check --from-git cba31292` → `all 65 cases passed — every
      selected record applies to the file it names`.

## 3. The falsification and the records

- [x] 3.1 `tools/loop-mutations.sh` — records `60.1` (the poll's whole second put back) and `60.2` (the elapsed-
      second test dropped) with their own note block.
      **Verify**: `bash tools/loop-mutations.sh --only '60.1 60.2'` → `RED 60.1 run#theWaitEndsWhenTheChildDoes`,
      `restore 60.1 … green again`, `RED 60.2 run#theProgressLineKeepsItsInterval`, `restore 60.2 … green again`,
      `red-when-mutated: 2 · not-noticed: 0 · restore-failures: 0`, exit 0
      (`.pi/issue-loop/items/259/mutations-60.log`).

## 4. The guideline

- [x] 4.1 `guidelines/Backlog.md`, section "7. The loop": the wait's granularity named beside the bound on one child (the
      two measurements), and the deadline's read corrected from "on every tick" to "once a second".
      **Verify**: `bash tools/check-doc-routes.sh` and `bash tools/check-todo-references.sh` pass, and the section
      still routes from `AGENTS.md`'s Documentation Map row for the loop.

## 5. Verification

- [x] 5.1 Re-run the cases the heartbeat and the two clocks reach: `run#childReportsWhileItWaits`,
      `run#childOutlastingTheWallClockIsBlocked`, `run#childIsEndedAtTheRunsDeadline`,
      `run#detachedProcessInTheWorktreeIsEndedByTheCap`, `run#childWriteOutsideTheWorktreeIsNoted`,
      `run#aWriterTheRunCanPlaceBlocksTheItem`, `run#aPeerWriteWhileAChildStandsDoesNotDecideTheItem`, and
      `driver#everyCaseDeclaresTheFilesItCanReach` (the new declarations).
      **Verify**: every one `PASS` (`evidence/affected-cases-1.log`, `evidence/affected-cases-2.log`,
      `evidence/green-after-fix-wait-cases.log`).
- [x] 5.2 Run the project's gate once, from the change root: `./tools/loop/gate.sh`, and copy every tally it writes
      into the change's `evidence/` and into `.pi/issue-loop/items/259/`.
      **Verify**: `LOOP-GATE: tests=411 failures=0 errors=0 (tools/loop-selftest.sh --from-git cba31292 273s, …)`,
      exit 0, end 2026-10-25T06:35Z (`evidence/gate.log`), every tally `failures="0" errors="0"`
      (`evidence/gate/`). The same suite cost 974 s before this change: the poll was 3.6× of the wall clock.
- [x] 5.3 Check the change's pointers mechanically: `bash .pi/skills/fix-loop/scripts/evidence-check.sh
      openspec/changes/fix-loop-wait-poll-granularity --root .` — no dangling `file:line`, no dangling XML
      pointer, no dangling `§N`.
      **Verify**: exit 0.
- [x] 5.4 `openspec validate fix-loop-wait-poll-granularity --strict`, then archive and confirm the delta merged
      into `openspec/specs/agent-issue-loop/spec.md`.
      **Verify**: `openspec validate … --strict` → `Change 'fix-loop-wait-poll-granularity' is valid`;
      `openspec archive … --yes` → archived as `2026-10-25-fix-loop-wait-poll-granularity`, `+1 added`;
      `openspec/specs/agent-issue-loop/spec.md:2168` carries
      `### Requirement: A wait ends when the child does, not on the next whole second`.

## 6. The case the finer poll exposes

- [x] 6.1 `tools/loop-cases.sh` — `labels#trapRemovesInProgressOnSignal` held neither side of its fifo: the stub
      opens the fifo for reading only after it has written `$block.ready`, the case wrote its release line with
      `printf '\n' > "$block"` 0.2 s after `kill -TERM`, and the run's trap used to fire only when the whole
      second of the poll had elapsed — so the child was still there to read the line. With the poll at 0.05 s the
      run ends the child first (`on_exit` → `loop_end_group`) and the case's open waits for a reader that is gone.
      The case now holds the fifo read-write for its whole life (`exec 8<> "$block"`, an open that never needs a
      partner) and writes its release line through that descriptor (`printf '\n' >&8`), so releasing a child never
      blocks whether the child is still there or has already been ended.
      **Verify**: `bash tools/loop-selftest.sh --case 'labels#trapRemovesInProgressOnSignal'` → `PASS` three times
      in a row, ~7 s each (`evidence/case-labels-fixed-1.log` … `-3.log`), where the un-repaired case had to be
      killed after 56 min (`evidence/gate-hang-20261025.log`).
- [x] 6.2 Measured before the repair: on the base `cba31292` (this change's four `run.sh`/case edits stashed) the
      case passes in 7 s; only with the finer poll does it hang — so the case's race, not the engine's wait, is
      what the change had to repair. The orphaned heartbeat subshell the signal path leaves behind (the run exits
      without touching `$stop`, and only the suite's own sweep of the case tree ends it) is a separate, pre-existing
      defect and is named for its own item rather than fixed here.
      **Verify**: `evidence/case-labels-base-probe.log` — base `PASS`, `exit=0 after 7s`.
