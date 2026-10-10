# Tasks

## 1. Reproduce the defect on HEAD (no production change)

- [x] 1.1 In `app/src/test/java/com/naviveylin/ui/route/RoutePanelViewModelStepAnalysisTest.kt`, add
  `start navigation ends the analysed step with the session`: calculate a route in an open session,
  `analyseStep(1)`, `setNavigating(true)`, then assert `analysedStepIndex`, `analysedSegment`,
  `analysedSegmentRange` and `analysedAnchor` all null and `routeEntry`/`routeResultFlow` still set
  (spec: `route-analysis` — "Start Navigation ends the highlight"; `route-planning-session` — "Start
  navigation ends the session's analysis"). Verify: it fails on unmodified HEAD — recorded: the case FAILED in
  `tests="31" failures="3" errors="0" timestamp="2026-10-10T16:11:22.511Z"` (run
  `:app:testMobileDebugUnitTest --tests "com.naviveylin.ui.route.RoutePanelViewModelStepAnalysisTest"
  -PforceTests --no-build-cache`, `BUILD FAILED in 1m 33s`), XML copied into
  `evidence/red-on-head-RoutePanelViewModelStepAnalysisTest.xml`.
- [x] 1.2 Add `ending a navigation-time review ends the analysed step and keeps the route`: calculate,
  `setNavigating(true)`, `openSession()`, `analyseStep(1)`, `endSession()`, then assert all four analysis
  values null and the route still drawn (spec: `route-planning-session` — "Ending a review during
  navigation ends the analysis"; `route-analysis` — "Ending a review during navigation ends the
  highlight"). Verify: red on HEAD — the case FAILED in the same run and XML as 1.1.
- [x] 1.3 Add `a route adopted without a session carries no analysis`: `setNavigating(true)` with no session,
  `adoptRoute(routeEntry(), Vehicle.CAR)`, then assert `analysedStepIndex` and `analysedSegment` null while
  `routeEntry`, `routeSteps`, `stepAnchors` and `routeResultFlow` are the adopted route's (spec:
  `route-analysis` — "An adopted route without a session carries no analysis"). Add
  `a selection made during navigation leaves the navigation step alone`: after `setActiveStepIndex(2)`,
  `analyseStep(1)` must not change `activeStepIndex` (spec: `route-analysis` — "A selection made during
  navigation is unaffected"). Verify: the adoption case is red on HEAD (it FAILED in the same run and XML as
  1.1), while **this second case passes on HEAD**: HEAD has no session gate to violate, so it guards against
  an over-eager clear — a fix that ends a selection the session still owns — rather than reproducing the
  defect. Its red-on-HEAD claim as originally written was wrong; the case stays with this guard reading.

## 2. Make the analysis end with its session

- [x] 2.1 Extract the ending into one private helper (UI-state pair + `_analysedSegmentRange` +
  `_analysedAnchor`) and use it from `clearAnalysedStep()`, so there is a single place that ends an
  analysis. Verify: the existing cases `clearing the analysed step drops the segment`,
  `clearing the analysed step leaves the route drawn` and `the analysed anchor follows the analysed step`
  stay green — no behaviour change so far. `endAnalysis()` is private and `clearAnalysedStep()` delegates to it.
- [x] 2.2 Call the helper from `onNavigationStarted()`, replacing the range-only clear. Verify: 1.1 green,
  and `start navigation ends the session but keeps the route drawn` still green (the route is navigation's,
  not the session's).
- [x] 2.3 Call the helper from `endSession()` on both of its paths, so a review ended while navigation runs
  drops the analysis without clearing the route. Verify: 1.2 green, and
  `ending a review during navigation keeps the route` green.
- [x] 2.4 In `adoptRoute()`, write the analysis only while a session is open; publish `routeEntry`,
  `routeSteps`, `stepAnchors` and the geometry unconditionally. Verify: 1.3's first case green, and an
  adoption during an open review still analyses the new route's current step.
- [x] 2.5 Rewrite the existing cases the deltas re-name so they state the session they need: the adopted-reroute
  case is renamed `an adopted reroute during a review starts at the new route's first step with a leg`
  (`RoutePanelViewModelStepAnalysisTest.kt`) and opens a session first, and `start navigation ends the session
  but keeps the route drawn` (`RoutePanelViewModelSessionTest.kt`) analyses a step first and asserts the ended
  analysis beside the kept route. Verify: ran 2026-10-10
  `:app:testMobileDebugUnitTest --tests …StepAnalysisTest --tests …SessionTest --tests
  com.naviveylin.ui.map.MapCanvasViewModelStepFocusTest -PforceTests --no-build-cache` →
  `BUILD SUCCESSFUL in 3m 19s`, `RoutePanelViewModelStepAnalysisTest tests="31" failures="0"`,
  `RoutePanelViewModelSessionTest tests="27" failures="0"`, `MapCanvasViewModelStepFocusTest tests="8"
  failures="0"`; XMLs in `evidence/green-*`.
- [x] 2.6 Falsify the new invariant once (`revert-check`): mutate 2.2 by deleting the helper call in
  `onNavigationStarted()`, confirm `start navigation ends the analysed step with the session` fails,
  restore, then re-run green forced with `-PforceTests --no-build-cache`. Record the mutation, the failing
  case and the restored tally. This is the one revert-check the change owes: task 2.4's
  session-gated adoption is covered by 1.3 and needs no separate mutation. — ran 2026-10-10: with the call in
  `onNavigationStarted()` replaced by the range-only clear (marker comment `REVERT-CHECK MUTATION (task
  2.6)`), **both** cases the guard protects failed at the invariant's own assertion —
  `start navigation ends the analysed step with the session` and `start navigation ends the session but keeps
  the route drawn`, each `java.lang.AssertionError: expected null, but was:<1>`
  (`RoutePanelViewModelStepAnalysisTest tests="31" failures="1"`, `RoutePanelViewModelSessionTest tests="27"
  failures="1"`, `BUILD FAILED in 2m 36s`, XMLs `evidence/mutation-2.6-*`). Restored exactly (marker grep in
  the touched files: 0), re-ran forced `-PforceTests --no-build-cache` → `BUILD SUCCESSFUL in 1m 59s`, 28
  actionable tasks executed, fresh timestamps `2026-10-10T16:21:08.203Z` / `2026-10-10T16:21:08.389Z`,
  `failures="0"` in both (`evidence/green-forced-*`).

## 3. Record the rule where it is stated

- [x] 3.1 Add the lifetime sentence to `guidelines/UI.md` §7a ("Route planning holds the camera"): the
  analysis, its camera anchor and its highlighted segment live exactly as long as the session, and every
  exit — Start Navigation above all — ends them while the route stays drawn. Verify: the sentence is in that
  section beside the step-navigator paragraph, and it names no coordinate or log line (the `UI.md`
  "Keeping this document honest" check still holds).

## 4. On-device measurement (phone)

- [x] 4.1 On a provisioned phone emulator with a map database, plan a route over a road network, tap Start
  Navigation and read `adb logcat -s RouteHighlight`: after the tap no new `range=… bboxPx=[…]` box may be
  logged, and the screenshot's `pixel-check` verdict must be the no-highlight one while the route is still
  drawn. Record the verdict and, per spec `auto-diagnostics`, the fact that the line carries no coordinate.
  — ran 2026-10-10 on `emulator-5556` (Pixel_8 AVD, Andorra region data, mobile debug build, `adb install -r`,
  maps survived): route "Rotonda de Govern → Carrer Major", 3,1 km, GPS fix injected with `adb emu geo fix`.
  **Measured pair**: the frame with an analysed step (card MAX, step "2 / 5" analysed) →
  `highlight (day): bbox=[461, 593, 595, 723] px=213`, `canvas=1080x2400 band=[0,1320]`, **verdict: inside**;
  after tapping *Navigation starten* → `{"image":…,"highlight":null,"image_size":[1080,2400],"band_bottom":1320}`,
  and the map still holds the route (navigation overlay shows `Abseits der Straße`, `Verbleibende Zeit`,
  `Entfernung`, `Ankunft`; no route-panel card is left — the route's continued drawing also rests on the unit
  case `start navigation ends the session but keeps the route drawn`). **Absence test, not just a null read**: the
  `RouteHighlight` buffer was cleared before the tap, the overlay had logged three boxes
  (`range=0..13 bboxPx=[503,576,903,956]` at 18:31:28) in that same session, and after the tap **no** line
  appeared even across two map pans that each change the drawn box — the overlay logged nothing because it
  drew nothing. The line carries pixels and a range only, no coordinate. Caveat recorded: after the
  calculation the auto-analysed step resolved to **no** segment on this route (the highlight only appeared
  once a step row was tapped), so the analysed frame above was produced by that explicit selection.
- [ ] 4.2 With navigation running, open the session, analyse a step, then end the session: record that the
  box is logged and `pixel-check` reports the highlight inside the free map area while the session is open,
  and that it reports no highlight after the session ends, with the route still drawn. This is the
  observable-behaviour evidence for the added requirement's phone surface.
  — **blocked 2026-10-10, not measured**: with navigation active on `emulator-5556` the phone UI offers no
  way into the session. In navigation mode the left rail (Menü / Ort suchen / Favoriten) is gone — the dump
  shows only compass, speed, location options, zoom, the status bar and *Navigation beenden* — so
  `openRoutePanelWithStart` is unreachable from search; its other call site is the favourites sheet's
  route chip, and this AVD's `files/favorites.json` holds `groups: []`, so no chip exists. The read-only
  review during navigation therefore needs a favourite added first (long-press → details → *Zu Favoriten
  hinzufügen*) or a deep link; the remaining half of 4.2 was not driven and the case stays unproven on the
  device. Evidence that does stand for the same behaviour: task 1.2's
  `ending a navigation-time review ends the analysed step and keeps the route` (red on HEAD, green after the
  fix), the extended `start navigation ends the session but keeps the route drawn`, and the 4.1 frames —
  the end-analysed-while-navigating path shares the one helper this change routes both exits through.
- [x] 4.3 State the limit of the harness: a mid-drive **reroute** cannot be produced on a stationary
  emulator. Use the GPX replay path if it is available (`device-check`); otherwise record the reroute
  confirmation as unrunnable without a drive and name task 1.3's adoption case as the standing evidence.
  — recorded 2026-10-10: the GPX replay path exists only as a **unit-level** parser
  (`app/src/test/java/com/naviveylin/test/GpxParser.kt`); no instrumented replay harness drives device
  location, so the drive was simulated with injected fixes instead — six `adb emu geo fix` points stepping
  ~1 km off the route. The engine consumed them (`MapCanvasVM: GPS fix quality: GOOD`, repeated `renderMap`,
  `LocationService`/`FREEDRIVE` entries in `files/diagnostics/app.log` showing `road=none`), but **no reroute
  was confirmed**: `grep -c reroute files/diagnostics/app.log` → `0` and no `NavigationEngine` line in
  logcat. Injected jumps are not a coherent track, so the adoption path stays device-unproven; task 1.3's
  `a route adopted without a session carries no analysis` is the standing evidence, and the `RouteHighlight`
  count stayed `0` through the whole simulated excursion.

## 5. Integration

- [x] 5.1 Verify the build compiles: the `build-app` skill's assemble of `:app` (debug) ends without error.
  — ran 2026-10-10: `./gradlew test :app:assembleMobileDebug :app:assembleAutomotiveDebug -PforceTests
  --no-build-cache` → `BUILD SUCCESSFUL in 11m 38s`, both flavor APK tasks in the graph (the mobile debug APK
  also built and installed on the phone AVD before task 4.1).
- [x] 5.2 Verify existing tests still pass: the `run-tests` skill's `:app` and `:auto` unit suites are green,
  including `MapCanvasViewModelStepFocusTest.kt`, which reads `analysedSegmentRange`.
  — ran 2026-10-10 in the same forced both-flavor invocation: `221 actionable tasks: 37 executed, 184
  up-to-date`; per module from the XMLs, all fresh (16:34–16:45Z, after the 18:33 local start) and all zero
  failures — `:app` mobile `tests=1881 failures=0`, `:app` automotive `tests=1881 failures=0`, `:auto`
  `tests=821 failures=0`, `:core` `tests=532 failures=0`, `:osmscout-client-java` `tests=34 failures=0`;
  5149 cases total, no `FAILED` line in the log.

## Scenario to case

Every scenario of the two deltas and what enforces it. Phone-only change: `route-analysis` and
`route-planning-session` name the phone card and the phone map, so no car case exists for either — neither
field has a reader outside `app/`.

| delta scenario | exercised by |
|---|---|
| route-analysis · Segment highlighted | `analysing a step selects it and resolves its segment`, `analysing the first step resolves the leading segment` (existing) |
| route-analysis · Highlight distinguishable in dark presentation | `RouteSegmentHighlightOverlayTest` casing/fill cases (existing) |
| route-analysis · Highlight removed with the selection | `clearing the analysed step drops the segment`, `clearing the route drops the analysed step and the anchors` (existing) |
| route-analysis · Start Navigation ends the highlight | 1.1, plus 4.1 for the pixels |
| route-analysis · Ending a review during navigation ends the highlight | 1.2, plus 4.2 for the pixels |
| route-analysis · A calculation in an open session analyses its current step | `a route starts on its first step that carries values`, `a new route calculation starts at its first step with a leg` (existing) |
| route-analysis · An adopted route without a session carries no analysis | 1.3 |
| route-analysis · An adopted route during a review starts at the new route's current step | rewritten `an adopted reroute starts at its first step with a leg` (2.5) |
| route-analysis · A selection made during navigation is unaffected | 1.3's second case |
| route-planning-session · Start navigation ends the session | `start navigation ends the session but keeps the route drawn` (existing, extended in 2.5) |
| route-planning-session · Cancel clears the route | `Cancel clears the route` case, `clearing the route drops the analysed step and the anchors` (existing) |
| route-planning-session · The explicit End action is always reachable | existing `RoutePanel` End-action cases |
| route-planning-session · System back ends the session | existing back-gesture case |
| route-planning-session · No route outlives its session | existing session tests (existing) |
| route-planning-session · Ending a review during navigation keeps the route | `ending a review during navigation keeps the route` (existing) |
| route-planning-session · Start navigation ends the session's analysis | 1.1 |
| route-planning-session · Ending a review during navigation ends the analysis | 1.2 |

## Workflow follow-up

- Independent read-only review of the change before archive, per the fix-loop.
- Archive the change (`openspec archive fix-navigation-start-segment-highlight`) and verify the merged
  specs: `route-analysis`'s modified requirement carries its added scenarios and the added requirement,
  `route-planning-session`'s exit requirement carries the analysis bullet.
- `TODO.md` bookkeeping: file the divergence this change left alone — the min strip's navigator names step 1
  when nothing is analysed (`RoutePanel.kt:848-851`), which contradicts `route-analysis`'s "With no step
  analysed the indicator SHALL name no step" on every path where the selection is empty — and record the
  defect's own entry as fixed once archived.
