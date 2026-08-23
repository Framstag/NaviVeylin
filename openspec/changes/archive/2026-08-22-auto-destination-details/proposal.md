## Why

On the phone, the details view is the cornerstone that connects a search result to the start of routing: it shows the destination's name, address, and OSM description, and routing starts from its "Route to location" action. Android Auto currently skips this step — search and POI result taps call `navigateTo()` directly, and once routing starts the destination identity (name/address) is discarded because `NavigationState` carries no destination data. Drivers on the car screen get no confirmation of *what* they selected before navigation begins, and no destination context during navigation.

## What Changes

- Extract the anonymous details screen currently embedded in `MapScreen.kt` (`createDetailsScreen`) into a first-class `DetailsScreen` class in the `:auto` module, shared by all entry points.
- `SearchScreen` and `PoiResultsScreen` row taps push `DetailsScreen` instead of starting navigation directly; routing starts from the details screen's "Navigate here" action.
- `NavigationState` (in `:core`) gains destination fields (lat/lon + optional name/address); `navigateTo` records them.
- `NavigationScreen` shows the destination name alongside the existing travel estimate during active navigation, and the `NavigationTemplate` carries a destination marker.
- Map-tap flow (`auto-map-destination-picker`) keeps its behavior but uses the shared `DetailsScreen`.

## Capabilities

### New Capabilities
- `auto-destination-details`: Details screen in the Android Auto flow — reachable from search results, POI results, and map taps — showing destination name/address/description, with routing started from it, and destination identity carried into the active navigation context.

### Modified Capabilities
- `auto-search`: Search result selection currently "transitions to the destination picker" (vague, unimplemented — taps start navigation directly). Requirement becomes explicit: a search result tap opens the details screen, and navigation starts only from its "Navigate here" action.

Note: `poi-search` (phone spec) and `auto-map-destination-picker` are NOT modified — the phone POI sheet already opens a details dialog, and the map-tap flow's observable behavior is unchanged (shared `DetailsScreen` is an implementation detail). Auto POI results are covered by the new `auto-destination-details` capability.

## Impact

- `:auto` module:
  - `MapScreen.kt` — remove anonymous `createDetailsScreen`, use shared `DetailsScreen`
  - new `DetailsScreen.kt` — extracted PaneTemplate screen (reverse-geocode + object description, "Navigate here" / "Clear" actions)
  - `SearchScreen.kt`, `PoiResultsScreen.kt` — row tap pushes `DetailsScreen` instead of `navigateTo`
  - `NavigationScreen.kt`, `NavigationTemplateFactory.kt` — destination name + marker from `NavigationState`
- `:core` module:
  - `NavigationState.kt` — add destination fields
  - `NavigationViewModel.kt` — `navigateTo` records destination; optional destination-name overload
- Tests: `:auto` unit tests (`SearchScreenMapperTest`, `StartupScreensTest`, `NavigationTemplateFactoryTest`, `MapScreenTest`) updated for the new flow; new `DetailsScreen` tests.
- No manifest changes; no new dependencies (Car App Library 1.7 already in use).
