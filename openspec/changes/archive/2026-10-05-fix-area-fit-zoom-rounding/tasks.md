# Tasks

> **Revert-check convention** (`TODO.md` §113): every task that adds an invariant also names the one mutation
> that must make a named case fail. One mutation per check — a combined mutation makes the assertion vacuous.
> Read the executed-task count and elapsed time from `test-results/*.xml`; a 5-second `BUILD SUCCESSFUL` is
> not a verdict (`TODO.md` §17).

## 1. Shared fit-verification seam

- [x] 1.1 Add `app/src/main/java/com/naviveylin/ui/map/FitVerification.kt` with `internal fun fitsVisibleArea(...)`
  — bbox, **camera center** (the geo point that will sit at the canvas center), magnification, width, height,
  dpi, `angleRad = 0.0`, `coveredPx = 0` — porting the band arithmetic from the route path's private check
  (`MapCanvasViewModel.kt:3674-3699`: project the four bbox corners with `ProjectionUtils.viewport`, require
  each inside `[0, width] × [coveredPx/2, coveredPx/2 + height − coveredPx]`), with KDoc stating that the
  camera center is *not* assumed to be the bbox midpoint. Compiles: `./gradlew :app:compileMobileDebugKotlin`.
  *(spec: fav-auto-zoom — Bounding box zoom calculation; poi-search — Details via single click)* — done
  2026-10-03: `fitsVisibleArea` landed with the camera-center and band parameters (no allocation of its own; the
  four corners are projected through `ProjectionUtils.viewport`).
- [x] 1.2 Add `internal fun verifiedAreaFit(...)` to the same file: `computeAreaZoom(bbox, width, height,
  minZoom, dpi)` then step the magnification out by one while `!fitsVisibleArea(...)` **and** `mag > minZoom`.
  KDoc records the per-site floors (design §4) and that the loop is pure arithmetic — no suspension, no IO,
  no renderer call, no per-step allocation. *(spec: fav-auto-zoom — Bounding box zoom calculation)* — done
  2026-10-03: `verifiedAreaFit` fits against `height − coveredPx` (as the route overview does) and verifies that
  same band, stepping out while `!fitsVisibleArea` and `mag > minZoom`; a fully covered or unlaid-out canvas
  falls through to `computeAreaZoom`'s own answer.
- [x] 1.3 Add `app/src/test/java/com/naviveylin/ui/map/FitVerificationTest.kt` with the pure cases of design
  §6: a bbox clipped at the rounded magnification returns one level out; an already-fitting bbox is returned
  unchanged; the floor is returned when even the floor does not fit; a rotated viewport (`angleRad ≠ 0`)
  needing a larger hull; and an **off-centre camera** (bbox not centered on the camera — the favorite/POI
  shape `computeAreaZoom` cannot serve). Verify:
  `./gradlew :app:testMobileDebugUnitTest --tests "com.naviveylin.ui.map.FitVerificationTest"` — all cases
  green. *(spec: fav-auto-zoom — Rotated viewport still contains the object)* — done 2026-10-03: 8 cases, each
  asserting its own premise first (round-up clip, already-fitting, off-centre camera, rotation, floor reached,
  below-floor reach, covered band, unlaid-out canvas).
- [x] 1.4 Revert-check one mutation: delete the step-out loop from `verifiedAreaFit` (return
  `computeAreaZoom`'s raw result) and confirm the clipped-at-rounded and off-centre cases **fail**; restore the
  loop and re-run green. Record both runs' counts in this task.
  *(spec: fav-auto-zoom — Whole-level rounding does not crop the object)* — **ran 2026-10-03**: with the loop
  removed, `roundedUpFitIsSteppedOutUntilTheBboxFits`, `offCentreCameraIsSteppedOutUntilTheBboxFits` and
  `rotatedViewportIsSteppedOutUntilTheBboxFits` failed (`3 tests completed, 3 failed`, `BUILD FAILED in 3m 16s`,
  XML `tests="3" failures="3"`); restored and re-ran with `--rerun-tasks` → `tests="8" skipped="0"
  failures="0" errors="0" time="9.966"`, `110 actionable tasks: 110 executed`, `BUILD SUCCESSFUL in 3m 14s`. The
  restored tree's first, cache-answered run (`BUILD SUCCESSFUL in 4s` carrying the previous run's XML timestamp)
  was discarded as evidence per `TODO.md` §17.
