## Why

Live tests show speed readings during routing are unreliable: standing at a traffic light still shows significant speed, and driving at 100 km/h sometimes shows no speed at all. The native `SpeedAgent` computes speed from GPS position differences rather than using the GPS-reported speed directly, causing lag at stops and gaps when position updates are infrequent.

## What Changes

- **SpeedAgent** (`libosmscout`): Prefer GPS-reported speed (`currentSpeed` from `GPSUpdateMessage`) over position-difference computation. Fall back to position-diff only when GPS speed is unavailable.
- **MapCanvasViewModel** (Kotlin): Pass `-1.0` for speed when `Location.hasSpeed()` is false, so native code can distinguish "no GPS speed" from "standing still".
- **NavigationStateOverlay** (Kotlin): Show current speed in red when it exceeds max allowed speed by 5+ km/h, normal color otherwise.
- No breaking changes — all existing APIs and contracts remain unchanged.

## Capabilities

### New Capabilities
- `gps-speed-priority`: Use GPS-reported speed as the primary source for navigation speed, with position-difference fallback when GPS speed is unavailable.

### Modified Capabilities
- `speed-spike-filtering`: The spike filter (reject > 150 km/h) already exists in `MapCanvasViewModel.filterSpeed()`. No requirement changes — the filter continues to work on the now-more-accurate speed values.
- `auto-speed-zoom`: No requirement changes — auto-zoom already consumes `onCurrentSpeed` and will benefit from more accurate input.
- `navigation-state-display`: Speed display now uses red color when current speed exceeds max allowed speed by 5+ km/h, providing visual overspeed warning.

## Impact

| Area | Files |
|------|-------|
| Native C++ | `libosmscout/src/osmscout/navigation/SpeedAgent.cpp` |
| Kotlin | `app/.../ui/map/MapCanvasViewModel.kt`, `app/.../ui/navigation/NavigationStateOverlay.kt` |
| Dependencies | None — no new libraries |
| Build | Rebuild native libs via CMake |
