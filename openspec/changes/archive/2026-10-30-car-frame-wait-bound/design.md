# Design

## Context

See `proposal.md` — Why. The state that shapes the approach:

- `AutoMapRenderer.onSurfaceCreated` (`AutoMapRenderer.kt:388-407`) and `resume()` (`:630-635`) both
  call `requestRender()`, so a started screen always has at least one outstanding request; what is
  missing is what happens when the request never turns into a frame.
- The render loop is a `renderSignal.collect` in `AutoMapRendererLoops.kt:45-61`: it drops requests
  while `paused`/`surfaceFailed`/`isShutdown`, then `frameIteration()` clears `pendingRender` and
  calls `renderFrame()`, which returns silently when `surface == null` or the size is 0
  (`AutoMapRendererRender.kt:50-54`).
- The counters already exist and are `@Volatile`: `fullRenderCount`, `blitCount`,
  `renderRequestCount` (`AutoMapRenderer.kt:236-238`).
- `RenderLoopSupervisor` (`RenderLoopSupervisor.kt`) already owns "per renderer instance" budgets
  and confines faults per iteration; `RendererGate` owns per-*started-period* budgets and re-arms
  them in `startPeriod()`.
- Measured on `emulator-5556` (AAOS AVD, automotive debug, HEAD 2026-10-30): push → first frame
  0.63 s; day/night toggle → surface destroyed, pause/detach, new surface → frame 0.87 s; a
  stylesheet reload 100–300 ms; one in-flight frame is dropped per surface replacement (correct per
  spec `car-host-fault-isolation`, and the replacement's own frame follows within 0.9 s).

## Goals / Non-Goals

**Goals**

- A request that never becomes a frame is retried within a bound, on the renderer's own thread, with
  no new public API and no new wakeups in the healthy path.
- A normal session is unchanged: no extra requests, no extra frames, no new log noise.
- The next device run can *read* an expired frame wait from the diagnostics instead of inferring it
  from a black screenshot.

**Non-Goals**

- Explaining #25's 31 s window (unproven; the reported build predates the surface-ownership and
  loop-confinement reworks, and the window does not reproduce at HEAD on the same AVD image).
- A placeholder, spinner or host notice while no frame exists (see D4).
- Any change to the phone renderer, to the blit/overrun pipeline, or to `requestRender()`'s contract.

## Decisions

### D1 — Where the expired wait is detected

- **(a) A fourth periodic loop** (e.g. 1 Hz) that checks the outstanding request. *Rejected:* a
  permanent wakeup for an event that is rare, a fourth entry in the loop table that
  `RenderLoopSupervisor` and the spec's "the loops of a re-created renderer are shut down" scenario
  would have to grow, and the healthy path would pay for it every second.
- **(b) A one-shot watchdog armed by `requestRender()`, re-armed after the check.** *Chosen:* zero
  wakeups while no request is outstanding beyond the single deadline; the check is one comparison
  against the draw counters; disarming is a job cancel, the same shape as the other renderer jobs.
- **(c) A watchdog in the owning screen** (`FreeDrivingScreen`/`NavigationScreen`), on the host main
  thread, re-requesting through `rendererGate`. *Rejected:* the host thread must stay free
  (`guidelines/Design.md` §4, spec `car-host-fault-isolation` — Host callbacks answer promptly), and
  every one of the three map screens would carry a copy of the rule — a fourth place to forget. The
  renderer already owns the request, the surface and the counters.

### D2 — What counts as "a frame landed"

