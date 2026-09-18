## Why

The follow-mode map rotation falls back to the last used angle whenever no new smoothed bearing is available — a rule the durable `gps-render-coalescing` spec requires for **follow direction** mode so the map does not spin or snap to north at a standstill. The follow collector applies that fallback unconditionally, without checking the orientation mode, so "always north" is not honored on any path where the bearing is missing *and* the previously used angle was a heading-up one. Concretely: drive with "follow direction" (heading-up), long-press the compass to switch to "always north" — the viewport is reset to 0° once, and the very next GPS fix re-applies the stale heading-up angle, so the map keeps rotating with the vehicle and the compass needle tracks the vehicle instead of north. The stale angle persists until the app restarts or the user drives long enough for a new bearing to overwrite it, and there is no test for the north-up branch of the fallback.

## What Changes

- The follow-mode angle fallback SHALL be scoped by orientation mode: in **"always north"** the map angle SHALL be 0 radians on every commit, independent of any previously used angle and of bearing availability; the keep-the-last-angle fallback SHALL apply only in **"follow direction"** mode.
- The last-used follow-direction angle SHALL survive a north-up period, so switching back to "follow direction" while no bearing is available keeps the last driving direction instead of snapping to 0° (preserves the `gps-render-coalescing` "SHALL NOT snap back to North-Up" guarantees).
- Behavior of the deadband, the per-render angle rate limit, the render throttle, the course-over-ground history and the marker arrow is unchanged.
- Additive fix to a documented contract, not a breaking change (north-up mode simply starts doing what its spec says); rollback: revert the single branch change.
- Tests: north-up mode with a valid bearing keeps `angle == 0` across consecutive fixes; north-up after a follow-direction period stays 0 (the regression test for the reported defect); follow-direction without an available bearing still keeps the last angle (guards the spec's "Course unavailable" / "Keep last valid course bearing" scenarios); switching north-up → follow direction without a bearing keeps the last follow-direction angle.

## Capabilities

### New Capabilities

- None — this is a behavior fix in existing capabilities.

### Modified Capabilities

- `compass-settings`: the "Per-mode orientation setting" requirement gains the explicit rule that "always north" holds the map at 0° on every commit once selected (not only at the moment of selection), with a scenario for toggling from "follow direction" to "always north" while driving.
- `gps-render-coalescing`: "Course unavailable" and "Keep last valid course bearing" are scoped to follow-direction mode and state that north-up mode always commits 0°, so the durable fallback rules no longer contradict the orientation setting.

## Impact

- **Code**:
  - `app/src/main/java/com/naviveylin/ui/map/MapCanvasViewModel.kt` — follow collector angle computation: the `isNorthUp` case commits `0.0` instead of the `lastUsedAngle` fallback; `lastUsedAngle` is only updated from follow-direction angles (so a north-up period does not destroy it). Constants (`MIN_BEARING_DELTA_DEG`, `MAX_ANGLE_RATE_DEG_PER_RENDER`) and the `computeMapAngle` helper are unchanged.
  - No change to `MapCanvasScreen`, `CompassButton`, the renderer, or Android Auto (AA's navigation rotation uses its own controller; out of scope, listed as a parity check).
- **Tests**: new `MapCanvasViewModel` follow-rotation tests (north-up across fixes, north-up after a follow-direction period, fallback preserved in follow direction, north-up → follow-direction without bearing); `OrientationLogicTest` (`computeMapAngle`) stays valid and unchanged.
- **Guidelines**: `guidelines/MapRendering.md` §6 (angle handling) gains the mode-scoping rule for the last-angle fallback — currently it documents the rate limit and normalization but not this branch.
- **Related changes**: `compass-always-north-phone` (in flight) relies on the map angle being correct for the north needle; this change removes that dependency risk. No overlap in the files it edits (`CompassButton`/`MapCanvasScreen` vs this change's `MapCanvasViewModel` angle branch), so the two can land in either order.
- **Native/JNI**: none — pure Kotlin state logic.
- **Scope**: phone (free driving and navigation share the same follow collector). Android Auto keeps its existing rotation controller.
