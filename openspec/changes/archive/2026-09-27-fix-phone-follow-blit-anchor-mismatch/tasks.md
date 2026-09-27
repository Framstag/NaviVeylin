# Tasks — Fix Phone Follow Blit Anchor Mismatch

References: spec `smooth-follow` — "Single resolved anchor across render, blit and marker" (this change); design decisions D1–D2 in `design.md`.

## 1. Core fix

- [x] 1.1 Change the follow blit offset anchor in `app/src/main/java/com/naviveylin/ui/map/MapCanvasScreen.kt` (follow display block, `FollowPrediction.displayOffsetPx(...)` call): pass `state.resolvedAnchor.fx, state.resolvedAnchor.fy` instead of `ui.activeFollowAnchor.fx, ui.activeFollowAnchor.fy` — the same resolved fractions the render target (`followRenderTarget`) and the marker projection already use (design D1). Verify by inspection that no other anchor argument in the follow pipeline still reads the raw preset (grep `activeFollowAnchor.fx|fy` under `app/src/main`).

## 2. Regression tests

- [x] 2.1 Add `blit against the raw preset while frame and marker use the resolved anchor leaves the marker off the content` to `app/src/test/java/com/naviveylin/ui/map/FollowAnchorFramingTest.kt`: same geometry as the existing resolved test (turn card 0.19H top, routing status 0.18H bottom, widget column 0.07W right, BOTTOM_CENTER, drift within margin) but the offset computed with the raw preset fractions; assert the marker screen position differs from the content position by the anchor delta (documents the defect; design D2).
- [x] 2.2 Extend/keep the resolved-consistency assertion for the same geometry with resolved fractions everywhere: marker on content, offset unclamped (fixed behavior; design D2).
- [x] 2.3 Verify the new tests pass: `./gradlew :app:testMobileDebugUnitTest --tests "com.naviveylin.ui.map.FollowAnchorFramingTest"` — DONE marker.

## 3. Build, tests, on-device verification

- [x] 3.1 Build the app module for arm64-v8a: `./gradlew :app:assembleMobileDebug -Pandroid.injected.build.abi=arm64-v8a` — BUILD SUCCESSFUL.
- [x] 3.2 Run the full unit suite: `./gradlew test` (mind the AGENTS.md classloader rules for tests touching `OSMScoutClient`).
- [x] 3.3 On-device phone check (GPX replay or actual drive, navigation mode, bottom-center placement): `adb logcat -s NaviVeylin` follow diagnostics show `off` small and `clamped` rare while driving, the marker stays glued to the route track (no 0.14H offset ahead in driving direction), and no 500 ms re-render churn; record the logcat excerpt in the change.
- [x] 3.4 Android Auto regression check: `:auto` unit tests stay green (`./gradlew :auto:testDebugUnitTest`) and `auto/src/main/java/com/naviveylin/auto/AutoMapRenderer.kt` is byte-identical after the change (AA already passes its resolved anchor).

## 4. Documentation

- [x] 4.1 Add one sentence to `guidelines/MapRendering.md` §1.1 (follow-flow bullets): the anchor is applied once, identically, in the render target, the blit offset and the marker projection (spec: single resolved anchor).