- **(a) The draw counters at arm time** — `fullRenderCount + blitCount` (`AutoMapRenderer.kt:236-238`).
  *Chosen:* both are incremented on the render thread once the frame work completed (full render
  `AutoMapRendererRender.kt:254`, before the draw — so it also counts the "dropping frame: surface
  changed during the render" case; blit `:454`, after the post), they already exist, and a test can
  read them without a timer. The predicate is therefore "the frame work completed", not "pixels
  reached the host": a frame dropped for a replaced surface counts as progress, which is what the
  wait is for (the new surface's own request follows).
- **(b) A `lastFrameMs` timestamp written on every draw.** *Rejected:* a second source of truth for
  the same fact, and the test would have to reason about wall-clock time (forbidden by
  `guidelines/Build.md` §6e).
- **(c) Request and frame sequence numbers.** *Rejected:* more state for the same predicate; the
  counters already answer "did anything get drawn since".

### D3 — Bound and budget

- **Bound: 4 000 ms.** Measured norm is 0.63 s (push) and 0.87 s (surface replacement); a full native
  render is 25–300 ms (`AutoMapRendererRender.kt:263` comment). 4 s is ~5× the worst measured
  transition and still short enough that a driver waiting for a map sees the retry, not a hang.
- **Budget: 2 retries for one stalled wait**, re-armed when a frame lands. Mirrors
  `MAX_SURFACE_REFRESH_ATTEMPTS = 2` on the screens (`FreeDrivingScreen.kt:940`), so the two recovery
  paths cannot disagree about how hard the app tries, and a long session is not permanently
  disarmed by one stall. The counter lives on the renderer because the retry is the renderer's work.

### D4 — What happens when the budget is spent

- **(a) Diagnostic only.** *Chosen.* One `MAP` line per expired wait (a stalled wait that spends its
  budget records `retry 1/2`, `retry 2/2` and the exhaustion line — three lines, never more per
  stall); the existing degraded notice (`MapUnavailableOverlay`) keeps its own rule.
- **(b) Draw a "loading" notice on the surface.** *Rejected:* the notice is drawn by the overlay
  drawer **inside** a frame (`drawToSurface` → `overlayDrawer`), so it cannot appear while no frame
  exists — the very case it would be for. A second, independent lock/draw path on the shared car
  surface would race the render loop and the session's surface ownership (the `surfaceLock`
  discipline exists exactly for that).
- **(c) Fall back to a template with a content slot** (`MapWithContentTemplate`, as `RootScreen`
  does with its disabled `map_unavailable` row). *Rejected for this change:* it replaces the map
  template on the failure path and re-introduces the "Free driving" box the full-screen
  `NavigationTemplate` exists to avoid; it is a presentation decision that needs its own item and
  its own pixels measurement. Filed as a follow-up instead.

### D5 — Threading and lifecycle

The watchdog is a job in the renderer's own scope (`carScreenScope("AutoMapRenderer",
Dispatchers.Default)`, `AutoMapRenderer.kt:102`), so it runs where the rest of the frame work runs
and a fault in it cannot reach the host thread. It reads the `@Volatile` counters and the
`paused`/`surfaceFailed`/`isShutdown` flags; it re-requests through the existing `requestRender()`
(which is already safe from any thread). `pause()`, `detachSurface()`, `onSurfaceDestroyed()` and
`shutdown()` cancel it, so a stopped screen performs no surface work — the rule of spec
`auto-map-renderer` — and the next `resume()`/surface delivery arms it again. The check itself is
exposed as an internal seam (`frameWaitIteration(nowMs)`) so the cases drive it directly instead of
sleeping, following the pattern of `frameIteration()` / `displayTickIteration()`; the bound
(`frameWaitBoundMs`) and the clock (`frameWaitClock`, `SystemClock.elapsedRealtime` in production)
are injectable for the two cases that must run the armed job rather than the seam, so no case paces
a test off a deadline and a wall-clock step cannot make an expired wait look fresh.

## Risks

- **A retry storm on a genuinely dead surface.** `frameWaitIteration` returns early while
  `surfaceFailed` is set (the same rule `frameIteration` follows), so the dead-surface path produces
  no retry lines; the budget is finite. Residual risk: none beyond two extra native renders per
  stall.
- **A spurious retry on a slow head unit** where a native render legitimately takes longer than 4 s.
  Consequence: the request is set again while the render is in flight, so one extra native render
  runs after it returns — bounded by the budget, no unbounded work. The bound is a constant with its
  measurement in the KDoc so the next device run can move it with evidence rather than guesswork.
- **Test flakiness.** The cases call the seam with explicit `nowMs` values and an injected
  "frames drawn" reading; nothing waits on a clock.

## Verification

Table: the four spec scenarios, the two wiring cases and the red-on-HEAD witness.

| what it proves | case |
|---|---|
| S1 a frame that never lands is requested again | `aFrameThatNeverLandsIsRequestedAgain` |
| S2 landed frames are not duplicated | `landedFramesAreNotDuplicated` |
| S3 a stopped renderer does not retry | `aStoppedRendererIssuesNoRetry` |
| S4 the retry budget is finite and reported once | `theExhaustedBudgetIsReportedOnce` |
| the request survives a frame path that cannot draw (red on HEAD) | `aRequestTheFramePathCannotServeStaysOutstanding` |
| the wiring: a request arms the wait, every stop path disarms it | `aRequestArmsTheWaitAndAStopDisarmsIt` |
| the armed job, not only the seam, turns an expiry into a request | `theArmedWaitFiresAndRetriesTheFrame` |

Red-on-HEAD: `aRequestTheFramePathCannotServeStaysOutstanding` fails there (`1 test completed, 1
failed`); the other cases reference the seam, which HEAD does not have, so they cannot be compiled
against HEAD at all — their red is the revert-check. Two mutations were run: making
`frameWaitIteration` return before `requestRender()` fails 2 cases, and making
`armFrameWaitWatchdog` a no-op fails 3 (the two wiring cases and the re-arm case) — the second is the
one that proves the feature is wired and not only correct in the seam.

On device (`emulator-5556`, automotive debug): a normal 60 s free-driving session must show **no**
`frame wait expired` line (the healthy-path half of case 2) and a first frame within the measured
0.63 s; the diagnostic's presence in a later run is the evidence a 30 s class of window has occurred
and been retried.
