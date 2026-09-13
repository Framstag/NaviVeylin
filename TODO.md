# NaviVeylin TODO 

**Legend:** ✗ = missing | ⏳ = in progress / blocked | ✅ = done

---

## 17. Verification-gate blind spots: Gradle test up-to-date masking + OpenSpec task-marker parsing

- **Gradle unit-test tasks report green without executing (found 2026-09-13 while running the archive gate for `fix-address-lookup-accuracy`)** ℹ: `./gradlew test` printed `BUILD SUCCESSFUL in 5s` with `152 actionable tasks: 4 executed, 145 up-to-date` — **zero tests ran**; the verdict came from the up-to-date check against a previous run's output, not from an execution. Two follow-ups that do NOT fix it: `--rerun` is ignored by AGP's unit-test tasks (only plain tasks such as `:osmscout-client-java:test` honour it), and `--rerun` on the aggregate `test` lifecycle task propagates to no dependent task at all. `--rerun-tasks` works but also forces every compile in the graph. What does work: delete the task outputs, e.g. `rm -rf app/build/test-results/testMobileDebugUnitTest app/build/test-results/testAutomotiveDebugUnitTest core/build/test-results/testDebugUnitTest auto/build/test-results/testDebugUnitTest`, then run the four test tasks — the run then reports real execution time (`BUILD SUCCESSFUL in 1m 59s`, 4 executed). Consequence to guard against: any OpenSpec apply/archive evidence of the form "`./gradlew test` → BUILD SUCCESSFUL" may prove nothing about the current tree; a 5-second "success" is the tell. Fix option: a Gradle `check`-wired task or a wrapper in `.pi/skills/run-tests` that clears `test-results` before invoking the suite, and a rule that the evidence line must quote the executed-task count and elapsed time. Also note the up-to-date check is content-hash based, so a file mtime later than the newest `test-results` XML does not by itself prove the run predates the edit — verify content, not timestamps.
- **OpenSpec silently ignores bare numbered task markers (found 2026-09-13)** ℹ: `fix-address-lookup-accuracy/tasks.md` used `1. [x] …`-style items (no `-` bullet); the task parser only recognises `- [x]`, so `openspec list` reported `completedTasks: 0, totalTasks: 0, status: "no-tasks"` for a change that was in fact 14/15 complete — while `openspec status` simultaneously reported `isPlanningComplete: true` / `isComplete: true`, so nothing errored. A change can therefore look stalled (or, read the other way, look finished) with no diagnostic. Fixed for that change by renumbering to `- [x] N.M` under numbered `## N.` headings; the other 13 open changes already used that form. Fix option: validate task-marker style in CI (every `tasks.md` must contain at least one `- [x]`/`- [ ]` item) so the mismatch fails loudly instead of silently zeroing the counts.
- **Aggregate `./gradlew test` is currently blocked by the in-flight license-compliance work (found 2026-09-13)** ℹ: the root `test` lifecycle pulls `:app:generateLicenseAssetsMobileDebug` together with `:app:mergeAutomotiveDebugAssets`, and Gradle 9 rejects the implicit dependency (license-assets output dir `app/build/generated/assets/licenses/mobile/debug` is consumed by the automotive merge without a declared dependency) — `BUILD FAILED` before any test runs. CI does NOT hit this because it invokes the four explicit per-flavor test tasks (`:app:testMobileDebugUnitTest :app:testAutomotiveDebugUnitTest :auto:testDebugUnitTest :core:testDebugUnitTest`), never the aggregate. Belongs to `license-compliance-baseline` (9/31); until then use the explicit four-task form as the regression gate and never cite `./gradlew test` as evidence.

---

## 16. On-device verification pending: fix-zero-distance-to-turn (native step distance)

- **⏳ Pending device run (change `fix-zero-distance-to-turn`, tasks 5.4/6.3)** ✗: the native instruction-distance fix (submodule `3c761d618` on `naviveylin-local`, gitlink bumped at main `195f278`) is implemented and unit-tested (Kotlin state handling + step-index lookup + mapper arrival step — 14 new tests green, full `./gradlew test` green, both flavors all ABIs build). The **native engine path is not verifiable on the host JVM** (the JNI stub .so is symbol-free, so `PositionAgent`/`RouteInstructionAgent`/`GenerateNextRouteInstruction` run only on-device). On next device/emulator session: boot the Automotive AVD, drive a GPX-replay or mock-GPS route with one deviation → reroute, and check via `adb logcat -s NaviVeylin` (plus a temporary `osmscout::log.Debug()` of `distanceTo`/`nodeDist`/`abscissa`): (a) distance counts down and never shows 0 m while the turn is ahead, (b) no routeNode reset-to-begin on a transient forward-search miss, (c) instruction keeps updating while a reroute is suppressed, (d) final step shows "Arrive — <remaining>" after the last turn. Also re-run AA/phone instruction-panel screens at the destination tail — this is where the 0 m freeze appeared.

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

