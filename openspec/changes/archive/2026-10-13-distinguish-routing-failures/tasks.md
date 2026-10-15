# Tasks

## 1. Router: the failure reason (native, submodule patch)

- [x] 1.1 Add `RoutingError` (`None`, `NoStartNode`, `NoTargetNode`, `NoRouteFound`, `DataFailure`,
      `Aborted`) with a getter/setter on `RoutingResult`, in
      `libosmscout/include/osmscout/routing/AbstractRoutingService.h`. Keep `Success()` exactly as it is
      (`!route.IsEmpty()`); document the upstream-able intent where the file's own TODO asks for it.
      Parent: `osmscout-jni` — *A failed routing call reports a reason*. Verify: the native library
      compiles for all three ABIs (task 7.1).
- [x] 1.2 Set the reason at each return site of `AbstractRoutingService::CalculateRoute`
      (`:1031` `NoTargetNode`, `:1042` `NoStartNode`, `:1149` and `:1164` `DataFailure`, `:1306`
      `NoRouteFound`, `:1338` `DataFailure`, breaker paths `Aborted`), in
      `libosmscout/src/osmscout/routing/AbstractRoutingService.cpp`. Parent: `osmscout-jni` — *A failed
      routing call reports a reason*. Verify: the six sites are each reached by the reason that names them;
      re-read the diff against the line list above (a native unit test is not available — `OSMSCOUT_BUILD_TESTS`
      is `OFF`, `app/src/main/cpp/CMakeLists.txt:96`).
- [x] 1.3 Add `RouteEndpoint { Start, Target, Unspecified }` beside `RoutingError` in
      `AbstractRoutingService.h`, and implement the endpoint-attachability predicate as a `const` member
      of `SimpleRoutingService` that applies the gates and the index ranges of `GetWayTargetNodes` /
      `GetWayStartNodes` (`:182-239`) **for the end it is given**: a way can carry a position at a
      coordinate when a route node is reachable from the attach index in a direction `CanUseForward` /
      `CanUseBackward` permits *for that end*. The four search functions are not `const` (they load route
      nodes through the non-`const` virtual `GetRouteNode`), so the predicate reads the route node
      through `RoutingDatabase::GetRouteNode` and names the four functions as the source of its ranges —
      implementable, but the ranges must be kept in step by hand. Parent: `osmscout-jni` — *An endpoint
      beside a direction-restricted road is still attached*. Verify: the predicate reproduces all four
      ranges and gates (forward/backward × start/target), and reads the attach index first as both
      `GetWayStartNodes` and `GetWayTargetNodes` do.
- [x] 1.4 Add a **new overload** `GetClosestRoutableNode(coord, profile, radius, RouteEndpoint)` on
      `SimpleRoutingService` and the matching `GetClosestRoutableNode(coord, radius, RouteEndpoint)` on
      `MultiDBRoutingService`; each accepts a candidate way only when the predicate holds for that end, so
      a way that cannot carry the position is skipped in favour of the next one within the same radius. The
      existing signatures keep their behaviour and delegate with `RouteEndpoint::Unspecified`, and the
      endpoint-aware selection replaces the old loop in place rather than being a second copy of it (the
      per-way closest segment is found first, the predicate decides, and only an accepted way that improves
      on the best distance updates the result). Pass `Start` / `Target` from `ResolveRoutePosition` in
      `OSMScoutClient.cpp` at all four call sites. Parent: `osmscout-jni` — *An endpoint beside a
      direction-restricted road is still attached*. Verify: the device case in group 6, plus the
      revert-check in group 8; compilation of the four touched translation units is green for
      arm64-v8a.

## 2. Bridge: the reason crosses JNI (submodule patch + one override)

- [x] 2.1 Add `default void onErrorDetail(int kind, String detail) { onError(detail); }` to the submodule's
      `libosmscout-client-java/java/com/framstag/libosmscout/client/RouteCallback.java`, with the
      `KIND_*` constants that name the cause, and resolve it in `GetRouteCallbackMethods`
      (`OSMScoutClient.cpp:6019`). Parent: `osmscout-jni` — *A failed routing call reports a reason*.
      Verify: `:osmscout-client-java:compileJava` and `:jar` build the interface (green).
