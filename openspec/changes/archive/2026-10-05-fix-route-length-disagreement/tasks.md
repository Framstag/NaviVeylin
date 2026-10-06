# Tasks

## 1. Measure the two numbers (blocking — everything below follows the verdict)

- [x] 1.1 Capture the long route's two figures together with a reference, and record them. Run the
      instrumented case `RouteInstructionPositionDeviceTest.perStepValuesAreTheStepsOwnLegs` on the AAOS AVD
      (`emulator-5556`, NRW database) for Dortmund Hbf -> Cologne Hbf (~70 km) and capture the existing
      coordinate-free `ROUTE` diagnostics line
      (`route analysis: steps=… withValues=… sumM=… totalM=… sumS=… totalS=… maxErrM=…`, built by
      `stepValuesSummary`) — it already carries both figures; `adb logcat -s Diag/ROUTE` if the test does not
      print it. Obtain the reference for the same coordinate pair from `osmscout`'s
      `Demos/src/Routing.cpp` (prints the description's cumulative distance per node) or from the GPX of a
      driven route. Verification: the task's record lists router total, description total, legs sum and
      reference total for that route, with the source of the reference named. If no device is attached, record
      the blocker and stop here — do not continue to group 2 on the numbers alone (design D5).
      (spec: `osmscout-jni` — One route length for a calculated route)
      **MEASURED 2026-10-05** (task 1.1) — device `emulator-5554` (`Pixel_8(AVD)`, SDK 37, x86_64, phone
      flavour `mobileDebug`; a phone AVD, not the `emulator-5556` the task named, and the same NRW database),
      case `RouteInstructionPositionDeviceTest.routeLengthsAreMeasuredForALongAndAShortRoute`, log tag
      `RouteDeviceTest`. The reference is the **published polyline's own length** (great-circle sum over
      `latitudes`/`longitudes`) instead of `Demos/src/Routing.cpp` or a GPX: the submodule's `Demos/` has no
      linked binary in `hostbuild/` and there is no host-side map database, so neither named reference could
      be produced — the polyline uses neither native total and answers the same question. Dortmund Hbf ->
      Cologne Hbf, 1663 vertices:

      | figure | value | over polyline |
      |---|---|---|
      | router total (`RouteEntry.distance`) | 72 771 m | **0.748** |
      | description total (sum of the per-step legs) | 97 416 m | 1.0014 |
      | drawn polyline | 97 283 m | 1.000 |

      The description's total is within 0.14 % of the geometry the app draws and steps through, while the
      router's figure sits 25 % below it. Caveat recorded for 1.3: the polyline and the description share the
      route's node walk, so their agreement is expected and shows consistency with what is drawn, not
      independently that the description is the true road distance.
