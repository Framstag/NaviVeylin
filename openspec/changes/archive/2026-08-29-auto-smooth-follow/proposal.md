# Proposal: Android Auto smooth follow-mode scrolling (overrun + blit + extrapolation + easing)

## Why

`AutoMapRenderer` has no double buffering, no overrun buffer, and no sub-region blit. Every 1 Hz GPS fix (same `LocationService` source) triggers a full native `renderToBitmap` at surface size (100-500 ms) — the map lags behind the fix and jumps in chunks. The phone renderer already has blit machinery; the AA renderer has none, so the same 1 Hz problem is worse there.

## What Changes

- Add an overrun buffer to `AutoMapRenderer` (render at ~1.2x surface size, extract the visible region).
- Add sub-region blit: on a viewport change within the overrun region, draw the shifted overrun buffer to the surface instead of a full native render.
- Add a display-clock extrapolation loop in follow mode: predict position between fixes from last fix + GPS speed + smoothed heading.
- Ease the viewport toward the true fix on arrival (correction smoothing, ~200-300 ms) — defers full renders and absorbs drift.
- GPS marker overlay tracks the predicted position between fixes.
- Render loop gated on resumed + follow mode + movement (battery/thermal on head units); respects the existing shared `surfaceLock` and pause/resume lifecycle.
- The navigation engine receives only real fixes — predicted positions are display-only.

## Capabilities

### New Capabilities
- `auto-smooth-follow`: overrun buffer + sub-region blit + extrapolation + correction easing for the AA map renderer.

### Modified Capabilities
- `auto-map-renderer`: follow-mode re-render semantics change — viewport changes within the overrun region are served by blit instead of a full re-render; the GPS marker updates between fixes.

## Impact

- `auto/src/main/java/com/naviveylin/auto/AutoMapRenderer.kt` — overrun buffer, blit, extrapolation loop, correction easing.
- `auto/src/main/java/com/naviveylin/auto/MapScreen.kt` — feed fix time/speed/heading to the renderer (extend `setGpsMarker` or new API).
- `auto/src/main/java/com/naviveylin/auto/FreeDrivingScreen.kt` — same wiring.
- `core/src/main/java/com/naviveylin/core/` — shared prediction/easing helper, reused from the phone change `phone-follow-smoothing` (created there first).
- Tests: `AutoMapRendererTest`, `MapScreen`/`FreeDrivingScreen` tests.

Android Auto / AAOS feature. No phone renderer impact (phone handled by `phone-follow-smoothing`).
