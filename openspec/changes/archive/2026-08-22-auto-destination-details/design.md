## Context

See proposal.md — Why. Current state: `:auto` module has an anonymous details screen embedded in `MapScreen.kt` (`createDetailsScreen`, PaneTemplate, reverse-geocodes via `getAddressAt` + `getDescription`), reachable only from map taps. `SearchScreen` and `PoiResultsScreen` call `navigationViewModel.navigateTo(lat, lon)` directly on row tap. `NavigationState` (`:core`) carries no destination data — only route progress. `NavigationScreen` already renders a `TravelEstimate` via `NavigationTemplateFactory`.

## Goals / Non-Goals

**Goals:**
- One shared `DetailsScreen` in `:auto` used by search, POI, and map-tap entry points
- Search/POI selection shows details first; navigation starts from "Navigate here"
- Destination identity (name/address/coords) survives into the active `NavigationTemplate`
- Additive change: phone app behavior untouched

**Non-Goals:**
- Favorites management in the auto details screen (phone-only today; `auto-favorites` spec exists separately)
- Changing deep-link behavior (still `navigateTo` directly — spec `auto-deep-links` unchanged)
- Destination display on the phone app's navigation UI
- Candidate-picker behavior changes (map-tap flow keeps its observable behavior)

## Decisions

### 1. Extract `DetailsScreen` as a first-class `Screen` class
New `DetailsScreen.kt` in `:auto`, constructor `(carContext, navigationViewModel, lat, lon, preloadedDescription: ObjectDescription? = null, showClear: Boolean = false, onClear: (() -> Unit)? = null, mag: Int = DEFAULT_MAGNIFICATION, nameHint: String? = null)`. Moves the anonymous screen body from `MapScreen.kt` verbatim, plus:
- `showClear` gates the "Clear" action — only the map-tap flow passes `true` (it owns the map selection state); search/POI flows rely on the standard back action.
- "Navigate here" passes the resolved address/name to `navigateTo` (see decision 2).
- **Actions are pane-level buttons, not row actions**: car hosts render PaneTemplate rows as non-actionable — row `addAction` buttons are never displayed (the original map-tap details screen had this bug: its "Navigate here"/"Clear" row actions were invisible). "Navigate here" is a primary pane button (`Action.FLAG_PRIMARY`); "Clear" is a second pane button when `showClear`. The pane caps at 4 rows (pane content limit; the host ignores extras).

**Alternatives considered:** keep the anonymous screen and duplicate it in Search/Poi screens — rejected (three copies of reverse-geocode + PaneTemplate logic). A shared factory function — rejected (a real class is testable and matches the other `:auto` screens). Row actions (`Row.Builder.addAction`) — rejected after the host showed them as invisible (design guide: pane rows are non-actionable).

### 2. Destination fields on `NavigationState` (`:core`)
Add to the data class (all defaulted, additive):
- `destLat: Double = Double.NaN`, `destLon: Double = Double.NaN`
- `destinationName: String? = null` (label/address when known)

`NavigationViewModel.navigateTo(destLat, destLon)` records coordinates; new overload `navigateTo(destLat, destLon, destinationName)` records the name. The details screen passes the best-known name (search label, resolved address, or null → coordinates shown).

**Alternatives considered:** separate `DestinationState` flow in `:core` — rejected (extra plumbing; `NavigationState` is already the single shared navigation contract). Storing destination only in the `:auto` session — rejected (phone and auto share `NavigationViewModel`; keeping it in the shared state keeps one source of truth).

### 3. Destination rendered on the `NavigationTemplate`
`NavigationScreen` reads `state.destinationName`/`destLat`/`destLon` and pushes them to `AutoMapRenderer.setDestinationMarker(lat, lon, name)`, which draws a pin + name label on the map surface in the same projection as the map bitmap (same mechanism as the GPS marker). The name is also carried into the navigation context via `NavigationState`; unnamed destinations fall back to coordinates-only display.

**Alternatives considered:** a destination marker via the template API (`NavigationTemplate.setDestinationMarker(Place)`) — rejected: the car-app 1.7 `NavigationTemplate` has no such method (only `setDestinationTravelEstimate`), and `Place` carries no text name. A separate destination pane pushed over the navigation template — rejected (extra tap; the surface pin + label is glanceable and driver-safe, which is the point of the car UI).

### 4. Search/POI row tap pushes `DetailsScreen`
`SearchScreen` and `PoiResultsScreen` row taps change from `navigateTo(...)` to `screenManager.push(DetailsScreen(carContext, navigationViewModel, lat, lon, preloadedDescription = null))`. Search results are `LocationEntry` (label + coords) — the label is passed as the initial name hint; the details screen still reverse-geocodes for the full address. POI results are `PoiEntry` — same pattern.

**Alternatives considered:** preloading the full `ObjectDescription` for search results — rejected (search returns `LocationEntry`, not descriptions; re-querying on the details screen matches the existing map-tap pattern and keeps search fast).

## Risks / Trade-offs

- [Reverse-geocode latency on details open] → Show coordinates immediately, fill address/description when the JNI call returns (existing pattern in the map-tap screen); loading is off the main thread.
- [Long destination names overflow the template] → Truncate/ellipsize the name; coordinates fallback when null.
- [`NavigationState` grows for phone consumers] → All new fields defaulted; phone code compiles unchanged.
- [Details screen pushed from three entry points drifts] → Single class, single PaneTemplate builder; entry points differ only in constructor args.
- [`ROW_CONSTRAINTS_SIMPLE`] → Not applicable: details uses PaneTemplate actions (pane-level buttons), not row click listeners or row actions.

## Migration Plan

Additive, no breaking changes. Land in one change: `:core` fields first (compile-safe), then `DetailsScreen` extraction, then entry-point rewiring, then template rendering. Rollback = revert the change; phone behavior never depends on the new fields.

## Open Questions

None — deferrable unknowns are covered by the fallbacks above (unnamed destination → coordinates; no description → no section).
