# Design — publish-requested-route-start

## Context

Facts the decisions depend on, each with its evidence. Read `proposal.md` — Why for the behaviour.

> **Re-based 2026-10-29.** The plan was written 2026-10-09. The bridge's instruction path was retired in
> between (`libosmscout-client-java` commit `6c8dc6f94`, "retire the instruction prose path"), the published
> length moved into `route_length.h`, and the device test this change was planned against no longer exists.
> Every citation below is re-read against today's tree; the decisions D1–D6 are unchanged.

- The requested start is the caller's coordinate, already in hand where the router snaps it:
  `osmscout::GeoCoord start(startLat, startLon)` (`OSMScoutClient.cpp:7193`, the legacy entry point's twin at
  `:7676`), passed to `ResolveRoutePosition(routingService, osmscout::RouteEndpoint::Start, start.GetLat(),
  start.GetLon(), …)` (`:7333`; legacy `:7830`, definition at `:6980`). Nothing after that call reads `start`
  again.
- The snapped start is what gets published: `TransformRouteDataToPoints` (`:7517`; legacy `:8050`) emits the
  route's entry boundaries from `iter->GetCurrentNodeIndex()` (`AbstractRoutingService.cpp:1845`/`:1867`), and
  the description's first node resolves to the same node by the same index
  (`RoutePostprocessor.cpp:174`/`:177`). The two are the same position — this is why §130's "200 m apart"
  premise is refuted.
- Instruction positions are the collected steps' own node coordinates, **not** an `instructionLats` array:
  `routeStepCollector.Collect(*descResult.GetDescription())` (`:7451`; legacy `:7982`) fills
  `osmscout::client::RouteStep.coord` from the description node's location (`RouteStepBuilder.cpp:51-59`; the
  field is `RouteStep.h:180`, "position of the node"), and `CreateJavaRouteSteps` (`:1424`, called `:7591` /
  `:8125`) bridges them into `RouteEntry.steps` (`RouteEntry.java:31`). The `DescCallback::BeforeNode` /
  `CollectCallback` text path this change was planned against is gone (`legStartKnown` with it), so "the first
  instruction's position" means `steps[0].coord`.
- The published route length is the step list's own total when it produced one, else the polyline length:
  `naviveylin::RouteLengthMeters(LastStepDistanceMeters(routeSteps.steps), polylineMeters)` (`:7457`/`:7572`;
  legacy `:7988`/`:8106`), with `LastStepDistanceMeters` at `:160` and the helper in `route_length.h`. The
  per-step distances are deltas of the description's cumulative node distances (`RouteStepBuilder.cpp:143`),
  so an offset applied to the description reaches the step list, the total and the analyser without a second
  edit — which is what D5 relies on.
- The start node's cumulative distance must stay zero: step 0 is the route's start, which
  `RouteStepCollector::DeriveKind` reads off `StartDescription`, and the analyser's first row is that start and
  owns no leg (`RouteStepAnchors.kt` — `rowAnchors`, `RouteStepSegments.kt:52-63` yields `IntRange.EMPTY` when
  `own <= from`). Offsetting node 0 would publish a non-zero distance-from-start on the start step. The
  `legStartKnown <= 0.0` detection the original plan cited for this exception no longer exists; the meaning of
  step 0 carries it now.
- The app path is the JNI entry `calculateRouteWithObjectsWithProfile` (`:7176`), reached through the Java
  wrapper `client.calculateRouteWithProfile(...)` (`RoutePanelViewModel.kt:554`, `NavigationEngine.kt:520`).
  The legacy `calculateRouteWithObjectsAsync` (`:7660`) is used by `:osmscout-client-java`'s own wrapper tests and
  the submodule's JavaScout tests. Both contain the same description + points + step pipeline.
