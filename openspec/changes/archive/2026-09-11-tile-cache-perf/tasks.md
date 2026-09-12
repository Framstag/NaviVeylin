## 1. Native tile data cache configuration

- [x] 1.1 Add `setNativeDataCacheSize(int)` native method declaration to `OSMScoutClient.java` (`:osmscout-client-java` module) and a matching no-op override in `FakeOSMScoutClient.kt`; verified by the app test compilation in 3.1
- [x] 1.2 Implement the C++ JNI entry point `setNativeDataCacheSize` in `OSMScoutClient.cpp` storing the value in `ClientData`, and apply `MapService::SetCacheSize` inside the render job's `loadDbData` lambda before `LookupTiles` so every open database and the basemap are configured (also covers async opens); verified by the native arm64 compile in 3.1
- [x] 1.3 Apply the configured capacity from Kotlin: `MapCanvasViewModel.initMap` calls `setNativeDataCacheSize(NATIVE_TILE_DATA_CACHE_SIZE = 512)` after a successful `openDatabase`; verify a ViewModel test asserts the call after successful open and absence on open failure

## 2. Verification

- [x] 2.1 Run the targeted unit tests (MapCanvasViewModelStyleTest, TileCacheRenderTest, RenderModeSwitchTest, MapCanvasGestureTransformTest, RotationHandoffTest, MapRendererSmokeTest) and verify they pass — including the pre-existing pin that north-up forced renders (zoom commits) reuse the tile cache
- [x] 2.2 Build `:app:assembleMobileDebug` (arm64) and verify the native bridge and app compile without errors or warnings
- [x] 2.3 Run the full unit test suite (`./gradlew test`) and verify all tests pass
