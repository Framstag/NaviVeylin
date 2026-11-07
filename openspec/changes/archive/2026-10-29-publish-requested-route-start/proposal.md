# Proposal

## Why

A calculated route begins at a node that is **not** the position the caller asked for. `ResolveRoutePosition`
(`OSMScoutClient.cpp:7333`) snaps the caller's start to a routable node and the published geometry starts
there — `TransformRouteDataToPoints` (`:7517`) emits `iter->GetCurrentNodeIndex()`, and the description's
first node resolves to the same node by the same index (`RoutePostprocessor.cpp:174`). The requested
coordinate is used to *find* the route and then thrown away, so on an off-network start the drawn polyline,
the start marker and the first instruction (`steps[0].coord`) all sit tens to hundreds of metres from where
the user asked to depart.

This is the residue of §130 (tracker #82). Its claim — that the two first points lie ~200 m apart — is
**refuted**: they are the same node, and the 198 m was the first leg's straight length. What is left is a
decision — **which position the published route may start at** — answered on 2026-10-29: the requested
start.

## What Changes

- **The published route starts where the caller asked.** When the requested start is off the routable
  network, the route result SHALL publish the requested start position as its first polyline point and
  as the position of its first instruction line. The routed geometry from the snapped node onward is
  unchanged.
- **The approach is part of the route the driver is shown.** The drawn polyline, the route's published
  total and the first step's leg distance all include the approach from the requested start to the
  snapped node, so `total == sum(per-step distances) == polyline length` keeps holding (specs
  `osmscout-jni`: *One route length for a calculated route*, *The published route length is a length of
  that route*). The approach contributes distance but no routed time — it has no routed speed.
- **A start already on the network is published unchanged.** When the requested start's distance to the
  snapped node is at or below a small snap tolerance (a reroute from the current position, an object
  start that resolves to a node), no extra first point and no zero-length segment is added.
- **The first step is anchored at the published start.** Step 0's anchor SHALL be the polyline's first
  vertex, so the analysis' start line still owns no leg and every instruction position still lies on the
  drawn polyline (spec `route-analysis`).
- **The rule lives in the bridge, once.** The step is shared by both native route entry points —
  `calculateRouteWithObjectsWithProfile` (the path the app calls through `client.calculateRouteWithProfile`:
  `RoutePanelViewModel.kt:554`, `NavigationEngine.kt:520`) and the legacy
  `calculateRouteWithObjectsAsync` — so one helper applies it to both instead of leaving the two copies of
  the description/points/step pipeline to drift.

Not in scope:

- **The target end of the route.** The destination pin is drawn app-side at the requested destination
  while the polyline ends at the snapped target node; that is the same class of gap on the other end,
  with its own measurement (the target marker is not part of the route geometry today). Recorded as a
  follow-up, not solved here.
- **Snap quality.** `ResolveRoutePosition`'s search radius and node/way choice are unchanged; this change
  does not make the snapped node closer, it makes the published route honest about the gap.
- **The route's text.** The `Start: …` line's street name still comes from the snapped node.

Additive, not breaking: no JNI signature, Java field or spec requirement is removed; a route whose
requested start is already on the network publishes byte-identical geometry to today. The only changed
behaviour is the geometry of a route whose start was snapped.

Rollback: revert the submodule commit and the gitlink bump. No persisted state, no schema and no
migration is involved — the geometry is recomputed on every calculation.

## Capabilities

### New Capabilities

None — this change corrects the geometry an existing capability publishes.

### Modified Capabilities

- `osmscout-jni`: new requirement for the published start position (the requested start becomes the
  first polyline point and the first instruction position); the published-length requirement gains the
  approach, so a snapped start does not split one route into two lengths.
- `route-analysis`: new requirement that the start line's anchor is the published start position, so the
  start line owns no leg and the position-agrees-with-the-route rule keeps holding after a snap.

## Impact

Affected files and modules:

| Where | What changes |
|---|---|
| `app/src/main/cpp/libosmscout/libosmscout-client-java/src/OSMScoutClient.cpp` (submodule) | capture the requested start (`start.GetLat()/GetLon()`, already in hand at `:7193`/`:7676`); publish it as the first polyline point; set the description's first node location to it (which the step collector turns into `steps[0].coord`); offset the description nodes' cumulative distances by the approach except the start node; add it to the published total |
| `app/src/main/cpp/libosmscout/libosmscout-client-java/src/route_start.h` (submodule) | dependency-free helpers: the approach distance, the snap-tolerance decision, and the node-distance offset rule (the shape of the neighbouring `route_length.h`, and of `search_scope.h` before it) |
| `app/src/main/cpp/libosmscout/Tests/src/RouteStartTest.cpp` + `Tests/meson.build` (submodule) | host tests for the helpers (approach below/above tolerance, no-op at the tolerance, offset leaves the start node at zero) |
| `app/src/androidTest/java/com/naviveylin/route/RouteStartPositionDeviceTest.kt` (new — the file this plan first named, `RouteInstructionPositionDeviceTest.kt`, was retired with the instruction prose path) | an Andorra candidate pair and a case asserting the published first point and `steps[0].coord` are the requested start, plus the length invariant on a snapped start |
| `app/src/test/java/com/naviveylin/ui/route/` | a host case that `rowAnchors`' first anchor is the polyline's first vertex and step 0 owns no leg when the first point is a prepended start (`RouteStepSegments`/`RouteStepAnchors` are pure) |
| gitlink `app/src/main/cpp/libosmscout` | bump after the submodule commit on `naviveylin-local` |

**Native/JNI form: submodule patch, minimal and upstreamable.** The requested start is already a
parameter of the JNI entry point, so no Java class, no `RouteEntry` field and no
`:osmscout-client-java` override changes — the fix is inside `OSMScoutClient.cpp` plus one
dependency-free header and its host test. This follows `guidelines/Design.md` §5 (patch the native side
in the submodule, never both sides of the bridge) and §4: all of it runs inside the existing
`dbThread->RunSynchronousJob` on the calculation thread, so no new dispatcher and no main-thread work.

**Surface scope:** both surfaces draw the route from the same `RouteEntry` produced by the shared
navigation engine, so the phone and the car receive the same corrected polyline (parity is by
construction, not by a second implementation). The step-anchor consumption in this change is the phone
analysis surface (`route-analysis`); the car has no step analysis.

Specs changed by this proposal: `osmscout-jni`, `route-analysis` (deltas in this change).

Guidelines: `guidelines/Design.md` §4/§5 (threading and the native boundary) and
`guidelines/Build.md` §12 (submodule commit, gitlink bump, all three ABIs) — no guideline text changes.
The new diagnostic line carries metres, not coordinates, per `guidelines/Logging.md` and the
`checkNoCoordinatesInLogs` gate.

**Prior state of §130:** the entry is tracker issue #82, open as a `bug`. Its decision was recorded there on
2026-10-29 (requested start wins, `status/open-device`); this proposal implements it and supersedes the entry's
fix candidate. The entry's closure waits for this change's evidence.
