# Proposal

## Why

Root cause: `openspec/specs/fav-auto-zoom/spec.md`'s scenario "Magnitude clamped to valid range" states a clamp
range the code has never applied — it claims **4–18**, while `computeAreaZoom` clamps with
`coerceIn(minZoom, MAX_MAG)` (`MapCanvasViewModel.kt:4689`) whose `minZoom` defaults to `MIN_AREA_ZOOM = 14.0`
(`:4615`, parameter at `:4646`) for the area-favorite fit and whose ceiling is `MAX_MAG = 20.0` (`:4584`). The
range this requirement's fit enforces is **14–20**; the scenario has been wrong on both ends since the initial
import, and nothing compared the two artefacts.

Evidence: `com.naviveylin.ui.map.FavAutoZoomClampRangeTest#the clamp scenario states the range the area favorite fit applies`
fails on HEAD — XML `tests="1" skipped="0" failures="1" errors="0" timestamp="2026-10-09T18:21:03.694Z"`,
failure `java.lang.AssertionError: the scenario's lower bound must be the floor the area-favorite fit clamps to expected:<14.0> but was:<4.0>`;
the same run's `system-out` carries both ranges, `clampRange spec=[4.0, 18.0] enforced=[14.0, 20.0]`.

Repro: `./gradlew :app:testMobileDebugUnitTest --tests "com.naviveylin.ui.map.FavAutoZoomClampRangeTest"`

Authority — the spec is the wrong side, on both ends:

- **The code has never been 4–18.** `git show 6b13586:app/src/main/java/com/naviveylin/ui/map/MapCanvasViewModel.kt`
  (the initial import) already ends `return mag.coerceIn(MIN_AREA_ZOOM, MAX_MAG)` with `MIN_AREA_ZOOM = 14` and
  `MAX_MAG = 20`, against the same stale spec line: no commit introduced the divergence and no behaviour
  regressed (`git log -S'MIN_AREA_ZOOM'` finds only that import, the `fix-address-lookup-accuracy` fold and
  `fix-area-fit-zoom-rounding`).
- **The app-wide range is spec'd as 4–20 elsewhere.** `openspec/specs/map-pan-zoom/spec.md:112` ("the viewport
  magnification headroom clamp between MIN_MAG (4) and MAX_MAG (20)") and
  `openspec/specs/map-rotation-gesture/spec.md:128` ("clamped to the application limits (4–20)").
- **A floor above 4 is a deliberate, spec'd concept this file itself relies on.** Its own "The area-favorites
  floor bounds the fit" scenario (added 2026-10-05 by `fix-area-fit-zoom-rounding`) and
  `openspec/specs/poi-search/spec.md:134` ("Distant POI zooms out below the area-favorites floor") /
  `openspec/specs/route-map-overview/spec.md:20` ("Long trip zooms out below the area-favorites floor") are only
  meaningful if the favorites floor is above the render minimum.
- **Test-pinned agreement, which is not authority by itself**: `AreaFitViewModelTest#areaFavoriteIsBoundedByTheAreaFavoritesFloor`
  asserts the applied magnification for a 2 km object is `14.0` and `MapCanvasViewModelZoomRangeTest#gestureClampKeepsFourFloor`
  pins 4→4 and 21→20. No case pins 4 or 18 for `computeAreaZoom` — `grep -rn "14\.0\|18\.0\|MIN_AREA_ZOOM\|MAX_MAG\|computeAreaZoom" app/src/test core/src/test auto/src/test`
  finds only the two `areaFloor = 14.0` locals in the fit tests (this change moves neither) and 18.0 hits in
  unrelated suites (speed zoom, diagnostics strings, a pan drag).

Spec: `fav-auto-zoom` / Bounding box zoom calculation (scenario "Magnitude clamped to valid range")   Guideline:
none — `guidelines/MapRendering.md` §3 names the area-favorites floor without a number (`:165`), so no guideline
text contradicts the corrected range.

## What Changes

- **`openspec/specs/fav-auto-zoom/spec.md` and the change's delta**: the scenario states the range the
  area-favorite fit applies — "below the area-favorites magnification floor (14) or above the maximum
  magnification (20)" — instead of "outside valid range (4–18)". The requirement's other four scenarios and its
  prose are carried verbatim.
- **A host case that holds the two artefacts together**:
  `app/src/test/java/com/naviveylin/ui/map/FavAutoZoomClampRangeTest.kt` reads the two bounds from the code (the
  floor through a bbox whose raw fit lies below it, the ceiling through the size range over which the fit stops
  moving) and compares them to the numbers the scenario states, printing both ranges before it asserts. It is
  the red-on-HEAD case and it stays as the guard: a code or spec change that moves either range now fails.
- **No production code changes.** The fit's behaviour is what the sibling specs, the floor concept and the
  existing fit cases already pin; the divergence is the sentence.

## Capabilities

### New Capabilities

(none)

### Modified Capabilities

- `fav-auto-zoom`: the clamp scenario states the range the area-favorite fit applies — the area-favorites
  magnification floor to the maximum magnification (14–20) — instead of a stale 4–18.

## Impact

- `openspec/specs/fav-auto-zoom/spec.md` (one scenario line; the rest of the requirement block unchanged).
- `app/src/test/java/com/naviveylin/ui/map/FavAutoZoomClampRangeTest.kt` (new, one case).
- No production Kotlin, no JNI, no manifest, no flavor, no dependency, no native change: both flavors compile
  the same code and run the same new case, so the automotive variant is unaffected beyond the forced gate.
- No test expectation moves (grep above).
- Additive and reversible: reverting the one scenario line and deleting the test file restores HEAD exactly. The
  archive sync of the main spec from the delta is a textual no-op, because this change writes the main spec and
  the delta with the same text.