- The consumer of the anchors is pure and already tested: `rowAnchors` (`RouteStepAnchors.kt`) and
  `stepSegments` (`RouteStepSegments.kt:29`), with `RouteStepAnchorsTest` / `RouteStepSegmentsTest` — a start
  anchor that is the polyline's first vertex yields "owns no leg" without a code change.
- The device test this change names does not exist any more: `RouteInstructionPositionDeviceTest.kt` is gone
  from `app/src/androidTest/java/com/naviveylin/route/`, which now holds `RouteEndpointAttachmentDeviceTest`,
  `RouteStepModelBridgeTest`, `RouteStepLiveGuidanceDeviceTest` and `RouteRowCensusDeviceTest`. The new cases go
  into a new file of their own (task 1.1) rather than into a resurrection of the retired one.
- Device evidence needs a provisioned AVD: `emulator-5554` was the reference phone AVD (API 37) with
  `andorra` + `basemap` + `isle-of-man` in its app data; no device was attached on 2026-10-29. The Andorra
  candidate pair stays the smallest routable dataset (the same reason `RouteEndpointAttachmentDeviceTest` uses
  it).
- Precedent for a dependency-free helper + host test inside the bridge is now nearer than `search_scope.h`:
  `route_length.h` (`naviveylin::RouteLengthMeters`) + `Tests/src/RouteStepModelTest.cpp`, same shape, same
  purpose — `route_start.h` slots beside it.

## Goals / Non-Goals

Goals

- The published route begins at the position the caller asked for, on both the calculation result and
  the navigation instruction list.
- One route still has exactly one length, and it is the length of the geometry that is drawn.
- The pure parts of the rule are host-testable; the geometry that needs a database and JNI is measured on
  the phone AVD that is attached.

Non-Goals

- No change to `ResolveRoutePosition`'s snapping (radius, node vs way choice) and no new routing API.
- No change to the target end of the route (see D6).
- No change to the app's analysis/render code, and no new `RouteEntry` field.
- No re-implementation of any of this in Kotlin.

## Decisions

### D1 — The bridge publishes the requested start (not the app, not upstream's point transform)

| Alternative | Consequence |
|---|---|
| **A (chosen)** the submodule's `OSMScoutClient.cpp` publishes the requested start: it prepends the requested position as the first polyline point and sets the description's first node location to it | the two arrays that must agree (polyline, instruction positions) are produced in one place; no Java class or override changes; the navigation instruction list is corrected by the same edit; host-testable helpers; minimal and upstreamable |
| B the app prepends the requested position to `RouteEntry.latitudes/longitudes` and rewrites `instructionLats[0]` in Kotlin | two writers for one geometry; the app would have to re-derive the anchor↔polyline correspondence `stepSegments` assumes, and the navigation path's own instruction list (built natively from the description) would diverge from the drawn route; violates `guidelines/Design.md` §9's "the library's job" rule |
| C change upstream's `AbstractRoutingService::TransformRouteDataToPoints` to emit the requested coordinate | `RouteData` does not carry the requested coordinate at all (it holds snapped nodes), so this needs an upstream API change for a client-local presentation decision; larger blast radius (every libosmscout consumer) |

Rationale: the requested coordinate is in hand at exactly one place in our own bridge, and both arrays
that must agree are computed there. A is the only alternative that keeps one source and stays a
client-side presentation decision.

Risk: the legacy entry point could be left behind (D4); both copies of the pipeline are in the same file,
so the mitigation is one shared helper plus a review grep for the second `TransformRouteDataToPoints`
call.

### D2 — The approach is part of the route

| Alternative | Consequence |
|---|---|
| **A (chosen)** the approach is part of the published route: it is a polyline segment, it is added to the published total, and it is added to the first turn's leg distance | keeps `total == sum(per-step distances) == polyline length`, which `osmscout-jni` requires; the driver is told the length of the geometry shown |
| B draw the approach but exclude it from every published length | the polyline is then longer than the published total. On the ~70 km measured case the gap is ~200 m (0.2 %, inside tolerance) but on a short route it is unbounded (a 200 m route with a 198 m snap publishes a total half the drawn length) — the spec's own "one route has one length of road" would be false |
| C replace the first routed vertex with the requested position instead of prepending | `TransformRouteDataToPoints` emits only entry boundaries, so the removed vertex can be a whole street away; the drawn route then crosses the first street in a straight chord — a new rendering defect in place of the old one |

