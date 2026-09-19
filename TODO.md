# NaviVeylin TODO 

**Legend:** ✗ = missing | ⏳ = in progress / blocked | ✅ = done

---


## 33. `:auto` unit-test fork overflows its 512 MB heap once the whole suite runs

- **Observed 2026-09-19 while applying `aa-entry-zoom-animation`** ⏳: the full `:auto:testDebugUnitTest` run (47 classes, ~490 Robolectric/MockK tests) died with `java.lang.OutOfMemoryError thrown from the UncaughtExceptionHandler in thread "kotlinx.coroutines.DefaultExecutor"` plus `java.lang.instrument ASSERTION FAILED: "!errorOutstanding" with message can't create name string` — dozens of classes then failed with bare `java.lang.OutOfMemoryError` (`DetailsScreenTest`, `FavoritesScreenTest`, `GermanRenderingTest`, `MapScreenTest`, ...) and **no per-class XMLs were written**, so the failure looks like a mass regression instead of an infra limit. Root cause candidates: AGP's default `maxHeapSize = 512m` for the unit-test fork (confirmed: the test JVM is launched with `-Xmx512m`), the suite's accumulation in ONE JVM (Robolectric sandboxes + MockK/byte-buddy instrumentation + leaked `AutoMapRenderer` instances whose background loops and 1296×720 overrun bitmaps outlive their test), and machine pressure — the same suite was green earlier the same day (`2026-09-19 09:17/09:20`, 488 tests) with a free box, and OOMed again with a concurrent `:app:testMobileDebugUnitTest` running from a second session. Three-class runs stay green (`2026-09-19 09:45`, 95 tests, 0 failures). Fix options: set `android.testOptions.unitTests.all { maxHeapSize = "2g" }` (and consider `forkEvery`) in `auto/build.gradle.kts`, and/or shut renderers down in the tests that start their loops (`AutoMapRendererTest.noRenderRequestAfterSurfaceLoss`, the `spyk(AutoMapRenderer(...))` cases) — the latter were added where they were the offender. Not introduced by `aa-entry-zoom-animation`: the change touches no build file (its proposal states "no Gradle configuration change"), so the ceiling is pre-existing and this change's additions only moved the suite closer to it.

## 35. Address resolution loses the "exact match" bonus for free-text hits

- **Side effect of `search-result-ranking` (2026-09-19)** ✅ **Resolved in the same change during its verification pass**: `AddressParser.matchedExactly` now reads the per-attribute qualities (`locationMatchQuality`/`addressMatchQuality`/`poiMatchQuality`) and falls back to the collapsed `matchQuality` only when a native library reports no per-attribute quality at all, so address scoring no longer depends on a field whose meaning became search-tier-specific (design D6; tasks 8.2). Original finding kept for context: the JNI bridge no longer stamps every MARISA text-index hit with `matchQuality = "match"`; a hit reports `"match"` only when the indexed name really equals the query, otherwise `"candidate"` (spec `search-free-text`), while `AddressParser.kt:258` awarded `+25` on that field — free-text-derived candidates in `AddressBookResolver`/`AddressRanker` scored 25 points lower than before. No test depended on the old weight.

## 34. Car search opened from the root/history screens has no distance reference

- **Noticed 2026-09-19 while implementing `search-result-ranking`** ℹ: the spec's distance reference for the car is "last known GPS fix, else the current car map viewport center, else none (order by tier and quality only)". Only `MapScreen` can supply that center (it owns the renderer gate, so it passes `{ rendererGate.renderer.value?.markerViewport() }`); `RootScreen` and `SearchHistoryScreen` push `SearchScreen` without any map, so their results are ordered by tier and quality and show **no distance at all** — spec-legal, but a driver comparing results from the root list has no proximity signal. Fix candidate: publish the last rendered viewport center through a small shared provider (e.g. alongside `AutoLocationProvider`, updated by `NavigationSession`/`AutoMapRenderer` whenever the displayed center changes) and pass it from all three `SearchScreen` call sites. Not a defect of this change — the "neither exists" case is specified and covered by tests.

## 30. Notification-channel names and neutral navigation strings are hardcoded Kotlin constants

