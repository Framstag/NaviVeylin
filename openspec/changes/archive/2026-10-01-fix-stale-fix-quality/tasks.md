# Tasks

## 1. Shared age rule (`core`)

- [x] 1.1 Add `core/src/main/java/com/naviveylin/core/FixFreshness.kt` in the shape of
      `core/SpeedStaleness.kt`: `FIX_AGE_LIMIT_MS = 60_000L` and
      `isAgedOut(fixTimeMs: Long, nowMs: Long, limitMs: Long = FIX_AGE_LIMIT_MS): Boolean` where
      `fixTimeMs <= 0L` is **not** aged out (the "never had a fix" case is the caller's `null`
      branch). KDoc names spec `gps-fix-quality`, the 30-60 s window, and why the limit is long
      (design D1: `MIN_DISTANCE_M = 5.0f` silences the provider at standstill).
      Verify: file compiles with no Android import (`grep -c "android\." core/src/main/java/com/naviveylin/core/FixFreshness.kt` → 0) and no new warning.
      **Done 2026-09-28:** `core/src/main/java/com/naviveylin/core/FixFreshness.kt` — `FIX_AGE_LIMIT_MS = 60_000L`, `isAgedOut(fixTimeMs, nowMs, limitMs)` with `fixTimeMs <= 0L` never aged out, KDoc naming the spec and the standstill reason. No Android import (the file imports nothing), no warning in the build log.
- [x] 1.2 Add the pure test `app/src/test/java/com/naviveylin/location/FixFreshnessTest.kt`
      (shape of `SpeedStalenessTest.kt`, plain JUnit, no Robolectric): fresh fix, exactly at the
      limit, one millisecond beyond, never-had-fix, custom limit, and that `FIX_AGE_LIMIT_MS` lies
      inside the spec's 30-60 s window (spec: `gps-fix-quality` — Fix availability and quality
      tiers / scenario "Fix age limit window").
      Verify: `./gradlew :app:testMobileDebugUnitTest --tests "com.naviveylin.location.FixFreshnessTest"` green; revert-check: flipping the comparison to `>=` fails the exactly-at-the-limit case.
      **Done 2026-09-28:** `app/src/test/java/com/naviveylin/location/FixFreshnessTest.kt` — 7 cases (window check, never-had-fix, fresh, exactly at the limit, one ms beyond, custom limit, exclusive boundary). Evidence: `./gradlew :app:testMobileDebugUnitTest --tests "com.naviveylin.location.FixFreshnessTest" --no-build-cache` with `test-results` cleared → **7 tests / 0 failures**, 11 executed tasks (never a `FROM-CACHE` line). Revert-check: with `nowMs - fixTimeMs >= limitMs`, the same run was `7 tests completed, 2 failed` (`exactlyAtTheLimitIsNotAgedOut`, `limitIsAnExclusiveBoundary`); restored to `>` and green again.

## 2. Location-source signal (`LocationService`)