## 7. Android Auto

| Feature | Status | Notes |
|---------|--------|-------|
| MapScreen renderer init off main thread | ✗ | `MapScreen.mapRenderer` lazy init calls `client()` + `getDatabaseBoundingBox()` on the main thread; if warmup is still building the native client, the main thread blocks (delays template delivery + screen coroutines — suspected cause of the favorites screen showing "Loading" for seconds). Make renderer init async ("renderer not ready" state + deferred renders) or move the blocking calls off the main thread. See change `fix-aa-favorites-latency` (A+B) for the favorites data-path fix; this is the separate startup-jank hardening. |

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

- **FIXED (`71e8662`)**: `saveViewport()` on `ON_PAUSE` could persist the default viewport if the app was backgrounded while `initMap` was still suspended at `viewportStorage.load` (the `isValid()` guard passes — the default center is valid-looking; the reorder does not cover this path since `saveViewport` does not go through the renderer). Rare (background within ~100 ms of launch), same bug class as the fixed `fix-viewport-save-race` change. Fix: `viewportRestored` flag set in `initMap` when the restore is applied; `saveViewport()` no-ops (with log line) until set. Regression test `MapCanvasViewModelViewportRestoreTest` (3 tests, green 2026-09-08 via `:app:testMobileDebugUnitTest`). Found 2026-09-06 while adding that test.
- **Residual: pre-init viewport key mismatch (`mapPath` vs `"default"`) ℹ**: `MapCanvasViewModel.initMap` loads with `viewportStorage.load(currentMapKey ?: mapPath)` (`MapCanvasViewModel.kt:1408`), while the navigation-end save (`:2167`) and `saveViewport()` (`:2811`) persist with `currentMapKey ?: "default"`. Harmless today because `initMap` sets `currentMapKey` (`:1314`) before the load, so the fallbacks never meet — but any future save/load before `initMap` (or after a failed init) would write and read different files. Fix candidate: share one key helper (`currentMapKey ?: mapPath`) across all three call sites. Found 2026-09-13 while triaging `MapCanvasViewModelNavEndRestoreTest` (change `smooth-decimal-auto-zoom`, task 6.2) — test-only fix there, production untouched.
- **Residual: initMap re-entry race can re-arm `viewportRestored` with a stale restore** ✗: `initMap` re-entry (MAIN screen after map download/update/delete) re-arms `viewportRestored = false` and cancels `rendererScope`, but cancels *not* the previous initMap's `viewModelScope.launch`. If the old coroutine is still in flight (e.g. suspended at `viewportStorage.load`), it can later set `viewportRestored = true` — and `_uiState.viewport` — AFTER the newer initMap re-armed the flag, so a lifecycle `saveViewport()` or the view-change listener persists the older map's (or default) viewport under the new map key. Low odds (re-entry during restore window), untested. Fix candidate: track the initMap `Job` and cancel it on re-entry, or gate the post-restore assignment on an initMap generation counter. Found 2026-09-08 while verifying the §9 fix (bug 1).

## 14. Build hygiene notes (from add-kover-coverage)

- **Pre-existing Kotlin opt-in warning** ℹ: `app/src/test/java/com/naviveylin/ui/map/MapCanvasViewModelRoadInfoTest.kt:102` — `ExperimentalCoroutinesApi` usage missing `@OptIn`. Not caused by the coverage change; appears in every `testDebugUnitTest` compile. Fix: add the opt-in annotation to the test class. Land with whichever change touches that file next.
- **Kover 0.9.8 emits a Gradle 9.6 deprecation** ⏳: Kover's own internals (`kotlinx.kover.gradle.plugin.appliers.PrepareKoverKt`) add a `Project`-object dependency notation — deprecated in Gradle 9.6, hard failure in Gradle 10. Not fixable from our scripts (we use string notation everywhere). Revisit on Gradle 10 upgrade / newer Kover. See `guidelines/Build.md` §7.

## 10. Pending On-Device Verification