- [x] 1.2 Repeat 1.1 for a short town route (~2 km; `TODO.md` §133's 847 ms sample is a suitable shape), and
      record the same four numbers. Verification: the record shows whether the disagreement's ratio grows or
      shrinks with route length, which is what separates a systematic inflation from a fixed offset.
      (spec: `osmscout-jni` — One route length for a calculated route)
      **MEASURED 2026-10-05** (task 1.2) — same device and case, three routable candidates in one run
      (`OK (1 test)`; the `Dortmund centre -> Dortmund north` pair is not routable on this map data and is
      logged as such):

      | route | vertices | router total | description total | polyline | router/poly | desc/poly | desc/router |
      |---|---|---|---|---|---|---|---|
      | Dortmund Hbf -> Cologne Hbf | 1663 | 72 771 m | 97 416 m | 97 283 m | 0.748 | 1.0014 | 1.339 |
      | Dortmund Hbf -> Bochum Hbf | 640 | 16 677 m | 21 011 m | 20 966 m | 0.795 | 1.0021 | 1.260 |
      | short hop within Dortmund | 91 | 824 m | 1 487 m | 1 493 m | 0.552 | 0.9964 | 1.805 |

      This settles the question 1.2 exists to ask: the description's total tracks the drawn polyline on
      **every** route to within 0.4 %, so the description is not inflated — the router's figure is what
      departs from the geometry, and its relative error **grows as the route gets shorter** (0.748 -> 0.795
      -> 0.552), which rules out a fixed absolute offset as well as a fixed ratio. The absolute deficits
      are 24.5 km / 4.3 km / 0.7 km. Fixture note: the app and its `files/maps` had to be restored first
      (see the session report); a Gradle connected-test run uninstalls the app and takes the installed map
      data with it.
- [x] 1.3 Decide (A) the router's `GetOverallDistance()` or (B) the description's terminal cumulative node
      distance from the measurements of 1.1/1.2, and record the verdict with its evidence in `design.md`
      (resolving D1) and, if the reference was unobtainable, the explicit deferral. Verification: `design.md`
      names the chosen source and quotes the measured numbers that decided it; `proposal.md`'s Decision
      matches. (specs: `osmscout-jni` — One route length for a calculated route)
      **DECIDED 2026-10-05: (B)** — the description's terminal cumulative node distance is the route's
      length. Evidence: on all three measured routes the description tracks the drawn polyline to within
      0.4 % (1.0014 / 1.0021 / 0.9964) while the router's figure drops to 0.748 / 0.795 / 0.552 of it, so the
      description describes the geometry the app draws, lists and highlights, and the router's
      `GetOverallDistance()` is the figure that disagrees with the route it produced. External check: the
      real road distance Dortmund Hbf -> Cologne Hbf via A1/A45 is ~95-100 km, matching the drawn 97.3 km,
      whereas 72.8 km is close to the ~66 km great-circle distance — implausible for a road route. Recorded
      in `design.md` D1. The router's under-count is a native defect of its own and is *not* fixed by this
      change (which publishes the description's figure); it should be filed rather than absorbed, since a
      third consumer reading `GetOverallDistance()` directly would inherit it.

## 2. Publish one length from the bridge

- [x] 2.1 Apply the chosen source (task 1.3) in the libosmscout submodule,
      `libosmscout-client-java/src/OSMScoutClient.cpp` — `:6136`/`:6518` (`totalDistance` ->
      `RouteEntry.distance`) versus `:6223`/`:6143` (`node.GetDistance()` ->
      `DistanceAndTimePostprocessor`) — so the route exposes one length and its per-step legs sum to it.
      Submodule patch on `naviveylin-local`, minimal and upstreamable; no local override (the
      `:osmscout-client-java` module overrides five files, none of them `RouteEntry`). Verification: the
      Android build compiles (`./gradlew :app:assembleMobileDebug -Pandroid.injected.build.abi=arm64-v8a`)
      and, re-running 1.1's capture, the two figures agree within rounding. Sequence this behind
      `fix-step-leg-distance-and-time`, which patches the same function; run
      `git ls-remote origin naviveylin-local` immediately before any push. (spec: `osmscout-jni` — One route
      length for a calculated route)
      **DONE 2026-10-05** — the submodule has **two** complete route-building paths, and both set the
      router's figure (`:6136` in `calculateRouteWithObjectsWithProfile`, `:6819` in
      `calculateRouteWithObjectsAsync`, each with its own `DescCallback` and its own `RouteEntry`
      construction). Both now derive the length from the description: a `lastNodeDistanceMeters` field per
      callback (the running maximum of `node.GetDistance()`, set in `BeforeNode`), then after the per-step
      alignment guard, `RouteEntry.distance` = the sum of the aligned per-step legs, else the description's
      last cumulative node distance, else the router's figure as the fallback for a route whose description
      produced nothing. The `GetOverallDistance()` assignment stays as that documented fallback, and the
      duration estimate derives from the corrected length. Submodule commit `96fb43a20` on
      `naviveylin-local` (not pushed). Verification: `./gradlew :app:assembleMobileDebug` (all three ABIs;
      `buildCMakeDebug[arm64-v8a]`, `[armeabi-v7a]`, `[x86_64]` all executed, BUILD SUCCESSFUL in 41s), then
      the measurement re-run on `emulator-5554` — `OK (1 test)` with
      `routerTotalM == descriptionTotalM` and `descriptionOverRouter = 1.0` on all three routes
      (97 416 / 21 011 / 1 487 m), each still within 0.4 % of the drawn polyline.
- [x] 2.2 Bump the submodule gitlink in the main repo and commit the submodule SHA, verifying the tree stays
      clean (`git -C app/src/main/cpp/libosmscout status --short` empty) and the gitlink points at the commit
      from 2.1. (spec: `osmscout-jni` — One route length for a calculated route)
      **DONE 2026-10-05** — gitlink now `160000 commit 96fb43a205f2b25eb5220c87ce63bd3071f44251`, identical to
      `git -C app/src/main/cpp/libosmscout rev-parse HEAD`; the submodule's `status --short` is empty; the
      main-repo commit `ff22dd4` stages exactly one path (`app/src/main/cpp/libosmscout`, 1 file changed) and
      nothing is left staged, so the ~107 uncommitted files of the peer in-flight changes were not swept in.
      The submodule commit is **not pushed** — the push is a separate decision (one session owns the submodule
      and its pushes, and `fix-step-leg-distance-and-time` shares this file).

## 3. One source in the app, read by all five consumers

- [x] 3.1 Add one pure accessor for a route's length next to `RouteStepValues`
      (`app/src/main/java/com/naviveylin/ui/route/`), reading the source chosen in 1.3, and unit-test it:
      a route whose legs sum to its total yields that length; a route built from the wrong source does not.
      Verification: new cases in `RouteStepValuesTest` green. (spec: `osmscout-jni` — One route length for a
      calculated route)
      **DONE 2026-10-05** — `routeLengthMeters(route): Double` in `RouteStepValues.kt`, `internal`, reusing
      `instructionValues`'s alignment guard: the sum of the aligned per-step legs when the route carries them
      (so the displayed total is literally what the step list adds up to), else `RouteEntry.distance` as the
      fallback for a route whose description produced no per-step values. Verification: `:app:testMobileDebugUnitTest
      --tests 'com.naviveylin.ui.route.*'` green (59 s, 171 tests in the package, 0 failures);
      `RouteStepValuesTest` 10/0/0 with three new cases, including one that pins the exact measured defect
      (legs 97 416 m vs a native total of 72 771 m — the legs win). The helper's `route()` fixture gained a
      `nativeDistance` parameter with a default, so the existing cases are unchanged.
- [x] 3.2 Route the phone consumers through 3.1: the planning card's statistic (`RoutePanel.kt:217-222`), the
      route summary (`RouteSummary.kt:74-75`), the summary dialog's `Route statistics displayed` statistic, and
      the routing-status distance progress denominator (`NavigationDetailsOverlay.kt`). Verification
      (one case per changed scenario): the summary component's shown total equals the sum of its listed steps
      (`routing-summary` — Distance and duration shown); the dialog's total equals its step list's sum
      (`route-summary-dialog` — Distance shown); the distance progress line reaches full at the destination
      rather than at the router's shorter total (`navigation-status-details` — Distance line reflects
      traveled distance); the card's statistic equals the sum of its own step list
      (`route-planning-session` — Headline agrees with the card's step list); the statistic follows a
      newly calculated route instead of keeping the previous one
      (`route-planning-session` — The statistic follows the route that is shown). (specs: `routing-summary`,
      `route-summary-dialog`, `navigation-status-details`, `route-planning-session`)
      **PART DONE 2026-10-05, with a finding that blocks the rest** ℹ: the card statistic (`RoutePanel.kt`) and
      the summary — which is also the summary dialog, both call sites of `RouteSummary` — now read
      `routeLengthMeters`. The progress denominator named here does **not** read `RouteEntry.distance` at all:
      it is `navState.totalDistance`, which `NavigationEngine.startInternal` computes for itself as
      `computeRouteDistance(routeEntry.latitudes, routeEntry.longitudes)` — an app-side great-circle sum of the
      polyline, i.e. a *third* figure. That is the same seam task 3.3 needs, since `carTripFor` also derives
      from `NavigationState`. Its two device numbers are already within 0.14 % of the description's total
      (97 283 m polyline vs 97 416 m description), so routing it through 3.1 means changing the engine's own
      derivation, not a consumer's read — raised with the owner rather than absorbed here. The diagnostics line
      in `RoutePanelViewModel.logStepValues` is deliberately left on `route.distance`: it exists to compare the
      two native figures, and computing it from the legs would make the comparison vacuous (4.1 then turns
      `stepValuesDiverge` into the regression guard for exactly that).
      **DONE 2026-10-05** (the progress denominator via the engine, per the owner's decision, not via
      `NavigationDetailsOverlay.kt`): `RoutePanel.kt` and `RouteSummary.kt` read `routeLengthMeters`;
      `NavigationEngine.startInternal` takes `totalDistance` from the new companion helper
      `NavigationEngine.routeTotalDistanceMeters(route, lats, lons)` — the accessor when the geometry is
      usable, the polyline sum when the route carries no length, and the documented 0 for a geometry-less
      route even when the bridge handed a length (that rule is pinned by
      `NavigationEngineTest.geometryLessRouteStillNavigates`, which the helper preserves). Cases: the summary's
      shown total and the card's statistic both come from the accessor's cases in `RouteStepValuesTest`
      (10/0/0), the denominator's source from three new `routeTotalDistance_*` cases in `NavigationEngineTest`
      (16/0/0), and the line reaching full at the destination from the existing `RouteProgressTest`
      (`total=100, remaining=0` → 100) — i.e. the changed aspect of the `navigation-status-details` scenario is
      the denominator, and that is what the new cases pin. Focused runs: `com.naviveylin.navigation.*` +
      `com.naviveylin.ui.route.*` → BUILD SUCCESSFUL, 285 tests then 0 failures on the re-run (see 3.3's note on
      the one flake seen).
- [x] 3.3 Route the car trip mapper through 3.1 (`auto/src/main/java/com/naviveylin/auto/`) and add the car's
      case, so the requirement that names both surfaces has one case per surface. Verification: an `:auto`
      case asserting the trip distance is the route's length from 3.1, plus the phone case from 3.2.
      (spec: `osmscout-jni` — One route length for a calculated route)
      **PREMISE DOES NOT HOLD — raised with the owner** ✗: `:auto` never reads a route total. `grep -rn
      'totalDistance' auto/src/main/java/` returns **nothing**: `NavigationScreen`/`NavigationTemplateMapper`
      display `state.remainingDistance` (the native arrival estimate) through `distanceForDisplay`, and the trip
      carries per-step estimates (`Trip.Builder().addStep`). There is no car-side route-total consumer to route
      through 3.1, and no requirement in this change names the car surface (the `osmscout-jni` requirement is
      bridge-level and the `route-planning-session`/`navigation-status-details` ones are the phone card and
      status card). What the car does inherit is the *seed* of `remainingDistance`, which `startInternal`
      initialises to `totalDistance` — the engine-level fact the new `routeTotalDistance_*` cases already pin.
      **CLOSED NARROWED 2026-10-05 (owner decision)** ✅: the car surface has no route-total consumer, so
      nothing in `:auto` is routed through 3.1 and no `:auto` case is added — there is no seam to assert. The
      narrow closure is the engine fact that `remainingDistance` (what the car displays) is seeded from the
      route's own length, pinned by `NavigationEngineTest.routeTotalDistance_*` (16/0/0) and by
      `NavigationEngineTest`'s geometry-less rule. Recorded as a deliberate narrowing of this task, not as a
      claim that the car was verified end to end.
      **Recorded flake (not caused by this change)** ℹ: the first combined focused run (285 tests) failed
      `NavigationEngineRerouteTest.instructionListUpdatesAfterAReroute` with `java.lang.AssertionError: State
      condition not met within 5s` at `NavigationEngineRerouteTest.kt:77` — verbatim the victim and message
      `TODO.md` §121 records for this family (its `awaitState` reads wall-clock time). Isolation evidence: the
      class alone → `tests="8" skipped="0" failures="0" errors="0" time="9.85" timestamp="2026-10-05T19:29:01.702Z"`,
      with that case taking 8.318 s of its 5 s budget; the same combined selection re-run → BUILD SUCCESSFUL,
      46 s, 0 failures. Recorded per §40.38 rather than retried silently; §121's fix candidate (drive the case
      off an injected clock) belongs to §121, not here.