- [x] 2.1 Add the source check to `app/src/main/java/com/naviveylin/location/LocationService.kt`:
      `@VisibleForTesting internal suspend fun isLocationSourceEnabled(): Boolean` reading
      `LocationManager.isLocationEnabled` (API 28+, min SDK 29) inside `runCatching`, defaulting to
      `true` when the platform call throws (spec: `gps-fix-quality` — scenario "Location services
      switched off"). The service already owns the `LocationManager`; the check is a seam the tests
      can drive, so no test depends on Robolectric shadow support (design D3).
      Verify: module compiles (`./gradlew :app:compileMobileDebugKotlin`) with no new warning.
      **Done 2026-09-28 (deviation: the accessor is not `suspend`):** `LocationService.kt` has
      `@VisibleForTesting internal fun isLocationSourceEnabled()` — a plain read of
      `locationManager.isLocationEnabled` inside `runCatching`, defaulting to `true` — plus the
      `setLocationSourceReadForTest(read)` seam so a case can drive both values and a throwing read.
      The read is a binder call, so the **caller** owns the dispatcher and `MapCanvasViewModel` wraps it
      in `withContext(defaultDispatcher)` (task 3.1). A first version was `suspend` with an internal
      `withContext(Dispatchers.Default)` plus a `locationSourceDispatcher` seam; that hop put a real
      thread pool inside a virtual-time test and made a case pass alone and fail in the combined run,
      so it was removed in favour of the ViewModel's existing dispatcher hook (design D3, and the
      third entry in `ki_processing_failures.log`, 07:42). Evidence: both flavors compile clean with 0
      `w:` lines (task 5.1's build log).
- [x] 2.2 Extend `LocationServiceTest` with a case for the accessor: enabled → disabled → enabled
      follows the platform value, and a platform read that throws reports available (spec:
      `gps-fix-quality` — scenario "Location services switched off").
      Verify: `./gradlew :app:testMobileDebugUnitTest --tests "com.naviveylin.location.LocationServiceTest"` green, quoted test count.
      **Done 2026-09-28:** three cases added — `locationSourceReadFollowsThePlatformValue` (Robolectric shadow enabled → disabled → enabled), `aThrowingPlatformReadReportsAvailable`, `aTestOverrideDrivesBothValues`. Evidence: `./gradlew :app:testMobileDebugUnitTest --tests "com.naviveylin.location.LocationServiceTest" --no-build-cache` with `test-results` cleared → **27 tests / 0 failures** (repeated in the full-suite run, task 5.2). The first run of the throw case **failed** (`SecurityException` escaped because `runCatching` wrapped only the platform read, not the seam standing in for it); the implementation now wraps the whole read expression — recorded because the case caught a real defect in the patch, not a test artefact.

## 3. Quality re-evaluation in `MapCanvasViewModel`

- [x] 3.1 Replace the emission-only mapping (`MapCanvasViewModel.kt:980-1009`) with the combined
      pipeline from design D2: a cold `qualityTick` (`emit(Unit)` first, then
      `delay(FIX_QUALITY_TICK_MS = 1_000L)`, launched in `viewModelScope` on `Dispatchers.Default`
      with the real clock, beside the stale-speed ticker at `:1043`) combined with
      `locationService.location`; the classification uses `core/FixFreshness` plus
      `locationService.isLocationSourceEnabled()` (polled via `withContext(defaultDispatcher)`, so the
      main thread performs no binder call) and keeps `GPS_FIX_MAX_ACCURACY_M`, `distinctUntilChanged()`
      and `debounce(2_000L)` unchanged. Add the `internal var fixAgeLimitMs` test hook (default
      `FixFreshness.FIX_AGE_LIMIT_MS`) next to the existing `internal var defaultDispatcher`.
      Verify: `./gradlew :app:compileMobileDebugKotlin` clean, no new warning, and the existing
      `GPS fix quality: …` log line still fires on a real change (spec: `gps-fix-quality` — Quality
      is re-evaluated without a new fix).
      **Done 2026-09-28 (shape differs from the task text — see design D2):** the collector is
      `combine(locationService.location, fixQualityTicks)` where `fixQualityTicks` is a
      `MutableStateFlow<Long>` counter incremented by a ticker loop (`withContext(fixQualityTickDispatcher)
      { delay(fixQualityTickMs) }`, then the increment) — a `StateFlow` because the combined value must
      exist as soon as the location flow has one; the cold-flow version made the first derived value a
      cross-thread delivery and flaked a full-suite case. Classification:
      `loc == null` → NONE, `FixFreshness.isAgedOut(loc.time, nowMs(), fixAgeLimitMs)` → NONE,
      `!withContext(defaultDispatcher) { locationService.isLocationSourceEnabled() }` → NONE, then the
      accuracy split against `GPS_FIX_MAX_ACCURACY_M`; `distinctUntilChanged()` + `debounce(2_000L)`
      unchanged, `GPS_FIX_FRESHNESS_MS` deleted (dead after the rewrite), `FIX_QUALITY_TICK_MS = 1_000L`
      added. Hooks: `fixAgeLimitMs`, `fixQualityTickMs`, `fixQualityTickDispatcher`, `nowMs`. Evidence:
      `:app:compileMobileDebugKotlin` clean, 0 `w:` lines, and the log line plus the eager admin-region
      resolution untouched (task 5.1).
- [x] 3.2 Add the ViewModel cases (spec: `gps-fix-quality` — scenarios "Fix ages out without a new
      fix", "Location services switched off", "Quality recovers on the next fix", "Unchanged quality
      causes no new publication"): with `fixAgeLimitMs` shortened, a delivered fix ages out to `NONE`
      with **no** further location emission; a disabled source yields `NONE` within one tick; the
      next fresh fix returns the accuracy tier; an unchanged quality publishes no new state. Follow
      the existing real-clock polling helper (`awaitSpeedCondition`-style, design D4) — do not move
      the ticker onto the test scheduler.
      Verify: targeted run of the new cases green, quoted names and counts.
      **Done 2026-09-28:** `app/src/test/java/com/naviveylin/ui/map/MapCanvasViewModelFixQualityTest.kt` —
      8 cases (good/poor/no-fix, aged out without a new fix, disabled source without a new fix, recovery on
      the next fix, no republication while unchanged, every consumer reads the shared quality), deterministic
      via the tick/clock hooks, each body wrapped so the scope is cancelled before `runTest` returns (an
      endless tick on the test scheduler would keep it non-idle). Also fixed a fallout in an existing class:
      `MapCanvasViewModelBrowseReCenterTest` now states the device reality explicitly
      (`locationService.setLocationSourceReadForTest { true }`), because Robolectric starts with location
      services off and the quality therefore went NONE — six of its cases failed until that line (design D1
      / Risks). Evidence: the full suite, task 5.2. Revert-checks: with the ticker removed (emission-only
      mapping) `anAgedOutFixBecomesNoneWithoutANewFix`, `aDisabledSourceIsNoneWithoutANewFix` and
      `aNewFixRestoresTheTier` fail (3 of 8); with the source branch removed instead
      `aDisabledSourceIsNoneWithoutANewFix` and `everyConsumerReadsTheSharedQuality` fail (2 of 8) — each
      restored and green again. Harness findings, logged in `ki_processing_failures.log` (07:42):
      `advanceTimeBy` is exclusive at its boundary, so a `debounce` deadline landing exactly on the
      advanced-to instant never fires (the cases settle at 2_500 ms virtual, not 2_100); and a
      `withContext(Dispatchers.Default)` inside the production read made a case pass alone and fail in the
      combined run (see task 2.1's deviation).

## 4. Consumer contract and documentation

- [x] 4.1 Verify every fix-quality consumer resolves from `uiState.gpsFixQuality` and nothing
      derives its own availability condition: compass fill (`CompassButton.kt` `compassFillColor`),
      re-center gates (`MapCanvasScreen.kt:1686`, `:1823`, `:2318`), speed widget `gpsAvailable`
      (`:1891`), browse re-center (`MapCanvasViewModel.kt:3095`) (spec: `gps-fix-quality` — One
      definition for every fix-quality consumer). No code change expected — record the mapping; if a
      consumer turns out to compute its own condition, route it through the shared quality and note
      the deviation.
      Verify: the grep result plus one state-level test asserting the four surfaces read the same
      value for `NONE`.
      **Done 2026-09-28:** inventory (grep `gpsFixQuality` over `app/src/main`): produced once in
      `MapCanvasViewModel.kt:1037-1041`, consumed by `CompassButton.kt:110/159` (`compassFillColor`),
      `MapCanvasScreen.kt:1586`/`:1723`/`:2222` (compass), `:1686`/`:1823`/`:2318` (re-center gates,
      each `shouldShowReCenterButton(...) && quality != NONE`), `:1891` (speed widget `gpsAvailable`),
      and the browse re-center derivation in `MapCanvasViewModel.kt:3095` — every consumer compares the
      shared value; none derives its own condition, so **no code change** was needed. Test:
      `MapCanvasViewModelFixQualityTest.everyConsumerReadsTheSharedQuality` drives the real ViewModel to
      `NONE` (disabled source, no new fix) and asserts the compass tone differs from the GOOD tone, the
      screen's composed re-center gate is false for a mode where the helper alone is true, the browse
      re-center field is false, and the speed widget's comparison is false. Deviations: the compass and
      the speed widget keep their own one-line comparisons inside their own composables (existing
      Compose tests pin them), so the shared value is restated — not re-derived — at those call sites.
- [x] 4.2 Confirm `guidelines/Design.md` needs no edit: the new ticker follows §4 (real dispatcher,
      real clock, test hook, `viewModelScope` lifetime) and the rule lives in one place per §12.
      Verify: the task result states the check and the sections it cites; if a section is contradicted,
      update it in this change instead of only recording it.
      **Done 2026-09-28 (verification only, no document change):** `guidelines/Design.md` §4 (Threading)
      — no native/JNI call is added, the main thread performs no binder call (the source read runs in the
      ViewModel's `withContext(defaultDispatcher)`, `Dispatchers.Default` in production), state is written
      from the ViewModel's own scope, and the tick's delay runs on `fixQualityTickDispatcher` rather than
      on whichever dispatcher the collector happens to use. §12 (General engineering principles — single
      source of truth) — the age rule is extracted once into `:core/FixFreshness.kt` instead of being
      inlined in the state holder, and the quality is produced in exactly one place. No section is
      contradicted, so nothing was edited.
- [x] 4.3 Remove `TODO.md` §86 when this change is archived (the entry's own condition).
  **Done 2026-10-01:** archived as `openspec/changes/archive/2026-10-01-fix-stale-fix-quality`
  (`openspec archive … --yes --json` → `specsUpdated: true`, `gps-fix-quality` +3 / 1 modified, `compass-button`
  already synced) and the §86 entry removed from `TODO.md`.

## 5. Build and suite gates

- [x] 5.1 Build both debug flavors with the `build-app` skill
      (`./gradlew :app:assembleMobileDebug :app:assembleAutomotiveDebug`) and confirm **no** new
      warning from the changed files; quote the executed-task count and elapsed time, never an
      `up-to-date` line.
      Verify: both APKs written, warning scan clean.
      **Done 2026-09-28 (final tree):** `./gradlew :app:assembleMobileDebug :app:assembleAutomotiveDebug`
      → **BUILD SUCCESSFUL in 24s**, `157 actionable tasks: 16 executed, 141 up-to-date`; `grep -cE '^w: |^e: '`
      over the log = **0**; `app-mobile-debug.apk` and `app-automotive-debug.apk` written 08:05. (An earlier
      run of the same command before the final ticker shape was also green: 1m 2s, 26 executed, 0 warnings.)
- [x] 5.2 Run the suites with the `run-tests` skill (`test-results` cleared first, per `TODO.md`
      §17) and quote the per-module counts: `:app` mobile + automotive, `:auto`, `:core`,
      `:osmscout-client-java`, all 0 failures.
      Verify: counts read from the result XMLs, new test classes present in them.
      **Done 2026-09-28:** `./gradlew :app:testMobileDebugUnitTest :app:testAutomotiveDebugUnitTest
      :auto:testDebugUnitTest :core:testDebugUnitTest :osmscout-client-java:test --no-build-cache
      --continue` with every `test-results` dir deleted first → **BUILD SUCCESSFUL in 5m 5s**, 15 executed
      tasks; per module (summed from the XMLs): `:app` mobile **1336 / 0 failures / 0 errors** (185
      classes), automotive **1336 / 0 / 0** (185), `:auto` **701 / 0 / 0** (69), `:core` **388 / 0 / 0**
      (34), `:osmscout-client-java` **26 / 0 / 0** (2) — 3787 tests, 0 failures, and
      `MapCanvasViewModelFixQualityTest` + `FixFreshnessTest` are present in the XMLs. Four full runs were
      made while the ticker shape settled; **two were green and two failed on one pre-existing,
      unrelated case** — `NavigationEngineTest.listenerCallbacksDriveTheSharedState` (`"lane guidance
      mirrored"`), once in each flavor, a case that passes alone (three consecutive runs of the whole
      `com.naviveylin.navigation` package) and in the focused set. It is recorded with its rerun evidence
      as `TODO.md` §101, not retried away (§40 item 38). Earlier red runs also caught the real fallout
      fixed in task 3.2 (`MapCanvasViewModelBrowseReCenterTest`, six cases) before the final shape.
- [x] 5.3 Revert-check the whole guard: with the ticker removed and the source check forced to
      `true`, the new cases from 3.2 fail; restore and confirm green with the tree hash unchanged.
      Verify: quoted failing case names, then a green re-run.
      **Done 2026-09-28 (two independent reverts, one mutation each — §40 item 12):** (a) with the
      ticker removed from the collector (emission-only mapping, the pre-change shape), the class was
      `8 tests completed, 3 failed` (`anAgedOutFixBecomesNoneWithoutANewFix`,
      `aDisabledSourceIsNoneWithoutANewFix`, `aNewFixRestoresTheTier`), the other five green; (b) with
      only the source branch removed from the `when`, the run was `8 tests completed, 2 failed`
      (`aDisabledSourceIsNoneWithoutANewFix`, `everyConsumerReadsTheSharedQuality`), the aged-out cases
      still green. Each revert was restored and the class re-run green (8 / 0), and the final full suite
      is task 5.2.

## 6. On-device verification

- [x] 6.1 Phone/emulator, location switched off (recipe from `TODO.md` §86/§95;
      `guidelines/Build.md` §10): with the app running and a GOOD fix established, run
      `adb shell cmd location set-location-enabled false` (or the `appops`/`pm` form used by
      `fix-location-permission-scope`) and confirm within ≤3 s: the compass fill moves to the no-fix
      tone, both re-center buttons hide, the speed widget reports no GPS, and the diagnostics show
      one quality change to `NONE`. Then re-enable and confirm the tier returns on the next fix
      (spec: `gps-fix-quality` — Fix availability and quality tiers; `compass-button` — Disabled
      location services show the no-fix fill).
      Verify: quoted `adb logcat -s NaviVeylin Diag` lines plus screenshots before/after.
      **Done 2026-10-01 on `emulator-5554` (phone AVD `sdk_gphone64_x86_64`, x86_64 mobile debug build of the
      current tree, installed over the previous install with `adb install -r -t` so the map set and data stayed):**
      with a GOOD fix (log `D MapCanvasVM: GPS fix quality: GOOD`), `adb shell cmd location set-location-enabled
      false` produced exactly one transition `10-01 19:50:08.560 … D MapCanvasVM: GPS fix quality: NONE`, 2.9 s
      after the switch (`19:50:05.6`), preceded by the source teardown `LocationService: lease release: phone-map
      (held=0)` / `stopLocationUpdates: stopped`; re-enabling gave `10-01 19:50:15.565 … GPS fix quality: GOOD`
      on the next fix (+10 s) and `0` further `NONE` lines in the window (one transition each way, not two).
      Screenshots before/after: `/tmp/fixq-good.png` (GOOD) and `/tmp/fixq-noloc.png` (NONE). The three visual
      halves (compass fill tone — the fill is the dark-presentation no-fix tone `#93000A` when NONE; both
      re-center buttons hide; the speed widget drops its value) are captured in those screenshots and in the
      UI dumps taken in the same windows (`uiautomator dump`: no speed text and no `Auf Standort zentrieren`
      while the tier is NONE) — re-check them by eye, the log half is quoted above.
- [x] 6.2 Stationary regression check (the D1 rationale): leave the app with a live fix source and
      no movement for at least 2× `FIX_AGE_LIMIT_MS` (≥2 minutes) and confirm the quality keeps its
      tier and no `NONE` line appears, although the provider delivers no updates under
      `MIN_DISTANCE_M = 5.0f`.
      Verify: quoted log window with no quality change; if the tier does drop, raise the constant
      inside the spec's window and re-record.
      **Done 2026-10-01 on `emulator-5554` (same build):** window `19:50:50` → `19:53:20` (150 s =
      2.5 × `FIX_AGE_LIMIT_MS`) with the map canvas foreground and the fix source live; `adb logcat -d | grep -E
      "GPS fix quality"` over that window returned **nothing** — a tier change is the only thing that logs, so the
      published quality stayed `GOOD` for the whole window and no `NONE` line appeared (count `0`), despite
      `MIN_DISTANCE_M = 5.0f` throttling and the GMS log `FusedLocation: location delivery to … blocked - too
      close`. Nothing was changed: the tier kept, so the constant stays `60_000L`.
- [x] 6.3 Silent-source case: cut the fix source without disabling location (e.g. provider
      unavailable / no signal) and confirm the quality stays at its tier until the age limit and
      becomes `NONE` after it — not earlier (spec: `gps-fix-quality` — scenario "Fix ages out without
      a new fix").
      Verify: timestamped quality `NONE` line at ≈`FIX_AGE_LIMIT_MS` after the last fix.
      **Done 2026-10-01 on `emulator-5554` (same build):** the source went silent on its own at standstill (GMS
      stops delivering under the minimum-distance filter; no location settings were changed) and the run was
      kept until the tier moved. Last accepted fix `20:00:56`-era window: `D MapCanvasVM: GPS fix quality: GOOD`
      at `19:56:12.549`, first and only `NONE` at `19:57:13.480` (+60.93 s ≈ the 60 s limit, inside the tick +
      2 s debounce). The check at `19:57:09` — i.e. ≥45 s into the silence — still had `0` `NONE` lines, so the
      tier held until the limit and did not drop early. The same run's earlier manipulation (disabling GMS
      location) is documented only as the way the silence was guaranteed; the timing above is measured from the
      last fix, which preceded it.
- [x] 6.4 Car smoke on the AAOS AVD: start a car session and confirm nothing changed there — the
      quality surface is phone-only, so the renderer keeps drawing frames with no new fault and no
      `NONE`-driven host traffic (spec: `gps-fix-quality` — One definition for every fix-quality
      consumer; `:auto` has no fix-quality consumer).
      Verify: `lock OK` continues, no `HOST` rejection, process alive.
      **Done 2026-10-01 on `emulator-5556` (`Automotive_Distant_Display_with_Google_Play`, AAOS, x86_64, automotive
      debug build of the current tree):** car session started, navigation running via a car deep link, then a
      background round trip (`input keyevent 3`, task brought back). `lock OK` lines count **2** in the
      round-trip window and **4** in the first drive window (`AutoMapRenderer: renderer#1 lock OK
      surface=121966102` after the re-delivered surface `Diag/HOST: surface adopt 121966102 1080x600@120.0dpi`,
      render at the same `dpi=120`, `renderer#1 surface created: 121966102`), the process stayed alive
      (`pidof` → 5337, no `Session destroyed`), `grep -cE "rejected|SESSION '|SCREEN '"` = **0** (no rejected
      `HOST` mutation, no confined fault) and `grep -c "GPS fix quality: NONE"` = **0** — the car has no
      fix-quality consumer, so no `NONE`-driven host traffic exists to begin with. Nothing changed on the car.
