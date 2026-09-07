# Design: Fix rotation gesture temporary angle jump

## Context

Two-finger rotation applies the visual angle via `graphicsLayer.rotationZ = gestureRotation` on top of the current front buffer (rendered at the *old* committed angle). At gesture end (`onRenderRequested`, `MapCanvasScreen.kt:731-795`) the sequence is:

1. `updateAngle(newAngle)` — state only, no render
2. `gestureRotation = 0f` (L787) — visual rotation removed immediately
3. `renderMap(forceFullRender = true)` — async: 50 ms rotate debounce (`MapRenderer.kt:48`) + background render (tile path or full render, 100 ms+)

Between step 2 and the render landing, the display shows the old bitmap (old angle) unrotated → temporary jump back to the pre-gesture angle, then restore when the new frame lands.

A first implementation fixed this with a stored hold (`rotationHold = gestureRotation` until the frame loop cleared it) but left a residual “little jump” at gesture end: the frame loop clears the stored hold from a direct `viewModel.uiState` read while the draw reads the collected composition state — when the render lands between those two reads, exactly one frame draws the new bitmap with the stale rotation applied (a double-rotation twist).

The pinch-to-zoom path already solved this exact problem: at gesture end it sets `zoomAnchor = gestureCentroid; zoomAnimScale = gestureZoom` (L784-785) so the display keeps showing `frontBuffer × gestureZoom`; the frame loop (L347-366) resets `zoomAnimScale = 1f` (or crossfades) when the front buffer swaps to the committed magnification. Rotation has no equivalent hold.

The committed render rotates around the viewport center; the graphicsLayer rotates around the screen center (`transformOrigin 0.5, 0.5`, L628). The existing comment (L608-616) documents that these match, so holding `rotationZ` until the new-angle frame lands is seamless — same mechanism as zoom.

## Decision D1: Rotation display-layer hold — derive, don't store

**Chosen: a hold FLAG, with the display rotation angle DERIVED per draw as `committedAngle − frontBufferAngle` (gap), computed from the same collected state the draw reads.**

At gesture end the flag (`rotationHoldActive`) is armed; the graphicsLayer computes `theta = gestureRotation + (holdActive ? committedAngle − frontBufferAngle : 0)` via the pure `rotationDisplayTheta`. While the re-render is in flight the front buffer is still at the old angle, so the gap keeps the display at the final angle. When the committed render lands, the front buffer angle becomes the committed angle → gap = 0 → theta = 0, **atomically with the bitmap swap** (both read the same collected `state`), so there is NO one-frame window that draws the new bitmap with a stale rotation (the residual “little jump” observed on-device with the first implementation — a draw-time race: the frame loop cleared the stored hold from a stale `viewModel.uiState` read while the draw already showed the new bitmap).

The stored alternative (keep `rotationHold = gestureRotation` until the frame loop clears it) is WRONG: the clear and the bitmap swap are read from different state sources (frame loop direct read vs collected composition state), so the render landing between the two leaves exactly one frame displaying the new bitmap rotated by the full gesture angle — a visible double-rotation twist.

**Alternative (rejected): synchronous render at gesture end** — blocks the UI thread, violates the responsiveness requirement, janky on slow renders.

**Risk:** the gap is wrapping-safe (rotating by `gap ≡ rotating by the accumulated gesture rotation` mod 2π); if the committed render is never emitted (epoch/mag mismatch at `MapRenderer.kt:828`), the gap stays non-zero and the display keeps showing the correct committed view via the rotation — harmless, disarmed by the next gesture start.

## Decision D2: When the hold disarms + render-land crossfade

**Chosen: disarm when the front buffer reaches the committed angle, on the next gesture start; the render-land swap is covered by a rotation crossfade (mirror of the zoom crossfade).**

Frame loop tracks `lastFrontAngle` (analogue of `lastFrontMag` in the zoom handoff); when the front buffer changes TO the committed angle while the flag is armed, the hold disarms and the previous frame (old bitmap, rotated by `committed − lastFrontAngle`) is captured as a crossfade that fades into the new native render over `CROSSFADE_MS`. The crossfade masks any tile-path-vs-native-rasterization difference between the rotated preview and the fresh render (the zoom path crossfades for exactly this reason). The captured old frame is drawn rotated about the canvas center (new `rotationDegrees` param of `drawFrontFrame`), which equals the native render's buffer-center rotation since the frame is drawn centered.

**Alternative (rejected): disarm only on front-buffer match, no crossfade, no next-gesture fallback** — a stale hold could leak into the start of the next gesture, and the instant frame swap shows any rasterization difference as a jump.

**Edge case:** committed angle normalizes to the same value as the front buffer angle (e.g. full 360° rotation) → the gap is zero immediately; the visual is already correct. No jump, nothing stuck.

## Decision D3: Interaction with the zoom handoff

**Chosen: no change — the two holds compose independently.**

A combined rotate+zoom gesture arms both the rotation gap and `zoomAnimScale` at gesture end; the graphicsLayer applies `rotationZ` and `scaleX/Y` together (L625-627), both holds clear on their own condition, and the render-land crossfade carries both `crossfadeScale/anchor` and `crossfadeAngle` (the rotation crossfade only writes `crossfadeAngle` when the zoom branch already captured the old frame).

## Threading model

All changes live in the Compose UI thread: `onRenderRequested` (gesture end), the graphicsLayer block, and the frame loop (`LaunchedEffect`, L294-445). The render pipeline (`MapRenderer` debounce + background render loop) is unchanged and stays off the UI thread. No new dispatchers, no new lifecycle handling.

## Files changed

- `app/src/main/java/com/naviveylin/ui/map/MapCanvasScreen.kt` — gesture-end commit (L731-795): arm the hold flag; graphicsLayer (L617-629): `theta = rotationDisplayTheta(...)` derived from collected state; frame loop (L294-445): `lastFrontAngle` tracking, disarm + rotation crossfade capture on render land; `drawFrontFrame` new `rotationDegrees` param; new `crossfadeAngle` state; `onRotate`/`onGestureCentroid` disarm.
- `app/src/main/java/com/naviveylin/ui/map/MapRenderer.kt` — read-only reference; no changes.
- `app/src/test/java/com/naviveylin/ui/map/RotationHandoffTest.kt` — pure-function tests for `rotationDisplayTheta` (hold keeps final angle, zeroes at render land, wrapping-safe, unknown-front-buffer fallback, zoom independence).
- `guidelines/MapRendering.md` — rotation display-layer handoff contract (§14).

## Verification

- Unit (Robolectric): `rotationDisplayTheta` — hold keeps the final angle while the front buffer differs, zeroes atomically when it matches, wrapping-safe for large single-gesture rotations, falls back to gesture rotation with no front buffer, independent of zoom state; full-suite regression (`./gradlew test`).
- Build: `./gradlew :app:assembleMobileDebug` (build-app skill).
- On-device: two-finger rotation on a real device — NO temporary jump back to the pre-gesture angle at gesture end and NO residual twist/jump when the re-render lands (the derived gap zeroes atomically with the bitmap swap; the crossfade masks any tile-vs-native rasterization difference); logcat shows `rotation: render landed ... crossfade=true`; repeat with a slow render (dense area) and a large one-gesture rotation (wrapping) to confirm the hold spans the full render duration.
