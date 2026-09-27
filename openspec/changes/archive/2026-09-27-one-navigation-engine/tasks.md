# Tasks

> Implemented 2026-09-27. Evidence: `./gradlew test` green — `:app` mobile 1255 /
> automotive 1255, `:auto` 697, `:core` 369, `:osmscout-client-java` 26 (0 failures);
> `:app:assembleMobileDebug` and `:app:assembleAutomotiveDebug` both write their APK;
> `openspec validate one-navigation-engine --strict` valid; no native/submodule change.
> Decision notes of the implementation are at the end of this file.

## 1. `:core` navigation state and seams

- [x] 1.1 `core/src/main/java/com/naviveylin/core/NavigationState.kt`: add `vehicle` (the profile the session runs with) and `errorOrigin` with a `SurfaceOrigin` enum (`ENGINE`, `PHONE`, `CAR`); keep every existing field and its meaning; document each new field and its default. Verify: `./gradlew :core:test` green and the class compiles for both flavors (spec: `navigation-engine` — One navigation state shared by all surfaces, Errors carry the surface that caused them; design D4/D5).
- [x] 1.2 `core/src/test/java/com/naviveylin/core/NavigationStateTest.kt`: a default state is not navigating and carries no origin; a state built with a vehicle profile and a `PHONE` origin round-trips through `copy`; origin filtering helper if one is introduced (a surface presents `ENGINE` and its own origin, not the other surface's). Verify: the new test class green, executed count quoted (spec: same requirement).
- [x] 1.3 `core/src/main/java/com/naviveylin/core/NavigationViewModel.kt`: keep the interface as the engine's contract; add the position-flow accessor the map surfaces use for follow mode (today `NavigationViewModel.positionFlow`) and document that the implementation is process-scoped. Verify: `:core:test` green; every implementer listed by a compile of `:app` (spec: `navigation-engine` — One navigation state shared by all surfaces; design D5/D7).

## 2. `:app` engine extraction

- [x] 2.1 Add `app/src/main/java/com/naviveylin/navigation/NavigationEngine.kt`: a `@Singleton` implementing `core.NavigationViewModel`, owning the native `NavigationController`, the `NavigationListener`, the reroute gate, one stale-speed ticker, the 2000 ms road-info throttle, the speed-spike filter and the state/position flows. Move the listener and the ticker/throttle/filter bodies from `app/src/main/java/com/naviveylin/navigation/NavigationViewModel.kt` (`processLocation`, `createListener` at `:387`) and take the merged policy constants from the phone file (`MAX_REROUTE_ACCURACY` 100.0, `TUNNEL_REROUTE_GUARD_MS` 30_000, `RerouteConfirmationGate` values, `ROAD_INFO_THROTTLE_MS` 2000, one `MAX_PLAUSIBLE_SPEED_KMH`). Native calls on `Dispatchers.Default`, state writes on the main dispatcher. No location call in `init`. Verify: `./gradlew :app:compileMobileDebugKotlin` clean (spec: `navigation-engine` — Engine lifecycle and threading, Per-surface view state never moves into the engine; design D1/D3/D6).
- [x] 2.2 Engine acquisition API: `acquire(destination, profile)` plus the surface-driven `start(routeEntry, vehicle)`; a confirmed reroute re-acquires with the retained profile and destination and needs no surface. Keep `startNavigationWithVehicle` usage as it is. Verify: compile clean and the existing route tests still reference a valid API (spec: `navigation-engine` — Route acquisition independent of a surface UI; design D2).
- [x] 2.3 Engine lifecycle: one `SupervisorJob + Dispatchers.Main` scope created once and never cancelled; `stopNavigation()` releases the native controller, resets the reroute gate and stale-speed state, and releases the location lease; `navigateTo` / `reportError` set `vehicle`/`errorOrigin` appropriately. Verify: compile clean; the stop path is exercised by task 6.2 (spec: `navigation-engine` — Engine lifecycle and threading; design D6).
- [x] 2.4 Location-lease integration: the engine acquires the lease (sibling change `shared-resource-arbitration`, `LocationService` consumer API) on navigation start and releases it on stop/arrival, and never calls `startLocationUpdates()` directly. Verify: compile clean; if the lease API is not in place yet, stop and confirm ordering with the sibling change before continuing (spec: `navigation-engine` — Engine lifecycle and threading; proposal Decided 4).

## 3. `:app` acquisition and observer switch

- [x] 3.1 `app/src/main/java/com/naviveylin/ui/route/RoutePanelViewModel.kt` and `app/src/main/java/com/naviveylin/ui/map/MapCanvasScreen.kt` (`:1882`, `:1987`): acquisition callers go through the engine; drop the `forceFollowMode` argument and let the phone surface enable follow from `isNavigating`. Verify: `./gradlew :app:testMobileDebugUnitTest --tests "com.naviveylin.ui.route.*" --tests "com.naviveylin.ui.map.MapCanvasViewModelModeTest"` green (spec: `navigation-controller` — Start navigation from route summary dialog, GPS follow mode; design D5/D7).
- [x] 3.2 Phone surface adapter: reduce `app/src/main/java/com/naviveylin/navigation/NavigationViewModel.kt` to the surface adapter (engine state/position flow for `MapCanvasViewModel.setNavigationViewModel`, follow callback, route-panel wiring, mode snapshot/restore) or replace it with an equivalently small adapter; `MapCanvasViewModel` (`:2580`) keeps its current contract. Verify: `./gradlew :app:testMobileDebugUnitTest --tests "com.naviveylin.ui.map.*"` green (spec: `navigation-engine` — Per-surface view state never moves into the engine; design D7).
- [x] 3.3 `app/src/main/java/com/naviveylin/service/NavigationNotificationService.kt` (`:49`) and `app/src/main/java/com/naviveylin/navigation/NavigationNotificationController.kt` (`:36`): inject the engine instead of the provider; the stop action calls the engine's `stopNavigation()` directly and keeps clearing the route panel and the drawn route; free driving keeps reading `DrivingModeProvider`. Verify: `./gradlew :app:testMobileDebugUnitTest --tests "com.naviveylin.navigation.NavigationNotificationControllerTest" --tests "com.naviveylin.service.*"` green (spec: `navigation-controller` — Stop navigation; design D8).

## 4. Delete the broker

- [x] 4.1 Delete `app/src/main/java/com/naviveylin/navigation/AANavigationController.kt` and `core/src/main/java/com/naviveylin/core/AutoNavigationController.kt`; remove `AutoEntryPoint.autoNavigationController()` and the car DI provider for it (`app/src/main/java/com/naviveylin/di/AutoServiceModule.kt`). Verify: `./gradlew :app:compileMobileDebugKotlin :auto:compileDebugKotlin` clean (spec: `navigation-engine` — Exactly one navigation engine per process; design D1/D8).
- [x] 4.2 Delete `app/src/main/java/com/naviveylin/navigation/NavigationStateProvider.kt`, `core/src/main/java/com/naviveylin/core/NavigationStopRequests.kt` and their tests; point `app/src/main/java/com/naviveylin/di/NavigationViewModelModule.kt` at the engine so `AutoEntryPoint.navigationViewModel()` returns it. Verify: `./gradlew :core:test :app:compileMobileDebugKotlin` clean and `grep -rn "NavigationStateProvider\|NavigationStopRequests" app/src/main auto/src/main core/src/main` returns nothing (spec: `navigation-engine` — Exactly one navigation engine per process; design D8).
- [x] 4.3 `:auto` wiring: `auto/src/main/java/com/naviveylin/auto/NavigationSession.kt` (`:403-408`) keeps its warmup step but resolves the engine (so a car-only process instantiates it), with the step string updated; `SessionLogTest` (`auto/src/test/java/com/naviveylin/auto/SessionLogTest.kt:105`) expectation updated. Verify: `./gradlew :auto:testDebugUnitTest --tests "com.naviveylin.auto.SessionLogTest"` green (spec: `auto-cross-device-sync` — Car-only navigation start; design D8).

## 5. Tests

- [x] 5.1 `app/src/test/java/com/naviveylin/navigation/NavigationEngineTest.kt`: listener callbacks drive state (position, instruction, lane, road info, arrival estimate, speeds), the road-info throttle holds its 2000 ms window, and the stale-speed ticker zeroes a stale speed (spec: `navigation-controller` — Navigation state exposed as StateFlow; design D1/D6).
- [x] 5.2 Engine reroute tests: a large deviation confirms on the fast path, a marginal one needs the 10 s confirmation, the 25 s cooldown blocks a cascade, accuracy worse than 100 m and a tunnel within 30 s block the reroute, and a confirmed reroute re-acquires with the retained profile without any surface present (spec: `reroute-trigger` — all requirements; `navigation-engine` — Route acquisition independent of a surface UI; design D2/D3). Revert-check: with the car's old `REROUTE_MIN_INTERVAL_MS` gate restored, the cooldown and confirmation cases fail.
- [x] 5.3 Two-surface test: one process, phone and car both observing; exactly one native controller is started, both surfaces see the same state emissions, a stop from either surface ends navigation for both, and neither surface's view state is changed by the other's session (spec: `navigation-engine` — Exactly one navigation engine per process, One navigation state shared by all surfaces, Per-surface view state never moves into the engine).
- [x] 5.4 Error-origin test: an error raised for the car is presented on the car and not on the phone; an engine-wide error is presented on both (spec: `navigation-engine` — Errors carry the surface that caused them; design D4).
- [x] 5.5 Retarget the stop-path tests (`NavigationViewModelStopPathTest`) onto the engine and add a notification-stop test asserting navigation ends, the notification withdraws and the route panel plus drawn route are cleared (spec: `navigation-controller` — Stop navigation; `auto/navigation-view` — Leave navigation at any time).
- [x] 5.6 Delete the provider-specific tests with the class (`app/src/test/java/com/naviveylin/navigation/NavigationStateProviderTest.kt`) and update the remaining `:app`/`:auto` tests that construct the removed classes; verify every remaining engine test runs under the classloader rules in `AGENTS.md` (no `@Config(sdk=…)` on classes touching `FakeOSMScoutClient`). Verify: `./gradlew :app:testMobileDebugUnitTest :auto:testDebugUnitTest` green.

## 6. Documentation

- [x] 6.1 `guidelines/Design.md` §3: state the engine/surface split (navigation state is process-owned by one engine; follow, free driving, viewport, zoom, anchor and render state stay surface-owned); §4: state the engine's scope rule (process-lifetime scope, native calls off the main thread, main-thread state publication, no location subscription outside navigation). Verify: both sections state the rules and match the implementation (design Threading and lifecycle).
- [x] 6.2 `guidelines/UI.md` §8: re-check the phone/car parity statement against the merged behaviour (one reroute policy, identical presentation) and adjust the wording if the merge changed what parity means. Verify: the section does not contradict the change.
- [x] 6.3 `TODO.md`: close or update the entries this change resolves (the `NavigationStateProvider` stop-routing residual in §46, the `AANavigationController` unreleased-ticker entry in §42) and record anything newly deferred. Verify: each touched entry names this change and its remaining follow-up, if any.

## 7. Gates

- [x] 7.1 Build both flavors with no new warnings: `./gradlew :app:assembleMobileDebug :app:assembleAutomotiveDebug` — verify both APKs are written and the log adds no `w:`/`e:` line from the changed files.
- [x] 7.2 Full unit-test suite: clear the result directories first, then `./gradlew test`; verify green for `:core`, `:auto`, `:app` (mobile + automotive) and `:osmscout-client-java`, quoting executed counts from the result XMLs.
- [x] 7.3 Confirm the change is app-side only: `git status`/`git diff --stat` show no `app/src/main/cpp/libosmscout` change, no submodule gitlink bump and no `:osmscout-client-java` override (proposal Impact — no native change).
- [x] 7.4 `openspec validate one-navigation-engine --strict` → valid, and every spec scenario maps to an implemented seam or an explicit on-device task.
- [x] 7.5 On-device, phone: start, stop, reroute, notification stop, follow mode, mode restore after navigation ends; `adb logcat -s NaviVeylin` shows one engine's lines only.
- [x] 7.6 On-device, Android Auto / AAOS: car-only deep-link start with the phone UI closed, connect mid-navigation, disconnect mid-navigation (navigation survives), and the merged reroute timing on a real deviation (the one user-visible behaviour change). `guidelines/Build.md` §10 recipe; record the result and any open measurement in `TODO.md`.

## 8. Implementation decision notes (2026-09-27)

Deviations and choices made while implementing the tasks above; all are inside the
scope the specs describe, none narrows a specified behaviour.

1. **One location-lease role** (task 2.4): `LocationConsumers.NAV_ENGINE` ("nav-engine")
   replaces `PHONE_NAV`/`CAR_NAV`. With one engine for the process, "navigation running"
   is a single role, not one per surface (spec: `location-updates-lease` names roles, not
   surfaces). `LocationServiceTest` was updated to the remaining role names.
2. **The engine is the only location feeder** (tasks 2.1/3.2): it collects
   `LocationService.location` while navigating (speed converted to m/s, the unit the JNI
   `processLocation` expects) and forwards each distinct fix to the native controller. The
   phone map's two forwarding call sites in `MapCanvasViewModel` were removed, so a fix is
   processed once, not twice — previously the car fed km/h and the phone fed m/s.
3. **Displayed speed comes from the native SpeedAgent on both surfaces** (task 2.1): the
   car controller's extra override that displayed the raw location-provider speed (a
   workaround for emulator values) is gone; the merged engine uses the spike filter plus
   the stale-speed decay for both surfaces (spec: `gps-speed-priority`).
