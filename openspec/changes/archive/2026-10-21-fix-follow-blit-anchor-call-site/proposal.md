# fix-follow-blit-anchor-call-site

## Why

Issue #18 (`§111`, class: proof gap of `anchor-per-surface-visible-area` and `fix-follow-vehicle-jumps`):
the phone follow blit anchor is proven only as pure geometry.

Verified on the base commit `2e732f0e00fa771d04569483ffe80bc2ca3fa974` on 2026-10-21.

**The condition still holds.** `app/src/main/java/com/naviveylin/ui/map/MapCanvasScreen.kt:817` computes the
follow display offset inline, inside the `LaunchedEffect`'s `while (isActive) { withFrameNanos { … } }` loop:

```kotlin
val offset = FollowPrediction.displayOffsetPx(
    followDisplayLat, followDisplayLon,
    vp.lat, vp.lon, vp.mag, vp.angle,
    bitmap.width, bitmap.height,
    canvasSize.width, canvasSize.height, dpi,
    ui.resolvedAnchor.fx, ui.resolvedAnchor.fy
)
```

The value passed is correct today (production was fixed by `fix-phone-follow-blit-anchor-mismatch`), but the
anchor argument is the caller's free choice and no case observes it. `FollowAnchorFramingTest`
(`app/src/test/java/com/naviveylin/ui/map/FollowAnchorFramingTest.kt`) drives `FollowPrediction.displayOffsetPx`
and `anchorCenter` with the fractions it is *handed*; its own regression case
(`blit against the raw preset while frame and marker use the resolved anchor leaves the marker off the
content`) asserts the *defect geometry* by handing the raw preset in. `FollowBlitRules.kt` does not exist;
`grep -c "FollowPrediction.displayOffsetPx(" MapCanvasScreen.kt` → `1`, `activeFollowAnchor` is read at
`MapCanvasScreen.kt:2122` and `:2360` outside the offset path.

**Root cause, one sentence:** the follow blit offset is computed inline in a composable's frame loop, so the
anchor the call site chooses is a local of a `withFrameNanos` block nothing can observe, and the only
coverage hands the helper its own fractions — the call site's correctness rests on inspection only, which is
why two fixes to the same anchor landed in one day (2026-09-27) without the suite seeing the second defect.

**Red test** — `FollowBlitCallSiteTest` (plain JUnit, `:app` unit tests, file
`app/src/test/java/com/naviveylin/ui/map/FollowBlitCallSiteTest.kt`), run on the unmodified tree with
`./gradlew :app:testMobileDebugUnitTest --tests "com.naviveylin.ui.map.FollowBlitCallSiteTest" -PforceTests
--no-build-cache` — `BUILD FAILED in 5s`, XML
`tests="2" skipped="0" failures="2" errors="0" timestamp="2026-10-21T08:59:28.889Z"`:

```
the follow blit offset is computed by the seam and not inline in the frame loop FAILED
    java.lang.AssertionError: the follow blit seam must exist at …/app/src/main/java/com/naviveylin/ui/map/FollowBlitRules.kt
the follow offset path cannot read the raw preset FAILED
    java.lang.AssertionError: the follow blit seam must exist at …/app/src/main/java/com/naviveylin/ui/map/FollowBlitRules.kt
```

**Owning capability and guideline:** capability `smooth-follow`, requirement "Single resolved anchor across
render, blit and marker" (`openspec/specs/smooth-follow/spec.md:296`); the guideline section that owns the
pipeline is `guidelines/MapRendering.md` §1.1 "Follow framing — the anchor applies to the RENDER TARGET".

**The single fix:** extract the offset computation into `ui/map/FollowBlitRules.kt`, an object whose
`offsetFor(ui: MapCanvasUiState, frame, displayedLat, displayedLon, canvasW, canvasH, dpi)` takes the whole
map state and reads `ui.resolvedAnchor` itself, and have the screen call it — so the raw preset is not a
parameter any caller can supply — with two cases holding the tree to it: the source guard above (seam called
once, no inline helper call, no `activeFollowAnchor` in the seam) and a geometry case that drives the seam
with a real ViewModel state whose overlays resolve the anchor away from the preset.

**Eligibility (phase B):** verified today (1); one mechanism (2); one fix, no option left open (3); a
falsifiable WHEN/THEN false on HEAD — "the screen SHALL NOT compute the follow blit offset inline" (4); one
capability, three files plus two test cases (5); decidable by a host test (6); improvement whose owning
capability exists and whose red case asserts counts and an absence (7).
