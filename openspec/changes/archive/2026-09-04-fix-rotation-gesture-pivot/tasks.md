# Tasks: Fix rotation gesture pivot — anchor at the finger midpoint

Parent spec: `map-rotation-gesture` (specs/map-rotation-gesture/spec.md — "Rotation anchored at the finger midpoint", "Map stays visible during rotation", "Rotation is re-rendered on gesture end"). Design: design.md.

## 1. Live gesture pivot (spec: map-rotation-gesture — Rotation anchored at the finger midpoint)

- [x] 1.1 In `MapCanvasScreen.kt`, change the graphicsLayer `transformOrigin` from `(0.5f, 0.5f)` to the finger midpoint as a fraction of the layer size (`gesturePivot.x / width`, `gesturePivot.y / height`; fall back to screen center when the pivot is unset/zero); update the graphicsLayer KDoc (rotation now pivots at the finger midpoint, not the screen center); verify compile via `./gradlew :app:compileMobileDebugKotlin`
- [x] 1.2 Update `gestureTransformTranslation` so the pivot-reconciliation term vanishes when the rotation pivot equals the zoom pivot (the midpoint): the formula reduces to `T = (1/s)·R(−θ)·D` (pan only); keep the existing behavior for the screen-center fallback; verify the existing `MapCanvasGestureTransformTest` cases still pass (pure-rotation-no-translation, pan-preserved, pan-not-amplified) and add a midpoint-anchor case: content under the midpoint stays fixed under rotate+zoom
- [x] 1.3 Verify the map stays covering for moderate angles: with the overrun buffer margin, a rotation up to ~30-40° around a midpoint near the screen edge shows no empty regions (spec: Map stays visible during rotation — moderate angles); no code change expected beyond the pivot — verify by reasoning + on-device

## 2. Commit: generalized focal-point center adjustment (spec: map-rotation-gesture — Rotation anchored at the finger midpoint, Rotation is re-rendered on gesture end)

- [x] 2.1 Add a pure helper in `core/src/main/java/com/naviveylin/core/ProjectionUtils.kt` (e.g. `rotateZoomAtFocalPoint(focalX, focalY, oldMag, newMag, rotationDelta, viewW, viewH, centerLat, centerLon, angle, dpi)`): computes `p = M + (1/s)·R(−Δ)·(C − M)` then `screenToGeoRotated(p)` with the pre-gesture viewport; Δ=0 must reduce to the `zoomAtCursor` result; verify compile
- [x] 2.2 In `onRenderRequested` (MapCanvasScreen.kt), replace the `zoomAtCursor`-only commit with the generalized helper: run it whenever rotation OR zoom changed (pure rotation now also moves the center); keep the `updateAngle`/`updateMagnification`/`updateCenter`/`renderMap(forceFullRender = true)` sequence; verify compile
- [x] 2.3 Add `RotationPivotTest.kt` under `app/src/test/java/com/naviveylin/ui/map/` (Robolectric, default sandbox per AGENTS.md classloader rule): (a) pure rotation around an off-center midpoint — the geo under the midpoint stays under it after the commit math; (b) Δ=0 → equals `zoomAtCursor`; (c) combined rotate+zoom keeps the anchor; (d) gesture transform keeps the geo under the midpoint fixed during the gesture; verify `./gradlew :app:testMobileDebugUnitTest --tests "*RotationPivotTest*"` passes

## 3. Crossfade pivot (spec: map-rotation-gesture — No temporary angle jump on gesture end, Rotation anchored at the finger midpoint)

- [x] 3.1 Capture the gesture midpoint at gesture end (before the gesture state resets) into a `crossfadePivot` state; pass it to `drawFrontFrame` so the render-land crossfade rotates the old frame about the midpoint (not the canvas center); verify compile
- [x] 3.2 Verify the jump-fix tests still pass: `./gradlew :app:testMobileDebugUnitTest --tests "*RotationHandoffTest*"` (the derived hold is pivot-agnostic — the gap rotation follows the graphicsLayer pivot)

## 4. Guidelines update

