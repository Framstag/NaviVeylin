## Why

During turn-by-turn navigation and free driving the map is locked to the vehicle: follow mode re-centers on every GPS fix, auto-zoom adjusts magnification by speed, and heading-up rotation spins the map with the bearing. The driver cannot look ahead along the route, inspect an upcoming junction, or re-check a just-passed exit. The root map view (`MapScreen`) already supports panning via `PanModeListener` + `SurfaceCallback.onScroll/onScale`, and the car-app API exposes the same mechanism on `NavigationTemplate` — the navigation and free-driving screens just never wire it up. The free-driving spec already anticipates it ("user does not pan the map" in the Follow-mode scenario).

## What Changes

- Register a `PanModeListener` on the `NavigationTemplate` built by both `NavigationScreen` (routing) and `FreeDrivingScreen` (free driving), so the host renders a pan affordance and forwards pan gestures to the surface while pan mode is active.
- Add the built-in `Action.PAN` to both map action strips: `NavigationScreenActions.navigationMapActionStrip` (routing, alongside the route-description action) and the free-driving strip (alongside the stop action).
- Implement `onScroll`/`onScale` in both screens' `SurfaceCallback` (same pattern as `MapScreen`: `ProjectionUtils.dragDeltaToNewCenterRotated` for pan, `zoomStep` + `zoomAtCursor` for pinch), so the map moves with the finger/rotary input.
- On pan-mode entry: disengage follow (`setViewport` — already flips `followMode` off), suspend speed-driven auto-zoom (`autoZoomController.suspend()`), and freeze heading-up rotation so the map does not spin under the gesture.
- On pan-mode exit: re-engage follow so guidance resumes (smooth `reengageFollow()`, no snap).
- While panned, GPS fixes keep updating the position marker only — the viewport stays where the user left it (existing `setGpsMarker` behavior when `followMode` is off).

## Capabilities

### New Capabilities

- `auto/map-pan`: manual map panning (pan + pinch zoom) on the Android Auto map surfaces, with follow/auto-zoom/rotation disengagement while panned and re-engagement on exit.

### Modified Capabilities

- `auto/navigation-view`: the navigation view gains a pan affordance and manual map movement during routing; the map action strip gains the PAN action.
- `auto/free-driving`: the free-driving view gains a pan affordance and manual map movement; the map action strip gains the PAN action.

## Impact

- `auto/src/main/java/com/naviveylin/auto/NavigationScreen.kt` — `SurfaceCallback` gains `onScroll`/`onScale`; pan-mode state machine (enter/exit).
- `auto/src/main/java/com/naviveylin/auto/FreeDrivingScreen.kt` — same: `SurfaceCallback` gains `onScroll`/`onScale`; pan-mode state machine (enter/exit).
- `auto/src/main/java/com/naviveylin/auto/NavigationTemplateFactory.kt` — `buildNavigationTemplate` gains a `panModeListener` parameter.
- `auto/src/main/java/com/naviveylin/auto/NavigationScreenActions.kt` — `navigationMapActionStrip` gains `Action.PAN`; free-driving strip gains `Action.PAN`.
- `auto/src/main/java/com/naviveylin/auto/AutoMapRenderer.kt` — no API change expected; `setViewport`/`reengageFollow`/`isFollowMode` already support the flow.
- `auto/src/main/java/com/naviveylin/auto/AutoZoomController.kt` — no change; `suspend()`/`isSuspended()` already exist.
- `:core` `ProjectionUtils` — reused as-is (already shared by `MapScreen`).
- Tests: `NavigationTemplateFactoryTest`, `NavigationScreenActionsTest`, `MapStripActionsTest`, new pan-mode/surface tests in the `:auto` suite.
- No new dependencies. Requires car-app 1.7.0 (already the pinned version) — `NavigationTemplate.Builder.setPanModeListener` is `RequiresCarApi(2)`.
- Host behavior on real AAOS/AA hosts needs on-device verification (emulator/head unit) — whether the host renders the pan affordance during active turn-by-turn and forwards gestures.
