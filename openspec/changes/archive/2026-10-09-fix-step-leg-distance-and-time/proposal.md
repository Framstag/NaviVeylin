# Proposal

## Why

The per-step distance and duration the phone shows for a calculated route are **not the leg that leads to
a step's manoeuvre** — they are the last geometry edge before it. The bridge advances its reference on
every route *node* (`BeforeNode` in `DescCallback` / `CollectCallback`, ~hundreds for a 17 km city route)
while a line is emitted only for the ~19 nodes carrying a description, so each row reports tens of metres
and seconds instead of ~1 km and ~1 min. The route's own headline (native overall distance) stays correct,
so the sums the user reads are "far below" what they should be, and the same defect makes
`RouteInstruction.timeTo` a fragment, which the navigation list (rendered in minutes → `0 min`) and the
car's per-manoeuvre arrival estimate consume.

Measured, not guessed: archived change `route-planning-session` task 12.12 recorded the device output of
one row as **`14 m` + `2 s`** on the 17,3 km / 19-step Dortmund→Bochum route (7 m/s of a single edge).
libosmscout's own Qt client does it the other way — `RouteDescriptionBuilder.cpp` `MkStep` advances
`distancePrevious`/`timestampPrevious` per **emitted step** — so the Java bridge diverges from the
library's own convention.

## What Changes

- **Bridge (native, both `DescCallback` copies):** the per-step reference advances when a **line is
  emitted**, not per node (Qt `MkStep` parity). Distance *and* duration of a step become the leg that ends
  at that step's manoeuvre; the sum over the rows then telescopes to the route's overall distance/duration.
- **Bridge:** the `[x km, y min]` bracket keeps its format but now carries those same leg values.
- **Bridge (new API, additive):** `RouteEntry` gains per-step numbers aligned one-to-one with the
  instruction lines and with the existing `instructionLats`/`instructionLons`: `instructionDistances`
  (metres, the leg that ends at that line's manoeuvre) and `instructionTimes` (seconds, same leg). Dropped
  (left null) when the alignment check fails, exactly like the position arrays. `RouteInstruction` gains the
  matching `legDistance`, so the navigation list can show a step's own segment too.
- **Bridge:** `CollectCallback` derives `RouteInstruction.timeTo` as the leg's travel time (advance per
  emitted instruction), so the navigation list and the car hint stop reporting an edge in seconds.
- **App:** the route analysis list, the step navigator and the route summary take per-step distance and
  duration from the new numbers and format them with the shared, locale-aware formatters; the
  `[x km, y min]` string parser degrades to a fallback used only when the arrays are absent.
- **App:** the navigation details list shows each step's own segment (leg distance and leg time) instead of
  a route-start-to-here distance next to a leg time.
- **App:** a coordinate-free diagnostics line reports the per-step sum against the route total
  (`RoutePanelVM: route analysis steps=… sumM=… totalM=…`), and a divergence beyond tolerance is logged as
  a warning, so a bridge regression is visible on device instead of only in the pixels.
- **Docs:** correct the wrong claim that the bracket is "the cumulative distance at its own node minus the
  previous node's" (spec `route-analysis`, `RouteStepSegments.kt`), and state the new contract.

Not in this change: the `RouteInstruction.distanceTo` duality (cumulative for the list, remaining for the
next instruction) stays as it is; the navigation list derives its leg distance in the app.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `osmscout-jni`: the bridge contract for per-step values — `RouteEntry` carries leg distance (metres) and
  leg duration (seconds) per instruction line, index-aligned and dropped as a pair on mismatch;
  `RouteInstruction.timeTo` is the travel time of the leg that ends at that instruction. Today the spec
  describes the bridge only in general terms and does not cover the per-step values at all.
- `route-analysis`: the step list / step navigator show each step's leg distance and leg duration, taken
  from the bridge's per-step numbers, so a row's numbers describe the same leg the selection highlights and
  the rows sum to the route's total; the rationale sentence about the bracket's meaning is corrected.
- `routing-summary`: the step list's per-step distance and time are the legs of the listed steps and sum to
  the route's total distance/duration; the values are formatted by the app (locale-aware), not by the
  native string. Today the spec only says a step "SHALL show ... the distance for the step, the time for
  the step" without saying which distance that is.
- `navigation-status-details`: the route description list shows each step's own segment — leg distance and
  leg time — so it matches the route summary's per-step values (its time already "SHALL match"; the
  distance is new, and today the row pairs a route-start distance with a leg time).

## Impact

Affected code:

- `app/src/main/cpp/libosmscout/libosmscout-client-java/src/OSMScoutClient.cpp` — `DescCallback` (`BeforeNode`,
  `NextLine`, `AppendDistanceTime`) in **both** copies (calculate + reroute) and `CollectCallback`
  (`BeforeNode`, `SegmentTimeSeconds`); new per-step vectors and their marshalling into `RouteEntry` in both
  route entry points.
- `app/src/main/cpp/libosmscout/libosmscout-client-java/java/com/framstag/libosmscout/client/RouteEntry.java`
  (submodule Java API) and the override `osmscout-client-java/src/main/java/com/framstag/libosmscout/client/OSMScoutClient.java`
  plus the app's local-only `InstalledMaps`-style override inventory if the field list is mirrored there.
- `app/src/main/java/com/naviveylin/ui/route/RoutePanelViewModel.kt` (`onSuccess`, `adoptRoute`,
  `parseStepDisplay`, the analysis state), `ui/route/RouteStepSegments.kt` (comment + any distance use),
  `ui/route/RouteSummary.kt`, `ui/route/RoutePanel.kt` (`StepNavigator`),
  `ui/navigation/NavigationDetailsOverlay.kt`, `ui/navigation/NextTurnOverlay.kt` / `ui/map/CarSessionSurface.kt`
  where a row's values are read, `core/src/main/java/com/naviveylin/core/DistanceFormat.kt` (a per-step
  duration formatter with a seconds branch).
- `app/src/test/...` fakes (`FakeOSMScoutClient`, route fixtures) and new unit tests; no manifest, resource
  or Gradle change expected.

Native/JNI classification: **submodule patch, minimal and upstreamable** (upstream file
`libosmscout-client-java/src/OSMScoutClient.cpp` on branch `naviveylin-local`, gitlink bump in the main repo
required). No local override is added to the bridge module beyond what exists.

Additive vs. breaking: **additive at the API level** (two new `RouteEntry` fields; no signature change) but
**behaviour-breaking for the value semantics** of the per-step numbers and of `RouteInstruction.timeTo` —
consumers in this repo are updated in the same change; no other in-tree consumer exists. Rollback path:
the app uses the new arrays only when present and otherwise falls back to the existing bracket parser, so
reverting the submodule commit (and the gitlink bump) leaves a working app with the old fragment numbers
rather than a broken one.

Guidelines affected: `guidelines/Design.md` (native boundary, one shared formatter per value across phone
and car), `guidelines/UI.md` (step row contents, phone/car parity of the per-step values),
`guidelines/Build.md` (§2 gate discipline and §10 on-device measurement recipe for the sum-vs-total check)
and `AGENTS.md` ("Agent iteration loop (measure first)" — the acceptance of this change is a measured
number, not a screenshot).

Scope: general — the fix is in the shared JNI bridge and the shared step-list components, so it applies to
the phone, the docked wide layout and the car surfaces, not to one surface only.
