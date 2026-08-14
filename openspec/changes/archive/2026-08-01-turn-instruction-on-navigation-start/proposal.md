## Why

When navigation starts, no turn instruction appears until the user has moved some distance along the route. This leaves the driver without guidance at the most critical moment — the start of navigation. The first maneuver (typically "turn left/right onto X") should be shown immediately upon route calculation, not after movement.

## What Changes

- Show first turn instruction immediately when navigation starts, without waiting for position change
- Add `showFirstInstructionOnStart` flag to navigation state
- Wire route start event to trigger instruction display in `NavigationViewModel`
- Update `NavigationScreen` composable to render instruction from route start

## Capabilities

### New Capabilities
- `immediate-turn-instruction`: First turn instruction appears on navigation start, not after movement

### Modified Capabilities

None. No existing spec-level behavior changes — this is a new capability.

## Impact

- `NavigationViewModel` — add state flag + logic to emit first instruction on route start
- `NavigationScreen` composable — consume flag, render instruction immediately
- `NavigationUiState` — add `showFirstInstructionOnStart` field
- No native/JNI changes — pure UI/ViewModel layer
