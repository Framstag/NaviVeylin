# Design: Overlays project against the displayed frame

## Context

See `proposal.md` — Why, and the spec deltas in `specs/` for the requirements.

Current state that matters for the approach (`auto/src/main/java/com/naviveylin/auto/AutoMapRenderer.kt`, 1452 lines):

```
PENDING RENDER TARGET (what the next fullRender will use)
  viewportLat / viewportLon / viewportZoomFraction / viewportAngle   @Volatile
      written by: setViewport, reCenter, reengageFollow,
                  extrapolationTick's clamp branch

DISPLAYED FRAME (what is on the surface right now)
  overrunBitmap + overrunLat / overrunLon / overrunMag / overrunAngle @Volatile
  blitOffsetX / blitOffsetY                                          @Volatile
      written by: fullRender (commit, under surfaceLock) and
                  blitToSurface (offset, before the draw)

TODAY: drawGpsMarker / drawDestinationMarker project against the PENDING target
       markerScreenPosition() = P(marker; viewportLat/Lon, viewportZoomFraction,
                                        viewportAngle) - blitOffset
       renderFrame's follow blit passes viewportLat/Lon as the "displayed position"
       extrapolationTick's blit offset correctly uses overrunLat/Lon + overrunMag/angle
```

Threading/lifecycle (guidelines/Design.md §4): the renderer owns `CoroutineScope(SupervisorJob() + Dispatchers.Default)`. The extrapolation loop (~11 Hz measured, 33 ms period) and the debounced render loop run in that scope; screen callbacks (`setGpsMarker`, `setViewport`, `setFollowAnchor`, `setHostBottomInset`) are called from the car host's main thread. Bitmap swap, blit and all canvas drawing are serialized by the static `surfaceLock`, and `drawToSurface`/`blitToSurface` hold it for the whole lock/draw/unlock. The fix must not add a lock acquisition to the draw path and must not move the native render inside the lock (a concurrent full render must keep blitting the old frame while it runs).

## Goals / Non-Goals

**Goals**

- One published "displayed frame" that every canvas overlay and the blit geometry agree on: centre, magnification, rotation, blit offset.
- The follow blit is servable for every anchor preset, so a frame centre change inside the overrun region is a blit and the per-fix full native render disappears.
- A logcat-visible diagnostic for the pending-vs-displayed window, so this class of defect is detectable in the field.

**Non-Goals**

- Changing *when* or *where* the follow frame is re-anchored (the per-fix `setViewport` + `reengageFollow` cadence in `FreeDrivingScreen`/`NavigationScreen`, the anchor fraction resolution, the display easing and the movement gate). Those are owned by `auto-smooth-follow` and the in-flight `fix-aa-follow-*` / `anchor-per-surface-visible-area` changes.
- Any change to `FollowPrediction` / `FollowDisplayState` math, to the phone renderer, to settings/persistence, or to JNI/native code.
- Making the anchor presets frame differently; the resolved-anchor semantics stay exactly as the in-flight anchor changes define them.

## Decisions

### D1 — Represent the displayed frame by the fields that already describe it (Alt A)

Alternatives:

1. **Alt A (chosen) — derive from the committed fields.** Add one internal accessor (e.g. `displayedFrame(): DisplayedFrame`) returning `(overrunLat/Lon, overrunMag/angle)` when `overrunBitmap != null`, else falling back to `(viewportLat/Lon, viewportZoomFraction/angle)`. `markerScreenPosition()` and `drawDestinationMarker()` read it inside the same `surfaceLock` critical section that already holds the draw, and subtract `blitOffsetX/Y`.
2. **Alt B — publish an immutable `FrameSnapshot` (data class) at commit and blit time.** Cleanest type-level separation, but it allocates once per blit frame (~11-30 Hz) and duplicates four fields that are already `@Volatile` and already written under `surfaceLock`; the only gain is immutability of a read that is already lock-protected.
3. **Alt C — mirror the phone: a `renderViewport` `StateFlow` emitted per committed frame.** The phone's shape, but an emission per blit frame needs a `StateFlow` write plus a collector hop for data the renderer already owns; the AA renderer draws its overlays in-place instead of through Compose, so there is no collector to benefit.

