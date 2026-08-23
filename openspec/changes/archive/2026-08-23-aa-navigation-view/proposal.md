# Proposal: Android Auto Navigation View (aa-navigation-view)

## Why

Navigation can now be started from favorites, search results and POI results (the latter two via the details view) — all of them initiate turn-by-turn navigation on the phone. On Android Auto that navigation is currently shown with a custom, surface-drawn hint panel (`NavigationHintsOverlay`): only the next turn, next-turn distance, description and a lane row. Android Auto's own `NavigationTemplate` navigation view — host-rendered maneuver, step list (route description, next-next turns) and lane guidance — is deliberately not used (archived design D4/W1). We want to switch to the host's navigation view so the head unit renders the full instruction set natively: next turn + next-next turn, lane instructions, time and distance to target, route description, current street name, maximum speed and the compass — with the ability to leave navigation at any time.

## What Changes

- Populate the host `NavigationTemplate` instruction panel from `NavigationState` (car-app 1.7 `RoutingInfo` model):
  - current step — next turn (type icon + distance + target street)
  - next step — next-next turn
  - lane guidance (with lanes-strip image), recommended lanes marked
  - `setDestinationTravelEstimate(...)` — remaining time + distance (already present, kept)
- Add the route description: full instruction list from the current step on, shown in an in-app route-description screen (the host panel only renders current + next step), opened from a new route-list action on the navigation view.
- Add maneuver/lanes-strip icons (`CarIcon`, Canvas-glyph approach like `CarGlyphs`) — host steps require them.
- Remove the surface-drawn `NavigationHintsOverlay` panel from the navigation screen (host panel replaces it; two instruction sources would conflict).
- Draw the current street name on the navigation surface (reuse `StreetNameLabel`/`StreetNameUpdater` from free driving; source: `NavigationState.currentRoadInfo`).
- Keep the surface-drawn compass rose and speed-limit badge (`SurfaceIndicators`) during navigation — the host template has no APIs for compass or speed limit.
- Keep leaving navigation at any time: Stop action + system BACK (already present, unchanged).
- No phone-app changes: `NavigationState` already carries `instructions` (full step list incl. next-next), lanes, ETA, distance, speed and current road info.

## Capabilities

### New Capabilities
- `auto/navigation-view`: Host-rendered Android Auto navigation view — next turn maneuver, step list / route description with next-next turns, lane guidance, travel estimate, current street name on the surface, exit-anytime.

### Modified Capabilities
- `auto-map-layout`: REMOVE the "Navigation hints left-oriented" requirement (surface-drawn hint panel is replaced by the host instruction panel); keep "Compass rose during navigation" and "Speed-limit indicator during navigation".
- `auto`: REMOVE the "Stop navigation action" requirement — the explicit stop action is dropped; system back is the single stop affordance (covered by `auto/navigation-view` — "Leave navigation at any time").

## Impact

- `auto/src/main/java/com/naviveylin/auto/NavigationScreen.kt` — build `RoutingInfo` (current step + distance, next step, lanes) from state; drop `NavigationHintsOverlay` panel drawing; draw street-name label on surface; add route-list action.
- `auto/src/main/java/com/naviveylin/auto/NavigationTemplateFactory.kt` — accept `RoutingInfo` and wire into `NavigationTemplate.setNavigationInfo`.
- `auto/src/main/java/com/naviveylin/auto/NavigationTemplateMapper.kt` — add `RouteInstruction` → `Step` / `RoutingInfo` mapping (reusing `maneuverTypeFromTurnType` / `laneDirectionShapeFromLaneTurn`) plus route-description row data.
- `auto/src/main/java/com/naviveylin/auto/ManeuverGlyphs.kt` (new) — Canvas-generated `CarIcon` per `TurnType` + lanes-strip image (mirrors `CarGlyphs`).
- `auto/src/main/java/com/naviveylin/auto/RouteDescriptionScreen.kt` (new) — `ListTemplate` with all remaining instructions, current step marked.
- `auto/src/main/java/com/naviveylin/auto/CarGlyphs.kt` — add route-list action glyph.
- `auto/src/main/java/com/naviveylin/auto/NavigationScreenActions.kt` — stop action/glyph removed (system back is the single stop affordance).
- `auto/src/main/java/com/naviveylin/auto/NavigationHintsOverlay.kt` — shrunk to shared turn/lane symbol renderer.
- Tests: `NavigationTemplateFactoryTest`, `NavigationTemplateMapperTest`, `NavigationHintsOverlayTest`, `NavigationScreenActionsTest` updated; new `ManeuverGlyphsTest`.
- `core`/`app` modules — no changes.
