## Why

When a two-finger rotation gesture ends, the map temporarily jumps back to the original rotation angle and then restores to the new angle. The visual rotation is applied only via the live gesture transform (`graphicsLayer.rotationZ = gestureRotation`), which is reset to zero at gesture end *before* the async re-render at the committed angle lands — so the old bitmap (rendered at the old angle) is shown unrotated for the render duration. The pinch-to-zoom path already solves this exact problem with a display-layer handoff (`zoomAnimScale` persists until the front buffer swaps to the committed magnification); rotation has no equivalent.

## What Changes

- Add a rotation display-layer hold: at gesture end, keep the accumulated rotation angle applied visually (mirroring the zoom `zoomAnimScale` handoff) until the front buffer swaps to the committed angle, then clear it.
- Clear the hold when the front buffer angle matches the committed viewport angle (frame-loop check, same pattern as the zoom handoff at `MapCanvasScreen.kt:347-366`).
- Handle the render-not-emitted edge case (epoch/mag mismatch at `MapRenderer.kt:828`): the hold must not stick forever — clear on next gesture start or via a bounded timeout.
- No change to the committed angle math, the debounce, or the render pipeline itself.

## Capabilities

### New Capabilities

- none

### Modified Capabilities

- `map-rotation-gesture`: the "Two-finger rotation gesture" requirement gains the no-temporary-jump behavior — the display SHALL keep the final rotation angle from gesture end until the re-render at that angle lands (no intermediate frame at the pre-gesture angle).

## Impact

- `app/src/main/java/com/naviveylin/ui/map/MapCanvasScreen.kt` — gesture-end commit (`onRenderRequested`, ~L731-795): hold rotation instead of zeroing `gestureRotation` immediately; frame loop (~L294-445): clear the hold when `renderViewport.angle == viewport.angle`; graphicsLayer (~L617-629): apply held rotation.
- `app/src/main/java/com/naviveylin/ui/map/MapRenderer.kt` — read-only reference (front buffer angle already exposed via `RenderViewport.angle` / `renderedAngle`); no changes expected.
- Unit tests: `app/src/test/java/com/naviveylin/ui/map/MapCanvasGestureTransformTest.kt` (or a new rotation-handoff test) — gesture end keeps the visual angle until the front buffer matches, then clears; render-not-emitted fallback clears the hold.
- Guidelines: `guidelines/MapRendering.md` — document the rotation display-layer handoff contract next to the zoom one.
- Additive, no breaking changes, no rollback path needed beyond reverting the commit.

## Impact on other changes

- None of the four active changes (`fast-reroute-trigger`, `fix-reroute-route-drawing`, `enlarge-phone-nav-overlays`, `fix-route-refresh-and-auto-zoom-reengage`) touch the rotation gesture path.
