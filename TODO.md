# NaviVeylin TODO 

**Legend:** ✗ = missing | ⏳ = in progress / blocked | ✅ = done

---


## 29. AA follow jumping — open points handed over (2026-09-17, stable-version handover)

Status at handover: the reported "map and marker jump up and back, sub-second, only while moving" was
traced to four separate defects; three are fixed and verified, one (P3) is implemented and installed but
never judged on device in a *moving* regime. Everything below is for the next session. The two changes
carry the detail: `openspec/changes/overlay-projects-against-displayed-frame` (19/23) and
`openspec/changes/aa-follow-framing-and-zoom-parity` (13/27).

**What was fixed (do not re-investigate):**

| # | Defect | Fix | Evidence |
|---|--------|-----|----------|
| 1 | Overlays projected against the PENDING render target, so the marker detaches from its road between a viewport write and the render commit | displayed-frame accessors (`displayedLat/Lon/Mag/Angle`) used by marker + pin; blit offset published under `surfaceLock`; frame snapshot per render | 4 tests fail pre-change; live divergence `frame … mag=13,08` vs `pending … mag=13,00` |
| 2 | The follow commit re-anchored on the RAW FIX, stepping the whole scene by the extrapolation lead once per fix | P1: `reengageFollow` anchors on `markerPosition()` | `followReanchorUsesTheDisplayedPosition` fails pre-change; 4.3 px mean / 13.1 px max step measured |
| 3 | A fresh render was drawn UNSHIFTED (`drawToSurface`) while the display advanced during the 25-300 ms render, so every commit moved the map ~5-7 px and the next blit moved it back | both draw paths use one placement rule (offset that puts the display on the anchor) + `roundToInt` instead of truncating | drawn-frame continuity: JUMP −7.4 px -> +0.7 px per commit, rms 0.58 px over 165 frames |
| 4 | The committed fractional magnification was reset to the INTEGER viewport level on every fix without a new zoom target, so the map breathed in/out | `ViewportState.zoomFraction` + all five commit sites | `viewportStateCarriesTheFractionalMagnification`; live `pending mag=13,00` vs `frame mag=13,08` |

**Open points, in the order I would pick them up:**

