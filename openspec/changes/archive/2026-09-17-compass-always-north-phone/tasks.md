## 1. Needle semantics (spec: compass-button — Compass shows north direction, North pointer points at rendered north)

- [x] 1.1 Change `compassNeedleTarget` in `app/src/main/java/com/naviveylin/ui/map/CompassButton.kt` to return `ProjectionUtils.compassRotationDegrees(mapAngleRadians)` unconditionally and drop the `isNorthUp`/`bearingDegrees` parameters; verify `CompassNeedleTargetTest` cases for north-up 0°, heading-up southbound 180°, eastbound 270° pass — done; `CompassNeedleTargetTest` rewritten (6 tests) and green
- [x] 1.2 Add the normalization coverage: table test over angles 0, ±45, 90, 180, −90, 270, 450, −450 — the needle equals `compassRotationDegrees` (mod 360, no NaN, no sign flip) — `normalization table matches compassRotationDegrees` (also asserts the [0,360) range and non-NaN)
- [x] 1.3 Verify bearing independence: a test that two inputs differing only in the (removed) vehicle bearing produce the same needle angle — the regression gate for the standstill swirl (spec scenario "Needle unaffected by an unstable vehicle bearing") — enforced by the signature (the needle has no bearing parameter; there is no second input to differ in) and pinned by `needle is independent of the vehicle bearing` + the compose test `headingUpFollowShowsTheNorthNeedle`; the bearing-difference runtime case is unreachable by construction
- [x] 1.4 (added during implementation) Extract the button fill mapping into `internal fun compassFillColor(gpsFixQuality)` so the fix-quality palette stays testable after the needle rework; verified by `CompassButtonComposeTest.fillColorReflectsGpsFixQuality`

## 2. Drawing (spec: compass-button — Compass shows north direction)

- [x] 2.1 Delete the follow-direction triangle branch in `drawCompassNeedle` and the `isNorthUp` parameter; the north half + neutral south half + "N" glyph is drawn in every mode; verify `CompassButtonComposeTest` asserts the north pointer appears in follow-direction mode and that no triangle path is drawn — triangle branch and its `Path` import deleted; one drawing path remains; `headingUpFollowShowsTheNorthNeedle` (north at 90° for westbound heading-up, not the triangle's 0°) + `northUpShowsTheNorthNeedle` pass. Note: "no triangle path is drawn" is structural (a single code path, no `isNorthUp`/bearing input) rather than pixel-asserted — a pixel test at 48dp would be brittle
- [x] 2.2 Verify the widget geometry is unchanged (needle half-length 10dp, 3px stroke, 11sp "N", 56dp layout / 48dp visual, same shadow) via the existing sizing tests in `CompassButtonComposeTest` (spec: compass-button — Compass button matches overlay button sizing) — `buttonLargerThanOtherOverlayButtons` passes; the needle geometry constants are untouched (10dp half-length, 3px stroke, 11sp "N", 56dp/48dp, shadow 3dp unchanged)
- [x] 2.3 Verify the GPS fix-quality fill color, short-press re-center and long-press orientation toggle are untouched: the corresponding `CompassButtonComposeTest` scenarios still pass — `shortPressTriggersCenterClick`, `longPressTriggersToggleOrientation` and the new `fillColorReflectsGpsFixQuality` pass (the fill mapping moved to `compassFillColor`, behavior unchanged)

## 3. Call sites

- [x] 3.1 Remove `bearingDegrees` from `CompassButton`, `MapCompassBlock` and `MapRightWidgetColumn` signatures and drop the `bearingDegrees = state.gpsMarkerBearing` argument at the three call sites in `MapCanvasScreen.kt` (free-form, landscape, routing view) — done; `isNorthUp`/`compassNorthUp` were dropped with it (they only fed the needle: `MapCompassBlock.isNorthUp`, `MapRightWidgetColumn.compassNorthUp`, the screen's `val compassNorthUp = when (viewModel.mode)` and the three call-site arguments are all gone, so no unused parameter remains)
- [x] 3.2 Update `MapRightWidgetColumnTest` and any other test that passes the removed parameter; verify the module compiles: `./gradlew :app:compileMobileDebugKotlin` — `MapRightWidgetColumnTest` updated; `CompassButtonComposeTest` rewritten for the new signature; module compiles clean
- [x] 3.3 Verify `MapCanvasViewModel`/`state.gpsMarkerBearing` is still consumed where it belongs (marker arrow, road lookup) and no unused import/state is left behind; no change to the map-rotation bearing source (`fix.smoothedBearing`) — `state.gpsMarkerBearing` still feeds the marker overlay and `resolveCurrentRoad`; the compass no longer consumes it; the map rotation still uses `fix.smoothedBearing`; `Path` import removed, no unused imports/warnings (see 5.2)

## 4. Docs

- [x] 4.1 Update `guidelines/UI.md` §8: replace the "follow-direction triangle stays ~70% of the button" clause with the always-north needle rule (same single convention as Android Auto); verify the doc no longer mentions a triangle as a live UI element — clause replaced with the always-north rule, the no-bearing-input rule and the "no travel-direction triangle" statement
- [x] 4.2 Verify no other guideline contradicts the change: `grep -ri "triangle" guidelines/` returns only the removed/updated context; `guidelines/MapRendering.md` §6/§7 read consistently with a bearing-free compass — only the UI.md "There is no travel-direction triangle" wording matches (the marker overlay's two-triangle *geometry* notes are unrelated, libosmscout's are upstream); `MapRendering.md` does not mention the compass at all and §7's `markerBearing`-for-marker-arrow statement is now exactly true

## 5. Test suite and build

- [x] 5.1 Run the full suite with the `run-tests` skill (`./gradlew test`) and report executed test counts per module; all existing tests for map rotation, marker and Auto pass — results cleared first, detached run: `BUILD SUCCESSFUL in 3m 41s`; app mobile **958**, app automotive **958**, auto 371, core 205 tests; 0 failures / 0 errors / 0 skipped
- [x] 5.2 Build the phone flavors with the `build-app` skill (`./gradlew :app:assembleMobileDebug`, `:app:assembleAutomotiveDebug`) — compile clean, no warnings — `BUILD SUCCESSFUL in 15s`, 0 `warning:` lines
- [x] 5.3 Verify spec/change coherence: `openspec validate compass-always-north-phone` is clean and `openspec show compass-button --type spec` reads consistently after archive (no leftover follow-direction-triangle requirement) — `validate --strict` clean; the delta removes that requirement with Reason/Migration, so the durable spec follows at archive time

## 6. On-device verification (phone)

- [x] 6.1 Heading-up follow, free driving: driving south puts the needle down (north behind), driving east puts it left — check against the map's own north direction
- [x] 6.2 Standstill check at a traffic light: the needle holds still while the reported bearing is noisy and does not settle on the travel direction
- [x] 6.3 Navigation (heading-up) with the routing view: needle shows north, next-turn overlay and speed widget unaffected, long-press still toggles to "always north" and the needle then points up while the map is north-up
- [x] 6.4 North-up regression: needle behavior identical to the previous build in north-up mode (rollback path of the proposal)
- [x] 6.5 Android Auto spot check: the compass rose behavior is unchanged (already north-pointing) — confirms phone/AA parity of the north indication
