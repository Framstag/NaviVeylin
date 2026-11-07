# MapFrames — the rationale

This is the rationale layer of `guidelines/MapFrames.md`: the regression checklist and the dated
measurements behind the delivery rules the base document states. An anchor here names a section number
the base document carries.

## §13 Overrun Display Window (pan and follow) — the regression checklist

## Known Pitfalls (Regression Checklist)

When "map jumps" / "marker wrong" appears, check first:

1. Does the emitted frame have the OVERRUN dimensions (screen × 1.2) and carry its own viewport? →
   the display draws it at the derived display offset (§13); a screen-sized crop or a frame described
   by another viewport shifts or scales the map wrongly.
2. Does the emitted frame share memory with `frontBuffer`? → next `setPixels` corrupts the
   displayed image (horizontal jumps without rotation).
3. Angle unnormalized (> π)? → grows across frames, apparent rotation jumps.
4. Is `epoch` incremented on pure GPS move? → running renders get discarded.
5. Does `smoothCourseBearing` return `-1.0` instead of NaN? → drift north after reset.
6. Turn-reset threshold too high (> 45°)? → course mixes old/new direction after corners.
7. Marker uses smoothed instead of fresh course? → arrow points in old direction after corners.
8. Blit delta without rotation matrix? → lateral offset on rotated map.
9. Center smoothed? → marker drifts off the road.
10. Rate limit < 90°/frame? → map rotates too slowly after corners.
11. Marker baked into cached tiles / reused front buffer (ghost marker artifacts after it moves)?
    → marker must be a Compose overlay; tiles/buffers must contain only map content.
12. Overlay projecting against `currentViewport` instead of the displayed frame's viewport? → marker
    misplaced during pan/zoom/rotate gestures; always use `uiState.renderViewport`.
12a. Pan window applied to the map content but NOT to the map-anchored overlays (or applied twice in
    follow mode)? → the marker rides off the content. The overlays translate by the SAME clamped
    display offset the frame is drawn with (`markerDisplayShiftPx`), and not at all in follow mode
    where the anchor-center projection already carries it.
12b. Pan tracked by per-event renders instead of the display window? → content moves only when a
    frame lands (50 ms debounce + render), pans inside the overrun margin never move at all, and the
    committed viewport drifts away from the displayed content (a later render then jumps).
12c. Pan frozen for the whole gesture, moving nothing at all? → the display window was released while
    the finger was still down: the hold may only end for a frame that carries the DISPLAYED center
    (`PanWindowRules.holdReleased`, never committed-vs-frame) and never while a gesture is live. The
    companion failure is a coverage predicate reading the renderer's own frame instead of the emitted
    one — it drops the request as covered for a window the display cannot shift, so nothing renders
    and nothing moves (`MapRenderer.EmittedFrame`, spec `canvas-overrun` — Coverage and display never
    disagree into a freeze). Confirm with the `pan end:` line (one per gesture) plus the renderer's
    gated drop lines, not with per-event logging.
13. `setGpsMarker`/`clearGpsMarker` or native `gpsMarker` state re-introduced? → forbidden: the
    marker renders exclusively via `LocationMarkerOverlay`.
14. Tile-path rotation pivots on each tile's own corner? → marker overlay (projected about the
    viewport center) diverges from the map by up to `d·θ` — rotate about the viewport center.
15. Overlay fed the LIVE GPS fix instead of the frame marker snapshot? → marker jumps ahead of the
    road by up to one fix of travel while frames lag; always draw the snapshot that rode with the
    displayed frame (`frameFlow`).

---

- **Log BOTH sides of a seam in one grep-able line** (§40.57): pending vs displayed frame, both path
  counters, the requested vs applied magnification. Five changes shipped while the AA follow defect was live
  because no log line compared the two frames — if two layers can disagree about a number, one line must
  carry both.
- **Write down which coordinate frame each number lives in before comparing them** (§40.59) — pre-shift
  projection vs post-shift visible band, frame pixels vs surface pixels, raw scale vs level. A comparison
  across frames reads as a defect in the code under test and costs a review round to disprove.

