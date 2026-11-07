# Map Frames — how a rendered frame is delivered

This document owns a frame's delivery: what is emitted and how it is described (§12), how the overrun
frame is positioned on the display (§13), and how a rotation gesture's angle is handed over (§14). The
pipeline that produces the frame is `guidelines/MapRendering.md`; the follow path is
`guidelines/MapFollow.md`, the car renderer `guidelines/MapCar.md`. The regression checklist, the
history and the measurements behind these rules are in `guidelines/MapFrames.why.md`.

---

## 12. Front-Buffer Emission

- Emit the finished frame as long as epoch AND magnification match the job.
- **Rule: The emitted bitmap is the overrun front buffer; the emitted viewport is the frame's OWN viewport** (center, mag, angle the pixels were rendered with). The display positions the frame
  with the derived display offset (§13) and every overlay projects against that viewport — never
  against `currentViewport` (the target of a render that has not landed).
- **Rule: Unchanged-frame reuse** compares the front-buffer sequence AND the previously emitted bitmap's
  own dimensions — a canvas-size change (rotation, fold, resize) must always produce a new
  emission, otherwise a stale crop is re-emitted.
- **Rule: Fractional magnification (continuous pinch)**: the committed viewport magnification is a
  `Double` zoom level (z, fractional from continuous pinch commits). Kotlin-internal magnification
  is always a *level-style* value; the JNI render/projection boundary converts to the libosmscout
  *scale factor* `2^z` once (JNI `SetMagnification(double)`). Tile lookups snap to
  `floor(log2(mag))` internally — never round fractional mags at commit time (only discrete
  controls snap via `zoomIn/zoomOut`).
- **Rule: No angle epsilon check against `currentAngle`**: during a slow render `prepareViewport`
  changes the target angle; the finished frame would otherwise be discarded (old cause of "map
  shows old image / jumps"). The next job picks up the new angle.
- Render job snapshots the viewport AND the marker state at enqueue (`PendingRender`); the marker
  snapshot is emitted with the frame (`frameFlow`) — the overlay reads that frame's snapshot per
  frame, so there is no marker/center skew to worry about.
## 13. Overrun Display Window (pan and follow)

The overrun frame is positioned by ONE display offset, derived on the UI thread from the frame that
is on screen. This replaced the per-event sub-region bitmap copy (`trySubRegionBlit` used to create a
shifted crop per pan event) — the copy allocated two bitmaps per touch event, and after commit
`c7e5bc1` it was dropped for a no-shift emission that only follow mode could offset, which left
browse pans with no visual tracking at all (fixed by `fix-phone-gesture-pan-tracking`).

```
displayedCenter   follow: anchorCenter(displayPos, resolvedAnchor)  |  pan: live panned center
      │
      ▼
OverrunWindow.shiftPx(displayedCenter, frameViewport, …, anchor = center)
      │  rotated delta (dx*cos − dy*sin, dx*sin + dy*cos), clamped to the overrun margin
      ├── drawFrontFrame draws the frame at baseCenter − displayOffset
      └── map-anchored overlays translate by (follow ? 0 : −displayOffset)
```

- The offset MUST be rotated by the viewport angle. Without rotation, a shift at -40° map angle
  moves the content horizontally by up to `sin(40°) × move` wrongly.
- **Rule: The overrun margin is never negative.** A frame that offers no overrun margin on an axis — for
  example a frame that still carries the previous orientation right after a rotation while the canvas
  already has the new one — yields a ZERO offset on that axis, reported as clamped
  (`OverrunWindow.shiftPx` coerces its margin with `coerceAtLeast(0.0)`), so the surface
  requests a frame in the current orientation instead of constructing an empty coercion range and
  killing the process (`TODO.md` §153).
- Exactly ONE displayed center: derive the offset from the frame in hand, never from the render
  target. The pan path keeps the displayed center as display-only screen state (`panDisplayLat/Lon`)
  and holds it until a frame carrying THAT center lands (`PanWindowRules.holdReleased`, released only
  after the gesture ended); the follow path uses its predicted display position.
- **Rule: Pan inside the margin renders nothing.** The pan callback must not call `renderMap()` per event:
  the display follows the finger for free. A render is requested only when the offset CLAMPS at the
  margin (throttled), plus once at gesture end when the window is saturated or no frame exists.
- The covered branch of `submitDebounced` is a PURE PREDICATE (`overrunWindowCovers`) that drops a
  pending non-forced render and returns — no emission, no bitmap work, no pixel copies. It never
  takes `bufferLock` and never enters the native render path.
- **Rule: Coverage and display must describe ONE frame: the emitted one.** `overrunWindowCovers` reads the
  frame snapshot the display holds — `MapRenderer.EmittedFrame` (emitted bitmap dimensions + the
  viewport published with it, one immutable object, one volatile write per emission) — never the
  renderer's private front-buffer fields, and it reports an UNSERVABLE window (no emitted frame yet, a
  frame without an overrun margin, another magnification or rotation) as **not covered**. Sharing only
  the math (`OverrunWindow.shiftPx` + `BLIT_COVER_SLACK_PX`) was not enough: the two sides
  could still read different frames — the class of bug that froze the pan on device (`fix-phone-
  gesture-pan-tracking` follow-up). The pan hold is the other half of the contract: it may only be
  released for a frame that already carries the DISPLAYED center and never while the gesture is live,
  or the display applies a zero offset for a moved center (the observed dead pan).
