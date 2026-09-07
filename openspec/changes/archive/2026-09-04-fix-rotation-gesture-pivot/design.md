# Design: Fix rotation gesture pivot — anchor at the finger midpoint

## Context

The two-finger rotation gesture currently rotates the map around the SCREEN CENTER: the graphicsLayer uses `transformOrigin(0.5f, 0.5f)` and the gesture-end commit leaves the viewport center unchanged for a pure rotation. The geographic point under the finger midpoint drifts away as the fingers rotate (unless the midpoint happens to be the screen center). This was a deliberate earlier choice — the spec scenario "Map stays visible during rotation" mandated screen-center rotation so the map never swings off-screen — but it contradicts standard map UX (Google Maps rotates around the finger midpoint).

The gesture-end jump fix (`fix-rotation-gesture-jump`, active) introduced a derived rotation hold (`rotationDisplayTheta` = committed − front-buffer angle gap) and a render-land crossfade. Both compose with a midpoint pivot: the hold rotates the old bitmap by the gap around the pivot, and the committed render is computed so it matches that exact transform.

## Decision D1: Live gesture pivot = finger midpoint

**Chosen: `transformOrigin` = the finger midpoint (as a fraction of the layer size), rotation around it; the translation compensation reduces to the pan term.**

The graphicsLayer RenderNode matrix is `M = T(pivot)·S·R·T(−pivot)·T(translation)` with the translation in local (pre-scale/rotate) space. With the rotation pivot AND the zoom pivot both at the midpoint `M` (the centroid of the two fingers — the same point `zoomAtCursor` uses for zoom), the desired visual is `p' = D + M + s·R(θ)·(p − M)` (pan `D` in screen space), which solves to `T = (1/s)·R(−θ)·D`. The current `gestureTransformTranslation` formula `T = (1/s − 1)·(C − P) + (1/s)·R(−θ)·D` reduces to exactly this when `P = C = M` — the `(1/s − 1)·(C − P)` pivot-reconciliation term vanishes.

**Alternative (rejected): keep screen-center rotation** — the current behavior; the object under the fingers drifts, which is the reported problem.

**Alternative (rejected): rotate around the midpoint but keep the commit center unchanged** — the committed render would not match the preview (the anchor would jump at gesture end).

**Risk:** rotating around an off-center pivot swings the map off-screen at large angles (empty corners). Mitigated by the overrun buffer (1.2× margin) for moderate angles; accepted for extreme angles (standard map-app behavior, see D3).

## Decision D2: Commit adjusts the viewport center (generalized focal-point commit)

**Chosen: at gesture end, compute the new center so the geo point under the midpoint stays fixed under the combined rotate+zoom, then render.**

The committed viewport must equal the pre-gesture viewport transformed by the gesture (rotate `Δ` around `M`, scale `s` around `M`). The geo point that ends up at the screen center `C` is the one that was at screen position `p = M + (1/s)·R(−Δ)·(C − M)` before the gesture, so:

```
p = M + (1/s)·R(−Δ)·(C − M)          // screen space, s = 2^(newMag − oldMag)
newCenter = oldViewport.screenToGeoRotated(p.x, p.y)
updateAngle(A + Δ); updateCenter(newCenter); updateMagnification(newMag); renderMap(forceFullRender = true)
```