- [x] 1.5 Add the fit rule to `guidelines/MapRendering.md` next to the `viewport.center` contract
  (`:137-138`): a camera fit SHALL be verified by projecting its extent against the visible band it is fitted
  to, because whole-level rounding, a rotated viewport and an off-centre camera can each enlarge the fitted
  extent past that band; `computeAreaZoom` alone is not a fit. Verify: the rule is present and names
  `FitVerification.kt` and `computeAreaZoom` (read back the section). — done 2026-10-03: rule at
  `guidelines/MapRendering.md:142-158`, next to the `viewport.center` contract. It also records that rounding
  clips only upwards and that a fit which returns its floor has NOT been verified to fit.

## 2. Route overview moves onto the seam

- [x] 2.1 In `MapCanvasViewModel.fitViewportToRoute`, replace the private `routeFitsVisibleArea` call and delete
  the method: use `verifiedAreaFit(bbox, cameraLat = bbox midpoint, cameraLon = bbox midpoint,
  minZoom = MIN_MAG, angleRad = viewport angle, coveredPx = sheet covered height)`, keeping the existing
  camera shift onto the visible-area center (`:3653-3658`) unchanged.
  *(spec: route-map-overview — Route overview fits the route bounding box)* — done 2026-10-03: the fit is now
  `verifiedAreaFit(bbox, midLat, midLon, screenWidth, screenHeight, projectionDpi, minZoom = MIN_MAG,
  angleRad = angle, coveredPx = coveredPx)`; `routeFitsVisibleArea` and the now-unused `visibleHeightPx` local
  are deleted (`grep -rn routeFitsVisibleArea app/` empty).
- [x] 2.2 Regression proof: `MapCanvasViewModelRouteFitTest` passes **unmodified** — `./gradlew
  :app:testMobileDebugUnitTest --tests "com.naviveylin.ui.map.MapCanvasViewModelRouteFitTest"`, quote the case
  count and elapsed time from the result XML. The extraction is only behaviour-preserving if these cases keep
  their existing expectations without edits (in particular
  `longTrip_zoomsBelowAreaFloor_bothEndpointsFit`, `sheetInset_fitUsesVisibleAreaHeightAndShiftedCenter`,
  `fullyCoveredCanvas_skipsFit`). *(spec: route-map-overview — Route fit is suppressed while driving)* —
  **ran 2026-10-03**: `MapCanvasViewModelRouteFitTest` untouched (`git diff` on the file empty) and green —
  `tests="14" skipped="0" failures="0" errors="0" time="2.392"`, `25 actionable tasks: 25 executed`, 0 `w:`
  warnings; `FitVerificationTest` 8/8 in the same run.

## 3. Area-favorite fit is verified

- [x] 3.1 In `MapCanvasViewModel.onFavoriteSelected` (`:2219`) replace the bare `computeAreaZoom` call with
  `verifiedAreaFit(..., cameraLat = fav.lat, cameraLon = fav.lon, minZoom = MIN_AREA_ZOOM, angleRad = viewport
  angle)` — the camera stays the favorite coordinate and the documented area-favorites floor stays in force
  (design §4). *(spec: fav-auto-zoom — Bounding box zoom calculation)* — done 2026-10-03: the area fit now calls
  `verifiedAreaFit(bbox, fav.lat, fav.lon, screenWidth, screenHeight, projectionDpi, minZoom = MIN_AREA_ZOOM,
  angleRad = viewport angle)`; the camera and the floor are unchanged, only the verification is new.
- [x] 3.2 Add the ViewModel case: an area favorite whose object bbox is off-centre from the favorite
  coordinate yields a magnification at which the whole bbox is inside the visible band; and a small object
  still lands on the area-favorites floor rather than below it. Verify with `--tests
  "com.naviveylin.ui.map.MapCanvasViewModelRouteFitTest"` (same harness as `favoriteSelectedWhileFollowing…`)
  or the favorites test class — quote the case names and counts.
  *(spec: fav-auto-zoom — Bounding box zoom calculation)* — done 2026-10-03 as a **new** class
  `AreaFitViewModelTest` (`app/src/test/java/com/naviveylin/ui/map/AreaFitViewModelTest.kt`) instead of adding to
  `MapCanvasViewModelRouteFitTest`: task 2.2's regression proof requires that file to stay byte-identical. Cases:
  `areaFavoriteShowsTheWholeObjectWhenTheFavoriteIsNotItsCentre` (premise asserted against the unverified fit —
  the favorite sits on the object's south-west corner) and `areaFavoriteIsBoundedByTheAreaFavoritesFloor` (a 2 km
  object stays at the floor and is documented as *not* fitted). Also added the settable
  `FakeOSMScoutClient.objectBoundingBox` hook (defaults to null — the previous behaviour). Green:
  `tests="2" skipped="0" failures="0" errors="0" time="8.722"`. Because the floor legitimately bounds the fit,
  the `fav-auto-zoom` delta gained the scenario **The area-favorites floor bounds the fit** rather than leaving
  the visibility clause unconditional.