- [x] 3.4 Give every new invariant in 3.1-3.3 its revert-check: name the triple (invariant / one mutation /
      the case that must fail) before editing, apply the single mutation (e.g. make the accessor return the
      raw `RouteEntry.distance` again), require the named equality case to fail with the expected assertion,
      restore, grep the mutation marker away, then force green with `--rerun-tasks`. Follow the
      `revert-check` skill's evidence format and quote both runs. Verification: each revert-check's failing
      run and its forced green run are quoted in this change's tasks.
      (specs: `routing-summary`, `route-planning-session`)
      **DONE 2026-10-05 — three checks, one mutation each, marker `// REVERT-CHECK MUTATION <A|B|C>`:**

      1. **Invariant A** — `routeLengthMeters` prefers a route's aligned per-step legs sum over the native
         total (spec: `osmscout-jni` — One route length for a calculated route). **Mutation:** the accessor
         returns `route.distance` again. **Cases that must fail:** the two legs-win cases.
         * Fail: `RouteStepValuesTest` `10 tests completed, 2 failed`,
           `timestamp="2026-10-05T19:39:25.905Z"`, and exactly those two —
           `expected:<1500.0> but was:<21011.0>` and `expected:<97416.0> but was:<72771.0>` (the measured
           defect's own pair, reprinted by the regression case).
         * Restore: marker grep clean. * Green (forced): `tests="10" failures="0" errors="0"`,
           `timestamp="2026-10-05T19:39:51.711Z"`.
      2. **Invariant B** — the engine publishes the route's own length when its geometry is usable.
         **Mutation:** `routeTotalDistanceMeters` returns `computeRouteDistance(lats, lons)` unconditionally
         (the old derivation). **Case that must fail:**
         `routeTotalDistance_usesTheRouteOwnLengthNotASecondPolylineSum`.
         * Fail: `16 tests completed, 1 failed`, `timestamp="2026-10-05T19:40:09.143Z"`,
           `expected:<97416.0> but was:<72811.44545496882>` (the polyline sum of the same two points).
         * Green (forced): `tests="16" failures="0" errors="0"`, `timestamp="2026-10-05T19:40:40.143Z"`.
      3. **Invariant C** — a route without usable geometry keeps the documented total 0 even when the bridge
         handed over a length (spec: `navigation-engine` — Acquisition without usable polyline geometry).
         **Mutation:** the geometry guard is dropped. **Cases that must fail:** the new geometry-less case
         *and* the pre-existing spec guard — both protect this invariant, so both were required to fail.
         * Fail: `16 tests completed, 2 failed`, `timestamp="2026-10-05T19:41:08.413Z"` —
           `routeTotalDistance_withoutUsableGeometryStaysZero` and
           `routeWithoutGeometryStartsNavigationWithoutPublishingGeometry`, each
           `expected:<0.0> but was:<5000.0>`.
         * Green (forced, both classes): `NavigationEngineTest` `tests="16" failures="0" errors="0"`
           `timestamp="2026-10-05T19:41:42.491Z"` and `RouteStepValuesTest` `tests="10" failures="0" errors="0"`
           `timestamp="2026-10-05T19:41:42.239Z"`.

      Every failure was the *expected assertion* (an equality on the value under test), never a premise or a
      compile error, and no mutation left a marker behind (`grep -rn 'REVERT-CHECK MUTATION' app/src` clean
      before each green run). The green runs were forced with `-PforceTests --no-build-cache` so they measured
      execution rather than a cache hit.

## 4. Tighten the guards and record the contract

- [x] 4.1 Tighten `stepValuesDiverge` (`RouteStepValues.kt:74`) from `maxOf(100.0, total * 0.50)` to the
      rounding the per-step values justify, and update `RouteStepValuesTest` accordingly. Safe to tighten
      because the function only gates a warning in `RoutePanelViewModel.logStepValues` — confirm that by
      reading the call site before changing the bound. Verification: `RouteStepValuesTest` green with cases at
      the new bound both sides; the device measurement from 1.1/1.2 satisfies it.
      (spec: `osmscout-jni` — One route length for a calculated route)
      **DONE 2026-10-05** — bound is now `maxOf(10.0, totalMeters * 0.02)` (was 50 % / 100 m), and its KDoc
      records that the route's total and its legs are the same description figure since this change, so the
      predicate is now the *regression guard for a second source* rather than an acceptance band for a known
      defect. Call site confirmed unchanged in kind: it still only gates the `Log.w` in
      `RoutePanelViewModel.logStepValues`, so tightening cannot hide step values. Two new cases: “the measured
      defect's own pair counts as a divergence” (the 72 771 / 97 416 pair and the short route's 824 / 1 487
      pair, with assertion messages) and “an agreeing pair does not count as a divergence”. Verification:
      `RouteStepValuesTest` `tests="12" failures="0" errors="0"`, `timestamp="2026-10-05T19:43:05.184Z"`,
      forced. Its own revert-check (marker D): the bound mutated back to `maxOf(100.0, total * 0.50)` → fail
      `12 tests completed, 1 failed`, `timestamp="2026-10-05T19:42:46.280Z"`,
      `AssertionError: 72 771 m reported against 97 416 m of legs must count as a divergence` — exactly the case
      the tightened bound exists for, and proof that the old band accepted that defect; restore, marker grep
      clean, forced green as quoted.
