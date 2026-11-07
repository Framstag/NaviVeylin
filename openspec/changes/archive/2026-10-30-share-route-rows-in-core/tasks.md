# Tasks

## 1. Move the row policy into `:core`

- [x] 1.1 Prove the three duplications before deleting anything (design D3): diff
  `app/src/main/java/com/naviveylin/ui/route/RouteRowSymbols.kt#rowTurnType` against
  `core/src/main/java/com/naviveylin/core/ManoeuvreTurnTypes.kt#turnTypeFor(Manoeuvre)`, and
  `app/.../RouteRows.kt#isDataOnly(Manoeuvre)` against `core/.../RouteStepGuidance.kt#isDataOnly(RouteStep)`.
  Verify: the diff is empty for the first pair and only the parameter type differs for the second; paste both
  outputs into the apply report. If either differs, stop and re-scope (spec: `route-analysis` — Rows are derived
  from the route's steps).
- [x] 1.2 Create `core/src/main/java/com/naviveylin/core/RouteRows.kt` holding `RouteRow`, `routeRows(steps)`,
  `MIN_ROW_LEG_METERS`, `ROW_MERGE_WINDOW_METERS` (KDoc naming the shared native
  `osmscout::client::RouteStepBuilder::NextNextWindowMeters` origin and its relation to `HINT_WINDOW_METERS`),
  `readableNameChange`, `namesMatch`, `foldName`, and both `isDataOnly` overloads; carry the census rationale
  (`302/330`, `1631/1663` informational) with the policy. `RouteRow` carries
  `manoeuvre/distanceMeters/durationSeconds/absorbedSteps` and no `values` property (design D2). Verify:
  `./gradlew :core:compileDebugKotlin` succeeds and the moved KDoc still names `guidelines/Build.md` §10k
  (spec: `route-analysis` — Rows are derived from the route's steps).
- [x] 1.3 Re-point the existing policy cases: keep `app/src/test/java/com/naviveylin/ui/route/RouteRowDerivationTest.kt`
  in `:app` and import the policy from `:core`, so its ten cases stay the pin for the moved rule. Verify:
  `./gradlew :app:testMobileDebugUnitTest --tests "com.naviveylin.ui.route.RouteRowDerivationTest"` is green and
  reports all ten cases (spec: `route-analysis` — each of the ten shipped scenarios names one of these cases).
- [x] 1.4 Delete `app/.../RouteRowSymbols.kt` and the `:app` copy of `isDataOnly`, and point
  `RoutePanelViewModel.toDisplay` at `core.turnTypeFor`. Verify: `./gradlew :auto:compileDebugKotlin :app:compileMobileDebugKotlin`
  succeeds, `grep -rn "rowTurnType" app/src` returns nothing, and `:auto`'s `NavigationTemplateMapperTest` is green
  (spec: `auto/navigation-view` — A roundabout exit is identified by its ordinal, and the glyph cases that read the
  turn type).

## 2. Re-point the phone variant (behaviour unchanged)

- [x] 2.1 `RoutePanelViewModel.buildRows` calls `core.routeRows(steps)`; `RouteRow.toDisplay` builds its
  `RouteStepValues` from `distanceMeters`/`durationSeconds` instead of the deleted `values` property. Verify:
  `./gradlew :app:testMobileDebugUnitTest --tests "com.naviveylin.ui.route.*"` is green, including
  `RoutePanelLocalizedRowsTest` and `RouteSummaryRowsTest` with no expectation edited (spec: `route-analysis` —
  Rows are derived from the route's steps; the phone list's rows, order and leg values must not change).
- [x] 2.2 Remove the leftovers of the move: no `RouteStepValues` import remains in `:core`, no duplicated constant
  (`MIN_ROW_LEG_METERS`, merge window) is declared in `:app`. Verify: `grep -rn "MIN_ROW_LEG_METERS\|CLOSE_MANOEUVRE_WINDOW_METERS"
  app/src core/src` shows one declaration each, and `:core:compileDebugKotlin` still succeeds (spec:
  `route-analysis` — one policy, one home).

## 3. Derive the car rows

- [x] 3.1 `NavigationTemplateMapper.routeDescriptionRows` derives `routeRows(stepsFromCurrentStep(state))` and maps
  each row: `turnType = turnTypeFor(row.manoeuvre)`, title from `row.distanceMeters` + the row's target street,
  text from `ManoeuvreLabels.description`, `roundaboutExitNumber = row.manoeuvre.roundaboutExit`, row 0 current
  (design D4, D5, D6). Verify: `./gradlew :auto:testDebugUnitTest --tests "com.naviveylin.auto.RouteDescriptionScreenTest"`
  is green with the cases added in 3.2 (spec: `auto/navigation-view` — Route description screen).
- [x] 3.2 Add the four `:auto` cases in `RouteDescriptionScreenTest`: (a) "the car list has one row per derived
  decision" — a 330-step fixture whose informational steps dominate yields the derived row count, not 330;
  (b) "a row's distance is its own leg" — a two-row fixture whose second row states its leg, not a cumulative
  value; (c) "an informational step earns no car row" — an informational step's leg lands in the row above;
  (d) "the car list is the same row set as the phone's" — the rows equal `core.routeRows(steps)` for the same
  input; plus "the marked row is the next decision" for the current-step rule. Verify: each case runs and passes
  under `--tests "com.naviveylin.auto.RouteDescriptionScreenTest"`, and each is named by the scenario it enforces
  (spec: `auto/navigation-view` — the three new scenarios and "Current step highlighted"; spec: `route-analysis` —
  "Both surfaces list the same rows").
- [x] 3.3 Add the coordinate-free diagnostic the device check reads: a pure
  `routeDescriptionDiagnostic(rows, steps)` (e.g. `"route description rows=24 steps=330"`) logged as
  `Diag/SCREEN` once per template build, numbers only, per `guidelines/Logging.md` §2 and the
  `checkNoCoordinatesInLogs` gate. Verify: a unit case pins the string's shape, `./gradlew :app:checkNoCoordinatesInLogs`
  (or the mobile flavor's equivalent task) is green, and the line contains no coordinate word (spec:
  `auto/navigation-view` — Route description screen; the measurement obligation of design D9).
- [x] 3.4 Revert-check the new bound: mutate `routeDescriptionRows` back to a 1:1 `steps` mapping, re-run
  `--tests "com.naviveylin.auto.RouteDescriptionScreenTest"` forced (`-PforceTests --no-build-cache`) and record that
  case (a) fails; restore the derivation and re-run forced green. Verify: the red run's XML tally and the green
  re-run are both quoted in the apply report (spec: `auto/navigation-view` — "The list is the derived decisions,
  not the steps").

## 4. Specs-owning documents

- [x] 4.1 Update `guidelines/Build.md` §10k: the census now explains both lists — the phone panel and the car route
  description list the same rows — and the AAOS recipe reads the new `Diag/SCREEN route description rows=…` line
  next to `RouteDescriptionScreen`'s tap. Verify: the section's recipe text names the new line, and
  `tools/check-doc-routes.sh` (if it covers this row) stays green (spec: `route-analysis` — Rows are derived from
  the route's steps).
- [x] 4.2 Update `guidelines/UI.md` §1/§6 parity text: the car route description lists the phone's row set; only
  the presentation differs (host `Row`s, no duration column). Verify: `grep -n "route description" guidelines/UI.md`
  finds the statement, and no other section still claims a per-step list (spec: `auto/navigation-view` — Route
  description screen; spec `cross-variant-ui-parity` — same element, same content).
- [x] 4.3 Update `guidelines/Design.md` §1's shared-behaviour list if it enumerates what `:core` owns, so the row
  policy is named there like `Manoeuvre`/`ManeuverSymbols`. Verify: the sentence exists and `openspec validate
  share-route-rows-in-core --strict` stays green (spec: `route-analysis` — one policy, one home).

## 5. On-device verification (AAOS AVD)

- [x] 5.1 Run the census route on the AVD (`guidelines/Build.md` §10a recipe: automotive debug install, `geo fix`
  inside Andorra, `geo:` deep link to 42.65,1.55), tap the route-list action, and read
  `Diag/SCREEN: route description rows=24 steps=330` plus the `CarApp.H.Tem: Host received new template` delta after
  `NavigationScreen: Show route description`. Verify: both quoted lines and the delta in ms are recorded; the row
  count matches the census's 24 (spec: `auto/navigation-view` — "The list is the derived decisions, not the steps"
  and "Route description opened from navigation view").
- [x] 5.2 Capture the opened list and measure the pixels for the claimed property — rows carry distance + target
  street, the current row is the marked first row, and back returns to the navigation view (`pixel-check` skill on
  the screenshot; `input keyevent KEYCODE_BACK` then a fresh capture). Verify: the measurement verdict and the two
  captures are recorded with their numbers (spec: `auto/navigation-view` — "Current step highlighted", "List
  scrollable", "Back returns to navigation view"). State explicitly that the projection variant cannot be produced
  on this AVD and is not claimed.

## 6. Integration gate

- [x] 6.1 Run the gate for the touched modules and both flavors: `./gradlew :core:testDebugUnitTest
  :auto:testDebugUnitTest :app:testMobileDebugUnitTest :app:testAutomotiveDebugUnitTest` plus
  `:app:assembleAutomotiveDebug` (Kotlin only, no ABI or native change, so no `assembleRelease` pass). Verify: green
  with the suites' tallies quoted, and `git status` shows no leftover build script change (spec: `route-analysis` —
  the whole policy; spec: `auto/navigation-view` — the car list).
- [x] 6.2 Run `openspec validate share-route-rows-in-core --strict` and re-read this change's two deltas against the
  landed code, so no scenario is left unenforced. Verify: the validator is green and every scenario names a case or
  the device step that exercised it (spec: both deltas).

## Workflow follow-up

- Read `ki_processing_failures.log` before the first apply action; log any dead end with its timestamp.
- Independent read-only review of the diff against every delta scenario before archiving (`agent-issue-loop`).
- File the projection reproduction (phone AVD + Desktop Head Unit) as a tracker issue: the change reduces the
  payload, it does not prove the reported stall is gone.
