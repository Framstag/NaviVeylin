## 1. Settings & persistence

- [x] 1.1 Add `styleSheet: String = "standard"` to `AppSettings` in `SettingsStorage.kt` and verify `SettingsStorageTest` covers save/load round-trip with a non-default value (e.g. `cycle`)
- [x] 1.2 Add `styleSheet: String = "standard"` to `AutoSettings` in `core/src/main/java/com/naviveylin/core/AutoSettings.kt` and map it in `AutoSettingsMapping.kt` (`AppSettings.styleSheet` → `AutoSettings.styleSheet`) and verify `AutoSettingsMappingTest` covers the mapping

## 2. Phone app

- [x] 2.1 Apply the persisted style in `MapCanvasViewModel`: on database open (reusing the first-frame re-push pattern from `pushDarkPresentation`, since `loadStyleSheet` is a no-op until a DB is open) call `client.loadStyleSheet(styleSheet)` off the main thread (`loadStyleSheet` blocks until the native load completes); on setting change re-apply immediately; verify with a unit test against `FakeOSMScoutClient` (style pushed after DB open, rollback on `false` return keeps prior selection)
- [x] 2.2 Phone map style control lives in the existing on-map settings view (`LocationOptionsOverlay` bottom sheet, gear button) as a compact exposed dropdown (one row) listing `client.getAvailableStyleSheets()` (display name = file name without `.oss`, e.g. `cycle`), current style marked; selecting applies immediately via `SettingsStorage` persistence; no style entry in the overflow `MapMenu`; verify with a Compose UI test (sheet dropdown lists styles, selection)

## 3. Android Auto

- [x] 3.1 Add a map-style row to the car `PreferencesScreen` (`:auto` module) backed by `AutoSettings.styleSheet`; verify the screen mapper (`PreferencesScreenMapper`) exposes the row and its current value, and that toggling it writes back through the shared settings
- [x] 3.2 Apply the shared style when a car navigation session starts (in `NavigationSession`/`MapScreen` initialization): call `client.loadStyleSheet(autoSettings.styleSheet)` off the main thread before/at first render; verify via unit test with `FakeOSMScoutClient` that the persisted phone selection reaches the native client at session start

## 4. Bundling guard

- [x] 4.1 Extend `AssetCopierTest` to assert that every current top-level `*.oss` from `libosmscout/stylesheets` is present in the packaged stylesheet assets (explicit list: `basemap-render`, `boundaries`, `coastlines`, `cycle`, `motorways`, `public-transport`, `railways`, `standard`, `winter-sports`) and verify the test passes against a fresh assemble

## 5. Integration

- [x] 5.1 Run `./gradlew :app:assembleMobileDebug test` and verify all unit tests (settings, mapping, phone UI, AA session, AssetCopier guard) pass and the APK builds