- [x] 4.2 Tighten the device case's loose bound in
      `app/src/androidTest/java/com/naviveylin/route/RouteInstructionPositionDeviceTest.kt` (`ratio > 0.5` ->
      rounding) so the case measures agreement instead of tolerance. Verification: the case re-run on the
      AAOS AVD passes with the tightened bound and reports `tests="4"`, no skip.
      (spec: `osmscout-jni` — One route length for a calculated route)
      **DONE 2026-10-05** ✅ — the bound is now `abs(ratio - 1.0) < 0.02` with a message naming both numbers; the
      stale “order of magnitude” comment block was replaced (it justified the 0.5 bound with the pre-fix 1.34×
      pair and cited `TODO.md` §126, the car navigation-lease entry — the finding is §129/§139), and
      `kotlin.math.abs` was imported. Re-run after the AVD was restarted: the whole class through
      `adb shell am instrument -w -e class com.naviveylin.route.RouteInstructionPositionDeviceTest` (which,
      unlike `connectedAndroidTest`, does not uninstall the app and its installed map data) →
      **`OK (6 tests)`**, `Time: 47,457`, no skip — the class has 6 cases now, not the 4 the task text assumed
      (1.1/1.2's measurement case plus the pre-existing ones). The tightened bound on hardware:
      `per-step values: steps=12 sumM=97416 totalM=97416 ratio=1.0` — i.e. `abs(1.0 - 1.0) = 0` against a
      0.02 bound, where the old 0.5 bound had accepted the pre-fix 0.748. The measurement case's three routes
      each report `descriptionOverRouter=1.0`. `files/maps/north-rhine-westphalia` survived the AVD restart, so
      no fixture re-download was needed.
- [x] 4.3 Record in `guidelines/MapRendering.md` (native route-data section) which of the two native figures
      is the route's length and why, plus the `ROUTE` diagnostics line that measures it. Verification: the
      guideline names the chosen source and the line's field names; no `UI.md` change (no visual rule moved).
      (specs: `osmscout-jni`, `routing-summary`)
      **DONE 2026-10-05** — new section `guidelines/MapRendering.md` §19 “Route data — one length for one
      route”, placed before the Parameter Overview: the route's length is the description's own total (with
      the router's figures and their three measured ratios), both native paths are named, the single app-side
      accessor and the engine helper (including its geometry-less 0 rule) are named, “do not add a second
      derivation” is stated, and the `Diag/ROUTE` field names plus the tightened `stepValuesDiverge` band and
      the device case are recorded as the measurement. No `UI.md` change (no visual rule moved).
- [x] 4.4 Correct the stale cross-reference: `TODO.md` §126 is the car navigation-lease entry, this finding is
      **§129**. It sits in `RouteStepValues.kt`'s KDoc and the diagnostics KDoc in
      `RoutePanelViewModel.logStepValues`. Verification: `grep -rn '§126' app/src/main/java/com/naviveylin/`
      returns no route-length citation; the in-flight `fix-step-leg-distance-and-time` delta's own §126
      citation is handed to that change (it cannot be edited from here).
      (spec: `osmscout-jni` — One route length for a calculated route)
      **DONE 2026-10-05** — `grep -rn '§126\|TODO.md 126\|1\.34' app/src auto/src core/src` returns no
      route-length citation (only unrelated `distanceKm` matches inside the submodule's ground-truth JSON
      fixtures). The two citations this task named were rewritten rather than renumbered: `RouteStepValues.kt`'s
      `stepValuesDiverge` KDoc (now documents the post-change contract and §129/§139) and the device case's
      comment block (4.2). The `RoutePanelViewModel.logStepValues` KDoc turned out to carry no §126 reference.
      The in-flight `fix-step-leg-distance-and-time` delta's own citation still stands and is handed to that
      change, as the task says. A new backlog entry takes the native defect forward: `TODO.md` §139, with the
      three measured ratios, the three candidate routes and the fix candidate (added with the owner's
      approval; its `route-and-navigation` bug cluster index line was updated too).

## 5. Integration verification

- [x] 5.1 Build both flavors with all three ABIs (`./gradlew :app:assembleMobileDebug
      :app:assembleAutomotiveDebug`) and verify both succeed.
      **DONE 2026-10-05** — BUILD SUCCESSFUL in 25 s, with `Task :app:buildCMakeDebug[…](arm64-v8a,
      armeabi-v7a, x86_64)` present for all three ABIs and both APKs repackaged (`app-mobile-debug.apk`
      21:44:05, `app-automotive-debug.apk` 21:44:24).
- [x] 5.2 Run the full both-flavor gate once, forced (`-PforceTests --no-build-cache`), and verify all four
      modules' suites are green with the executed-task count and elapsed time quoted.
      **DONE 2026-10-05** — `./gradlew test -PforceTests --no-build-cache` BUILD SUCCESSFUL in **4m 58s**,
      `185 actionable tasks: 25 executed, 160 up-to-date`. Tallies read from the result XMLs, not from the
      console line: `:app` mobile **1691 / 0 failures / 0 errors**, `:app` automotive **1691 / 0 / 0**,
      `:core` **444 / 0 / 0**, `:auto` **781 / 0 / 0**, `:osmscout-client-java` **26 / 0 / 0**, all
      `skipped=0` — i.e. both flavors executed the same class set and every revert-check's restored state is
      green under the full gate. This run also re-covered the §121 flake (recorded in 3.3):
      `NavigationEngineRerouteTest` passed in both flavors here.
- [x] 5.3 Re-run the on-device measurement end to end — long intercity route and short town route, phone and
      car — and record the numbers that settle the requirement: the card statistic, the step list's sum, the
      progress denominator and the car trip distance all report one length within rounding.
      Verification: the recorded `ROUTE` lines and the UI-dump geometry per route, with the phone case and the
      car case named separately. (specs: `osmscout-jni`, `navigation-status-details`, `route-planning-session`)
      **DONE 2026-10-05 (second attempt)** ✅ — the drive that worked, on `emulator-5554` with
      `files/maps/north-rhine-westphalia` installed: app relaunched via
      `am start -n com.framstag.naviveylin/com.naviveylin.MainActivity`, location granted with
      `pm grant …ACCESS_FINE_LOCATION`, GPS fix injected with `adb emu geo fix` (lon lat) and confirmed by the
      app drawing its own street pill; destination sent as a `geo:` intent **with an explicit `-n` component**
      (otherwise the “Öffnen mit” dialog needs blind taps and can hand the intent to Google Maps), then
      candidate → `Route berechnen` → `Berechnen`. The first attempt failed because the geo candidate and the
      injected fix resolved to the **same point**; keeping them distinct is the whole trick.

      **The app's own coordinate-free diagnostics line is the measurement** (it is the `ROUTE` line the task
      asks for, emitted by `RoutePanelViewModel.logStepValues`), and the card's rendered statistic was read
      from the same UI dump:

      | case | `Diag/ROUTE` line | card statistic (rendered) | step rows |
      |---|---|---|---|
      | short town route | `steps=7 withValues=6 sumM=1983 totalM=1983 sumS=212 totalS=142 maxErrM=0` | `2,0 km · 2 min` | `100 m` / `150 m` … |
      | long intercity route | `steps=9 withValues=8 sumM=95563 totalM=95563 sumS=3719 totalS=6880 maxErrM=0` | `95,6 km · 1h 54min` | `100 m` / `100 m` … |

      So **the card statistic and the step list's sum are the same number, `maxErrM=0`, on both a 2 km and a
      95.6 km route** — the rendered string follows the legs (2,0 km of 1 983 m; 95,6 km of 95 563 m), which is
      exactly what the phone case of this task asks for. The UI-dump geometry also confirmed the card's own
      layout (statistic at `y≈1405`, step rows below it).

      **Progress denominator:** the routing-status progress lines exist only during navigation and are ~2 px
      tall with no text, so no dump can read them. Its source is `NavigationState.totalDistance` =
      `NavigationEngine.routeTotalDistanceMeters` — pinned by the `routeTotalDistance_*` cases (16/0/0, revert-
      checked as B and C) and by `RouteProgressTest` reaching 100 at `remaining=0`, i.e. the line reaches full
      at the destination rather than at the router's shorter figure.

      **Car case: named separately, and there is nothing to measure** — `:auto` reads no route total (task 3.3's
      finding: `grep -rn 'totalDistance' auto/src/main/java/` is empty; the car shows `state.remainingDistance`
      and per-step trip estimates). Recorded as a gap in the car surface, not as a verified number.

      **Reusable recipe** (worth carrying into `guidelines/Build.md` §10 — raised with the owner, not done
      here): for any UI-level route measurement on the phone AVD, `geo:` + explicit `-n` component + a
      destination **distinct from the injected fix** is the sequence that reaches a calculated route without
      blind dialog taps, and `adb logcat -s Diag/ROUTE` is the app-side measurement.
