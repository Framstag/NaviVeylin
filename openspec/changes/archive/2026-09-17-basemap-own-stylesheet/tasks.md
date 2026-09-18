# basemap-own-stylesheet — Tasks

## 1. Native core (spec: basemap-loading)

- [x] 1.1 Add `basemapStyleFilename` member (default empty) to `DBThread` (`app/src/main/cpp/libosmscout/libosmscout-client/include/osmscoutclient/DBThread.h`) and a constructor parameter. Verify: `:app:compileMobileDebugKotlin`-adjacent native build compiles (see 6.1).
- [x] 1.2 Update `DBThread::LoadStyleInternal` (`app/src/main/cpp/libosmscout/libosmscout-client/src/osmscoutclient/DBThread.cpp`): when `basemapStyleFilename` is non-empty, load `basemapStyleFilename + suffix` for `basemapDatabase` instead of the main style file; keep the fallback to the main style when empty. Verify: native build compiles.

## 2. JNI bridge (spec: basemap-loading)

- [x] 2.1 Add `withBasemapStyleSheet(String name)` to `OSMScoutClientBuilder` (`app/src/main/cpp/libosmscout/libosmscout-client-java/java/com/framstag/libosmscout/client/OSMScoutClientBuilder.java`) with a `basemapStyleSheet` field. Verify: `:app:compileMobileDebugKotlin` succeeds.
- [x] 2.2 In `OSMScoutClientBuilder_build` (`app/src/main/cpp/libosmscout/libosmscout-client-java/src/OSMScoutClient.cpp`): read the field, validate it (reject empty, path-like, `.`/`..` — same rules as `loadStyleSheet`), resolve `stylesheetDir + "/" + name + ".oss"`, and pass the absolute path to the `DBThread` constructor. Verify: native build compiles.

## 3. Stylesheet (spec: basemap-loading)

- [x] 3.1 Complete `app/src/main/cpp/libosmscout/stylesheets/basemap-render.oss`: add `IF daylight`/`ELSE` color branches mirroring `land_sea_color.oss`/`place.oss` dark values; replace the undefined `symbol: "star"` with the `place_capital_city` symbol (and add `place_city` for million cities); add `AREA.TEXT` for sea/continent/capital/millioncity; add dashed `AREA.BORDER` for sea; adopt the MAG ranges of `include/place.oss` (see design D4). Symbols are duplicated locally (NOT included via `MODULE "include/place"` — the module would pull in style rules for non-basemap types and reintroduce warnings). Verify: style parses on-device (see 6.3) with zero unknown-type warnings.
- [x] 3.2 Confirm the stylesheet references only types present in the basemap database (basemap.ost types + `_tile_sea`/`_tile_land`/`_tile_unknown`). Verify: on-device logcat shows no "Unknown type" warnings for the basemap DB.

## 4. App wiring (spec: basemap-loading, map-styles)

- [x] 4.1 Add `.withBasemapStyleSheet("basemap-render")` to the builder chain in `app/src/main/java/com/naviveylin/di/MapDownloadModule.kt`. Verify: `:app:compileMobileDebugKotlin` succeeds.
- [x] 4.2 Filter `basemap-render` out of `availableStyleSheets` in `MapCanvasViewModel` (`app/src/main/java/com/naviveylin/ui/map/MapCanvasViewModel.kt`, the `getAvailableStyleSheets()` result at ~line 973) so the style picker never offers it. Verify: `:app:compileMobileDebugKotlin` succeeds; picker list excludes `basemap-render`.

## 5. Guidelines and docs

- [x] 5.1 Update `guidelines/MapRendering.md` style-loading section: the basemap renders with its own stylesheet (`basemap-render.oss`), loaded per-database by `DBThread`; unknown-type warnings for the basemap DB are gone. Verify: doc reflects the new behavior.

## 6. Verification

- [x] 6.1 Build both flavors (`./gradlew :app:assembleMobileDebug :app:assembleAutomotiveDebug`) and verify compilation succeeds without warnings.
- [x] 6.2 Run the full unit test suite (`:core:test`, `:app:test`, `:auto:test`) and verify all existing tests still pass (no new Kotlin logic beyond the picker filter; native changes are not unit-testable in the JVM — the JNI stub is a no-op).
- [x] 6.3 On-device (phone): with a basemap installed, check logcat (`adb logcat -s NaviVeylin`) for style-load warnings — zero "Unknown type" entries for the basemap DB; verify basemap borders, country names, and coastlines render; toggle dark mode and verify the basemap follows; switch the main style (e.g. `cycle`) and verify the basemap stays on `basemap-render`; zoom into an area without a regional map and verify basemap labels persist at the same zoom levels as before. Verify: manual pass on emulator/device.
- [x] 6.4 On-device (phone): verify the style picker does not list `basemap-render`. Verify: manual pass.
- [x] 6.5 On-device (phone): download/update/delete the basemap while the app runs and verify the basemap re-renders with `basemap-render.oss` (live-reload path from `fix-basemap-live-reload` still works). Verify: manual pass.
- [x] 6.6 On-device (AA emulator/head unit): verify the basemap renders with `basemap-render.oss` in the car map (shared client singleton). Verify: manual pass.
