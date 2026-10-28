# Tasks — route panel from structured steps

## 1. Foundation in `:core` (spec: `turn-instruction-localization` — Route analysis rows are localized from the step model)

- [x] 1.1 Add `Manoeuvre` in `:core` — kind, move, roundabout exit ordinal and circulation direction, motorway
  from/to, name and ref, way type, coord, leg distance and leg time, and the node identity — with a factory from
  a `RouteStep`. Verification: compiles; `ManoeuvreFromStepTest` "every step kind maps to its manoeuvre fields"
  and "an absent record stays absent" (no invented values for a plain turn).
- [x] 1.2 Add the structure-based label builder (short and composed text per kind, using the existing `nav_*`
  resources; a new resource only where a kind has no string). Verification: `ManoeuvreLabelTest` cases for all
  seven moves, roundabout enter/leave with ordinal, motorway enter/change/leave, name change, empty-street
  composition.
- [x] 1.3 Add the parity case between the new builder and `TurnInstructionLocalizer` over a shared fixture set.
  Verification: `ManoeuvreLabelTest` "the row builder and the live localizer agree on a turn" green.

## 2. Row derivation and the filter (spec: `route-analysis` — Rows are derived from the route's steps)

- [x] 2.1 Add `routeRows(steps): List<RouteRow>` — a pure function applying the keep set, the straight-on
  collapse, the name-change merge window, the name normalisation and the leg fold. Verification:
  `RouteRowDerivationTest` "roundabout exit and motorway entry become rows" and "consecutive straight-on turns
  collapse into one row".
- [x] 2.2 Name-change rules. Verification: `RouteRowDerivationTest` "a street-name change earns its own row",
  "a street-name change restated by the next manoeuvre is merged into it", "an empty or repeated name change is
  merged".
- [x] 2.3 Informational steps as data. Verification: `RouteRowDerivationTest` "an informative step is not a row
  and its leg moves to the row above".
- [x] 2.4 Tiling and totals. Verification: `RouteRowDerivationTest` "rows tile the polyline without a gap or an
  overlap", "row distances sum to the route length after merging", "merged legs are attributed to the surviving
  row".
- [x] 2.5 Keep the full step list in state and derive rows from it without mutating it. Verification:
  `RouteRowDerivationTest` "row derivation leaves the step list intact".
- [x] 2.6 Revert-check for the tiling invariant: mutate the boundary rule so a merged step keeps its own
  boundary — "rows tile the polyline without a gap or an overlap" must fail; restore; re-run
  `-PforceTests --no-build-cache` green.
- [x] 2.7 Revert-check for the sum invariant: mutate the fold so a merged step's leg is dropped —
  "row distances sum to the route length after merging" must fail; restore; forced green.
- [x] 2.8 Revert-check for the `informative` rule: let an informative step through as a row — "an informative
  step is not a row and its leg moves to the row above" must fail; restore; forced green.

## 3. Positions, segments and values (spec: `route-analysis` — Analysed step is locatable on the map; Step values describe the step's own leg)

