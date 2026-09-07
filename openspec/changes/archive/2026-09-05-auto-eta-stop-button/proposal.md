## Why

During AA routing the user sees two cancel affordances: the host-rendered ETA
card stop button and the app's own "x" button in the navigation map action
strip. The ETA card stop button does nothing: the host delivers it via
`NavigationManager.onStopNavigation()`, which is dropped because the app never
registers a `NavigationManagerCallback` and never calls
`NavigationManager.navigationStarted()` (both gates in the car-app 1.7.0
`NavigationManager` implementation). The map action strip "x" works because it
is a plain app action wired directly to `stopNavigation()`. Two buttons for one
action, one dead — the ETA card stop button is the host-standard affordance and
should be the single one.

## What Changes

- `NavigationSession` registers a `NavigationManagerCallback` and calls
  `NavigationManager.navigationStarted()` when navigation starts, and
  `navigationEnded()` + `clearNavigationManagerCallback()` when it stops. The
  callback's `onStopNavigation()` calls `navigationViewModel.stopNavigation()`,
  making the host ETA card stop button functional.
- `NavigationScreen` drops the app-owned stop action from the navigation map
  action strip (route-description action stays). The ETA card stop button
  becomes the single visible stop affordance; system back remains as the
  secondary leave affordance.
- `NavigationScreenActions.stopAction` becomes dead code and is removed.
- Spec `auto/navigation-view` — "Leave navigation at any time" changes: the
  stop action moves from the map action strip to the host ETA card.
- Additive behavior fix, no manifest/API/native changes. Rollback: revert the
  session callback wiring and restore the map action strip stop action.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `auto/navigation-view` — "Leave navigation at any time": the stop action is
  no longer an app-drawn "x" in the navigation map action strip; it is the
  host ETA card stop button, delivered via `NavigationManagerCallback`.
  System back still stops navigation.

## Impact

- `auto/src/main/java/com/naviveylin/auto/NavigationSession.kt` — register
  `NavigationManagerCallback` + `navigationStarted()`/`navigationEnded()`/
  `clearNavigationManagerCallback()` in the `isNavigating` observer; cleanup on
  session destroy (`clearNavigationManagerCallback` throws while navigating,
  so `navigationEnded()` must run first).
- `auto/src/main/java/com/naviveylin/auto/NavigationScreen.kt` — remove the
  stop action from the map action strip in `buildTemplate`.
- `auto/src/main/java/com/naviveylin/auto/NavigationScreenActions.kt` — add the `navigationMapActionStrip` factory (route-description action only); `stopAction` stays (FreeDrivingScreen still uses it).
- Tests: `NavigationTemplateFactoryTest` (map action strip 2 → 1 action),
  `NavigationScreenActionsTest` (stopAction cases removed), new
  `NavigationSession` test (callback registered on navigation start, cleared on
  end, `onStopNavigation` → `stopNavigation`).
- Spec: `openspec/specs/auto/navigation-view/spec.md` — "Leave navigation at
  any time" requirement.
- Guidelines: `guidelines/UI.md` does not document the stop button or map
  action strip contents — no guideline change.
- No native/JNI, manifest, or Gradle changes. Scope: `:auto` module, routing
  mode only; phone UI and the non-navigation `MapScreen` full-screen template
  (which keeps its own exit action) are unaffected.