- [x] 4.1 Update `guidelines/MapRendering.md` (§14 + gesture section): rotation pivot = finger midpoint; the gesture-end commit adjusts the viewport center so the anchor holds; the render-land crossfade rotates about the midpoint; empty corners at extreme angles are accepted (overrun margin covers moderate angles)

## 5. Build and regression verification

- [x] 5.1 Verify full build compiles without errors: `./gradlew :app:assembleMobileDebug` (build-app skill)
- [x] 5.2 Verify existing tests still pass: `./gradlew test` (run-tests skill), including `MapCanvasGestureTransformTest.kt`, `RotationHandoffTest.kt`, and navigation tests
- [x] 5.3 Confirm the sibling changes are unaffected (`fix-rotation-gesture-jump` shares the gesture-end path — coordinate apply order; `fast-reroute-trigger`, `fix-reroute-route-drawing`, `enlarge-phone-nav-overlays`, `fix-route-refresh-and-auto-zoom-reengage` untouched; code review diff check)

## 6. On-device verification (device, phone scope)

- [x] 6.1 Two-finger rotation around an off-center object: the object stays under the fingers for the whole gesture AND after the committed re-render (no drift, no jump) (spec: Rotation anchored at the finger midpoint)
- [x] 6.2 Combined rotate+zoom: the anchor (geo under the midpoint) stays fixed through the combined gesture and the commit
- [x] 6.3 Moderate rotation angles keep the map covering (no empty regions within the overrun margin); large angles around an edge midpoint may show empty corners (accepted per spec)
- [x] 6.4 The gesture-end jump fix still holds: no temporary jump back to the pre-gesture angle, no twist when the re-render lands (logcat `rotation: render landed ... crossfade=true`)

## 7. Defect found on device: live pivot re-anchors the accumulated rotation (erratic jumps during back-and-forth rotations)

On-device testing (6.1) reported the map jumping erratically DURING longer, back-and-forth rotations. Root cause: the implementation set `transformOrigin` to the LIVE finger midpoint (`gesturePivot = gestureCentroid` on every `onGestureCentroid`), but the translation compensation `T = (1/s)·R(−θ)·D` (design D1) is the FIXED-pivot formula. With a moving pivot the RenderNode matrix `p' = P + s·R(θ)·(p − P + T)` expands to `p' = P + s·R(θ)·(p − P) + D`, so a centroid drift δ between frames jumps the map by `(2I − s·R(θ))·δ` — up to 3× the drift at 180°, direction rotating with the accumulated angle (the archived `2026-08-15-bugfix-rotation` defect, re-introduced).

- [x] 7.1 Freeze the rotation/zoom pivot at the gesture-START midpoint: `gesturePivot` is set only on the first `onGestureCentroid` of a gesture (new `gestureActive` flag, cleared in `onRenderRequested`); the centroid drift is carried by the pan compensation. The frozen pivot makes `T = (1/s)·R(−θ)·D` correct (any fixed pivot), the display matches the commit exactly, and the per-frame jump reduces to the pure drift δ. Verify compile
- [x] 7.2 Capture the gesture-END midpoint into `crossfadePivot` in `onRenderRequested` (before the gesture state resets) and use it as the rotation-hold `transformOrigin` and the render-land crossfade pivot — the commit's focal point (D2) — so the held/crossfaded old frame aligns with the committed render; clear it when the crossfade completes
- [x] 7.3 Add `panFollowsPivotInScreenSpaceAtAnyRotation` to `MapCanvasGestureTransformTest`: the content under the pivot follows the pan by exactly D at any rotation/zoom (pins the frozen-pivot model); verify `./gradlew :app:testMobileDebugUnitTest --tests "*MapCanvasGestureTransformTest*" --tests "*RotationPivotTest*" --tests "*RotationHandoffTest*"` passes
- [x] 7.4 Verify the full map-package unit suite passes: `./gradlew :app:testMobileDebugUnitTest --tests "com.naviveylin.ui.map.*"`
- [x] 7.5 Re-verify on device: back-and-forth rotation with finger drift stays smooth (no erratic jumps); the anchor holds through the gesture and the commit
