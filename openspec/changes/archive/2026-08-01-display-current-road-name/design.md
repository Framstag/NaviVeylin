## Context

NaviVeylin's `NavigationViewModel` already receives `onPositionEstimate` callbacks with lat/lon during navigation. The `OSMScoutClient` already exposes `getDescription(lat, lon, mag)` returning `ObjectDescription` with `DescriptionEntry` entries. The `CurrentRoadInfo` class already exists in `com.framstag.libosmscout.client`. No new JNI or native code needed.

Current layout: `NextTurnOverlay` (top-center card) shows turn icon + distance + description. `NavigationStateOverlay` (bottom bar) shows ETA, distance, speed. Road info belongs in the bottom card, above the stats row.

## Goals / Non-Goals

**Goals:**
- Add `currentRoadInfo` field to `NavigationState` in `NavigationViewModel`
- Add throttled road info lookup in `NavigationViewModel` using `client.getDescription()`
- Display current road name/ref/type as first row in `NavigationStateOverlay`
- Show "Offroad" text when vehicle is off-route (no road info available)
- Match JavaScout throttle behavior: 2s cooldown + ~50m distance threshold

**Non-Goals:**
- No new JNI/native changes
- No separate overlay composable — road info lives inside existing `NavigationStateOverlay`
- No changes to `NextTurnOverlay` (top card)
- No changes to route description or instruction parsing

## Decisions

1. **Throttled `getDescription` lookup in ViewModel** (not in a separate service)
   - JavaScout does the same in `MainController.updateRoadInfoFromPosition()`
   - ViewModel already has `viewModelScope` for coroutine management
   - `Dispatchers.IO` for the blocking `getDescription` call (native JNI)
   - Alternative considered: dedicated repository — overkill for one lookup pattern

2. **Road info row inside `NavigationStateOverlay`** (not a separate overlay)
   - Road info displayed in bottom status card, above ETA/Dist/Speed row
   - Keeps bottom-center layout clean — one card with road name + stats
   - Road info shows "Offroad" when empty, stats stay visible
   - Alternative considered: separate overlay — adds another positioned element, more complexity

3. **`CurrentRoadInfo` from `com.framstag.libosmscout.client`** (not a new data class)
   - Already exists in the JNI bridge JAR with `ref`, `typeName`, `name`, `hasInfo()`, `toDisplayString()`
   - No need to duplicate or wrap
   - Alternative considered: new Kotlin data class — unnecessary indirection

4. **Magnification for `getDescription`**: use current map magnification from `MapCanvasViewModel` or a fixed default (e.g., 15). The road lookup doesn't need exact zoom — any magnification that resolves the road works.
   - Decision: pass a fixed magnification of 15 (mid-range, resolves road features). The `NavigationViewModel` doesn't currently track map zoom, and road info is not zoom-dependent.

## Risks / Trade-offs

- [Blocking JNI call] → `getDescription` is a native JNI call that may block briefly. Run on `Dispatchers.IO` with a coroutine, never on Main thread.
- [Throttle misses short road segments] → 2s + 50m threshold means very short roads (<50m) may not be reported. Acceptable — driver wouldn't benefit from sub-second road name flashes.
- [Magnification hardcoded to 15] → If map is zoomed far out, description service may not resolve the road. Acceptable — during navigation the map is typically zoomed in enough. Can be made dynamic later.
