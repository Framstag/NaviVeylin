## Why

During active navigation, when the app recalculates a route (rerouting), there is no visual feedback. The navigation status card looks identical whether the route is stable or being recalculated. This leaves users uncertain whether the app is working on a new route or has lost guidance. An animated border on the status card during rerouting provides immediate, glanceable feedback that rerouting is in progress.

## What Changes

- Add `isRerouting` boolean field to `NavigationState` data class
- Set `isRerouting = true` when `onRerouteRequest` fires, `false` when new route instructions arrive or navigation stops
- Modify `NavigationStateOverlay` composable to accept `isRerouting` parameter
- Add animated pulsing/scanning border to the `Card` when `isRerouting` is true
- Border uses Material 3 theme accent color, subtle animation (not distracting)

## Capabilities

### New Capabilities
- `rerouting-visual-feedback`: Animated border on navigation status card during route recalculation, giving users immediate visual confirmation that rerouting is in progress

### Modified Capabilities
<!-- No existing spec-level behavior changes -->

## Impact

- `NavigationState` data class — new `isRerouting: Boolean` field
- `NavigationViewModel` — set `isRerouting` in `onRerouteRequest` and clear it on `onRouteInstructions` / `stopNavigation`
- `NavigationStateOverlay` — new `isRerouting` param, animated border composable
- `MapCanvasScreen` — pass `navState.isRerouting` to overlay
- No native/JNI changes — pure UI + ViewModel change
