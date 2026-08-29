# Proposal: smooth-zoom

## Why

Follow mode now pans smoothly (extrapolation + easing, see `smooth-follow`), but zooming is still discontinuous: discrete zoom inputs (buttons, scroll wheel, keyboard) snap the magnification level in one step, and the handoff from the gesture placeholder to the freshly rendered map is a hard cut. `zoom-transition-scaling` only covers scaling during an *active* pinch gesture — there is no animation between the start and end zoom levels anywhere else. Navigation apps animate zoom; NaviVeylin should too.

## What Changes

- Add an eased zoom animation that scales the front buffer from the current displayed magnification toward the target magnification over ~250 ms whenever the zoom level changes without an active pinch gesture (zoom buttons, scroll wheel, keyboard).
- Anchor the geographic point under the zoom focal point (button/keyboard = screen center, scroll wheel = cursor position) so it stays visually fixed during the animation.
- Ease the gesture-end → render handoff: when a pinch gesture ends and the native render completes at the target magnification, blend from the scaled placeholder into the rendered frame instead of a single-frame swap.
- Native render pipeline stays unchanged: 200 ms zoom debounce, render queued while the animation plays; render completion interrupts the placeholder phase of the animation.
- Follow-mode interaction: a zoom animation composes with the smooth-follow display loop (zoom animation scales, follow extrapolation offsets pan). Neither disables the other.
- Zoom controls remain enabled during the animation; a tapped animation retracks smoothly from the current animated scale (no snap-back).

## Capabilities

### New Capabilities

- `smooth-zoom`: Eased front-buffer zoom animation between start and end magnification for discrete zoom inputs and gesture completion, with geographic anchoring, render-completion blending, and follow-mode composition.

### Modified Capabilities

- `zoom-transition-scaling`: The "Native render replaces placeholder at exact target magnification" requirement changes from an atomic placeholder swap to a blend from the animated placeholder into the freshly rendered frame (no single-frame content jump at render completion).
- `zoom-controls`: Tap scenarios change from "scaled placeholder displayed immediately" to "eased zoom animation plays while the debounced render runs"; rapid re-taps retrack the running animation instead of snapping.

## Impact

- **Code**: `app/src/main/java/com/naviveylin/ui/map/` — `MapCanvasScreen.kt` (animation driver, gesture-end handoff, controls/scroll-wheel/keyboard handlers, draw block), `MapRenderer.kt` (front-buffer scale/anchor API consumed by the display loop; possibly the zoom-deferred placeholder path), `MapCanvasViewModel.kt` (zoom input entry points for buttons and follow mode), new animation state helper (e.g., `ZoomAnimation` similar in role to `FollowPrediction` / `smooth-follow` implementation).
- **Specs**: deltas for `zoom-transition-scaling`, `zoom-controls`; new spec `smooth-zoom`.
- **Behavior invariants**: no render-pipeline changes (still debounced, still native-only rendering); navigation engine unaffected; viewport persistence records final magnification only.
- **Tests**: unit tests for the animation easing/anchoring; Compose tests for button-triggered animation retrack; existing placeholder tests updated for blended handoff.