# Design

See `proposal.md` — Why, and `specs/route-map-overview/spec.md` +
`specs/navigation-engine/spec.md` for the requirement deltas.

## Context

The route panel already treats "no geometry" as normal on four of its six read sites, and assumes it
is present on the other two. Verified today in `app/src/main/java/com/naviveylin/ui/route/`:

| site | nullable handling |
|---|---|
| `RouteStepAnchors.kt:36-38` (`instructionAnchors`) | `descriptions ?: return emptyList()`, `instructionLats ?: return …` |
| `RouteStepValues.kt:31-33` (`instructionValues`) | same pattern |
| `RoutePanelViewModel.kt:642` (`buildSteps`) | `route.descriptions ?.filter …  ?: emptyList()` |
| `RoutePanelViewModel.kt:687-696` (`segmentOf`) | `route.latitudes ?: return null` |
| `RoutePanelViewModel.kt:834-836` (`adoptRoute` publish) | **unguarded** — `val lats = route.latitudes; if (lats.size >= 2 …)` |
| `RoutePanelViewModel.kt:514-518` (`onSuccess` publish) | **unguarded** — builds `RouteResult(routeLats = route.latitudes, …)` |

`RouteEntry` is a plain Java class in the bridge (`libosmscout-client-java/.../RouteEntry.java:69`
no-arg constructor, public mutable fields), so `latitudes`/`longitudes` are platform types: absent is
a legal value, and a test can produce one by simply not assigning the field.

Two call paths reach the unguarded sites: `NavigationViewModel.setRoutePanelViewModel`
(`:69-105`, adopt at `:84-88`) inside `viewModelScope.launch { engine.state.collect { … } }` — the
car-only/deep-link adopt and the post-reroute re-acquisition — and the panel's own
`onSuccess(route)` after a user calculation. Both run on the main dispatcher (Design.md §3/§4: view
state is published from a view-model scope; the engine owns navigation state and calls its listeners
on the main thread — `navigation-engine` — "Engine lifecycle and threading").

The engine's start path reads the same arrays: `NavigationEngine.startInternal`
(`:273` `computeRouteDistance(routeEntry.latitudes, routeEntry.longitudes)` where
`:937` does `lats.size`; `:280-281` `routeEntry.latitudes.lastOrNull()`), and it publishes them into
`NavigationState.routeLats/routeLons` (`core/NavigationState.kt:88`, `DoubleArray?`). Every consumer
of those fields is already nullable-tolerant (`MapRenderUtil.kt:56-179`, `MapRenderer.kt:87-928`,
`AutoMapRenderer.kt:155-1381`, `NavigationScreen.kt:597-599`), so the shared state needs no change —
only the derivation inside `startInternal` must stop dereferencing a possibly-absent array.

## Goals / Non-Goals

**Goals**

- One rule for "the route result carries no usable geometry", applied at every site that publishes
  route geometry from a `RouteEntry`, so the class cannot drift back to per-site guards.
- No behavior change for a route that carries geometry (same `RouteResult`, same camera, same render).
- Failures replaced by an observable no-op: a diagnostics line for the developer, no exception for the
  user.

**Non-Goals**

- No change to the JNI/bridge contract, submodule or `RouteEntry` field types (see Open Questions).
- Not the camera/overview rules for empty geometry — `route-map-overview` already defines them and
  they are implemented (`MapCanvasViewModel.kt:691` returns `Double.NaN` for an absent polyline).
- Not §129 (two disagreeing totals) or §130 (first node vs first polyline point): separate findings,
  both needing a native reference measurement.

## Decisions

### D1 — A single pure geometry rule, not per-site guards

**Chosen:** add one pure geometry rule in `app/src/main/java/com/naviveylin/ui/route/` (the panel's own
package, next to `RouteStepAnchors.kt` / `RouteStepValues.kt`) as two named functions — the geometry
check (`hasUsablePolyline`: present, two or more points, equal length) plus the coordinate-free
diagnostics line live in that one file, so no call site decides the rule for itself:

