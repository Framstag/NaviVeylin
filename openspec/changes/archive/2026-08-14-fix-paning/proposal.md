## Why

Single-finger panning is incorrect when the map is rotated (viewport angle != 0). The drag delta is converted to a geographic center offset using the north-up `dragDeltaToNewCenter()`, so the map moves as if the angle were 0 — dragging right on a 90°-rotated map moves the center west instead of south. The two-finger centroid pan already uses the rotation-aware `dragDeltaToNewCenterRotated()`; the single-finger path was missed.

## What Changes

- Make single-finger pan rotation-aware: `MapCanvasScreen.onPan` will use `ProjectionUtils.dragDeltaToNewCenterRotated(dx, dy, s.viewport.angle, ...)` instead of `dragDeltaToNewCenter(...)`, matching the existing two-finger `onCentroidPan` behavior.
- No new native or core math: `dragDeltaToNewCenterRotated` already exists in `core/src/main/java/com/naviveylin/core/ProjectionUtils.kt` and is verified to reduce to `dragDeltaToNewCenter` at angle 0 (existing unit tests).
- Add/extend unit tests covering single-finger pan deltas at non-zero angles (e.g., 90°), mirroring the existing `dragDeltaToNewCenterRotated` test cases, to lock in the fixed behavior at the gesture-call-site level.

## Capabilities

### New Capabilities

(none)

### Modified Capabilities

- `map-pan-zoom`: The "Touch-based pan" requirement changes — pan SHALL convert the screen drag delta using the viewport rotation angle (`dragDeltaToNewCenterRotated`), not the north-up-only `dragDeltaToNewCenter`, so the map follows the finger on a rotated viewport.

## Impact

- `app/src/main/java/com/naviveylin/ui/map/MapCanvasScreen.kt` — `onPan` callback (line ~272): swap the projection call, pass `s.viewport.angle`; single-line behavioral change.
- `core/src/main/java/com/naviveylin/core/ProjectionUtils.kt` — unchanged; `dragDeltaToNewCenterRotated` already provided (used by `onCentroidPan`).
- `app/src/test/java/com/naviveylin/ui/map/ProjectionUtilsTest.kt` — extend with rotated-pan cases at the `ProjectionUtils` level and/or a call-site test for `onPan`.
- Spec delta: `openspec/specs/map-pan-zoom/spec.md` — Touch-based pan requirement + scenario "Pan map east on rotated viewport".
- No native/C++ changes, no ABI/API changes, no new dependencies. Gesture render paths (tile blit vs full render) unaffected — pan math is fixed regardless of which render path runs.
