## Why

The auto-zoom speed table doesn't match real driving needs. Walking speeds (≤6 km/h) sit too zoomed out, and suburban speeds (up to 60 km/h) target magnification 14–15 — below the stylesheet's building-label threshold (mag ≥ 16) — so building numbers and names never appear while driving through town.

## What Changes

- Update `SPEED_ZOOM_TABLE` in `MapCanvasViewModel.kt`:
  - Walking speeds +1: `0.0 → 18.0`, `6.0 → 17.5`
  - Suburban band raised to 16: `30.0 → 16.0`, `60.0 → 16.0` (building names/numbers render at mag ≥ 16 per `labelBuildingMag = veryClose` in the stylesheets)
  - `15.0 → 16.0`, `90.0 → 13.0`, `130.0 → 12.0` unchanged
- Update the `auto-speed-zoom` spec: table range requirement (17→13 becomes 18→12) and stale scenario values (100 km/h → 14.0 was already wrong vs current code; 5 km/h → 16.5)
- Add unit tests covering the new table values

## Capabilities

### New Capabilities

- none

### Modified Capabilities

- `auto-speed-zoom`: speed-to-magnification table values change; building names/numbers visible at speeds up to 60 km/h

## Impact

- `app/src/main/java/com/naviveylin/ui/map/MapCanvasViewModel.kt` — `SPEED_ZOOM_TABLE` values only; interpolation, throttling, and hysteresis logic unchanged
- `app/src/test/java/com/naviveylin/ui/map/` — new unit test for table interpolation (`computeSpeedZoom` is private; expose as `internal` or extract the table+interpolation for testability)
- No native, JNI, stylesheet, or renderer changes
