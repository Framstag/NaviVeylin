## 1. Kotlin: SpeedSanity filter at the fix choke point

- [x] 1.1 Add pure `SpeedSanity` class (mirroring `BearingFilter`): tracks previous fix (lat/lon/time); `process(lat, lon, reportedSpeedKmH, time)` returns effective speed — 0 when consecutive fixes (gap ≤ 3 s) moved < 2 m AND reported speed < 2.5 km/h, else reported value. Constants (`DEAD_BAND_KMH = 2.5`, `STATIONARY_DISPLACEMENT_M = 2.0`, `MAX_EVIDENCE_GAP_MS = 3000`) as named consts.
- [x] 1.2 Wire `SpeedSanity` into `LocationService.toGpsFix` (alongside `BearingFilter`) so `fix.speedKmH` is sanitized for all consumers (follow widget, `processLocation` to native, AA display).
- [x] 1.3 Add `SpeedSanityTest.kt` in `app/src/test/java/com/naviveylin/location/` covering: stationary + residual speed → 0; stationary + fresh low speed with real displacement → reported; moving fast → reported; gap > evidence window → no snap.

## 2. Kotlin: SpeedStaleness guard for provider silence

- [x] 2.1 Add pure `SpeedStaleness` class (now in `:core` — `core/src/main/java/com/naviveylin/core/SpeedStaleness.kt` — so the framework-free `:auto` module can share it): `isStale(lastFixTimeMs, nowMs)` when `now - lastFixTime > 3000`.
- [x] 2.2 `MapCanvasViewModel`: track `fix.time` of last fix; 1 Hz ticker in follow mode forces `currentSpeedKmH = 0` when stale; cancels on fresh fix / surface teardown.
- [x] 2.3 `NavigationViewModel`: update `lastFixTime` from the timestamp param of `processLocation` calls; on stale tick while navigating, set displayed `currentSpeedKmH = 0` (also settles auto-zoom target).
- [x] 2.4 `AANavigationController`: same guard on its `location.collect` (`fix.time`), mirror of 2.3.
- [x] 2.5 Add `SpeedStalenessTest.kt` + VM-level tests asserting the widget-visible speed goes to 0 after silence and recovers on a fresh fix.
- [x] 2.6 On-device AA free-driving gap fix: `FreeDrivingScreen` showed the pre-stop speed forever at standstill — the D2 tickers covered only the phone VMs and nav-mode controller state, never the free-driving screen's own badge volatile. Added a `SpeedStaleness`-gated 1 Hz ticker in `FreeDrivingScreen` (last-fix wall clock, zeroes the badge + re-renders; `:core` move made the guard importable). Verify: `:core:test`, `:auto:test`, `:app:testMobileDebugUnitTest` green; `:auto:assembleDebug :app:assembleMobileDebug` compile.
- [x] 2.7 On-device follow-up: after the zeroing landed, the AA badge showed the ROAD'S SPEED LIMIT at standstill instead of 0 km/h — `SurfaceIndicators.drawSpeedBadge` treated `currentKmH > 0.0` as "has speed", so a real 0 fell through to the `maxKmH` fallback and displayed the limit as the current speed ("final speed on halt = exactly max speed"). Fixed with pure `shouldShowSpeedBadge` (`>= 0.0` keeps the badge, so 0 without a limit also renders) + `speedBadgeLabel` (`>= 0.0 → "N km/h"`, limit only when current unknown/negative). Phone `SpeedWidget` was already correct (`!isNaN && >= 0`). Verify: `:auto:testDebugUnitTest` green (new `stationaryZeroShowsBadgeWithZeroKmh`).

## 3. Kotlin: filterSpeed decay instead of freeze

- [x] 3.1 `NavigationViewModel.filterSpeed`: record `lastValidSpeedKmH` + timestamp; unknown input (NaN/negative) returns 0 when stale (> 3 s), else the last known good.
- [x] 3.2 `AANavigationController.filterSpeed`: same decay rule (shared constants, same behavior as phone).
- [x] 3.3 Tests: unknown speed after staleness → 0; unknown within window → last good; fresh valid update restores immediately.

## 4. Native: SpeedAgent fallback displacement floor

- [x] 4.1 Patch `SpeedAgent.cpp` fallback branch (`libosmscout/src/osmscout/navigation/SpeedAgent.cpp`): push zero distance when `segmentDistance < Meters(2)` (gap < 10 s as today); per-segment, no other behavior change.
- [x] 4.2 Commit in submodule (`app/src/main/cpp/libosmscout`) with minimal, upstreamable message; bump submodule pointer in the main repo.
- [x] 4.3 Verify CI Android-free gate unaffected (no `android/log.h` usage added).

## 5. Build, test, verification

- [x] 5.1 Run `./gradlew test` — new unit tests + existing speed suite (`SpeedWidgetTest`, `MapCanvasViewModelSpeedWidgetTest`, `LocationServiceTest`, `AANavigationControllerRoadInfoTest`) green.
- [x] 5.2 Build debug: `./gradlew :app:assembleMobileDebug` (native rebuild via CMake).
- [x] 5.3 Emulator/GPX replay: stop mid-track → widget 0 within ~3 s; resume → speed returns; no regression on spike rejection.
- [x] 5.4 Real device (parked 60 s, drive): confirm widget 0 while parked, real values while driving; grab `adb logcat -s LocationService NaviVeylin` to confirm which mechanism dominated (silent-provider pin vs residual velocity) and tune constants if needed.
