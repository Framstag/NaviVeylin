# Proposal

## Why

A car map screen can request a frame that is never drawn, and nothing notices. Three code facts
make that silent and unbounded:

- the render loop consumes a request and then drops it on `surface == null` or `width/height <= 0`
  without retrying (`auto/src/main/java/com/naviveylin/auto/AutoMapRendererRender.kt:50-54` — the
  request was already consumed by `frameIteration`, `AutoMapRendererLoops.kt:79-80`);
- one failed `lockCanvas` latches `surfaceFailed` and the loop then drops **every** subsequent
  signal (`AutoMapRendererLoops.kt:51,79`, `AutoMapRendererRender.kt:511-529`); the only recovery is
  the owning screen's `invalidate()`, capped at two attempts per start
  (`FreeDrivingScreen.kt:347-358`, `:940`);
- there is no deadline anywhere: the shipped spec requires "a full frame before the first frame
  after a start" (`openspec/specs/auto-map-renderer/spec.md:224-243`) but no bound, and no watchdog
  re-requests a frame that never landed.

Consequence, reported as issue #25 (`§92`): on 2026-09-27 one of four free-driving pushes showed **no
frame for ≈31 s** — the host chrome was drawn, the surface stayed `#040505`, and no error, spinner or
notice told the driver whether the app was working. Measured on `emulator-5556` (AAOS AVD) at HEAD on
2026-10-30, the reported window did **not** reproduce and the mechanism the item's own fix candidate
named is stale: `onSurfaceCreated` already requests a frame (`AutoMapRenderer.kt:406`) and so does
`resume()` (`:630-635`), so "the renderer waits for the next fix/layout change" is not true; a
stylesheet reload — the other candidate, since a day/night toggle takes the DB thread's write lock
that renders wait on — costs **100–300 ms** here (four loads in 1.2 s at start), which cannot produce
30 s. Measured normal path: push → first frame **0.63 s**; day/night toggle → new surface → frame
**0.87 s**. The cause of that one window is therefore unproven, and this change does not claim it.

This is a defect-shaped improvement, not a `fix-*` change: the root cause of #25's window is not
established, so the change closes the **class** of failure it exposes — a requested frame that never
lands must be retried within a bound, and the exhausted wait must be visible in the diagnostics —
and leaves the 30 s window itself to the next measurement (`status/open-device` stays on #25). The
red case that fails on HEAD asserts the bound that is missing, as `AGENTS.md` requires for a
defect-shaped improvement.

## What Changes

- **A frame wait has a bound.** After `requestRender()` the renderer arms a one-shot watchdog. If no
  frame has been drawn when it expires — and the screen is started, the renderer is not paused or
  shut down, and a surface is held — the renderer requests the frame again.
- **The retry budget is finite.** Two attempts for one stalled wait — the budget re-arms when a frame
  lands — mirroring the screen's surface-refresh budget; each expired wait is recorded once as a
  coordinate-free `MAP` diagnostic naming the retry index (a stalled wait that spends its budget
  records three lines in total: `retry 1/2`, `retry 2/2`, then the exhaustion line), so the next
  device run can read the failure instead of inferring it from a black screenshot.
- **A request the frame path cannot serve stays outstanding.** A frame iteration that draws nothing
  because of the frame's extent or a fault confined to that iteration keeps `pendingRender` set and
  arms the wait, instead of consuming the request and leaving the surface on its last frame. A
  request issued while **no** surface is held is not armed — the surface delivery requests a frame
  itself (`onSurfaceCreated`), and an expired wait without a surface has nothing to retry.
- **Frames that land are not duplicated.** The watchdog compares the draw counters
  (`fullRenderCount + blitCount`) with the values recorded when it was armed, so a normal session
  issues exactly the requests it issued before this change.
- **A stopped renderer retries nothing.** `pause()`, `detachSurface()` and `shutdown()` disarm the
  watchdog, so a stopped screen still performs no surface work (spec `auto-map-renderer` — A stopped
  renderer holds no surface or frame buffer).

Not in scope: the cause of #25's 31 s window (unproven, not reproducible at HEAD on the AAOS AVD),
a placeholder or host-drawn notice for a screen that has **never** drawn a frame (the surface cannot
carry text without a frame and `NavigationTemplate` has no content slot — `MapUnavailableOverlay`'s
KDoc records this limitation; it becomes its own tracker item), and the phone renderer.

## Capabilities

### Modified Capabilities

- `auto-map-renderer`: gains one requirement — a requested frame that never lands is re-requested,
  bounded, reported once, and never duplicated. No shipped requirement's text changes; the
  requirement is ADDED to the existing capability, next to "A stopped renderer holds no surface or
  frame buffer", which is the requirement its stop rule has to agree with.

## Impact

Affected code (`:auto` module only):

- `auto/src/main/java/com/naviveylin/auto/AutoMapRenderer.kt` — the frame-wait state (bound, attempt
  count, draw counters at arm time), the watchdog job, and the disarm in `pause()`,
  `detachSurface()`, `shutdown()`.
- `auto/src/main/java/com/naviveylin/auto/AutoMapRendererLoops.kt` — the watchdog body next to the
  three existing loop entry points, so the frame-wait seam sits where the other renderer seams are.
- Tests: `auto/src/test/java/com/naviveylin/auto/AutoMapRendererFrameWaitTest.kt` (new).

Not affected: no native or JNI change (nothing in `app/src/main/cpp/`), no new dependency, no
manifest, resource or flavor change, no phone code, no change to `requestRender()`'s signature or to
the renderer's public surface that `:app` compiles against.

Additive, not breaking. Rollback path: delete the watchdog job, its arming call and the
`pendingRender` retention in `frameIteration()` — the renderer behaves exactly as on HEAD (a request
that could not be drawn is consumed, and a lost request stays lost).

Guidelines: `guidelines/MapRendering.md` §14 (Android Auto renderer) gains the bound, the budget and
its numbers; `guidelines/Logging.md` §1/§3 (the diagnostic is an identity line, no coordinates —
`checkNoCoordinatesInLogs` must stay green); `guidelines/Build.md` §6e (no case paces a test off a
clock: the seam takes an explicit `nowMs`, the bound and the clock are injectable) and §2a (measure
first — the numbers above are why this change exists and why it claims no root cause).
