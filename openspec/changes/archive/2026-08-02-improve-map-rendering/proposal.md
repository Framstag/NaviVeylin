# Improve Map Rendering

## Why

Map rendering shows visible glitches: GPS/marker direction drifts, marker jumps off-road, and intermediate scaled tiles do not fit the zoom level before/after. JavaScout already fixed these issues upstream. NaviVeylin needs to port those fixes into the app-side rendering code and JNI bridge to eliminate the visual defects.

## What Changes

- Port JavaScout rendering corrections to the `libosmscout-client-java` JNI bridge and C++ render path.
- Fix marker direction/position projection in Kotlin UI layer so GPS and favorite markers stay aligned with map features during pan, zoom, and rotation.
- Fix intermediate zoom scaling so placeholder tiles match the target magnification during and after the pinch-to-zoom gesture.
- Add regression specs and unit tests for marker projection, direction angle, and zoom placeholder math.
- **BREAKING**: Any app code that manually compensates for projection drift must be removed or updated after the upstream fix.

## Capabilities

### New Capabilities

- `marker-render-accuracy`: GPS and favorite markers align with road geometry and heading direction across pan, zoom, and map rotation.
- `zoom-transition-scaling`: Intermediate placeholder tiles scale correctly between source and target zoom levels without visible jumps or mis-fit.
- `jnisync-java-scout`: Rendering fixes from JavaScout are applied to the NaviVeylin JNI bridge and native render path.

### Modified Capabilities

- `gps-location-marker`: Requirement "Marker position tracks map viewport" must be updated to cover rotation and to remove off-road drift during zoom transitions.
- `map-pan-zoom`: Requirement "Pinch-to-zoom" must be updated so placeholder scaling matches target zoom before and after re-render.

## Impact

- `app/src/main/cpp/` — C++ render path and JNI wrapper changes.
- `libosmscout/libosmscout-client-java/` — JavaScout fixes synced into submodule or overlay.
- `app/src/main/java/com/naviveylin/ui/map/` — marker overlay and viewport projection Kotlin code.
- `app/src/main/java/com/naviveylin/ui/map/MapCanvasViewModel.kt` — epoch/debounce and zoom placeholder logic.
- `app/src/main/java/com/naviveylin/ui/map/MapCanvasScreen.kt` — marker drawing using current projection.
- `app/src/main/java/com/naviveylin/ui/map/ProjectionUtils.kt` — projection math for marker position and zoom scaling.
- `app/build.gradle.kts` and `CMakeLists.txt` — if native source set changes.
- Affected specs: `gps-location-marker`, `map-pan-zoom`; new specs for `marker-render-accuracy`, `zoom-transition-scaling`, `jnisync-java-scout`.
- Test additions: unit tests for `ProjectionUtils` and placeholder math in `app/src/test/`.
