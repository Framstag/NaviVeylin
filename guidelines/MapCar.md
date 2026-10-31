# Map Car — the Android Auto renderer and the phone canvas

This document owns the car's own render path — the Android Auto smooth-follow renderer (overrun,
blit and extrapolation) — and what happens to the phone canvas while a car session is active. The
pipeline they share is `guidelines/MapRendering.md`; the history is in `guidelines/MapCar.why.md`.

---

## 14. Android Auto renderer — smooth follow (overrun + blit + extrapolation)

`AutoMapRenderer` (spec `auto-smooth-follow`) mirrors the phone's overrun/blit machinery on the
car Surface:

- **Rule: CRITICAL — the renderer never owns the surface**: the car Surface's lifetime belongs to
  the session (`SessionCarSurfaceHost`, change `fix-aaos-host-crash`, spec
  `car-host-fault-isolation`): one `SurfaceCallback` registration per session, one owner screen
  at a time, released exactly once per **delivery** on the host's `onSurfaceDestroyed`, on
  replacement, or at session end. A
  renderer draws, stops drawing (`detachSurface`, `pause`) and clears its reference — it never
  calls `Surface.release()`. Releasing it here (the earlier shape, on replace/stop/shutdown)
  disconnected the buffer queue the host — or the screen that superseded this one — was still
  using, which showed up as `lockCanvas` failures and an invalidate loop. A frame whose surface
  was replaced or destroyed during the long native render is dropped (`isCurrentSurface`), and
  a stale surface is never reported as a failure of the live one (that used to wedge the render
  loop for the live surface until another delivery).
- **Rule: CRITICAL — the stop path detaches**: a screen's `onStop` is `pause()` **plus**
  `rendererGate.detachSurface()` (change `fix-car-surface-ownership-and-host-callbacks`). The host
  starts the incoming screen before it stops the outgoing one, so a renderer that kept the surface
  reference could lock the one session surface while the incoming screen drew through it; the
  session also revokes the surface from the owner it supersedes
  (`CarSurfaceOwner.onCarSurfaceRevoked`) at `attach`, so at most one renderer locks it. Detaching
  drops the reference, clears `surfaceFailed` (a failure recorded while the screen was visible must
  not gate the next start) and releases the overrun buffer — the buffer is per screen, and up to
  four car screens can sit in the stack.
- **Rule: Overrun buffer**: every full native render is at `OVERRUN_FACTOR` (1.2×) the surface size and
  is KEPT as the overrun buffer for sub-region blits — until the screen stops, which releases it;
  the first frame after the next start is therefore a full render, never a blit of a
  frame that predates the stop. The visible region is drawn
  centered: `dx = (surfaceW - bitmapW) / 2`.
- **Rule: The overrun frame is a pooled target, held until replaced** (change `fix-render-buffer-reuse`,
  spec `render-performance`): the renderer acquires its target from `RenderBitmapPool` and keeps it
  as `overrunBitmap`, so a render that starts while a frame is displayed acquires a **second**
  target — the displayed frame and the in-flight render never share one. Replacing the frame
  **releases** the old target (it used to `recycle()`), `clearOverrunBuffer()` releases the held one
  (surface change, stop, shutdown), a failed render releases the unused target, and `displayed*()`
  stays correct because it derives from the reference that is held exactly as long as the frame is
  displayed. The marker draw objects are cached the same way (spec `auto-map-renderer` — Marker
  drawing allocates no per-frame objects): built on first use, rebuilt only when the surface
  bounds, the density or the dark presentation changes.
- **Rule: Sub-region blit**: a viewport change within the overrun region is served by
  `lockCanvas → drawBitmap(overrun, dx, dy) → unlockCanvasAndPost` — no native render. The blit
  offset comes from `OverrunWindow.shiftPx` (rotated-frame clamp, same rotation rule as
  the phone blit). Beyond the margin → full render at the new center.
- **Rule: Extrapolation loop**: a gated ~30 fps coroutine eases the displayed position toward
  `FollowPrediction.predictedPosition` and blits the delta; the GPS/destination markers are drawn
  at the displayed position so they glide with the map. Gate: resumed + follow mode + speed
  > ~1 m/s + fresh fix + valid surface (battery/thermal on head units).
- **Rule: Display-only prediction**: predicted positions never reach the navigation engine — the
  prediction lives entirely inside the renderer.
