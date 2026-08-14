## 1. Settings persistence (spec: dark-mode — Three-state dark mode preference)

- [x] 1.1 Add `DarkModePreference` enum (ON, OFF, AUTOMATIC) and `darkMode: DarkModePreference = AUTOMATIC` field to `AppSettings` in `app/src/main/java/com/naviveylin/data/SettingsStorage.kt`
- [x] 1.2 Extend `AppSettingsTest`: default value is AUTOMATIC, serialize/deserialize round-trip, old JSON without `darkMode` field decodes with default
- [x] 1.3 Verify existing tests pass: `./gradlew test`

## 2. Dark presentation resolution (spec: dark-mode — Automatic mode follows environment dimming)

- [x] 2.1 Add resolved dark state to `MapCanvasUiState` (e.g. `darkModePreference`, `isDarkPresentation`)
- [x] 2.2 Implement resolution in `MapCanvasViewModel`: `resolvedDark = pref == ON || (pref == AUTOMATIC && environmentDark)`; load preference in the existing settings-load block (spec: environment change applies without restart)
- [x] 2.3 Wire `environmentDark` to `isSystemInDarkTheme()` at composition site with a single extension point for a future car-environment source (design D1/D7)
- [x] 2.4 Unit test resolution logic for all three preference states × environment states

## 3. Compose control theming (spec: dark-mode — Dark presentation applies to UI controls)

- [x] 3.1 Change `NaviVeylinTheme` call site to pass resolved dark state instead of raw `isSystemInDarkTheme()` (Theme.kt signature unchanged; only the argument changes)
- [x] 3.2 Verify controls (overlay sheet, buttons, dialogs) switch dark/light with resolved state

## 4. Native style flag API (spec: dark-mode — Dark presentation applies to map rendering)

- [x] 4.1 Add `public native void setStyleSheetFlag(String key, boolean value)` to `OSMScoutClient.java` in libosmscout-client-java
- [x] 4.2 Implement JNI in `OSMScoutClient.cpp`: resolve `ClientData` from handle, call `dbThread->setStyleFlag(key, value)`
- [x] 4.3 Apply flag at `initMap` so first render honors resolved presentation (spec: Map darkens from the start)
- [x] 4.4 On presentation change: push `setStyleSheetFlag("daylight", !resolvedDark)`, bump render epoch, request `forceFullRender` so no light-variant tiles/front buffer survive (design D4; upstream PR #1701)
- [x] 4.5 Verify tile cache invalidation: switch dark→light→dark shows no stale-variant tiles
- [x] 4.6 Build verification: `./gradlew :app:assembleDebug -Pandroid.injected.build.abi=arm64-v8a` compiles with new JNI method

## 5. Settings control (spec: dark-mode — Manual dark mode controls)

- [x] 5.1 Add three-state dark mode control (On/Off/Automatic) to the settings overlay (`LocationOptionsOverlay` pattern); persist via `settingsStorage.save`
- [x] 5.2 Changing the control applies immediately to controls and map (spec scenarios: switches to On, immediate application)

## 6. On-map button removal (user decision — settings control suffices)

- [x] 6.1 Remove on-map dark mode button from `MapCanvasScreen` and `onCycleDarkMode()` from `MapCanvasViewModel`
- [x] 6.2 Update spec/proposal/design: on-map button dropped, settings control remains the only manual control
- [x] 6.3 Manual verify: settings control (On/Off/Automatic), automatic system-switch, restart persistence, first-render dark

## 7. Stylesheet audit (spec: dark-mode — Dark presentation applies to map rendering)

- [x] 7.1 Audit `app/src/main/assets/stylesheets/standard.oss` `IF daylight` coverage for major features (land, water, roads, areas, labels); patch gaps with dark variants if any feature lacks one

## 8. Automated test coverage (verification suggestions)

- [x] 8.1 `SettingsStorageTest` (Robolectric): dark mode round-trip, overwrite, missing-file default, other settings survive
- [x] 8.2 `MapCanvasViewModelDarkModeTest` (Robolectric + `FakeOSMScoutClient`): automatic env signal, ON/OFF preference, ON beats light environment — verifies `setStyleSheetFlag("daylight", …)` pushes + uiState
- [x] 8.3 JNI stub `app/src/test/jniLibs/libosmscout_client_java.so` so `OSMScoutClient` static `loadLibrary` succeeds in JVM unit tests (Android .so can't load on host)
