# NaviVeylin TODO 

**Legend:** ✗ = missing | ⏳ = in progress / blocked | ✅ = done

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

## 11. Lint Report Baseline Drift

- **Lint report counts drift with the dirty tree** ℹ: pre-fix report (2026-08-29) showed 3 errors/80 warnings; after `fix-lint-missing-class` the committed tree shows 0 errors (lint gate green). The +1 `UnusedResources` warning in the interim came from uncommitted i18n work. The tree still carries uncommitted search-related work (`unify-auto-search` in progress) — regenerate lint reports once that lands to get a stable baseline.

## 13. Map rotation stuck after north-up toggle while moving

- **Pre-existing bug found during on-device verify of fix-compass-north-orientation** ✗ (`MapCanvasViewModel` GPS-collect loop, ~L842-875): `onSetNavOrientation(true)` renders angle 0 immediately, but the next GPS fix re-applies `else { if (!lastUsedAngle.isNaN()) lastUsedAngle else 0.0 }` — `lastUsedAngle` still holds the pre-toggle follow angle (−bearing). Result: toggling to north-up WHILE moving (free drive / navigation) keeps the map rotated at the old follow angle instead of 0°, while fixes keep arriving. Repro on emulator: follow west (angle ≈ −270° ≡ +90°), long-press compass → map rotates to 0 for one render, reverts to +89° on next fix; `settings.json` shows `navNorthUp: true` while the map stays rotated. Not compass-related (compass needle correctly tracks the rendered viewport angle; unit-tested). Fix candidate: reset `lastUsedAngle = 0.0` when entering north-up (`onSetNavOrientation(northUp=true)`), or in the collect loop force angle 0 when `isNorthUp` (drop the `lastUsedAngle` fallback for north-up). Add regression test in `MapCanvasViewModel` tests. Found 2026-09-11 via GMS-disabled emulator (LocationManager fallback course bearing) + `adb emu geo fix` westward sequence.

## 12. Regional maps emit unknown-type warnings loading standard.oss

- **Pre-existing, not caused by basemap-own-stylesheet** ℹ: on-device logcat shows ~9159 "Unknown type" warnings per startup from loading `standard.oss` (incl. `include/basemap.oss`, `include/place.oss`, `include/tourism.oss`, `include/natural.oss`) into the REGIONAL map databases (Iceland/NRW/Dortmund on the test emulator). The regional maps lack types standard.oss references: `basemap_boundary_country`, `boundary_municipality`, `boundary_suburb`, `place_ocean`, `place_sea`, `tourism_apartment`, `natural_rock`, etc. Likely the installed maps were imported with an older map.ost (newer types missing) — re-importing with the current submodule should clear most of them. The basemap itself now loads `basemap-render.oss` with zero warnings (change `basemap-own-stylesheet`). Investigate: check map import date/version vs current map.ost; consider whether standard.oss should guard `include/basemap.oss` behind a flag (it already has `IF boundary` for some rules).
