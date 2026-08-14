## Why

Rerouting animated border runs constantly instead of only during active reroute. No visual feedback when vehicle first leaves planned route before reroute calculation completes.

## What Changes

- Fix: `isRerouting` animation lifecycle — only animate during reroute window (off-route detected → new route calculated)
- Fix: Restructure animation to avoid `rememberInfiniteTransition` inside conditional (Compose slot table violation causes stale animation state)
- Add: `isOffRoute` state separate from `isRerouting` for red tint
- Add: Soft reddish background tint on routing status card when off-route
- Ensure: Animation stops when `onRouteInstructions` delivers new route

## Capabilities

### New Capabilities
- `off-route-indicator`: Visual red tint on routing status view when vehicle leaves planned route, before and during reroute calculation

### Modified Capabilities
- `rerouting-visual-feedback`: Animated border only during active reroute calculation, stops when new route shown. Add `isOffRoute` state to navigation state model

## Impact

- `NavigationStateOverlay.kt` — restructure animation, add background tint
- `NavigationViewModel.kt` — add `isOffRoute` to `NavigationState`, set on reroute request, clear on new route
- `MapCanvasScreen.kt` — pass `isOffRoute` to overlay
