## Context

Rerouting animation uses `rememberInfiniteTransition` inside a conditional `if (isRerouting)` block in `NavigationStateOverlay.kt`. This violates Compose slot table rules — when `isRerouting` toggles, the composable slot for `rememberInfiniteTransition` shifts, causing stale animation state. Additionally, `isRerouting` and off-route detection share the same boolean, so there's no way to show a red tint during the full off-route window (from route deviation through reroute completion).

See proposal.md for motivation, specs/ for requirements.

## Goals / Non-Goals

**Goals:**
- Separate `isOffRoute` from `isRerouting` in navigation state model
- Restructure animation to use top-level `rememberInfiniteTransition` controlled by `isRerouting` (start/stop, not create/dispose)
- Add soft red background tint on status card when `isOffRoute` is true
- Animation only runs during active reroute window

**Non-Goals:**
- No changes to native libosmscout reroute logic
- No changes to route calculation flow
- No changes to MapCanvasScreen layout

## Decisions

### Decision 1: Separate `isOffRoute` from `isRerouting`, auto-start with confirmation and tunnel guard
- **Why**: `isRerouting` is only true during the brief window between `onRerouteRequest` and new route calculation. The red tint should cover the entire off-route period. Auto-start is required so the native controller tracks the new route and stops re-firing.
- **How**: When `routeCalculatedEvent` fires during rerouting, call `startNavigation()` on the new route. Confirmation counter (5 calls within 60s) prevents false triggers from transient GPS noise. Additional tunnel/no-signal guard ignores reroute requests for 10s after the native engine reports `EstimateInTunnel` or `NoGpsSignal`.
- **Critical lifecycle fix**: `startNavigation()` now stops the old native controller before creating a new one. Without this, the old controller keeps emitting callbacks in parallel, causing marker jumps, double reroutes, and corrupted navigation state.
- **UX fix**: Reroute auto-start passes `forceFollowMode=false` so the map does not snap/rotate when the route is exchanged.
- **Alternative considered**: No auto-start — rejected because native controller keeps re-firing against old route, causing perpetual reroute loop.

### Decision 2: Solid red tint, no animated border
- **Why**: User feedback: flashing border is distracting and doesn't reliably stop. Solid red tint via full-view overlay is cleaner and always consistent.
- **How**: Removed `rememberInfiniteTransition`, `animateFloat`, and `drawBehind` border entirely. Red overlay Box (alpha 0.08) when `isOffRoute` is true is the sole visual indicator.
- **Alternative considered**: Keeping animated border with better lifecycle — rejected as unnecessary complexity.

### Decision 3: Red tint via full-view overlay Box
- **Why**: User wants text and icons to inherit the reddish tint. A `Box` overlay with `background` on top of the entire card applies a uniform tint to all content.
- **How**: Wrap `Card` in a `Box`. When `isOffRoute` is true, add a second `Box` with `matchParentSize()`, `clip(RoundedCornerShape(12.dp))`, and `background(error.copy(alpha = 0.16f))` on top. This tints text, icons, and background uniformly.
- **Alternative considered**: `containerColor` override — rejected because it doesn't affect text/icons.

### Decision 4: Reroute confirmation counter + tunnel/no-signal guard + GPS accuracy guard (replaces accuracy guard)
- **Why**: GPS accuracy check alone was unreliable — native controller processes GPS async. Native controller fires `onRerouteRequest` every ~5s while off-route (`RouteStateAgent.cpp:84`). Require multiple consecutive calls before accepting, but also suppress reroute inside tunnels and with poor accuracy.
- **How**: Track `rerouteConfirmCount` and `rerouteConfirmStart`. On each `onRerouteRequest`:
  1. If we were in `EstimateInTunnel` or `NoGpsSignal` within the last 30s, ignore and reset counter.
  2. If last GPS accuracy is unknown or >100m, ignore and reset counter.
  3. If window expired (>60s since first call), reset counter to 1.
  4. Otherwise increment counter.
  5. If counter < 5, log "pending" and return (don't reroute yet).
  6. If counter >= 5 but less than 30s elapsed since first call, log "pending" and return.
  7. If counter >= 5 and 30s elapsed, proceed with reroute (auto-start on new route).
- **Why 5 calls / 60s**: Native fires every ~5s, so 5 calls = ~25s of consistent off-route detection. Tunnel GPS glitch may produce 1-3 spurious calls but rarely 5 within 60s. 60s window ensures even intermittent glitches expire before reaching threshold.
- **Why 30s minimum off-route duration**: Even when native fires rapidly (e.g., every 1s in noisy conditions), reaching 5 calls takes only a few seconds. Requiring 30s since the first off-route request ensures the deviation is persistent, not a tunnel exit GPS spike.
- **Why 100m accuracy guard**: Extra defense when simulator or real device reports poor-accuracy fixes that native projects off the route.
- **Alternative considered**: Accuracy guard alone — rejected because async processing makes it unreliable. Time debounce — rejected because it only prevents re-fires after first reroute, not the first false positive.
