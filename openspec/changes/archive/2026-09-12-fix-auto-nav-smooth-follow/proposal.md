# Proposal: Fix smooth map scrolling in the AA routing (navigation) view

## Why

During turn-by-turn navigation on Android Auto the map follows the vehicle in small 1 Hz jumps instead of gliding. The smooth-follow machinery (`auto-smooth-follow`: overrun buffer, sub-region blit, extrapolation loop, correction easing) is built and works in free driving, but the navigation view never feeds fix speed to the renderer, so the extrapolation gate stays closed and every fix snaps the map to the raw position.

## What Changes

- `NavigationScreen` feeds fix speed, heading and timestamp to `AutoMapRenderer.setGpsMarker` (currently drops them — `speedKmH` defaults to `NaN`).
- Extract the free-driving speed/bearing derivation (GPS value when valid, else movement-between-fixes) into a shared helper and use it in `NavigationScreen`, so smoothing also works on receivers without GPS speed or bearing (e.g. GPX replay).
- No renderer changes: `AutoMapRenderer` already supports the flow (`setGpsMarker` speed param, extrapolation gate, `reengageFollow` after the per-fix viewport commit).

## Capabilities

### New Capabilities

(none)

### Modified Capabilities

- `auto-smooth-follow`: follow-mode screens feed fix speed/heading/timestamp to the renderer so the extrapolation loop can run; screens derive speed/bearing from movement when the GPS values are missing.
- `auto/navigation-view`: the navigation map follows the vehicle with smooth extrapolated scrolling between fixes (same as free driving), no per-fix snap; derived-speed fallback keeps smoothing when GPS speed/bearing are unavailable.

## Impact

- `auto/src/main/java/com/naviveylin/auto/NavigationScreen.kt` — pass `speedKmH`/`timeMs`/derived bearing to `setGpsMarker`; use shared speed/bearing derivation.
- `auto/src/main/java/com/naviveylin/auto/FreeDrivingScreen.kt` — move `effectiveSpeed`/`effectiveBearing`/`movementSpeedKmH`/`movementBearing` into a shared helper (behavior unchanged).
- New shared helper (e.g. `core` `AutoPositionUtil`) used by both screens.
- Tests: `NavigationScreen` wiring test (speed fed to renderer), shared derivation helper tests; `AutoMapRenderer` tests unchanged.
- No new dependencies. No phone-app impact.