- **Noticed 2026-09-18 during `car-turn-by-turn-rail-widget`** ℹ: the new automotive channel had to be named through a string resource (adding `values-de` entries for the name, description and the car hint text), which exposed that other notification channels and the phone formatter still carry user-facing text as Kotlin constants: `MapDownloadService.CHANNEL_NAME` (`app/src/main/java/com/naviveylin/service/MapDownloadService.kt`, a plain `private const val` passed to `NotificationChannel`), the navigation channel name that this change converted to `navigation_notification_channel_name`, and `NavigationNotificationContent.TITLE_NAVIGATION_ACTIVE` / `TITLE_FREE_DRIVING` (`app/src/main/java/com/naviveylin/service/NavigationNotificationContent.kt:63-64` — the formatter file was renamed since this note) plus the `"Offroad"` fallback in `currentRoadText` (`app/src/main/java/com/naviveylin/ui/navigation/NavigationStateOverlay.kt:245`). None of them are translatable today, yet `GermanTranslationCompletenessTest` and the app's `checkHardcodedStrings` gate only look at resource files and at Compose/`setTitle`-style literals — the gate also misses `NotificationChannel(id, CONSTANT, …)`. Fix candidate: move the remaining names/neutral labels into resources with German translations, and (optionally) teach `checkHardcodedStrings` to flag `NotificationChannel(` name arguments so the next channel cannot ship untranslated.

## 31. Coordinate text is formatted with the default locale at several UI call sites

- **Noticed 2026-09-18 during `car-turn-by-turn-rail-widget`** ℹ: the new destination fallback for the car trip metadata needed a coordinate string and the repo showed two conventions: `MapCanvasViewModel.kt:2332` formats the selected-location *label* with `String.format(Locale.US, "%.5f, %.5f", …)` for stability, while `FavoritePickerDialog.kt:135`, `FavoritesSheet.kt:619` and `MapCanvasViewModel.kt:2357` use `"%.5f, %.5f".format(lat, lon)` with the default locale — in German that renders `52,51628, 13,37770`, and the comma between the two numbers makes the pair ambiguous to read. The car mapper deliberately uses `Locale.US`. Fix candidate: one shared `formatCoordinates(lat, lon)` helper in `:core` using `Locale.US`, used by every display site (and by the map logs that print the centre).

## 32. Phone notification neutral strings duplicated by the car hint resources

- **Introduced knowingly by `car-turn-by-turn-rail-widget` (2026-09-18)** ℹ: the car hint resolves its neutral fallback through the new `core` string `nav_hint_neutral` ("Navigation active"), while the phone formatter keeps the identical wording as the Kotlin constant `TITLE_NAVIGATION_ACTIVE`. The duplication is deliberate for now — the phone path had to stay byte-identical (its tests pass unedited) and the car path needed a resource for the i18n gate — but the two must not drift. Fix candidate: route the phone neutral title through `nav_hint_neutral` too (one formatter, one wording, German included) once the phone notification tests are allowed to change.

## 29. AA follow jumping — open points handed over (2026-09-17, stable-version handover)

Status: all four defects fixed and device-verified (2026-09-18, AAOS), including P2 and P3. `overlay-projects-against-displayed-frame` is archived; `aa-follow-framing-and-zoom-parity` is 27/27 and awaits archiving. What remains below: one open decision (the auto-zoom first commit), the deferred P4 option, and the device-verification method.

**What was fixed (do not re-investigate):**

| # | Defect | Fix | Evidence |
|---|--------|-----|----------|
| 1 | Overlays projected against the PENDING render target, so the marker detaches from its road between a viewport write and the render commit | displayed-frame accessors (`displayedLat/Lon/Mag/Angle`) used by marker + pin; blit offset published under `surfaceLock`; frame snapshot per render | 4 tests fail pre-change; live divergence `frame … mag=13,08` vs `pending … mag=13,00` |
| 2 | The follow commit re-anchored on the RAW FIX, stepping the whole scene by the extrapolation lead once per fix | P1: `reengageFollow` anchors on `markerPosition()` | `followReanchorUsesTheDisplayedPosition` fails pre-change; 4.3 px mean / 13.1 px max step measured |
| 3 | A fresh render was drawn UNSHIFTED (`drawToSurface`) while the display advanced during the 25-300 ms render, so every commit moved the map ~5-7 px and the next blit moved it back | both draw paths use one placement rule (offset that puts the display on the anchor) + `roundToInt` instead of truncating | drawn-frame continuity: JUMP −7.4 px -> +0.7 px per commit, rms 0.58 px over 165 frames |
| 4 | The committed fractional magnification was reset to the INTEGER viewport level on every fix without a new zoom target, so the map breathed in/out | `ViewportState.zoomFraction` + all five commit sites | `viewportStateCarriesTheFractionalMagnification`; live `pending mag=13,00` vs `frame mag=13,08` |

**Open points:**

