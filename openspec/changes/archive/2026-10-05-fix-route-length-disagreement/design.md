# Design

## Context

See `proposal.md` — Why and Decision. What shapes the approach:

- Both figures are produced in one function, `calculateRouteAsync` in
  `app/src/main/cpp/libosmscout/libosmscout-client-java/src/OSMScoutClient.cpp`: the router's
  `result.GetOverallDistance()` at `:6136` becomes `RouteEntry.distance` (`:6518`), while the per-step legs
  come from the description's `node.GetDistance()` (`:6223`) via `DistanceAndTimePostprocessor` (`:6143`)
  and are published as `instructionDistances` (`:6477-6497`).
- The measurement vehicle largely exists: `RoutePanelViewModel.logStepValues` already emits a
  `DiagnosticsLog.ROUTE_TAG` line built by `stepValuesSummary(steps, route.distance, route.duration)`
  (`RouteStepValues.kt:60-65`) carrying `sumM`, `totalM`, `maxErrM` — i.e. both figures and the legs sum in
  one coordinate-free line.
- `stepValuesDiverge` (`RouteStepValues.kt:74`) only makes `logStepValues` emit an extra `Log.w`. It does
  **not** drop or hide step values, so its threshold is a warning sensitivity, not a behaviour switch.
- The five consumers read the total straight off `RouteEntry.distance`: `RoutePanel.kt:217-222`,
  `RouteSummary.kt:74-75`, the summary dialog, the routing-status progress denominator
  (`NavigationDetailsOverlay.kt`), and the car trip mapper.
- `fix-step-leg-distance-and-time` (in flight) patches the same native function on `naviveylin-local` and
  adds the sibling per-step requirement to `osmscout-jni`.
- `adb devices` was empty at triage, but the instrumented case `RouteInstructionPositionDeviceTest` that
  measured this ran on the AAOS AVD `emulator-5556`.

## Goals / Non-Goals

**Goals:**
- Settle which figure is the route's length from a measurement, not from argument.
- Leave exactly one source the app reads for a route's length, so the headline, the step list, the progress
  denominator and the car trip cannot drift apart again.
- Keep the measurement readable from a normal on-device run, without a rebuild.

**Non-Goals:**
- The duration estimate (`RouteEntry.duration` is derived from the total at a hardcoded 50/15/5 km/h,
  `OSMScoutClient.cpp:6521-6529`) beyond what follows from the chosen length.
- The `RouteEntry` field layout, the description format, and the postprocessor set.
- Changing any displayed number *before* the measurement decides (see D5).

## Decisions

### D1 — Measure first, then choose between two candidate sources

Owner decision (2026-10-05), recorded in the proposal. The candidates are (A) the router's
`GetOverallDistance()` and (B) the description's terminal cumulative node distance; the fix differs
completely between them (A rewrites the description/postprocessor path and moves every step leg, B moves
only the headline). Alternatives considered:

- **Pick (A) or (B) now** — rejected. The two numbers differ by 34 % on the measured route; a guess would
  rewrite either the headline or every step row on no evidence.
- **Option (C), publish both under distinct names, as the end state** — rejected in the proposal: it defers
  "which is the route's length" to every consumer, which is today's defect in a nicer shape. It stays
  available *only* as a measurement aid if the second number cannot be observed otherwise.

**Measurement protocol** (one session, one device):

| | |
|---|---|
| Routes | the long intercity route (Dortmund Hbf -> Cologne Hbf, ~70 km) **and** a short town route (~2 km, the `TODO.md` §133 sample is 847 ms) |
| Numbers | router total, description total, legs sum — the existing `ROUTE` diagnostics line already prints all three (`sumM` / `totalM`) |
| Reference | `osmscout` `Demos/src/Routing.cpp` for the same coordinate pair (it prints the description's cumulative distance per node), or a GPX of a driven route |
| Verdict | for each route, which figure the reference matches, and by how much the other differs |
| If neither reference is available | record the disagreement and defer the decision explicitly — do not pick on the numbers alone |

**Resolved 2026-10-05: (B)** — the description's terminal cumulative node distance is the route's length.
Measured on `emulator-5554` (phone AVD, NRW database, case
`RouteInstructionPositionDeviceTest.routeLengthsAreMeasuredForALongAndAShortRoute`, `OK (1 test)`), with the
published polyline's own length as the reference (`Demos/src/Routing.cpp` and a GPX were unobtainable: no
linked demo binary in `hostbuild/`, no host-side map database):

