# Design: Remove explicit back button, add stop action on Android Auto navigation

## Context

Target-routing navigation screen (`NavigationScreen`, `:auto` module) originally showed an explicit `Action.BACK` in the `NavigationTemplate` map action strip next to the route-description action. System back delivered through `CarContext.getOnBackPressedDispatcher()` into `NavigationScreen.backCallback` → `navigationViewModel.stopNavigation()` already works and is covered by tests (`NavigationScreenTest`) and the auto/navigation-view spec ("Leave navigation at any time").

Iteration 1 removed `Action.BACK` and relied on system back alone. On the AAOS emulator, the host renders no back arrow for the navigation template — no visible leave affordance remained (the ETA card cannot host a button: car-app 1.7.0 `destinationTravelEstimate` is display-only, verified against the 1.7.0 AAR). Iteration 2 adds a visible stop action (X) to the map action strip. Free driving already follows this pattern (exit "x" in the map strip, system back exits — pinned by the auto/free-driving spec) and needs no change.

## Goals / Non-Goals

**Goals:**
- Visible leave affordance during navigation: the stop action (X) in the map action strip.
- No explicit back button on the map (no accidental stop from a back-arrow tap).
- Keep the route-description action in the map action strip.
- System back stays as a secondary leave path.

**Non-Goals:**
- A stop control inside the ETA/summarization card (no API surface in car-app 1.7.0).
- Changing free driving (already compliant; stop action + system back stay).
- Phone-app navigation UI (already uses the ETA-card X + `BackHandler`).

## Decisions

**D1: Remove `Action.BACK`, add a visible stop action (X) to the map action strip, keep `backCallback` (system back) as secondary.**
The `OnBackPressedDispatcher` path is the platform-standard leave signal on Android Auto/AAOS: hardware back, steering-wheel back and the host's back arrow on pushed screens all arrive as a back press. `NavigationScreen` already registers `backCallback { stopNavigation() }`; the session's `isNavigating` observer then pops to the root menu. Iteration 1 (implicit-only) proved insufficient on the AAOS emulator — no host back arrow, no visible affordance. A visible stop action (the same X glyph free driving uses) now sits in the map action strip and calls `stopNavigation()` directly; system back remains as secondary.
- Alternative considered: keep `Action.BACK` and drop nothing — rejected, BACK duplicates the stop action and sits on the map where a tap could feel like a map interaction; the stop X has clearer cancel semantics than a back arrow.
- Alternative considered: stop button inside the ETA card — rejected, no API (D4).
- Alternative considered: stop action in the top action strip with zoom — rejected by user decision; the map strip sits next to the ETA area and matches free driving.

**D2: Keep the route-description action in the map action strip.**
It is orthogonal to leaving navigation (opens the step list while navigation continues). Final map strip: stop (X) + route-description, both icon-only (car-app constraint: map-strip actions must be icon-only, no custom titles).

**D3: No change to free driving.**
Its map strip (exit "x" only) and system-back exit are already pinned by spec scenarios ("No menu affordance in free driving", "System back exits free driving"). The `exitAction` factory is renamed `stopAction` (shared X glyph) — call-site only, no behavior change.

**D4: The ETA card hosts no button.**
car-app 1.7.0 `NavigationTemplate` exposes no action slot inside `destinationTravelEstimate` (the summarization element). The map action strip is the nearest standard control area (bottom-left, adjacent to the ETA area on AAOS).

## Risks / Trade-offs

- [AAOS emulator renders no host back arrow during navigation] → The visible stop action (X) is the primary leave affordance; system back stays as secondary. The emulator gap is covered by the strip action — no host-dependent behavior required.
- [Test churn from the rename] → `exitAction` → `stopAction` touches `FreeDrivingScreen`, `NavigationScreen`, `NavigationScreenActionsTest`, `NavigationTemplateFactoryTest` in one commit; `NavigationScreenTest` (system back) stays green unchanged.
- [Map strip crowding] → two icon-only actions (stop + route-description); AA hosts reserve space for at least two map-strip actions (navigation previously rendered two).

## Migration Plan

Single commit, no runtime migration: drop the back arrow, add the stop action, update docs/comments/tests, update the spec delta. Rollback = remove the stop action and re-add `Action.BACK` (one line each).

## Open Questions

None.