1. **⏳ The auto-zoom's FIRST commit jumped straight to the speed target — FIXED in `aa-entry-zoom-animation` (2026-09-19), device verification pending:** the measured `mag 13.000 -> 17.000` in ONE frame (16x area) on entering free driving is now walked: a transition-eligible zoom commit farther than `ZOOM_BLIT_LIMIT` becomes a walk target, stepped ≤ the blit window once per LANDED render, ending exactly on the requested value (11 new `AutoMapRendererTest` cases; revert-checked: 6 fail pre-change). The walk has its own loop (`startZoomWalkLoop`) because the extrapolation loop is movement-gated — a parked entry would otherwise stall. It is deliberately NOT a controller seed (fix-paced ≈8 s, no visual gain) and stays render-paced. Remaining: on-device judgement of the walk (change tasks 4.1-4.5: magnitude sequence, `lock OK` render count vs the ~4 s bound, parked entry, navigation start, re-enable). Note for whoever takes it: P3's transition cannot smooth it as-is, because for a zoom-IN the frame carries the *new* magnification and reaching the old displayed one would need the buffer scaled below the overrun limit — the phone solves this by queueing the render while the animation plays (`smooth-zoom`: "the debounced native render SHALL be queued while the animation plays"), i.e. render at the old magnification first and let the displayed scale animate upward.
2. **⏳ P4 (smooth heading-up between fixes) — mechanism recorded, deliberately deferred** (design D4 of the follow-up change): rotate the canvas about the follow anchor by the accumulated heading residual. Bound: the exposed corner sliver is ~`r·θ` with `r` up to ~1000 px on a 1080x600 surface, i.e. ~1.5-2 deg of accumulated rotation before the overrun margin (108/60 px) is exceeded; cost: a filtered 1296x720 rotate per tick on a loop already measured at ~11 Hz (§19); risk: resampling softness and composing the overlay rotation with the marker's own bearing rotation. Revisit only if rotation stepping is still visible after P3.
3. **ℹ Device-verification method (do not re-derive):**
   - full renders = `adb logcat -d -t 2000 | grep -c 'lock OK'` (`blitToSurface` logs nothing, so `lock OK` counts ONLY full native renders); fixes = `grep -c 'FreeDrivingScreen: GPS fix'`. Baseline before the fixes: 101 renders / 105 fixes in 104 s.
   - frame placement = log `dy` + the frame centre + the display per drawn frame, then per consecutive pair compute `(Δdy + Δframe_px) − Δdisp_px` (the display's advance IS the expected content scroll); rms should stay sub-pixel, and any commit step shows as a spike. This is the check that found defect 3.
   - the AVD is `Automotive_Distant_Display_with_Google_Play` (AAOS, x86_64, API 33, 1080x600 main + virtual distant displays); the app id is `com.framstag.naviveylin`; the debug APK is `testOnly`, so install with `adb install -r -t`; build it with `-Pandroid.injected.build.abi=x86_64` (an arm64-only APK will not install); relaunch with `adb shell am start -n com.framstag.naviveylin/androidx.car.app.activity.CarAppActivity` and then tap Free driving (the car UI is not reachable from `uiautomator`).
   - the emulator console has NO `geo gpx`; `geo fix` / `geo nmea` injections are IGNORED by whatever feeds this AVD (the feed reports `bearing+360` at times), so a straight-line measurement must be produced by controlling the feed itself, not by injecting.
4. **⏳ Archive `aa-follow-framing-and-zoom-parity` (27/27, device-verified 2026-09-18):** `openspec/changes/*/` is gitignored, so an unarchived change exists only in this working tree. `overlay-projects-against-displayed-frame` is already archived.

---

## 19. Findings from `overlay-projects-against-displayed-frame` (2026-09-17)

Findings detected while fixing the AA follow overlay projection; all out of that change's scope
(it deliberately keeps the follow re-anchor cadence and the overlay frame bookkeeping only).

- **⏳ `reengageFollow` relies on a preceding `setViewport` to have requested the render** ✗: it writes `viewportLat/Lon` + `emitViewportState()` but never calls `requestRender()`; it works only because every current call site calls it immediately after `setViewport` (which does request). A caller that re-engages follow on its own would silently keep the stale frame on the surface. Fix option: request the render in `reengageFollow` when the target actually moved.
- **ℹ Extrapolation loop measured ~11 Hz, not the nominal 30 Hz** (`EXTRAPOLATION_FRAME_MS = 33`): in the 104 s AA window, 38 diagnostic lines at one line per 30 ticks is ~1140 ticks / 104 s ≈ 11 Hz. Each tick locks the shared surface and draws the full 1296×720 overrun bitmap plus the overlays, so the period is dominated by the draw — measure before tuning the constant (a smaller period would not raise the rate).
- **ℹ Diagnostics use the default locale:** `"%.6f".format(...)` in the AA follow log and the `Diag/MAP` render lines prints decimal commas on a German device (`51,513637`), so those lines are not machine-parseable. Use `Locale.ROOT` for diagnostic formatting.

---

## 18. Test-infra flakes found during `fix-aa-vehicle-anchor-not-applied` (2026-09-17)

- **`:auto` full-suite flake — `AutoMapRendererTest.marginRenderRequestHonoursThrottleParity`** ⏳: fails intermittently in full `:auto:testDebugUnitTest` runs (assertion at line 588), passes standalone 3/3. Belongs to the in-flight `fix-follow-vehicle-jumps` WIP (`AutoMapRenderer.kt` is modified in the working tree by that change; this change never touches the renderer). Rerun the suite to confirm; land the fix in that change, not here.

## 17. Verification-gate blind spots: Gradle test up-to-date masking + OpenSpec task-marker parsing

- **Gradle unit-test tasks report green without executing (found 2026-09-13 while running the archive gate for `fix-address-lookup-accuracy`)** ℹ: `./gradlew test` printed `BUILD SUCCESSFUL in 5s` with `152 actionable tasks: 4 executed, 145 up-to-date` — **zero tests ran**; the verdict came from the up-to-date check against a previous run's output, not from an execution. Two follow-ups that do NOT fix it: `--rerun` is ignored by AGP's unit-test tasks (only plain tasks such as `:osmscout-client-java:test` honour it), and `--rerun` on the aggregate `test` lifecycle task propagates to no dependent task at all. `--rerun-tasks` works but also forces every compile in the graph. What does work: delete the task outputs, e.g. `rm -rf app/build/test-results/testMobileDebugUnitTest app/build/test-results/testAutomotiveDebugUnitTest core/build/test-results/testDebugUnitTest auto/build/test-results/testDebugUnitTest`, then run the four test tasks — the run then reports real execution time (`BUILD SUCCESSFUL in 1m 59s`, 4 executed). Consequence to guard against: any OpenSpec apply/archive evidence of the form "`./gradlew test` → BUILD SUCCESSFUL" may prove nothing about the current tree; a 5-second "success" is the tell. Fix option: a Gradle `check`-wired task or a wrapper in `.pi/skills/run-tests` that clears `test-results` before invoking the suite, and a rule that the evidence line must quote the executed-task count and elapsed time. Also note the up-to-date check is content-hash based, so a file mtime later than the newest `test-results` XML does not by itself prove the run predates the edit — verify content, not timestamps.
- **OpenSpec silently ignores bare numbered task markers (found 2026-09-13)** ℹ: `fix-address-lookup-accuracy/tasks.md` used `1. [x] …`-style items (no `-` bullet); the task parser only recognises `- [x]`, so `openspec list` reported `completedTasks: 0, totalTasks: 0, status: "no-tasks"` for a change that was in fact 14/15 complete — while `openspec status` simultaneously reported `isPlanningComplete: true` / `isComplete: true`, so nothing errored. A change can therefore look stalled (or, read the other way, look finished) with no diagnostic. Fixed for that change by renumbering to `- [x] N.M` under numbered `## N.` headings; the other 13 open changes already used that form. Fix option: validate task-marker style in CI (every `tasks.md` must contain at least one `- [x]`/`- [ ]` item) so the mismatch fails loudly instead of silently zeroing the counts.

---

## 16. On-device verification pending: fix-zero-distance-to-turn (native step distance)

- **⏳ Pending device run (change `fix-zero-distance-to-turn`, tasks 5.4/6.3)** ✗: the native instruction-distance fix (submodule `3c761d618` on `naviveylin-local`, gitlink bumped at main `195f278`) is implemented and unit-tested (Kotlin state handling + step-index lookup + mapper arrival step — 14 new tests green, full `./gradlew test` green, both flavors all ABIs build). The **native engine path is not verifiable on the host JVM** (the JNI stub .so is symbol-free, so `PositionAgent`/`RouteInstructionAgent`/`GenerateNextRouteInstruction` run only on-device). On next device/emulator session: boot the Automotive AVD, drive a GPX-replay or mock-GPS route with one deviation → reroute, and check via `adb logcat -s NaviVeylin` (plus a temporary `osmscout::log.Debug()` of `distanceTo`/`nodeDist`/`abscissa`): (a) distance counts down and never shows 0 m while the turn is ahead, (b) no routeNode reset-to-begin on a transient forward-search miss, (c) instruction keeps updating while a reroute is suppressed, (d) final step shows "Arrive — <remaining>" after the last turn. Also re-run AA/phone instruction-panel screens at the destination tail — this is where the 0 m freeze appeared.

- **2026-09-15 emulator attempt: blocked by harness, not verified** ⏳: full run executed on Pixel_8 AVD (GMS disabled → `LocationManager` fallback; regional NRW map; Decathlon Aplerbeck route, 7,4 km, nav started). Fix stream works (locations + velocity delivered, `vel=6.69 m/s`, follow-mode camera tracks every hop), but **`geo fix` always emits `bear=0.0`** (emulator can't inject bearing — the §13 note: "geo fix has no bearing arg"). Without a directional fix, the native `PositionAgent` cannot establish travel direction and retains `routeNode` (the new fix-D1 behavior), so the instruction card froze on the first turn across ~2 km of driving and no reroute fired on a 1 km deviation — i.e. the 4 criteria could not be exercised meaningfully. One unconfirmed finding for the real-device pass: the next-turn overlay showed **"0 m" while the route panel showed "40 m" for the *same* turn** ("Links abbiegen → Ruhrallee") — possible distance-source divergence worth checking on-device (overlay vs panel step distance). §16 stays open; needs a real drive, or a proper mock-GPS app using `setTestProvider` with real timestamps/velocity/bearing (emulator `geo fix` and Fused both lack bearing).

---

## 1. Route Calculation & Visualization

| Feature | Status | Notes |
|---------|--------|-------|
| Avoid tolls/ferries checkboxes | ✗ | `RoutingProfile` supports avoid flags — no UI yet |

## 2. Turn-by-Turn Navigation

| Feature | Status | Notes |
|---------|--------|-------|
| Voice guidance / audio instructions | ✗ | JavaScout `onVoiceInstruction(int[])` callback exists in JNI |

## 3. GPX Track Import & Playback

> libosmscout submodule (master) already has GPX import/render support (archived change `javascout-gpx-track-import-render`) and JavaScout has `TrackPlayer` — but nothing is wired into the NaviVeylin app yet.

| Feature | Status | Notes |
|---------|--------|-------|
| GPX file import | ✗ | `importGpxTrack()` exists in JNI — no app UI |
| Track rendering on map | ✗ | `renderWithRouteAndPois()` accepts `trackLats`/`trackLons` |
| Track playback (simulated GPS) | ✗ | JavaScout `TrackPlayer.java` with speed multiplier |
| Track playback toolbar (play/pause/stop/speed) | ✗ | JavaScout `trackToolbar` HBox |

## 4. Object Description & Long-Press

| Feature | Status | Notes |
|---------|--------|-------|
| Long-press timeout configuration | ✗ | Hardcoded 500ms — JavaScout configurable |

## 5. UI / Shell

| Feature | Status | Notes |
|---------|--------|-------|
| Responsive layout (small screen support) | ✗ | JavaScout `SMALL_SCREEN_THRESHOLD` (600px) |
| Double-tap to zoom | ✗ | Candidate feature (not in JavaScout either). No double-tap gesture exists (`map-pan-zoom` covers pan/pinch only). If added later, wire it into the smooth-zoom animation path and the continuous fractional magnification (see `continuous-pinch-zoom`). |

## 6. Rendering

| Feature | Status | Notes |
|---------|--------|-------|
| Track rendering on map | ✗ | |

## 8. GPS / Position

| Feature | Status | Notes |
|---------|--------|-------|
| GPS simulation / dead reckoning when no fix (PositionSimulator) | ✗ | Guess vehicle movement from last position + speed + heading (+ route if navigating) when GPS is stale. Separate service from `LocationService` — never talk to raw GPS directly when there is no fix. See detail below. |

### PositionSimulator — aggregated design notes (from GPS jump investigation)

**Problem:** free driving mode shows random GPS/map jumps on real devices. Root cause: `LocationService` ran Fused AND raw `LocationManager` (GPS/NETWORK/PASSIVE) in parallel on Play Services devices; raw fixes bypassed OS smoothing. Fixed by change `gps-strict-fallback` (Fused only when available). This entry covers the *next* gap: when there is no GPS fix at all.

**Goal:** when GPS is lost, estimate vehicle movement instead of freezing the marker at the last fix (current behavior in free mode).

**State machine:**

```
GPS fresh (acc<50m, age<5s)  →  REAL
GPS stale > N s              →  ESTIMATED (extrapolate)
estimate age > M s / drift > D m → LOST (give up, show signal-lost)
GPS back                     →  REAL
```

**Estimation math:** `position(t) = lastFix + ∫ speed(τ) · heading(τ) dτ`

- heading: free mode → course-over-ground from position history (already implemented in `MapCanvasViewModel` for bearing, low-pass alpha 0.3/0.7); nav mode → route bearing
- speed: filtered GPS speed (150 km/h cap, `filterSpeed` exists in `NavigationViewModel`/`AANavigationController`); AAOS: CAN bus speed via Vehicle HAL later (`AutomotiveDevice` is feature-check only today)
- route: nav mode → snap to route geometry; free mode → heading projection (drifts, must be bounded)

**What already exists (reuse):**
- Native `PositionAgent` (`app/src/main/cpp/libosmscout/libosmscout/src/osmscout/navigation/PositionAgent.cpp:267-355`) dead-reckons along the route at vehicle speed (capped by max speed) — but only in tunnels and only during navigation. Outside tunnels → `NoGpsSignal`, estimate holds.
- `GpsFixQuality` enum (NONE/POOR/GOOD) + `GPS_FIX_FRESHNESS_MS = 5000`, `GPS_FIX_MAX_ACCURACY_M = 50f` in `MapCanvasViewModel` — freshness/accuracy thresholds exist but nothing acts on them.
- Course-over-ground history + smoothed bearing in `MapCanvasViewModel` (`addCoursePoint`/`computeCourseBearing`/`smoothCourseBearing`).

**Open design questions:**
1. Scope: free mode only, or also open-road GPS loss during navigation (native agent only covers tunnels)?
2. Give-up bounds: after N s / M m of estimation → LOST (real apps ~10-30 s).
3. Marker UX: ESTIMATED position visually distinct from REAL (color/opacity)?
4. Where: new Kotlin `@Singleton` service feeding a derived position flow with state (REAL/ESTIMATED/LOST); consumers = marker, center, nav engine, AA.

## 9. Viewport Save — Residual Bug

- **Residual: pre-init viewport key mismatch (`mapPath` vs `"default"`) ℹ**: `MapCanvasViewModel.initMap` loads with `viewportStorage.load(currentMapKey ?: mapPath)` (`MapCanvasViewModel.kt:1408`), while the navigation-end save (`:2167`) and `saveViewport()` (`:2811`) persist with `currentMapKey ?: "default"`. Harmless today because `initMap` sets `currentMapKey` (`:1314`) before the load, so the fallbacks never meet — but any future save/load before `initMap` (or after a failed init) would write and read different files. Fix candidate: share one key helper (`currentMapKey ?: mapPath`) across all three call sites. Found 2026-09-13 while triaging `MapCanvasViewModelNavEndRestoreTest` (change `smooth-decimal-auto-zoom`, task 6.2) — test-only fix there, production untouched.

## 14. Kover deprecation on the Gradle 10 path

- **Kover 0.9.8 emits a Gradle 9.6 deprecation** ⏳: Kover's own internals (`kotlinx.kover.gradle.plugin.appliers.PrepareKoverKt`) add a `Project`-object dependency notation — deprecated in Gradle 9.6, hard failure in Gradle 10. Not fixable from our scripts (we use string notation everywhere). Revisit on Gradle 10 upgrade / newer Kover. See `guidelines/Build.md` §7.

## 22. Gradle 10 deprecation sweep (build-level, pre-upgrade audit)

- **Every build prints “Deprecated Gradle features were used in this build, making it incompatible with Gradle 10” (observed 2026-09-15 on the §17-verified `./gradlew test`)** ⏳: Gradle 9.6.1 tolerates the deprecations, Gradle 10 hard-fails; §14 already tracks Kover's Project-object notation (not fixable from our scripts). Sweep the remaining deprecations once before any Gradle 10 upgrade: `./gradlew test :app:assembleMobileDebug --warning-mode all`, triage the emitted list, and separate plugin-owned warnings (AGP/Kover — expect one each, document and ignore) from our own scripts' (`buildSrc/*.gradle.kts`, root/app/auto/core `*.gradle.kts` — fixable locally). Two adjacent, still-untried items: configuration cache (`--configuration-cache`, Gradle suggests it each build) — verify it against the license-assets Variant-API tasks and the `release` version-state bump (config-time execution) before enabling in CI; and `org.gradle.configuration-cache=true` interplay with the `release` task gating (bump runs at configuration time, which config-cache may re-run per invocation).

## 10. Pending On-Device Verification

| Item | Status | Notes |
|------|--------|-------|
| Vehicle anchor visible-area + per-surface parity (change `anchor-per-surface-visible-area`, tasks 6.3–6.6, 8.1–8.3) | ⏳ | Implementation green (suite 2026-09-15: app mobile/automotive 964 each, auto 373, core 216; incl. `markerRidesTheBlittedContentWithABlitOffset` + `hostPaneClampsTheLeadingEdgeAnchorOnly`). On-device: routing/free-driving anchors stay clear of the overlays (turn card, routing-status card, street-name pill, widget column) at large font scale; browse framing unchanged (identity); car↔phone per-surface anchor values independent; upgraded install keeps its pre-split value until the car gets its own; `center/center` on both surfaces frames identically to the previous build; AA: leading-edge preset clears the host pane (LTR + RTL), vehicle marker + destination pin ride the blitted content without lead-then-snap during extrapolation glides. |
| Continuous pinch zoom — real-device sanity check | ⏳ | Emulator pinch is synthetic input; user confirmed pinch on emulator 2026-08-29 (task 5.2 closed), real-device check remains (task 5.2 tail). Verify: pinch in/out continuity, limits, fractional mag persistence, GPS marker anchor, follow-mode pinch, no FATAL. |

## 12. Regional maps emit unknown-type warnings loading standard.oss

- **Pre-existing, not caused by basemap-own-stylesheet** ℹ: on-device logcat shows ~9159 "Unknown type" warnings per startup from loading `standard.oss` (incl. `include/basemap.oss`, `include/place.oss`, `include/tourism.oss`, `include/natural.oss`) into the REGIONAL map databases (Iceland/NRW/Dortmund on the test emulator). The regional maps lack types standard.oss references: `basemap_boundary_country`, `boundary_municipality`, `boundary_suburb`, `place_ocean`, `place_sea`, `tourism_apartment`, `natural_rock`, etc. Likely the installed maps were imported with an older map.ost (newer types missing) — re-importing with the current submodule should clear most of them. The basemap itself now loads `basemap-render.oss` with zero warnings (change `basemap-own-stylesheet`). Investigate: check map import date/version vs current map.ost; consider whether standard.oss should guard `include/basemap.oss` behind a flag (it already has `IF boundary` for some rules).

## 15. Kover coverage attribution for Robolectric-tested classes

- **Pre-existing tooling gap found during fix-contact-address-resolution (2026-09-13)** ℹ: in the merged and `:app` Kover XML reports, classes exercised only by Robolectric tests show near-zero instruction coverage while their tests pass and assert behaviour — `ContactsRepository` 5 covered/0 missed, `FavoriteRepository` 16/0, `AddressBookSheetKt` 41 missed/0 covered (its four Compose tests pass). Plain-JUnit-covered classes report plausible numbers. Suspected cause: Robolectric loads app classes in its own sandbox classloader, so JaCoCo/Kover exec data is recorded against a different class identity. Investigate: Kover/Robolectric instrumentation options (offline instrumentation, `kover { }` filters, or `robolectric.properties` sandbox config) before trusting any coverage gate. Until then, use the revert-check (new test fails on pre-change code) as coverage evidence instead.

## 21. README build commands are stale

- **Pre-existing, noticed 2026-09-13 during `license-compliance-baseline` task 8.4** ℹ: `README.md` › Build Commands still lists `./gradlew :app:assembleRelease` and `:osmscout-jni:assembleRelease`, and the project-structure tree lists `osmscout-jni/` as a JNI bridge AAR — neither exists: the module is `:osmscout-client-java`, and the app builds per flavor (`:app:assembleMobileDebug`, `:app:assembleAutomotiveDebug`, `./gradlew release` for both AABs). The license additions from this change were added to the same document, so the two stale commands now sit next to correct ones. Fix: replace the stale commands with the flavor-aware ones and correct the structure tree.

## 19. libosmscout license statement is self-inconsistent

- **Found 2026-09-13 during `license-compliance-baseline` task 1.3** ✅ (GPLv2-text part): the submodule `README.md` says "The libraries itself are under LGPL. For details see the LICENSE file", but at the time `app/src/main/cpp/libosmscout/LICENSE` contained the plain GNU GPL v2 text with no Lesser section and no version clause, and no source file carries a license header. The license map records `LGPL-2.1-or-later` (following the README) with an explicit caveat, and `licenses/license-policy.json` lists it under `reviewRequired`. **Resolved 2026-09-17 by `app-license-gpl-3-0-or-later` + upstream**: since 2026-03-06 (upstream commit f4a9dabe7) the submodule LICENSE is a 21-line LGPL + five exceptions/clarifications document with no GPL text; the application license decision was made (GPL-3.0-or-later) and the policy caveat was refreshed accordingly. Open item only: upstream states no license version — raising LGPL 2.1 vs 3 with Framstag stays as `reviewRequired`; the conservative `LGPL-2.1-or-later` mapping remains.

## 20. OpenSpec config rules: add `openspec doctor` to CI

- **Residual hardening after the `config.yaml` rules bug (2026-09-13)** ℹ: three rule sets in `openspec/config.yaml` (proposal/specs/design) were silently dropped — colon+space entries parsed as YAML mappings, the array failed the array-of-strings check; quoting the entries fixed it. No CI guard exists (checked `build.yml`); add `openspec doctor`, or a tasks.md marker-style validation (§17), so a silently dropped rules file fails loudly.

## 23. libosmscout-kotlin port stubs — verify the binding is unused before anyone wires it in

- **Submodule `app/src/main/cpp/libosmscout/libosmscout-kotlin/` carries 7 unfinished port markers (found 2026-09-15)** ℹ: `objecttypes/TypeConfig.kt` skips feature-description handling in `loadFromData` (“TODO Fetch feature”, “TODO: Add description to feature”), `registerType` has “TODO: Calculate wayTypeIdBytes & Co.” plus two “TODO: Fix” lines, and `index/AreaWayIndex.kt:120` has “TODO: Reserve capacity for offsets”. Grep across every `*.gradle*`/`CMakeLists.txt` shows **no module references `libosmscout-kotlin`** — the binding is inert today (the app uses the C++ JNI bridge + `:osmscout-client-java`; a plain-JUnit or Kotlin binding is not on any build path). The submodule is our own fork (`naviveylin-local`), so this is decision material, not urgent: either finish the port to match C++/Java behavior (TypeConfig without feature descriptions would render/query differently), or document the binding as deliberately unbuilt and add a code comment so a future dependency addition fails loudly instead of silently using a stub.

## 24. Sharp-s uppercase form (ẞ U+1E9E) is not folded

- **Known limitation left in place by `fix-sharp-s-transliteration-match` (2026-09-16)** ℹ: the character map row for U+1E9E (capital sharp S) transliterates to itself, so the case-normalized transliterated comparison that change introduced still cannot match a query spelling a name with `ẞ` against an index name spelled `SS`/`ss` (nor the reverse). No name in the NRW or Iceland map databases uses U+1E9E (verified by extracting `location.idx` and counting spellings: `straße` 65 848×, `strasse` 22×, zero `ẞ`), so nothing is unfindable today. Fix candidate: an upstream character-map row that transliterates `ẞ` to `SS` (or `ss`, now equivalent because the comparison is case-normalized) when the table is regenerated. Land upstream when the table is next touched.

## 27. Unconstrained search of a second loaded database returns out-of-area noise

- **Observed 2026-09-16 during `fix-sharp-s-transliteration-match` task 6.5** ℹ: with the map view centered on Iceland and the GPS scope resolved to Regierungsbezirk Arnsberg, the query `Am Birkenbaum 6 Dortmund` returned Iceland-database entries (`Leiðhamrar Dofri`, `Lokinhamrar`, 5,8 km) instead of the Dortmund address. Cause is the documented per-database scope rule: a region handle is database-local, so the database that does *not* own the handle is searched unconstrained (`OSMScoutClient.cpp`, string-search scope comment) and its free-text index answers on short partial tokens ("am" inside "…hamrar…"). Not created by this change — the characters in the returned names (`ð`, `ö`, `í`, `æ`, `á`) are not affected by the transliteration fix, and no pre-change baseline run was made. Investigate: when a scope exists for one database, either skip the other databases' free-text hits or rank them below scoped results (and/or apply a distance limit), so an address query cannot be answered from another map region's data.

## 26. Flaky `BasemapSectionComposeTest.availableShowsDownloadButton`

- **Observed 2026-09-16 during `fix-sharp-s-transliteration-match` task 4.1** ⏳: the first full `./gradlew test` run failed once with `BasemapSectionComposeTest > availableShowsDownloadButton FAILED — android.view.ViewRootImpl$CalledFromWrongThreadException at ViewRootImpl.java:11357` (972 tests, 1 failed). Re-running the class alone (`--tests com.naviveylin.ui.mapmanager.BasemapSectionComposeTest`) passed, and the next full `./gradlew test` was green (972/0/0 for both variants). Unrelated to that change (native-only + gitlink bump; no basemap file in the dirty tree), but a wrong-thread violation indicates a real ordering race in the test (Compose/Robolectric) rather than pure noise. Fix candidate: identify the View access happening off the main thread (probably a `LaunchedEffect`/callback in the basemap section composing a download button) and make the assertion wait for idle instead of racing it.

## 28. Whole-level rounding in `computeAreaZoom` still over-fits area favorites and POI search

- **Found 2026-09-18 during `route-overview-fit` (DPI/`cos(lat)` ground-resolution fix)** ℹ: the shared bbox→magnification helper rounds to whole levels (`Math.round`), which can round the exact fit down by up to half a level, so the fitted content ends up to ~13% larger than the 80%-margin target. The route overview is now protected by an explicit projection check (`routeFitsVisibleArea`, design Decision 8), but the other two callers — the area-favorites zoom (`onFavoriteSelected`, the details/area zoom helper) and the POI/radius search fit (`SearchDialog.poiFitMagnification`) — have no such verification, so their fitted bbox can slightly overflow the mini map / map viewport (previously masked by the over-zoom the ground-resolution fix removed). Fix candidate: round *up* for fit callers (never clip, at the cost of ≤1 level more zoom-out) or reuse the projection check; note that the favorites zoom is additionally clamped to 14–20, which hides the effect for small objects.

## 29. On-device re-check of the area-favorites and POI-search fit zooms

- **Created 2026-09-18 by `route-overview-fit`** ℹ: that change corrected `computeAreaZoom`'s ground resolution (display DPI + Mercator `cos(lat)`), which *intentionally* changes the zoom the phone picks when selecting an area favorite and when the POI search fits its results — both now zoom out to the geometrically correct level (previously over-zoomed by `dpi / 96 * (1 / cos(lat))`, ≈2 levels on a 420-dpi phone). Unit tests stay green, but no on-device/visual check of those two flows is part of that change. Follow-up: pick an area favorite and run a POI radius search on a real phone/emulator and confirm the framing is sane (not too far out, markers inside the visible area).
