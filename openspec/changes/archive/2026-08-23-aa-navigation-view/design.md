# Design: Android Auto Navigation View

## Context

The `:auto` module already shows a `NavigationTemplate` during navigation with a travel estimate and action strips, but the instruction content (next turn, lanes) is drawn by a custom canvas panel (`NavigationHintsOverlay`) on the map surface — the host-rendered instruction panel (maneuver, steps, lanes) is deliberately unused (archived design D4/W1). `NavigationState` already carries everything the host panel needs: `nextInstruction` (turn type, distance, street name), `instructions` (full step list), `currentStepIndex`, lane fields, ETA/distance, speed and reroute flags. The AA-only process (`AANavigationController`) populates all of these. Street name of the current road is NOT in the shared state for the AA process — free driving already solves this with `StreetNameUpdater` (native description lookup from position). See proposal.md for motivation; behavior contract in `specs/auto/navigation-view/spec.md`.

## Goals / Non-Goals

**Goals:**
- Render the current-step maneuver (+ distance) and the next step (next-next turn) in the host instruction panel; lane guidance on the current step
- Provide the full route description as an in-app route-description screen
- Draw the current street name on the navigation surface
- Keep compass rose, speed-limit badge, travel estimate and exit-anytime working
- Remove the redundant surface-drawn hint panel

**Non-Goals:**
- No phone-app or `core` changes (state is already sufficient)
- No new search/favorites/rerouting behavior
- No host API for compass or speed limit — those stay surface-drawn (host `NavigationTemplate` has no such elements)

## Decisions

### D1: Host instruction panel replaces the surface hint panel
Populate `NavigationTemplate` with a `RoutingInfo` (`setNavigationInfo`) carrying the current step (maneuver + distance), the next step and lane guidance; remove `NavigationHintsOverlay.drawHintPanel` from `NavigationScreen`'s overlay.
**Why**: The host panel is the "special navigation view" the user wants; rendering instructions twice (surface + host) conflicts visually. The host panel renders outside the map surface, so it also never covers the map.
**Alternative considered**: Keep surface hints and only add host steps — rejected: double instruction sources (the archived D4/W1 conflict the old design tried to avoid by picking one source).
**API constraint**: car-app 1.7.0 reworked the navigation model — `NavigationTemplate.Builder.setNavigationInfo(RoutingInfo)`, where `RoutingInfo` holds only current step (+ distance) and next step. There is no host step-list for the full route.

### D2: Maneuver icons as Canvas-generated `CarIcon` glyphs
New `ManeuverGlyphs` (mirrors existing `CarGlyphs`): render the arrow per `TurnType` into a bitmap-backed `CarIcon` (`IconCompat.createWithBitmap`), reusing the arrow geometry already ported in `NavigationHintsOverlay.drawTurnSymbol`. `NavigationTemplateMapper.maneuverTypeFromTurnType` already maps `TurnType` → `Maneuver` type constant.
**Why**: No new drawable resources; icons match the phone/current arrows exactly; `CarGlyphs` already established the pattern in this module.
**Alternative considered**: Vector XML drawables per turn type — rejected: 11+ new resources, style drift from the existing canvas arrows, more maintenance.

### D3: Host steps — live next instruction wins, list as fallback
`RoutingInfo` maps to: current step = the **live** `nextInstruction` (the native engine re-emits it with an updated distance on every position update), falling back to `instructions[currentStepIndex]`; next step = `instructions[currentStepIndex + 1]` (next-next turn). Distances are rounded for display (exact ≤ 50 m, ×50 m to 1 km, ×100 m above) and unit-split (km above 1 km) via `distanceForDisplay`. Both rebuild only on state change (`hasStateChanged` gate already exists).
**Why**: The `instructions` list is frozen at route start — using it froze the displayed distance after the first turn. Preferring the live instruction keeps the countdown working.
**Note**: The full route description is NOT host-renderable in car-app 1.7 (`RoutingInfo` has no step list) — see D8.

### D4: Lane guidance via step lanes
Lanes attach to the current `Step` (`addLane`) with recommended lanes via `LaneDirection.create(shape, isRecommended)`; the host requires a lanes image alongside lane data, so a lanes-strip `CarIcon` is rendered per state (`ManeuverGlyphs.lanesImage`, suggested lanes highlighted). Only set when `laneHintsEnabled` and `laneCount > 0`.
**Why**: Host renders lanes with the maneuver; matches the phone's lane hint toggle semantics.

