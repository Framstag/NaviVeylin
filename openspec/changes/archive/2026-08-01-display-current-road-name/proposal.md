## Why

NaviVeylin's navigation UI shows next-turn instructions and a status bar (ETA, distance, speed) but lacks the current road name/ref display that JavaScout provides. During navigation, knowing the current road ("A40", "Ruhrschnellweg") gives the driver immediate spatial context — especially on highways where the next turn may be several km away. JavaScout implements this via `CurrentRoadInfo` from `client.getDescription(lat, lon)` with throttled lookups.

## What Changes

- Add `currentRoadInfo` field to `NavigationState` in `NavigationViewModel`
- Add throttled road info lookup in `NavigationViewModel` using `client.getDescription(lat, lon, mag)`, parsing "General" section entries for "NameRef", "Type", "Name" labels
- Add `CurrentRoadInfo` data class (mirrors `com.framstag.libosmscout.client.CurrentRoadInfo` already in the JNI bridge)
- Add current road name display row to `NextTurnOverlay` composable (top of the card, above turn info) — matches JavaScout layout where current road sits above next-turn in one container
- Wire road info lookup from `onPositionEstimate` callback in `NavigationListener`
- Throttle: skip lookup if <2s since last or <~50m movement (same as JavaScout)

## Capabilities

### New Capabilities
- `current-road-info`: Report and display the name, reference, and type of the road the vehicle is currently on during an active navigation session. Lookup uses `client.getDescription()` at the estimated position, throttled to avoid excessive DB queries.

### Modified Capabilities
- *(none — no existing spec changes)*

## Impact

- `NavigationViewModel.kt` — add `currentRoadInfo` to `NavigationState`, add `updateRoadInfoFromPosition()` with throttle logic, call from `onPositionEstimate`
- `NextTurnOverlay.kt` — add `currentRoadInfo` parameter, display road name row above turn info
- `MapCanvasScreen.kt` — pass `navState.currentRoadInfo` to `NextTurnOverlay`
- No new native/JNI changes needed — `getDescription()` and `CurrentRoadInfo` already exist in `libosmscout-client-java`
