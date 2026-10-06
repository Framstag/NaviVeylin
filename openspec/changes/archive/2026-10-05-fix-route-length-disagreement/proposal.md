# Proposal

## Why

Two native numbers describe one calculated route and disagree. On a device run (AAOS AVD `emulator-5556`,
NRW database, Dortmund Hbf -> Cologne Hbf, 2026-10-05) the per-step legs summed to **97 416 m** while
`RouteEntry.distance` — the router's `GetOverallDistance()` — reported **72 771 m**, a ratio of **1.34**.

`fix-step-leg-distance-and-time` (in flight, 25/26) makes each step row carry its own leg, so both figures
are now user-visible side by side: the card headline shows `RouteEntry.distance` (`RoutePanel.kt:217-222`)
and the step list sums the legs, while the routing-status distance progress line divides by the total
(`navigation-status-details`) and the car trip summary publishes it. One route therefore has two lengths
depending on which screen the driver reads, and on short routes the relative gap is larger still.

The discrepancy is not caused by that change — it only became visible. The loose guards added with it
(`stepValuesDiverge`, 50 %; the device case's `ratio > 0.5`) exist precisely because this is unresolved.

## What Changes

- **Measure, then decide which figure is the route's length** (owner decision, 2026-10-05: measure first,
  then (A) or (B); see Decision) — the two native numbers are compared against a reference before either is
  made the contract.
- Make the bridge publish **one** route length for a calculated route: `RouteEntry.distance` and the
  description's terminal cumulative node distance agree, or the bridge publishes both under names that say
  which is which and the app uses one.
- Make the app's route-length consumers read that one source: the planning card's headline distance
  (`route-planning-session`), the route summary and the summary dialog totals, the routing-status distance
  progress denominator, and the car trip summary.
- Add one **coordinate-free** diagnostics line per calculated route reporting both native numbers and the
  leg sum, so the disagreement is measured on device instead of inferred from a device test.
- Tighten the guards that currently hide the gap once the numbers agree: the `stepValuesDiverge` threshold
  (`RouteStepValues.kt:74`) and the device case's `ratio > 0.5`.
- Record in `guidelines/` which number is the route's length and why.

Not in scope: the duration estimate (`RouteEntry.duration` is currently derived from the total distance at
a hardcoded 50/15/5 km/h, `OSMScoutClient.cpp:6521-6529`) beyond whatever follows automatically from the
chosen length; and the `RouteEntry` field layout.

### Decision

**Measure first, then choose (A) or (B). Owner decision, 2026-10-05.** The candidates were:

   - **(A) `GetOverallDistance()` is authoritative** — it is the router's own accumulated routing cost.
     Then the description's node-to-node sum is inflated (a node whose index resolves against a different
     way would produce exactly this), and the fix is in the description/postprocessor path. Consequence:
     touches `DistanceAndTimePostprocessor` output, so every per-step leg changes value too.
   - **(B) The description's node sum is authoritative** — it is what a driver's step list telescopes to.
     Then `RouteEntry.distance` is recomputed from the description's last node. Consequence: the headline
     number changes on every route while the steps stay as they are; smallest diff.
   - **(C) Publish both, named** — `distance` (router) plus `descriptionDistance`, and the app picks one.
     **Rejected as an end state**: it defers "which is the route's length" to every consumer, which is the
     current defect in a nicer shape. It remains available as the interim step that makes the measurement
     legible, if measuring needs the second number on device.

The measurement is the design phase's first work item, and the specs state the contract once it lands: the
route exposes **one** length and its per-step legs sum to it, with the source named. The measurement
compares the two native numbers and the leg sum against a reference — a GPX of a driven route, or
`osmscout`'s own `Demos/src/Routing.cpp` output for the same two coordinates (it prints the description's
cumulative distance per node) — for at least one long intercity route and one short town route, so the gap
is characterised rather than sampled once.

## Capabilities

### New Capabilities

None. The contract belongs next to the existing bridge contract in `osmscout-jni`.

### Modified Capabilities

- `osmscout-jni`: new requirement — a calculated route exposes one length, and its per-step legs sum to it
  (the per-step requirement this must agree with is currently an ADDED requirement in the in-flight
  `fix-step-leg-distance-and-time` delta, and its text already records this disagreement).
- `routing-summary`: the total distance the component shows SHALL be the number its listed step legs sum to.
- `route-summary-dialog`: the "total route distance" statistic SHALL be that same number.
- `navigation-status-details`: the distance progress line's denominator SHALL be that same number (today it
  is "the total route distance", which is currently ambiguous between the two figures).
- `route-planning-session`: the planning card's headline distance SHALL be that same number.

## Impact

**Native / JNI (submodule patch — minimal, upstreamable):** both numbers are produced in one place,
`app/src/main/cpp/libosmscout/libosmscout-client-java/src/OSMScoutClient.cpp` —
`totalDistance = result.GetOverallDistance().AsMeter()` at `:6136` (written to `RouteEntry.distance` at
`:6518`) and the per-node `node.GetDistance()` feeds the description at `:6223` via
`DistanceAndTimePostprocessor` (`:6143`). The `:osmscout-client-java` module overrides five files, none of
them `RouteEntry`, so the fix is a **submodule commit on `naviveylin-local` plus a gitlink bump in the main
repo** — not a bridge-module override. Note the in-flight `fix-step-leg-distance-and-time` also owns a
submodule change in this file; one session owns the submodule and the pushes (`AGENTS.md`).

**App / Kotlin:**
- `app/src/main/java/com/naviveylin/ui/route/RoutePanel.kt` (headline distance)
- `app/src/main/java/com/naviveylin/ui/route/RoutePanelViewModel.kt:678` (`stepValuesDiverge` call site)
- `app/src/main/java/com/naviveylin/ui/route/RouteStepValues.kt:74` (threshold)
- `app/src/main/java/com/naviveylin/ui/route/RouteSummary.kt`, `RouteSummaryDialog` (totals)
- `app/src/main/java/com/naviveylin/ui/navigation/NavigationDetailsOverlay.kt` (progress denominator)
- `auto/src/main/java/com/naviveylin/auto/NavigationTemplateMapper.kt` (trip distance)
- `app/src/androidTest/java/com/naviveylin/route/RouteInstructionPositionDeviceTest.kt` (the loose bound)

**Tests / evidence:** host unit tests for whichever total the app reads; the device case
`RouteInstructionPositionDeviceTest.perStepValuesAreTheStepsOwnLegs` (already on the AAOS AVD) for the
native numbers; the new diagnostics line as the on-device measurement.

**Guidelines:** `guidelines/MapRendering.md` (native render/route data section) — and whichever document
owns the route data contract; not `UI.md`, since no visual rule changes.

**Scope:** general — phone and car both read the affected numbers; no AA-only behaviour.

**Additive or breaking:** additive at the API level. **Behaviourally** it changes the value of
`RouteEntry.distance`'s *source* under option (A) or (B), so every screen showing a route total changes
number together. **Rollback path:** revert the submodule commit and its gitlink bump (native half) or the
app-side source swap (app half); each half is verifiable independently.

**Coordination:** `fix-step-leg-distance-and-time` (in flight) adds the sibling requirement to
`osmscout-jni` and already cites this finding. Its delta cites `TODO.md §126` for the distance
disagreement, but §126 is the car navigation-lease entry — the finding is **§129**; that citation should be
corrected when this change lands (or in that change, if it archives first).
