# Fix north-up marker bearing

## Why

In navigation mode with "North always up" orientation, the GPS location marker arrow always points north on screen regardless of driving direction. Driving south shows the arrow pointing backwards (up), suggesting the vehicle drives in reverse. In "Follow direction" mode the arrow is correct. Root cause: `MapCanvasViewModel` forces the marker bearing to the "unavailable" sentinel (`-1.0`) whenever north-up orientation is active, and both the Compose overlay and the native renderer interpret `bearing < 0` as "no bearing → point north". North-up mode is a map-rotation choice, not a bearing-availability state — the arrow must keep pointing in the direction of travel in both orientation modes.

## What Changes

- Fix `MapCanvasViewModel` marker-bearing computation: pass the real GPS bearing to the marker whenever it is available, regardless of orientation mode. Keep the `-1.0` sentinel only for genuinely unavailable bearing (arrow-north fallback preserved).
- Correct misleading comments/KDoc that document the wrong "north-up → arrow north" behavior (`LocationMarkerOverlay.kt`, `MapCanvasViewModel.kt`).
- Extract the marker-bearing decision into a pure, unit-testable function and add real unit tests covering north-up, follow-direction, and unavailable-bearing cases (existing `OrientationLogicTest` is tautological — it tests inline expressions, not production logic).
- Add an explicit spec scenario to `gps-location-marker` stating that north-up mode keeps the arrow in the direction of travel — the current spec is silent on north-up, which is how the wrong implementation slipped in.

No native/JNI changes. No spec changes to `compass-settings` (it governs map rotation only, and its behavior is correct).

## Capabilities

### Modified Capabilities

- `gps-location-marker` — add explicit requirement/scenario: in north-up orientation the direction arrow SHALL still point in the direction of travel (bearing available), not north. Clarify that `bearing < 0` means "bearing unavailable" only.

## Impact

- `app/src/main/java/com/naviveylin/ui/map/MapCanvasViewModel.kt` — marker-bearing computation (line ~690), KDoc on `gpsMarkerBearing` state field.
- `app/src/main/java/com/naviveylin/ui/map/LocationMarkerOverlay.kt` — comment correction only (rendering logic already correct).
- `app/src/test/java/com/naviveylin/ui/map/` — new/extended unit tests for the extracted bearing function.
- `openspec/specs/gps-location-marker/spec.md` — delta spec with the north-up scenario.
- Guidelines: no `guidelines/` doc changes needed (UI.md/MapRendering.md do not specify marker arrow behavior per orientation mode).
- Additive, no breaking changes, no rollback risk beyond reverting the one-line fix.

### Out of scope (observation, not action)

- `OSMScoutClient.cpp` contains a native GPS-marker draw path (`renderWithRouteAndPois`), but no app code calls the native `setGpsMarker` — it is dead code for NaviVeylin (JavaScout heritage). Not touched by this change; could be removed upstream later.
