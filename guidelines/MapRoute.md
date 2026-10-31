# Map Route — a route's numbers

This document owns the numbers derived from a route: one length for one route, the single accessor the
app reads it through, and the diagnostics line that measures the two against each other. The pipeline
that draws the route is `guidelines/MapRendering.md`; the surfaces that display the numbers are
`guidelines/UIPhoneMap.md`.

---

## 19. Route data — one length for one route

**The route's length is the route description's own total.** `RouteEntry.distance` is set from the
description's cumulative node distances (the sum of the per-step legs when the bridge published aligned
ones — which is the normal case — else the last node's cumulative distance, else the router's own figure as
the fallback for a route whose description produced nothing). The router's `GetOverallDistance()` is **not**
the route's length: it disagrees with the geometry the same routing produced, measured on device 2026-10-05
as 0.748× of the drawn polyline on a ~70 km route, 0.795× on a ~17 km one and 0.552× on a ~1.5 km one —
its relative error grows as the route shortens, so neither a fixed ratio nor a fixed offset explains it
(`TODO.md` §139; the user-visible half is §129). Both route-building paths in
`libosmscout-client-java/src/OSMScoutClient.cpp` (`calculateRouteWithObjectsWithProfile` and
`calculateRouteWithObjectsAsync`) derive the length this way, and the change is a submodule commit on
`naviveylin-local` — not an override in the `:osmscout-client-java` module.

**The app reads it in one place.** `routeLengthMeters(route)` (`app/.../ui/route/RouteStepValues.kt`) is the
single accessor: it prefers the aligned per-step legs sum (so the displayed total is literally the number the
step list below it adds up to) and falls back to `RouteEntry.distance`. The planning card's statistic, the
route summary and the summary dialog read it; `NavigationEngine.routeTotalDistanceMeters(route, lats, lons)`
does the same for the value navigation publishes, which the routing-status progress denominator divides by.
That helper keeps one older rule intact: a route without usable geometry publishes **0** even when the
bridge handed over a length, because the surfaces have no geometry to draw it on
(spec: `navigation-engine` — Acquisition without usable polyline geometry).

**Do not add a second derivation.** A new consumer that sums the polyline itself, or reads the router's
figure, recreates exactly this defect: two totals for one route, one of them visible next to a step list that
adds up to the other. `computeRouteDistance` remains only as the geometry-less fallback inside the helper.

**Measurement.** `RoutePanelViewModel.logStepValues` emits one coordinate-free `Diag/ROUTE` line per
calculated route (`route analysis: steps=… withValues=… sumM=… totalM=… sumS=… totalS=… maxErrM=…`) comparing
the legs sum with the route's total; `maxErrM` is ~0 on a correct build. That line deliberately keeps
`RouteEntry.distance` as its `totalM` rather than the accessor's value — computing the total *from* the legs
would make the comparison vacuous. `stepValuesDiverge` is the guard on it (2 % / 10 m band since this
change, tightened from 50 %). The on-device half is
`RouteInstructionPositionDeviceTest` (`lengths:` lines on `adb logcat -s RouteDeviceTest`), which prints the
router's figure, the legs sum and the drawn polyline per candidate route.

---
