# Proposal: Unified Vehicle Marker (Phone + Android Auto)

## Why

The vehicle position marker is drawn independently on the two surfaces and has drifted far apart: the phone renders a 56 dp compass arrow (`LocationMarkerOverlay`, #4A90D9, tail triangle), while Android Auto renders a 14 px arrow at reference density (`AutoMapRenderer.drawGpsMarker`, #2196F3, no tail). On the car display the marker is disproportionately small, washes out against daylight map backgrounds (midtone blue on light land), and reads as flat. Users want one marker everywhere: same geometry and colors, density-aware sizing (same visual size on every device/screen), larger and more legible on AA, and visually richer — directional arrow with simulated lighting/3D shading (no car-shaped icon).

## What Changes

- **Unified marker style both surfaces** (Compose Canvas on phone, android.graphics on AA): compass arrow (tip + tail triangle) with rounded tip, casing ring, dark accent rim, vertical blue gradient (light from above), soft blurred drop shadow. Only the casing color branches on dark presentation — white in day, deep blue-black at night so no bright halo appears against dark roads (review feedback); shape, rim, gradient and shadow stay branch-free.
- **Density-aware size**: same dp target on both surfaces — 32 dp (phone: `32.dp`, AA: `32f * surfaceDensity`), replacing 56 dp phone / 14 px AA. Size is the only platform deviation; geometry and palette identical.
- **Parity**: satisfies `cross-variant-ui-parity` (same visual style wherever platform allows; deviation limited to needed size delta — car surfaces are much wider).

Additive visual change. **No new dependencies** — `android.graphics.LinearGradient`, `BlurMaskFilter`, and Compose `Brush.linearGradient`/shadow APIs already cover it. No behavior change to bearing semantics (`bearing < 0` → north), accuracy circle, gliding/follow logic, or rendering pipeline.

## Capabilities

- **New Capabilities**: none.
- **Modified Capabilities**:
  - `openspec/specs/gps-location-marker/spec.md` — MODIFIED "Location marker rendering": direction indicator gains unified geometry (tail), size 32 dp, casing, gradient, blurred shadow; parity with the AA marker stated.
  - `openspec/specs/auto-map-renderer/spec.md` — MODIFIED "GPS position marker on car map": visual style unified with the phone marker (32 dp density-aware, casing, gradient, blurred shadow, day/night-safe).

## Impact

- **Code**:
  - `app/src/main/java/com/naviveylin/ui/map/LocationMarkerOverlay.kt` — port unified geometry/colors at 32 dp (Compose).
  - `auto/src/main/java/com/naviveylin/auto/AutoMapRenderer.kt` — `drawGpsMarker`: 32 dp size, casing/rim/gradient/soft shadow (android.graphics).
  - Shared geometry constants (single source) if practical — e.g. a small shared object in `:core` or mirrored constants per module with parity tests.
- **Tests**: unit tests for projected geometry/composition (both sides); Compose/Robolectric snapshot-checks where existing harness allows (see `app/src/test/...`, `auto/src/test/...`); no native/JNI changes.
- **Specs**: deltas above.
- **Guidelines**: `guidelines/UI.md` §8/§9-adjacent marker mentions (compass/speed badge sections) — add a marker line if a marker-size note exists; `guidelines/Design.md` unaffected.
- **Dependencies**: none new.
- **Scope**: both phone and Android Auto variants (projection + AAOS), per parity spec.

## Open Questions

- Exact 32 dp target vs 28–36 range — recommend 32; adjustable in design with user OK.
- Single shared `:core` geometry object vs mirrored constants with parity tests — design decision, recommend shared constants.
