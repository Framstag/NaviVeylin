## 1. Fix long-press coordinate conversion

- [x] 1.1 Modify `fireLongPress` in `app/src/main/java/com/naviveylin/ui/map/MapCanvasScreen.kt` to build a `ProjectionUtils.viewport(centerLat, centerLon, mag, w, h, dpi, s.viewport.angle)` and convert the press position with `screenToGeoRotated` instead of the north-up `screenToGeo` (spec: long-press-details / Long-press gesture detection; design D1/D2)

## 2. Regression tests

- [x] 2.1 Add unit test in `app/src/test/java/com/naviveylin/ui/map/ProjectionUtilsTest.kt`: `screenToGeoRotated` round-trips with `geoToScreenRotated` at a non-zero angle (spec: long-press-details / Long press in rotated viewport)
- [x] 2.2 Add unit test: `screenToGeoRotated` equals `screenToGeo` at angle 0 (spec: long-press-details / Long press in north-up viewport)

## 3. Verification

- [x] 3.1 Run `./gradlew test` — all unit tests pass, including existing `ProjectionUtilsTest` and `RoutePanelComposeTest`
- [x] 3.2 Run `./gradlew :app:compileDebugKotlin` — app compiles without errors