Chosen Alt A: smallest diff on the hot path, no allocation, no new concurrency primitive, and the fallback preserves today's behaviour before the first frame.

**Refinement the implementation makes explicit (task 3.3):** `fullRender` snapshots the frame parameters once and publishes that snapshot as the committed frame, instead of re-reading the render target afterwards (which the renderer did — once for the native call, once for the diagnostic, once for the label). The native render runs outside `surfaceLock` by design, so the target can move while it is in flight, and a re-read labels the frame with parameters the pixels were not rendered at — the overlays, the blit offset and the diagnostic then all describe a frame that is not on screen. Locking `surfaceLock` across the native render was rejected: it would serialize the extrapolation blit against every render and freeze the display for the render duration, which is exactly what the "render outside the lock, keep blitting" split exists to avoid.

### D2 — The follow blit offset is computed against the displayed vehicle position (Alt A)

Alternatives:

1. **Alt A (chosen) — pass the displayed vehicle position** (the follow display position, or the raw fix when no display exists) as the point `displayOffsetPx` measures. `renderFrame`'s follow branch then yields the vehicle's drift from its anchor in the displayed frame; the frame is anchor-centred, so this is exactly the shift that puts the new centre on the surface centre *and* keeps the vehicle at its resolved anchor fraction.
2. **Alt B — keep passing the frame centre and pass `anchorX = anchorY = 0.5`.** Algebraically equal for an anchor-centred target (`P(display, C_o) − anchorOX == P(C_v, C_o)` when `C_v == anchorCenterFor(display)`), and it is a smaller edit. Rejected: it hides the anchor semantics in a magic 0.5, breaks the documented `displayOffsetPx` contract ("the displayed position, measured from the frame the bitmap was rendered with"), and silently produces a wrong offset if the target is ever not anchor-centred (the clamp branch writes `anchorCenterFor(display)`, but a future caller need not).
3. **Alt C — special-case the anchor away by rendering the frame centre-centred and re-applying the anchor in the blit.** Rejected: that is the double-anchor application the `displayOffsetPx` KDoc explicitly forbids, and it would leave an uncovered strip of surface colour at the overrun margin.

Chosen Alt A. Cross-check for the implementation: with Alt A the offset magnitude becomes the per-fix frame displacement (13.7 m ≈ 17 px at 50 km/h, mag 16, 0.825 m/px), which is inside the 60 px vertical margin, so the blit is served; with Alt B the numbers must come out identical for the anchor-centred case — the new tests assert the *outcome* (no full render), not which argument produced it.

### D3 — Overlays use the displayed frame's magnification and rotation (Alt A)

Alternatives:

1. **Alt A (chosen) — project the overlays with `overrunMag`/`overrunAngle`.** A pending zoom or heading rotation then cannot rotate or rescale an overlay before the frame carrying it lands. Note the extrapolation blit already uses `overrunMag`/`overrunAngle` for its offset, so this also removes an internal inconsistency between the overlay and the content.
2. **Alt B — suppress the overlays while a magnification or rotation change is pending.** Rejected: a marker that blinks off every fix is worse than an error bounded by the pending change, and on the car host a heading-up rotation is pending on most fixes.
3. **Alt C — queue/defer the rotation and zoom until the render commits.** Rejected: it delays the visible heading-up response, changes gesture feel, and duplicates the epoch-based staleness logic the phone solves elsewhere.

### D4 — Diagnostics: extend the existing throttled follow line (Alt A)

Alternatives:

1. **Alt A (chosen) — add the displayed frame centre, magnification/rotation and the render/blit counters to the existing throttled follow log** (one line per ~30 ticks, already guarded by `followLogCount`). Gives a logcat-visible "pending vs displayed" window and makes the render rate measurable without counting `lock OK` lines by hand.
2. **Alt B — a new dedicated throttled log line.** Rejected: another throttle timer for data already assembled in the same place.
3. **Alt C — no diagnostic; rely on the new unit tests.** Rejected: the five prior changes on this seam each shipped tests and still missed the defect; the missing evidence was the runtime frame identity.

## Risks / Trade-offs

