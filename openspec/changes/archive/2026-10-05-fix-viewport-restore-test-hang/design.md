# Design

## Context

See `proposal.md` — Why, and `TODO.md` §117 for the measurement. The shape that matters:

- `MapCanvasViewModelViewportRestoreTest` freezes an in-flight `initMap` by pointing
  `ViewportStorage.ioDispatcher` at one of three test dispatchers: `GatedDispatcher` (holds every
  dispatch until `release()`), `FirstDispatchGatedDispatcher` (holds only the first), and
  `PostInitializationGatedDispatcher` (holds once the initialization is over). The first and third set
  a `released` flag and run later dispatches inline; the second did not.
- The hops *before* the gated viewport load (`AssetCopier.ensureStylesheets`, the settings read, the
  additional-maps discovery) run on real dispatchers the test scheduler cannot drain, so a case cannot
  assume the coroutine has already reached the load just because it advanced the scheduler.
- The two cases that were green most of the time relied on real time: a 5 s poll for the first render
  (measured at ~5 s under load) and a `!!` on a file that only exists once the restore had applied.
- `runTest`'s own timeout is not a safety net here: while the test thread is parked in `runBlocking`
  and Robolectric's main thread spins, the cancellation the timeout depends on is never processed —
  measured as a >20-minute stall with no failure reported.

## Goals / Non-Goals

**Goals:**

- The class cannot stall a test task: every wait is bounded, and no coroutine can be queued behind a
  gate that has already been released.
- A case that depends on an ordering waits for the observable state and fails loudly if it never
  arrives.
- The cases keep the falsifying power they were written for.

**Non-Goals:**

- Changing production behaviour, the restore window, or the save guard.
- Reworking every gate in the class into a state-keyed one (`PostInitializationGatedDispatcher`'s KDoc
  already prefers that); the two re-entry cases still key on dispatch ordinals, and a follow-up can
  move them — recorded in `TODO.md` §117.
- The wider §101 flake family (load-sensitive `:app` suites); this change closes the class that made a
  verdict unobtainable.

## Decisions

### D1 — Make the first-dispatch gate safe after release, keep its ordering semantics

Chosen: add an `AtomicBoolean released` to `FirstDispatchGatedDispatcher`; `dispatch` holds only while
`!released && first.compareAndSet(true, false)` and runs inline otherwise; `release()` sets the flag
before draining.

- Alternative A — switch the two re-entry cases to the hold-everything `GatedDispatcher`: simpler, but
  it queues *both* loads, so mapA's stale restore would be applied *before* mapB's instead of after —
  the case would pass even with the supersession guard removed, i.e. the assertion stops being a
  falsifier. Rejected on that evidence (the revert-check in `tasks.md` shows the current form fails
  when the guard goes).
- Alternative B — drop the gate and sleep a real-time window: that is the assumption that produced the
  stall, just with a different failure mode.
- Rationale: the defect was the *post-release* branch, not the ordering idea; one flag fixes the stall
  while keeping "mapA is suspended while mapB completes".

### D2 — Wait for the observable state, bounded, and assert on timeout

Chosen: a private `TestScope.awaitHeldBlock(reason, timeoutMs = 5_000) { condition }` that advances the
scheduler, sleeps 10 ms and re-checks, and calls `assertTrue(reason, condition())` when the deadline
passes. Used for "the gate holds the load", "mapB's viewport landed", "the restore is applied", "the
save persisted".

- Alternative A — trust `runTest`'s timeout: measured not to fire while the main thread spins (see
  Context); the suite stalls instead of failing.
- Alternative B — unbounded poll: a regression stalls the task again, which is the defect.
- Alternative C — `Thread.sleep` of a fixed size: what the render case did (5 s deadline reached at
  ~5 s); a fixed sleep encodes the machine's speed into the test.
- Rationale: bounded + asserted turns "the environment was slow" into either a pass with the state
  reached or a named failure, and never into a stall.

### D3 — Deadlines sized from measurement, not from the old values

Chosen: 15 s for the first-render wait (measured ~5 s on a loaded host, and the old 5 s deadline failed
with `expected:<1> but was:<0>`), 5 s for the state polls that only cover scheduler/real-dispatcher
hand-offs.

- Alternative — keep 5 s everywhere: the render case demonstrably fails at the deadline it shares with
  the hand-off polls.
- Rationale: only the failing path pays the longer deadline, and the condition it waits for is
  bounded by one debounce, not by load.

## Risks / Trade-offs

- [A case now waits longer before failing] → only on failure; the wait is the same work the passing run
  does, and the failure names the missing state.
- [The remaining ordinal-keyed gates can still mis-target if `initMap` gains or loses a suspension
  point before the load] → both cases now assert `heldCount > 0`, so a mis-target fails loudly instead
  of hanging; moving them to a state key stays as the §117 follow-up.
- [The §101 family can still produce unrelated load-sensitive failures] → out of scope; the verdict is
  now obtainable, which is what those failures need to be investigated at all.
- [Rollback] → revert the one test file; nothing else depends on it.

## Migration Plan

None — a test-only change. Land it, then re-run both flavors' full `:app` suites (they are the gate
that was unobtainable) and the `:core`/`:auto` suites.

## Verification

- `./gradlew :app:testMobileDebugUnitTest --tests "com.naviveylin.ui.map.MapCanvasViewModelViewportRestoreTest"`
  and the automotive equivalent: 6 tests, 0 failures each (~27 s), where the automotive run used to
  stall (240 s timeout with no XML).
- Full gate, real execution (`--no-build-cache`, never a cached verdict per `TODO.md` §17):
  `:core` 437/0 (38 classes) · `:app` mobile 1483/0 (198) · `:app` automotive 1483/0 (198) ·
  `:auto` 754/0 (74) — `BUILD SUCCESSFUL in 5m 31s`.
- Revert-checks (one mutation each, restored and re-run green): remove the supersession guard in
  `initMap` (`initJob?.cancel()`) → `re-entry while init suspended keeps the new map viewport` fails;
  remove the save guard (`if (!viewportRestored)`) → 3 cases fail. Both bounded (51 s, 36 s) instead of
  stalling.