- [x] 2.2 Map `RoutingError` plus the classification numbers to the callback kind in **one** helper shared
      by `calculateRouteWithObjectsWithProfile` and `calculateRouteWithObjectsAsync`, including the
      cost-limit-versus-disconnected split and the thrown-fault case. The mapping is a pure header
      (`route_failure_kind.h`, dependency-free like `routing_progress_throttle.h`), one `CallOnRouteError`
      helper carries kind + message to the callback with `onError(String)` as the fallback, and both entries
      set an `errorKind` beside their `errorMsg` at all five failure sites. **The cost-limit split was built,
      measured wrong, and removed.** `RoutingResult::GetCostLimitIgnoredCount()` counted the extensions
      `WalkPaths` discarded at the cost limit, on the theory that a non-zero count proves "this route is
      longer than the profile allows" — the device disproved it: a 416-point sweep of Dortmund's old town
      produced 24 failures that had all hit the cost limit, on trips of at most 1.2 km air-line distance,
      where the limit is not the user's problem (the endpoints' parts of the network do not join). The kind,
      the counter and the wording are gone; both causes report `NO_ROUTE_FOUND`. Parent: `osmscout-jni` — *A
      failed routing call reports a reason*;
      `routing-failure-reporting` — *A reason that concerns one end of the route identifies that end*.
      Verify: arm64-v8a build green; all 12 call sites and 10 failure sites are wired (no direct
      `cbMethods.onError` dispatch remains); the pure mapping is **not** runnable here — a locally compiled
      binary is refused by the shell allowlist, and the submodule's own `Tests/` suite is excluded from this
      build (`OSMSCOUT_BUILD_TESTS OFF`, `app/src/main/cpp/CMakeLists.txt:96`), so its end-to-end proof is
      the device task 6.3, and a `Tests/src/RouteFailureKindTest.cpp` in the libosmscout repo (where
      `RoutingProgressThrottleTest.cpp` runs) is the matching host test to add there.
- [x] 2.3 Confirm the local override needs no change, and record why. The `.java` override
      `osmscout-client-java/src/main/java/com/framstag/libosmscout/client/OSMScoutClient.java` only declares
      and forwards `RouteCallback` (lines 468-562, no `onError` handling, no implementation of the
      interface), so the kind rides the callback object the caller supplies and the override has nothing to
      carry. Patched in one place, never both — the change stays on the submodule's interface, which the
      `:osmscout-client-java` build compiles. Parent: `osmscout-jni` — *A failed routing call reports a
      reason*. Verify: `grep -n "RouteCallback|onError"` over the override shows declaration and forwarding
      only.

## 3. App: typed failure and per-reason wording (`:core`, `:app`)

- [x] 3.1 Add the `RouteError` value in `:core` (reason, affected endpoint, nearest-road distance when
      known, search radius) and publish it on `NavigationState` in place of the bare `String`; add one
      string resource per reason in `:core/src/main/res/values/strings.xml` and `values-de/strings.xml`,
      plus the unspecified-failure fallback for an unknown reason. Parent: `routing-failure-reporting` —
      *A failed route calculation reports a reason*, *Each reason is worded by the surface*;
      `i18n-l10n` — *A failure from native code is worded by the surface*. Verify: the resource set is
      complete in both locales (the existing `i18n-l10n` completeness check); the superseded field is
      retired by 3.5, which is why `errorMessage` still exists here.
- [x] 3.2 Publish `RouteError` from `RoutePanelViewModel` (`:553-561`) in place of the `String`, render the
      reason's resource in `RoutePanel.kt` (`:664-678`), and offer the reason's action (the map manager for
      unusable routing data; nothing for a reason no action fixes). Parent: `route-panel-ui` — *Calculate
      route* (the failure scenarios). Verify: the UI cases in group 4.
- [x] 3.3 Publish `RouteError` from `NavigationEngine` (`:540-554`, `:566-580`) and delete the hardcoded
      English literals at `:453` and `:547`/`:575` in favour of resources. The **phone carrier** for an
      engine-raised failure is part of this task (design D5): a snackbar fed from the shared state, gated on
      the failure applying to `SurfaceOrigin.PHONE`, disjoint from the panel's own snackbar by construction.
      Parent: `i18n-l10n` — *A failure from native code is worded by the surface*;
      `routing-failure-reporting` — *Each reason is worded by the surface*; `navigation-engine` — *Errors
      carry the surface that caused them* (its new phone scenario). Verify: `grep` finds no user-facing
      literal at those sites; the wording cases in group 4.
