# Tasks: Fast reroute trigger

Parent spec: `reroute-trigger` (specs/reroute-trigger/spec.md). Design: design.md.

## 1. Distance helper (spec: reroute-trigger — Distance-based fast path)

- [x] 1.1 Add pure function `distanceToPolyline(lat, lon, lats, lons)` (haversine point-to-segment, mirroring `computeRouteDistance`) in `app/src/main/java/com/naviveylin/navigation/` and verify it compiles via `./gradlew :app:compileMobileDebugKotlin`
- [x] 1.2 Add unit tests for `distanceToPolyline` covering: point on segment (≈0m), perpendicular offset (right-angle detour), point beyond route ends, empty/single-point polyline, and verify `./gradlew :app:testMobileDebugUnitTest --tests "*DistanceToPolyline*"` passes

## 2. Confirmation gate rework (spec: reroute-trigger — Fast first trigger, Post-reroute cooldown, Distance fast path, Noise guards)

- [x] 2.1 Change `MIN_REROUTE_CONFIRM_COUNT` 5→2 and `MIN_OFF_ROUTE_DURATION_MS` 30s→10s in `NavigationViewModel.kt` companion object and verify constants compile
- [x] 2.2 Add `REROUTE_COOLDOWN_MS = 25_000L` and `lastConfirmedRerouteTime` state; reject confirmation while `now - lastConfirmedRerouteTime < REROUTE_COOLDOWN_MS`; set timestamp on every confirmed reroute; reset in `startNavigation`/`stopNavigation`; verify logic compiles
- [x] 2.3 Add distance fast path: on first `onRerouteRequest` of an episode, compute `distanceToPolyline` on `Dispatchers.Default`; if > 50m confirm immediately (count=1), else fall through to normal 2-count/10s path; verify logic compiles
- [x] 2.4 Keep existing guards unchanged (`MAX_REROUTE_ACCURACY` 100m, `TUNNEL_REROUTE_GUARD_MS` 30s) and verify no guard code was modified
- [x] 2.5 Add unit tests for the confirmation state machine: transient deviation (<10s) does not trigger, consistent deviation (≥10s) triggers, cooldown blocks re-confirmation within 25s and allows after, distance fast path confirms on first request, poor accuracy and tunnel guard still block; verify `./gradlew :app:testMobileDebugUnitTest` passes

## 3. Build and regression verification

- [x] 3.1 Verify full build compiles without errors: `./gradlew :app:assembleMobileDebug` (build-app skill)
- [x] 3.2 Verify existing tests still pass: `./gradlew test` (run-tests skill), including `RoutePanelComposeTest.kt` and navigation tests

## 4. On-device verification (spec: reroute-trigger — Phone and Android Auto parity)

- [x] 4.1 GPX replay of a scripted deviation on the phone: measure off-route→reroute-trigger latency from logcat (`onRerouteRequest: pending/rerouting` timestamps); verify ~10s normal path and ~5s fast path (>50m deviation)
- [x] 4.2 Verify no cascade reroute within 25s of a confirmed reroute during replay (logcat shows no second `rerouting` line inside the cooldown window)
- [x] 4.3 Verify parity on Android Auto / AAOS: start navigation from the car screen, deviate, confirm reroute triggers with same timing (emulator/head unit, `adb logcat -s NaviVeylin`)