- [x] 3.1 `RouteStepAnchors` publishes a row boundary per row (first row at the route's first vertex) instead of
  a per-step manoeuvre point. Verification: `RouteStepAnchorsTest` updated; "the first row anchors at the route
  start" and "a condensed row anchors at its own manoeuvre".
- [x] 3.2 `stepSegments` derives a range per row from consecutive boundaries and the polyline ends.
  Verification: `RouteStepSegmentsTest` "rows tile the polyline", "a condensed row's segment spans every absorbed
  step", "empty polyline yields no range".
- [x] 3.3 `RouteStepValues` reads the row's folded values instead of `instructionDistances`/`instructionTimes`,
  and `routeLengthMeters` sums rows. Verification: `RouteStepValuesTest` "a row's values are its own plus the
  merged legs", "a row without a leg shows no numbers", "rows sum to the route length".
- [x] 3.4 Divergence diagnostics: keep `stepValuesDiverge` on the row sum, coordinate-free. Verification:
  `RoutePanelViewModelStepAnalysisTest` "a diverging sum is logged without coordinates" (existing case kept
  green).

## 4. Panel, summary and icon (spec: `route-panel-ui` — Turn-by-turn instruction list; `turn-instruction-localization` — Route analysis rows are localized from the step model)

- [x] 4.1 `RoutePanelViewModel.buildSteps` reads `RouteEntry.steps`, derives rows and stores the localized text.
  Verification: `RoutePanelLocalizedRowsTest` "a German left turn row is localized", "a German route lists a
  German roundabout row", "roundabout and motorway manoeuvres appear in the list".
- [x] 4.2 Row icon from the manoeuvre kind and move, not from text. Verification: `RoutePanelLocalizedRowsTest`
  "the row icon follows the step kind" and "a motorway change row is not a plain turn".
- [x] 4.3 Delete `parseStepDisplay`, `parseTurnType`, the bracket/unit parser and `isInstructionLine`; no
  `RouteEntry.descriptions` read remains in `app/ui/route`. Verification: `grep -rn "descriptions\|parseTurnType\|parseStepDisplay\|isInstructionLine"`
  over `app/src/main` returns nothing; the route test suites compile and pass.
- [x] 4.4 Empty-step route: no rows, and a stated reason instead of invented rows. Verification:
  `RoutePanelStoppedStateTest` (extended) "a route without steps lists no rows".
- [x] 4.5 The car surfaces are untouched in this change — record that as a deliberate scope line in the change
  report, so an untested car path is not read as proven by the phone cases.
- [x] 4.6 Route summary dialog (`RouteSummary`) lists the same rows with their own leg distance (spec:
  `route-summary-dialog` — Scrollable step list). Verification: `RouteSummaryRowsTest` "rows are listed in route
  order with their own leg distance".

## 5. Census: measurement before policy (spec: `route-analysis` — Rows are derived from the route's steps)

- [x] 5.1 Emit the census line at route application (counts and metres by kind, leg quantiles, straight-on run
  lengths, name-change restatement counts, info-name-only count), coordinate-free, on `ROUTE_TAG`. Verification:
  `RoutePanelDiagnosticsTest` "the census line carries counts and metres and no coordinates"; the
  `checkNoCoordinatesInLogs` gate green.
- [x] 5.2 Record the census on device for one city route and one motorway route; write the numbers into the
  change's evidence. Verification: two logcat excerpts with their kind counts and metres, and the observation
  that roundabout/motorway counts are non-zero while today's list showed none.
- [x] 5.3 Settle the undecided policy from 5.2 — `informative`, `POIAtRoute`, `Via`, and `MinRowLegMeters` — by
  changing the single predicate the filter keeps for them. Verification: the chosen policy recorded with the
  number that justified it; affected cases added to `RouteRowDerivationTest`.
- [x] 5.4 Revert-check for the name-change merge window: set the window to 0 m — "a street-name change restated
  by the next manoeuvre is merged into it" must fail; restore; forced green.

## 6. The step-projection contract the app now depends on (spec: `osmscout-jni` — A calculated route publishes structured route steps)

- [x] 6.1 Add bridge-level cases for the five scenarios of that requirement — route order, roundabout ordinal and
  direction, motorway change distinguishable from a plain turn, per-step legs summing to the route length,
  cross-database step identity. Verification: the new cases fail if the projection stops publishing a field
  (mutate one field write in the bridge and observe the failure, then restore) — the projection is live but
  unproven from Java today.
- [x] 6.2 No bridge code changes in this change. Verification: `git diff` touches no file under
  `app/src/main/cpp/libosmscout`; the submodule pin is unchanged (`git submodule status`).

## 7. Build and suite verification

- [x] 7.1 Build both flavors (`assembleMobileDebug`, `assembleAutomotiveDebug`) without warnings.
- [x] 7.2 Run the affected unit suites focused first — `--tests "com.naviveylin.ui.route.*"`,
  `--tests "com.naviveylin.core.*"` — then the full both-flavor gate once before the change is complete. Name
  any exclusion in the change report.
- [x] 7.3 Device: calculate a city route and a motorway route, open the panel, scroll the list. Verification: the
  census excerpts from 5.2 plus one screenshot judged with the `pixel-check` skill (`guided/route-list` case) —
  the property is producible on a stationary emulator, so no "cannot be measured" note applies.

## 8. Guidelines

- [x] 8.1 Update `guidelines/UI.md` §7a/§8 to state that the route list is derived from the structured steps and
  what a row owns (range + folded legs).
- [x] 8.2 Add the census recipe to `guidelines/Build.md` §10, next to §10j, with the logcat filter.
- [x] 8.3 Record in `guidelines/Design.md` §13 (Provenance) that the list moved from parsed description text to
  the step model, and why.
