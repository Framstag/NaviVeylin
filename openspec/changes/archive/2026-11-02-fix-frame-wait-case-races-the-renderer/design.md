# Design

## Context

See `proposal.md` — Why. The state that shapes the approach:

- `AutoMapRenderer.requestRender()` (`AutoMapRenderer.kt:841`) is the only arm site
  (`AutoMapRenderer.kt:849`); `onSurfaceCreated` calls it, so a renderer that has a surface always has a
  wait armed before a case touches the bound.
- `armFrameWaitWatchdog` (`AutoMapRendererLoops.kt:113-123`) records `frameWaitArmedAtMs` from
  `frameWaitClock()` and launches a one-shot job whose body reads **both** live fields again:
  `delay(frameWaitBoundMs)` and `frameWaitIteration(frameWaitClock())`.
- `frameWaitIteration` (`:136-163`) drops the wait when the draw counters moved, when the surface is gone,
  and when `nowMs - frameWaitArmedAtMs < frameWaitBoundMs` (`:146`); the retry is `requestRender()` (`:162`).
- The case's own seam is `frameWaitClock` / `frameWaitReport` / `frameWaitRetries` / `frameWaitJob`
  (`AutoMapRenderer.kt:334-364`), all `internal` and already used by the other six cases.

## Goals / Non-Goals

**Goals**

- One armed wait, one clock, no dependence on which thread reaches a statement first: the case's verdict is
  the same on an idle host and on a loaded CI runner.
- Keep what the case is for: the **armed job**, not the seam, turns an expired wait into a request — the
  coverage `aRequestArmsTheWaitAndAStopDisarmsIt` cannot give.

**Non-Goals**

- Any change to the frame-wait bound, its retry budget, its clock (`SystemClock.elapsedRealtime`), or the
  renderer's behaviour.
- Any change to the other six cases, which drive the seam with explicit `nowMs` values and never arm.

## Decisions

### D1 — The case owns its clock read; the production check is left alone

The injected clock advances **on its own read**: the first read (the arm instant) returns the arm instant,
every later read returns past the bound. The fire-time decision is then a property of the case, not of the
scheduler.

*Alternative eliminated:* make `frameWaitIteration` re-arm when a fire-time read is behind the arm instant.
The production clock is `SystemClock.elapsedRealtime` (`AutoMapRenderer.kt:340`) and `delay(bound)` never
resumes early, so a production fire-time read is always past the bound: the drop is reachable only through
an injected clock, no observation shows it on a device, and a re-arm path would be behaviour nobody
measured (and a second decision — the re-arm's delay — gate 3 does not answer).

### D2 — Disarm the surface-created wait before lowering the bound

`onSurfaceCreated`'s request armed a wait whose `delay(frameWaitBoundMs)` reads the bound when it runs, so a
bound lowered afterwards shortens it in flight: it fires beside the case's own wait and its retry lands in
the counters. The case disarms it (`disarmFrameWaitWatchdog()`) before setting the bound, so exactly one
watchdog exists and `frameWaitRetries` is the retry of the wait under test.

*Measured:* with the clock fix alone (D1) and no disarm, the case failed 4 of 8 runs with
`the armed wait spent one retry expected:<1> but was:<2>` (`evidence/second-watchdog-diagnostic.txt`) — the
second retry is the surface-created wait firing after the bound changed under it.

*Alternative eliminated:* arm the wait before creating the surface. `rendererWithSurface()` constructs the
renderer, and its `onSurfaceCreated` request is the only arming path — disarming once is the one-line
version of the same ordering.

### D3 — Assert the retry's own observables, not a snapshot taken after the request

The retry lands as soon as the one-millisecond bound elapses — possibly while the test thread is still
inside `requestRender()`. The case therefore takes `renderRequestCount` **before** its own request and
asserts the count grew by the request *and* the retry (≥ +2), asserts `frameWaitRetries == 1` (the retry
incremented it; nothing resets it, since no frame can land while `asyncLoopsEnabled = false`), and asserts
one recorded diagnostic. A retry that never happens leaves the count at +1 and the retry counter at 0, which
is the mutation the case is checked against (`evidence/mutation.xml`).

*Alternative eliminated:* re-read the count "after a short wait". Refused by `:auto:checkNoWallClockWaits`
(no allowlist) and by `guidelines/BuildTests.md` §6e — the retry has an observable, use it.

## Threading / lifecycle

- The clock lambda is read from the main thread (arm) and from the dispatcher (fire); the read counter stays
  a plain local because the two reads are ordered by the arm-then-launch happens-before in
  `armFrameWaitWatchdog`.
- The re-armed wait of the retry fires with `armedAt == now`, so it returns at `:146` without retrying:
  the chain ends by itself, and `disarmFrameWaitWatchdog()` after the join still leaves no active job for
  `RendererTestRule` to report.
- Teardown is unchanged: the rule shuts the renderer down and fails the class if a watchdog survives.

## Verification

- Red on the base commit (probe, reverted): `evidence/red-case.xml` — `tests="1" failures="1"`,
  `timestamp="2026-11-02T18:33:56.716Z"`, message `the armed wait requested the frame again`.
- Green and repeatable after the fix: the case run 10 times —
  `evidence/case-repeat.xml` (one representative XML; every run `tests="1" failures="0"`), timestamps
  `18:42:07Z … 18:43:30Z`; the whole class — `evidence/class.xml`, `tests="7" failures="0" errors="0"`,
  `timestamp="2026-11-02T18:43:58.749Z"`.
- Not vacuous: with the retry removed from `frameWaitIteration` (`requestRender()` at `:162`) the case fails
  deterministically — `evidence/mutation.xml`, `tests="1" failures="1"`,
  `timestamp="2026-11-02T18:43:45.515Z"`; the production file was restored from a copy, `git status` names
  only the test file.
- The gate: `LOOP-GATE` from `./tools/loop/gate.sh` (see `tasks.md` 4.1).
