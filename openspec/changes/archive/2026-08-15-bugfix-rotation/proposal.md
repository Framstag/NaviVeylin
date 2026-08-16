## Why

Two-finger rotation does nothing on a real device. The gesture handler filters each event's angle delta with a fixed `0.05 rad` (2.9°) threshold, but high-refresh-rate touch sampling delivers per-event deltas far below that (a 90° rotation over 1 s at 120 Hz is ~0.013 rad/event), so `onRotate` never fires. The emulator check passed only because emulator multi-touch sends coarse events and the Compose test rotates 9°/step.

## What Changes

- Replace the per-event rotation threshold in `MapGestures.kt` with an accumulator: raw angle deltas are summed per gesture and reported when the accumulated rotation exceeds a small threshold (`0.01 rad`), with any residual flushed on gesture end.
- Add a Compose UI test reproducing the real-device scenario (many small per-event deltas) that fails on the old threshold and passes with the accumulator.

## Capabilities

### New Capabilities

- none

### Modified Capabilities

- `map-rotation-gesture`: rotation must respond to slow rotations whose per-event angle deltas are small (high-refresh-rate devices), not just fast/coarse gestures.

## Impact

- `app/src/main/java/com/naviveylin/ui/map/MapGestures.kt` — rotation reporting logic.
- `app/src/test/java/com/naviveylin/ui/map/MapGestureComposeTest.kt` — new regression test.
- No native, JNI, renderer, or ViewModel changes.
