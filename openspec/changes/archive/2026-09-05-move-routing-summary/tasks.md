# Tasks: move-routing-summary

## 1. RouteSummary component

- [x] 1.1 Extract stats block (distance + duration) and step list from `RouteSummaryDialog` into `RouteSummary` composable in `ui/route/RouteSummary.kt` (signature: `routeEntry`, `steps`, `activeStepIndex`).
- [x] 1.2 Refactor `RouteSummaryDialog` to embed `RouteSummary` (keep scrim, surface, header, Start/Stop Navigation button).

## 2. Route panel inline summary

- [x] 2.1 In `RoutePanel.kt` Done state: keep Calculate button, add Start/Stop Navigation button below it, embed `RouteSummary` below the button, keep Show Route and Clear Route.
- [x] 2.2 In `RoutePanelViewModel.onSuccess`: stop setting `showSummaryDialog` (leave false) so the panel stays open.
- [x] 2.3 In `MapCanvasScreen.kt`: remove the `LaunchedEffect(routeState.showSummaryDialog)` that dismisses the route panel.

## 3. Per-step time in NavigationDetailsOverlay (native)

- [x] 3.1 Add `double timeTo{0.0}` to `JavaRouteInstruction` struct in `OSMScoutClient.cpp`.
- [x] 3.2 `CollectCallback`: track `prevTime`/`time` in `BeforeNode`; set `instr.timeTo` (seconds) in each On* handler.
- [x] 3.3 `CreateJavaRouteInstruction` + ctor signature in `GetNavigationListenerMethods`: pass `timeTo` (double).
- [x] 3.4 Add `public final double timeTo` to `RouteInstruction.java`; 5-arg constructor defaults to `0.0`.

## 4. Display per-step time

- [x] 4.1 In `NavigationDetailsOverlay.kt` step row: render time under distance (format `"Xh Ymin"` / `"Y min"`, hidden when `timeTo <= 0`).

## 5. Tests

- [x] 5.1 `NavigationDetailsOverlayTest.kt`: add per-step time display test (time text visible, matches formatted value).
- [x] 5.2 `RoutePanelComposeTest.kt`: verify Done state shows Calculate, Start Navigation, and summary inline (panel not dismissed).
- [x] 5.3 Run `./gradlew :app:testDebugUnitTest` (or `run-tests` skill) and fix failures.
- [x] 5.4 On "Start Navigation" press, close the route panel (MapCanvasScreen `onStartNavigation` → `dismissRoutePanel()`).
- [x] 5.5 Add Show Route action test (`RoutePanelComposeTest.showRouteOpensSummaryDialog`).
- [x] 5.6 Add `RouteSummary` active-step highlight tests (`RouteSummaryDialogComposeTest`), with `activeStep`/`step` testTags.
- [x] 5.7 Wire `setActiveStepIndex` from the navigation flow (`NavigationViewModel`: startNavigation, onNextRouteInstruction, onRouteInstructions).
- [x] 5.8 Rename details-sheet button "Navigate to" → "Calculate route" (`calculate_route` string, en+de; tests updated).
