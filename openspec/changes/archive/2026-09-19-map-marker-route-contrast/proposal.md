# Proposal

## Why

Three appearance defects reported from on-device use:

1. The vehicle position marker is a little small.
2. In light presentation the active route does not stand out from some road classes: the fill is `#ff000088` (53 % red, alpha-blended) and `primaryColor` in the day stylesheet is `#ec4044` red, so the route blends into primary roads while its white casing is invisible against the white residential roads it crosses.
3. In dark presentation the marker is too dark: the near-black casing `#0E1622` and the dark end of the blue gradient `#0D47A1` dominate on dark land.

All three are visual-quality defects on the primary navigation surfaces, so they are fixed together as one cosmetic change.

## What Changes

- Marker size: the shared footprint constant increases from 32 dp to 38 dp. Both surfaces (phone Compose canvas and Android Auto surface renderer) read the same constant, so the marker grows on both — parity is preserved deliberately.
- Marker dark palette: dark presentation gains a light-blue core gradient (`#BBDEFB` top, `#1E88E5` bottom) while rim (`#0D47A1`) and casing (`#0E1622`) stay as they are. The shape and the light-presentation palette are unchanged; the change is "lighter, not white" so the deliberate removal of the white casing ring (no stencil halo on dark land) is not reversed.
- Route day appearance: `stylesheets/include/route.oss` gains an `IF daylight` branch — opaque violet fill `#7b1fa2` with dark violet casing `#311b92` — and the hardcoded white casing color becomes a stylesheet constant. The `ELSE` (night) branch keeps today's red fill `#ff000088` with the white casing byte-identically, as requested. Stylesheet hex literals must be lowercase: uppercase makes `osmscout::Color::FromHexString` assert, the module load fails, and the renderer crashes natively (observed on the first emulator run and fixed by lowercasing).
- Not breaking: no API, data format, navigation behaviour, or user-visible workflow changes. Nothing moves; only colors and one dp constant change.
- Rollback path: revert the two files (`core/src/main/java/com/naviveylin/core/VehicleMarkerGeometry.kt` and the submodule `stylesheets/include/route.oss` commit) plus the associated spec/guideline lines. No migration, no data loss.

## Capabilities

### New Capabilities

- `route-appearance`: visual contract of the active route polyline on the map — its fill and casing colors per presentation (day/night), that the route must be distinguishable from every road class of the active stylesheet, and that both surfaces (phone, Android Auto) show the same route colors because they share one stylesheet.

### Modified Capabilities

- `gps-location-marker`: the 32 dp size requirement becomes 38 dp; the requirement that only the casing color branches on presentation is replaced by "casing and core gradient palette branch; geometry never branches" and the dark scenarios gain the lighter core.
- `auto-map-renderer`: same two changes on the car side — size 32 dp becomes 38 dp, and the night-state palette branch extends from the casing to the core gradient, keeping phone/car parity.

## Impact

**New capability**

- `openspec/specs/route-appearance/spec.md` (via this change's delta spec).

**Modified code**

- `core/src/main/java/com/naviveylin/core/VehicleMarkerGeometry.kt` — `SIZE_DP` 32f to 38f; new dark-presentation gradient constants; no change to `outlineVertices()`, `CASING_SCALE`, `RIM_WIDTH_H`, shadow constants.
- `core/src/test/java/com/naviveylin/core/VehicleMarkerGeometryTest.kt` — footprint assertion 32f to 38f; dark-palette assertions extended (dark core must be markedly lighter than the dark casing while staying below "white").
- `app/src/test/java/com/naviveylin/data/StylesheetHexColorCaseTest.kt` — **new guard**: every packaged stylesheet's hex color literals must be lowercase (the uppercase spelling asserted in `Color::GetHexValue`, failed the `route.oss` module load and crashed the app on the emulator), and the shared route include must keep the `IF daylight` branch with `#7b1fa2` / `#311b92` and the `@routeCasingColor` reference.
- `app/src/main/java/com/naviveylin/ui/map/LocationMarkerOverlay.kt` — consumer only; expected to need no code change because it derives size and palette from `VehicleMarkerGeometry`. Verify after the constant change.
- `auto/src/main/java/com/naviveylin/auto/AutoMapRenderer.kt` — same: consumer of the shared constants and of `setDarkPresentation`; verify no hardcoded 32 dp or day-only palette remains.

**Modified native stylesheet (submodule patch)**

- `app/src/main/cpp/libosmscout/stylesheets/include/route.oss` — this is a **submodule patch**, minimal and upstreamable (a `daylight` branch plus one stylesheet constant), committed on the `naviveylin-local` branch of the libosmscout submodule; the main repo bumps the gitlink in the same change. Precedent: the current white-casing/red comment block in that file is already a local submodule commit. No C++/JNI code is touched, so no bridge-module override is involved.
- The stylesheet reaches the APK through the existing `syncSubmoduleStylesheets` task; there is no committed snapshot to update.

**Android-specific components affected**

- `:core` module (`VehicleMarkerGeometry`, shared by phone and car).
- `:app` Compose overlay path (`LocationMarkerOverlay` inside `MapCanvasScreen`) and the `:auto` surface renderer path (`AutoMapRenderer` inside `MapScreen` / `NavigationScreen`).
- The native render bridge `OSMScoutClient.renderWithRouteAndPois` (`_route` type) is unchanged; only the stylesheet it reads changes.
- No manifest, resource-string, permission, or Gradle configuration change.

**Guidelines affected**

- `guidelines/UI.md` — the marker paragraph (32 dp, "white casing ring + dark rim + vertical blue gradient core") is superseded by the 38 dp size and the presentation-dependent core palette. Also carries the phone/car parity statement for the marker.
- `guidelines/UI.md` — the route paragraph records the shared route colors and states that every bundled style drawing a route uses them (the cycle style after this change).
- `guidelines/MapRendering.md` — reviewed; no change expected because the marker stays a post-render overlay and the route still comes from the native `_route` render. Confirm during apply and update only if a statement becomes wrong.

**Specs checked and deliberately not changed**

- `dark-mode` (defines resolved dark presentation, not marker or route colors), `map-styles` (stylesheet selection), `marker-render-accuracy` (marker/map alignment, not palette), `map-render` (rendering pipeline), `reroute-route-visibility` (route persistence across reroutes), `route-panel-ui` (route panel and `renderWithRoute()` usage).

**Scope**

General feature affecting both surfaces: the marker constants are shared by phone and Android Auto, and the single route stylesheet serves both. Nothing in this change is phone-only or auto-only.
