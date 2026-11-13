# Design

## Root cause

`LocationMarkerOverlay` projects the fix once and, when `projectMarker` returns `null` (the position is
beyond `MARGIN_PX = 100` of the frame, `LocationMarkerOverlay.kt:126-129`, `:254`), returns from the
`Canvas` draw scope without painting anything (`:66-67`). The overlay therefore has one off-screen
branch and it is empty, and the shipped requirement states that emptiness as the contract
(`openspec/specs/gps-location-marker/spec.md:120-123`). The mechanism is single: no cue value is
derived and nothing is drawn for a position the map band has scrolled off.

## The single fix

One derived value and one draw, on the branch that today draws nothing:

- `projectMarkerRaw(...)` — the projection without the margin cull; `null` only for a NaN projection.
  `projectMarker` keeps its contract (raw + the `MARGIN_PX` cull) so its four shipped cases stay
  untouched, and it is what decides "the marker is drawn".
- `edgeCuePlacement(pos, screenW, screenH, insetLeftPx, insetTopPx, insetRightPx, insetBottomPx,
  radiusPx): EdgeCue?` — pure. It returns `null` when `pos` lies inside `0..screenW × 0..screenH`
  (nothing to cue), otherwise:
  - the *inset bounds* are the frame shrunk by the band's reserved columns and by the cue's own
    radius: `left = insetLeft + r`, `top = insetTop + r`, `right = screenW - insetRight - r`,
    `bottom = screenH - insetBottom - r`;
  - the origin is the centre of those bounds (not the frame centre — on a foldable the reserved right
    column moves the usable area);
  - the cue sits where the ray origin → `pos` meets those bounds (`t = min` over the two axes), and is
    then coerced into them, so a degenerate inset pair still yields an in-frame position;
  - the trigger is the marker's own off-screen state (`projectMarker` culled at `MARGIN_PX`), which is
    why the modified requirement's off-screen `WHEN` names that margin: the arrow keeps the entry band
    it has today and the cue takes over where the arrow can no longer reach into the frame, so the two
    glyphs are mutually exclusive by construction;
  - `pointingDegrees = degrees(atan2(dx, -dy))`, the same 0°-is-up screen convention the marker arrow
    uses (`ProjectionUtils.screenBearing` / `rotate(degrees = bearingDegrees, pivot = Offset.Zero)`).
- `drawEdgeCue(cue, dark)` — a small filled chevron (`CUE_RADIUS_DP = 12.dp`) rotated to
  `pointingDegrees`, painted in the vehicle marker's own palette values
  (`VehicleMarkerGeometry` casing + light/dark gradient top), so the cue reads as the same element
  family as the arrow and follows the presentation branch the marker already resolves. It is drawn
  inside the same `LocationMarkerOverlay` `Canvas`: one overlay element, one projection per frame.
- The composable gains the four inset parameters with `0` defaults. The call site
  (`MapCanvasMapBand.kt:667`) passes `viewModel.overlayInset*Px` — the values
  `MapCanvasViewModelFollow.kt:55-58` already feeds `resolveAnchorFraction`, i.e. the columns the band
  reserves for chrome. The band already observes the view state whose publication moves with those
  insets (`publishResolvedAnchor` writes `resolvedAnchor`), so the cue sees the same numbers the
  framing does.

## Alternatives the evidence eliminated

- **Retire the item, keep the re-center button** (the item's option B) — rejected by the recorded
  decision of 2026-11-08T16:19:33Z: it leaves the "where is the vehicle" gap open in BROWSE, which is
  what the item exists for.
- **A second `Canvas`/layer above the marker overlay** — rejected by the recorded decision's
  sub-question (i) ("a Compose overlay element on top of `LocationMarkerOverlay`, not a new canvas"): a
  second layer would project the same fix twice per frame and add a layer to
  `MapCanvasScreen`'s stack, whose order is a tested contract (`guidelines/UIPhoneMap.md`, section 11
  "Phone overlay layering (the band stack)", asserted by `MapLayerBandsTest`).
