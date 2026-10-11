# Design

## Context

See `proposal.md` — Why for the defect and its evidence, `specs/map-modes/spec.md` for the behavior
contract.

State that shapes the approach:

- `MapCanvasViewModel.refreshBrowseReCenter(state)` (`MapCanvasViewModel.kt:3612`) is a pure-ish
  derivation over one UI-state snapshot plus two private fields (`browseReCenterBeyondSinceMs`,
  `browseReCenterVisible`). Its only caller is `uiState.collect { refreshBrowseReCenter(it) }`
  (`:1334-1336`).
- The clock the rule already uses is the injected `timeSource` (`EngineTimeSource`), and
  `RECENTER_DWELL_MS`, `RECENTER_SHOW_OFFSET_PX`, `RECENTER_HIDE_OFFSET_PX` are `internal const` so the
  rule tests calibrate against the same numbers.
- The file already owns two real-clock tickers on `Dispatchers.Default`
  (`:1243-1251` fix quality with `fixQualityTickMs` / `fixQualityTickCount` seams, `:1318-1327`
  stale speed). Its documented rules: the tick must not read the main dispatcher while nothing changes
  (`TODO.md §121`; spec `gps-fix-quality`), and an endless delay loop must not feed the virtual test
  scheduler (`TODO.md §40.C.16`).
- Fields the ticker needs must be declared **before** `init`: `viewModelScope` is
  `Dispatchers.Main.immediate`, so a loop launched in `init` runs its body during construction
  (`:1122-1132`).
- Test constraints: `Build.md` §6 (teardown of the endless loop; tests await state instead of a
  deadline) and `spec unit-test-suite-runtime`.

## Goals / Non-Goals

**Goals:**

- A dwell that has elapsed reveals the button with no further fix, pan, zoom or other state change.
- One clock for the rule: the injected `timeSource`. No wall-clock wait or real sleep in a test.
- No new loop and no new thread; no behavior change for FREE_DRIVE, NAVIGATION or the car.

**Non-Goals:**

- Not changing the thresholds, the dwell length, the hysteresis or the BROWSE-only scope.
- Not re-deriving on canvas-metric changes alone (the `setScreenSize` gap in `proposal.md` — Impact).
- Not touching the car variant's imperative `RendererGate.reCenter()`.
- Not adding a UI affordance, string or icon.

## Decisions

### D1 — Complete the dwell on a tick, not on state emission

While an appear dwell is armed and the button is not yet visible, the existing `Dispatchers.Default`
tick loop emits a re-evaluation once `timeSource.nowMillis() - armedAt >= RECENTER_DWELL_MS`, and the
derivation consumes that emission exactly as it consumes a state change. The arm is cleared as soon as
the derivation sees the button visible, so a revealed (or discarded) dwell publishes nothing further
and a settled viewport costs no ticks at all — the tick's own guard is `arm != 0`, no second flag.

Alternatives rejected:

- **`delay`-job per arm.** Two clocks: the job runs on the (virtual in tests) scheduler while the rule
  is decided by `timeSource`. Under `advanceUntilIdle()` the job fires with an unmoved injected clock,
  so it must either re-arm (an unbounded reschedule loop that keeps the test scheduler non-idle) or
  give up (intermittently wrong in production when a timer fires a fraction early).
- **Dwell measured in fix timestamps.** Needs fixes to arrive; provider silence is the failing case.
- **Drop the dwell; scale the appear threshold by the reported accuracy.** Removes the noise problem
  statically, but changes the contract (large magnification would need a ~170 px threshold at 5 m
  accuracy, hiding small genuine drift) and owes its own spec delta.

### D2 — The ticker reads a `@Volatile` arm, never `_uiState`

The ticker thread reads only an `@Volatile` field holding the arm timestamp (and the derived `mode`),
never `_uiState`. This keeps the "an unchanged quality costs no main-dispatcher traffic" rule
(`:1230-1242`) intact and avoids a cross-thread read of the state holder. The visibility decision and
the arm reset stay on the collector's (main) dispatcher.

### D3 — Test seams mirroring the fix-quality pair

