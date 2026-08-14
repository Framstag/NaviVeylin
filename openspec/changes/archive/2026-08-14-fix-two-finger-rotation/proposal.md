# Fix Two-Finger Rotation

## What Changes

The two-finger rotation gesture on the map canvas is broken: performing it produces a zoom-like effect instead of rotating the map. The gesture code exists but has multiple defects:

1. **Pan fires during rotation** — the pan branch tracks the first finger's movement, and during a rotation that finger moves, so the map center drifts while the user rotates.
2. **Zoom fires on small finger-distance changes** — the zoom threshold (3%) is too sensitive, so the natural distance jitter of a rotation gesture triggers zoom. The zoom delta logic is also inverted for the 1.03–1.05 range (zooms out instead of in).
3. **Rotation renders are slow** — any non-zero angle bypasses the fast tile-cache render path and uses a full native render at 1.2× overrun with a 200 ms debounce, so rotation appears laggy or not at all, while zoom (tile path) is instant. The user perceives "zoom like effect".
4. **"Always north" is not disabled** — the gesture disengages follow mode but leaves `freeFormNorthUp`/`navNorthUp` set, so re-engaging follow mode snaps the map back to north-up and the compass still reports north-up.

This change fixes the gesture so a two-finger rotation rotates the map in real time, does not pan or zoom spuriously, and disables "always north" when the user manually rotates.

## Capabilities

### New Capabilities

- None.

### Modified Capabilities

- `map-rotation-gesture`: The two-finger rotation gesture must rotate the map in real time without triggering pan or zoom, and must disengage "always north" orientation (in addition to follow mode) when used.

## Impact

- `app/src/main/java/com/naviveylin/ui/map/MapCanvasScreen.kt` — gesture handler: multi-touch pan via centroid, rotation via finger angle delta, zoom via distance ratio with corrected threshold/delta logic.
- `app/src/main/java/com/naviveylin/ui/map/MapCanvasViewModel.kt` — new method to disengage follow mode and clear the active north-up flag on manual rotation.
- `app/src/main/java/com/naviveylin/ui/map/MapRenderer.kt` — tile-cache render path supports rotation (compose cached north-up tiles with a rotation transform) so rotated renders are fast; shorter debounce for angle-only changes.
- `core/src/main/java/com/naviveylin/core/ProjectionUtils.kt` — rotation-aware drag delta for centroid panning in a rotated viewport.
- Tests: extend `ProjectionUtilsTest` (rotated drag delta), add renderer test for rotated tile composition, add ViewModel test for north-up disengagement on manual rotation.