- **Rule: CRITICAL — overrun bitmap lifecycle**: the overrun bitmap is recycled when a new full render
  swaps it in. The extrapolation loop and the render loop run on different coroutines, so the
  read of the overrun bitmap AND the blit draw MUST happen under the shared `surfaceLock` —
  otherwise a concurrent full render recycles the bitmap mid-draw ("trying to use a recycled
  bitmap" crash). The full native render itself runs OUTSIDE the lock so the loop can keep
  blitting the old frame while the render is in flight.
- **Rule: CRITICAL — display ownership in follow mode**: in follow mode the extrapolation loop owns
  `displayLat/Lon` (the eased predicted position). `fullRender` and the `renderFrame` blit path
  MUST NOT reset the display to the render target when `followMode` is engaged — a render
  triggered by a transient follow-off (heading-up `setViewport`) would otherwise yank the eased
  display back every fix (visible "pumping"). Only set `display = viewport` when `!followMode`.
- **Rule: reengageFollow must anchor the viewport and request the frame**: screens that do transient
  `setViewport(...) → reengageFollow()` per fix (heading-up rotation) must make
  `reengageFollow` set `viewport = fix` + emit — otherwise the next `setViewport` reads a stale
  `viewportState` (initial center), the pending render targets it, and the display pumps between
  the stale center and the fix. `reCenter` keeps the snap (user re-center button);
  `reengageFollow` re-engages without snapping the display. It also MUST request the frame it
  re-anchored when none is pending (`blitEligible = true; if (!pendingRender) requestRender()`) —
  a pan release (`MapPanHandler`) and a screen start/resume re-engage with no commit behind them, so
  relying on a preceding `setViewport` left the stale frame on the surface (change
  `fix-car-follow-reengage-render`, spec `auto-smooth-follow` — A follow re-engage requests the frame
  it re-anchored). The guard matters: the render loop draws one frame per `renderSignal` value, so an
  unconditional request doubles the fix path's renders.
- **Rule: A stale fix must not freeze the display**: a GPS gap does NOT gate the extrapolation loop off.
  The feed-forward keeps the last committed speed, decaying to zero over `STALE_FEED_GRACE_SEC` +
  `STALE_FEED_DECAY_SEC` = 3 + 7 s (`FollowPrediction`), and the correction and the snap are skipped
  while the reference is older than `STALE_REFERENCE_SEC` — the display keeps advancing forward at
  the decaying speed instead of stopping. Both alternatives were measured as wrong: freezing the
  display at the last predicted position reads as a massive overshoot during gaps, and correcting
  onto the stale fix dragged the map backwards off the road. The first fresh fix afterwards
  re-applies the correction and, beyond `TELEPORT_SNAP_METERS`, the snap — that is the GPS-recovery
  case.
- **Rule: Blit eligibility**: only pure viewport/marker changes may blit. Favorites, route, a change of
  the renderer's projection DPI, and
  surface changes force a full render (`blitEligible = false`) — a blit would show stale
  native-rendered content.
- **Rule: The projection DPI travels with the render request** (spec: `render-projection-dpi`): each
  renderer passes its own display's DPI (`MapRenderer.dpi`, `AutoMapRenderer.projectionDpi`) to
  `MapRenderUtil.renderInto`/`renderPixels`, and the JNI render projects with it. The client
  keeps no DPI of its own, so an AA session and the phone canvas sharing one process cannot
  change each other's frame scale; `TileCache` keys a tile by the DPI it was rendered at,
  because the pixels depend on it.
- **Rule: Loop liveness — a fault costs one frame, never the pipeline** (change
  `fix-car-render-loop-restart`, spec `car-host-fault-isolation` — "A fault in one frame keeps the
  frames coming"; design D1/D2/D7): all three loops (the `renderSignal` consumer in
  `startRenderLoop`, `startExtrapolationLoop`, `startZoomWalkLoop`) run their iteration body through
  `RenderLoopSupervisor.runIteration(RenderLoop.X) { … }` — the extracted `frameIteration()`,
  `displayTickIteration()`, `zoomWalkIteration()` are the testable form. The guard **must stay
  inside the `renderSignal.collect` body**: a throw that escapes the collector ends it and every
  later `requestRender()` is dropped while the surface stays on its last frame (the `TODO.md` §51
  freeze). `CancellationException` is rethrown by the guard — a cancelled scope must end the loop.
  `frameIteration()` additionally returns early for `isShutdown`/`paused`/`surfaceFailed`, so a
  stopped screen neither draws nor counts a fault (it cannot drive the ladder).
- **Rule: A requested frame that never lands is re-requested** (change `car-frame-wait-bound`, spec
  `auto-map-renderer` — A requested car frame that never lands is re-requested; the documented silence (`MapCar.why.md` §14) is what the
  old silence looked like to a driver: a black surface with host chrome and no message).
  `requestRender()` arms a one-shot wait; when it expires with no draw since it was armed
  (`fullRenderCount + blitCount` unchanged, a surface still held, not paused), the renderer requests
  the frame again. Bound `FRAME_WAIT_BOUND_MS` = 4 s, budget `FRAME_WAIT_MAX_RETRIES` = 2 for ONE
  stalled wait, and the budget re-arms as soon as a frame lands — so one stall does not disarm a long
  session. The numbers come from the AAOS AVD (2026-10-30): a free-driving push draws its first frame
  in 0.63–0.79 s, a surface replacement in 0.87 s, a full native render takes 25–300 ms, so 4 s is ~5x
  the worst measured transition. A `frameIteration()` that drew nothing keeps `pendingRender` set
  instead of consuming the request, and `pause()`, `detachSurface()` and `shutdown()` disarm the wait,
  so a stopped screen still performs no surface work. A healthy session logs **nothing** —
  `adb logcat -s Diag/MAP | grep 'frame wait expired'` was empty over a 60 s free-driving run (measured
  2026-10-30); when a wait does expire, one line per retry is recorded, identity-only:
  `frame wait expired: no frame in 4000 ms (retry 1/2)` and, at the end of the budget,
  `frame wait expired: no frame in 4000 ms after 2 retries`.
- **Rule: Diagnosing a frozen or degraded car map** (same change): one `adb logcat -s Diag/MAP` line per
  fault streak, per re-creation and at the degrade transition, all identity-only:
  - `renderer frame fault #1: IllegalStateException (recoveries=0)` — `<loop>` is `frame`,
    `extrapolation` or `zoom-walk`; further skips in the same streak are throttled to one entry
    per 5 s;
  - `renderer frame: re-creating after 3 consecutive faults (attempt 1/2)` — the gate shuts the
    old renderer down, publishes a new one (the state slots replay: viewport, follow anchor,
    presentation, pane geometry, marker/favorites/route/destination, overlay drawer) and the screen
    re-attaches the session's surface;
  - `renderer frame: degraded after 2 re-creation(s), last fault IllegalStateException` — the
    budget for this started screen period is spent; the screens log `map state -> DEGRADED`
    (`MapScreen`/`NavigationScreen`/`FreeDrivingScreen`/`DetailsScreen`) and show `map_unavailable`
    (a disabled content row on the map/details screens, a surface-drawn notice on the two
    `NavigationTemplate` screens, which have no content slot). `onStart` re-arms the budget.
  A fault here is **not** the dead-surface path: `surfaceFailed` (a surface that cannot be locked)
  still stops the loop deliberately and asks the host for a fresh surface (`MAX_SURFACE_REFRESH_ATTEMPTS`).

---
## 18. The phone canvas while a car session is active — disposal, not hiding

A live car session **disposes** the phone map canvas: `MapCanvasScreen` composes
the car-session surface *instead of* the canvas (`guidelines/UIPhoneMap.md` §10a) — the
canvas and all of its overlays, gesture state and remembered frames are simply
not composed. Merely hiding the canvas would keep its window/EGL/GL buffers
allocated, which is where the measured saving is: Graphics 132 MB → 28 MB with
the phone UI gone while the car kept drawing (design of
`phone-surface-while-car-session`, Pixel 8, fresh process per state).

`MapRenderer.releaseRenderStorage()` gives up the rest. Its order *is* the race
argument, and each step earns its place:

1. the render **epoch is bumped first**, so a render already in flight discards
   its result instead of swapping a buffer in behind the release (every commit
   path checks the epoch under `bufferLock`);
2. a queued-but-unstarted render is dropped (`pendingRender`). The two
   **lifetime loops are NOT cancelled**: `debounceJob`/`renderJob` consume the
   renderer's channels for as long as the renderer lives, and cancelling them
   leaves a renderer that can never render again — including the resume render.
   (A first implementation cancelled them and the test caught it immediately:
   every post-release render silently produced no frame.)
3. the two frame buffers are recycled under `bufferLock` — they are
   renderer-private, nothing outside the class ever sees them, exactly as in
   `shutdown()`;
4. the tile cache is cleared. Its bitmaps are only **dropped, never recycled**: a
   tile may still be referenced by a render in flight (the rule `TileCache.clear`
   already followed);
5. the last emitted frame is dropped the same way and **never recycled** —
   Compose may still be drawing it — and the frame flow is reset to a
   bitmap-less state, because a `StateFlow` retains its last value and that value
   would otherwise be the only remaining reference keeping the frame alive;
6. the pool's idle targets are released (`RenderBitmapPool.releaseIdle`). Nobody
   draws with a target the pool holds free, so this cannot alias a live target;
   the car's only cost is one fresh allocation on its next render.

**What is deliberately not released: the shared native tile-data cache.** It is
client-wide and the car surface renders from it while the phone is suspended —
trimming it would force a refetch storm on the car. Releasing it under *pressure*
is `bound-tile-data-retention`'s job, not this one.

No native render call happens in the release — only reference drops and two
short locks — so it is safe from a presence/lifecycle callback. The renderer
stays usable; the resume is a **fresh render at the retained viewport**, with no
crossfade from the last frame: keeping that frame for a transition would hold an
overrun-sized bitmap plus its GPU texture for the whole session, i.e. it would
give back part of the saving for a cosmetic effect.

All the counters involved are **high-water marks inside a process** (native heap,
`Graphics`, `TOTAL PSS`, malloced bitmaps), so any verification of this path needs
a fresh process per state — recipe in `guidelines/Build.md` §10.

---
