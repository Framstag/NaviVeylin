# Proposal

## Why

The phone and Android Auto animate the same magnification change with two different
mechanisms, and the phone's one is visible as a **wrong-scale picture**: from the commit
until the render at the target lands, the phone shows the OLD magnification raster scaled
across the whole gap, so old road widths, old label sizes and old label density are on
screen and the geometry is only correct after the render swap. Android Auto walks the same
change as a sequence of real renders and therefore never shows a wrong-scale frame.

Reported from a phone/AA comparison: *"in AA the zoom in seems to be perfect spot on the
vehicle. In phone the zoom during the animation is somehow more static and fixed and
perspective etc. are only corrected after the zoom animation is finished."*

```
ENTRY / AUTO-ZOOM COMMIT, 4 LEVELS (13.0 -> 17.0 = 16x area)

PHONE - app/src/main/java/com/naviveylin/ui/map/MapCanvasScreen.kt
  commit mag 17.0 commits the viewport, the front buffer still holds mag 13.0
  target scale = 2^(commit - front)                                 (:315, core ZoomAnimation)
  -> Compose display layer scales THE ONE OLD BITMAP about the canvas CENTER
       scale(zoomScale, zoomScale, pivot = zoomAnchor)               (:2109)
       zoomAnchor = canvas center for EVERY auto-zoom commit         (:326-340, :2067 = 650 ms)
  -> no native render while the animation plays (spec smooth-zoom: the render at the
     TARGET is queued), so no frame carries intermediate geometry
  -> the target render lands (100-300 ms), the frame swaps, the scaled old frame
     crossfades out over CROSSFADE_MS = 150 ms                       (:2050)
  => between the commit and the landing the screen shows the mag-13 raster blown up
     (up to 16x for a 4-level entry), then snaps to the render

ANDROID AUTO - auto/src/main/java/com/naviveylin/auto/AutoMapRenderer.kt
  advanceZoomWalk() commits ONE step per LANDED render               (:869-897)
  step <= ZOOM_BLIT_LIMIT = 0.25 mag                                 (:1991)
  every step is a real native render (commitZoom -> requestRender)    (:901-907)
  the displayed scale is clamped to the blit window and snaps outside it (:1050-1086)
  13.0 -> 17.0 = ~16 renders, each frame real geometry, <= 1.19x resampling
```

Three concrete differences fall out of that, all verified in the code:

1. **Content fidelity.** The phone resamples one bitmap across the whole gap with no
   bound (`core/src/main/java/com/naviveylin/core/ZoomAnimation.kt` has no blit-window
   clamp; the target is the full `2^(commit - front)`). AA bounds every displayed frame to
   `ZOOM_BLIT_LIMIT` and refuses to scale outside that window. This is the reported
   "perspective only corrected after the animation is finished".

2. **Anchor.** The phone pivots every auto-zoom animation on the canvas center, while
   follow framing places the vehicle at the resolved follow anchor
   (`com.naviveylin.core.resolveAnchorFraction`, `VehicleAnchorPosition` grid). With the
   factory default preset `CENTER` (0.5, 0.5) the two pivots coincide, so the phone IS
   anchored on the vehicle today; on any non-center preset the whole scene (content AND
   marker, which scales around the same pivot - `LocationMarkerOverlay.kt:67,132`) leaves
   its anchor slot by `(1 - s) * ((fx - 0.5) * W, (fy - 0.5) * H)` and snaps back when the
   render lands (bottom-center at s = 2 = 0.4 * H). AA pivots on the resolved follow anchor
   and renders the frame anchor-centered on the vehicle, so it is anchored by construction.

3. **Coverage.** Unclamped zoom-out scales a 1.2x overrun frame below the surface size, so
   a large zoom-out commit can expose bare surface background for the animation's duration.
   AA rejects exactly that path ("a zoom-out past the rendered area" - `:1058-1061`).

Why now: the mechanisms were deliberately allowed to differ (`guidelines/UI.md:36` keeps the
mechanism out of the parity contract and `guidelines/MapRendering.md:159-181` documents the
car walk), but the phone side keeps failing the observable half of that contract - a frame
whose magnification differs from what is displayed is exactly what "no single-frame jump"
and "anchored" are supposed to rule out. The car entry transition was just fixed for the
same class of defect (`aa-entry-zoom-animation`), so the two surfaces are now visibly
divergent on the same gesture.

## What Changes

- **A displayed frame never shows a magnification farther than a bounded window from the
  scale it is drawn at.** The window is the overrun margin the frame in hand can serve; a
  larger magnification change is applied as a sequence of displayed steps, each served by a
  real render, instead of one resampled bitmap across the whole gap. This is the behavior
  AA already has.
- **An active follow mode anchors the zoom animation on the resolved follow anchor**, not on
  the surface center: the vehicle marker keeps the same screen pixel for every frame of the
  animation, on every anchor preset, phone and car alike.
- **The animation never uncovers surface background.** The displayed scale stays inside the
  region the frame in hand actually covers, or the display falls back to the rendered frame
  at the committed magnification.
- **The auto-zoom entry stops being a special case of the display animation.** Today the
  first commit jumps to the speed target (`MapCanvasViewModel.kt:1274`) and the display
  animation covers the entire jump; it becomes a walked change like the car's, so the
  per-frame magnification error is bounded on the entry too.