- [x] 3.4 Extend the `endCalculation` diagnostics entry (`NavigationEngine.kt:687-697`) with the reason and
      the classification numbers, keeping it coordinate-free. The "how far the search reached" number is the
      routing layer's own progress percentage, which the engine already receives and stores on the live
      calculation — no new native value is needed for it. Parent: `route-calculation-feedback` — *Route
      calculation is measured without coordinates*. Verify: the diagnostics cases in group 4 and the
      coordinate gate (`checkNoCoordinatesInLogs`).
- [x] 3.5 Retire the rendered-string field once nothing reads it. `NavigationState.errorMessage`,
      `errorOrigin` and `errorAppliesTo` are kept while the producers and consumers move to
      `NavigationFailure` (so every step compiles); they are deleted only when no main or test source
      reads them, leaving the typed failure as the single carrier (design D4). Parent:
      `routing-failure-reporting` — *A failed route calculation reports a reason*; `i18n-l10n` — *A failure
      from native code is worded by the surface*. Verify: `grep -rn "errorMessage|errorOrigin|errorAppliesTo"`
      over `app/src auto/src core/src` returns nothing; the both-flavor gate is green.

## 4. Tests: the app-side contract

- [x] 4.1 Add the reason (and its numbers) to `FakeOSMScoutClient`, then write the phone wording cases in
      `app/src/test/java/com/naviveylin/ui/route/RoutingFailureWordingTest.kt`, naming by scenario:
      *A failure reports which cause applies* and *The two unattachable ends are told apart*
      (`everyReasonResolvesToItsOwnResource`, `theStartAndTheTargetReasonsAreDifferentResources`);
      *A cancellation is not a failure*; *Phone message in the device language*
      (`aFailureOnAGermanDeviceShowsTheGermanResource`); *Wording that names the place is still a
      resource* (`aMessageNamingARoadIsAResourceWithASubstitutedValue`); *Phone offers the map manager*
      (`unusableRoutingDataOffersTheMapManager`); *A reason with no action offers none*
      (`noRouteFoundOffersNoAction`); *The failing end is identified* and *The distance to the nearest
      usable road is carried when known* (`theDestinationReasonNamesTheDestination`,
      `theFailureCarriesTheNearestRoadDistance`); *An unknown failure identifier still has wording*
      (`anUnknownReasonShowsTheUnspecifiedFailureResource`); *A surface-specific wording keeps its own
      label* (`aSurfaceWordedFailureKeepsItsOwnWording`). Parent: `routing-failure-reporting`,
      `i18n-l10n` — *A failure from native code is worded by the surface*. Verify: the class is green.
      This case caught a real contradiction: `hasMapManagerAction()` offers the map manager for
      `NO_ROUTE_FOUND` (whose own wording names a missing map region), while R3's no-action scenario named
      exactly that reason. The scenario was the weaker choice and was corrected; the implementation was
      right.
- [x] 4.2 Write the panel cases in `app/src/test/java/com/naviveylin/ui/route/RoutePanelViewModelFailureReasonTest.kt`,
      naming by scenario: *Route calculation failure shows the reason's own message*
      (`aFailureShowsTheReasonResourceNotOneFixedMessage`, `theReasonAndNotTheMessageDecidesTheWording`);
      *Route calculation failure offers the reason's action* (`anActionableReasonOffersItsAction`);
      *Route calculation failure of an unattachable end names the end*
      (`anUnattachableDestinationNamesTheDestination`); plus `theNearestRoadDistanceReachesThePanel`,
      `aRefusedRequestNamesItsOwnReason` and `aClearedPanelCarriesNoFailure`. Keep the existing six
      scenarios of the *Calculate route* requirement green in their current cases. Parent:
      `route-panel-ui` — *Calculate route*. Verify: this class and the `RoutePanelViewModel*` suites are
      green.
- [x] 4.3 Write the engine and carrier cases in
      `app/src/test/java/com/naviveylin/navigation/NavigationEngineFailureReasonTest.kt`, naming by
      scenario: *Engine-wide error reaches the phone* (`anEngineRaisedErrorReachesThePhoneCarrier`);
      *Engine-wide error raised with no surface presenting navigation*
      (`anEngineErrorWithNoSurfaceIsStillObservableInState`); *The reason reaches the caller*
      (`anEngineRaisedErrorReachesThePhoneCarrier`); *An existing caller keeps its behaviour*
      (`aFailureStillNotifiesACallerThatReadsOnlyTheMessage`); *Cancellation carries no failure reason*
      (`aCancelledCalculationPublishesNoReason`); *A thrown native fault is reported as one reason*
      (`aThrownFaultBecomesTheEngineFailureReason`); plus `aFailedAttemptClearsTheFailureOnTheNextSuccess`.
      The class runs under Robolectric with the **default sandbox** — no `@Config(sdk = …)`, which
      `TODO.md` §128 records as forbidden here. The existing *Car deep-link failure does not appear on the
      phone* and *Engine-wide error reaches every surface* cases stay green. Parent: `navigation-engine` —
      *Errors carry the surface that caused them*; `osmscout-jni` — *A failed routing call reports a
      reason*. Verify: this class and the `NavigationEngine*` suites are green.