This generalizes `ProjectionUtils.zoomAtCursor` (the Δ = 0 case reduces to it exactly: `p = M + (1/s)(C − M)` keeps the geo under the cursor fixed under a pure zoom). The commit runs whenever rotation OR zoom changed (pure rotation now also moves the center). The old viewport for `screenToGeoRotated` is built from the state BEFORE `updateAngle`/`updateCenter` (the state still holds the pre-gesture angle/center at that point; the pan applied during the gesture via `onCentroidPan` is already in the state's center).

**Alternative (rejected): keep `zoomAtCursor` for zoom and add a separate rotation-center adjustment** — two sequential center moves are order-dependent and error-prone; the single generalized formula handles rotate+zoom+pan in one step.

**Risk:** the pan conversion during the gesture uses the pre-gesture angle (`dragDeltaToNewCenterRotated` with `s.viewport.angle`), so a combined rotate+pan gesture's pan is converted in the old-angle frame while the render is at the new angle — a small pan error for combined gestures. Pre-existing behavior, out of scope; noted for a follow-up.

## Decision D3: Map-covering tradeoff

**Chosen: accept empty corners at extreme angles (standard map-app behavior); the overrun margin covers moderate angles.**

The spec scenario "Map stays visible during rotation" is relaxed: the map SHALL cover the canvas for moderate angles (within the overrun margin) and MAY show empty regions at large angles around an off-center midpoint. Google Maps behaves the same way. The alternative — clamping the pivot toward the screen center as the angle grows — is non-standard and feels unpredictable.

**Risk:** a user rotating 180° around a midpoint near the screen edge sees empty corners. Accepted; the re-render at gesture end re-centers the viewport so the map covers again after the gesture.

## Decision D4: Interaction with the gesture-end jump hold and crossfade

**Chosen: the derived hold and the render-land crossfade keep working; the crossfade pivot becomes the midpoint.**

- The hold (`rotationDisplayTheta` = committed − front-buffer angle gap) rotates the old bitmap by the gap around the graphicsLayer pivot — now the midpoint. The committed render is computed (D2) to be exactly the old viewport rotated by `Δ` around `M`, so the held preview and the committed frame match.
- The render-land crossfade (`crossfadeAngle` in `drawFrontFrame`) currently rotates the old frame about the canvas center; it must rotate about the gesture midpoint instead. The midpoint is captured at gesture end (before the gesture state resets) into a `crossfadePivot` state, and `drawFrontFrame` gains a rotation-pivot parameter.

**Risk:** if the pivot change lands before the jump fix, the crossfade pivot is the canvas center (current behavior) — still correct for the screen-center pivot; the two changes must be applied in coordination (jump fix first, then pivot, or together).

## Threading model

All changes are in the Compose UI thread (gesture callbacks, graphicsLayer, frame loop) plus the pure commit math (no new dispatchers). The render pipeline is unchanged.

## Files changed

- `app/src/main/java/com/naviveylin/ui/map/MapCanvasScreen.kt` — graphicsLayer `transformOrigin` = midpoint fraction; `gestureTransformTranslation` pivot term removal (or pass `P = C = M`); gesture-end commit: generalized focal-point center computation replacing the `zoomAtCursor`-only path; capture `crossfadePivot` at gesture end; `drawFrontFrame` rotation-pivot parameter.
- `core/src/main/java/com/naviveylin/core/ProjectionUtils.kt` — new pure helper `rotateZoomAtFocalPoint(...)` (or inline the math in the commit using `ProjectedViewport.screenToGeoRotated`).
- `app/src/test/java/com/naviveylin/ui/map/` — `RotationPivotTest.kt`: midpoint-anchor transform tests (content under midpoint stays fixed during the gesture transform) + commit math tests (pure rotation around an off-center midpoint keeps the anchor; Δ=0 reduces to `zoomAtCursor`; combined rotate+zoom).
- `guidelines/MapRendering.md` — rotation pivot contract update (§14 + gesture section): pivot = finger midpoint, commit center adjustment, crossfade pivot.

## Verification

- Unit (Robolectric): `RotationPivotTest` — gesture transform keeps the geo under the midpoint fixed; commit math: pure rotation around an off-center midpoint → new center keeps the anchor; Δ=0 → equals `zoomAtCursor`; combined rotate+zoom; full-suite regression (`./gradlew test`).
- Build: `./gradlew :app:assembleMobileDebug` (build-app skill).
- On-device: two-finger rotation around an off-center object — the object stays under the fingers for the whole gesture AND after the committed re-render (no drift, no jump); combined rotate+zoom keeps the anchor; moderate angles keep the map covering; large angles may show empty corners (accepted); the gesture-end jump fix still holds (no temporary jump back, no twist).
