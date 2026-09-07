# Proposal: Enlarge phone navigation overlays for driver-seat readability

## Why

On-device feedback: the phone map navigation overlays are too small to read from the driver seat. Three complaints:

1. **Current speed badge** — text too small (16sp), and the fixed near-black background (`Color(0xCC1C1B1F)`) is invisible on a dark map in dark mode and poorly readable with the dark-red overspeed text in light mode. Unlike every other overlay it is not a normal theme card.
2. **Turn instruction card** — the actual text (distance 24sp, description 18sp, next-next 16sp) is too small to read while driving.
3. **Speed limit sign** — 40dp circle with 16sp digits is too small at a glance.

## What Changes

- **Speed badge** (`SpeedWidget.kt`): speed text 16sp → 24sp bold; badge background from the fixed `0xCC1C1B1F` to the standard overlay card container (`surface.copy(alpha = 0.92f)`, `RoundedCornerShape(12.dp)`) — the same treatment as the turn card and routing status; overspeed text keeps `colorScheme.error` (contrast now guaranteed by the theme card in both light and dark mode).
- **Speed limit sign** (`SpeedWidget.kt`): circle 40dp → 56dp, red border 4dp → 5dp, digits 16sp → 22sp bold; placeholder slot and reserved-footprint logic (spec `map-speed-widget`) unchanged — only the visible sign grows.
- **Turn instruction card** (`NextTurnOverlay.kt`): distance 24sp → 32sp bold (`headlineLarge`); description + destination 18sp → 22sp; turn arrow 48dp → 64dp; next-next text 16sp → 20sp, next-next arrow 28dp → 36dp. Card remains full-width, top-anchored, `maxLines = 2` per text — grows ~40% in height but keeps the map visible.
- **Unchanged behavior**: overspeed rule (`+5 km/h → red`), widget hidden without speed data, badge width stability ("999 km/h" reservation), sign-slot reservation, labels, and turn instruction semantics.

## Capabilities

### New Capabilities

None — both changes modify existing capabilities.

### Modified Capabilities

- `map-speed-widget`: badge container SHALL use the standard overlay card container (theme surface card, rounded) instead of a fixed dark color; minimum readable sizes for speed text and the limit sign; overspeed color still applied on the card background.
- `next-turn-overlay`: minimum font sizes for the turn instruction texts (primary distance, description/destination, next-next) readable from the driver seat.

## Impact

- **Code**:
  - `app/src/main/java/com/naviveylin/ui/map/SpeedWidget.kt` — badge container + text size, sign size (Kotlin, Compose, ~40 lines).
  - `app/src/main/java/com/naviveylin/ui/navigation/NextTurnOverlay.kt` — text sizes + arrow sizes (Compose, ~10 lines).
- **Tests**:
  - `app/src/test/java/com/naviveylin/ui/map/SpeedWidgetTest.kt` — keep existing cases green (badge visible, width stability, overspeed color, sign tag, placeholder); add assertions for the new container (card background) and minimum text sizes (font size checks on badge + sign).
  - Add/update a Compose test for `NextTurnOverlay` font sizes if one exists; otherwise extend `SpeedWidgetTest`-style coverage in a small `NextTurnOverlay` test.
  - `MapCanvasViewModelSpeedWidgetTest.kt` (data selection) unaffected.
  - Full `./gradlew test` regression.
- **No native change**: no submodule, JNI, vcpkg/CMake impact.
- **Scope**: phone variant only. Android Auto/AAOS has its own template-scaled indicators (`SurfaceIndicators` in `:auto`); labels and hierarchy unchanged, so `cross-variant-ui-parity` (labels, not pixel sizes) is intact — no AA change.
- **Additive**, not breaking. Rollback: revert the two Kotlin files and tests; sizes return to current values.
- **Guidelines**: `guidelines/UI.md` — add the phone overlay size convention (badge = standard card container, minimum text sizes) under the phone map section.
