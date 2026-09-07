# Tasks: Fix stale route after reroute + auto-zoom re-engage affordance

Parent specs: `map-render` (specs/map-render/spec.md), `map-recenter-button` (specs/map-recenter-button/spec.md). Design: design.md.

## 1. MapRenderer: blit fast-path must not discard forced renders (spec: map-render — Forced overlay renders are never dropped by the blit fast-path, New route appears without user interaction)

- [x] 1.1 In `MapRenderer.submitDebounced` (app/src/main/java/com/naviveylin/ui/map/MapRenderer.kt:454-457), change the blit-covered branch so it discards `pendingRender` only when it is not a forced render: `if (pendingRender?.forceFullRender != true) pendingRender = null` before `return`; add a comment explaining the blit is a tile preview and must not suppress overlay/forced passes; verify compile via `./gradlew :app:compileMobileDebugKotlin`
- [x] 1.2 Add a Robolectric unit test (new `MapRendererBlitTest.kt` under `app/src/test/java/com/naviveylin/ui/map/`, `@RunWith(RobolectricTestRunner::class)` default sandbox — see AGENTS.md classloader rule): (a) submit a forced render (`setRoute` with non-empty arrays) then a zero-shift non-force submission whose blit covers → the forced render still executes and a new frame carrying the route is emitted without any camera movement; (b) control: covering blit with no pending forced render → no render job enqueued; verify `./gradlew :app:testMobileDebugUnitTest --tests "*MapRendererBlitTest*"` passes (add a test-visible hook in MapRenderer only if needed to observe queued jobs, e.g. `@VisibleForTesting` accessor)
- [x] 1.3 Verify the same guard does not degrade the ordinary pan path: run the existing `canvas-overrun`/render unit tests and confirm the blit optimization scenarios still pass (no extra full renders for in-overrun pans)

## 2. MapCanvasViewModel: expose auto-zoom suspension (spec: map-recenter-button — Re-center button appears when follow mode is inactive or auto-zoom suspended)

- [x] 2.1 Add `autoZoomPaused: Boolean = false` to `MapCanvasUiState` (app/src/main/java/com/naviveylin/ui/map/MapCanvasViewModel.kt:114 area); mirror `autoZoomSuspended` (line 243) into it: set `autoZoomPaused = true` in `updateMagnification` when suspending, `false` on every resume path (`onToggleFollowMode(true)`, `startNavigation`, speed-band unsuspend); keep the @Volatile field as the internal check source; verify compile
- [x] 2.2 Add unit tests (Robolectric, `MainDispatcherRule`): pinch/button zoom during navigation → `uiState.autoZoomPaused == true`; follow toggle true → false; `startNavigation` → false; verify `./gradlew :app:testMobileDebugUnitTest` passes

## 3. MapCanvasScreen: re-center button on auto-zoom suspension (spec: map-recenter-button — Auto-zoom suspended while navigating (follow still on), No suspension, following normally)

- [x] 3.1 Update the visibility condition at MapCanvasScreen.kt:1076 and :1220 to `(state.autoZoomPaused && navState.isNavigating) || !state.followMode` (keep the GPS-fix gate); `reCenterAction` unchanged (already re-enables follow + unsuspends); verify compile via `./gradlew :app:compileMobileDebugKotlin`
- [x] 3.2 Add Compose tests (`MapCanvasScreen` follow-up in `app/src/test/java/com/naviveylin/ui/map/`): button visible when `autoZoomPaused && isNavigating` (follow still on); hidden when auto-driving (no suspension, follow on); visible when `!followMode`; hidden with no GPS fix; tapping while suspended re-enables and hides the button; verify `./gradlew :app:testMobileDebugUnitTest --tests "*MapCanvasScreen*"` passes
- [x] 3.3 Re-center button must not be covered by the routing status view: while navigating, anchor the button inside the navigation widget `Box` (bottom-start, directly above `NavigationStateOverlay`); keep the screen-bottom placement only for free-form (non-navigating) mode; verify compile via `./gradlew :app:compileMobileDebugKotlin`

## 4. Guidelines update

- [x] 4.1 Update `guidelines/MapRendering.md`: document the blit fast-path contract — sub-region blit is a tile-only preview and SHALL NOT suppress/queueless a pending forced overlay render (route/favorites/clear/stylesheet); reference `MapRenderer.submitDebounced`
- [x] 4.2 Update `guidelines/UI.md`: re-center button visibility rules — shown when follow mode is off OR auto-zoom is suspended while navigating; note the button placement (bottom-left) and that tapping re-centers + re-enables follow/auto-zoom

## 5. Build and regression verification

- [x] 5.1 Verify full build compiles without errors: `./gradlew :app:assembleMobileDebug` (build-app skill)
- [x] 5.2 Verify existing tests still pass: `./gradlew test` (run-tests skill), including `RoutePanelComposeTest.kt`, `RoutePanelViewModelRerouteTest.kt`, and navigation tests
- [x] 5.3 Verify the two in-progress sibling changes are unaffected (`fix-reroute-route-drawing`, `fast-reroute-trigger` — reroute trigger timing and route-clearing behavior untouched; code review diff check)

## 6. On-device verification (GPS replay / device, phone scope)

- [x] 6.1 GPX replay with a scripted deviation while navigating: after `onRerouteRequest: rerouting`, confirm logcat shows a `submitDebounced ... force=true` submission followed by a `debounce enqueue` and the new route rendered WITHOUT any pan/zoom gesture (spec: new route appears without user interaction); repeat with a failed calc → old route stays + snackbar (per `reroute-route-visibility`)
- [x] 6.2 During navigation, pinch-zoom (or zoom buttons): confirm the re-center button appears (auto-zoom suspended, follow still on) — visible in both portrait and landscape; tap it → viewport re-centers on GPS, auto zoom resumes (speed-based zoom commits in logcat), button hides
- [x] 6.3 During navigation, pan once: confirm follow mode disengages, the re-center button appears, auto zoom stops; tap → follow + auto zoom resume (no auto re-engage without the tap — verify no follow re-engages by itself within 60s)
