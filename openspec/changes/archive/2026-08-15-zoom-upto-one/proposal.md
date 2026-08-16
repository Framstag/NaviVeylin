## Why

The application-wide zoom floor of 4 (`MIN_MAG = 4`) was introduced as the clamp for the two-finger rotation+pinch gesture (change `2026-08-14-fix-two-finger-rotation`: "magnification SHALL be clamped to the application limits (4–20)"). That gesture-specific limit currently also caps the zoom control buttons, keyboard `-`/`+` keys, and scroll wheel, so the user cannot zoom out to the low levels that libosmscout/OpenStreetMap support (zoom 0 = whole world, zoom 1 = half the world). The zoom control should be able to reach magnification 1; the pinch/rotation gesture keeps its own 4–20 clamp.

## What Changes

- Split the zoom range into two distinct limits:
  - **Zoom control** (+/− buttons, keyboard `-`/`+` keys, mouse scroll wheel): magnification range **1–20**.
  - **Pinch/rotation gesture**: magnification range **4–20** (unchanged behavior, per `map-rotation-gesture`).
- Lower `MIN_MAG` from `4` to `1` in `MapCanvasViewModel` (controls the zoom control / programmatic zoom path via `updateMagnification()`).
- Introduce `GESTURE_MIN_MAG = 4` in `MapCanvasViewModel`; the gesture commit clamp in `MapCanvasScreen.kt` uses it instead of `MIN_MAG`, so pinch-out below 4 stays impossible.
- Zoom out button (`canZoomOut`) stays enabled down to magnification 1 in both portrait and landscape layouts; zoom level label shows the lower values.
- Mouse scroll-wheel zoom clamps to the control range (1–20), matching the buttons.
- Auto-zoom is unaffected (speed table targets 12–17, always inside both ranges).

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `zoom-controls`: The zoom out button SHALL be disabled only at magnification 1 (was 4); the control range becomes 1–20.
- `map-rotation-gesture`: The gesture clamp is no longer the "application limits (4–20)" — the gesture keeps its own dedicated 4–20 clamp while the application/control range widens to 1–20.

## Impact

- `app/src/main/java/com/naviveylin/ui/map/MapCanvasViewModel.kt` — `MIN_MAG` 4 → 1, new `GESTURE_MIN_MAG = 4`, `updateMagnification()` clamp range (companion constants block, ~line 1827).
- `app/src/main/java/com/naviveylin/ui/map/MapCanvasScreen.kt` — gesture commit clamp uses `GESTURE_MIN_MAG` (~line 357); scroll-wheel clamp stays on `MIN_MAG` (~line 403); `canZoomOut` for both layout branches (~lines 653, 833).
- Spec deltas: `openspec/specs/zoom-controls/spec.md`, `openspec/specs/map-rotation-gesture/spec.md`.
- No native/JNI changes; no new dependencies. Floor is 1 (matches change name); 0 (full OSM range) would be a one-constant change if desired.
