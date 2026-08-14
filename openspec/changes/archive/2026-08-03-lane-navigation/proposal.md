## Why

Upstream libosmscout now computes lane-level guidance (lane count, per-lane turn arrows, suggested lane) via `LaneAgent` and exposes it through the JNI bridge (`NavigationListener.onLaneUpdate` with `LaneTurn[]`). JavaScout already renders lane hints in the next-turn overlay. NaviVeylin's `NextTurnOverlay` ignores lane data entirely — users navigating with NaviVeylin cannot see which lane to use at complex junctions.

## What Changes

- Add lane guidance fields to `NavigationState` and wire `onLaneUpdate` callback in `NavigationViewModel`
- Rewrite `NextTurnOverlay` to show lane hints row between next-turn and next-next-turn rows
- Left-align the overlay (currently centered)
- Enable text wrapping on next-turn and next-next-turn descriptions
- No gap when lane hints absent

## Capabilities

### New Capabilities
- `lane-guidance`: Render per-lane turn arrows in the navigation overlay, with suggested lanes highlighted

### Modified Capabilities
- `turn-by-turn-instructions`: Next-turn overlay layout changes (left-aligned, wrapping text, lane hints row)

## Impact

- `app/src/main/java/com/naviveylin/navigation/NavigationViewModel.kt` — add lane state fields + `onLaneUpdate` handler
- `app/src/main/java/com/naviveylin/ui/navigation/NextTurnOverlay.kt` — full rewrite
- `app/src/main/java/com/naviveylin/ui/map/MapCanvasScreen.kt` — pass lane props, change alignment
- `app/src/main/java/com/naviveylin/data/SettingsStorage.kt` — add `laneHintsEnabled` to `AppSettings`
- `app/src/main/java/com/naviveylin/ui/map/MapCanvasViewModel.kt` — add `laneHintsEnabled` state + toggle function
- `app/src/main/java/com/naviveylin/ui/map/LocationOptionsOverlay.kt` — add lane hints toggle switch (visible during navigation)
- No native/JNI changes needed — upstream already provides `onLaneUpdate` + `LaneTurn`
