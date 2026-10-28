# Design — route panel from structured steps

## Context

`RouteEntry.steps` (`com.framstag.libosmscout.client.RouteStep[]`) already crosses JNI on every route
calculation (`OSMScoutClient.cpp:8434`, `:9207`) and carries, per described route node: `kind`, `coord`,
`distanceFromStart`, `timeFromStart`, `legDistance`, `legTime`, `name`, `nameChanged`, `typeName`,
`crossingWays`, `direction`, `turn`, `roundaboutEnter/Leave` (with `exitCountTaken`, `clockwise`),
`motorwayEnter/Change/Leave` (with `from`, `to`, `move`), `junction`, `destination`, `maxSpeed`, `lanes`,
`suggestedLanes`, `poi`, `via`, `start`, `target`, `databaseId`, `pathObject`, `currentNodeIndex`,
`targetNodeIndex`, plus `RouteEntry.databaseMapping`. Nothing in the app reads it.

The list the app shows instead comes from `RouteEntry.descriptions` — English strings built in the bridge by a
callback that overrides `OnStart`, `OnTurn` and `OnTargetReached` only (`OSMScoutClient.cpp:8077`, `:8842`).
Roundabout and motorway rows are therefore missing, the text is untranslated, and the icon is chosen by a
prose matcher (`RoutePanelViewModel.parseTurnType:1104`).

## D1 — Where the row filter lives

| Option | What it costs | Risk |
|---|---|---|
| **A. App-side, in the route view model** (chosen) | one pure Kotlin function over `List<RouteStep>` | display policy lives in the app that displays it |
| B. A `RouteStepCollector` option in the submodule | a pin bump and a second consumer contract | a filter shaped for a phone list enters a library shared with Qt, JavaScout and the MCPServer; the owner asked for app-side |
| C. A `RouteDescriptionPostprocessor` filter upstream | submodule + upstream review | same as B, plus it would have to express display thresholds as library policy |

Chosen A: the owner directive is app-side, no pin bump is needed for this change, and a second consumer that
wants a different row set (the car list, the future condensed/full toggle) reads the same full step list.

## D2 — A row owns a polyline range, not a point

Today `RouteStepSegments.stepSegments` matches each step's manoeuvre coordinate to the nearest polyline vertex
monotonically (`nearestVertexFrom`), and derives a segment from the previous match to its own. A condensed row
would defeat that twice: it has no single manoeuvre coordinate for the run it represents, and its interior
steps' manoeuvres would each snap and produce interior boundaries the highlight must ignore.

