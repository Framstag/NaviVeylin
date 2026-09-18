# Tasks — Fix AA Follow Blit Anchor Mismatch

References: spec `auto-smooth-follow` — "Single resolved anchor in the AA follow blit" (this change); design decisions D1–D2 in `design.md`.

## 1. Core fix

- [x] 1.1 Change the two AA follow blit offset anchors in `auto/src/main/java/com/naviveylin/auto/AutoMapRenderer.kt`: in the extrapolation-loop blit (`FollowPrediction.displayOffsetPx(...)` with `followAnchor.fx/fy`) and in `renderFrame`'s blit path (the `if (followMode) followAnchor.fx/fy` branch), pass `resolvedFollowAnchor().fx/fy` (design D1). Keep the non-follow browse branch at `0.5/0.5`. Verify by grep that no follow-path anchor argument in `auto/src/main` still reads the raw `followAnchor` for a blit offset.

## 2. Regression tests

- [x] 2.1 Add `pane-band blit offset uses the resolved anchor` to `auto/src/test/java/com/naviveylin/auto/AutoMapRendererTest.kt`: LTR + far-left preset, compute the AA blit offset (`displayOffsetPx` with the AA frame geometry) for raw `followAnchor` and for `resolvedFollowAnchor()`; assert the raw-anchor offset is clamped outside the overrun margin (churn) while the resolved-anchor offset for the same drift stays inside the margin and the content lands at the resolved fraction (design D2).
- [x] 2.2 Add/extend a browse-mode guard: non-follow blit keeps the surface-center `0.5/0.5` behavior unchanged (design D2).
- [x] 2.3 Verify the new tests and the existing anchor/marker tests pass: `./gradlew :auto:testDebugUnitTest --tests "com.naviveylin.auto.AutoMapRendererTest"` — DONE marker (39/39 incl. new pane-band + browse-guard tests).

## 3. Build, tests, on-device verification

- [x] 3.1 Build the AAOS flavor for arm64-v8a: `./gradlew :app:assembleAutomotiveDebug -Pandroid.injected.build.abi=arm64-v8a` — BUILD SUCCESSFUL.
- [x] 3.2 Run the full unit suite: `./gradlew test` (mind the AGENTS.md classloader rules; phone framing tests include the raw-vs-resolved regression from `fix-phone-follow-blit-anchor-mismatch`).
- [x] 3.3 On-device AA check (head unit / car app host with pane, far-left preset, navigation or free driving): AA follow diagnostics show `off` inside the margin, `clamped` rare, no repeated full renders; the vehicle holds the configured fraction instead of being pressed toward the pane; record the logcat excerpt in the change. — verified on-device 2026-09-18 (car host, far-left preset): `off` inside the margin, `clamped` rare, no repeated full renders; vehicle holds the configured fraction
- [x] 3.4 Phone regression check: `app` unit tests stay green and `app/src/main/java/com/naviveylin/ui/map/MapCanvasScreen.kt` is byte-identical after the change.

## 4. Documentation

- [x] 4.1 Extend the Android Auto paragraph of `guidelines/MapRendering.md` §1.1 (host-pane collision): the AA blit offset uses the same resolved value as the AA frame render target (single resolved anchor rule shared with the phone).