- `adoptedRouteGeometry(route): RouteResult?` — the adopt path's rule: `null` unless the route carries
a usable polyline (an adopted route has no other endpoints). `null` means *publish nothing*.
- `calculatedRouteGeometry(route, startLat, startLon, destLat, destLon): RouteResult` — a completed
calculation always publishes: absent or too few coordinates become an **empty** polyline with the
requested endpoints, which is exactly the empty-polyline case the camera fallback already requires.

The panel state (`routeState = Done`, `routeEntry`, steps, anchors, `_routeVisible`) is set exactly as
today, independent of geometry.

**Alternatives**

1. *Inline guard at each site* (`val lats = route.latitudes ?: return`): smallest diff, but leaves two
   copies of the rule and no seam — the drift that produced this defect (two guarded sites, two not)
   would remain possible. Rejected.
2. *Make `RouteResult.routeLats/Lons` nullable or empty-tolerant and let the map handle it*: pushes
   the decision into `MapCanvasViewModel`/`MapRenderer` (`:1500` passes `routeEntry?.latitudes` into
   the highlight overlay) and changes an existing data type used by the fit and the highlight. Larger
   blast radius for no benefit: the map's own absent-polyline path already exists. Rejected.

**Risk:** a helper returning `null` could be silently ignored at a future call site. Mitigation: the
helper is the only way to build a `RouteResult` from a `RouteEntry`; the two `RouteResult(...)`
constructions in the file are replaced by it, and a test asserts both paths.

### D2 — Absent geometry behaves exactly like an empty polyline; the adopt path publishes nothing

**Chosen:** the app already has two rules for unusable geometry, and absent coordinates follow them
rather than inventing a third:

- **A completed calculation publishes** the result with an empty polyline and the requested endpoints,
because `route-map-overview` — "Empty polyline falls back to endpoints" needs it: the fit moves to the
start/target midpoint. The pre-change calculation path published unconditionally (only the adopt path
had a length guard), so this preserves its behaviour and merely stops a `null` array from reaching a
non-null parameter.
- **An adopted route publishes nothing** (`_routeResultFlow` unchanged), so the map keeps the
previously drawn route or draws none, matching `reroute-route-visibility` — "Failed reroute keeps the
last route visible" and `route-map-overview` — "Missing coordinates leave the viewport untouched".

**Alternatives**

1. *Clear the drawn route on a geometry-less adoption*: would blank the map while navigation is
   actually running on that route, contradicting the reroute-visibility requirement. Rejected.
2. *Publish an empty-array `RouteResult`*: `RouteResult` declares non-null `DoubleArray`s, and its
   consumers (`MapCanvasViewModel.kt:2953` combine → fit/highlight) would receive a *changed* result
   whose geometry is empty, moving from "keep the last drawing" to "redraw nothing" — the same wrong
   outcome as (1) plus a type change. Rejected.

### D3 — The engine derives destination/distance null-safely instead of requiring geometry

