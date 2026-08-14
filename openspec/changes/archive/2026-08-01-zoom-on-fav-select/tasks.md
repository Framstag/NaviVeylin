## 1. Native: Add `getObjectBoundingBox` to JNI bridge

- [x] 1.1 Add `native double[] getObjectBoundingBox(double lat, double lon, int magnification)` declaration to `OSMScoutClient.java`
- [x] 1.2 Implement JNI function in C++ that calls `MapService::SearchForObjects` to find object at coordinate, retrieves its type and bounding box
- [x] 1.3 Return `[minLat, maxLat, minLon, maxLon]` for area/way objects, return null for nodes or when no object found
- [x] 1.4 Build and verify native code compiles (`./gradlew :app:assembleDebug -Pandroid.injected.build.abi=arm64-v8a`)

## 2. Kotlin: Zoom computation in MapCanvasViewModel

- [x] 2.1 Add `computeAreaZoom(boundingBox: DoubleArray, viewportWidth: Int, viewportHeight: Int): Int` function that computes magnification from bounding box dimensions and viewport size with 80% padding
- [x] 2.2 Add `NODE_ZOOM = 17` constant and `MIN_AREA_ZOOM = 14` constant to `MapCanvasViewModel` companion
- [x] 2.3 Modify `onFavoriteSelected()` to call `client.getObjectBoundingBox()` after `getDescription()`, determine type, compute target zoom, and update `viewport.magnification` before `renderMap()`
- [x] 2.4 Ensure zoom override works regardless of current zoom level (no conditional skip)
- [x] 2.5 Build and verify app compiles (`./gradlew :app:assembleDebug`)

## 3. Testing

- [x] 3.1 Run existing unit tests (`./gradlew test`) — verify no regressions
- [x] 3.2 Run instrumented tests (`./gradlew connectedAndroidTest`) — verify no regressions
- [x] 3.3 Manual test: select a node-type favorite → verify zoom changes to 17
- [x] 3.4 Manual test: select an area-type favorite → verify zoom fits object in viewport
