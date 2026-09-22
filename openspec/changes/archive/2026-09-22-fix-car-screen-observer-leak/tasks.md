# Tasks

Specs: `specs/auto/screen-observation/spec.md` (requirements: one instance of each observation per
started period; a stopped screen performs no renderer or host work; observations are re-established
with the current state on start; work that must continue while stopped is not scoped to the started
period). Design: `design.md` D1–D5.

## 1. The observation seam (spec: auto/screen-observation — One instance of each observation per started period; design D1/D2/D5)

- [x] 1.1 Add the shared seam as a new `internal` class in `:auto` (e.g.
  `auto/src/main/java/com/naviveylin/auto/CarScreenObservations.kt`): one child
  `CoroutineScope(SupervisorJob() + Dispatchers.Main)` per started period, a launch entry point for
  observations, idempotent `start()`/`stop()` (a second start without a stop is a no-op; `stop()`
  before any start is a no-op), a stop that cancels every observation, and a test-visible count of
  live observations. KDoc it as main-thread-only and state that it neither resolves providers nor
  starts work of its own (design D4). — verify: new
  `auto/src/test/java/com/naviveylin/auto/CarScreenObservationsTest.kt` covers ten start/stop cycles
  leaving exactly one instance per registered observation, idempotent `start`, `stop` before `start`,
  `start` after `stop`, and the live count returning to 0 after `stop` (use `MainDispatcherRule` +
  `kotlinx-coroutines-test`; no `CarContext`, no Robolectric)
- [x] 1.2 Confirm the seam needs no DI, no Gradle dependency and no manifest change, and that
  `auto/build.gradle.kts:119-131` is unchanged — verify: `git status` shows no build-file or
  manifest diff, and `./gradlew :auto:compileDebugKotlin` succeeds

## 2. Browse map screen (spec: auto/screen-observation — A stopped screen performs no renderer or host work; design D3)

- [x] 2.1 Move `MapScreen`'s four collectors (GPS position incl. the periodic settings re-read,
  favorites, resolved dark presentation, basemap revision — `MapScreen.kt:693`/`:722`/`:734`/`:750`)
  onto the seam, called from the existing `onStart`/`onStop` (`:289`/`:292`), and delete the
  `observeJob` field plus `startObserving()`/`stopObserving()` (`:691-795`); constructor-time work
  (renderer init, surface-DPI collector, initial settings load) stays on the screen's own scope —
  verify: `./gradlew :auto:compileDebugKotlin` succeeds and the file contains no `scope.launch` inside
  a start path other than through the seam
  — **done 2026-09-21**: new `MapScreenObservations` (what is observed; the seam owns how long) plus
  the screen's `observations`/`screenObservations` pair; `onStart` → `screenObservations.start()`,
  `onStop`/`onDestroy` → `observations.stop()`; the fix body became `onGpsFix(pos)` and
  `resolveStreetName` is unchanged. `:auto:compileDebugKotlin` BUILD SUCCESSFUL, 0 warnings, no
  `observeJob`/`startObserving`/`stopObserving` left in the file
