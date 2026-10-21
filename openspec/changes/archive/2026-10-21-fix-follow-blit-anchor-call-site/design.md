# Design — the follow blit anchor is proven at its call site

## Root cause

`MapCanvasScreen.kt:817` computes the follow display offset inline inside the per-frame loop
(`LaunchedEffect { while (isActive) { withFrameNanos { … } } }`). The anchor fraction is one of the
arguments of that call, chosen by the screen, and the resulting `DisplayOffset` is a local
(`followOffsetX/Y`) consumed immediately by a `graphicsLayer` translation. Nothing publishes it, and the
loop never becomes idle, so no test can drive the block (`MapNavColumnWidthProbeTest`'s header records the
same constraint for the whole screen). The only coverage of the geometry is
`FollowAnchorFramingTest`, which calls the helper with fractions it constructs itself — including a
regression case that asserts the *defect* geometry by handing the raw preset in. Which anchor the
production call site passes is therefore established by inspection, and inspection is what the
2026-09-27 pair of fixes (spec `smooth-follow`, `fix-follow-vehicle-jumps` then
`fix-follow-phone-blit-anchor-mismatch`) did not deliver: the second defect was invisible to the suite.

## The single fix

One new production file and one call-site rewrite:

- `app/src/main/java/com/naviveylin/ui/map/FollowBlitRules.kt` — an object in the shape of the existing
  `PanWindowRules` seam:

  ```kotlin
  internal fun offsetFor(
      ui: MapCanvasUiState,
      frame: PanWindowRules.Frame,
      displayedLat: Double,
      displayedLon: Double,
      canvasW: Int,
      canvasH: Int,
      dpi: Double
  ): FollowPrediction.Companion.DisplayOffset
  ```

  It reads `ui.resolvedAnchor.fx/fy` itself and calls `FollowPrediction.displayOffsetPx`. Taking the whole
  state — not an `anchor` argument — is the fix, not a style choice: no caller can pass the raw preset as a
  fraction. A caller that rebuilds the state around it
  (`ui.copy(resolvedAnchor = ResolvedAnchor(ui.activeFollowAnchor.fx, ui.activeFollowAnchor.fy))`) is refused
  by the call-site guard, which reads the code around the seam call. `frame` is `PanWindowRules.Frame` (pure
  data: viewport + bitmap dimensions), so the seam needs no Android or Compose types and its geometry case
  needs no bitmap.
- `MapCanvasScreen.kt:817` — the inline call becomes `FollowBlitRules.offsetFor(ui, PanWindowRules.Frame(vp.lat,
  vp.lon, vp.mag, vp.angle, bitmap.width, bitmap.height), followDisplayLat, followDisplayLon,
  canvasSize.width, canvasSize.height, dpi)`. The surrounding guard (`bitmap != null && vp != null &&
  canvasSize.width > 0`), the clamp, the refresh policy and the diagnostics are untouched.

Two cases hold the tree to it:

1. `FollowBlitCallSiteTest` (plain JUnit, reads the two sources the way `QualifierUseSiteTargetTest` reads
   the module's sources): the screen calls the seam exactly once, calls `displayOffsetPx` zero times, and
   the code around the seam call names `activeFollowAnchor` zero times (the screen legitimately reads the
   preset elsewhere, in the settings sheet's mirror); the seam reads `resolvedAnchor` once,
   `activeFollowAnchor` zero times and still calls the shared helper. The needles are assembled from parts
   so the file's own text is not a hit if the surface ever grows.
2. `MapCanvasViewModelVehicleAnchorTest#followBlitOffsetTakesTheResolvedAnchorNotTheRawPreset`: a real
   ViewModel with `BOTTOM_CENTER` (raw `fy = 0.9`) and the navigation overlay insets measured
   (`setMapOverlayInsets(top = 400, bottom = 400, right = 80)`) publishes `resolvedAnchor.fy ≈ 0.76`; the
   case asserts the persisted preset (`activeFollowAnchor.fy == 0.9`, so a lost settings file's `0.5`
   fallback cannot make the two anchors collapse), takes the frame from the production
   `followRenderTarget(...)` rather than constructing it, and with the display drifted 40 m north the
   seam's offset is unclamped and equals the resolved-anchor computation, while the raw-preset computation
   of the same geometry is clamped — the per-500 ms churn the item names. It prints both numbers, so the
   XML's `system-out` carries them.

`guidelines/MapRendering.md` §1.1 gains the seam's name and the guard, next to the statement it already
carries.

## Blast radius

- Phone follow display path only: one composable's offset computation. The offset's value is unchanged —
  `ui.resolvedAnchor` is what the code passed before — so no rendering behaviour moves.
- `FollowBlitRules` is `internal` to `:app`; no module boundary, no DI, no state, no threading change.
- The car path (`AutoMapRenderer`) has its own `resolvedFollowAnchor()` seam and is not touched.
- No new dependency.

## Rollback

Revert the three files: the call site returns to the inline `FollowPrediction.displayOffsetPx(…,
ui.resolvedAnchor.fx, ui.resolvedAnchor.fy)`, the seam file is deleted and the two cases are removed. The
pipeline's behaviour is identical either way, so rollback is a pure loss of the proof, not a change of
framing.

## Alternatives the evidence eliminated

- **Drive the follow display block from a Compose test.** The offset is a local of a `withFrameNanos` loop;
  nothing in `MapCanvasUiState` carries it, and the screen's loop never idles (recorded in
  `MapNavColumnWidthProbeTest`). Observing it would mean publishing a diagnostic field into the UI state
  for the test's benefit alone — a production surface with no user, and still not the call site's anchor
  choice but a copy of it.
- **Keep the inline call and add a grep-based guard only.** A text guard alone cannot prove the *value*: it
  can only assert a spelling, and the next caller can satisfy the spelling while computing the fraction
  from the preset. The seam makes the wrong value unexpressible; the guard only holds the seam in place.
- **Pass `resolvedAnchor` as a parameter to the seam.** That is the current shape with a new name: the
  caller still chooses, and the regression remains expressible. Eliminated by the same measurement that
  motivates the item.
- **Assert the anchor through `followRenderTarget()`.** It already uses the resolved anchor and is covered;
  the blit is a separate call site, which is exactly the gap. The geometry case does take its frame from
  `followRenderTarget()`, so the WHEN is production code; but the render target alone cannot prove the blit
  consumes that same value, because a test can still hand the helper a different fraction.
