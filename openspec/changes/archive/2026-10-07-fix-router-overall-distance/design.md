# Design

## Context

See `proposal.md` — Why. The state the approach has to fit:

- The bridge publishes the route's length in two places, both derived from the same native field:
  `libosmscout-client-java/src/OSMScoutClient.cpp:6140` and `:6852`
  (`totalDistance = result.GetOverallDistance().AsMeter()`), each carrying a comment that claims the value is
  "the router's own accumulated distance ... the fallback for a route whose description produced nothing".
  Inside the description branch the same variable is then overwritten with the description's own total
  (`:6396`/`:6399`), so the estimate is what a **description-less** route publishes, not what a normal route
  publishes.
- That field is the **air-line estimate**: `AbstractRoutingService.cpp:1088-1094` sets it from
  `GetSphericalDistance(startCoord, targetCoord)` and uses it for `GetEstimateCosts` / `GetCostLimit`
  (`:1090-1092`) and for the progress denominator (`:819`, `currentMaxDistance, overallDistance`).
  Upstream's `Demos/src/Routing.cpp:1291` prints it as "Air-line distance", so its meaning is upstream's.
- The route's real length already exists on the same code path: the description's total is the sum of
  `GetEllipsoidalDistance` between consecutive route nodes (`RoutePostprocessor.cpp:182`,
  `DistanceAndTimePostprocessor`), and it tracks the drawn polyline to 0.4 % on the measured routes.
- The app reads a length through exactly one seam: `RouteStepValues.routeLengthMeters`
  (`app/src/main/java/com/naviveylin/ui/route/RouteStepValues.kt:53-65`) prefers the step sum and falls
  back to `RouteEntry.distance`; `RoutePanelViewModel.kt:681-687` logs the divergence. Because the bridge
  now publishes the description's total, that divergence check compares two numbers from one source and can
  only fire on a partial step list (`TODO.md` §129's "34 % apart" measurement predates
  `fix-route-length-disagreement`) — worth knowing when reading the diagnostic, not part of this change.
- The one case that measures all three numbers, `RouteInstructionPositionDeviceTest.routeLengthsAreMeasuredForALongAndAShortRoute`
  (`app/src/androidTest/java/com/naviveylin/route/RouteInstructionPositionDeviceTest.kt`), logs
  `routerOverPoly` / `descriptionOverPoly` / `descriptionOverRouter` per candidate and asserts only
  `total > 0.0` and `polylineMeters > 0.0` — the under-count is invisible to the suite.

## Goals / Non-Goals

**Goals**

- Every path that publishes a route length publishes a route length (the description's total where it
  exists; the published polyline's great-circle sum where it does not).
- The air-line estimate keeps its two jobs (cost limit, progress) and stops being a route's length.
- The device case fails when a published length leaves the route, without new setup: it already walks
  three routes and prints every number the assertion needs.
- The rule survives a fresh clone: which number is a route's length is stated in `guidelines/` next to the
  recipe that measured it.

**Non-Goals**

- Not changing the number a user sees where a description exists (the step sum keeps winning).
- Not changing `RoutingResult::overallDistance` or any other upstream field semantics, and not touching
  the routing algorithm, the cost model or the progress callback.
- No map-data re-import, no UI change, no new Kotlin component, no flavor/manifest change.

## Decisions

### D1: where the published length comes from

**Chosen**: the description's total where the description exists (reuse the value the same path already
computes), and where the description produced nothing, the great-circle length of the polyline that same
call publishes.

*Alternatives*

- **Change `RoutingResult::overallDistance` upstream to the accumulated route distance** — rejected: that
  field is the estimate for `GetEstimateCosts`/`GetCostLimit` and the progress denominator, and upstream's
  demo prints it as the air-line distance. Repurposing it would change progress semantics
  (`currentMaxDistance / overallDistance`) and needs an upstream API decision for a single client's naming
  problem.
- **Sum the route data's nodes in the bridge before the description is generated** — same outcome as the
  chosen fallback, more code: the polyline the bridge publishes *is* those node coordinates, so the sum
  over the published vertices subsumes it and is the same witness the device case uses.
- **Publish the estimate but label it** — kept as D2's alternative, not as a way to satisfy "one length".

### D2: what happens to the returned estimate

**Chosen**: `RouteEntry.distance` carries a route length on both paths; the estimate is not returned to
the client. Nothing in the app reads the estimate (grep: only `RouteStepValues`' fallback and the
divergence diagnostic, both of which want a length).

*Alternatives*

- **Keep it under an estimate-named accessor** (`airLineDistanceMeters`) — rejected unless apply finds a
  consumer: it adds API surface for nobody, and the second requirement in the delta permits it later.
- **Drop the field from the native result entirely** — rejected: the native routing needs it, and this
  change is a bridge-side fix, not an upstream behaviour change.

### D3: the guard

**Chosen**: extend the existing instrumented case from logging to asserting — published total against the
description's total where present, and against the polyline length in every case, within the tolerance the
description itself achieves (measured 0.9964-1.0021).

