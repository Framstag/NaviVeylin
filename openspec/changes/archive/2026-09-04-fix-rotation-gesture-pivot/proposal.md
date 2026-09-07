## Why

The two-finger rotation gesture rotates the map around the SCREEN CENTER, not around the midpoint of the two fingers. The geographic point under the finger midpoint at gesture start drifts away as the fingers rotate — the map does not behave like standard map apps (Google Maps rotates around the finger midpoint). The screen-center pivot was a deliberate earlier choice (spec "Map stays visible during rotation") to avoid empty regions at large angles, but it makes the gesture feel wrong: the object under the fingers rotates out from under them.

## What Changes

- Rotation pivot becomes the **finger midpoint**: the geographic point under the midpoint at gesture start stays under the midpoint for the whole gesture AND after the committed re-render.
- Live gesture: `graphicsLayer` `transformOrigin` moves from screen center to the finger midpoint; the translation compensation simplifies to the pan term only.
- Commit: the viewport CENTER is adjusted at gesture end so the committed render matches the preview — `newCenter = screenToGeoRotated(M + (1/s)·R(−Δ)·(C − M))` with the pre-gesture viewport (generalizes `zoomAtCursor`; Δ=0 reduces to it). Pure rotation now also moves the center.
- Spec reversal: "Map stays visible during rotation" is relaxed — rotating around an off-center midpoint can expose empty corners at large angles (overrun buffer margin mitigates moderate angles; standard map-app behavior).
- The gesture-end jump fix (derived rotation hold + render-land crossfade) composes: the hold rotates the old bitmap by the angle gap around the midpoint, matching the committed render; the crossfade pivot becomes the midpoint.

## Capabilities

### New Capabilities

- none

### Modified Capabilities

- `map-rotation-gesture`: "Two-finger rotation gesture" — rotation anchored at the finger midpoint (content under midpoint stays fixed during gesture and at commit); "Map stays visible during rotation" scenario relaxed (empty regions possible at extreme angles); "Rotation is re-rendered on gesture end" gains the center adjustment.

## Impact

- `app/src/main/java/com/naviveylin/ui/map/MapCanvasScreen.kt` — graphicsLayer `transformOrigin` = finger midpoint; `gestureTransformTranslation` pivot term removal; gesture-end commit: generalized focal-point center computation (replaces the `zoomAtCursor`-only path); crossfade pivot = midpoint.
- `core/src/main/java/com/naviveylin/core/ProjectionUtils.kt` — new pure helper for the rotate+zoom focal-point commit (or reuse `ProjectedViewport.screenToGeoRotated` + inline math).
- `app/src/test/java/com/naviveylin/ui/map/MapCanvasGestureTransformTest.kt` / new `RotationPivotTest.kt` — midpoint-anchor transform tests + commit math tests.
- `guidelines/MapRendering.md` — rotation pivot contract update (§14 + gesture section).
- Additive behavior change, no breaking API; rollback = revert commit.

## Impact on other changes

- `fix-rotation-gesture-jump` (active): the derived hold + crossfade compose with the midpoint pivot — the crossfade pivot changes from canvas center to the gesture midpoint; no conflict, both changes touch the same gesture-end path (coordinate the apply order).
- Other active changes (`fast-reroute-trigger`, `fix-reroute-route-drawing`, `enlarge-phone-nav-overlays`, `fix-route-refresh-and-auto-zoom-reengage`) do not touch the rotation gesture path.