- [x] 3.3 Revert-check one mutation: pass `computeAreaZoom(bbox, screenWidth, screenHeight, dpi = projectionDpi)`
  raw at this call site and confirm 3.2's off-centre case **fails**; restore and re-run green. If a pre-existing
  favorites/zoom case changes its expected magnification under the verified fit, report it with the old and new
  value — do not re-baseline it silently.
  *(spec: fav-auto-zoom — Whole-level rounding does not crop the object)* — **ran 2026-10-03**: with the call
  site passing `computeAreaZoom(bbox, screenWidth, screenHeight, dpi = projectionDpi)`,
  `areaFavoriteShowsTheWholeObjectWhenTheFavoriteIsNotItsCentre` failed (`BUILD FAILED in 52s`, XML
  `tests="1" failures="1"`, `AssertionError: the whole object must be visible at the applied magnification`);
  restored and re-ran with `--rerun-tasks` → `tests="2" failures="0"`, fresh timestamp. No pre-existing
  favorites/zoom case changed its expected magnification (the favorites cases in the route-fit class stayed
  green against `expectedFitMag`), so nothing had to be re-baselined.

## 4. POI fit on the main map is verified, and reaches below the area floor

- [x] 4.1 In `MapCanvasViewModel.poiFitMagnification` (`:2530-2547`) return
  `verifiedAreaFit(bbox, cameraLat = entry.lat, cameraLon = entry.lon, minZoom = MIN_MAG, angleRad = viewport
  angle)` — the camera stays the POI (`updateCenter(entry.lat, entry.lon)`, `:2488`) and the area-favorites
  floor is lifted for this fit (option B2, design §4). No fix available ⇒ the current magnification is
  returned unchanged, as today. *(spec: poi-search — Details via single click)* — done 2026-10-03: the function's
  KDoc now states the POI-centred camera and the `MIN_MAG` floor, and the fit is
  `verifiedAreaFit(bbox, entry.lat, entry.lon, screenWidth, screenHeight, projectionDpi, minZoom = MIN_MAG,
  angleRad = viewport angle)`. The degenerate case (a POI exactly north/south of the fix, so the bbox has no
  longitude span and `computeAreaZoom` answers `NODE_ZOOM`) is now stepped out to a real fit as well.
- [x] 4.2 Add the ViewModel case: a POI whose distance from the current location does not fit at magnification
  14 yields a magnification **below** 14 with both positions inside the visible band — the mirror of the
  existing `longTrip_zoomsBelowAreaFloor_bothEndpointsFit`. Verify: `--tests` run of that class, quote case
  names and counts. *(spec: poi-search — Distant POI zooms out below the area-favorites floor)* — done
  2026-10-03: `AreaFitViewModelTest.distantPoiZoomsOutBelowTheAreaFavoritesFloor` — a POI 3 km north yields
  mag 12 with the current location visible; the case asserts `mag < 14` as its premise (the old floor-14 fit
  could not have produced it) and that both endpoints project inside the canvas.
- [x] 4.3 Add the rotated case: with a rotated viewport, clicking a POI with a fix keeps both positions inside
  the visible band. *(spec: poi-search — Rotated viewport keeps both positions visible)* — done 2026-10-03:
  `AreaFitViewModelTest.rotatedViewportKeepsBothPositionsVisible` — a POI 3 km north / 200 m east clicked at 0°
  then at 90° (the long axis moves onto the tight canvas axis): the north-up fit is asserted **not** to keep both
  endpoints visible at 90°, and the rotated fit is strictly smaller (12 → 11).
- [x] 4.4 Revert-check one mutation: pass `MIN_AREA_ZOOM` as this fit's floor and confirm 4.2's distant-POI case
  **fails**; restore `MIN_MAG` and re-run green. *(spec: poi-search — Distant POI zooms out below the
  area-favorites floor)* — **ran 2026-10-03**: with `minZoom = MIN_AREA_ZOOM` at this call site,
  `distantPoiZoomsOutBelowTheAreaFavoritesFloor` failed (`BUILD FAILED in 40s`, XML `tests="1" failures="1"`,
  `AssertionError: premise: a floor-14 fit could not show a 3 km-distant POI`); restored to `MIN_MAG` and
  re-ran with `--rerun-tasks` → `tests="5" failures="0"`, `110 actionable tasks: 110 executed`, fresh timestamp,
  no warning from the touched files.
