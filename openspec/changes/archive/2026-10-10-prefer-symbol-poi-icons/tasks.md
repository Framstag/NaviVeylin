# Tasks

## 1. Native preference in `libosmscout-map`

- [x] 1.1 Add `preferSymbolIcons` (default `false`) with `GetPreferSymbolIcons()`/`SetPreferSymbolIcons(bool)` to `libosmscout-map/include/osmscoutmap/MapParameter.h` and its constructor init in `MapParameter.cpp`; verify the library still builds (`hostbuild` meson target for `libosmscout-map`).
- [x] 1.2 Implement the preference-first branch in `MapPainter::LayoutPointLabels` (`src/osmscoutmap/MapPainter.cpp:480-510`) as designed — symbol first when the preference is set and a symbol exists, today's icon-then-symbol order otherwise; verify with the test from 1.3.
- [x] 1.3 Add a native test beside `Tests/src/MapPainterLabelCullingTest.cpp` that lays out one entry carrying both a raster icon name and a symbol and asserts the selected rendering for both values of the preference (a painter subclass exposes the protected `labelLayoutData`; no PNG needs to exist for the symbol case); verify the test fails before 1.2 and passes after, and that it is registered in the test build.
- [x] 1.4 Falsify 1.2 once (`revert-check`): flip the branch condition, confirm the named case of 1.3 fails, restore, and re-run the native suite green.
- [x] 1.5 Update `guidelines/MapRendering.md` §16a: with the preference on, a symbol-carrying entry attempts no PNG load, so the `ERROR while loading image` lines for `charging_station` and `mini_roundabout` disappear — state the recipe for both preference values and verify the section names the same entries it did before.

## 2. JNI boundary

- [x] 2.1 Add the `ClientData` field and the `setPreferSymbolIcons(boolean)` entry point to `libosmscout-client-java/src/OSMScoutClient.cpp`, and read it in the render path where `MapParameter` is built (mirror the `setNativeDataCacheSize` shape); verify the signature matches its Java declaration with the submodule's `scripts/check-jni-signatures.sh`.
- [x] 2.2 Declare the new native method in the submodule's `java/com/framstag/libosmscout/client/OSMScoutClient.java` and mirror the identical declaration into the committed override copy `osmscout-client-java/src/main/java/com/framstag/libosmscout/client/OSMScoutClient.java`; verify the two declaration sets stay identical (`diff` of sorted `public native` lines is empty).
- [ ] 2.3 Build the native library for all three ABIs (`:app:assembleMobileDebug` with each `-Pandroid.injected.build.abi=…`) and verify the link succeeds with no new `undefined reference`; commit the submodule work on `naviveylin-local`, push it (after `git ls-remote origin naviveylin-local`), and bump the gitlink in one commit.

## 3. App setting, application and invalidation

- [x] 3.1 Add `preferSymbolPoiIcons: Boolean = false` to `AppSettings` in `data/SettingsStorage.kt`; verify a settings file written before the field decodes with the default and round-trips once set (`SettingsStorageTest`/`AppSettingsTest`).
- [x] 3.2 Apply the persisted value where the persisted style is applied (`MapCanvasViewModel.kt:1573-1610`) so it is in effect for the first map display, and add the VM setter that updates the UI state, persists through `settingsStorage.update`, pushes `client.setPreferSymbolIcons(...)`, then calls `mapRenderer?.invalidateStyle()`; verify with a VM test that the native call happens once per change and that the epoch advanced and the tile cache was cleared.
- [x] 3.3 Update every fake of `OSMScoutClient` to override the new method (a new native method is invisible to the compiler and fails only at its first call, `Build.md` §481); verify `:app` and `:auto` test suites run without `UnsatisfiedLinkError`.
- [x] 3.4 Falsify 3.2 once: remove the `invalidateStyle()` call, confirm the invalidation case of 3.2 fails, restore, and re-run the suite green.

## 4. Phone control

- [x] 4.1 Add the preference control to the phone map options sheet (`ui/map/LocationOptionsOverlay.kt`) beside the map style picker and wire it through `MapCanvasScreen.kt` to the VM setter; verify with a composable test that the control shows the enabled state and that toggling it invokes the VM setter (compare with `RenderModeSwitchTest`).
- [x] 4.2 Verify the control's label and placement against `guidelines/UI.md` §7/§8 (map options sheet), and that the car variant offers no control for it (no new element in the car preferences path).

## 5. On-device integration

- [x] 5.1 On the phone emulator/device: capture the map with the preference off and on over an area with a dual entry (`amenity_hospital`, `amenity_parking`, `amenity_pharmacy`, `highway_bus_stop`) and verify the raster icon is drawn when off and the symbol when on.
- [ ] 5.2 With the preference on, verify via `adb logcat -s NaviVeylin` that no `ERROR while loading image` line appears for `charging_station` or `mini_roundabout`, and that no log or diagnostics line carries a coordinate.
- [ ] 5.3 In a car session (Android Auto or the AAOS AVD) with the phone's preference enabled, verify the car map draws the symbol rendering and the car settings offer no control for the preference.

## Workflow follow-up

- Run the independent read-only review the project's loop requires, then archive the change.
- Verify the archived spec result (`openspec status`/`openspec show map-styles --type spec`) and record the resulting follow-ups in `TODO.md` if the on-device pass leaves any open.
