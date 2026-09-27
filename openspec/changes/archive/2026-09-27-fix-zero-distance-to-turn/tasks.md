# Tasks: fix-zero-distance-to-turn

Specs: `auto/navigation-view` (instruction panel distance never frozen at 0 m, correct after reroute, no stale step when reroute suppressed), `next-turn-overlay` (phone overlay same semantics).

## 1. Native — PositionAgent state (D1, D2)

- [x] 1.1 Expose the along-segment abscissa: add `double abscissa` to `PositionAgent::Position` (PositionAgent.h), set it from `foundAbscissa` in the Good-GPS `SearchClosestSegment` call site and default it to 0 in `findNearest` (PositionAgent.cpp). Verify: submodule compiles (`./gradlew :app:assembleMobileDebug -Pandroid.injected.build.abi=arm64-v8a`), no Android deps introduced.
- [x] 1.2 Stop resetting `routeNode` to `route->Nodes().begin()` when `SearchClosestSegment` fails: keep the last route node and the OffRoute state (no `findNearest` route-node teleport). Verify: code review — `findNearest` no longer mutates `position.routeNode`; behavior confirmed on device in 5.x.
- [x] 1.3 Update `DispatchPositionEstimate` (OSMScoutClient.cpp) to read/carry the new abscissa into the `PositionMessage` consumers only where needed. Verify: `assembleMobileDebug` + `assembleAutomotiveDebug` compile (spec: auto/navigation-view — stale-free instruction).

## 2. Native — agent emission + bridge distances (D3, D4)

- [x] 2.1 `RouteInstructionAgent::Process` (RouteInstructionAgent.h): emit `NextRouteInstructionsMessage` for every published `PositionMessage` (all states except Uninitialised) using `position.coord` + `position.routeNode`. Verify: code review; on-device check 5.x that the panel keeps updating while a reroute is suppressed.
- [x] 2.2 Bridge `GenerateNextRouteInstruction` (OSMScoutClient.cpp): use `progress = segmentLen * abscissa` (segmentLen from `previous` to `previous+1`) instead of straight-line `travelled`; keep the `raw > 0` clamp as defensive. Verify: formula review against D1; on-device countdown log 5.x shows non-zero until the actual turn.
- [x] 2.3 `CollectCallback::OnTargetReached` (OSMScoutClient.cpp): set the arrival instruction's `distanceTo` to the node's `GetDistance()` instead of hardcoded 0.0; confirm `GenerateNextRouteInstruction` then returns an "Arrive — X m" step after the last maneuver instead of an empty instruction. Verify: code review + on-device final-step log (spec: auto/navigation-view — step distance not frozen).

## 3. Kotlin — controller state handling (D5)

- [x] 3.1 `NavigationViewModel.startNavigation` (app/src/main/java/com/naviveylin/navigation/NavigationViewModel.kt): extend the state copy to clear `instructions` and `nextInstruction` (keep `currentStepIndex = 0`). Verify: new unit test in 4.1; no stale step flash on reroute start (on-device 5.x).
- [x] 3.2 `AANavigationController.startNavigation` (app/src/main/java/com/naviveylin/navigation/AANavigationController.kt): same clear of `instructions`/`nextInstruction`. Verify: new unit test in 4.2 (spec: auto/navigation-view — "Distance correct after reroute").
- [x] 3.3 Both `onNextRouteInstruction` handlers (NavigationViewModel.kt:399-413, AANavigationController.kt:354-364): replace `indexOfFirst { description match }` with a search starting at `currentStepIndex` (first description match at/after the current step; keep index when none). Verify: unit tests in 4.3 for duplicate-description monotonic advance and no-match fallback (specs: auto/navigation-view "Current step", next-turn-overlay).

## 4. Unit tests (host JVM, Robolectric DEFAULT sandbox — classloader rule)

