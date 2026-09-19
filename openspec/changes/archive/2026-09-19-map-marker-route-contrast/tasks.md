# Tasks

## 1. Marker constants (`:core`) — specs `gps-location-marker`, `auto-map-renderer`

- [x] 1.1 In `core/src/main/java/com/naviveylin/core/VehicleMarkerGeometry.kt` change `SIZE_DP` from `32f` to `38f` and update the KDoc line that names the footprint. Verify: `grep -n "SIZE_DP" core/src/main/java/com/naviveylin/core/VehicleMarkerGeometry.kt` shows 38f.
- [x] 1.2 Add the dark-presentation core gradient constants `COLOR_GRADIENT_TOP_DARK = 0xFFBBDEFB` and `COLOR_GRADIENT_BOTTOM_DARK = 0xFF1E88E5` next to `COLOR_CASING_DARK`, leave `COLOR_RIM`, `COLOR_CASING`, `COLOR_CASING_DARK` and all geometry constants untouched, and update the object KDoc so it states "palette branches on presentation, geometry never does" (spec `gps-location-marker`, scenario "Marker geometry identical in both presentations"). Verify: the file still exposes exactly one size constant and the vertices/casing-scale/rim-width/shadow constants are unchanged (`git diff` on the file shows only size, palette, and comments).
- [x] 1.3 Extend `core/src/test/java/com/naviveylin/core/VehicleMarkerGeometryTest.kt`: footprint assertion 32f to 38f; assert the dark core top and bottom are both lighter than `COLOR_CASING_DARK`; assert neither dark core color is white (each channel below a white threshold); assert the day palette values are unchanged; assert `outlineVertices()`, `CASING_SCALE`, `RIM_WIDTH_H`, `SHADOW_OFFSET_DP`, `SHADOW_BLUR_DP` are palette-independent. Verify: `./gradlew :core:test` passes with the new assertions.
- [x] 1.4 Update the two renderer consumers to use the new dark palette and confirm neither hardcodes a size or a day-only core: `app/src/main/java/com/naviveylin/ui/map/LocationMarkerOverlay.kt` selects the gradient pair by its `dark` argument, `auto/src/main/java/com/naviveylin/auto/AutoMapRenderer.kt` selects it from `darkPresentation`. Verify: `grep -rn "SIZE_DP\|COLOR_GRADIENT" app/src/main/java/com/naviveylin/ui/map auto/src/main/java/com/naviveylin/auto` shows only constant reads, no literal `32`/`38` and no hardcoded gradient value.

## 2. Route stylesheet (submodule patch) — spec `route-appearance`

- [x] 2.1 In `app/src/main/cpp/libosmscout/stylesheets/include/route.oss` add the `IF daylight` branch: `routeColor = #7b1fa2` (opaque violet) and a new `routeCasingColor = #311B92`; in the `ELSE` branch keep `routeColor = #ff000088` and `routeCasingColor = #ffffff`; change the `[TYPE _route] WAY#outline` rule to `color: @routeCasingColor` instead of the hardcoded `#ffffff`; leave `displayWidth`, `width` and `priority` values untouched. Verify: `grep -n "routeColor\|routeCasingColor\|_route" app/src/main/cpp/libosmscout/stylesheets/include/route.oss` shows both branches and the casing constant, and `git -C app/src/main/cpp/libosmscout diff --stat` shows only that file.
- [x] 2.2 Commit the stylesheet patch inside the submodule on `naviveylin-local` (minimal, upstreamable message), then bump the gitlink in the main repo. Verify: `git -C app/src/main/cpp/libosmscout log --oneline -1 -- stylesheets/include/route.oss` shows the new commit and `git submodule status` reports the new SHA without a `+` prefix in the main repo after staging the gitlink.
- [x] 2.3 Confirm the build copies the changed stylesheet into the APK assets (no committed snapshot exists) and that the stylesheet still parses. Verify: `./gradlew :app:assembleMobileDebug` succeeds, the packaged asset contains `#7B1FA2` (`unzip -p app/build/outputs/apk/mobile/debug/app-mobile-debug.apk assets/stylesheets/include/route.oss | grep 7B1FA2`), and an app start shows no stylesheet load error in `adb logcat -s NaviVeylin`.
- [x] 2.4 Replace the cycle style's own route rule with the shared include: in `app/src/main/cpp/libosmscout/stylesheets/cycle.oss` remove `COLOR routeColor = #ae00ff88` and the `[TYPE _route] WAY` rule inside `[MAG world-]`, and add `MODULE "include/route"` to its MODULE block (mirroring `standard.oss`/`winter-sports.oss`). Verify: `grep -n "routeColor\|_route\|include/route" app/src/main/cpp/libosmscout/stylesheets/cycle.oss` shows only the `GROUP _route` ordering entry and the new include, and the file defines no symbol or color name that `route.oss` also defines.
- [x] 2.5 Commit the cycle stylesheet change inside the submodule on `naviveylin-local` and bump the gitlink again. Verify: `git -C app/src/main/cpp/libosmscout status --porcelain` is empty, `git submodule status` reports the new SHA without a `+`, and the packaged asset check (`unzip -p app/build/outputs/apk/mobile/debug/app-mobile-debug.apk assets/stylesheets/cycle.oss | grep 'include/route'`) finds the include in both flavors.

