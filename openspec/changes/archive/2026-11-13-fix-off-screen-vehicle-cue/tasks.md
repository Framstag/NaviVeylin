# Tasks

Scope: `:app` only (phone marker overlay + its one call site) plus one spec delta and one guideline
section. No native, JNI, `:auto`, Gradle, manifest or resource change.

## 1. The red case, before any production change

- [x] 1.1 `app: LocationMarkerEdgeCueTest` (new) — `theOffScreenVehicleDrawsACueAtTheEdge`: compose
      `LocationMarkerOverlay` for a fix ~10° east of the viewport centre in a 300 px frame over one
      flat land colour (`LocalDensity provides Density(1f)`), rasterize under Robolectric
      `@GraphicsMode(NATIVE)` after `LocationMarkerOverlayPaletteTest`'s harness, and require at least
      one non-land pixel in the east edge band. Red on HEAD — the off-screen branch returns from the
      `Canvas` before drawing anything (`LocationMarkerOverlay.kt:66-67`).
      Parent: `gps-location-marker` — "Marker hidden when off-screen" (as modified by this change).
      Verification: `./gradlew :app:testMobileDebugUnitTest --tests
      "com.naviveylin.ui.map.LocationMarkerEdgeCueTest"` red, XML `tests=1 failures=1` at
      `2026-11-13T06:55:21.648Z` copied to `evidence/red/`.

## 2. The spec delta (the contract first)

- [x] 2.1 `openspec/changes/fix-off-screen-vehicle-cue/specs/gps-location-marker/spec.md` — `MODIFIED`
      requirement "Marker position tracks map viewport", carried as the whole block: the two
      description paragraphs verbatim plus the cue paragraph, the five surviving scenarios verbatim
      (only the mandated `**Case**:` line added), the off-screen scenario rewritten to the cue, and two
      scenarios added (cue presence/direction, foldable inset clearance) plus the on-screen negative.
      Parent: `gps-location-marker`.
      Verification: `./tools/check-change-readiness.sh openspec/changes/fix-off-screen-vehicle-cue`
      reports every scenario carrying a case, and `python3 tools/sync-specs.py --change
      fix-off-screen-vehicle-cue --dry-run` plans one `modified requirement`.
- [x] 2.2 The requirement the item's recorded decision names is the one modified — the "no off-screen
      indicators" clause at `openspec/specs/gps-location-marker/spec.md:120-123` — and no other
      requirement is rewritten.
      Verification: the delta carries one `MODIFIED` block; `tools/look.sh 'no off-screen indicators'
      --path openspec/specs/gps-location-marker/spec.md` still finds the shipped line until the archive
      merges the delta.

## 3. The cue in the overlay

- [x] 3.1 `LocationMarkerOverlay.kt` — `projectMarkerRaw` (projection without the `MARGIN_PX` cull),
      `edgeCuePlacement` (pure: inset-bounds clamp + pointing angle, `null` when the position is inside
      the frame), `EdgeCue`, `drawEdgeCue` (chevron in the marker's own palette branch) and
      `CUE_RADIUS_DP = 12.dp`; the `Canvas` takes the cue branch when `projectMarker` culls and paints
      the cue there. `projectMarker`, `MARGIN_PX`, the accuracy ring and the arrow are unchanged.
      Parent: `gps-location-marker` — "Marker hidden when off-screen" (modified).
      Verification: 1.1 green and 3.3 green.
- [x] 3.2 `LocationMarkerOverlay` gains `insetLeftPx`/`insetTopPx`/`insetRightPx`/`insetBottomPx`
      (`0` defaults), and `MapCanvasMapBand.kt:667` passes `viewModel.overlayInset*Px` — the columns
      `MapCanvasViewModelFollow.kt:55-58` already hands the framing.
      Parent: `gps-location-marker` — "The cue clears the inset columns".
      Verification: `./gradlew :app:compileMobileDebugKotlin` and 3.3's foldable case.
- [x] 3.3 `app: LocationMarkerEdgeCueTest` — add `theCueSitsOnTheEdgeTowardTheVehicle` (the cue centre
      is on the east edge in the vehicle's direction and `projectMarker` is `null` for that fix),
      `theCueClearsTheColumnInsetBounds` (a 900 px frame with a 200 px reserved right column keeps the
      whole cue left of the column) and `theOnScreenMarkerPlacesNoCue` (a centred fix places none).
      Parent: `gps-location-marker` (scenarios 7-9).
      Verification: focused run green — `tests="4" failures="0"` at `2026-11-13T06:57:41.355Z`
      (`evidence/green/TEST-LocationMarkerEdgeCueTest-green.xml`).
- [x] 3.4 Revert-check for the new invariant: delete the inset subtraction from the clamp
      (`right = screenWidthPx - insetRightPx - radiusPx` → `right = screenWidthPx - radiusPx`),
      `theCueClearsTheColumnInsetBounds` must go red, restore, then green forced with
      `-PforceTests --no-build-cache`.
      Parent: `gps-location-marker` — "The cue clears the inset columns".
      Verification: `evidence/red/TEST-LocationMarkerEdgeCueTest-revert-red.xml` (`tests="4"
      failures="1"`, only `theCueClearsTheColumnInsetBounds` red, `2026-11-13T06:58:32.156Z`) and
      `evidence/green/TEST-LocationMarkerEdgeCueTest-revert-green.xml` (`tests="4" failures="0"`,
      `2026-11-13T06:58:53.696Z`, `-PforceTests --no-build-cache`).

## 4. The guideline the rule lives in

- [x] 4.1 `guidelines/MapFollow.md` §8 — one rule under "Marker Rules": an off-viewport position draws
      no marker and one cue clamped to the viewport edge in the vehicle's direction, inside the inset
      bounds the map band reserves; phone only, the car cue is a native change.
      Parent: `gps-location-marker` (the requirement this change modifies).
      Verification: `./tools/check-doc-routes.sh` reports every section reference resolving and
      `tools/look.sh 'Off-screen cue' --path guidelines/MapFollow.md` finds the rule.

## 5. Verification of the whole delta

- [x] 5.1 Focused green run of the case class plus the two cases the changed file owns —
      `./gradlew :app:testMobileDebugUnitTest --tests
      "com.naviveylin.ui.map.LocationMarkerEdgeCueTest" --tests
      "com.naviveylin.ui.map.LocationMarkerOverlayTest" --tests
      "com.naviveylin.ui.map.LocationMarkerOverlayPaletteTest"` — 0 failures; XMLs copied to
      `evidence/green/` (`LocationMarkerOverlayTest` `tests="9" failures="0"` at
      `2026-11-13T06:58:01.187Z`; `LocationMarkerOverlayPaletteTest` `tests="4" failures="0"` at
      `2026-11-13T06:58:02.038Z`).
      Parent: `gps-location-marker` (regression of the projection, rotation and palette cases).
- [x] 5.2 `./tools/loop/measure.sh` re-run after the delta moved, and its `LOOP-MEASURE` block quoted
      in the change's evidence and in the worker's report.
      Verification: `evidence/measure.txt` — `paths=4 insertions=165 deletions=6`, the six
      `LOOP-MEASURE-TALLY` lines it reads, and
      `LOOP-MEASURE-CHANGE fix-off-screen-vehicle-cue scenarios=9 cases=9 unnamed=0`.
- [x] 5.3 Grade the diff before handing it over: every `THEN` clause of the delta points at a case line,
      every tree claim in this change is made true by a hunk, and every artifact it cites exists.
      Verification: this file and `evidence/` read back after the diff.
