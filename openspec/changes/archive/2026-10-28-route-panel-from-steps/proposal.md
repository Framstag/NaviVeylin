# Route panel from structured steps

## Why

The phone's route list is built by **parsing the native English description lines** back into structure:
`RouteEntry.descriptions` carries strings like `"Turn left onto Hauptstrasse  [1.2 km, 5 min]"`, and the app
recovers the turn type with a prose-prefix matcher (`RoutePanelViewModel.parseTurnType`, 26 branches), the
leg values with a bracket/unit parser (`parseStepDisplay`), and drops the `"--- Route ---"` header with
`isInstructionLine`. Three defects follow from that shape, all visible today:

- **Roundabout and motorway manoeuvres never reach the list.** The bridge's description callback
  (`OSMScoutClient.cpp:8077` and its copy at `:8842`) overrides `OnStart`, `OnTurn` and `OnTargetReached`
  only, while the postprocessor dispatches roundabout and motorway descriptions through their own hooks and
  excludes them from `OnTurn` (`RouteDescriptionPostprocessor.cpp:212-247`). A route over a roundabout
  therefore lists no exit and a motorway route lists no entry, change or leave.
- **The list is not localized.** It renders the native English text in every locale, while every other
  surface rebuilds the same words from resources.
- **The glyph is a guess.** `parseTurnType` decides the row icon from prose (`"bear left"`, `"highway"`),
  and it cannot distinguish a motorway change from a plain move at all.

In the meantime the bridge already publishes the structured model and nothing reads it:
`osmscout::client::RouteStep` (submodule commit `62a248b84`) crosses JNI through `CreateJavaRouteSteps`
on **every** route calculation (`OSMScoutClient.cpp:8434`, `:9207`), and lands in `RouteEntry.steps` and
`RouteEntry.databaseMapping`. The projection is live-exercised in production and has no consumer.

This change gives the route list that consumer. It is the first of three: the live navigation path follows in
`route-steps-live-guidance`, and the retirement of the prose path in `retire-instruction-prose`.

## What Changes

- **Add** a step-derived view model in `:core`: `Manoeuvre` (kind, move, exit ordinal, circulation direction,
  name/ref, way type, coord, leg distance, leg time) plus one structure-based label builder that turns it into
  localized text. `TurnInstructionLocalizer` stays as it is for the live path (retired in the follow-up
  change) — a deliberate, short-lived duplication so this change ships on its own.
- **Change** the route panel, the step navigator and the route analysis to read `RouteEntry.steps` and derive
  **rows**: manoeuvre rows, `nameChange` rows, condensed runs of straight-on turns, `informative` steps as data
  on a neighbouring row — never as rows.
- **Add** the row rules as an app-side filter: a row owns a polyline **range**, consecutive rows tile the
  polyline without a gap, and a row's leg is its own leg plus the legs of every step merged into it, so the
  rows keep adding up to the route's length.
- **Add** a coordinate-free **census** diagnostics line (counts and metres by step kind, straight-on run
  lengths, `nameChange` leg spread, `informative`-only-name count) and a device run that records it. The
  census settles the three thresholds this change cannot settle from the code: whether `informative` steps
  earn rows, whether `Via`/`POIAtRoute` earn rows, and the `MinRowLegMeters` floor.
- **Remove** the description-line parsing from the app: `parseStepDisplay`, `parseTurnType`,
  `isInstructionLine`, the bracket/unit parser, and the parallel-array alignment in `instructionValues`.

Not breaking for the submodule and not breaking for users of `RouteEntry` other than this app: both
`RouteEntry.steps` and `RouteEntry.descriptions` keep being published (the retirement of the second is
`retire-instruction-prose`).

**Scope**: phone route panel, route analysis and `:core`. The car surfaces are untouched here — their route
description screen reads the live `RouteInstruction` list, which belongs to `route-steps-live-guidance`.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `route-analysis` — **MODIFIED** "Analysed step is locatable on the map" (a row owns the run between two row
  boundaries, so a condensed row is highlighted without a gap) and "Step values describe the step's own leg"
  (a row's values are its own leg plus the legs merged into it); **ADDED** "Rows are derived from the route's
  steps" (the filter, the collapse, the merge and the tiling invariant).
- `route-panel-ui` — **MODIFIED** "Turn-by-turn instruction list" (the list is built from the route's steps and
  carries localized text, so it names roundabout and motorway manoeuvres it dropped before).
- `osmscout-jni` — **ADDED** "A calculated route publishes structured route steps" (the contract the app now
  reads: one step per described node, in route order, with its manoeuvre records, identity and leg values).
- `turn-instruction-localization` — **ADDED** "Route analysis rows are localized from the step model" (the list
  joins the surfaces that resolve their text from resources).
- `route-summary-dialog` — **MODIFIED** "Scrollable step list" (the dialog lists the route's rows with their own
  leg values and localized text, not `RouteInstruction` entries with native description text).

Unchanged on purpose: `osmscout-jni`'s per-step array requirements stay valid, because the bridge keeps
publishing the arrays; they are retired together with the arrays in `retire-instruction-prose`.

## Impact

**Modules and files**

| Area | File |
|---|---|
| `:core` (new) | `Manoeuvre` type + structure-based label builder next to `core/TurnInstructionLocalizer.kt` |
| `:core` | strings for the step rows (`nav_*` set is reused; `strings.xml` additions only if a kind needs one) |
| `app/ui/route` | `RoutePanelViewModel.kt` (`buildSteps`, `parseStepDisplay`, `parseTurnType`), `RouteStepValues.kt`, `RouteStepAnchors.kt`, `RouteStepSegments.kt`, `RouteGeometry.kt` (diagnostics line), `RoutePanel.kt`, `RouteSummary.kt` |
| `app/navigation` | `RoundaboutInstructionDiagnostics.kt` only if the census reuses its tag set — no behavioural change here |
| tests | `app/src/test/java/com/naviveylin/ui/route/*` (7 test classes), `core/src/test/.../ManeuverSymbolsTest.kt` where the glyph mapping moves |
| docs | `guidelines/UI.md` §7a/§8 (route panel content), `guidelines/Build.md` §10 (census recipe next to §10j) |

**Previous specifications changed**: `route-analysis` (2 requirements modified, 1 added), `route-panel-ui`
(1 modified), `route-summary-dialog` (1 modified), `osmscout-jni` (1 added), `turn-instruction-localization`
(1 added).

**Guidelines read/applied**: `guidelines/UI.md` §7a "Route panel", §8 "Route analysis", §10 "Text";
`guidelines/Design.md` §3 (state), §4 (threading), §9 "Data, persistence & search" for the route result
shape; `guidelines/Logging.md` §2 (the census line's shape) and `guidelines/Regulatory.md` §9 (no
coordinates in any diagnostics line).

**Native/JNI**: none. Submodule patch: none, local override: none — this change reads a projection that
already ships.

**Rollback**: revert the app-side read path; `RouteEntry.descriptions` keeps being published unchanged until
`retire-instruction-prose`, so the previous list can be restored by reverting this change alone.

**Additive or breaking**: additive for the submodule and for `RouteEntry`'s shape; **user-visible change** for
the route list (it gains roundabout and motorway rows, loses noise rows, and its text becomes localized).