Rationale: A is the only alternative under which the three published numbers stay one length.

Risk: the approach has no routed speed, so adding distance without time makes the first leg's average
speed lower and the ETA a little optimistic over that short stretch. Accepted and recorded; the
alternative (inventing a speed) would be a fabrication.

### D3 — The approach is applied only beyond a snap tolerance

| Alternative | Consequence |
|---|---|
| **A (chosen)** prepend only when the requested start's distance to the snapped node exceeds a small documented tolerance (sub-metre) | a reroute from the current position and an object start that resolves to a node publish byte-identical geometry to today; no degenerate zero-length first segment, which would otherwise show up in the polyline-length sum and in `stepSegments` |
| B always prepend | harmless for the length, but adds a duplicate vertex and a zero-length leading edge on every ordinary route; it also makes the polyline a worse input for any consumer that assumes distinct consecutive vertices |
| C decide from the description total being zero at node 0 | conflates "the start is the route start" with "an approach exists"; the tolerance is the actual quantity |

Rationale: the tolerance is the honest expression of "the caller asked for a position that is not on the
network". The value is a named constant with a host test on both sides of it.

### D4 — One helper, both native entry points

| Alternative | Consequence |
|---|---|
| **A (chosen)** one dependency-free header (`route_start.h`) exposes the approach distance, the tolerance decision and the node-distance offset rule; both `calculateRouteWithObjectsWithProfile` and `calculateRouteWithObjectsAsync` call it | the two copies of the pipeline cannot drift; the rule is host-tested without a database or JNI (the `search_scope.h` precedent) |
| B patch only `calculateRouteWithObjectsWithProfile` | smallest diff, but the submodule's JavaScout tests and any future caller of the legacy path keep publishing the snapped start — a silent divergence of one JNI contract between two entry points |
| C fold both entry points into one implementation first, then patch once | the right end state, but it is a refactor of ~700 duplicated lines mixed into a behaviour change — its own change |

Rationale: A applies one rule to both without mixing in the refactor. The duplication itself is recorded
as a non-goal here and is the obvious follow-up.

### D5 — The approach enters the description's cumulative distances, except the start node's

| Alternative | Consequence |
|---|---|
| **A (chosen)** after the postprocessors, add the approach to every description node's cumulative distance except node 0, whose distance stays 0 | the start node stays at 0, so step 0 still reads as the route's start (`RouteStepCollector::DeriveKind` off `StartDescription`) and owns no leg; every later node's cumulative distance and therefore the first leg's delta, the description total and the per-step sums all include the approach; the polyline and the description agree by construction |
| B offset node 0 as well | the start step would publish a non-zero distance-from-start, so "the first row is the route's start and owns no leg" (`RouteStepAnchors.kt`, `RouteStepSegments.kt:52-63`) stops being readable from the data, and every later per-step delta shifts by the approach |
| C leave the description distances alone and patch only the published per-step values | the calculation result's step list would include the approach while the navigation path's step list (collected from the same description) would not — `osmscout-jni` requires the two step lists of one route to agree, and the device case measures exactly that |

Rationale: A is the single place where all four published numbers (total, per-step distances, polyline,
step positions and distances) follow from the same offset.

### D6 — The target end is deliberately not changed

