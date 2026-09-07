# Proposal: Make the max-speed sign and compass widgets even bigger (phone + Android Auto)

## Why

The previous change (`2026-09-04-enlarge-phone-nav-overlays`) enlarged the phone speed badge, turn card, and max-speed sign for driver-seat readability. On-device feedback: the **max-speed sign** and the **compass** are still too small to read at a glance — on the phone and on Android Auto. The compass rose on AA is still sized like the host strip buttons (48 dp), and the phone compass is still the standard 48 dp overlay-button size. Both need another size bump.

## What Changes

- **Phone max-speed sign** (`SpeedWidget.kt`): circle 56dp → 64dp, red border 5dp → 6dp, digits `titleLarge` (22sp) → `headlineMedium` (28sp) bold. Badge, overspeed rule, placeholder/reserved-slot footprint, and labels unchanged — only the visible sign grows (placeholder grows with it to keep the reserved slot matching).
- **Phone compass** (`CompassButton.kt`): button 48dp layout / 40dp visual → 56dp layout / 48dp visual; follow-direction triangle stays 70% of the visual; "N" label 9sp → 11sp. The compass becomes **larger than** the other overlay buttons (menu, search, location options stay 48dp) — a deliberate hierarchy change so the compass reads at a glance.
- **AA compass rose** (`SurfaceIndicators.kt`): `ROSE_DIAMETER_DP` 48f → 56f. Still right-aligned, still horizontally centered over the speed badge.
- **AA max-speed sign** (`SurfaceIndicators.kt`): `LIMIT_DIAMETER_DP` 48f → 56f, `LIMIT_RING_DP` 6f → 7f, `LIMIT_TEXT_DP` 20f → 24f. AA speed badge (128×52, 20sp text) unchanged — only the sign grows.
- **Unchanged behavior**: overspeed rule (+5 km/h → red, phone; rounded-value comparison, AA), widget hidden without speed data, badge width stability, sign-slot reservation, compass interactions (short press re-center, long press orientation toggle, GPS fill colors, animation ≤300ms), AA badge/rose placement rules.

## Capabilities

### New Capabilities

None — all three changes modify existing capabilities.

### Modified Capabilities

- `map-speed-widget`: minimum size of the max-speed sign raised (circle ≥64dp, red border ≥6dp, digits ≥28sp bold); reserved-slot footprint follows the new sign size.
- `compass-button`: compass button SHALL be larger than the other overlay buttons (56dp layout / 48dp visual vs 48dp / 40dp), replacing the "same size as other overlay buttons" requirement; needle still ~70% of the button.
- `auto-map-layout`: compass rose during navigation SHALL be 56dp (no longer "sized like the strip buttons (48 dp)"); speed-limit sign SHALL be ≥56dp with ≥7dp ring and ≥24sp digits.

## Impact

- **Code**:
  - `app/src/main/java/com/naviveylin/ui/map/SpeedWidget.kt` — sign size/border/digit style + placeholder footprint (Compose, ~6 lines).
  - `app/src/main/java/com/naviveylin/ui/map/CompassButton.kt` — button/visual size, "N" font size (Compose, ~4 lines).
  - `auto/src/main/java/com/naviveylin/auto/SurfaceIndicators.kt` — rose/limit constants (Canvas, ~4 lines).
- **Tests**:
  - `app/src/test/java/com/naviveylin/ui/map/SpeedWidgetTest.kt` — update sign-size assertions (56dp → 64dp, digit style), keep placeholder-matching check.
  - `app/src/test/java/com/naviveylin/ui/map/CompassButtonComposeTest.kt` — update size assertions (48/40 → 56/48), needle ratio unchanged.
  - `auto/src/test/java/com/naviveylin/auto/SurfaceIndicatorsTest.kt` — update rose/limit geometry assertions (48 → 56).
  - `auto/src/test/java/com/naviveylin/auto/SurfaceLayoutTest.kt` — verify no overlap with the larger indicators.
  - Full `./gradlew test` regression.
- **No native change**: no submodule, JNI, vcpkg/CMake impact.
- **Scope**: phone + Android Auto. AAOS uses the same `:auto` code path (SurfaceIndicators), so it inherits the AA sizes. `cross-variant-ui-parity` (labels, not pixel sizes) intact.
- **Additive**, not breaking. Rollback: revert the three Kotlin files and test updates; sizes return to current values.
- **Guidelines**: `guidelines/UI.md` — update the phone overlay size convention (compass larger than overlay buttons; new sign minimums) and the AA indicator sizes.