**Chosen:** in `startInternal`, read the arrays once, derive the destination from `lastOrNull()` on
the read (not on the nullable field) and compute the total distance from a possibly-absent array
(`computeRouteDistance` keeps its `DoubleArray` signature and is called only when both arrays exist;
otherwise the total is `0.0`, which is already the function's answer for fewer than two points). The
engine still starts navigation and stores `null` in `NavigationState.routeLats/routeLons`, which every
consumer already accepts.

**Alternatives**

1. *Refuse to start navigation without geometry*: navigation is the engine's job and the native
   side is already running it; refusing would turn a rendering gap into a navigation failure.
   Rejected.
2. *Guard `computeRouteDistance`'s signature with nullable parameters*: changes a small public helper
   used by tests; the null decision belongs at the call site, one level up. Rejected.

**Threading model:** no new thread, dispatcher, scope or component. Both publish sites already run on
the main dispatcher (view-model scope); the helper is pure and allocation-light (no arrays copied).
State ownership is unchanged (Design.md §3: the engine owns navigation state, the surface adapter
publishes view state; Design.md §4: nothing native is called from a host/callback thread here).

**Observability:** when the helper yields `null`, write one coordinate-free
`DiagnosticsLog` line (tag `ROUTE`) naming the route's step count and which arrays were missing
(booleans/counts only, never a coordinate — spec `auto-diagnostics`, build gate
`checkNoCoordinatesInLogs`) so a device run can attribute "no route drawn" to data instead of guessing.

## Risks / Trade-offs

- **[The null case may not occur in production]** → the guard is cheap and the spec now states the
  contract; the change records that null was observed through a test-constructed `RouteEntry`, not on
  a device, instead of claiming device proof. The diagnostics line above is what will confirm it on
  the next device run.
- **[A pinned expectation in an unrelated class]** → found by the full gate, not by the focused suites:
`MapCanvasViewModelRouteFitTest.emptyPolyline_fallsBackToEndpointMidpoint` (both flavors, deterministic
when run alone) proved that the calculation path's result is what carries the endpoint fallback, and an
early version of this change made the calculation path publish nothing for a zero-length polyline.
Mitigation: the two named functions above, the class's case kept as the guard for the fallback, and the
rule recorded in `ki_processing_failures.log` (2026-10-05) — a geometry rule is not applied to both
publish sites until each site's *existing* behaviour for an empty polyline has been checked.
- **[A test that asserts the throw disappears]** → none exists; `RouteSessionCardExitTest:130` carried
  a comment saying `adoptRoute` cannot take a polyline-less route, which the change updates.
- **[Silent no-op hides a real native regression]** (e.g. the bridge starts dropping geometry) → the
  helper's diagnostics line is the trace; without it the map would simply stay empty. That is the
  reason the line is part of the change rather than optional.
- **[Engine distance becomes 0 for a geometry-less route]** → already the documented answer for fewer
  than two points (`computeRouteDistance:936-937`); the remaining-distance display then inherits the
  native arrival estimate, as it does for any route until the first estimate arrives.

## Verification

Unit (JVM, no device, no NDK — the defect is reproducible with a test-constructed `RouteEntry`):

- `RoutePanelViewModelSessionTest` (`@RunWith(RobolectricTestRunner::class)`, default sandbox — AGENTS
  classloader rule): adopt a `RouteEntry` with no `latitudes`/`longitudes` → the session state is
  adopted, `routeResultFlow` stays unchanged, no exception; plus a case that a previously published
  geometry survives the adoption.
- The same class for the calculation-success publish path.
- `NavigationEngineTest` (default sandbox, same runner): `start()` with a geometry-less `RouteEntry` →
  navigation activates, `state.routeLats` is `null`, destination falls back to the previous value.
- Revert-check (one mutation, per `revert-check` / `guidelines/Build.md` §4): restore the unguarded
  `val lats = route.latitudes; lats.size` in the adopt path → the geometry-less adopt case must fail
  with the recorded `NullPointerException`; restore the guard and re-run forced green
  (`--rerun-tasks`).
- Focused suites only (`--tests "com.naviveylin.ui.route.*"` and
  `--tests "com.naviveylin.navigation.*"`, ≥3 min each), full both-flavor gate once before the change
  is complete (`guidelines/Build.md` §2).

On device (recommended, not gating): a car-initiated destination (deep link / car screen) that
resolves with no phone UI present, then the phone resumed — `adb logcat -s NaviVeylin | grep -i "route
geometry"` should show the new `ROUTE` line and the phone must not crash; without a producible
geometry-less native result the step is reported as not producible on a stationary AVD, per the
project's measurement rule.

## Migration Plan

Additive guards only, no data, schema or state migration. Land order: helper + diagnostics → the two
publish sites → the engine derivation → tests. Rollback: revert the guards (the tree returns to
today's throw) and delete the added cases; nothing else depends on the helper.

## Open Questions

- Should the bridge guarantee non-null geometry arrays (`RouteEntry.latitudes = DoubleArray(0)`
  instead of null) so the app never sees a platform type here? Deferrable: it is a bridge-contract
  question, changes no requirement of this change, and the guards are needed either way. Filed in
  `TODO.md` §137's neighborhood rather than decided here.
