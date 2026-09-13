# Design: Unified Vehicle Marker

## Decision

One marker for phone + Android Auto: compass direction arrow (tip + tail triangles, rounded tip), 32 dp density-aware on both surfaces, vertical blue gradient core, white casing ring, dark accent rim, soft blurred drop shadow, plus the existing accuracy circle. No day/night color branching. No new dependencies — `android.graphics.LinearGradient`, `BlurMaskFilter` (AA side), Compose `Brush.linearGradient` + shadow (phone side).

## Unified visual spec

```
PROPOSED MARKER (drawn centered at GPS position, rotated by screen bearing)
================================================================
        *                         soft blurred shadow ~3dp below
       / \   white casing ring 2dp  (also under core)
      /   \  dark accent rim edge
     /  G  \ core: vertical gradient  top #42A5F5 -> bottom #0D47A1
    /  R  /         (linear, light from above = 3D read)
    \  A  /   rounded tip
     \  D /    tail triangle (matches phone today)
      \   /
       \ /
     32 dp total footprint
================================================================
```

| Element | Value | Rationale |
|---|---|---|
| Footprint | 32 dp | ~9% of 360 dp phone, ~2.7% of 1200 dp car surface; between phone's 56 dp and AA's 14 px today |
| Core gradient | #42A5F5 → #0D47A1 (vertical) | light-from-above; deeper bottom end improves gap on light land |
| Casing ring | white, ~2 dp | daylight contrast; also separates core from dark roads at night |
| Accent rim | #0D47A1-family edge under casing | tri-layer arrow (dark rim / white casing / blue core) survives every background |
| Shadow | blurred, ~3 dp offset, no hard offset | floating-chip depth (`BlurMaskFilter` / Compose shadow) |
| Tail | phone-style tail triangle added to AA | closes geometry divergence (AA today has none) |
| Accuracy circle | unchanged, parallel both sides | out of scope |

## Density rule

- Phone (`LocationMarkerOverlay`, Compose): constant `32.dp` converted via `LocalDensity` — dp = density-aware.
- AA (`AutoMapRenderer.drawGpsMarker`, android.graphics): `32f * density` where `density = projectionDpi / 160f` (the surface DPI the native renderer already uses, `updateProjectionDpi`). Projection hosts report mdpi surface DPI; AAOS head units may report higher — scaling covers both.
- Both sides hit the same physical size because each platform multiplies by its own density. This is the meaning of "same size on all devices" in the specs.

## Shared geometry

Single source for geometry constants recommended: phone uses Compose `Path`, AA uses `android.graphics.Path` — code cannot be literally shared, but **normalized 0..1 geometry constants** (tip/length, tail length, casing width, rim width, gradient stops) CAN live in one object in `:core` (e.g. `MarkerGeometry`), with both renderers multiplying by `dpPx`. Parity unit tests assert both modules use it. Fallback if `:core` placement fights module layering: mirrored constants + a parity test that compares the projected vertex positions for the same bearing.

## Casing color follows presentation (assumption invalidated on review)

Initial design assumed "no day/night branch". On-device review (dark mode) showed a fixed white casing reads as a stencil-like bright border around the arrow — not clean-cut. So the casing color SHALL be the ONE scheme-aware property:

- Light presentation: white casing (daylight contrast on light land).
- Dark presentation: deep blue-black casing (`COLOR_CASING_DARK`, #0E1622) that blends with dark asphalt — silhouette stays clean-cut, no bright halo.

Everything else is branch-free: shape, rim stroke, gradient core, blurred shadow. The dark flag rides the already-resolved presentation: phone `state.isDarkPresentation` -> `LocationMarkerOverlay(dark=...)`; AA `resolvedDark` -> `AutoMapRenderer.setDarkPresentation(...)` (pushed in the same collector as the daylight flag). Contrast math per scheme: day = white casing (high-L) + dark rim + deep core bottom; night = dark casing (low-L) + bright gradient core — both present both luminance extremes on their background, so either scheme reads.

## Why tri-layer shape (rim + casing + core)

- Single shape path renders in both schemes — only the casing color swaps, no mid-render theme flips.
- Day: white halo (L* high) + deep blue core (L* low) keeps the arrow visible on light land.
- Night: dark casing + bright core keeps the arrow visible on dark roads; white never leaks into night rendering.

## Implementation notes (apply phase)

- AA: `drawGpsMarker` currently single triangle + hard shadow (`AutoMapRenderer` ~L962-1029). Replace with: casing path (slightly enlarged), rim stroke, gradient shader (`LinearGradient` with arrow-local geometry), blurred shadow (`Paint` + `BlurMaskFilter`), tail triangle, rounded tip via path arc.
- Phone: `drawCompassArrowWithShadow` (`LocationMarkerOverlay` ~L150+) — replace hard shadow triangle with blurred shadow, add casing/rim/gradient (Compose `Brush.linearGradient`, `drawPath` with `brush =`), keep tail, round tip; size constant 56.dp → 32.dp.
- Keep `shadowOffset` semantics: blurred shadow offset in dp both sides.
- Tests: existing `AutoMapRendererTest` + marker tests must still pass (geometry changed but projection logic untouched); add unit tests for vertex math against shared constants, plus Compose test asserting no hard-offset shadow path (if harness supports).

## Risks

- Gradient shader per draw: negligible cost at one 32 dp arrow per frame.
- `BlurMaskFilter` on AA: GPU-friendly at this size; verified cheap.
- Parity spec: size delta (32 dp fixed both) is no longer a deviation at all — style now identical; only surface density scaling differs, which is mandatory platform behavior.
- Rounded tip: keep radius small (≤ 8% of length) so the arrow still points crisply at high zoom.