- Unchanged: the speed-to-magnification table and its convergence (0.5 levels per update,
  epsilon deadband), manual-zoom suspension and re-engagement, the persisted viewport
  (which keeps the target magnification throughout - `smooth-zoom`), pinch/rotation
  semantics, every Android Auto behavior, and the car walk itself.
- **Not BREAKING**: no API, settings, persistence, manifest or resource change; the phone's
  zoom animation becomes longer (a walked change takes as long as its renders take, the way
  the car's does) - see Open Questions for the cheaper alternatives.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `smooth-zoom`: (a) the "Eased zoom animation on discrete zoom input" requirement gains the
  bounded-deviation rule - the displayed frame's magnification never differs from the scale
  it is drawn at by more than the frame's serviceable window, and a larger change is applied
  as a sequence of rendered steps; (b) the "Geographic anchor stays fixed during zoom
  animation" requirement gains the follow-anchor rule, so an active follow mode anchors on
  the resolved follow anchor instead of the surface center; (c) a new requirement - the
  animation never exposes uncovered surface background.
- `auto-speed-zoom` is **not** modified: the car walk stays as it is, and it already
  satisfies all three requirements above.

## Impact

**Code (phone only):**

- `app/src/main/java/com/naviveylin/ui/map/MapCanvasScreen.kt` - the zoom-animation anchor
  (`animateDiscreteZoom` / `animateDiscreteZoomToCenter`, the auto-zoom `LaunchedEffect`),
  the draw-side scale (`drawFrontFrame`), the render-land handoff and its crossfade, and the
  render requests the animation makes.
- `core/src/main/java/com/naviveylin/core/ZoomAnimation.kt` - the display-scale model: the
  window bound and, if the walked variant is chosen, the step/target bookkeeping. Pure
  Kotlin, host-JVM testable.
- `app/src/main/java/com/naviveylin/ui/map/MapCanvasViewModel.kt` - only the first
  auto-zoom commit (`:1274`) if the entry stops jumping; the render-request cadence the
  walked variant needs.
- `app/src/main/java/com/naviveylin/ui/map/LocationMarkerOverlay.kt` - unchanged behavior,
  but the marker compensation (`applyZoomAnchorScale`) is what proves the anchor rule in
  tests, so it is touched by test coverage.
- Tests: `ZoomControlsAnimationTest`, `LocationMarkerOverlayTest`, `FollowAnchorFramingTest`,
  `MapCanvasViewModel`/follow tests, plus new cases for the walked step bound, the
  follow-anchor pivot and the uncovered-area rule.
- Android components: no manifest, resource, Gradle, DI or template change.

**Native / JNI:** none. No libosmscout submodule patch and no `:osmscout-client-java`
override - the change stays in Kotlin.

**Android Auto:** no `:auto` file changes. The car keeps its walk; it is the reference
behavior this change brings the phone to.

**Guidelines affected:**

- `guidelines/UI.md:36` (phone/AA zoom parity row) - the mechanism note stays, but the
  observable contract ("no single-frame jump, anchored, ends on an exact render") has to
  name the follow anchor and the bounded window, since today it is only true for the car.
- `guidelines/MapRendering.md` - section 11 (Auto-Zoom) and section 12 (Front-Buffer
  Emission) describe the phone's ~650 ms front-buffer animation as the end state; section 13
  already states "on zoom change keep the old correct frame, don't show a scaled
  placeholder", which the phone's display animation contradicts over large gaps.

**Rollback:** revert the phone-side Kotlin edits; the animation falls back to the current
single-scaled-bitmap behavior, and the spec delta reverts with it. Nothing is persisted, so
no data or settings migration is involved.

**Scope:** phone only. Android Auto, the shared `SpeedZoomTable`, the anchor presets and the
settings surface are out of scope; the car is used as the behavioral reference.

## Open Questions

1. **How the bounded window is achieved on the phone decides the cost.** The requirements
   above hold for all three variants, but they are very different changes:

   - **Walk the displayed scale (full parity with the car).** Same rule as
     `advanceZoomWalk`: a step per landed render, each within the blit window, ending on a
     render at the exact target. Consequence: an entry costs ~16 renders instead of one, and
     the animation lasts as long as those renders take (1.5-4 s on the car). This is the only
     variant in which the map keeps showing real geometry *throughout* the change, which is
     what the report asks for.
   - **Clamp the displayed scale and land the remainder in one render.** Consequence: the
     resampling error is bounded (~1.19x), coverage can never break, one render is still
     enough - but a large change is a short capped ramp followed by a jump, i.e. it stays
     "scale, then correct", just with a much smaller error. Cheapest option, does not fully
     remove the reported artifact.
   - **Fix only the anchor pivot.** Consequence: the vehicle no longer slides off its slot
     (real defect on non-center presets), but the wrong-scale raster remains for the whole
     gap. Smallest change, does not address the report's main symptom.

   Recommendation: the walked variant, because the report is about the map content rather
   than about the anchor, and the car already pays that cost on the same transition. If the
   render count during an entry is a concern, the clamp variant is the fallback, at the price
   of keeping a visible correction at the end of large changes.

2. **Does the phone keep animating at all for changes inside the window?** The car keeps the
   single-eased-blit path for a delta inside `ZOOM_BLIT_LIMIT` (no walk, no extra render).
   Adopting the same split on the phone means the animation duration becomes
   delta-dependent (a large change takes longer than 650 ms). Confirming that this asymmetry
   is acceptable is a UX decision; the alternative is a fixed duration with fewer, larger
   steps, which weakens the bounded-window requirement.
