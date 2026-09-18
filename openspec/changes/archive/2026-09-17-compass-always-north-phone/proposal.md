## Why

`8b0bec8` (2026-09-12) changed the phone compass needle target from "north" to a travel-direction indicator in "follow direction" mode: in heading-up follow (`screenBearing(bearing, -bearing) ≡ 0`) the needle is pinned straight up, so the compass never shows where north is — which is exactly when a driver needs it (driving south, north is behind you and must draw at the bottom). The needle is additionally fed the *freshest* GPS bearing (`gpsMarkerBearing`) while the map rotates with the *smoothed* bearing, so at a standstill the noisy bearing makes the needle swirl around before it settles on the wrong (travel) direction. The widget's purpose, its own "Compass shows north direction" requirement and the Android Auto compass rose (which does show north) all disagree with the shipped phone behavior.

## What Changes

- The phone compass needle SHALL point at **geographic north** in every orientation mode (north-up and follow direction / heading-up), computed from the map rotation alone: north's screen direction is the map angle measured clockwise from screen-up.
- The needle SHALL take **no bearing input**: the vehicle bearing SHALL NOT influence the compass, so a standstill (undefined/noisy heading) cannot make the needle swirl — it holds the last map rotation's north direction and follows a rotation smoothly.
- The phone "follow direction" travel-direction triangle is **REMOVED** (its spec requirement is removed with the change): north's screen direction is the information the widget exists to give, and the travel direction is already visible as the vehicle marker arrow and as the map's own rotation.
- The needle keeps its current appearance rule for the remaining mode difference: nothing mode-specific remains in the needle; the "N" north pointer is drawn in all modes. Orientation mode stays visible through the map rotation itself and through the location options sheet.
- Additive behavior for north-up mode (unchanged); **user-visible behavior change in follow direction** (the triangle is replaced by the north pointer). Rollback: revert the needle-target change in `CompassButton.kt` (single expression).
- Tests: heading-up southbound → needle at 180°; the needle is independent of `gpsMarkerBearing` (noise/no-fix scenarios); north-up unchanged; the compass test that currently asserts the triangle behavior is rewritten.

## Capabilities

### New Capabilities

- None — this is a behavior change to an existing capability.

### Modified Capabilities

- `compass-button`: "Compass shows north direction" and "North pointer points at rendered north" are extended to the phone needle in every orientation mode (and to a needle that ignores the vehicle bearing); "Follow-direction triangle shows travel direction" is removed; "Compass visually differentiates orientation modes" is removed (both modes show the north pointer); "Compass button matches overlay button sizing" loses the triangle clause.

## Impact

- **Code**:
  - `app/src/main/java/com/naviveylin/ui/map/CompassButton.kt` — `compassNeedleTarget` returns `ProjectionUtils.compassRotationDegrees(mapAngle)` for both orientation modes and no longer consumes `bearingDegrees`; the follow-direction triangle branch in `drawCompassNeedle` is deleted; the `bearingDegrees` parameter is removed from `CompassButton`/`MapCompassBlock`/`MapRightWidgetColumn`.
  - `app/src/main/java/com/naviveylin/ui/map/MapCanvasScreen.kt` — drop the `bearingDegrees = state.gpsMarkerBearing` argument at the three `MapRightWidgetColumn` call sites (free-form, landscape, routing view).
  - No change to `MapCanvasViewModel` (the map rotation and the marker arrow keep using the smoothed/fresh bearings as today).
- **Tests**: `app/src/test/java/com/naviveylin/ui/map/CompassNeedleTargetTest.kt` (rewritten: north in both modes, heading-up cases, bearing-independence, no-fix case), `CompassButtonComposeTest.kt`, `MapRightWidgetColumnTest.kt` (parameter removal), plus a standstill scenario: a fix with an unstable/absent bearing does not change the needle.
- **Guidelines**: `guidelines/UI.md` §8 — the "follow-direction triangle stays ~70% of the button" clause and the compass needle sizing note must be replaced by the "needle always indicates north" rule (spec/guideline contradiction rule: updated in the same change). `guidelines/MapRendering.md` §7 needs no change: after this change `markerBearing` feeds only the marker arrow, which is exactly what §7 documents.
- **Surfaces**: phone only. Android Auto keeps its compass rose (already north-pointing, `SurfaceIndicators`); `guidelines/UI.md` parity rule is satisfied because both surfaces now show a north pointer.
- **Native/JNI**: none — pure Compose/UI change.
- **Related finding, deliberately out of scope**: `MapCanvasViewModel` falls back to `lastUsedAngle` in the north-up branch of the follow angle computation, so after toggling heading-up → "always north" the map keeps rotating to the vehicle heading. That is a map-rotation defect (`compass-settings`/`gps-render-coalescing` territory, not the needle); it is fixed by the separate change `fix-north-up-orientation-angle`.