## 3. Specs and guidelines

- [x] 3.1 Update `guidelines/UI.md` (marker paragraph around line 441): footprint 32 dp to 38 dp, and replace "white casing ring + dark rim + vertical blue gradient core" with the presentation-dependent palette (white casing + `#42A5F5`→`#0D47A1` core in light, deep blue-black casing + `#BBDEFB`→`#1E88E5` core in dark), keeping the phone/car parity statement. Verify: the paragraph names 38 dp and both palettes and no longer contradicts the modified specs.
- [x] 3.2 Add the route color contract to `guidelines/UI.md` (day violet `#7B1FA2` fill with `#311B92` casing, night red with white casing, same colors on phone and Android Auto) and re-read `guidelines/MapRendering.md` to confirm its marker-overlay and route-render statements are still true. Verify: every statement in the touched guideline sections matches the modified specs; `guidelines/MapRendering.md` is either unchanged with a note that it was reviewed, or updated where a statement became wrong.
- [x] 3.3 Confirm the durable specs still agree with the deltas after the change: read `openspec/specs/gps-location-marker/spec.md` and `openspec/specs/auto-map-renderer/spec.md` and check that the only conflicting lines are the ones the deltas replace (size, palette branch, dark scenarios). Verify: `openspec validate map-marker-route-contrast --strict` passes.

## 4. Build and test verification

- [x] 4.1 Use the `build-app` skill to build the debug APKs for both flavors (`:app:assembleMobileDebug`, `:app:assembleAutomotiveDebug`) and verify the build compiles without errors or warnings. Verify: skill reports success and no new warnings.
- [x] 4.2 Use the `run-tests` skill to run the unit tests for `:core`, `:app`, `:auto`, and `:osmscout-client-java` and verify all existing tests still pass, including the marker and map-render suites that consume the shared constants. Verify: skill reports green.
- [ ] 4.3 Verify coverage of the changed code: confirm `VehicleMarkerGeometryTest` covers both palettes, the size constant, and palette-independence of the geometry, and that no new untested branch was introduced in `LocationMarkerOverlay`/`AutoMapRenderer`. Verify: Kover/JaCoCo report (`./gradlew :koverHtmlReport :osmscout-client-java:jacocoTestReport`) shows fully covered lines for the modified constant-selection code.
- [x] 4.4 Add a regression guard for the crash cause: `app/src/test/java/com/naviveylin/data/StylesheetHexColorCaseTest.kt` scans every packaged stylesheet for uppercase hex color literals (the uppercase spelling makes `Color::GetHexValue` assert, fails the module load and crashes the renderer) and pins the route include's `IF daylight` branch with `#7b1fa2` / `#311b92` / `@routeCasingColor`. Verify: the test fails with the uppercase spelling (checked: 2 failures) and passes with lowercase (`./gradlew :app:testMobileDebugUnitTest --tests "com.naviveylin.data.StylesheetHexColorCaseTest"` green).

## 5. On-device verification

