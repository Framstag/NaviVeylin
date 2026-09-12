## Why

The current street name shown in navigation and free-driving mode is not always the street the vehicle is driving on — reverse-geocode/description lookups pick the nearest address point or nearest way, which at intersections and near side streets is often a side street. Ref names (e.g. "B 1", "A 44") are missing entirely on the Android Auto surfaces. The native navigation engine already map-matches the vehicle to the route and resolves the exact way (`PositionAgent::Position::way`), but the JNI bridge discards it.

## What Changes

- **Routing mode (phone + Auto): street name comes from the route, not an area search.** The native `PositionAgent` resolves the way the vehicle is on from the route; `DispatchPositionEstimate` will expose that way's name + ref through `NavigationPosition`. The phone `NavigationViewModel` and the AA `AANavigationController` populate `currentRoadInfo` from it (the AA controller currently never populates it). The Auto `NavigationScreen` consumes `state.currentRoadInfo` instead of `getAddressAt`.
- **Free driving (Auto + phone): bearing-aware road selection.** New native lookup `getRoadAt(lat, lon, bearing)` performs map matching: ways in radius are ranked by how well their direction at the nearest point matches the vehicle bearing, then by distance — so the street actually driven on wins over a nearer side street. `FreeDrivingScreen` uses it instead of `getAddressAt` + `getMaxSpeedAt`. The phone map gains a current-street label in free driving (no active route), fed by the same lookup — the phone currently shows road info only during navigation.
- **Ref shown on Auto surfaces.** The free-driving and navigation street labels show "ref name" (e.g. "B 1 Hauptstrasse"), matching the phone's `currentRoadText` format. Ref comes from the route way (navigation) or `getRoadAt` (free driving).
- **Off-route during navigation** falls back to the bearing-aware `getRoadAt` instead of the nearest-way `getDescription` pick.
- **BREAKING (spec)**: `current-road-info` currently requires DescriptionService lookup "not by extracting it from the route description" — that requirement is reversed for the on-route case.

## Capabilities

### New Capabilities
- `road-lookup-bearing`: bearing-aware native road lookup (`getRoadAt`) that returns the road the vehicle is actually driving on (name, ref, max speed) for free-driving and off-route display.

### Modified Capabilities
- `current-road-info`: on-route road info source changes from DescriptionService area lookup to the route's resolved way (name + ref from the navigation engine); off-route falls back to the bearing-aware lookup.
- `auto/free-driving`: current street name requirement changes — ref shown, and the street is selected by bearing-aware map matching instead of nearest-address reverse geocode.
- `map-canvas-screen` (or `current-road-info`): phone free-driving street label — new requirement that the phone map shows the current street when no route is active.
- `auto/navigation-view`: current street name requirement changes — ref shown, and the name comes from the route's way at the current point instead of `getAddressAt`.

## Impact

- **Native (submodule patch, minimal, upstreamable)**: `app/src/main/cpp/libosmscout/libosmscout-client-java/src/OSMScoutClient.cpp` — `DispatchPositionEstimate` reads Name/Ref from `position.way`; new `getRoadAt` JNI method. Java API: `NavigationPosition` gains `wayName`/`wayRef`; `OSMScoutClient` gains `getRoadAt`. No Android dependencies added (CI Android-free gate).
- **App-owned JNI bridge**: `osmscout-client-java/src/main/java/com/framstag/libosmscout/client/OSMScoutClient.java` — declare `getRoadAt`; `NavigationPosition.java` (submodule java) — new fields.
- **Phone**: `app/src/main/java/com/naviveylin/navigation/NavigationViewModel.kt` — on-route road info from way; off-route fallback `getRoadAt`. `app/src/main/java/com/naviveylin/ui/map/MapCanvasViewModel.kt` + `MapCanvasScreen.kt` — free-driving street label fed by `getRoadAt`.
- **AA**: `app/src/main/java/com/naviveylin/navigation/AANavigationController.kt` — populate `currentRoadInfo` from way; `auto/src/main/java/com/naviveylin/auto/NavigationScreen.kt` — consume `state.currentRoadInfo`; `auto/src/main/java/com/naviveylin/auto/FreeDrivingScreen.kt` — use `getRoadAt`; `auto/src/main/java/com/naviveylin/auto/StreetNameUpdater.kt` — ref handling.
- **Tests**: `FakeOSMScoutClient`, `NavigationViewModel` tests, `AANavigationController` tests, auto screen tests, submodule JavaScout tests.
- **Guidelines**: `guidelines/UI.md` (Auto street-label parity), `guidelines/MapRendering.md` (if it enumerates road-info sources).
- **Specs**: `openspec/specs/current-road-info/spec.md`, `openspec/specs/auto/free-driving/spec.md`, `openspec/specs/auto/navigation-view/spec.md` (MODIFIED deltas), new `openspec/specs/road-lookup-bearing/spec.md`.
- **Rollback**: revert submodule pointer + Kotlin changes; old behavior (area lookup) is the fallback path and remains in place.