- [x] 4.4 Write the diagnostics cases in
      `app/src/test/java/com/naviveylin/navigation/NavigationEngineCalculationDiagnosticsTest.kt`, naming by
      scenario: *Entry on a successful calculation*, *Entry on a failed calculation*, *Entry on a cancelled
      calculation*, *No coordinates in the entry*, *The failing reason is identifiable from the entry
      alone* (`theReasonIsIdentifiableFromTheEntryAlone`), plus
      `aCalculationThatReportedNoProgressSaysSo` and `anUnknownFailureStillNamesSomething`. The line itself
      is **extracted** into a pure `internal fun routeCalculationDoneLine(…)` in
      `app/src/main/java/com/naviveylin/navigation/RouteCalculationDiagnostics.kt`, so the entry's shape is
      asserted without reading the diagnostics file — its reader is `internal` to `:core` and a polling
      test is a wall-clock wait, which `checkNoWallClockWaits` refuses (the lesson
      `ki_processing_failures.log` records for `add-map-update-check` task 3.5). Parent:
      `route-calculation-feedback` — *Route calculation is measured without coordinates*. Verify: the class
      is green and the build's coordinate gate passes.
- [x] 4.5 Write the car cases in `auto/src/test/java/com/naviveylin/auto/RoutingFailureNoticeTest.kt`,
      naming by scenario: *The car uses the same label for the same reason*
      (`theCarLabelEqualsThePhoneLabelForEveryReason`, `theDistanceIsWordedIdenticallyOnBothSurfaces`);
      *Car names the phone and launches no phone screen*
      (`unusableRoutingDataNamesThePhoneAndLaunchesNothing`); plus
      `aReasonWithNoActionStatesNoDestinationForIt` and `aFailureWithNoReasonLeavesTheNoticeWithTheCauseAlone`.
      The notice's text is built by a testable `internal fun failureNoticeText(context, failure,
      mapsOnPhoneMessage)` in `auto/src/main/java/com/naviveylin/auto/FailureNoticeText.kt`, so the parity
      property is asserted without standing up a host session; the car's half of "launches no phone
      screen" is structural (`:auto` has no map-manager destination) and is confirmed on the device in
      6.5. Keep the existing car notice cases green. Parent: `routing-failure-reporting` — *Each reason is
      worded by the surface*, *A reason whose cause the user can act on offers the action*. Verify: the
      `:auto` suite is green.
- [x] 4.6 The native-contract case that the stub allows — *A thrown native fault is reported as one
      reason* — is `NavigationEngineFailureReasonTest.aThrownFaultBecomesTheEngineFailureReason`, and the
      fault-isolation cases in `NavigationEngineFaultIsolationTest` assert the reason and the detail since
      4.7; both are green. No second copy was written for its own sake. The change's own record that the
      routing-reason and attachment scenarios are **not** covered by a JVM case is
      `design.md`'s "What no JVM case covers, named so it is not read as covered" bullet, which names the
      four `osmscout-jni` scenarios and the device-gated phone carrier. Parent: `osmscout-jni` — *A failed
      routing call reports a reason*. Verify: the named cases are green and the limit is written down.
- [x] 4.7 Update the existing suites that assert the retired rendered string. The suites read
      `errorMessage`/`errorOrigin`/`errorAppliesTo` (`NavigationEngine*Test`, `NavigationTripTest`,
      `NavigationNotification*Test`); each assertion became an assertion on `failure?.reason` (and its
      endpoint/distance where the case is about a failing end), which is what the spec asks the case to
      prove. Parent: `routing-failure-reporting` — *A failed route calculation reports a reason*;
      `navigation-engine` — *Errors carry the surface that caused them*. Verify: the module suites run green
      and no case asserts a sentence the surface renders —
      `:app:testMobileDebugUnitTest --tests "com.naviveylin.navigation.*" --tests "com.naviveylin.ui.route.*"`,
      `:auto:testDebugUnitTest`, `:core:testDebugUnitTest` all BUILD SUCCESSFUL.

## 5. Guidelines and backlog

- [x] 5.1 Add the parity-table row for the routing-failure message and its action to `guidelines/UI.md` §1
      (phone carrier, car carrier, the shared label, the phone-only map-manager action), and record there
      that the car states the action belongs to the phone. Parent: `routing-failure-reporting` — *A reason
      whose cause the user can act on offers the action*. Verify: the row names both surfaces and the
      constraint that forces the deviation — the structural check for this docs-only delta is that every
      identifier it names exists (`navigationFailureWordingRes`, `hasMapManagerAction`,
      `car_failure_maps_on_phone` all resolve).
- [x] 5.2 Record in `guidelines/Logging.md` that a failure originating in native code is published as an
      identifier and worded by the surface, and that the cause of a failed calculation is part of the
      coordinate-free diagnostics entry. Parent: `i18n-l10n` — *A failure from native code is worded by the
      surface*; `route-calculation-feedback` — *Route calculation is measured without coordinates*. Verify:
      the two statements sit in §3 beside the coordinate rule that owns them, not in `AGENTS.md` (facts
      only); the test-side note about how the line's shape is asserted was removed from the guideline and
      kept in the change's design.
- [x] 5.3 Record the bridge's error contract in `guidelines/Design.md` §5: the additive `onErrorDetail`
      seam, that `onError(String)` keeps its behaviour, and that the reason is extracted in one helper
      shared by both route entries. Parent: `osmscout-jni` — *A failed routing call reports a reason*.
      Verify: the section names the files on each side of the seam and the one-place-patch rule — and adds
      the router-side rule this change turned out to need (a router defect is fixed in the selection, not
      described to the user; the rule is parameterized by the route end because the two ends attach along
      opposite halves of a way).
- [x] 5.4 Add three `TODO.md` entries with the full metadata line, for the findings this change does not
      fix: §191 the raw-coordinate logging on the legacy bridge entry, and that no gate reads native code
      for coordinates; §192 the `HardcodedStringScanner` blind spot that lets an `errorMessage = "…"`
      literal through; §193 the log-only `avoidTolls` / `avoidUnpaved` flags. Parent: none — backlog
      maintenance (`AGENTS.md` "Backlog maintenance"). Verify: each entry carries
      `**id:** · **category:** · **class:** · **status:**` on the line under its heading and is listed in
      the cluster table at the top of `TODO.md` (`build-and-harness` — bug: §146 §192; `native-jni` — bug:
      §191; `native-jni` — improvement: … §193).

## 6. On-device verification (native behaviour and both surfaces)

- [x] 6.1 Build and install both flavors and run the existing device suites to establish that the
      attachment change did not regress a working route. Parent: `osmscout-jni` — *An endpoint beside a
      direction-restricted road is still attached* (scenario *An attachable nearest way is unchanged*).
      **Done, re-scoped — the original premise was wrong.** The installed dataset is not
      nordrhein-westfalen: `RouteInstructionPositionDeviceTest` runs, opens the database and then fails
      **every** candidate with *"No routable node near start position"* (Dortmund/Cologne/Bochum are outside
      the region), so those fixtures cannot pass on this device at all — a fixture bound to a dataset nobody
      has installed, recorded as `TODO.md` §196. The regression baseline is therefore
      `RouteEndpointAttachmentDeviceTest.aWorkingRouteStillRoutes` (Andorra la Vella → Pas de la Casa,
      green) plus the 722-point sweep below. **Installed per `provision-phone-emulator`:** app and test APKs
      by `adb install -r -t` (never `connectedAndroidTest`, which uninstalls and took this AVD's map data
      once), run by `am instrument`, `europe/andorra` (5.6 MB, the provider's smallest routable region)
      downloaded through the app's own map manager.
- [x] 6.2 Add and run `RouteEndpointAttachmentDeviceTest` on the same map set: a destination whose nearest
      routable way is one-way with its route node behind the attach point, and a start with the same shape.
      Parent: `osmscout-jni` — *An endpoint beside a direction-restricted road is still attached*
      (scenarios *Destination beside a one-way road*, *Start beside a direction-restricted road*).
      **Done as far as this dataset allows, with a measured limit.** The case asserts the defect **shape**
      instead of a fixed fixture — an endpoint reported unattachable while the router reports a usable road
      nearer than 1 km — and all eight named targets (Andorra la Vella and Escaldes block interiors,
      an avenue corridor, and the Sant Julià / La Massana / Ordino / Encamp / Canillo centres) **routed**,
      12–721 polyline points each. **The shape does not occur in this dataset**: a 722-point sweep of the
      dense one-way area on the *mutated* (pre-change) selection found 10 route failures and **0** defect
      shapes, and both builds produced byte-identical candidate results. So this case proves *no regression*
      on 730 points and does **not** demonstrate the fix — see 8.1. **Also swept on NRW**
      (`nordrhein-westfalen-27-20260820-0826` on the AAOS AVD `emulator-5556`): 416 points of Dortmund's old
      town on the mutated selection and on the restored one, and 1482 points of the wider centre —
      `defectShapes=0` in all four runs, i.e. **~2 600 points across three areas and none of them in the
      shape**. What those sweeps did produce is the taxonomy evidence recorded under 6.3.
- [x] 6.3 Run the failing-end scenario on device against a point with nothing attachable within the search
      radius. Parent: `osmscout-jni` — *An endpoint beside a direction-restricted road is still attached*
      (scenario *Nothing attachable within the search radius*); `routing-failure-reporting` — *A reason that
      concerns one end of the route identifies that end*. **Done at the instrumented level**: from Andorra
      la Vella to Barcelona — ~100 km outside the dataset, so no way of it is anywhere near the target — the
      failure reported `reason=TARGET_UNROUTABLE`, `endpoint=TARGET`, `origin=ENGINE`, and the native detail
      naming the destination. That is the *first* device evidence that the kinded failure reaches Java: the
      real `.so` called `onErrorDetail`, and the older suite, which implements only `onError`, still received
      its message (spec `osmscout-jni` — *An existing caller keeps its behaviour*). **Two halves are not
      measurable on this dataset**: the phone's *rendering* of the message (6.4) and the diagnostics entry's
      `reason`/`nearestRoadM` — the first needs a failing route driven from the UI, and the second is written
      by the engine's acquisition path, which a phone UI cannot start without a deep link or a car session. A
      UI-searchable failing destination does not exist here in any case: every point in Andorra is within a
      few kilometres of a road. **The taxonomy is confirmed on real data (Dortmund, NRW)** — the sweep of
      6.2 is what produced the evidence: the same 416-point grid gave 24 failures that the pre-change build
      reported as `ROUTE_TOO_LONG` ("the distance is longer than this vehicle profile allows" — false for a
      trip of at most 1.2 km) and that the restored build reports as `NO_ROUTE_FOUND`, whose wording names the
      map data on the way and whose cause offers the map manager. That is the owner's original symptom
      reproduced in data — a destination off the road, reported as one opaque sentence before this change —
      and shown to become a specific, actionable message. The same run found a **pre-existing startup race**
      (`TODO.md` §197): the first two calculations after `openDatabases` saw an empty database list and
      reported `ROUTING_DATA_UNAVAILABLE`, which a retry clears; the reason conflates "not loaded yet" with
      "could not open", so the wording was made neutral ("not available", not "missing") to be true in both.
- [ ] 6.4 Measure the phone surface: with the route panel open, a failed calculation shows the reason's
      message and, for unusable routing data, a reachable map-manager action; assert the message's bounds and
      the action's bounds from a UI dump (`device-check` skill) rather than by eye. Parent: `route-panel-ui` —
      *Calculate route*; `routing-failure-reporting` — *A reason whose cause the user can act on offers the
      action*. **BLOCKED on the available fixture, not on the code.** Reaching the panel's failure text needs
      an in-app calculation that fails, and reaching the *action* needs a reason the map manager can fix
      (`ROUTING_DATA_UNAVAILABLE`, `ROUTING_DATA_FAILURE` or `NO_ROUTE_FOUND`): the first does not occur in a
      dataset where every point is near a road, and the second requires deleting the installed region — which
      is what destroyed this AVD's map data once, so it is not done casually. Options: a dataset with a
      road-less interior (NRW, 783.7 MB, needs a deliberate download), or an owner-supplied failing
      destination. The panel's rendering itself is covered host-side by
      `RoutePanelViewModelFailureReasonTest`, and its wording by `RoutingFailureWordingTest`.
- [ ] 6.5 Measure the car surface on the AAOS AVD: the error notice shows the reason's label for the same
      reason the phone showed, states that the maps belong to the phone for the unusable-data reason, and
      the notice lifecycle is unchanged (one notice, update-in-place, removed when the calculation ends).
      Parent: `routing-failure-reporting` — *Each reason is worded by the surface*, *A reason whose cause
      the user can act on offers the action*; `route-calculation-feedback` — the notice-consistency
      requirement. **BLOCKED, with the attempt and its evidence recorded.** The AAOS AVD is up
      (`emulator-5556`, `automotive`, SDK 33, x86_64, with `nordrhein-westfalen-27-20260820-0826` installed) and
      my build is on it (app + test APK by `adb install -r -t`, permissions granted). Its car session **is**
      bound and running — `dumpsys activity services` shows
      `ServiceRecord{… com.framstag.naviveylin/com.naviveylin.NaviVeylinCarAppService}` and the process — and
      the AVD has a screen (`wm size` 1080x600; `screencap` yields a real 342 kB frame), so `adb` dumps work.
      What it does not give is a way to reach *our* session's surface: this is an `osdouble` automotive image
      whose visible window is Google Maps (`Ansicht mit Nordausrichtung`, `Meldung hinzufügen`) and whose
      `mCurrentFocus` is the cluster activity; the launcher's app grid never appeared in the dumps, so the
      notice cannot be driven into view from here. Two further limits: the part of the scenario that is
      specific to the car — the "maps belong to the phone" referral — needs a reason the map manager can fix,
      i.e. **no routing data**, while this AVD has NRW installed (producing it would mean deleting the region,
      the operation that already destroyed the phone AVD's data), and the notice's wording and referral are
      covered host-side by `RoutingFailureNoticeTest`'s five cases. Still needed: an AAOS AVD whose own screen
      shows the launcher (the `emulator` binary is not on this host's `PATH`) or the Automotive Distant Display
      Host.

## 7. Build and existing tests

- [x] 7.1 Build both flavors and the native library for all target ABIs
      (`./gradlew :app:assembleMobileDebug :app:assembleAutomotiveDebug`), confirming the submodule patch
      compiles for `arm64-v8a`, `armeabi-v7a` and `x86_64` and that no compilation warning is introduced.
      Parent: `osmscout-jni` (both requirements) — this is the task that proves the native patch is
      buildable at all. Verify: the build output is quoted, warning count included. **Done**:
      `BUILD SUCCESSFUL`, 0 errors, 0 warnings, and the four touched translation units
      (`AbstractRoutingService`, `MultiDBRoutingService`, `SimpleRoutingService`, `OSMScoutClient`) each
      rebuilt for **all three** ABIs this run (14 objects per ABI: arm64-v8a 07:15, armeabi-v7a 07:32,
      x86_64 07:33).
- [x] 7.2 Run the tests the change can affect through the module filters and quote the tallies:
      `:app` (`com.naviveylin.navigation.*`, `com.naviveylin.ui.route.*`), `:auto`, `:core`,
      `:osmscout-client-java`. Parent: every modified capability. Verify: the tallies are quoted; any
      exclusion is named. **Done**: `:app` navigation+route suites green; `:auto` 843 tests / 0 failures;
      `:core` 623 / 0; `:osmscout-client-java` 39 / 0; `buildSrc` 9 / 0. Exclusions: `buildSrc`'s scanner
      suites test the coordinate gate, which this change does not touch, and the native JNI suite is not
      runnable here (`OSMSCOUT_BUILD_TESTS` is `OFF`).
- [ ] 7.3 Run the full both-flavor gate once, forced, and quote the result:
      `./gradlew test -PforceTests --no-build-cache` (the documented "full gate"). Parent: every modified
      capability. **BLOCKED, not green — the failure is not this change's.** Two runs (31m37s, 32m37s) both
      fail, for two independent reasons recorded as `TODO.md` §194 and §195: the shipped `routing-max-speed`
      spec is unclassified in `tools/feature-list/specs.json` (pre-existing; the change's own capability is a
      delta and is not in that set at all), and **31 mobile / 38 automotive** cases across six Compose
      suites fail with `AppNotIdleException: Compose did not get idle after ~2.9 million attempts in 60
      SECONDS` — every one of them green in isolation on the same host (29 tests / 0 failures for four of
      them in 18 s). No assertion failure in the run is attributable to this change. Quoted tallies from
      the second run: `:app` mobile 2012 tests / 32 failures, automotive 1974 / 39, `:auto` 843 / 0, `:core`
      623 / 0, `:osmscout-client-java` 39 / 0, `buildSrc` 9 / 0. This task stays open until the gate is green
      on a tree where §194 is fixed and the Compose timeouts are gone — the change must not be archived
      with it unchecked.

## 8. Revert-checks (one per invariant this change introduces)

- [ ] 8.1 Attachment predicate (native, device): mutate the predicate in
      `SimpleRoutingService::GetClosestRoutableNode` to accept every way, re-run the device case of task
      6.2 — *Destination beside a one-way road* must fail again (the calculation fails, the reason is the
      target-node one, and `No route node found for target way` reappears in Logcat). Restore, then re-run
      green and quote both runs. Parent: `osmscout-jni` — *An endpoint beside a direction-restricted road is
      still attached*. **MUTATION APPLIED, CASE DID NOT GO RED — the check is not demonstrated.** The
      predicate was mutated to `if (true)` (accept every candidate), rebuilt (`SimpleRoutingService.cpp.o`
      x86_64 at 16:48, so the change really was in the binary) and installed: the device case stayed green
      and both builds produced byte-identical route lengths for all eight targets, and a 722-point sweep of
      the dense one-way area on the mutated build found **10 failures and 0 defect shapes**. The predicate
      was then restored (sha256 `a27d7af3…`, identical to the backup; 0 mutation markers) and the case re-ran
      green. **What that means**: the fix is a no-op on this dataset, so the device run proves *no
      regression* (730 points) and cannot prove the fix; the positive claim for the one-way shape rests on
      the code reading of the four direction-gated searches. A device falsification needs a fixture whose
      nearest routable way is direction-restricted with no route node on the permitted side — the owner's
      original failing destination, or NRW (783.7 MB). Recorded rather than claimed.
- [x] 8.2 Reason-to-resource mapping (`:app`, JVM): mutate the mapping so every reason resolves to the same
      resource, run the group-4 cases — `everyReasonResolvesToItsOwnResource` and
      `theStartAndTheTargetReasonsAreDifferentResources` must fail. Restore, then re-run forced green with
      `-PforceTests --no-build-cache`. Parent: `routing-failure-reporting` — *Each reason is worded by the
      surface*. **Done**: mutated `navigationFailureWordingRes`'s target branch to the start resource →
      `tests="11" failures="3"` (`everyReasonResolvesToItsOwnResource`,
      `theStartAndTheTargetReasonsAreDifferentResources`, `theDestinationReasonNamesTheDestination`);
      restored (sha256 `ad4f3947…`, identical to the backup) → forced green `tests="11" failures="0"`.
- [x] 8.3 Phone engine-error carrier gating (`:app`, JVM): mutate the carrier's gate to accept every origin,
      run `aCarOnlyFailureDoesNotReachThePhoneCarrier` — it must fail (a car-raised error must not appear on
      the phone, `navigation-engine` — *Car deep-link failure does not appear on the phone*). Restore, then
      re-run forced green. Parent: `navigation-engine` — *Errors carry the surface that caused them*.
      **Done**: the gate needed a home first, so it is now `internal fun phoneFailureOf(state)` in
      `NavigationFailureMapping.kt` (the screen calls it), with its own case; mutated `.takeIf { … }` away →
      `tests="7" failures="1"` on exactly that case; restored (sha256 `4e178eb1…`) → forced green
      `tests="7" failures="0"`.
- [x] 8.4 Cost-limit classification — **superseded by the device measurement; the invariant it checked no
      longer exists.** The case was written and falsified as planned (mutating the Kotlin mapping to collapse
      `KIND_ROUTE_TOO_LONG` into `KIND_NO_ROUTE_FOUND` → `tests="12" failures="1"`, restored sha256
      `4e178eb1…` → forced green `tests="12" failures="0"`). Then the device showed that the *reason itself*
      was wrong — 24 of 416 swept points across Dortmund's old town had discarded extensions at the cost
      limit on trips of at most 1.2 km — so the kind, the native counter behind it and its wording were
      removed, and the case was replaced by
      `RoutingFailureWordingTest.theRouterIsNotAskedToDistinguishATooLongRoute`, which asserts that the
      router's cost-limit stop reports `NO_ROUTE_FOUND` and that its wording does not blame the distance.
      Parent: `route-calculation-feedback` — *The failing reason is identifiable from the entry alone*.
      Verify: the replacement case is green; the device re-run of the same grid reports 24 ×
      `NO_ROUTE_FOUND` and no distance claim (task 6.2).