*Alternatives*

- **A native test in the submodule's `Tests/`** with runtime-imported map data (the
  `LocationServiceTest.cpp` pattern) — heavier: needs an import harness, and the JNI TU cannot be compiled
  in the host build today (`TODO.md` §66). Rejected for this change; a candidate afterwards.
- **A host-level Kotlin test of the seam** — cannot see the defect: the JNI test stub is symbol-free, so no
  host test can calculate a route.

### D4: where the rule is recorded

**Chosen**: `guidelines/Build.md` §10, beside the device recipe that produced the numbers, plus the
existing `osmscout-jni` requirement this delta extends.

*Alternative*: a new guidelines section for "route length" — rejected: the value has one seam
(`RouteStepValues.routeLengthMeters`) and one measurement location; `guidelines/Design.md` already states
the one-seam rule that this follows.

## Risks / Trade-offs

- [The description's total is unavailable on an early-failure path where the route data exists] → the
  fallback sums the polyline the same call publishes; if neither exists, publish no length and keep the
  route unreported (today's behaviour) rather than falling back to the estimate.
- [A hard tolerance could fail on other data] → the guard uses the description as its reference where
  present (measured agreement 0.4 %) and states its tolerance in the task; the polyline is the second
  witness, as in `TODO.md` §139.
- [`fix-step-leg-distance-and-time` (25/26, in flight) rewrites the same spec area] → this delta only ADDs
  requirements and never rewrites the existing one; it lands after that change or on top of it, and its
  final `osmscout-jni` text is re-read before the delta is written.
- [Submodule drift / a second copy of the bridge] → one commit on `naviveylin-local`, `git ls-remote origin
  <branch>` checked immediately before the push (AGENTS: one session owns the submodule and its pushes),
  then the main repo's gitlink bump in the same commit as the bridge-dependent assertions.
- [The progress denominator or the cost limit changes behaviour] → they read the estimate inside the native
  routing and are not touched; the change is confined to what the result publishes.

## Migration Plan

1. Submodule: fix the published length in both bridge paths, correct their comments, commit on
   `naviveylin-local`.
2. Main repo: bump the gitlink in the same commit as the app-side change (per `AGENTS.md`).
3. Extend the device case's assertions (same commit).
4. `guidelines/Build.md` §10 note (same commit).

Rollback: revert the commit (bridge lines + gitlink bump + assertions). The tree returns to today's
behaviour, under-count included, and the device case returns to logging only.

## Verification

- **Device (primary)**: `RouteInstructionPositionDeviceTest.routeLengthsAreMeasuredForALongAndAShortRoute`
  on the AAOS AVD (`emulator-5556`) or a phone AVD — quote the per-candidate ratios before and after:
  measured before on three routes `routerOverPoly` 0.748 / 0.795 / 0.552 with `descriptionOverPoly`
  1.0014 / 1.0021 / 0.9964; expected after: both within the tolerance on every candidate. Recipe and the
  install/build caveats (`-Pandroid.injected.build.abi=x86_64`, `testOnly`, `adb install -r -t`) live in
  `guidelines/Build.md` §10.
- **Host**: the existing `:app` suites (`RouteStepValues`/route-panel cases) stay green — the change must
  not move the displayed number; both flavors build with all three ABIs (the native TU is linked by both).
- **Revert-check** (the invariant this change creates): restore the air-line assignment in one bridge path,
  and the new assertion must fail on the affected candidates; restore the fix and re-run the forced case
  green (`revert-check` skill; the device case is the failing case, so the mutation and the run happen on
  the same AVD session).
- **Compile check for the JNI TU**: the Android build (`:app:assembleMobileDebug`) is the reliable compile
  path; `TODO.md` §66 records the local meson `-fsyntax-only` workaround if only the TU is wanted.

Threading and lifecycle: no new component and no Kotlin-side concurrency — the change sits in the existing
native route-calculation path, whose thread and lifecycle are unchanged (see `guidelines/Design.md` §4).

## Open Questions

**Answered during apply (2026-10-07), kept for the record:**

- *Is the description-less path reachable?* The description branch has **no `else`** in either path, so a
  route whose description generation fails (or returns none) keeps whatever `totalDistance` holds. Before
  this change that was the air-line estimate; it is now `0.0` until the new polyline-sum fallback fills it
  (`OSMScoutClient.cpp:6515` in `Java_..._calculateRouteWithObjectsWithProfile`, `:7278` in
  `calculateRouteAsync`). The branch is therefore **defensive as far as the tree knows**: no case, log line
  or diagnostic in the repository produces it, which is why task 3.3 exercises it with a deliberate
  uncommitted mutation rather than claiming coverage.
- *Does a consumer want the estimate?* No: after the change the only use of `GetOverallDistance()` in the
  bridge is the async path's success log, now labelled `air-line estimate=` (`:6868`). The native routing
  itself keeps using the value for its cost limit and progress denominator.

**Left open:** none that would change the specs, the approach or the task order.
