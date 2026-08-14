## 1. Native C++: SpeedAgent GPS priority

- [x] 1.1 Modify `SpeedAgent::Process` to prefer `gpsUpdateMsg->currentSpeed` when ≥ 0, convert m/s → km/h, emit `CurrentSpeedMessage` directly
- [x] 1.2 Clear `segmentFifo` when GPS speed < 0.5 m/s to prevent lingering speed after stop
- [x] 1.3 Keep position-diff fallback when `currentSpeed < 0` (GPS speed unknown)

## 2. Kotlin: Correct speed-unknown signaling

- [x] 2.1 Change `MapCanvasViewModel` location feed to pass `-1.0` when `!loc.hasSpeed()`, honoring the native contract

## 4. UI: Red speed on overspeed

- [x] 4.1 Show current speed in red in `NavigationStateOverlay` when it exceeds max allowed speed by 5+ km/h, normal color otherwise

- [x] 3.1 Rebuild native libs: run CMake build and verify `libosmscout_client_java.so` compiles for all ABIs
- [x] 3.2 Run `./gradlew :app:assembleDebug` and verify no build errors
- [x] 3.3 Run existing unit tests and verify they pass
- [x] 3.4 Verify speed accuracy in simulator with GPX track playback: speed should drop to 0 immediately on stop, and show correct speed at 100 km/h

  **Manual verification steps:**
  1. Load a GPX track with known speed profile (includes stop at traffic light + 100 km/h section)
  2. Start navigation on the route
  3. Observe speed display: should drop to 0 immediately when track stops
  4. Observe speed display: should show ~100 km/h during highway section
  5. If issues persist, check `adb logcat -s NavigationVM SpeedAgent` for debug output