- [x] 5.1 Phone, daylight presentation: show an active route along a primary road and along a residential street, then with the GPX track visible and with a search selection visible; verify the route reads as an opaque violet ribbon with a dark violet border, is distinguishable from every road it crosses, and remains distinguishable from the track and the search marker (spec `route-appearance` scenarios 1-4). Verify: screenshots at city and detail zoom.
- [x] 5.2 Phone, dark presentation: verify the route is unchanged (red fill, white casing) and the marker is visibly lighter on dark land with no white ring around it (spec `gps-location-marker` scenarios "Arrow legible on dark map", "Arrow not too dark on dark map", "No white halo on dark map"). Verify: screenshots next to the previous build for comparison.
- [x] 5.3 Phone: verify the marker occupies 38 dp of visual size at the same zoom as before and does not cover the next-turn card or hide the route line in follow mode (spec `gps-location-marker` scenario "Marker size uniform across device densities"). Verify: side-by-side screenshot with the previous build in follow mode at city zoom.
- [x] 5.4 Android Auto emulator or head unit: verify day and night map show the route and marker with the same colors and size as the phone for the same presentation, and that the night route is still red with a white casing (spec `auto-map-renderer` scenarios "Marker size uniform with phone", "Marker style unified with phone"; spec `route-appearance` scenarios "Phone and car route colors match", "Car night route matches the phone night route"). Verify: car-screen screenshots in both host day/night states plus a clean surface start/stop cycle.
- [x] 5.5 Phone map-style switch: verify the route keeps the shared violet fill and dark casing in the `cycle` and `winter-sports` styles, and that the cycle style now shows favorites, the search selection, the GPX track and the route end points (spec `route-appearance`, scenarios "Switching map style keeps the route colors", "Non-default styles have a route casing"; design D6 consequence). Verify: screenshots per style in daylight plus one dark-presentation check.

## 6. Wrap-up

- [x] 6.1 Append any failed approach or dead end hit during implementation to `ki_processing_failures.log` with a timestamp and problem description, and record any unrelated problem discovered during the work in `TODO.md`. Verify: both files contain the new entries or are explicitly unchanged with a reason.
- [x] 6.2 Confirm traceability before finalizing: every scenario of the modified `gps-location-marker`/`auto-map-renderer` requirements and of the new `route-appearance` capability maps to at least one task above, and every task maps back to a requirement. Verify: a written mapping in the change notes, and `openspec validate map-marker-route-contrast --strict` passing.

## 7. Traceability (task 6.2)

Spec scenario -> task. Unchanged scenarios of the two modified requirements (accuracy circle, bearing semantics, fix updates, gliding, displayed-frame projection, re-anchor) are untouched by this change and stay covered by the pre-existing suites run in 4.2.

| Spec scenario | Task |
|---|---|
| `route-appearance` route over a primary road / over a white road / opaque fill / vs GPX track | 2.1, 4.4, 5.1 |
| `route-appearance` route in dark presentation / daylight and dark differ | 2.1, 4.4, 5.2 |
| `route-appearance` phone and car match / car night matches phone night | 2.1 (one stylesheet), 5.4 |
| `route-appearance` switching style keeps colors / non-default style has a casing | 2.4, 2.5, 4.4, 5.5 |
| `route-appearance` width unchanged / no extra render pass | 2.1 (displayWidth, width, priority untouched; stylesheet-only) |
| `gps-location-marker` legible on daylight / dark, not too dark, no white halo | 1.2, 1.3, 1.4, 4.2, 5.2 |
| `gps-location-marker` geometry identical in both presentations | 1.2, 1.3 (`geometryIsPaletteIndependent`) |
| `gps-location-marker` size uniform across densities / phone and Auto share one style | 1.1, 1.3, 1.4, 4.2, 5.3, 5.4 |
| `auto-map-renderer` legible daylight/dark, no white halo, size and style unified with phone | 1.1, 1.2, 1.4, 4.2, 5.4 |
| guidelines/UI.md marker + route contract matches the specs | 3.1, 3.2, 3.3 |

Task -> spec: 1.x implement `gps-location-marker` + `auto-map-renderer`; 2.x implement `route-appearance`; 3.x keep `guidelines/UI.md`/`MapRendering.md` consistent with all three; 4.x are the build/test gates of the same specs; 5.x are the observable scenarios; 6.1 is process hygiene, 6.2 this mapping.

Verification outcome recorded 2026-09-19: tasks 5.1-5.5 were run by the user on the phone emulator and reported working ("concrete colors have to be tested under real life situations"); 5.4 (Android Auto) was accepted by assumption rather than measured on a head unit, and both residuals are recorded in `TODO.md` section 34.