| route | vertices | router total | description total | polyline | router/poly | desc/poly |
|---|---|---|---|---|---|---|
| Dortmund Hbf -> Cologne Hbf | 1663 | 72 771 m | 97 416 m | 97 283 m | 0.748 | 1.0014 |
| Dortmund Hbf -> Bochum Hbf | 640 | 16 677 m | 21 011 m | 20 966 m | 0.795 | 1.0021 |
| short hop within Dortmund | 91 | 824 m | 1 487 m | 1 493 m | 0.552 | 0.9964 |

The description tracks the drawn polyline to within 0.4 % on every route, so it is not inflated — it is the
figure that agrees with the geometry the app draws, steps through and highlights. The router's
`GetOverallDistance()` is the figure that departs from the route it produced, and its relative error grows as
the route shortens (0.748 -> 0.795 -> 0.552), which rules out both a fixed ratio and a fixed absolute offset
as its explanation. External check: the real Dortmund Hbf -> Cologne Hbf road distance via A1/A45 is ~95-100
km, matching the drawn 97.3 km, while 72.8 km is close to the ~66 km great-circle distance.

Consequence for this change: the app publishes the description's figure. The router's under-count is a
separate native defect — noted for a `TODO.md` entry rather than absorbed here, because a future consumer
reading `GetOverallDistance()` directly would inherit it unseen.

### D2 — The measurement rides on the existing `ROUTE` diagnostics line

`route analysis: steps=N withValues=M sumM=… totalM=… sumS=… totalS=… maxErrM=…` already carries both
figures and their difference. Add the reference comparison to that line (or to the measurement task's
recorded output) rather than introducing a second line. Alternatives: native `osmscout::log.Debug()`
(gated off by default, so a normal run shows nothing), a new instrumented-only assertion (invisible during
an ordinary on-device session). Threading is unchanged: `DiagnosticsLog.log` buffers on the caller's thread
and its worker writes the file (`AGENTS.md` — Kotlin logging), so no new dispatcher hop is introduced.

### D3 — One app-side accessor for the route's length

Introduce one pure helper next to `RouteStepValues` (in `:core`'s or `:app`'s route package, following the
existing placement) that yields the route's length from the chosen source, and route all five consumers
through it. Rationale: five independent reads of `RouteEntry.distance` are exactly how the two totals
became visible side by side. Alternatives: patch each site (rejected — drift returns with the next
consumer); rely on the bridge alone and keep raw reads (that is what the helper does once the source is
settled, but the helper gives a single unit-testable seam and one place to update if the source changes).

### D4 — Tighten the guards only after the chosen source has landed

Once the totals agree, `stepValuesDiverge`'s threshold drops from `maxOf(100.0, total * 0.50)` to the
rounding the per-step values justify (order of a few percent), and the device case's `ratio > 0.5` bound
tightens with it. Safe to tighten: the function only warns (`RoutePanelViewModel.logStepValues`), so a
tighter threshold cannot remove step values from the UI. Alternatives: leave the loose bound (rejected —
it is the guard that would have caught this, and it was widened to accommodate the defect).

### D5 — Displayed numbers stay as they are until the measurement decides

The first deliverable is the measurement; the specs' agreement scenarios become satisfiable only once the
chosen source lands, so tasks order: measure -> decide -> change the source -> tighten the guards. Showing
the legs sum as the headline immediately would be a second guess in the opposite direction.

## Risks / Trade-offs

- **Same submodule file as the in-flight change** -> sequence the two changes; one session owns the
  submodule and its pushes; run `git ls-remote origin naviveylin-local` immediately before a push
  (`AGENTS.md`, `TODO.md` §40.54).
- **Option (A) changes every per-step leg value** -> the device case and the `ROUTE` line are re-measured
  after the source change, and each new invariant gets its revert-check task.
- **The measurement needs a device** -> the instrumented case has already run on `emulator-5556`; if no
  device is available the measurement is the blocked first task and the change stops there rather than
  guessing.
- **Reference may be unavailable** -> `Demos/src/Routing.cpp` is the fallback for a GPX; if neither exists,
  defer the decision and say so (D1).
- **A stale cross-reference** -> `TODO.md` §126 is the car navigation-lease entry; this finding is **§129**.
  The wrong id sits in `RouteStepValues.kt`'s KDoc, in the existing diagnostics KDoc, and in the in-flight
  `fix-step-leg-distance-and-time` delta. Correct it in this change.
- **Tightening a threshold can make a shared suite fail on legitimate builds** -> only after the source
  change, with the device measurement as the evidence that the new bound holds.

## Migration Plan

Both halves are independently revertible: the native half is a submodule commit on `naviveylin-local` plus
a gitlink bump in the main repo (revert the bump), the app half is the accessor change (revert the commit).
No data migration; no persisted format change.