### D5: Current street name via `StreetNameUpdater`
Reuse the free-driving mechanism: `StreetNameUpdater` resolves the road name from the native client at the current position (AA process has no `currentRoadInfo` in shared state); draw with `StreetNameLabel` bottom-centered on the surface; draw nothing when unnamed.
**Why**: Proven in the AA process (free driving), no state plumbing changes.
**Alternative considered**: Reading `state.currentRoadInfo` — rejected: only the phone `NavigationViewModel` populates it; the AA-only process never would, so the label would never show.

### D6: Keep surface indicators — badge shows current speed, sign shows limit
Compass rose stays as-is. The speed badge shows the **current speed** and the current speed limit is drawn as the round sign below it — during navigation (previously the navigation badge conflated limit and current speed) and free driving (unchanged). Warning color on the badge when over the limit (`auto-map-layout` "Speed-limit indicator during navigation"). Travel estimate unchanged; the explicit stop action is removed — system back is the single stop affordance (`auto` delta, `auto/navigation-view` "Leave navigation at any time").

### D7: `NavigationHintsOverlay` shrinks to arrow-symbol utilities
Delete the panel layout/drawing functions (`drawHintPanel`, `hintPanelGeometry`) and their tests; keep `drawTurnSymbol`/`drawLaneSymbol`/`formatDistance` as the shared canvas symbol renderer consumed by `ManeuverGlyphs`.
**Why**: Avoids duplicating the arrow geometry port; keeps the diff minimal.

### D8: Route description as an in-app screen
Since car-app 1.7 cannot render a full step list in the host panel, the route description is a `RouteDescriptionScreen` (`ListTemplate`, one row per remaining instruction with maneuver icon, distance + target street, current step marked) pushed from a route-list action in the navigation map action strip; BACK pops back and navigation keeps running.
**Why**: Full route description is explicitly requested; an in-app screen matches the existing `auto-map-layout` "content box acts as the app menu" pattern.
**Alternative considered**: Host-only current/next steps without a full list — rejected: does not deliver the route description the user asked for.

### D9: Speed-driven auto-zoom on the navigation view
Reuse `AutoZoomController` (already proven in free driving) in `NavigationScreen`: settings-gated (`autoZoomEnabled`), speed from the AA location provider, zoom committed with the same viewport update as heading rotation; manual zoom (`+`/`-` actions) suspends it until a speed-band change.
**Why**: Mirrors the phone's auto-zoom behavior; zero new logic — the controller is pure and already tested.
**Alternative considered**: Navigation keeps manual zoom only — rejected by user decision (auto-zoom expected on the navigation view too).

### D10: Route polyline on the navigation map
`NavigationState` carries the route geometry (`routeLats`/`routeLons`, populated by the AA controller from the `RouteEntry`); `AutoMapRenderer.setRoute` hands it to the native renderer (`renderWithRouteAndPois`), which draws the route with the stylesheet `_route` style. Re-renders on navigation start/reroute, cleared on stop.
**Why**: The car map must show the calculated route; the shared render util already supported the overlay — only wiring was missing.

## Risks / Trade-offs

- [Host maneuver icon looks different from phone arrow on some head units] → Icons are bitmaps rendered from the exact same geometry constants as the current arrows; worst case cosmetic, easily tuned in `ManeuverGlyphs`.
- [Step list too long / template invalidate storms] → Steps only rebuild on state change (`hasStateChanged`); host scrolls long lists.
- [`setSteps`/`setLanes`/`setManeuver` interplay with host chrome on AAOS] → Host-managed panel, nothing drawn on surface; surface overlay keeps only compass/speed/street name inside the stable area (existing behavior).
- [Lane guidance toggle now hides host lanes too] → Intended: setting stays authoritative.
- [Regression: next-turn distance/description removed from surface] → Host maneuver shows icon + distance + street name; step list shows full descriptions; covered by `auto/navigation-view` scenarios.

## Migration Plan

- Land behind existing screens; `NavigationScreen` is the only consumer. No flags needed — behavior switch is atomic in one screen.
- Rollback: revert to archived D4/W1 state (surface hints) from git history; specs archived with the change.

## Open Questions

None — all data paths verified in `AANavigationController`/`NavigationState`; remaining unknowns (exact icon look, host rendering quirks) are cosmetic and resolvable after first head-unit test without changing specs or task breakdown.