- **A pending frame still exists for one render latency (~100 ms debounce + one Cairo render).** → Mitigation: that is now *invisible* by construction (overlays project against the displayed frame), and D2 removes the full render from the steady follow path so the window rarely carries a centre change at all. The remaining pending changes are zoom/rotation, which are full renders by requirement.
- **A viewport write can land *during* a native render** (the render runs outside `surfaceLock`), which would label the committed frame with parameters it was not rendered at. → Mitigation: the frame snapshot (task 3.3); verified by `committedFrameIsLabeledWithTheRenderedParameters` with the new `FakeAutoRenderClient.onRender` hook.
- **The per-fix frame displacement can exceed the overrun margin at very low magnification or after a long render stall** (e.g. a backgrounded host). → Mitigation: the clamp still fires and a full render is performed — correct, just not the fast path. The delta is bounded by `v × fix interval` and the margin is 10 % of the surface per axis; at 50 km/h the margin covers ~44 s of travel.
- **Landing order against the in-flight changes.** `fix-aa-follow-blit-anchor-mismatch` (8/9) and `anchor-per-surface-visible-area` (31/38) edit the same lines and assume the current frame-of-reference; a concurrent edit can silently revert this one. → Mitigation: land this change first or fold their remaining task into it, and merge `guidelines/MapRendering.md` §1.1 in a single edit.
- **Behaviour change for the default centre anchor.** `renderFrame`'s follow branch currently blits correctly only for the centre anchor; after the fix the non-centre presets also blit. The existing pane-band anchor tests (`paneBandBlitOffsetUsesTheResolvedAnchor`) assert the offset geometry, not the render path, so they stay valid; the new tests must pin the render path explicitly.
- **Battery/CPU:** the fix reduces native renders from ~1/s to the overrun-margin cycle. No new work per frame in the overlay path (one extra field read).

## Migration Plan

No data, settings or API migration. Implementation order: publish the displayed frame → switch the overlay projections → fix the follow blit call site → diagnostic → tests → guideline paragraph. Rollback: revert the `AutoMapRenderer.kt` edit (single file, plus the test additions and the guideline paragraph); the phone, the native layer and the persisted state are untouched.

## Verification

Unit tests (`:auto:testDebugUnitTest`, Robolectric, `asyncLoopsEnabled = false` for determinism) — the three that fail before the fix:

1. `markerStaysOnItsContentWhileAFollowReanchorIsPending` — commit a frame, blit with an offset, then `setViewport`/`reengageFollow` to move the pending target **without** letting a render commit; assert `markerScreenPosition()` is unchanged from the pre-write value.
2. `followBlitServedForANonCenterAnchor` — `BOTTOM_CENTER` anchor, committed frame, then a centre change inside the overrun region; assert `renderFrame` blits (overrun bitmap identity unchanged) and no full render occurs.
3. `markerUsesTheDisplayedFrameRotation` — a pending rotation change must not move the marker before the commit.

Plus: `markerRidesTheBlittedContentWithABlitOffset`, `paneBandBlitOffsetUsesTheResolvedAnchor` and the bottom-band anchor tests stay green.

Build: `:auto:testDebugUnitTest` and `:app:assembleAutomotiveDebug -Pandroid.injected.build.abi=arm64-v8a`; both flavors for the release path per `guidelines/Build.md`.

On-device (AA emulator, free driving with a GPX replay, and the navigation view):

```
baseline today (104 s, 50.6 km/h):  105 fixes / 101 full renders  (lock OK lines)
after the fix:                      full renders drop to the overrun-margin cycle
                                    (~1 per 40 s at 50 km/h) and match the fix
                                    count only when the anchor re-anchor leaves the margin
marker:                             no per-fix pop relative to the map content; the marker
                                    holds its road pixel while a re-anchor renders
diagnostic:                         the follow line shows displayed centre == the frame the
                                    blit used; a pending target differs only inside the
                                    render window
```

Note the verification blind spot from `TODO.md` §17: quote the executed-task count and elapsed time for the test run, and clear `auto/build/test-results/testDebugUnitTest` before the run — `./gradlew test` can report success without executing anything.

## Open Questions

None: the remaining decisions (whether the follow frame should re-anchor on the fix or on the display position, and the anchor-preset framing) are explicitly out of scope and owned by the in-flight changes named in the proposal.