| Item | Status | Notes |
|------|--------|-------|
| Continuous pinch zoom — real-device sanity check | ⏳ | Emulator pinch is synthetic input; user confirmed pinch on emulator 2026-08-29 (task 5.2 closed), real-device check remains (task 5.2 tail). Verify: pinch in/out continuity, limits, fractional mag persistence, GPS marker anchor, follow-mode pinch, no FATAL. |
| AA street-name label vs host ETA card — real head unit | ⏳ | AAOS emulator cannot run full navigation (Fused throttles emulator GPS fixes, no map data, phone-only map-download UI). Emulator proved boot/render only. Verify on user's real head unit: street-name label sits above host ETA card while navigating; free-driving unchanged. |
| Basemap live reload — download/update/delete without restart | ⏳ | Implementation committed (change `fix-basemap-live-reload`, HEAD `c2231ee`); remaining change tasks 7.1–7.5 are on-device. Verify: fresh install → download basemap → return to map → visible WITHOUT restart (`adb logcat -s NaviVeylin` shows "Basemap loaded from ..." + tile re-render logs); delete/update while running re-renders without restart; region-with-no-map shows basemap borders/country names; Android Auto surface refreshes after download. |
| Multi-DB POI search — NRW/Dortmund repro | ✅ | Fixed (submodule `5371eb122`, change `fix-multi-db-poi-search`): results merged across loaded maps — bbox-containing DBs searched first, dedup by rounded coordinates, distance-ascending + limit; verified on-device multi-map (Dortmund first, no dupes) and single-map (same object set, deterministic order). |
| Speed-badge overspeed colors — phone (light+dark) | ⏳ | Change `speed-limit-warning-colors` task 4.4 deferred (2026-09-12): fresh emulator lacks a regional map with speed-limit roads; in-app download UI not automatable headless. Verify on real phone: follow mode + navigation over the limit by 5+ km/h → red-600 fill `#E53935` @0.92 α with white text; normal badge unchanged. |
| Speed-badge overspeed colors — AA (nav + free driving) | ⏳ | Change `speed-limit-warning-colors` task 4.5 deferred (2026-09-12). Verify on real head unit / AA: red badge fill when over the limit in navigation and free driving, semi-transparency retained (0xCC α), no surface draw failures. |

## 11. Lint Report Baseline Drift

- **Lint report counts drift with the dirty tree** ℹ: pre-fix report (2026-08-29) showed 3 errors/80 warnings; after `fix-lint-missing-class` the committed tree shows 0 errors (lint gate green). The +1 `UnusedResources` warning in the interim came from uncommitted i18n work. The tree still carries uncommitted search-related work (`unify-auto-search` in progress) — regenerate lint reports once that lands to get a stable baseline.

## 13. Map rotation stuck after north-up toggle while moving

- **Pre-existing bug found during on-device verify of fix-compass-north-orientation** ✗ (`MapCanvasViewModel` GPS-collect loop, ~L842-875): `onSetNavOrientation(true)` renders angle 0 immediately, but the next GPS fix re-applies `else { if (!lastUsedAngle.isNaN()) lastUsedAngle else 0.0 }` — `lastUsedAngle` still holds the pre-toggle follow angle (−bearing). Result: toggling to north-up WHILE moving (free drive / navigation) keeps the map rotated at the old follow angle instead of 0°, while fixes keep arriving. Repro on emulator: follow west (angle ≈ −270° ≡ +90°), long-press compass → map rotates to 0 for one render, reverts to +89° on next fix; `settings.json` shows `navNorthUp: true` while the map stays rotated. Not compass-related (compass needle correctly tracks the rendered viewport angle; unit-tested). Fix candidate: reset `lastUsedAngle = 0.0` when entering north-up (`onSetNavOrientation(northUp=true)`), or in the collect loop force angle 0 when `isNorthUp` (drop the `lastUsedAngle` fallback for north-up). Add regression test in `MapCanvasViewModel` tests. Found 2026-09-11 via GMS-disabled emulator (LocationManager fallback course bearing) + `adb emu geo fix` westward sequence.

## 12. Regional maps emit unknown-type warnings loading standard.oss

- **Pre-existing, not caused by basemap-own-stylesheet** ℹ: on-device logcat shows ~9159 "Unknown type" warnings per startup from loading `standard.oss` (incl. `include/basemap.oss`, `include/place.oss`, `include/tourism.oss`, `include/natural.oss`) into the REGIONAL map databases (Iceland/NRW/Dortmund on the test emulator). The regional maps lack types standard.oss references: `basemap_boundary_country`, `boundary_municipality`, `boundary_suburb`, `place_ocean`, `place_sea`, `tourism_apartment`, `natural_rock`, etc. Likely the installed maps were imported with an older map.ost (newer types missing) — re-importing with the current submodule should clear most of them. The basemap itself now loads `basemap-render.oss` with zero warnings (change `basemap-own-stylesheet`). Investigate: check map import date/version vs current map.ost; consider whether standard.oss should guard `include/basemap.oss` behind a flag (it already has `IF boundary` for some rules).

## 14. Zoom unit-test failures (resolved in smooth-decimal-auto-zoom)

