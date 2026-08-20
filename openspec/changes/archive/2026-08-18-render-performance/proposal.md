# Render Performance

## Why

Follow-mode map updates are limited by the native render duration: `MapPainterCairo::DrawMap` transforms and draws every node of every way and area because `MapParameter` defaults to `TransPolygon::none` for node optimization and the JNI bridge never overrides it. Tile data loading is single-threaded (`SetUseMultithreading(false)`), and each emitted frame copies the full ARGB_8888 buffer twice (`getPixels`/`setPixels` plus a `Bitmap.createBitmap` copy for Compose). On a 1080×2400 screen that is ~10 MB per copy, several times per frame. The visible result is a map bitmap that lags behind the GPS marker at 1–3 fps on slow devices.

## What Changes

- Enable native node optimization in the JNI render path: `SetOptimizeWayNodes(TransPolygon::fast)` and `SetOptimizeAreaNodes(TransPolygon::fast)` so intermediate nodes that are not needed for rendering are dropped before Cairo draws them.
- Set `SetOptimizeErrorToleranceMm` to a small tolerance so the node reduction is aggressive enough to matter without visible geometry loss.
- Enable multithreaded tile data loading: `searchParam.SetUseMultithreading(true)` so `LoadMissingTileData` parallelizes across cores.
- Eliminate the full-buffer pixel copies in `MapRenderer`: replace `getPixels`/`setPixels` with `Canvas.drawBitmap` for the double-buffer swap, and emit the `_frameFlow` bitmap copy only when the frame actually changed.

## Capabilities

### New Capabilities
- `render-performance`: Reduce native map render cost and per-frame buffer copy overhead so follow-mode map updates keep up with the GPS marker.

## Impact

- `app/src/main/cpp/libosmscout/libosmscout-client-java/src/OSMScoutClient.cpp` (JNI render path, `MapParameter` + `AreaSearchParameter`)
- `app/src/main/java/com/naviveylin/ui/map/MapRenderer.kt` (double-buffer swap, frame emission)
- `app/src/test/java/com/naviveylin/ui/map/MapRendererTest.kt` (buffer copy behavior)
- Native rebuild required: `./gradlew :app:assembleDebug -Pandroid.injected.build.abi=arm64-v8a`