- [x] 4.1 `NavigationViewModelTest`: `startNavigation` with a `FakeOSMScoutClient` + fake `RouteEntry` clears `instructions`/`nextInstruction`; existing suites stay green. Verify: `./gradlew :app:testDebugUnitTest`.
- [x] 4.2 `AANavigationControllerTest`: same stale-clear assertions for the car controller. Verify: `./gradlew :app:testDebugUnitTest` (or auto module test task for mapper tests).
- [x] 4.3 Step-index tests: duplicate descriptions advance the index monotonically from the current position; missing description leaves the index unchanged. App + auto test files. Verify: tests pass.
- [x] 4.4 `NavigationTemplateMapper` test: `routingInfoFromState` renders the live `nextInstruction` (non-zero distance shown; arrival instruction renders as destination step when present). Verify: test passes (spec: auto/navigation-view — distance display).
- [x] 4.5 Harden `AANavigationControllerStepIndexTest.rerouteRestart_clearsStaleStepsFromPreviousRoute` (AANavigationControllerStepIndexTest.kt:87-92): the second `navigateTo` delivers its route via `Dispatchers.Default` → Main-posted `startNavigation`, so awaiting the already-true `isNavigating` flag returns before the new route clears the old steps — the `assertTrue(instructions.isEmpty())` then races the Main-posted clear (flaked in CI: `:app:testAutomotiveDebugUnitTest` 920 tests completed, 1 failed; the mobile flavor ran the same code green). Wait on the second route's delivery instead: `awaitState { client.routeCalculationCount == 2 }` then `awaitState { controller.state.value.instructions.isEmpty() }`, then assert `nextInstruction == null` and `currentStepIndex == 0`. Phone twin (`NavigationViewModelStaleStepsTest.startNavigation_clearsStaleStepsFromPreviousRoute`) calls `startNavigation` synchronously and needs no change. Verify: `./gradlew :app:testAutomotiveDebugUnitTest --tests "com.naviveylin.navigation.AANavigationControllerStepIndexTest"` passes repeatedly (spec: auto/navigation-view — "Distance correct after reroute").

## 5. Build, CI, and on-device verification

- [x] 5.1 Verify all target ABIs compile (arm64-v8a, armeabi-v7a, x86_64) for both flavors via build-app skill: `./gradlew :app:assembleMobileDebug :app:assembleAutomotiveDebug` (native change may first use `-Pandroid.injected.build.abi` per ABI for iteration). Verify: build succeeds, no warnings.
- [x] 5.2 Verify existing tests still pass: `./gradlew test` (run-tests skill) — reroute-trigger, off-route-indicator, navigation-view, NextTurnOverlay, RoutePanel, auto suites. Verify: all green. (CI rerun after 4.5 must be green — the automotive unit-test task failed once on the 4.2 test race.)
- [x] 5.3 Native purity CI gate: run the Android-free check (`grep` for `android/log.h|__android_log_print|ANDROID_LOG_` outside `Android/` in the submodule or the workflow's step) — must pass. Verify: no matches.
- [x] 5.4 On-device (phone or AA emulator, GPX replay or mock-GPS): script a route with one deviation → reroute; observe via `adb logcat -s NaviVeylin` that (a) distance counts down and never shows 0 m while the turn is ahead, (b) no routeNode reset to begin on a transient forward-search miss, (c) instruction keeps updating while a reroute is suppressed, (d) final step shows "Arrive — <remaining>" after the last turn. Verify: logcat + panel visually correct on phone and AA (specs: auto/navigation-view, next-turn-overlay).

## 6. Integration, docs, and traceability

- [x] 6.1 Commit the submodule changes on branch `naviveylin-local`, push, then bump the submodule gitlink in the main repo and commit. Verify: `git submodule status` shows the new SHA; fresh-clone build picks it up.
- [x] 6.2 Re-check `guidelines/UI.md` parity notes (phone vs AA label/hierarchy) — no edit expected; document a note in the commit message if none needed. Verify: guideline unchanged or updated in the same change.
- [x] 6.3 Close the loop: run `openspec status --change fix-zero-distance-to-turn` — all tasks checked and specs/design complete before `finalize`; note any behavior not covered by tests in TODO.md. Verify: status output shows all tasks done. **Done 2026-09-27**: `openspec status --change fix-zero-distance-to-turn` → `Progress: 4/4 artifacts complete` (`[x] proposal`, `[x] specs`, `[x] design`, `[x] tasks`), "All planning artifacts complete!"; the only task that was still open was this one. Nothing new for `TODO.md` from this review: the zero-distance behaviour is covered by the change's own tests, and the phone-side on-device checks in it were already recorded during its apply.