- **Resolved 2026-09-13** ✅: the three `:app:testMobileDebugUnitTest` failures first seen at HEAD `fe9ae77` were two distinct defects, not pre-existing/unrelated leftovers:
  1. `MapCanvasViewModelAutoZoomCommitTest` (2 tests) still asserted the superseded fixed 0.5-level step and an exact landing on 14.5 (`15.55` vs `15.5`, `13.45` vs `13.5`). The convergence rewrite in `fe9ae77` (`SpeedZoomTable.ZOOM_CONVERGENCE_GAIN = 0.3`, distance-proportional, settling inside the `ZOOM_EPSILON` 0.05 deadband) updated `SpeedZoomTableTest` and `AutoZoomControllerTest` but left the phone-side test stale. Fixed by rewriting both tests to the proportional curve (change `smooth-decimal-auto-zoom`, tasks 6.1/6.4), sharing a direction-aware convergence helper.
  2. `MapCanvasViewModelNavEndRestoreTest.browseBeforeNavAppliesBrowseRepresentationKeepingViewport` (`persisted zoom is the routing-end zoom`, `13.0` vs `-1.0`) was a test-determinism race, not a production bug: the test left `ViewportStorage.ioDispatcher` at `Dispatchers.IO`, so `advanceUntilIdle()` could not await the navigation-end save and the test's `load("default")` raced the write — passing when the class ran alone, failing in full-suite runs. Fixed by pointing the storage dispatcher at the test dispatcher (change `smooth-decimal-auto-zoom`, task 6.2).
- Duplicate section number ℹ: this heading shares `## 14.` with "Build hygiene notes (from add-kover-coverage)" above. Pre-existing and harmless; renumber when either section is next touched.

## 15. Kover coverage attribution for Robolectric-tested classes

- **Pre-existing tooling gap found during fix-contact-address-resolution (2026-09-13)** ℹ: in the merged and `:app` Kover XML reports, classes exercised only by Robolectric tests show near-zero instruction coverage while their tests pass and assert behaviour — `ContactsRepository` 5 covered/0 missed, `FavoriteRepository` 16/0, `AddressBookSheetKt` 41 missed/0 covered (its four Compose tests pass). Plain-JUnit-covered classes report plausible numbers. Suspected cause: Robolectric loads app classes in its own sandbox classloader, so JaCoCo/Kover exec data is recorded against a different class identity. Investigate: Kover/Robolectric instrumentation options (offline instrumentation, `kover { }` filters, or `robolectric.properties` sandbox config) before trusting any coverage gate. Until then, use the revert-check (new test fails on pre-change code) as coverage evidence instead.

## 18. Non-SPDX license on the Play Services artifacts

- **Found 2026-09-13 during `license-compliance-baseline` task 1.4** ⏳: the four `com.google.android.gms` components in the SBOM (`play-services-base`, `play-services-basement`, `play-services-location`, `play-services-tasks`) carry a **name-only** license value — `"Android Software Development Kit License"` with `url: https://developer.android.com/studio/terms.html` — not an SPDX identifier. The change's spec requires every distributed component to resolve to an SPDX identifier and forbids assertion-less license values, so this case needs a modelling decision before the license gate (tasks 4.x) and the license-text generation (tasks 5.x/7.x) can pass: `LicenseRef-…` plus a link-only entry, `LicenseRef-…` plus vendored terms text, or an explicit named exception in the policy file. Blocked on that decision; the rest of the native half is already modelled (see `licenses/`).

## 19. libosmscout license statement is self-inconsistent

- **Found 2026-09-13 during `license-compliance-baseline` task 1.3** ✗: the submodule `README.md` says "The libraries itself are under LGPL. For details see the LICENSE file", but `app/src/main/cpp/libosmscout/LICENSE` contains the plain GNU GPL v2 text with no Lesser section and no version clause, and no source file carries a license header. The license map records `LGPL-2.1-or-later` (following the README) with an explicit caveat, and `licenses/license-policy.json` lists it under `reviewRequired`. The submodule is pushed to our own fork (`naviveylin-local`), so upstreaming an accurate `LICENSE`/header statement is possible; it is not part of this change. Needs the application license decision and, ideally, a clarification upstream.

## 20. OpenSpec silently ignores `config.yaml` rules for proposal/specs/design

- **Found 2026-09-13 while scaffolding `license-compliance-baseline`** ℹ: `openspec instructions <artifact>` prints `Rules for 'proposal' must be an array of strings, ignoring this artifact's rules` (same for `specs` and `design`) — the entries in `openspec/config.yaml` are written as a YAML block sequence, but three of them contain a colon followed by a space (lines 32, 41, 50), so YAML parses them as mappings and the whole array fails the array-of-strings check. Consequence: those three rule sets have never been applied to any change in this repository (the `tasks`/`apply`/`archive` sets are unaffected). Fix candidates: quote every rules entry, or use a mapping-free style; then verify by re-running `openspec instructions proposal --change <any> --json` and confirming the rules appear instead of the warning. Also worth a `openspec doctor` check in CI so a silently dropped rules file fails loudly.
