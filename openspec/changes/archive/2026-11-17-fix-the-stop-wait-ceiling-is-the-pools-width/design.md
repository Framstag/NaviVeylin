# Design: fix-the-stop-wait-ceiling-is-the-pools-width

## What was established before this design

The root cause is the one `proposal.md`'s Why records: `stop_wait_deadline` multiplied a duration the loaded host had
already stretched (`10 286 ms → 1 020 ticks`) under a 120 s cap, and the run's stop and item were still empty when that
ceiling arrived (`.pi/issue-loop/base-red.log:1029`). So the question this design answers is not "is the wait wrong"
but "what figure can bound a wait when no duration measured on this host can".

## Decision: the ceiling is 30 s per pool slot

`stop_wait_deadline` answers `STOP_WAIT_TICK_FLOOR × LOOP_SELFTEST_WORKERS` ticks, where `LOOP_SELFTEST_WORKERS` is the
pool's width exported by `tools/loop/tests/loop-selftest.sh` and a case that runs with nothing named gets a pool of one.
The wait itself is unchanged (`wait_for_run_stop`: two facts off the state every third tick, a 5 s tail grace once both
are there, the state read once more at the ceiling).

**Why this figure is the load-independent one.** The load that stretched the run is contention for this host, and the
only thing about that contention the suite *declares* rather than measures is how many cases it starts at once. It is
already the multiplier the suite's own case bound uses, for the same reason (`case_max = LOOP_CASE_MAX_SECONDS ×
workers`, `tools/loop/tests/loop-selftest.sh:196-198`, with the 2026-11-04 measurement in the comment above it — "each
competing case can sit behind its peers for its own wall clock, so the pool's size is the ceiling of that wait"). The
case's wait is now the same statement at a thirtieth of the size, and 30 s is not a new measurement: it is the figure
#928 already priced as what a quiet host needs.

**Why no cap.** A cap is another figure the host cannot answer for, and it is what ran out: at four slots the derivation
allowed 102 s and the run needed more. It is also unnecessary — `STOP_WAIT_TICK_FLOOR × workers` is always a thirtieth
of `LOOP_CASE_MAX_SECONDS × workers`, so the wait can never hold a slot longer than the suite was going to give the
case; the guard case asserts that relation on the configured bound, not on a constant.

## The same defect's second site: the grace a case spends ending the run it started

`case_run_aHupTheRunNeverTookIsAnsweredAtTheCasesOwnClock` proved its point (the run outlives the case's 3 s clock)
and then ended the run through `loop_end_pid`, whose `TERM`→`KILL` grace is 2 s (`tools/loop/lib.sh:676-687`). Under a
sixteen-case pool the run's own end — the child's exit, the stop, the unpick, the item's record, the unlock — spends
longer than that, so the run is killed with nothing written and the case's last assertion reads an empty state.
Measured 2026-11-17 with sixteen concurrent instances of the case: **14 failed** on `the run recorded the item its
signal interrupted: expected 'blocked', got ''`; the same case alone is green, and green in both 4-way runs. The case
therefore sends `TERM` itself and waits for the state the run writes under `stop_wait_deadline`, keeping `loop_end_pid`
and `wait_for_pid_end` as the last resort for a run that ignores the signal: 16 of 16 pass under the same load.

Why not widen `loop_end_pid`'s grace: it is the bound every case that kills a leftover pays for, and a bigger constant
is the same mistake one order of magnitude later (alternative 1 above). The case that was red owns the clock it waits
on.

## Alternatives the evidence eliminated

1. **A bigger fixed ceiling** (300 → 3 000 ticks). Impossible: a fixed number is a statement about the machine, and the
   red was recorded at a figure already ten times the quiet one. Any host slow enough to stretch 30 s stretches 300 s,
   and the case then reports a trap bug that does not exist — the defect, one order of magnitude later.
2. **Keep the staging factor and raise the multiple or the cap.** Impossible: the measured factor was 10× a 10.3 s
   staging and the run's end outlived it. A multiple of a duration measured *inside* the pool scales what the load
   already did instead of compensating for it, and the cap is exactly the figure that ran out at 102 s.
3. **The suite's own per-case bound as the ceiling** (`LOOP_CASE_MAX_SECONDS × pool`, offered by the issue as "or the
   suite's own bound"). Impossible as the *case's* clock: `928.2` — the mutation that removes the HUP trap — would then
   hold a slot for four hours in a sixteen-case pool on every mutation pass, and the case would be ended by the suite's
   watcher instead of naming the state it read, which is the failure #308 and #860 bounded. As a *ceiling on the
   ceiling* it is honoured: the derived figure is a thirtieth of it.
4. **Pin the HUP cases out of the pool** (the issue's alternative). Impossible as the fix: the red was recorded with a
   four-step *gate* pool (`./gradlew test`, `loop-mutations --check`, `mutations-selftest`, the self-test itself) while
   the self-test ran at its own CPU-count width, so a case pinned within the self-test's pool still shares the host —
   and it would still measure its staging on that host, so the stretched figure would come back unchanged. Pinning
   changes who queues, not what the queue costs.

## Risks

- **A wide pool buys a long wait for a genuinely broken trap.** At sixteen slots the HUP case reds after 480 s instead
  of 120 s, and `928.2` pays that once per full mutation pass (`--check` runs no case, so the gate's cheap leg is
  unaffected). Accepted: the alternative is a false red on every busy host, and 480 s is a thirtieth of the slot the
  case was given anyway. Measured 2026-11-17 on this host: `RED 928.2 run#aHupEndedRunRecordsItsStopAndUnpicksItsItem`
  at `4800 ticks`, restored green.
- **A case that starts a suite of its own** inherits `LOOP_SELFTEST_WORKERS` from the suite that started it; the inner
  suite recomputes `workers` with `selftest_workers` and re-exports it, so the inner cases read the inner pool. A
  process a case starts outside a suite keeps the pool of one, which is the figure a quiet host needs.
- **A profile that sets `LOOP_SELFTEST_JOBS` or a `--jobs` flag wider than the host can carry** now widens every
  waiting case's ceiling by the same factor. That is the declared width, and it is the width the suite sized its own
  case bound with.

## Verification

- `run#theStopWaitIsDecidedByTheStateTheRunWrites` — the ceiling at a pool of one, of `0`, of four and of sixteen, and
  that the ceiling stays inside the case's own bound; plus the three readings of the wait (recorded and ended;
  recorded nothing; recorded both and stood) that the state-driven end must keep. Red on HEAD with the new assertions
  and HEAD's helper: `a pool of four allows 30 s per slot … expected '1200', got '300'`.
- `run#aHupTheRunNeverTookIsAnsweredAtTheCasesOwnClock` — the case that proves a run the signal never reached
  outlives the case's own clock, now also under sixteen concurrent instances (14 of 16 red before the ending became
  patient, 16 of 16 green after).
- `run#aHupEndedRunRecordsItsStopAndUnpicksItsItem`, `run#noProcessOfASignalledRunOutlivesIt` — the case the ceiling
  guards, and the trap's child hygiene.
- `tools/loop/tests/loop-mutations.sh --check --only "928.1 928.2"` — both records still apply to the lines they name;
  a full run of the two shows `928.1` red on the guard case and `928.2` red on the HUP case, both restored green.
- No build and no Android suite: the delta touches no compiled file (`guidelines/Build.md` §2, the scope block). The
  structural check is `bash -n` over the three shell files, `openspec validate --strict`, and
  `tools/check-doc-routes.sh`.
