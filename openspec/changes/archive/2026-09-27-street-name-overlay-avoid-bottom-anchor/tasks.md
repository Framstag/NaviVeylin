## 1. StreetNameLabel placement API (spec: auto/free-driving, auto/navigation-view)

- [x] 1.1 Add a placement switch (`TOP` / `BOTTOM`, default `BOTTOM`) to `StreetNameLabel` (auto/src/main/java/com/naviveylin/auto/StreetNameLabel.kt): `TOP` resolves the label top edge from the largest of the known stable/visible area tops plus a symmetric margin (raw surface top + margin when both unknown); `BOTTOM` keeps today's `bottoms.min()` logic untouched; horizontal centering, `MAX_WIDTH_DP`, ellipsize, styling unchanged. Verify: `:auto` unit tests compile and existing `StreetNameLabelTest` passes unchanged (bottom path byte-identical).
- [x] 1.2 Add the anchor→placement rule as a tested pure helper (e.g. `StreetNameLabel.placementFor(anchor)` returning `TOP` for `VehicleAnchorPosition.BOTTOM_CENTER`, `BOTTOM` otherwise). Verify: plain-JUnit test in auto/src/test covering all 15 presets (exactly one yields TOP).
- [x] 1.3 Extend `StreetNameLabelTest` with TOP-geometry cases: known stable/visible tops, unknown areas (fallback to surface top), blank name draws nothing, `MAX_WIDTH_DP` still caps a long name, and bottom geometry unchanged. Verify: `./gradlew :auto:testDebugUnitTest --tests "com.naviveylin.auto.StreetNameLabelTest"` green.

## 2. Android Auto wiring (spec: auto/navigation-view, auto/free-driving)

- [x] 2.1 `NavigationScreen` (auto/.../NavigationScreen.kt): street-label placement from `routingAnchor` via the 1.2 helper; when placement is TOP, draw the map label unconditionally (skip `isMapLabelSafe`/`setTripText` fallback, design D3). Verify: unit test asserts the TOP path draws on the surface without the bottom-clearance gate; `:auto` tests green.
- [x] 2.2 `FreeDrivingScreen` (auto/.../FreeDrivingScreen.kt): street-label placement from `freeDrivingAnchor` via the same helper. Verify: unit test asserts placement selection; `:auto` tests green.

## 3. Phone wiring (spec: current-road-info)

- [x] 3.1 `MapCanvasScreen` (app/src/main/java/com/naviveylin/ui/map/MapCanvasScreen.kt): free-driving `StreetNamePill` aligns `TopCenter` (padding top 16.dp) when `state.activeFollowAnchor == VehicleAnchorPosition.BOTTOM_CENTER`, else `BottomCenter` (padding bottom 16.dp) unchanged. Verify: code + existing pill tests still pass.
- [x] 3.2 Add/extend a Compose unit test in app/src/test covering the pill alignment for both anchors (bottom default, top for bottom-center) and that the pill is still hidden during navigation. Verify: `./gradlew :app:testDebugUnitTest` targeted test green.
- [x] 3.3 `StreetNamePill` (app/src/main/java/com/naviveylin/ui/map/StreetNamePill.kt): the TOP placement applies `Modifier.statusBarsPadding()` as outer modifier, then the existing 16.dp margin inside it, so the pill clears the status bar / front-camera cutout on edge-to-edge phones; BOTTOM placement byte-identical. Order matters: `statusBarsPadding()` first, `padding(top=16.dp)` second. Extend `FreeDrivingStreetPillComposeTest` with an assertion that the top pill (BOTTOM_CENTER anchor) stays at the top edge under the test environment's zero insets with the 16.dp margin intact. Verify: `./gradlew :app:testMobileDebugUnitTest --tests "com.naviveylin.ui.map.FreeDrivingStreetPillComposeTest"` green.

## 4. Documentation

- [x] 4.1 Update `guidelines/UI.md` street-name/road-info placement notes: bottom-center default, top-center when the active anchor preset is bottom-center, same rule on phone and Android Auto (parity). Verify: doc reviewed against the three spec deltas; wording consistent.

## 5. Verification (build + on-device)

- [x] 5.1 Full unit-test pass: `./gradlew :auto:testDebugUnitTest :app:testMobileDebugUnitTest` (run-tests skill). Verify: all green, no regressions.
- [x] 5.2 Debug builds compile for both flavors (build-app skill): `./gradlew :app:assembleMobileDebug :app:assembleAutomotiveDebug`. Verify: builds succeed without errors; APK outputs fresh (mobile + automotive debug).
- [x] 5.3 AAOS emulator on-device check: vehicle-position picker → `BOTTOM_CENTER`; verify street name top-center and vehicle uncovered in both navigation and free driving; back to center anchor → label bottom (unchanged). Verify: observed on head-unit renderer/logcat (tag NaviVeylin) — no label/marker overlap in either anchor. — verified on-device 2026-09-18 (AAOS emulator): BOTTOM_CENTER → street name top-center, vehicle uncovered in navigation + free driving; center anchor → label bottom unchanged
- [x] 5.4 Phone emulator on-device check: location-options sheet → `BOTTOM_CENTER`; verify free-driving pill at top-center, vehicle uncovered; other presets (bottom-left, center) keep pill bottom-center. Verify: observed on device; no overlap with action column (top-left) or widget column (top-right); on a punch-hole/notch device the pill top edge sits below the status-bar inset, clear of the front camera (edge-to-edge screen).