- [x] 2.2 Add the screen regression test — verify: new cases in
  `MapScreenObservationsTest.kt` (no `CarContext`: the real seam plus a mockable `:core` location and
  favorites provider and counting effects) assert every observed source reaches its effect, four
  observations after each of three start/stop cycles with one fix reaching exactly one of them, no
  effect while stopped (spec scenario "GPS fix arrives while the screen is stopped", "Basemap data
  changes while the screen is stopped"), one application per start for a change made while stopped
  ("Observations are re-established with the current state on start"), and the preserved guards
  (initial basemap revision 0 and a null fix are not applied)
  — **done 2026-09-21**: `MapScreenObservationsTest`, 6 cases, green. The screen object itself is not
  constructible in `:auto` tests (see the design's D2 note), so the screen's wiring is covered through
  its seam

## 3. Navigation screen (spec: auto/screen-observation — One instance of each observation per started period)

- [x] 3.1 Move `NavigationScreen`'s four collectors (navigation state, GPS position, resolved dark
  presentation, basemap revision — `NavigationScreen.kt:465`/`:519`/`:601`/`:614`) onto the seam,
  called from the existing `onStart`/`onStop` (`:389`/`:392`), and delete the `observeJob` field plus
  `startObserving()`/`stopObserving()` (`:458-664`); keep `reloadLiveSettings()` on the start path —
  verify: `./gradlew :auto:compileDebugKotlin` succeeds and the screen no longer launches any
  observation on its own scope
  — **done 2026-09-21**: new `NavigationScreenObservations`; `onStart` → `reloadLiveSettings()` +
  `screenObservations.start()`, `onStop`/`onDestroy` → `observations.stop()`; the collector bodies
  became `onNavigationState(state)` (`return@collect` → `return`) and `onGpsFix(pos)` with comments
  preserved. `:auto:compileDebugKotlin` BUILD SUCCESSFUL, 0 warnings, no leftovers
- [x] 3.2 Add the screen regression test — verify: cases in `NavigationScreenObservationsTest.kt`
  (mockk `NavigationViewModel` + fake location provider + counting effects) assert every observed
  source reaches its effect, four observations after each of three cycles with one state change
  reaching exactly one of them, no state/template/renderer work while stopped, and one application per
  start for a route change made while stopped
  — **done 2026-09-21**: `NavigationScreenObservationsTest`, 6 cases, green (same seam-based
  mechanism as 2.2)

## 4. Free-driving screen (spec: auto/screen-observation — Work that must continue while stopped is not scoped to the started period)

- [x] 4.1 Move `FreeDrivingScreen`'s GPS-position and basemap-revision collectors
  (`FreeDrivingScreen.kt:418`/`:431`) onto the seam, called from the existing `onStart`/`onStop`
  (`:353`/`:356`), and delete the `observeJob` field plus `startObserving()`/`stopObserving()`
  (`:416-574`); leave the stale-speed ticker (`:281`), `loadSettings()` and the renderer init on the
  constructor scope — verify: `./gradlew :auto:compileDebugKotlin` succeeds and the stale-speed ticker
  is still launched from `init`
  — **done 2026-09-21**: new `FreeDrivingScreenObservations` (keeps the fix-fault confinement and the
  `FreeDrivingScreen` log tag); `onStart` → `loadSettings()` + `screenObservations.start()`,
  `onStop`/`onDestroy` → `observations.stop()`; the stale-speed ticker is still in `init` on the
  screen's own scope. `:auto:compileDebugKotlin` BUILD SUCCESSFUL, 0 warnings, no leftovers
- [x] 4.2 Add the screen regression test — verify: cases in `FreeDrivingScreenObservationsTest.kt`
  assert every observed source reaches its effect, two observations after each of three cycles, no
  work while stopped, one application per start for a fix that arrived while stopped, the preserved
  guards, and the spec scenario "Background round trip during free driving with a stale fix"'s
  mechanism — a throwing fix does not kill the observation (the runCatching behaviour the screen had)
  — **done 2026-09-21**: `FreeDrivingScreenObservationsTest`, 7 cases, green. Robolectric (default
  sandbox) is required for the fault-confinement case only, because the wiring logs the failed fix
  through `android.util.Log`

## 5. Suites, builds and coverage

- [x] 5.1 Run the unit suites (spec: all four requirements — the change must not regress the
  screens' existing behaviour) — verify: `run-tests` skill green for `:auto`, `:app` (mobile and
  automotive) and `:core` with 0 failures/0 errors in the result XMLs, quoting the executed-task
  count and elapsed time
  — **done 2026-09-21**: result dirs cleared first (TODO §17), then `./gradlew test --continue
  --rerun-tasks` → **BUILD SUCCESSFUL in 6m11s, 181 actionable tasks (181 executed, no cache hits)**,
  no `FAILED` line. Result XMLs: `:app` mobile 1087 tests / 0 failures, `:app` automotive 1087 / 0,
  `:auto` 579 / 0 (was 553; +26 new observation cases), `:core` 302 / 0 — 236 XMLs, all
  `failures="0" errors="0"`. `./gradlew -p buildSrc test` → BUILD SUCCESSFUL
- [x] 5.2 Build both flavours (spec: auto/screen-observation — the car screens compile and ship in
  both distributions) — verify: `build-app` skill builds `:app:assembleMobileDebug` and
  `:app:assembleAutomotiveDebug` with `BUILD SUCCESSFUL` and no new Kotlin warnings; then one
  all-ABI build and `unzip -l` shows `arm64-v8a`, `armeabi-v7a` and `x86_64`
  — **done 2026-09-21**: `./gradlew :app:assembleMobileDebug :app:assembleAutomotiveDebug` BUILD
  SUCCESSFUL, no ABI injection (all three ABIs built), 0 Kotlin warnings from the new/changed files
  (the 66 warnings in the full log are the pre-existing test-source ones of TODO §44). Both APKs
  contain `lib/arm64-v8a/`, `lib/armeabi-v7a/` and `lib/x86_64/` (`unzip -l`)
- [x] 5.3 Regenerate the coverage report for the new seam (report-only, no gate) — verify:
  `./gradlew :koverHtmlReport :koverXmlReport :osmscout-client-java:jacocoTestReport` succeeds and
  `CarScreenObservations` shows line coverage from its dedicated test class
  — **done 2026-09-21**: command BUILD SUCCESSFUL; the merged `build/reports/kover/report.xml`
  (17:05, fresh) lists all four new classes with 0 missed lines: `CarScreenObservations` 13/13,
  `MapScreenObservations` 9/9, `NavigationScreenObservations` 15/15,
  `FreeDrivingScreenObservations` 15/15

## 6. On-device verification (spec: auto/screen-observation — all four requirements; design `Verification`)

- [x] 6.1 AAOS AVD run: with the automotive debug APK on the AVD, drive the scenario "start a car
  session, then repeat (open a menu screen and pop back) three times and (HOME + return) three
  times" while navigating — verify: `adb logcat -d | grep -c 'lock OK'` and the `Diag/MAP` line
  count per round trip do **not** grow with the number of round trips (compare the first and the last
  round trip), and no `lockCanvas failed`/`surface invalid` appears; record the numbers against the
  `guidelines/Build.md` §10 baseline
  — **done 2026-09-21** on `emulator-5556` (AAOS AVD), automotive debug APK installed with no live
  session first (§50 pitfall), then `CarAppActivity` started: three *identical* background round trips
  (HOME, relaunch, 8 injected fixes via `adb emu geo fix`) → **9 full renders / 9 `Diag/MAP` render
  lines / 1 surface adopt each, identical in all three**, i.e. exactly one render per fix with no
  growth. A deep-link navigation push/pop round trip was driven as well (`navStart`/`navEnd` in the
  log; the one round trip without a navigation start shows the same 9-render baseline). Zero
  `lockCanvas failed` / `surface invalid` / `surface failed` lines in any of the six round-trip logs.
  Note: the per-fix render count is coalesced by `requestRender`, so the *decisive* multiplicity
  assertion stays the unit test (one emission → one effect); the device run corroborates it
- [x] 6.2 Memory check on the same run — verify: `adb shell dumpsys meminfo <app package>` TOTAL PSS
  and native heap after the third round trip are within ~10% of the value after the first (the leak
  is closed when the footprint stops growing with round trips); capture both samples in the notes
  — **done 2026-09-21**: native heap 111,812 KB after three round trips → 115,976 KB after three
  *further* round trips (**+3.7%**, within tolerance), Java heap 18,144 KB → 14,468 KB, TOTAL PSS
  189,057 KB (184 MB) after six round trips
- [x] 6.3 Phone + Android Auto projection smoke check (spec: auto/screen-observation — parity of the
  car screens across distributions) — verify: with the mobile build and a phone connected to Android
  Auto, browse → free driving → navigation → back to browse renders after every step and the street
  name pill / speed badge / navigation panel still update
  — **done (user verification, 2026-09-22)**: the app was run under Android Auto (mobile build) after
  the stability change series and runs again there — the car screens render and update across the
  browse → free driving → navigation → back walk, no host crash and no app `FATAL EXCEPTION`.
  Resolved from the former blocker (DHU unreachable from the agent session) by the user's manual run.

## 7. Documentation and handover

- [x] 7.1 `guidelines/Design.md`: record the screen-observation rule next to the existing car
  lifecycle rules (§8 Android Auto — "Android Auto & cross-variant"; extend §4's threading bullet if
  the wording fits better there): a car screen's shared-state observations are scoped to its started
  period, exactly once each, and a stopped screen touches neither the renderer nor the host — verify:
  the guideline text names the rule, matches this spec, and `grep -n "observation" guidelines/Design.md`
  shows the addition
  — **done 2026-09-21**: `guidelines/Design.md` §8 gained the `**MUST**: a car screen's
  shared-state observations are scoped to its **started period**` rule (one instance per start, all
  cancelled on stop, `CarScreenObservations` + `<Screen>Observations`, the leak it replaces, and the
  work that must survive a stop)
- [x] 7.2 `TODO.md`: add the fixed entry for the observer leak in the established
  "FIXED by `<change>`" form (as §41/§46 do), cross-reference the still-open related items (§52-§57)
  and the app-process-killer ranking in §51 — verify: the new entry names this change, the three
  screens and the regression tests, and the still-open entries are unchanged
  — **done 2026-09-21**: `TODO.md` §58 (leak, fix, regression tests, on-device numbers — removed at
  archive) and §59 (new out-of-scope finding: an exception escaping a screen observation kills the
  app process, fix candidate = confine faults in the seam's `observe()`). §51-§57 untouched — in
  particular §57 (`DetailsScreen`'s init-scoped observers) stays open, since that screen is not part
  of this change
- [x] 7.3 `AGENTS.md`: update the Android Auto section only if this change alters a stated fact (the
  observer lifecycle may deserve one sentence next to the surface-ownership rules) — verify: either
  the section gains that sentence, or the notes state why no AGENTS.md change was needed
  — **done 2026-09-21**: `AGENTS.md` §Android Auto gained the bullet "**Screen observations are
  scoped to the started period**" with the rule, the `CarScreenObservations`/`<Screen>Observations`
  split and the instruction to add a new observation there rather than as a bare `scope.launch`
- [x] 7.4 Before finishing, re-check `ki_processing_failures.log` for an entry covering
  "screen-scoped collector cancellation" and record any failed approach from this implementation
  (short log + problem description + timestamp), per the apply guidance — verify: the log contains
  the new entry, or the notes state that no approach failed
  — **done 2026-09-21**: entry `2026-09-21 17:12 — fix-car-screen-observer-leak` with three dead
  ends (invented absolute emission counts on StateFlow-backed sources — same class as the earlier
  FavoriteRepository entry; `android.util.Log` not mocked in a plain-JUnit `:auto` test killing the
  fault-confinement case; a wrong ABI `grep` pattern that produced a false "x86_64 missing" report)
  plus the shell-allowlist notes (incl. the blocked DHU binary of task 6.3). No pre-existing entry
  covered screen-scoped collector cancellation