- **A car-side cue in this change** — impossible without a native change: the car marker is drawn by
  `AutoMapRenderer` on the host surface, so a cue there is a separate item (recorded decision,
  sub-question (iii)). Named as the platform constraint behind the deviation in the delta.
- **Reusing `drawCompassArrowWithShadow` for the cue** — impossible without a second orientation
  source: that function paints the vehicle's *travel*-direction arrow (38 dp, bearing-based), while the
  cue points along the viewport-centre → vehicle ray. Reusing it would either draw a 38 dp arrow at the
  edge or make one function answer two questions.

## Open decisions

none

The item's recorded decision of 2026-11-08T16:19:33Z settles the fork (add the cue and amend
`gps-location-marker`), its sub-questions (i) a Compose overlay element on the marker layer, (ii) the
inset-bounds clamp, (iii) phone now and the car cue as its own native item.

## Blast radius

- `LocationMarkerOverlay.kt`: two new helpers, one draw function, four defaulted parameters, one new
  branch in the `Canvas`. `projectMarker`, `MARGIN_PX`, the accuracy ring, the arrow geometry and the
  palette are untouched.
- `MapCanvasMapBand.kt`: one call site gains four arguments.
- No new state, no view-model field, no snapshot read, no log line, no resource, no manifest, no
  dependency, no `:auto` change.

## Threading and lifecycle

Composition and draw only: the same main-thread `Canvas` draw the marker already runs in, no coroutine,
no dispatcher hop, no `LaunchedEffect`, no state that outlives the frame. The cue value is derived from
values the composable already receives, so nothing has to be remembered between frames.

## Verification

- `app: LocationMarkerEdgeCueTest` (new, Robolectric `@GraphicsMode(NATIVE)`) — the red case measures
  the drawn pixels at the east edge for an off-screen fix; the three green cases exercise
  `edgeCuePlacement` directly (edge + direction, foldable inset clearance, no cue on screen).
- Revert-check: the single mutation is deleting the inset subtraction from the clamp; the foldable
  clearance case must go red, then green after the restore (`-PforceTests --no-build-cache`).
- Regression: `app: LocationMarkerOverlayTest` and `app: LocationMarkerOverlayPaletteTest` — the
  projection, the rotation convention and the drawn ring must be unchanged.
- On-device: a stationary emulator cannot put the vehicle outside the viewport in BROWSE, so the
  pixel measurement is the Robolectric raster named above rather than a screenshot; the diagnostics
  log gains nothing (a cue position is a pixel, and no new line is added).
- Structural checks: `tools/check-change-readiness.sh openspec/changes/fix-off-screen-vehicle-cue` and
  `tools/check-doc-routes.sh` after the `MapFollow.md` §8 edit.

## Rollback

Revert the two production hunks and the case file: the overlay draws nothing for an off-screen
position again, the red case goes red again, and the delta spec is removed with the change; no shipped
behaviour, stored value or API outside this overlay depends on the cue.

## Risks

- **A cue that hides under the action column on a wide foldable** — the reason the clamp is expressed in
  inset bounds and not in frame bounds; the foldable case pins it.
- **Two glyphs at the boundary** — impossible by construction: the cue branch is taken only when
  `projectMarker` returns `null`, so the arrow and the cue are never drawn for the same frame.
- **A cue the margin swallows** — a position between the viewport edge and `MARGIN_PX` past it draws
  neither glyph visibly (the arrow is already off-frame, the cue not yet placed); this is unchanged
  behaviour and the reason the requirement's `WHEN` names the margin rather than the bare viewport.
- **A degenerate inset pair** (insets that exceed the frame) — the clamp coerces into the bounds and the
  degenerate case yields the bounds' centre rather than a NaN; the pure case would fail on a `NaN`
  position.