One new seam: `internal val recenterDwellTickCount`, a `MutableStateFlow` counting published dwell
re-evaluations, mirroring `fixQualityTickCount` (declared before `init`). The new cases drive the
injected `timeSource` past the dwell and pump the scheduler from a real dispatcher, the pattern
`MapCanvasViewModelFixQualityTest.kt:146-160` already uses — awaiting observable state, not a deadline.

There is deliberately **no** second tick-period seam. The dwell rides the one period the shared loop
already has (`fixQualityTickMs`, production 1000 ms, the same value as `RECENTER_DWELL_MS`), so a case
that needs the dwell tick to be quick shortens that one period instead of adding a second cadence for
the same loop to honour.

`recenterDwellTickCount` alone cannot prove a *negative* ("no re-evaluation happened"), and no test may
read a clock (`checkNoWallClockWaits`). The cases therefore wait for `fixQualityTickCount` — the loop's
heartbeat, which counts every tick — to advance by a known number, and only then assert that
`recenterDwellTickCount` did not move. The loop is proven to have run without any timing assumption,
and the heartbeat is incremented **after** the dwell evaluation of the same iteration, so N heartbeats
imply N dwell evaluations rather than N-1 (independent review 2026-10-11, finding P2-3).

One case must observe state *before* a debounced publication could arrive
(`anAgingFixDoesNotLetTheClockRevealTheButton`): it uses `pumpCurrentUntil`, which drains only the
already-runnable tasks (`TestCoroutineScheduler.runCurrent`) and never advances virtual time, because
`advanceUntilIdle` would fire the quality pipeline's 2 s debounce first and close the window under test.

### D4 — The existing `awaitDwellAndRefresh` harness stops implying a second fix is required

The helper (`MapCanvasViewModelBrowseReCenterTest.kt:150`) keeps advancing `fakeNowMs`, but the new
cases assert the reveal **without** delivering a further fix or writing the viewport. The helper's
current form is retained for the cases where a fix is what moves the vehicle.

### D5 — Arm lifetime

The arm is cleared on the same conditions that reset `browseReCenterBeyondSinceMs` today (offset below
the hide threshold, offset `NaN`, quality `NONE`, or not BROWSE). No new clearing rule; the tick simply
re-reads the same conditions, so leaving BROWSE mid-dwell cannot reveal the button later. That covers
spec scenarios "Leaving BROWSE discards a pending dwell" and "A fix that ages out suppresses the
pending dwell".

## Risks / Trade-offs

- [Reveal latency becomes tick-quantized: up to one tick period after the dwell] → the tick period
  stays the existing 1 s, i.e. worst case ~2 s from the first off-center evaluation; the spec asks for
  "a short dwell", not a precise instant, and the boundary case is asserted through the injected clock,
  not through an observable latency.
- [A spin-bounded pump can outrun its budget on a loaded machine] → the budget is a spin count, not a
  clock read (a wall-clock deadline loop is refused by `checkNoWallClockWaits`), sized to cover many
tick periods; the cases use the production 1000 ms period, so a case that waits for a dwell tick costs
  about a real tick period.
- [A background tick writing UI state could fight the collector] → the tick only increments a
  `MutableStateFlow` counter; only the main-dispatcher collector writes `browseReCenterVisible`.
- [The reveal cases pay a real tick period each (~1 s; the class spends most of its ~17 s in that
  wait)] → accepted: they are bounded and the discriminating cases assert the counter and the boundary,
  not a latency. A second, shorter period for the same loop was rejected in D3, and a virtual `delay`
  job in D1.
- [Extra ticks in a stationary app] → ticks are emitted only while an arm is pending and the button is
  hidden, and stop at the first flip; "no dwell re-evaluation while centered" is asserted.
- [The dwell now completes while the app is backgrounded] → the arm and the reveal are state-only; the
  button is composed when the surface returns. Behavior equals the pre-change intent; no new wakelock
  or timer registration exists beyond the already-running tick loop.

## Migration Plan

No data, schema, persistence or manifest change; no migration. Rollback: revert the ticker duty and the
two seams — the previous (defective) emission-driven dwell returns. Deploy path is an ordinary app
release; nothing to sequence.

## Open Questions

- Whether the tick should also re-derive after a canvas-metric-only change (`setScreenSize` emits no UI
  state). Deferrable: it is a separate latent gap, recorded in `proposal.md` — Impact for a `TODO.md`
  entry, and it does not change this contract or these tasks.
