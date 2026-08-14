## Why

During turn-by-turn navigation, the map zoom stays fixed at whatever the user last set. At highway speed, zoomed-in views scroll frantically and hide upcoming turns. At walking speed, zoomed-out views make streets and paths tiny. The driver must manually zoom in/out as speed changes — distracting and unsafe. JavaScout already implements speed-based auto-zoom, turn-aware zoom boosting, and speed spike filtering. NaviVeylin needs the same.

## What Changes

- Add speed-to-magnification mapping with linear interpolation between configurable breakpoints
- Add turn-aware zoom boosting: boost magnification when approaching a turn, hold past it
- Add speed spike rejection: reject speed values > 150 km/h, use last known good speed
- Add auto-zoom toggle in navigation UI (independent of follow mode)
- Add manual zoom suspension: user manual zoom temporarily suspends auto-zoom, re-engages on speed band change
- Add smooth zoom transitions: max 1 magnification level change per position update
- No changes to JNI layer, C++ code, or build files

## Capabilities

### New Capabilities
- `auto-speed-zoom`: Map magnification adjusts automatically based on navigation speed during follow mode, using a configurable speed-to-magnification lookup table with linear interpolation
- `auto-turn-zoom`: Map magnification boosts when approaching a turn (≥ 16 within 300m, ≥ 15 within 300-600m) and holds until 600m past the turn
- `speed-spike-filtering`: Speed values exceeding 150 km/h are rejected and replaced with the last known good speed

### Modified Capabilities
<!-- No existing NaviVeylin specs change — these are entirely new capabilities -->

## Impact

- `app/src/main/java/com/naviveylin/navigation/NavigationViewModel.kt` — store last speed, expose auto-zoom state, wire turn distance from route instructions
- `app/src/main/java/com/naviveylin/ui/map/MapCanvasViewModel.kt` — add auto-zoom computation in follow-mode position handler, manual zoom suspension, smooth transition logic
- `app/src/main/java/com/naviveylin/ui/map/MapCanvasScreen.kt` — add auto-zoom toggle UI, wire manual zoom events to suspension
- `app/src/main/java/com/naviveylin/ui/map/ZoomControls.kt` — report manual zoom events for suspension
- `app/src/main/java/com/naviveylin/ui/navigation/NavigationStateOverlay.kt` — optional auto-zoom toggle display
- `app/src/main/java/com/naviveylin/ui/navigation/NextTurnOverlay.kt` — expose turn distance for turn-aware zoom
- No changes to `libosmscout-client-java` JNI layer, C++ code, or Gradle build files