1. **⏳ Remove the temporary traces first** (`aa-follow-framing-and-zoom-parity` task 10.3): `AutoMapRenderer` still logs one line per tick (`AutoMapRenderer: tick`) and per drawn frame (`draw-blit`, `draw-render`) — ~11 Hz of logcat spam, and the `draw-*` lines were the instrument that found defect 3. Reduce to the throttled follow line; keep the frame-continuity assertion if it can be checked cheaply per commit (see the method in item 6).
2. **⏳ Judge P3 on device while MOVING** (tasks 7.1-7.4, 9.2): P3 (displayed magnification easing across frames, `ZOOM_EASE_TAU_SEC` 0.25, cap `ZOOM_BLIT_LIMIT` 0.25 mag, scale about the follow anchor) is installed but was only ever observed at a crawl. Also verify defect 1's and 3's measurements in the driving regime (all three were measured at 50-90 km/h, 50-70 km/h and mag 14.7-16 respectively, so the regime matters).
3. **⏳ P2 (heading commit deadband): RECOMMEND DROPPING IT.** Measured heading motion is ~0.9 deg/s (near-continuous), so a deadband would make the rotation *steppier* (1.5 deg jumps every few seconds) to save full renders — the wrong trade for a change about smoothness. The ~1 full render per fix it targets exists because a rotation cannot be served by a blit; item 4 addresses that without the smoothness cost. If the render rate turns out to matter for battery, implement item 4 instead of P2. The task list (2.1-2.3) should be re-scoped or closed when this decision is taken.
4. **⏳ P4 (smooth heading-up between fixes) — mechanism recorded, deliberately deferred** (design D4 of the follow-up change): rotate the canvas about the follow anchor by the accumulated heading residual. Bound: the exposed corner sliver is ~`r·θ` with `r` up to ~1000 px on a 1080x600 surface, i.e. ~1.5-2 deg of accumulated rotation before the overrun margin (108/60 px) is exceeded; cost: a filtered 1296x720 rotate per tick on a loop already measured at ~11 Hz (§19); risk: resampling softness and composing the overlay rotation with the marker's own bearing rotation. Revisit only if rotation stepping is still visible after P3.
5. **⏳ The auto-zoom's FIRST commit jumps straight to the speed target** — measured `mag 13.000 -> 17.000` in ONE frame (16x scale) on entering free driving. This is spec'd behaviour (`AutoSpeedZoom`/`AutoZoomController`: "the first commit jumps directly to the target … no easing from the default map zoom"), so it needs a *decision*, not a bug fix. Note for whoever takes it: P3's transition cannot smooth it as-is, because for a zoom-IN the frame carries the *new* magnification and reaching the old displayed one would need the buffer scaled below the overrun limit — the phone solves this by queueing the render while the animation plays (`smooth-zoom`: "the debounced native render SHALL be queued while the animation plays"), i.e. render at the old magnification first and let the displayed scale animate upward.
6. **ℹ Device-verification method (do not re-derive):**
   - full renders = `adb logcat -d -t 2000 | grep -c 'lock OK'` (`blitToSurface` logs nothing, so `lock OK` counts ONLY full native renders); fixes = `grep -c 'FreeDrivingScreen: GPS fix'`. Baseline before the fixes: 101 renders / 105 fixes in 104 s.
   - frame placement = log `dy` + the frame centre + the display per drawn frame, then per consecutive pair compute `(Δdy + Δframe_px) − Δdisp_px` (the display's advance IS the expected content scroll); rms should stay sub-pixel, and any commit step shows as a spike. This is the check that found defect 3.
   - the AVD is `Automotive_Distant_Display_with_Google_Play` (AAOS, x86_64, API 33, 1080x600 main + virtual distant displays); the app id is `com.framstag.naviveylin`; the debug APK is `testOnly`, so install with `adb install -r -t`; build it with `-Pandroid.injected.build.abi=x86_64` (an arm64-only APK will not install); relaunch with `adb shell am start -n com.framstag.naviveylin/androidx.car.app.activity.CarAppActivity` and then tap Free driving (the car UI is not reachable from `uiautomator`).
   - the emulator console has NO `geo gpx`; `geo fix` / `geo nmea` injections are IGNORED by whatever feeds this AVD (the feed reports `bearing+360` at times), so a straight-line measurement must be produced by controlling the feed itself, not by injecting.
7. **ℹ `Double.roundToInt()` THROWS on NaN** (Kotlin, unlike Java's `Math.round`): a fresh render before the first GPS fix produced NaN offsets, the exception hit `reportSurfaceFailure`, the surface was marked failed and the display gate closed — 7 tests went red and, on device, the first frame after a screen switch could have killed the surface. Fixed with `roundOffset()` in both draw paths; worth remembering for any arithmetic on possibly-NaN projection output.
8. **⏳ Archive both changes before they are lost:** `openspec/changes/*/` is gitignored, so the change artifacts exist only in this working tree, and the tree is uncommitted (80 dirty files, last commit `45548d4`). Archive (`/openspec-archive-change`) or commit deliberately — the code changes from both changes plus the peer's `street-name-host-views` work are all uncommitted together.
9. **⏳ Remaining tasks in the two changes** (state at handover, some may already be satisfied by the suite runs recorded here — re-check before doing them):
   - `overlay-projects-against-displayed-frame` 19/23: 7.1-7.4 (device: render-rate vs fixes, marker holds its road pixel, navigation view, surface lifecycle).
   - `aa-follow-framing-and-zoom-parity` 13/27: 1.3 (screen fix-path checks), 2.1-2.3 (P2 — see item 3), 4.1 (diagnostics), 5.1-5.3 (tests: the P3 tests exist; the deadband ones depend on item 3), 6.3 (the four per-flavor test tasks: the last four-task run (mobile 975 / automotive 975 / auto / core 231, all green) PREDATES P3 — `:auto` alone is green after it at 443 tests, so re-run the four before archiving), 7.1-7.4 + 9.2 (device), 8.1 (MapRendering §1.1: the displayed-frame wording is written from the first change; the follow-target/P3 wording is not), 10.3 (item 1).
   - Other in-flight changes' tails (source of truth: `openspec list`): `fix-zero-distance-to-turn` 19/21, `fix-auto-navigation-dead-end` 11/12, `background-navigation-notification` 16/23, `browse-drive-modes` 20/23, `auto-pan-during-navigation` 14/16, `street-name-host-views` 27/30 (peer session, device-only left).

---

## 28. Intermittent `AutoMapRendererTest` projection-test failures (street-name-host-views 8.4)

- **Observed 2026-09-17 during `street-name-host-views` task 8.4** ⏳: one full-class run of `AutoMapRendererTest` failed 4 projection tests (`markerStaysOnItsContentWhileAFollowReanchorIsPending`, `markerUsesTheDisplayedFrameRotation`, `followBlitServedForANonCenterAnchor`, `destinationPinProjectsAgainstTheDisplayedFrame`) with deterministic-looking assertion diffs (e.g. marker x 30.8 vs 50.0). Every individual rerun passed, and the full `:auto:testDebugUnitTest` suite (with the identical tree) passed green. Those 4 tests belong to the in-flight `overlay-projects-against-displayed-frame` work (peer agent applied it to `AutoMapRenderer.kt`/`AutoMapRendererTest.kt` the same day). The later `EOFException`/`NoSuchFileException: binary/in-progress-results-generic.bin` crashes in `./gradlew test` and the four-task run are the §18 concurrent-build collision — confirmed by the peer: two Gradle clients were writing the same `test-results/**/binary/*.bin` (my `rm -rf */build/test-results/...` at ~17:25 ran while the peer's build was active). Do not treat any of it as evidence of a street-pill regression.

---

## 19. Findings from `overlay-projects-against-displayed-frame` (2026-09-17)

Findings detected while fixing the AA follow overlay projection; all out of that change's scope
(it deliberately keeps the follow re-anchor cadence and the overlay frame bookkeeping only).

- **✅ Follow re-anchor targets the raw fix, not the displayed position — FIXED 2026-09-17** (change `aa-follow-framing-and-zoom-parity`, P1): `reengageFollow` now anchors the render target on `markerPosition()` (the displayed/eased position), falling back to the raw fix before the first display frame; `followReanchorUsesTheDisplayedPosition` fails on the pre-change code. The original finding: `FreeDrivingScreen.onGpsFix` and `NavigationScreen` run `setViewport(...)` + `reengageFollow()` on every fix, and `reengageFollow` anchored the render target on the raw fix while the displayed position was up to `v·τ` ahead — so the whole scene stepped by that lead at every fix (measured 4.3 px mean / 13.1 px max at mag 15-16 on device). Remaining follow-up in the same area: the per-fix commit itself (the screens' `setViewport` + `reengageFollow` pair) is still the commit cadence — the phone instead lets the display loop own the centre (design D1 Alt B in that change).
- **⏳ `reengageFollow` relies on a preceding `setViewport` to have requested the render** ✗: it writes `viewportLat/Lon` + `emitViewportState()` but never calls `requestRender()`; it works only because every current call site calls it immediately after `setViewport` (which does request). A caller that re-engages follow on its own would silently keep the stale frame on the surface. Fix option: request the render in `reengageFollow` when the target actually moved.
- **✅ `fullRender` reads the render target twice — FIXED 2026-09-17** (change `aa-follow-framing-and-zoom-parity`, task 3.3): it now snapshots the frame parameters once and publishes that snapshot as the committed frame (`overrunLat/Lon/Mag/Angle`), verified by `committedFrameIsLabeledWithTheRenderedParameters` with a new `FakeAutoRenderClient.onRender` hook (the target is mutated *while* the render is in flight). The original finding: the native render centre and the committed-frame label were read separately, so a viewport write landing in between (the extrapolation clamp runs every 200 ms while clamped) mislabelled the frame every overlay projects against.
- **ℹ Extrapolation loop measured ~11 Hz, not the nominal 30 Hz** (`EXTRAPOLATION_FRAME_MS = 33`): in the 104 s AA window, 38 diagnostic lines at one line per 30 ticks is ~1140 ticks / 104 s ≈ 11 Hz. Each tick locks the shared surface and draws the full 1296×720 overrun bitmap plus the overlays, so the period is dominated by the draw — measure before tuning the constant (a smaller period would not raise the rate).
- **ℹ Diagnostics use the default locale:** `"%.6f".format(...)` in the AA follow log and the `Diag/MAP` render lines prints decimal commas on a German device (`51,513637`), so those lines are not machine-parseable. Use `Locale.ROOT` for diagnostic formatting.
- **✅ `:auto`/`:app` test-fork failures (`NoSuchFileException` / `EOFException` on `binary/in-progress-results-generic.bin`, §18) — cause CONFIRMED 2026-09-17** ⏳: a second agent session ran the §17 workaround (`rm -rf <module>/build/test-results/<task>`) in this project while another Gradle invocation had the same test task in flight. The deleting session confirmed it, and the signatures match exactly: the victim's fork writes `results-generic.bin`, the directory is removed underneath it, and the task then either cannot open the in-progress file (`NoSuchFileException`) or reads a truncated binary (`EOFException`) — with no assertion failures and no per-class XMLs. Three occurrences on 2026-09-17 (`:auto:testDebugUnitTest` 19:19, `:app:testAutomotiveDebugUnitTest` 19:22, `:app:testMobileDebugUnitTest` 19:39). Recovery: re-run the task standalone after the `rm -rf` — `:app:testMobileDebugUnitTest` then ran 136 classes green in 4m 13s. **Rule for parallel sessions: never delete `*/build/test-results/**` while another Gradle invocation may be live in the same project — coordinate first.** The deletion is only needed to defeat §17's masking when a task's inputs did NOT change; otherwise the up-to-date check already re-runs the task.

---

## 18. Test-infra flakes found during `fix-aa-vehicle-anchor-not-applied` (2026-09-17)

- **`:auto` full-suite flake — `AutoMapRendererTest.marginRenderRequestHonoursThrottleParity`** ⏳: fails intermittently in full `:auto:testDebugUnitTest` runs (assertion at line 588), passes standalone 3/3. Belongs to the in-flight `fix-follow-vehicle-jumps` WIP (`AutoMapRenderer.kt` is modified in the working tree by that change; this change never touches the renderer). Rerun the suite to confirm; land the fix in that change, not here.
- **`:app:testAutomotiveDebugUnitTest` full-suite fork crash — observed, NOT reproducible 2026-09-17** ✅: a whole-suite run died inside the test fork (Gradle reported `NoSuchFileException: binary/in-progress-results-generic.bin` then `java.io.EOFException` on the binary results; zero or stale per-class XMLs, **no assertion failures**). Follow-up: every package slice passed individually, and a clean full run after `rm -rf` of `app/build/test-results/testAutomotiveDebugUnitTest` passed 136/136 (`BUILD SUCCESSFUL`, 1m47s); the aggregate `./gradlew test` passed (mobile executed fresh) and a final `--no-build-cache` four-task run passed (both app flavors executed fresh, 3m50s, `exit=0`, no failure line). Treat as flaky infra (fork JVM instability), not a code defect; `SpeedSanityTest.kt` was suspected via a doc-comment grep hit but is innocent (loads no JNI stub). Land any re-occurrence investigation in the owning change; evidence chain in TODO §17's strictness rules.

## 17. Verification-gate blind spots: Gradle test up-to-date masking + OpenSpec task-marker parsing

- **Gradle unit-test tasks report green without executing (found 2026-09-13 while running the archive gate for `fix-address-lookup-accuracy`)** ℹ: `./gradlew test` printed `BUILD SUCCESSFUL in 5s` with `152 actionable tasks: 4 executed, 145 up-to-date` — **zero tests ran**; the verdict came from the up-to-date check against a previous run's output, not from an execution. Two follow-ups that do NOT fix it: `--rerun` is ignored by AGP's unit-test tasks (only plain tasks such as `:osmscout-client-java:test` honour it), and `--rerun` on the aggregate `test` lifecycle task propagates to no dependent task at all. `--rerun-tasks` works but also forces every compile in the graph. What does work: delete the task outputs, e.g. `rm -rf app/build/test-results/testMobileDebugUnitTest app/build/test-results/testAutomotiveDebugUnitTest core/build/test-results/testDebugUnitTest auto/build/test-results/testDebugUnitTest`, then run the four test tasks — the run then reports real execution time (`BUILD SUCCESSFUL in 1m 59s`, 4 executed). Consequence to guard against: any OpenSpec apply/archive evidence of the form "`./gradlew test` → BUILD SUCCESSFUL" may prove nothing about the current tree; a 5-second "success" is the tell. Fix option: a Gradle `check`-wired task or a wrapper in `.pi/skills/run-tests` that clears `test-results` before invoking the suite, and a rule that the evidence line must quote the executed-task count and elapsed time. Also note the up-to-date check is content-hash based, so a file mtime later than the newest `test-results` XML does not by itself prove the run predates the edit — verify content, not timestamps.
- **OpenSpec silently ignores bare numbered task markers (found 2026-09-13)** ℹ: `fix-address-lookup-accuracy/tasks.md` used `1. [x] …`-style items (no `-` bullet); the task parser only recognises `- [x]`, so `openspec list` reported `completedTasks: 0, totalTasks: 0, status: "no-tasks"` for a change that was in fact 14/15 complete — while `openspec status` simultaneously reported `isPlanningComplete: true` / `isComplete: true`, so nothing errored. A change can therefore look stalled (or, read the other way, look finished) with no diagnostic. Fixed for that change by renumbering to `- [x] N.M` under numbered `## N.` headings; the other 13 open changes already used that form. Fix option: validate task-marker style in CI (every `tasks.md` must contain at least one `- [x]`/`- [ ]` item) so the mismatch fails loudly instead of silently zeroing the counts.
- **Aggregate `./gradlew test` was blocked by the license-assets wiring (found 2026-09-13)** ℹ: the root `test` lifecycle pulls `:app:generateLicenseAssetsMobileDebug` together with `:app:mergeAutomotiveDebugAssets`; Gradle 9 rejected the implicit dependency (license-assets output dir `app/build/generated/assets/licenses/mobile/debug` consumed by the automotive merge without a declared dependency) — `BUILD FAILED` before any test ran. License task 5.1 has since moved the assets through the Variant API (`addGeneratedSourceDirectory`), which declares the dependency — **verified 2026-09-15: the aggregate now executes**: `./gradlew test` → `BUILD SUCCESSFUL in 2m 56s`, `181 actionable tasks: 27 executed, 8 from cache, 146 up-to-date` — real execution (the four test tasks ran and their results were consumed), so the Variant-API dependency declaration fixed the implicit-dep rejection. Regression gate remains the four explicit per-flavor test tasks (`:app:testMobileDebugUnitTest :app:testAutomotiveDebugUnitTest :auto:testDebugUnitTest :core:testDebugUnitTest`); never cite `./gradlew test` as evidence without an executed-task count + elapsed time (first bullet).

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

## 14. Build hygiene notes (from add-kover-coverage)

- **Pre-existing Kotlin opt-in warning** ℹ: `app/src/test/java/com/naviveylin/ui/map/MapCanvasViewModelRoadInfoTest.kt:102` — `ExperimentalCoroutinesApi` usage missing `@OptIn`. Not caused by the coverage change; appears in every `testDebugUnitTest` compile. Fix: add the opt-in annotation to the test class. Land with whichever change touches that file next.
- **Kover 0.9.8 emits a Gradle 9.6 deprecation** ⏳: Kover's own internals (`kotlinx.kover.gradle.plugin.appliers.PrepareKoverKt`) add a `Project`-object dependency notation — deprecated in Gradle 9.6, hard failure in Gradle 10. Not fixable from our scripts (we use string notation everywhere). Revisit on Gradle 10 upgrade / newer Kover. See `guidelines/Build.md` §7.

## 22. Gradle 10 deprecation sweep (build-level, pre-upgrade audit)

- **Every build prints “Deprecated Gradle features were used in this build, making it incompatible with Gradle 10” (observed 2026-09-15 on the §17-verified `./gradlew test`)** ⏳: Gradle 9.6.1 tolerates the deprecations, Gradle 10 hard-fails; §14 already tracks Kover's Project-object notation (not fixable from our scripts). Sweep the remaining deprecations once before any Gradle 10 upgrade: `./gradlew test :app:assembleMobileDebug --warning-mode all`, triage the emitted list, and separate plugin-owned warnings (AGP/Kover — expect one each, document and ignore) from our own scripts' (`buildSrc/*.gradle.kts`, root/app/auto/core `*.gradle.kts` — fixable locally). Two adjacent, still-untried items: configuration cache (`--configuration-cache`, Gradle suggests it each build) — verify it against the license-assets Variant-API tasks and the `release` version-state bump (config-time execution) before enabling in CI; and `org.gradle.configuration-cache=true` interplay with the `release` task gating (bump runs at configuration time, which config-cache may re-run per invocation).

## 10. Pending On-Device Verification

| Item | Status | Notes |
|------|--------|-------|
| Vehicle anchor visible-area + per-surface parity (change `anchor-per-surface-visible-area`, tasks 6.3–6.6, 8.1–8.3) | ⏳ | Implementation green (suite 2026-09-15: app mobile/automotive 964 each, auto 373, core 216; incl. `markerRidesTheBlittedContentWithABlitOffset` + `hostPaneClampsTheLeadingEdgeAnchorOnly`). On-device: routing/free-driving anchors stay clear of the overlays (turn card, routing-status card, street-name pill, widget column) at large font scale; browse framing unchanged (identity); car↔phone per-surface anchor values independent; upgraded install keeps its pre-split value until the car gets its own; `center/center` on both surfaces frames identically to the previous build; AA: leading-edge preset clears the host pane (LTR + RTL), vehicle marker + destination pin ride the blitted content without lead-then-snap during extrapolation glides. |
| Continuous pinch zoom — real-device sanity check | ⏳ | Emulator pinch is synthetic input; user confirmed pinch on emulator 2026-08-29 (task 5.2 closed), real-device check remains (task 5.2 tail). Verify: pinch in/out continuity, limits, fractional mag persistence, GPS marker anchor, follow-mode pinch, no FATAL. |
| AA street-name label vs host ETA card — real head unit | ⏳ | AAOS emulator cannot run full navigation (Fused throttles emulator GPS fixes, no map data, phone-only map-download UI). Emulator proved boot/render only. Verify on user's real head unit: street-name label sits above host ETA card while navigating; free-driving unchanged. |
| Basemap live reload — download/update/delete without restart | ⏳ | Implementation committed (change `fix-basemap-live-reload`, HEAD `c2231ee`); remaining change tasks 7.1–7.5 are on-device. Verify: fresh install → download basemap → return to map → visible WITHOUT restart (`adb logcat -s NaviVeylin` shows "Basemap loaded from ..." + tile re-render logs); delete/update while running re-renders without restart; region-with-no-map shows basemap borders/country names; Android Auto surface refreshes after download. |
| Speed-badge overspeed colors — AA (nav + free driving) | ⏳ | Change `speed-limit-warning-colors` task 4.5 deferred (2026-09-12); phone counterpart (task 4.4) verified on a real phone 2026-09-14. Verify on real head unit / AA: red badge fill when over the limit in navigation and free driving, semi-transparency retained (0xCC α), no surface draw failures. |
| AA renderer init off main thread — session ordering | ⏳ | Implementation landed (change `fix-aa-renderer-init-off-main`, tasks 1–4: RendererGate, async init in MapScreen/Navigation/FreeDriving, MapPanHandler supplier; :auto + :app suites green, automotive debug APK boots on AVD). Remaining change tasks 5.1/5.2 are the car-host session check: `adb logcat -s NaviVeylin` shows template delivered before the renderer-ready log, warmup client build timestamps on `Dispatchers.Default`, first map frame on surface delivery, cold-start favorites without multi-second "Loading", and destroy mid-init leaves no lingering renderer lines. |

## 11. Lint Report Baseline Drift

- **Lint report counts drift with the dirty tree** ℹ: pre-fix report (2026-08-29) showed 3 errors/80 warnings; after `fix-lint-missing-class` the committed tree shows 0 errors (lint gate green). The +1 `UnusedResources` warning in the interim came from uncommitted i18n work. The tree still carries uncommitted search-related work (`unify-auto-search` in progress) — regenerate lint reports once that lands to get a stable baseline.

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

## 25. Pre-existing unused-variable warnings in the JNI bridge

- **Noticed 2026-09-16 during `fix-sharp-s-transliteration-match` (full native test build)** ℹ: `libosmscout-client-java/src/OSMScoutClient.cpp:1266-1270` sets `gpsMarkerHasBearing`, `gpsMarkerLat`, `gpsMarkerLon`, `gpsMarkerBearing`, `gpsMarkerAccuracy` without reading them — GCC reports five `-Wunused-but-set-variable` warnings per native build (`libosmscout_client_java`), unrelated to the transliteration fix (which compiles warning-free). Fix: either use the locals (they look like a half-finished GPS-marker path) or delete them; land with whichever change touches the bridge's marker code next. The Android app build is unaffected (NDK clang does not warn on these).

## 27. Unconstrained search of a second loaded database returns out-of-area noise

- **Observed 2026-09-16 during `fix-sharp-s-transliteration-match` task 6.5** ℹ: with the map view centered on Iceland and the GPS scope resolved to Regierungsbezirk Arnsberg, the query `Am Birkenbaum 6 Dortmund` returned Iceland-database entries (`Leiðhamrar Dofri`, `Lokinhamrar`, 5,8 km) instead of the Dortmund address. Cause is the documented per-database scope rule: a region handle is database-local, so the database that does *not* own the handle is searched unconstrained (`OSMScoutClient.cpp`, string-search scope comment) and its free-text index answers on short partial tokens ("am" inside "…hamrar…"). Not created by this change — the characters in the returned names (`ð`, `ö`, `í`, `æ`, `á`) are not affected by the transliteration fix, and no pre-change baseline run was made. Investigate: when a scope exists for one database, either skip the other databases' free-text hits or rank them below scoped results (and/or apply a distance limit), so an address query cannot be answered from another map region's data.

## 26. Flaky `BasemapSectionComposeTest.availableShowsDownloadButton`

- **Observed 2026-09-16 during `fix-sharp-s-transliteration-match` task 4.1** ⏳: the first full `./gradlew test` run failed once with `BasemapSectionComposeTest > availableShowsDownloadButton FAILED — android.view.ViewRootImpl$CalledFromWrongThreadException at ViewRootImpl.java:11357` (972 tests, 1 failed). Re-running the class alone (`--tests com.naviveylin.ui.mapmanager.BasemapSectionComposeTest`) passed, and the next full `./gradlew test` was green (972/0/0 for both variants). Unrelated to that change (native-only + gitlink bump; no basemap file in the dirty tree), but a wrong-thread violation indicates a real ordering race in the test (Compose/Robolectric) rather than pure noise. Fix candidate: identify the View access happening off the main thread (probably a `LaunchedEffect`/callback in the basemap section composing a download button) and make the assertion wait for idle instead of racing it.
