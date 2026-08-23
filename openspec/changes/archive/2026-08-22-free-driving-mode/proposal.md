## Why

Android Auto currently has no free-driving mode: the "Free driving" row in the map menu (`MapTemplateFactory.buildMenuContent`) only stops any active navigation and re-centers the browse map — it never switches to a navigation-style view. Drivers without a destination get a static browse map instead of a live, vehicle-following view with a compass, position marker and current street name. Free driving is a standard navigation-app mode (Google Maps, OSMAnd) and closes the gap between browse mode and turn-by-turn navigation.

## What Changes

- **BREAKING** (behavioral): selecting "Free driving" in the Android Auto map menu (`MapScreen` content menu) now pushes a new full-screen free-driving view instead of merely stopping navigation and re-centering.
- New `FreeDrivingScreen` in the `:auto` module, rendered with `NavigationTemplate` so the host provides the compass and treats the screen as a navigation surface (full-bleed map).
- Free-driving view features:
  - Compass shown (host `NavigationTemplate` compass; surface compass rose from `SurfaceIndicators` rotates with the map).
  - GPS location marker drawn on the map surface (`AutoMapRenderer.setGpsMarker`), as in `NavigationScreen`.
  - Follow mode activated: the viewport re-centers on the GPS position (`AutoMapRenderer.reCenter`), matching the shared `followMode` setting behavior of the browse map.
  - North-up disabled: the map rotates heading-up with the vehicle bearing (viewport angle = bearing), independent of the `navNorthUp` setting — free driving is always heading-up.
  - Current street name shown: reverse-geocoded via `OSMScoutClient.getAddressAt` on the GPS position (throttled), displayed as a surface overlay label.
  - No navigation hints, no lane hints, no travel estimate / ETA — there is no destination, so `NavigationHintsOverlay` and host instruction content are not drawn.
  - "Exit free driving" action (map action strip, alongside back) pops back to the map view (`MapScreen`).
- Free driving is a distinct screen, not a mode of the turn-by-turn `NavigationScreen`: it does not set `isNavigating`, so the `NavigationSession` state machine (root screen / `NavigationScreen` switch on `isNavigating`) is untouched.
- New pure/factory logic and render helpers extracted for unit tests, following the existing pattern (`NavigationTemplateFactory`, `SurfaceIndicators`, `NavigationHintsOverlay`).

## Capabilities

### New Capabilities
- `auto/free-driving`: destination-free driving view on Android Auto — entered from the map menu, shows a heading-up vehicle-following map with compass, GPS marker, current street name, no turn-by-turn hints, and an exit action back to the map view.

### Modified Capabilities
- None. Existing capabilities (`auto`, `auto-map-layout`, `auto-speed-zoom`, `navigation-status-details`, `next-turn-overlay`, `lane-guidance`) keep their requirements; free driving reuses their components without changing their contracts.

## Impact

- `:auto` module (`auto/src/main/java/com/naviveylin/auto/`):
  - New `FreeDrivingScreen.kt` (screen + surface callback + GPS observation).
  - `MapScreen.kt`: wire the existing `onFreeDriving` menu callback to push `FreeDrivingScreen`.
  - `NavigationTemplateFactory.kt` or a new factory: free-driving `NavigationTemplate` (no destination estimate, no instruction panel).
  - `SurfaceIndicators.kt` / new overlay helper: street-name label drawing on the surface.
  - `AutoMapRenderer.kt`: reuse follow mode, `setGpsMarker`, heading-up viewport rotation (no changes expected).
- `:core` (`AutoSettings`): no changes — free driving ignores `navNorthUp` (always heading-up) and `laneHintsEnabled` (never shows lanes).
- Native `libosmscout-client-java`: no changes — street name uses existing `getAddressAt` JNI.
- Android Auto host surface: `NavigationTemplate` requires the navigation surface (`ACCESS_SURFACE`), already declared in `app/src/main/AndroidManifest.xml`.
- Tests: new Robolectric/unit tests in `auto/src/test/java/com/naviveylin/auto/` (screen factory, street-name overlay, menu wiring, exit action) following the existing test patterns (`NavigationTemplateFactoryTest`, `NavigationScreenActionsTest`, `MapStripActionsTest`).
