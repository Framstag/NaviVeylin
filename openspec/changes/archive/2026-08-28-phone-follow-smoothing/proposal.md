# Proposal: Phone follow-mode smooth scrolling (extrapolation + correction easing)

## Why

GPS fixes arrive at 1 Hz (`LocationService`: `UPDATE_INTERVAL_MS = 1000`, `FASTEST_INTERVAL_MS = 500`, `MIN_DISTANCE_M = 5`). In follow mode the map viewport jumps once per second: the sub-region blit (`MapRenderer.trySubRegionBlit`) only fires when a new fix arrives, so between fixes the map is frozen and then snaps. At 50 km/h (~7-14 px/s at nav zoom) the stutter is clearly visible.

## What Changes

- Add a display-clock extrapolation loop (Choreographer / `withFrameNanos`, ~60 fps) active only in follow mode while moving: predict the position between fixes from the last fix + GPS speed + smoothed heading.
- Blit the front buffer by the predicted delta each frame, reusing the existing `trySubRegionBlit` machinery (overrun buffer already 1.2x).
- Ease the viewport toward the true fix on arrival (correction smoothing over ~200-300 ms) instead of snapping — absorbs extrapolation drift on curves and acceleration.
- GPS marker overlay tracks the predicted position between fixes instead of the last frame snapshot.
- The navigation engine (`NavigationController.processLocation()`) continues to receive only real fixes — predicted positions are display-only, never fed to routing.

## Capabilities

### New Capabilities
- `smooth-follow`: extrapolation + correction easing for follow-mode map scrolling on the phone renderer.

### Modified Capabilities
- `gps-location-marker`: marker position is predicted between fixes (currently snapshotted per rendered frame).
- `gps-render-coalescing`: follow-mode render trigger changes from fix cadence to "prediction exits overrun region"; the 200 ms throttle semantics are superseded by overrun-driven renders.

## Impact

- `app/src/main/java/com/naviveylin/ui/map/MapRenderer.kt` — extrapolation clock, prediction state, correction easing, blit integration.
- `app/src/main/java/com/naviveylin/ui/map/MapCanvasViewModel.kt` — pass fix time/speed/heading into the renderer; gate the loop on follow mode + movement.
- `app/src/main/java/com/naviveylin/ui/map/MapCanvasScreen.kt` — marker overlay draws at predicted position each frame.
- `app/src/main/java/com/naviveylin/location/LocationService.kt` — no change expected (`GpsFix.time` already carries fix time).
- `core/src/main/java/com/naviveylin/core/` — new shared prediction/easing helper (reused by the AA change `auto-smooth-follow`).
- Tests: `MapRenderer` unit tests, `MapCanvasViewModel` tests, marker overlay tests.

Phone-only feature. No Android Auto / AAOS impact.
