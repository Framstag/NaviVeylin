# Tasks: Render Performance

## 1. Native node optimization (spec: render-performance)

- [x] 1.1 In `app/src/main/cpp/libosmscout/libosmscout-client-java/src/OSMScoutClient.cpp` after `MapParameter params;` (~line 1012) add:
  - `params.SetOptimizeWayNodes(osmscout::TransPolygon::fast);`
  - `params.SetOptimizeAreaNodes(osmscout::TransPolygon::fast);`
  - `params.SetOptimizeErrorToleranceMm(0.5);`
- [x] 1.2 Verify `TransPolygon` is included/visible in the translation unit (include `<osmscout/util/Transformation.h>` if needed)

## 2. Multithreaded tile data loading (spec: render-performance)

- [x] 2.1 In both `loadDbData` lambdas (regular DBs ~line 1047 and basemap branch ~line 1093) change `searchParam.SetUseMultithreading(false)` to `true`

## 3. Double-buffer swap without pixel copies (spec: render-performance)

- [x] 3.1 In `MapRenderer.executeRender` rotated path (lines 749–751) replace `getPixels`/`setPixels` with `Canvas.drawBitmap(bitmap, 0f, 0f, null)` into `backBuffer`
- [x] 3.2 Keep `bitmap.recycle()` after the blit; verify no backing-storage sharing between `bitmap` and `backBuffer` (drawBitmap copies pixels)

## 4. Frame emission only on change (spec: render-performance)

- [x] 4.1 Track last emitted epoch in `MapRenderer`; in both emission paths (tile + rotated) skip the `_frameFlow` `Bitmap.createBitmap` copy when the front buffer epoch is unchanged since the last emission
- [x] 4.2 Ensure the reused frame is never mutated by Compose (overlay reads only)

## 5. Tests

- [x] 5.1 Add/extend `MapRendererTest` (or `MapRendererGpsMarkerTest`): rotated render emits a frame; a second emission with unchanged epoch reuses the previous bitmap (no new allocation)
- [x] 5.2 Add/extend tests: `Canvas.drawBitmap` swap produces identical pixels to the old `getPixels`/`setPixels` path (compare a small bitmap)
- [x] 5.3 Run `./gradlew test` — all unit tests pass

## 6. Build verification

- [x] 6.1 Native rebuild for one ABI: `./gradlew :app:assembleDebug -Pandroid.injected.build.abi=arm64-v8a` — compiles without errors
- [x] 6.2 Verify all target ABIs compile: `./gradlew :app:assembleDebug` (arm64-v8a, armeabi-v7a, x86_64)
- [x] 6.3 Smoke test on device: follow-mode render, pan, zoom — map updates visibly faster, no geometry artifacts at navigation zoom levels
