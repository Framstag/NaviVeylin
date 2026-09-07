# Proposal: move-routing-summary

## Why

After a route calculation the routing view (route panel) closes and a full-screen
route summary overlay appears. This is disruptive: the user loses the routing
dialog context (start/destination fields, vehicle selector) and must dismiss the
overlay to get back to it. The route summary belongs inside the routing dialog,
below the calculate button, so the user can review the result and still edit the
route.

## What Changes

- Extract the route summary content (total distance, estimated duration,
  scrollable step list) from `RouteSummaryDialog` into a reusable
  `RouteSummary` composable component.
- Show the `RouteSummary` component inside the route panel (`RoutePanel`),
  below the calculate button, whenever a route has been calculated.
- Show a "Start Navigation" button below the calculate button and above the
  routing summary component when a route has been calculated (replacing the
  current Done-state button layout).
- Stop auto-closing the route panel and auto-showing the full-screen summary
  overlay after a successful calculation — the summary is now inline in the
  panel.
- The routing status view press during navigation is unchanged: it keeps
  opening the full-screen details overlay (`NavigationDetailsOverlay`).
- Add per-step time to `NavigationDetailsOverlay`: each route instruction
  shows the time for its segment (e.g. "5 min"), matching the per-step time
  already shown in the route summary. Requires a `timeTo` field on the native
  `RouteInstruction` (JNI bridge in the libosmscout submodule).

## Capabilities

### New Capabilities

- `routing-summary`: Reusable route summary component (distance, duration,
  step list) that can be embedded in the route panel or shown as an overlay.

### Modified Capabilities

- `route-summary-dialog`: The summary is no longer auto-shown as a full-screen
  overlay after calculation; it is shown inline in the route panel. The dialog
  form remains available via the "Show Route" action.
- `route-panel-ui`: The route panel shows the routing summary component below
  the calculate button, with the Start Navigation button between the calculate
  button and the summary when a route is calculated.
- `navigation-status-details`: The expanded view shows per-step time for each
  route instruction, matching the route summary step list.

## Impact

- `app/src/main/java/com/naviveylin/ui/route/RouteSummaryDialog.kt` — extract
  summary content into `RouteSummary` component; dialog reuses it.
- `app/src/main/java/com/naviveylin/ui/route/RoutePanel.kt` — embed summary
  component and reorder action buttons in the Done state.
- `app/src/main/java/com/naviveylin/ui/route/RoutePanelViewModel.kt` — stop
  auto-setting `showSummaryDialog` on route success; keep panel open.
- `app/src/main/java/com/naviveylin/ui/map/MapCanvasScreen.kt` — remove the
  panel-dismiss `LaunchedEffect`; keep the status-view press wiring as-is.
- `app/src/main/cpp/libosmscout/libosmscout-client-java/` — add `timeTo` to
  `JavaRouteInstruction` (C++), `RouteInstruction.java`, and the JNI bridge
  (`OSMScoutClient.cpp`).
- `app/src/main/java/com/naviveylin/ui/navigation/NavigationDetailsOverlay.kt`
  — display per-step time.
- Tests: `RoutePanelComposeTest.kt`, `NavigationDetailsOverlayTest.kt`,
  `FakeOSMScoutClient.kt` may need updates.