Chosen: **rows are boundaries, not points.** Row *i* owns the vertices from its own boundary vertex to row *i+1*'s
boundary vertex; the first row starts at the route's first vertex, the last row ends at the last. The anchor a
row publishes is its boundary; the segment is the span between consecutive boundaries. Condensing or merging a
step then moves no boundary and leaves no gap — the invariant the owner asked for ("on the map the condensed
turn must be shown, there must be no gap").

Alternatives rejected: keeping point anchors and marking merged steps "skip my anchor" (a second rule that must
agree with the first), and recomputing boundaries from geometry per row (a second source of truth for where a
manoeuvre is, when the step already carries `coord`).

Follow-up available, not required here: `pathObject` + `currentNodeIndex`/`targetNodeIndex` let the native side
name the exact polyline range; that retires the nearest-vertex match entirely and is scoped to
`route-steps-live-guidance` or a later change.

## D3 — Leg attribution: merging is a value fold

A row's values are `own.legDistance + Σ legDistance(merged steps)` and the same for time. Alternatives:

- **keep the values in the parallel arrays and index them** — impossible once rows ≠ steps;
- **recompute the leg from the polyline** — introduces a second length source, exactly the defect class
  `fix-step-leg-distance-and-time` closed ("14 m / 2 s" for a step of a 17,3 km route);
- **show an untranslated bracket string** — the parsing this change removes.

The fold keeps `routeLengthMeters`' existing rule intact: the rows' sum is still the route's length, and
`stepValuesDiverge` still has something true to compare. It also makes the future condensed/full toggle safe:
both modes sum to the same total because both derive from the same fold.

## D4 — Two text builders for one release

`:core` gains a structure-based label builder used by the rows. `TurnInstructionLocalizer` stays for the live
path, which still receives `RouteInstruction`.

| Option | Cost | Risk |
|---|---|---|
| **A. Add the new builder beside the old localizer** (chosen) | one short-lived duplicate mapping | two mappings can drift for one release; mitigated by a parity test over a shared fixture set |
| B. Replace the localizer now and convert the live path in this change | the whole engine/listener migration | that is `route-steps-live-guidance`, and it needs the pin bump; it would make one change both app-side and native |

Chosen A, with a parity case (`RoutePanelLocalizedRowsTest` "the row builder and the live localizer agree on a
turn") so the duplicate cannot silently disagree.

## D5 — Thresholds, and what measurement settles first

Three numbers exist and only one of them is known:

| Constant | Value | Basis |
|---|---|---|
| close-manoeuvre window | 200 m | `osmscout::client::RouteStepBuilder::NextNextWindowMeters` — reused so "close" means one thing app-wide |
| `MinRowLegMeters` | candidate 20 m | unmeasured; a node artifact below it carries no driving decision |
| `informative` / `POIAtRoute` / `Via` row policy | undecided | owner: "informative: depends" |

The **census** produces the evidence before the policy is fixed: counts and metres per kind, straight-on run
lengths, `nameChange` leg spread and how many name changes the next manoeuvre already restates, and the count
of `informative` steps whose only content is a name. Alternative rejected: guessing the floor and the policy —
the repo's rule is measure first, and a wrong guess here is invisible in review.

The filter therefore keeps **one predicate** for the undecided kinds, so the follow-up task changes one place.

## D6 — Census line shape

One `DiagnosticsLog` line per calculated route, on the existing `ROUTE_TAG`, coordinate-free (kinds, counts,
metres — `checkNoCoordinatesInLogs` stays green, `guidelines/Regulatory.md` §9):

```
steps=97 turn=31 straightOn=22 nameChange=18 roundabout=4 motorway=6 info=16
dist_km turn=41.2 straightOn=12.8 nameChange=6.1 info=0.9
leg_m median=310 p90=1450 under20=9
so_runs=7 so_len_median=420 so_len_max=2300
nc_total=18 nc_restated=11 nc_runs2plus=3 nc_empty=1 nc_leg_median=380
info_name_only=12
```

This is what decides `informative`, `POI`, `Via` and the floor, and it proves the roundabout/motorway gap this
change closes (their counts are non-zero while today's list shows none).

## Threading and lifecycle

Row derivation is a pure function over an immutable step list, called where the route is applied to UI state
today (`RoutePanelViewModel.buildSteps`, main thread, once per route — not per frame). The full step list and
the derived rows both live in the view model's state; the analysis selection stays a row index, so the existing
"selection cleared on route change" rule (`route-analysis` — Selection cleared on route change) is unchanged.
No new dispatcher, no new native call, no lifecycle owner.

## Risks

| Risk | Mitigation |
|---|---|
| Rows ≠ steps breaks an existing assumption elsewhere (e.g. a consumer indexing `descriptions`) | grep gate: no `descriptions`/`instructionLats` reads outside the retired parsers; the live path is untouched |
| A condensed run's highlight drifts at the run's ends | `RouteStepSegmentsTest` case for the condensed row, plus the pixel/geometry check on device |
| Two text builders disagree for one release | parity case over a shared fixture set (D4) |
| The census is read on one route and generalizes badly | census taken on a city route and a motorway route before the policy task is closed |
| Merging hides a real manoeuvre (a step merged that the driver needed) | every merge rule has a case proving the *kept* row exists and names the merged content when it carries a name |

## Verification

- **Unit**: `RouteRowDerivationTest` (one case per rule in `route-analysis` — Rows are derived from the route's
  steps), `RouteStepSegmentsTest` (tiling, condensed row), `RoutePanelLocalizedRowsTest` (German rows, icon from
  kind, parity with the live localizer), `RouteStepValuesTest` (fold, no borrow).
- **Revert-check**: for the tiling invariant, the sum invariant, the `informative`-never-a-row rule and the
  name-change merge window — one mutation each, named case must fail, restore, forced green.
- **Device (measurement)**: the census line on one city and one motorway route, recorded with its numbers; the
  recipe lands in `guidelines/Build.md` §10 next to §10j.
- **Pixels**: one screenshot of the list on a stationary emulator with a calculated route, `pixel-check` skill —
  the claimed property (rows exist, are localized, are condensed) is producible stationary, so no
  "cannot be measured" note is needed.
