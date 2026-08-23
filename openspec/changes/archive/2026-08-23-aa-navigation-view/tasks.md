## 1. Maneuver icons and mapping (spec: auto/navigation-view — "Current and next step in host instruction panel")

- [x] 1.1 Create `ManeuverGlyphs.kt` in `auto/src/main/java/com/naviveylin/auto/` generating a bitmap-backed `CarIcon` per `TurnType` (mirroring `CarGlyphs`), reusing `NavigationHintsOverlay.drawTurnSymbol` geometry, plus a lanes-strip image (`lanesImage`) with suggested lanes highlighted; verify `ManeuverGlyphsTest` asserts a non-null `CarIcon` for every `TurnType` value and lanes-image rendering.
- [x] 1.2 Extend `NavigationTemplateMapper` with `RouteInstruction` → `Step` (maneuver via existing `maneuverTypeFromTurnType`, road + cue text) and `RoutingInfo` mapping (current step + distance, next step, lanes via existing `laneDirectionShapeFromLaneTurn` with recommended marks); verify `NavigationTemplateMapperTest` covers step/lane/routing mapping incl. recommended lanes, next-step presence and null-safety.

## 2. Template factory wiring (spec: auto/navigation-view — host instruction panel)

- [x] 2.1 Extend `NavigationTemplateFactory.buildNavigationTemplate` to accept and set `RoutingInfo` via `setNavigationInfo` (optional, absent when null); verify `NavigationTemplateFactoryTest` asserts the host panel carries routing info when supplied and stays unset otherwise, and that the not-navigating branch stays strip-only.

## 3. NavigationScreen integration (spec: auto/navigation-view)

- [x] 3.1 In `NavigationScreen.buildTemplate`, build `RoutingInfo` from state (honoring `laneHintsEnabled` + lanes image) and pass it into the factory; verify `:auto` unit tests pass.
- [x] 3.2 Remove `NavigationHintsOverlay.drawHintPanel` call from `NavigationScreen` overlay; keep `SurfaceIndicators` (compass rose + speed badge) and add `StreetNameUpdater` + `StreetNameLabel.draw` for the current street name (bottom-centered, hidden when unnamed); verify `StreetNameUpdaterTest` still passes.

## 4. Route description screen (spec: auto/navigation-view — "Route description screen")

- [x] 4.1 Add `RouteDescriptionScreen` (ListTemplate over `routeDescriptionRows` from the current step, current step marked, maneuver icon per row) and push it from a route-list action (`CarGlyphs.routeList` + `NavigationScreenActions.routeListAction`) in the navigation map action strip; verify `NavigationScreenActionsTest` covers the new action (icon-only, driving-safe, callback) and mapper tests cover row ordering/titles/current mark.

## 5. Auto-zoom on navigation view (spec: auto/navigation-view — "Speed-driven auto-zoom during navigation")

- [x] 5.1 Wire `AutoZoomController` into `NavigationScreen` (gated by `settings.autoZoomEnabled`, speed from the AA location provider, zoom committed with the heading rotation, manual zoom suspends until band change, suspend on stop/destroy); verify `:auto:test` passes (controller behavior already covered by `AutoZoomControllerTest`).

## 6. Cleanup

- [x] 6.1 Strip panel layout/drawing from `NavigationHintsOverlay` (`drawHintPanel`, `hintPanelGeometry` and panel constants), keeping `drawTurnSymbol`/`drawLaneSymbol`/`formatDistance` as shared symbol renderers; update `NavigationHintsOverlayTest` to symbol-only tests; verify `:auto:test` passes.
- [x] 6.2 Remove obsolete surface-hint references in comments/docs and the `auto-map-layout` "Navigation hints left-oriented" requirement; verify `openspec validate --changes aa-navigation-view` passes.

## 7. Build verification

- [x] 7.1 Run `./gradlew :auto:test` and confirm all auto-module unit tests pass.
- [x] 7.2 Run `./gradlew :auto:assembleDebug` and confirm the module compiles.

## 8. Verification follow-ups (spec: auto/navigation-view)

- [x] 8.1 Update design D3 to the live-next-instruction preference (current step = live `nextInstruction`, instructions list fallback; rounded distances).
- [x] 8.2 Add spec requirement "Route line on navigation map" + design D10 (route polyline via `renderWithRouteAndPois` `_route` style, updates on reroute, hidden when not navigating); verify `:auto:test` passes.
- [x] 8.3 Add "Distance rounded for display" scenarios (current step + remaining distance) to `auto/navigation-view`; verify `:auto:test` passes.
- [x] 8.4 Add `NavigationScreen.backCallback` factory + `NavigationScreenTest` covering back→stop-navigation handler; verify `:auto:test` passes.