4. **The reroute cooldown survives the engine's own re-acquisition** (task 5.2): a
   surface-driven start resets the confirmation gate, but the engine's reroute restart does
   not — otherwise the restart would clear the 25 s cooldown it just started and the next
   off-route report could cascade (spec: `reroute-trigger` — cascade protection).
5. **The destination identity is part of the session** (task 2.2): `start()` records the
   acquired route's end point as the destination, so the state carries the destination and
   the engine's self-sufficient reroute re-acquires to the retained destination without a
   deep-link request having set it (spec: `navigation-engine` — Destination and vehicle are
   part of the state).
6. **The phone's route view adopts engine-acquired routes** (task 3.2):
   `RoutePanelViewModel.adoptRoute` lets the surface adapter mirror a route the engine
   acquired on its own into the panel and the map, so a self-sufficient reroute still draws
   the new route on the phone (spec: `navigation-controller` — Reroute handling).
7. **Follow mode is only imposed by the surface that started the session** (task 3.2): the
   phone adapter enables follow for a phone-started session and never for a car-initiated
   one, so starting navigation on the car leaves the phone's map mode, center and rotation
   untouched (spec: `navigation-controller` — Follow mode is not imposed on the other
   surface). `NavigationEngineTwoSurfaceTest` pins this.
8. **The engine's OSMScoutClient is a `dagger.Lazy`** (design D6): the engine is resolved
   eagerly (notification controller, car warmup) but builds the native client on first
   navigation, so app start does no native work through it.
9. **Test-suite migration**: the phone-VM and car-controller test files became engine tests
   (`NavigationEngineTest`, `…AcquisitionTest`, `…RoadInfoTest`, `…StepIndexTest`,
   `…RouteGeometryTest`, `…StopPathTest`) plus the new `…RerouteTest`, `…TwoSurfaceTest`,
   `…ErrorOriginTest`; `NavigationStateProviderTest` was deleted with the class, and the
   map/notification tests now build engine + adapter.