| Alternative | Consequence |
|---|---|
| **A (chosen)** the start only; the target gap is filed as its own entry | the target's asymmetry is real (the destination pin is drawn app-side at the requested destination while the polyline ends at the snapped node) and needs its own measurement of what the driver sees at arrival, plus a decision about a route whose last leg has no routed successor |
| B fix both ends in one change | one decision for the start, a *second* independent decision for the target (where the existing arrival-30 m and reroute rules live, `RouteStateAgent.cpp`), two device scripts and two spec deltas in one change — the reason §130's own text called its fix candidate a choice |

Rationale: the user chose the start's authority; the target's is a separate question with a separate
blast radius (arrival detection, "turn around" guidance after arrival — TODO §150).

## Threading and lifecycle

No new thread, dispatcher, scope or lifecycle. Every step runs inside the block that already owns the
calculation: the `data->dbThread->RunSynchronousJob(...)` that produces the points and the description
(`OSMScoutClient.cpp:7517`, the legacy twin at `:8050`), on the bridge's own calculation thread. The
description is stored under `data->routeDescriptionMutex` (`:1549`, locked at `:7488`) exactly as today; the
location and distance edits happen before it is stored and before the step collector reads it, so no reader
observes a half-edited description. No main-thread work, no object outlives the call.

## Verification

**Host (no device, no database)**

- `Tests/src/RouteStartTest.cpp` (C++, host build): approach distance for identical points is zero;
  a point inside the tolerance is not prepended; a point beyond it is; the offset leaves the first node
  at zero and adds exactly the approach to every later node. Run per the README's host-build recipe
  (`meson test -C hostbuild`), registered in `Tests/meson.build` like `RouteStepModelTest`.
- `RouteStepSegmentsTest.aPrependedStartAnchorsStepZeroAndOwnsNoLeg` (`:app` unit): a polyline whose first
  vertex is a prepended start and an anchor list whose first anchor is that same vertex yields step 0
  `IntRange.EMPTY` and a first-turn leg beginning at vertex 0.
- The whole JNI geometry path is **not** host-runnable (the JVM suite loads a symbol-free stub
  `libosmscout_client_java.so`, so `TransformRouteDataToPoints` never runs) — stated here so the host
  cases are not read as covering it.

**Device (the measurement; a phone AVD, API 37, Andorra map installed — provisioned first, since none was
attached on 2026-10-29)**

A new `RouteStartPositionDeviceTest` (task 1.1 — the file this plan originally extended,
`RouteInstructionPositionDeviceTest`, no longer exists), with an Andorra candidate pair whose start is
deliberately placed off the road network:

- `publishedStartIsTheRequestedStartPosition`: the published polyline's first point and the first
  instruction's position are the requested start within ~1 m, and the second point equals the snapped
  route's first vertex (numbers only in the log line, per `guidelines/Logging.md`).
- `routeWithAnOnNetworkStartIsPublishedUnchanged`: a start on the road yields the routed first vertex and
  no leading zero-length segment.
- `snappedStartKeepsOneRouteLength`: `sum(per-step distances) / route.distance` within 2 %, and
  `route.distance / polyline length` within 2 % — the same numbers the existing
  `routeLengthsAreMeasuredForALongAndAShortRoute` prints, now with a snapped start.
- the analyser's existing geometric bounds stay green for the same route — a step's segment is monotonic and
  on the polyline, and every row's anchor lies on it (the polyline-match bound is now met at the start because
  the polyline begins there).

Recipe: `guidelines/Build.md` §10; build with `-Pandroid.injected.build.abi=x86_64` for the AVD, install
the test APK with `-t`. Pixel evidence is not needed for this property (it is geometry and numbers, not a
colour), which is why `pixel-check` is not used.

**Revert-check (the new invariant)**

The invariant is "a snapped start is published as the requested position". Single mutation: make the
tolerance helper return "do not prepend" unconditionally (one constant). The named case
`publishedStartIsTheRequestedStartPosition` must fail on the device; restore, then re-run forced green.
A second, cheaper mutation on the host side: remove the `except node 0` condition in the offset rule and
`Tests/src/RouteStartTest.cpp`'s start-node case must fail.
