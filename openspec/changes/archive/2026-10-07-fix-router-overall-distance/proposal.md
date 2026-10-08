# Proposal

## Why

A calculated route's published length is not a route length. The bridge publishes
`RoutingResult::GetOverallDistance()` as `RouteEntry.distance`
(`app/src/main/cpp/libosmscout/libosmscout-client-java/src/OSMScoutClient.cpp:6140` and `:6852`), and that
value is set once, from the straight line between start and target:

```cpp
// app/src/main/cpp/libosmscout/libosmscout/src/osmscout/routing/AbstractRoutingService.cpp:1088-1094
Distance overallDistance = GetSphericalDistance(startCoord, targetCoord);   // air-line
double overallCost = GetEstimateCosts(state, start.GetDatabaseId(), overallDistance);
double costLimit  = GetCostLimit(state, start.GetDatabaseId(), overallDistance);
result.SetOverallDistance(overallDistance);
```

It is the **estimate input** for the cost limit and the progress denominator — upstream's own
`Demos/src/Routing.cpp:1291` prints that field as "Air-line distance" — and the bridge's comment calls it
"the router's own accumulated distance", which is false. Measured against the drawn polyline
(`TODO.md` §139, `emulator-5554`): 72 771 m vs 97 283 m (0.748), 16 677 vs 20 966 (0.795), 824 vs 1 493
(0.552); the description's total tracked the same polyline to within 0.4 %. The real road distance
Dortmund Hbf → Cologne Hbf is ~95-100 km, so the published figure is the implausible one.

The app stopped *displaying* it (`fix-route-length-disagreement`: `RouteStepValues.routeLengthMeters`
prefers the sum of the step legs, `RouteStepValues.kt:53-65`), and the same change made the description's
total override the assignment inside the description branch (`OSMScoutClient.cpp:6396` sums the published
per-step legs, `:6399` uses the description's last-node distance). The estimate therefore survives as the
route's published length on **one** path: where the description produced nothing. That branch has no
`else` — nothing overwrites the pre-description default and no log records the case — so the figure the
comment above it calls "the fallback for a route whose description produced nothing" is the start/target
air-line estimate, and every consumer of `RouteEntry.distance` (the app's `routeLengthMeters` fallback,
the car trip summary, any future caller) inherits a 25-45 % under-count. The device case that measures all
three numbers only *logs* the ratios and asserts `total > 0.0`, so nothing fails if the under-count
returns.

## What Changes

- **The published length's fallback becomes a route length** (submodule patch,
  `libosmscout-client-java/src/OSMScoutClient.cpp`): the pre-description default is no longer read from the
  estimate at all, and on the path where the description produced nothing the bridge now publishes the
  great-circle length of the polyline it built for the same result — the witness that already tracks the
  description to 0.4 %. Both calculation paths change together (`:6515`, `:7278`), and the comments plus the
  async path's success log that presented the estimate as an accumulated distance are corrected.
- **The start/target estimate stops being published as a length.** It is not dropped from the native side,
  where the cost limit and the progress denominator need it; it is removed from `RouteEntry.distance`
  (or, if a client turns out to need it, named so that it cannot be read as the route's length — decided in
  `design.md`, D2).
- **The device case gains the guard it lacks.** The existing instrumented case starts asserting what it
  prints today: the published total agrees with the drawn polyline and with the description's total within
  the description's own tolerance, so the under-count cannot return unseen.
- **The guidelines record which number is a route length** — the router's estimate is not one, and a surface
  reads the length through the one seam (`RouteStepValues.routeLengthMeters`). Recorded with the
  measurement recipe that produced the numbers (`guidelines/Build.md` §10).
- `TODO.md` §129 (user-visible half) and §139 (native half) are removed when this change is archived.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `osmscout-jni` — the existing requirement *One route length for a calculated route* is extended, not
  rewritten: the route's published length must be a length of that route (its geometry or its description)
  and must never be the start/target air-line estimate; a route without per-step values still carries a
  route length. The existing text and its four scenarios stay as they are — they state that the total and
  the step sum agree, which the app satisfies and which this change makes true at the source as well.

## Impact

Affected files and modules:

- `app/src/main/cpp/libosmscout/libosmscout-client-java/src/OSMScoutClient.cpp` — **submodule patch**
  (minimal, upstreamable), the two route-calculation paths.
- `app/src/main/cpp/libosmscout` — submodule commit + the main repo's gitlink bump (per `AGENTS.md`:
  patch the C++ side in the submodule, never in a second copy; keep the submodule clean).
- `osmscout-client-java` (`:osmscout-client-java`, Java override) — only if the published field is
  renamed; the `RouteEntry` accessor is what surfaces read.
- `app/src/main/java/com/naviveylin/ui/route/RouteStepValues.kt` — the fallback keeps returning the
  published length; it becomes a route length by virtue of the bridge fix (no rule change).
- `app/src/androidTest/java/com/naviveylin/route/RouteInstructionPositionDeviceTest.kt` — the guard.
- `guidelines/Build.md` §10 — the measurement recipe and which number is the route's length.

Not affected: `AbstractRoutingService.cpp` (upstream's field keeps its meaning — the estimate feeds the
cost limit and the native progress callback), the phone/car UI composition, the flavors, the manifests,
the native ABI set, and no Kotlin behaviour where a description exists.

Scope: surface-agnostic — both surfaces read the same `RouteEntry`, and the change touches no
phone-only or car-only path. Both distribution flavors build the same native TU.

Kind: **additive** for the app (the displayed number does not move where the description exists; the
step-sum rule keeps winning) and a defect fix for the fallback path. Breaking for a hypothetical caller
that reads `RouteEntry.distance` on a description-less route: its number changes from the air-line
estimate to a route length — that is the point of the change.

Rollback: revert the two bridge lines and the gitlink bump (one commit), restore the device case's
assertions; the tree then behaves exactly as today, under-count included.

Guidelines referenced: `guidelines/Build.md` §10 (on-device recipe), `guidelines/Design.md` (native
boundary — one seam per value). Sequencing: `fix-step-leg-distance-and-time` (25/26, in flight) touches
the same capability's delta (`osmscout-jni`, `route-analysis`) and produces the per-step legs this
change's numbers come from; this change lands after it, or on top of it, and re-reads its final spec text
before writing the delta.
