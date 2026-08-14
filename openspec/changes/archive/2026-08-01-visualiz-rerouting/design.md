## Context

See proposal.md for motivation. Current state: `NavigationState` data class has no `isRerouting` field. `NavigationViewModel.onRerouteRequest()` triggers route recalculation via `RoutePanelViewModel` but provides no UI feedback. `NavigationStateOverlay` is a `Card` composable at bottom-center during navigation.

## Goals / Non-Goals

**Goals:**
- Add `isRerouting: Boolean` to `NavigationState` with correct lifecycle (set on reroute request, cleared on new instructions or stop)
- Animated border on `NavigationStateOverlay` Card during rerouting
- Subtle, non-distracting animation using Material 3 theme colors

**Non-Goals:**
- No changes to native JNI or libosmscout code
- No changes to reroute logic itself (route calculation flow stays same)
- No sound/vibration/haptic feedback
- No changes to `NextTurnOverlay` or other navigation UI

## Decisions

1. **State field over separate flow** — `isRerouting` lives in `NavigationState` alongside other nav state. Simpler than a separate `StateFlow<Boolean>`. Consumers read one state object.

2. **Clear on `onRouteInstructions` not `onArrivalEstimate`** — New route instructions signal reroute complete. `onArrivalEstimate` fires continuously during normal navigation and would prematurely clear the flag.

3. **`drawBehind` with animated border over `BorderStroke`** — Compose `Modifier.border()` with `BorderStroke` doesn't support animation well. Use `Modifier.drawBehind {}` with an animated `Float` for alpha/offset to create a pulsing or scanning effect. This keeps the border inside the Card shape.

4. **`rememberInfiniteTransition` for animation** — Use Compose's `rememberInfiniteTransition` with `animateFloat` for a continuous subtle pulse while `isRerouting` is true. No coroutine lifecycle management needed.

5. **Material 3 `primary` color for border** — Uses existing theme color, no new color resources. Subtle alpha animation (0.3 → 1.0 → 0.3) creates gentle pulse.

## Risks / Trade-offs

- [Animation performance] → `drawBehind` on a simple Card is cheap. No recomposition during animation since `drawBehind` is a draw-phase lambda. Negligible impact.
- [False positive during normal nav] → `onRerouteRequest` is the only trigger. If libosmscout fires it spuriously, the border briefly flashes. Acceptable — user sees app is "thinking" which is truthful.
- [Accessibility] → Visual-only feedback. If users need non-visual reroute indication, this can be extended later. Non-goal for now.
