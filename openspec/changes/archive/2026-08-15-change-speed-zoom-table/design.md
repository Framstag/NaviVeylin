# Design: Speed-Zoom Table Update

## Context

`SPEED_ZOOM_TABLE` in `MapCanvasViewModel.kt` maps speed to target magnification with linear interpolation (`computeSpeedZoom`). The stylesheet renders building names/numbers at mag ≥ 16 (`labelBuildingMag = veryClose`). Current table targets 14–15 for suburban speeds, so buildings never label while driving. See proposal.md — Why.

## Goals / Non-Goals

**Goals:**
- New table values: walking +1 (`0.0→18.0`, `6.0→17.5`), suburban band at 16 (`30.0→16.0`, `60.0→16.0`)
- Unit-testable table without instantiating the full ViewModel
- Fix stale spec scenarios (100 km/h → 14.0 was already wrong vs current code)

**Non-Goals:**
- No changes to interpolation, throttling, hysteresis, or turn-boost logic
- No stylesheet changes (building threshold stays at 16)
- No changes to the JavaScout reference app's table (`libosmscout-client-java` submodule)

## Decisions

### D1: Extract table + interpolation into an internal `SpeedZoomTable` object

Extract `SpeedZoomLevel`, `SPEED_ZOOM_TABLE`, and the interpolation into `internal object SpeedZoomTable` (new file `app/src/main/java/com/naviveylin/ui/map/SpeedZoomTable.kt`) with `fun compute(speedKmH: Double): Double`. `MapCanvasViewModel.computeSpeedZoom` delegates to it.

- **Why**: `computeSpeedZoom` is private; testing it requires the full Robolectric ViewModel setup (FakeOSMScoutClient, cooldown 2500 ms, hysteresis, stable-sample counting). A pure JVM test of the extracted object is fast, deterministic, and doesn't touch the JNI stub classloader rules.
- **Alternatives considered**:
  - *Make `computeSpeedZoom` internal* — minimal diff, but tests still need the full ViewModel + Robolectric boilerplate (see `MapCanvasViewModelZoomRangeTest`).
  - *Test through the position-update flow* — timing-dependent (cooldown, stable samples), brittle, slow.

### D2: Keep table values as the only behavioral change

No changes to `ZOOM_COOLDOWN_MS`, `ZOOM_HYSTERESIS_MAG`, `ZOOM_COMMIT_SAMPLES`, or the commit logic. The flat 30–60 km/h band (both 16.0) avoids pumping within the band; the existing 1-level-per-update smoothing handles the steeper 60→90 drop.

## Risks / Trade-offs

- [Steep 60→90 km/h drop (16→13 over 30 km/h)] → Existing smooth-transition logic (≤1 level per position update) prevents jarring jumps; commit hysteresis (≥1.0 mag) filters noise.
- [Mag 18 at walking speeds = `magBlock` detail, higher render cost] → Only applies ≤6 km/h; brief exposure; label priority system drops overlapping labels.
- [More building labels at 16 in city/suburban] → Denser label layer; libosmscout label placement handles collisions; acceptable per user request.
- [Spec scenario values drift again] → Delta spec now matches the table exactly; unit tests pin the values.

## Migration Plan

No persistence or config migration — table is a compile-time constant. Rollback: revert the table values (and test expectations) in one commit.

## Open Questions

None.