- A zoom or an angle change is never covered by the frame in hand (wrong magnification/rotation).
- **Rule: Diagnosing a stuck pan** (bounded output: at most one line per gesture, `MapCanvasScreen` tag
  `pan end: window=served|clamped|unavailable offsetPx=… mag=…`, plus one `pan: no usable frame
  window` per rejection): `window=unavailable` means the frame in hand cannot serve the window and a
  render was requested — check the render path next; `window=served` with `offsetPx=0,0` while the
  finger moved means the display released the hold (or drew the offset nowhere) — neither state is
  reachable while the gesture is live, so the line plus the renderer's gated request/drop lines name
  the stuck side. No per-event pan logging exists by design.
- **Rule: The marker overlay must be shifted by the same offset the content is drawn with** — in follow
  mode the anchor-center projection already carries it (translate there = double application).
- A covered request is a TILE-PREVIEW-class optimization only: it MUST NOT discard a pending FORCED
  render (`forceFullRender=true` — route set/clear, favorites, search selection, stylesheet switch,
  epoch bump). Discarding it would leave the new overlay undrawn until a gesture triggers a full
  render (observed symptom: stale route on the map after a reroute until the user pans). Keep the
  pending render (`pendingRender?.forceFullRender != true` guard) so overlay changes render even when
  the camera never moved.
- Blit/window content is pure map content — the marker overlay is drawn by Compose on top afterwards,
  so a shifted window can never carry stale marker pixels.
- Tile-path rotated composition MUST rotate about the viewport center (tiles placed north-up,
  `canvas.rotate(deg, W/2, H/2)`), NEVER about each tile's own corner — corner pivots shift
  content by up to `d·θ` (d = tile distance from center, θ = rotation) and break marker-overlay
  alignment.
- **Rule: The renderer's target center (`currentLat/Lon`) only advances with render requests.** After a pan
  that stayed inside the margin it lags the viewport until the next request — harmless: the display
  offset is derived from the frame in hand and the viewport, so the content stays where the finger
  left it, and the next request (any render, a forced overlay render included) re-targets the center.
- **Rule: Pan hot path budget** a pan event moves display-only state only — no UI-state copy, no
  recomposition of the screen, no allocation, no logging; gesture-start side effects (follow
  disengagement, attribution tick) run once per gesture, and render requests are throttled
  (`PAN_RENDER_REQUEST_THROTTLE_MS`).

---
## 14. Rotation Gesture Display-Layer Handoff

- The live rotation angle during a two-finger gesture lives in the graphicsLayer
  (`rotationZ = gestureRotation`), NOT in the rendered bitmap (which stays at the committed
  angle until gesture end).
- The rotation AND zoom pivot at the FINGER MIDPOINT (the graphicsLayer `transformOrigin` =
  `gesturePivot`): the geographic point under the midpoint at gesture start stays under the
  midpoint for the whole gesture, and the gesture-end commit adjusts the viewport center so the
  anchor holds in the rendered frame (`ProjectionUtils.rotateZoomAtFocalPoint` — the
  generalized focal-point commit; Δ=0 reduces to `zoomAtCursor`).
- Rotating around an off-center pivot can expose empty corners at large angles (accepted — the
  overrun buffer margin covers moderate angles; standard map-app behavior).
- At gesture end the committed angle is set and a full render is requested, but the render is
  async (debounce + background job). The display MUST keep the final angle applied until the
  front buffer swaps to the committed angle — otherwise the old bitmap (old angle) shows
  unrotated for the render duration and the map temporarily jumps back to the pre-gesture
  angle (observed symptom: rotate → map snaps back → restores).
- The hold is a FLAG (`rotationHoldActive` in `MapCanvasScreen`); the held ANGLE is derived per draw as `committedAngle − frontBufferAngle` (`rotationDisplayTheta`) from the SAME collected state the draw reads — so it zeroes atomically with the committed bitmap swap and there is no one-frame window that draws the new bitmap with a stale rotation. A STORED hold value cleared by the frame loop is wrong: the clear (direct `uiState` read) and the draw (collected state) can straddle the render-land emission and show a double-rotation twist for one frame.
- At render land the old rotated frame is crossfaded into the new native render (mirror of the zoom crossfade, `crossfadeAngle`), masking tile-vs-native rasterization differences; the old frame is drawn rotated about the gesture midpoint (`drawFrontFrame` `rotationDegrees`/`rotationPivot`), which equals the committed render's pivot.
- The hold disarms when the front buffer reaches the committed angle or on the next gesture start. The derived gap composes independently with the zoom handoff: `rotationZ` and `scaleX/Y` are applied together in the graphicsLayer and each clears on its own condition.

---