- [x] 4.5 Confirm the "no current location" path is unchanged: no fix ⇒ current magnification, POI centered,
  marker shown. *(spec: poi-search — No current location available)* — done 2026-10-03:
  `AreaFitViewModelTest.poiClickWithoutAFixKeepsTheCurrentMagnification` — with no fix the applied magnification
  stays at the value set before the click (12.0) and the map centers on the POI.

## 5. Embedded result map fit is verified

- [x] 5.1 In `SearchDialog.poiFitMagnification` (`:944-1000`, called `:809`) route the result through
  `verifiedAreaFit(..., cameraLat = centerLat, cameraLon = centerLon, minZoom = MIN_MAG, angleRad = 0.0)` —
  the embedded map is north-locked (`MiniMap.kt:107`) — keeping the radius-bbox fallback and the
  `mapW`/`mapH`/`dpi` it is handed. *(spec: poi-search — Embedded result map fit never clips a result)* — done
  2026-10-03: **both** return paths go through the seam — the result-extent fit and the radius-bbox fallback
  (whose camera is the radius box's midpoint, so it is verified like any other), each with
  `minZoom = MapCanvasViewModel.MIN_MAG`; the `mapW/mapH <= 0` and `NaN` center guards are unchanged.
- [x] 5.2 Make that function `internal` (it is currently file-private) so its pure result is unit-testable, and
  say so in its KDoc; no behaviour change. Verify the module still compiles with
  `./gradlew :app:compileMobileDebugKotlin` and that no other call site appears (`grep -rn poiFitMagnification`).
  *(spec: poi-search — Embedded result map fit never clips a result)* — done 2026-10-03: it is `internal` and its
  KDoc says why (pure result, unit-testable) plus why `angleRad = 0` and why the floor is `MIN_MAG` here;
  `grep -rn poiFitMagnification app/` shows the single call site in `PoiResultsWithMap` (`SearchDialog.kt:809`)
  and the new test.
- [x] 5.3 Add the pure case: a result set whose extent clips at the rounded magnification returns a
  magnification at which every result position is inside `[0, mapW] × [0, mapH]`, and with a current position
  supplied that position is inside too. *(spec: poi-search — Every result stays inside the embedded map)* — done
  2026-10-03 in `app/src/test/java/com/naviveylin/ui/map/EmbeddedResultMapFitTest.kt` (400x240 at 160 dpi):
  `everyResultStaysInsideTheEmbeddedMap`, `currentPositionStaysInsideTheEmbeddedMap`,
  `noResultsFallsBackToTheRadiusAndStillFits`, `unknownCanvasOrCenterFallsBackToTheRenderMinimum`. Each premise
  case rebuilds the documented 30 % margin, so a margin change fails the premise loudly instead of leaving the
  case vacuous.
- [x] 5.4 Revert-check one mutation: return `computeAreaZoom`'s raw result from the embedded fit and confirm
  5.3's clipping case **fails**; restore and re-run green.
  *(spec: poi-search — Embedded result map fit never clips a result)* — **ran 2026-10-03, and it caught a weak
  case**: with `computeAreaZoom`'s raw result returned, `everyResultStaysInsideTheEmbeddedMap` failed but
  `currentPositionStaysInsideTheEmbeddedMap` still passed — it asserted the two endpoints, which stayed inside
  the canvas even though the fit's padded extent did not. That case now also asserts the padded extent
  (`fitsVisibleArea`), and the mutation fails **both** (`2 tests completed, 2 failed`, `BUILD FAILED in 20s`,
  XML `tests="2" failures="2"`). Restored and re-ran with `--rerun-tasks` → `tests="4" failures="0"`,
  `110 actionable tasks: 110 executed`, fresh timestamp.
- [x] 5.5 Confirm the embedded fit leaves the main map alone: re-run the existing embedded-map cases
  (`PoiSearchPanelMapComposeTest`) and quote counts — no main-map center or magnification change.
  *(spec: poi-search — Fitting the embedded map leaves the main map alone)* — done 2026-10-03: forced run
  (`--rerun-tasks`) of `PoiSearchPanelMapComposeTest` → `tests="2" skipped="0" failures="0" time="6.126"`, green
  with the new fit; no main-map center or magnification assertion moved.

## 6. Integration gate and bookkeeping

- [x] 6.1 Both flavors build with all three ABIs: `./gradlew :app:assembleMobileDebug :app:assembleAutomotiveDebug`,
  then `unzip -l <apk> | grep -E 'arm64-v8a|armeabi-v7a|x86_64'` on each. Quote the build's exit line.
  *(spec: all — nothing new in the native/manifest surface)* — done 2026-10-03: `BUILD SUCCESSFUL in 52s`,
  `160 actionable tasks: 33 executed, 1 from cache, 126 up-to-date`, 0 `w:` lines; `unzip -l` shows
  `lib/arm64-v8a/`, `lib/armeabi-v7a/` and `lib/x86_64/` in **both** `app-mobile-debug.apk` and
  `app-automotive-debug.apk`.
- [x] 6.2 Full `:app` suites, forced so a cached verdict cannot answer (`--rerun-tasks`, or clear
  `app/build/test-results/<flavor>` first — `TODO.md` §17): `:app:testMobileDebugUnitTest` and
  `:app:testAutomotiveDebugUnitTest`. Quote executed-task count, test count and elapsed time from the XMLs.
  *(spec: all delta scenarios)* — **ran 2026-10-03** (`--rerun-tasks`, `182 actionable tasks: 182 executed`,
  `BUILD SUCCESSFUL in 10m 12s`): `:app` mobile **201 classes / 1500 tests / 0 failures**, `:app` automotive
  **201 / 1500 / 0**. The first forced run of this gate **failed** and is recorded because it earned its keep:
  `PoiSearchViewModelTest.fitZoomShowsCurrentLocationAndPoi` asserted the old behaviour verbatim
  (`assertEquals("fit floor reached", 14.0, …)`) for a POI ~12 km away, which option B2 deliberately changes.
  Old value **14.0** → new value **12.0** (probed, then removed); the assertion is now spec-derived instead of a
  number — both the current location and the POI must project inside the canvas at the applied magnification
  (`isVisible` → `ProjectionUtils.viewport`). That class is 14/14 green. This is the one pre-existing
expectation the change legitimately moved; nothing was re-baselined silently.
- [x] 6.3 Confirm `:core` and `:auto` are unaffected: `./gradlew :core:testDebugUnitTest :auto:testDebugUnitTest`
  — quote counts. *(spec: none — regression guard for the shared `:app` package)* — done 2026-10-03 in the same
  forced run: `:core` **38 classes / 437 tests / 0 failures**, `:auto` **74 / 754 / 0**. The 106 compiler
  warnings in that run are all pre-existing (`TODO.md` §37/§44 family); none come from a file this change
  touched.
- [x] 6.4 On-device step (device-gated; record the run **or** the blocker): with a fix available, click a POI a
  few kilometres away and confirm both markers are on screen, then select an area favorite with the map
  rotated and confirm the whole object is visible; recipe `guidelines/Build.md` §10
  (`adb logcat -s NaviVeylin Diag/MAP`). As of 2026-10-02 `adb devices` is empty, so record the blocker if no
  device is attached. *(spec: poi-search — Details via single click; fav-auto-zoom — Rotated viewport still
  contains the object)* — **blocker recorded 2026-10-03**: `adb devices` lists no device (and no emulator is
  running), so the run could not happen. Owed on the next device session: click a POI a few kilometres away with
  a fix and confirm both markers are on screen; then select an area favorite with the map rotated and confirm the
  whole object is visible (`guidelines/Build.md` §10, `adb logcat -s NaviVeylin Diag/MAP`). The unit level covers
  both assertions at the projection, so this is a confirmation of the wiring, not of the geometry.
- [x] 6.5 Bookkeeping: remove `TODO.md` §28 (its own condition — "removed when the change is archived") and
  check whether the entry's note about the favorites zoom being clamped to 14–20 needs a cross-reference to
  the divergence recorded in `design.md`'s Open Questions.
  *(no spec — backlog hygiene)* — done 2026-10-03, with **one deliberate deviation from this task's wording**: §28
  was **not deleted**. Its parenthetical claim that it carries an "removed when archived" condition was my own
  error — the original entry had no such clause. It is now a *FIXED by `fix-area-fit-zoom-rounding`* entry that
  says it is removed when the change is archived, matching how §117/§49/§51 track a landed fix, and it carries two
  findings this change produced: the corrected rounding **direction** (rounding the magnitude down makes the
  content smaller; it is rounding up that exceeds the viewport — the entry and the route fit's own comment had it
  backwards) and the note that the favourite/POI cameras are off-centre. Deleting it outright would have lost
  both until the archive. Also in `TODO.md`: §44 gained the 2026-10-03 warning recount (106 warnings, none from
  this change; the additions are named), and the pre-existing `fav-auto-zoom` clamp divergence (spec says 4–18,
  code enforces 14–20) is filed as the new **§118** so it is not lost in `design.md`'s Open Questions alone.
  `grep -o '^## [0-9]*' TODO.md | sort | uniq -d` is empty — the `§79` collision this pass found was resolved
  to `§119` (and the harness-side `§79` itself was later removed once its defect was fixed).
