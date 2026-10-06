# Proposal

## Why

A `RouteEntry` is a platform type from the JNI bridge: `latitudes`/`longitudes` are Java arrays and
can be **absent** (null), not merely empty. The route panel's publish block reads them without a
guard (`RoutePanelViewModel.adoptRoute`, `RoutePanelViewModel.kt:834-836` reads
`val lats = route.latitudes` and then `lats.size`), while the same file guards the identical concern
elsewhere (`:690` `route.latitudes ?: return null`, and `onSuccess` returns
`route.latitudes ?: return null` in the step-segment helpers). So the panel treats missing geometry
as normal on one path and crashes on another.

`adoptRoute` is the path a route the engine acquired **for itself** travels — the car-only adopt
(destination from a car deep link with no phone UI) and the reroute re-acquisition — so the crash sits
on the shared-state path both surfaces depend on, and the caller is inside
`viewModelScope.launch { engine.state.collect { … } }` (`NavigationViewModel.kt:69-105`, adopt at
`:87`) with no catch: an exception there is uncaught on the main thread. The defect was found while
writing the screen-level exit test: its first version built a `RouteEntry` with distance, duration and
descriptions only, and failed with `NullPointerException: Cannot read the array length because "lats"
is null` before any assertion ran (`TODO.md` §137). Existing tests all supply a polyline, so no case
covers the branch.

## What Changes

- Guard the adopt publish path: a route adopted from the engine **without** polyline geometry SHALL
  stop crashing — the session/panel state is still adopted, and no route geometry is published (the
  map keeps its previous drawing, exactly as the camera rule already requires for degenerate
  geometry).
- Guard the calculation-success publish on the same rule (`:514` builds `RouteResult` from
  `route.latitudes` unguarded), and the engine's start path, which derives the destination from the
  same arrays (`NavigationEngine.kt:273` `computeRouteDistance(routeEntry.latitudes, …)` →
  `:937 lats.size`, `:280-281 routeEntry.latitudes.lastOrNull()`).
- State the rule once: **absent geometry is handled like empty geometry** — the app publishes no
  route geometry and draws no route; it never throws. The existing `route-analysis` "Empty polyline
  degrades safely" and `route-map-overview` "Degenerate route geometry degrades safely" are the
  precedent; this change extends them to `null` arrays rather than `emptyArray`.
- Add the missing units: a case that adopts a geometry-less route and asserts the panel state is set
  with no `RouteResult` published (the case §137 names as missing), plus the same for the
  calculation-success publish.
- No native, submodule or bridge change: the JNI result is taken as it is; this is a Kotlin guard on
  the consuming side.

Not breaking and no behavior change for a route that carries geometry: the guards only convert a
throw into "no geometry published".

## Capabilities

### New Capabilities

None — the behavior is a correction of an existing capability's degrade path, not a new capability.

### Modified Capabilities

- `route-map-overview`: "Degenerate route geometry degrades safely" gains the **absent-array** case —
  a route result whose polyline arrays are missing (null) rather than empty is treated the same way:
  the session keeps its state, no route geometry is published, the camera is not moved, and the map
  does not fail. Today the requirement covers empty arrays and invalid endpoints only.
- `navigation-engine`: "Route acquisition independent of a surface UI" gains the qualifier that a
  surface observing an engine-acquired route **without** geometry adopts it without an exception
  ("the surface's route views reflect the acquired route" holds when the route carries geometry;
  otherwise the view publishes no geometry while navigation continues unchanged).

The previous specifications that change are those two requirement blocks; no other spec is touched.
`route-analysis`'s empty-polyline scenario is the precedent this change cites, not a delta.

## Impact

Code:

- `app/src/main/java/com/naviveylin/ui/route/RoutePanelViewModel.kt` — `adoptRoute` publish block
  (`:806-844`, the `val lats = route.latitudes` at `:834`) and the calculation-success publish
  (`:514-518`); the `RouteResult` holder (`:155`) stays a non-null-array type, so "no geometry" is
  expressed by not publishing a result.
- `app/src/main/java/com/naviveylin/navigation/NavigationEngine.kt` — `startInternal` destination
  derivation (`:273`, `:280-281`); `computeRouteDistance` (`:936-937`) keeps its signature.
- `app/src/main/java/com/naviveylin/navigation/NavigationViewModel.kt` — the adopt call site
  (`:84-88`); no structural change, but it is where the reachable path was verified.
- Tests: `app/src/test/java/com/naviveylin/ui/route/RoutePanelViewModelSessionTest.kt` (or a sibling
  class in the same package) for the geometry-less adopt and calculation cases; the existing
  `RouteSessionCardExitTest` comment ("`adoptRoute` cannot take a route without a polyline") becomes
  obsolete with the fix.

Modules/surfaces: `:app` only (no `:auto`, `:core` or `osmscout-client-java` change). Both Play
flavors compile the same sources; the shared-state consumer is the phone map (`routeResultFlow` →
`MapCanvasViewModel.kt:2953`), and the car inherits the route geometry through the same
`NavigationState`, so the fix is general rather than phone-only — but the verification surface is the
phone: the car-only adopt that reaches this path cannot be produced on a stationary AVD (a deep link
into a reroute whose native result carries no polyline).

Guidelines: `guidelines/Design.md` (state ownership — the engine owns navigation state, a surface
adapter publishes view state from it; a missing value is not an exception) and `guidelines/Build.md`
§4 (the revert-check discipline for the new guard). No rendering, UI or MapRendering rule changes.

Verification path: a focused JVM suite (`--tests "com.naviveylin.ui.route.*"`, plus the navigation
package for the engine sites) — no NDK build and no device, because the missing geometry is produced
by a test-constructed `RouteEntry`. Revert-check: remove the guard → the new geometry-less adopt case
must fail with the recorded NPE, restore, re-run forced green.

Known limitation to record, not hide: the native hand-over of a **null** array has been observed only
through a test-constructed `RouteEntry`, not on a device; the production reachability is inferred from
the platform type, so the change states that in its verification instead of claiming device proof.

Rollback: revert the guards and the new cases — the tree returns to today's behavior (the throw);
nothing else in the tree depends on the guard.

Out of scope (filed, not fixed here): `TODO.md` §129/§130/§126 and the other route-geometry
questions, and any decision to make `RouteEntry`'s arrays non-null at the bridge.
