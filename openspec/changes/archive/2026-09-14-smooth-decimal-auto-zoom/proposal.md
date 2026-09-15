## Why

Speed-dependent auto-zoom still feels clunky: magnification changes snap in full-level jumps (16 → 15 → 14) every few seconds instead of gliding. The decimal zoom levels the app already supports — `SpeedZoomTable` computes fractional targets (14.5 at 75 km/h, per the existing spec and tests) and pinch/persistence render fractional magnifications fine — are thrown away at the commit gate by `roundToInt()` in both the phone controller and the Android Auto controller. Two defects compound: decimals never reach the committed magnification, and auto-zoom commits bypass the eased front-buffer zoom animation that discrete zoom input already enjoys, so each commit lands as a hard visual snap.

## What Changes

- **Fractional auto-zoom commits (phone + Android Auto):** the committed magnification becomes the fractional target from `SpeedZoomTable` (with turn/curve/post-turn floors, unchanged) instead of the integer-rounded level. Native rendering at fractional magnifications is already proven (pinch commits continuous values; AA renders `2^fraction`).
- **Slower convergence rate limit:** magnification moves at most **0.5 level per position update** toward the target, replacing the integer-era machinery (3 identical-sample stability, 2.5 s cooldown, 1.0-level hysteresis) with a rate-based limiter plus a small epsilon skip so constant/stopped speeds do not re-render every fix. A 16 → 14 transition now takes ~4 s instead of one jump. Manual-zoom suspension + speed-band re-engage semantics are preserved.
- **Eased display animation for auto-zoom (phone):** each auto-zoom commit drives the existing `ZoomAnimation` front-buffer easing (~500 ms, versus 250 ms for button zoom) anchored at screen center, with retrack continuity for consecutive commits. The render-land handoff (smooth-zoom / zoom-transition-scaling) is reused unchanged. Android Auto needs no display animation: its renderer draws a fresh frame at the fractional magnification each commit and the follow extrapolation loop already eases the display.
- **Tests:** `AutoZoomControllerTest` rewritten for fractional rate-limit semantics; phone auto-zoom commit behavior covered; `SpeedZoomTableTest` and discrete-input animation tests untouched.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `auto-speed-zoom`: commits must use the fractional magnification target (no integer rounding — the interpolation requirement already promises fractional values like 14.5, but the commit path rounds them away); the smooth-transition requirement changes from "at most 1 level per position update" to a slower rate (0.5 level per update) with fractional convergence.
- `smooth-zoom`: the eased front-buffer zoom animation currently covers only discrete zoom input (buttons, scroll wheel, keyboard). It extends to auto-zoom-driven magnification changes (phone), which animate at a longer duration (~500 ms) than discrete input (~250 ms).

## Impact

- `app/src/main/java/com/naviveylin/ui/map/MapCanvasViewModel.kt` — auto-zoom commit block (~lines 905–1000) and its private zoom-commit constants (`ZOOM_COOLDOWN_MS`, `ZOOM_COMMIT_SAMPLES`, `ZOOM_HYSTERESIS_MAG`).
- `app/src/main/java/com/naviveylin/ui/map/MapCanvasScreen.kt` — auto-zoom commits trigger `animateDiscreteZoom`-style easing; animation duration parameterization.
- `auto/src/main/java/com/naviveylin/auto/AutoZoomController.kt` — returns fractional `Double?` target instead of `Int?`; rate limiter replaces stability/cooldown/hysteresis; suspension semantics kept.
- `auto/src/main/java/com/naviveylin/auto/FreeDrivingScreen.kt`, `auto/src/main/java/com/naviveylin/auto/NavigationScreen.kt` — `setViewport` receives the controller's fractional magnification as the fraction argument (currently fed `Int.toDouble()`), mirroring how pinch `zoomStep` already passes `(fraction, int)`.
- Tests: `auto/src/test/java/com/naviveylin/auto/AutoZoomControllerTest.kt` (rewrite), new phone-side commit tests in `app/src/test`, existing `ZoomControlsAnimationTest` harness reused.
- No native code, stylesheet, or persistence format changes.
