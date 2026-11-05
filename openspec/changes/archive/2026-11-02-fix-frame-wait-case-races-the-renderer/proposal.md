# Proposal

## Why

The `:auto` unit suite is red on `main` for one case, and the case is load-dependent rather than wrong
about the requirement: issue #743 reports
`test / com.naviveylin.auto.AutoMapRendererFrameWaitTest.theArmedWaitFiresAndRetriesTheFrame` with a
failure and `baseline: success` (sensor `broken-main`, fingerprint `0ad539b4…`, occurrence 1).

**Root cause.** `theArmedWaitFiresAndRetriesTheFrame`
(`auto/src/test/java/com/naviveylin/auto/AutoMapRendererFrameWaitTest.kt:207`) decides its expiry from
state that two other threads mutate, so the case races the renderer's dispatcher:

- it arms the watchdog with `frameWaitBoundMs = 1L` and advances its injected clock **from the test
  thread after** `requestRender()` (`:212-223` at base). The armed coroutine's fire-time read
  (`AutoMapRendererLoops.kt:120`) can therefore beat the assignment, read the clock at the arm instant,
  and take the early return at `AutoMapRendererLoops.kt:146` — the wait is dropped with no retry and no
  report, and both assertions fail;
- `rendererWithSurface()` (`:45-50`) calls `onSurfaceCreated`, which already armed a wait
  (`AutoMapRenderer.kt:841-849`). That job's `delay(frameWaitBoundMs)` reads the bound **when it runs**
  (`:119`), so lowering the bound below shortens the surface-created wait mid-flight: two watchdogs fire
  against the case's clock and the second retry moves `renderRequestCount` past any snapshot the case
  takes. Measured with a diagnostic run of the clock-only fix (the case failed 4 of 8 runs):
  `java.lang.AssertionError: the armed wait spent one retry expected:<1> but was:<2>`
  (`evidence/second-watchdog-diagnostic.txt`). The other direction is measured too — a retry that lands
  before the case's count snapshot leaves it at `requests=3->3 retries=1`
  (`evidence/clock-race-diagnostic.txt`).

Both are the same defect: the case does not own the state it decides its expiry from. Under load either
interleaving lands, which is why the same tree is green on an idle host and red in CI.

**Red case on the base commit.** The base case fails on the base commit when the interleaving is forced —
a 20 ms stall between `requestRender()` and the clock assignment (a probe, reverted; a fixed sleep in test
sources is refused by `:auto:checkNoWallClockWaits`, so the probe run needed
`-x :auto:checkNoWallClockWaits`). Run
`./gradlew :auto:testDebugUnitTest --tests '…AutoMapRendererFrameWaitTest.theArmedWaitFiresAndRetriesTheFrame' -PforceTests -PjvmTestsOnly --no-build-cache`:
XML `tests="1" failures="0" errors="0"` on an idle host (8 runs) and `tests="1" failures="1" errors="0"`
with the stall, `timestamp="2026-11-02T18:33:56.716Z"`, message
`java.lang.AssertionError: the armed wait requested the frame again` at
`AutoMapRendererFrameWaitTest.kt:228` (`evidence/red-case.xml`). Fourteen unmodified base runs (8 idle, 6
under a four-spinner CPU load) stayed green: the window is real but narrow, which is what a load-dependent
red build looks like.

**Owning requirement.** `openspec/specs/auto-map-renderer/spec.md:353` — "A requested car frame that
never lands is re-requested". The requirement's scenarios name only the seam cases; the **armed** watchdog
(`AutoMapRendererFrameWaitTest.theArmedWaitFiresAndRetriesTheFrame`) — the wiring the seam cases cannot
see — is not in the spec yet, so the delta states it.

**Owning guideline section.** `guidelines/BuildTests.md` §6e "Wall-clock waits and the conversion
measured": *a test that must wait drives the component's seam and awaits observable state*, and wall-clock
waits are refused with no allowlist. §6f "Diagnosing a red run": a load-dependent case is diagnosed from
the failing aggregate run's own record.

**Surface** (`grep -rn "frameWaitClock" --include=*.kt auto/src`, `grep -rn "frameWaitJob" --include=*.kt
auto/src/test`): 1 case sets the injected clock, 1 case joins the armed job, 7 cases in the class, 2
production read sites of the clock (`AutoMapRendererLoops.kt:115,120`), no fixtures and no prompts — 1 case
rewritten, no case added, no production file changed.

surface: 1 case rewritten, 0 cases added, 2 production read sites unchanged, 0 fixtures realigned — `grep -rn "frameWaitClock" --include=*.kt auto/src` and `grep -rn "frameWaitJob" --include=*.kt auto/src/test`

## What Changes

- `AutoMapRendererFrameWaitTest.theArmedWaitFiresAndRetriesTheFrame` owns all the state it decides its
  expiry from: it disarms the wait `onSurfaceCreated` armed before lowering the bound, so exactly one
  watchdog is in play, and its injected clock advances **on its own read** — the arm reads the arm
  instant, every later read is past the bound — so the fire-time decision cannot depend on which thread
  ran first.
- The assertions move from order-dependent snapshots to the retry's own observables: the retry counter
  (exactly one retry), the recorded diagnostic (one identity-only line, `Logging.md` §3), and a request
  count taken **before** this case's own request (so the retry may land at any point and still be seen).
- No production code, no public API, no behaviour change: the watchdog keeps its one deadline, its
  re-arm on a landed frame and its bound.

## Capabilities

### Modified Capabilities

- `auto-map-renderer`: "A requested car frame that never lands is re-requested" gains the scenario for the
  armed watchdog, naming the case that now decides the expiry from its own clock.

## Impact

- One test file: `auto/src/test/java/com/naviveylin/auto/AutoMapRendererFrameWaitTest.kt`.
- No production file, no spec requirement text removed, no fixture, no device run: the defect is a case
  that races the renderer, and a host test decides it.
- Rollback: `git revert` of the single test-file hunk; the base case stays flaky, which is what it was.
