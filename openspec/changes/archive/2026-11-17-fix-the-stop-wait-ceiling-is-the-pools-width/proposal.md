# Proposal: fix-the-stop-wait-ceiling-is-the-pools-width

## Why

A self-test case's wait for a run it signalled is bounded by a figure the host's load can stretch, so a busy host still
reads as a broken trap (issue #949).

- **Observed** ✗: `run#aHupEndedRunRecordsItsStopAndUnpicksItsItem` ended at its own wait while the pool was busy — the
  case printed `a run that took HUP did not end inside 1020 ticks derived from 10286 ms of staging (status 124) — its
  stop was '' and its item ''` in the `--from-git 5330313b` leg of the run's base-red, with four gate steps in flight
  (that leg, `./gradlew test`, `loop-mutations --check`, `mutations-selftest`). The same case passes alone on the same
  tree (`tools/loop/tests/loop-selftest.sh --case run#aHupEndedRunRecordsItsStopAndUnpicksItsItem` → `PASS`), and its
  sibling `run#aHupTheRunNeverTookIsAnsweredAtTheCasesOwnClock` passed in this run having failed in the one before it.
- **Root cause** ⊕: `stop_wait_deadline` (`tools/loop/tests/loop-cases.sh:1374`) multiplied the staging the case had
  measured — 10 286 ms → 1 020 ticks — under the 120 s cap. That staging is a duration *taken inside the loaded pool*,
  so the multiplier scales a figure the load has already stretched instead of compensating for the stretch: the host
  stretched 10.3 s of staging, could stretch the run's end by any amount, and nothing in the figure said by how much.
  Evidence: `.pi/issue-loop/base-red.log:1029` (the case's own line) and `:1037` (its `FAIL`), the pool line
  `gate-pool: 4 steps start together — at most 4 in flight`, and `LOOP-BASERED: tests=1550 failures=1 errors=0` naming
  only this case. The red-on-HEAD reading recorded for this change, with the case's new ceiling assertions run against
  HEAD's helper, is `a pool of four allows 30 s per slot, in the ticks the wait sleeps in: expected '1200', got '300'`.
- **Consequence** ⊨: the wait guards every HUP assertion, so a red HUP case is read as "the trap is broken" whenever the
  host is merely busy; a base-red that subtracts this step is the whole of today's cost.
- **The one fix** ➜: derive the ceiling from a number the load cannot stretch — **how many cases the suite starts beside
  the one that waits** — and keep the wait ending on the facts. The pool's width is the same honest multiplier the
  suite's own case bound already uses (`LOOP_CASE_MAX_SECONDS` times the pool,
  `tools/loop/tests/loop-selftest.sh:196-198`); `stop_wait_deadline` therefore allows 30 s per pool slot, read off
  `LOOP_SELFTEST_WORKERS`, and the multiplier of a measured duration and the 120 s cap are both gone.
- **Requirement contradicted**: `openspec/specs/agent-issue-loop/spec.md`, "A self-test case is held to a bound of its
  own" — "That clock SHALL be derived from the staging the same case measured … floored at the figure a quiet host
  needs and capped".

## What Changes

1. **`stop_wait_deadline` derives the ceiling from the pool's width** (`tools/loop/tests/loop-cases.sh`): it takes no
   argument, reads `LOOP_SELFTEST_WORKERS` (default 1, `0`/non-numeric → 1) and answers
   `STOP_WAIT_TICK_FLOOR × workers` ticks. `STOP_WAIT_STAGE_FACTOR` and `STOP_WAIT_TICK_CEILING` are removed with the
   multiplication they priced; `STOP_WAIT_END_TICKS`, `STOP_WAIT_STATE_EVERY` and the whole state-driven end of
   `wait_for_run_stop` are unchanged, and the helper's default tick count is now its own derived ceiling.
2. **The suite exports its pool's width** (`tools/loop/tests/loop-selftest.sh`): `LOOP_SELFTEST_WORKERS="$workers"`,
   beside the case bound it already multiplies by the same figure, so a case reads the number the suite sized its own
   bound with instead of measuring a duration.
3. **The HUP case stops measuring its staging** (`case_run_aHupEndedRunRecordsItsStopAndUnpicksItsItem`): the
   `stage_started`/`stage_ms` pair and its derivation are gone, the deadline is `stop_wait_deadline`, and the failure
   text names the pool the ceiling was derived for rather than a staging figure.
4. **The sibling case ends its run under the same clock** (`case_run_aHupTheRunNeverTookIsAnsweredAtTheCasesOwnClock`):
   its cleanup sent `TERM` through `loop_end_pid`, whose `TERM`→`KILL` grace is 2 s, and a host carrying a sixteen-case
   pool spends longer than that ending the child and writing the item — measured 2026-11-17, **14 of 16** concurrent runs
   of the case failed on `the run recorded the item its signal interrupted: expected 'blocked', got ''`. The case now
   sends the signal itself and waits for the state the run writes under `stop_wait_deadline`, keeping the bounded kill
   as the last resort: 16 of 16 pass under the same load. This is the reading the issue names — "its sibling passed in
   this run having failed in the one before it".
5. **The guard case holds the new ceiling** (`run#theStopWaitIsDecidedByTheStateTheRunWrites`): a pool of one (and one
   reported as `0`) keeps 30 s, a pool of four allows 120 s, a pool of sixteen 480 s — the figure #928's cap made
   impossible and the one the measured red needed — and each ceiling must stay inside the case's own bound.
6. **`loop-mutations.sh` record `928.1` moves with the invariant** it removes: it now drops the pool's width
   (`STOP_WAIT_TICK_FLOOR * workers` → `STOP_WAIT_TICK_FLOOR`), so a case in a wide pool waits the quiet figure again
   and the guard case reds on the same assertion it reds on today. `928.2` (the trap removed) is unchanged and still
   reds the HUP case.
7. **`guidelines/Backlog.recipes.md` §7b carries the rule**: what #928 derived, the 2026-11-17 measurement that
   overruled it, the pool's width as the ceiling, why no cap is needed (the wait is a thirtieth of the slot the
   suite gives the case), and the second site where the same defect red a case.

Not changed: the state-driven end of the wait, the 5 s tail grace, the 3-tick state read, `CASE_WAIT_TICKS` for the
`wait_for_pid_end` call sites that name no record, `loop_end_pid`'s own grace (`tools/loop/lib.sh` — every case that
kills a leftover uses it, and this change answers the load in the case that was red, not by widening a bound every
caller pays), and every HUP assertion.

## Impact

- Files: `tools/loop/tests/loop-cases.sh`, `tools/loop/tests/loop-selftest.sh`,
  `tools/loop/tests/loop-mutations.sh`, `guidelines/Backlog.recipes.md`, and this change's specs.
- Modules: none — no Kotlin, Java, C++ or Gradle file is touched, so no build and no Android suite is owed
  (`guidelines/Build.md` §2, the scope block); the structural check of the edited shell files stands in.
- Spec changed: `agent-issue-loop`, requirement "A self-test case is held to a bound of its own" (MODIFIED; the same
  requirement #928 modified).
- Additive, not breaking: no caller loses an ability. The one signature change, `stop_wait_deadline <staging ms>` →
  `stop_wait_deadline`, has three call sites in the tree and all three are updated here; the helper's third-argument
  default now answers the derived ceiling where it answered `CASE_WAIT_TICKS`.
- Rollback: revert the four files. A case then waits ten times its own staging again (capped at 120 s) and the HUP case
  is red again whenever the pool is busy — the defect this change removes.
