# Tasks — Phone ambient light dark mode

Parent specs: `dark-mode` (MODIFIED "Automatic mode follows environment dimming", ADDED "Ambient light sensor option"). Design: design.md.

## 1. Pure resolution logic

- [x] 1.1 Add pure `classifyLux(previousDark: Boolean, lux: Float): Boolean` with hysteresis (dark entry < 10 lux, light exit > 50 lux, band keeps previous) and a debounce helper (classification must hold DEBOUNCE_MS = 10 s). Verify: new unit test covers entry, exit, band-hold, and debounce window.
- [x] 1.2 Extend `resolveDarkPresentation` to take `sensorDark: Boolean?` + `sensorEnabled: Boolean`: sensor wins when enabled and non-null, else system. Verify: extend `DarkModeResolutionTest.kt` — sensor null → system, sensor active → sensor, option off → system.
- [x] 1.3 Verify tests pass: `./gradlew :app:testDebugUnitTest --tests "*DarkMode*" --tests "*AmbientLight*"`.

## 2. AmbientLightMonitor

- [x] 2.1 Add `AmbientLightMonitor` in `app/src/main/java/com/naviveylin/data/`: `SensorManager` `TYPE_LIGHT` listener, `start()`/`stop()`, feeds `classifyLux` + debounce, reports `Boolean?` (null when sensor absent or stopped). Verify: unit test with a fake sensor source drives lux values through the classifier and debounce.
- [x] 2.2 Verify monitor test passes: `./gradlew :app:testDebugUnitTest --tests "*AmbientLightMonitor*"`.

## 3. Settings + controller wiring

- [x] 3.1 Add `ambientLightDarkMode: Boolean = false` to `AppSettings`; verify old settings files load with default (extend `AppSettingsTest`/`SettingsStorageTest`).
- [x] 3.2 `DarkModeController`: add `setSensorDark(Boolean?)`, `sensorEnabled` state, `setSensorOption(Boolean)` persisting via `SettingsStorage`; resolution per 1.2. Verify: extend `DarkModeResolutionTest` + `MapCanvasViewModelDarkModeTest` for the option toggle path.
- [x] 3.3 Verify tests pass: `./gradlew :app:testDebugUnitTest --tests "*DarkMode*" --tests "*Settings*"`.

## 4. UI

- [x] 4.1 Add the ambient light toggle to the dark-mode section of `LocationOptionsOverlay` (below On/Off/Automatic), wired through `MapCanvasViewModel` → `DarkModeController.setSensorOption`. Verify: Compose UI test asserts toggle renders, reflects state, and fires the callback.
- [x] 4.2 Verify UI test passes: `./gradlew :app:testDebugUnitTest --tests "*LocationOptions*"`.

## 5. Lifecycle wiring

- [x] 5.1 `MainActivity`: start `AmbientLightMonitor` when preference = **Automatic** + option enabled (observed via controller flows), stop otherwise; stop unconditionally in `onStop`. Verify: Robolectric test asserts start/stop gating on state changes.
- [x] 5.2 Verify test passes: `./gradlew :app:testDebugUnitTest --tests "*MainActivity*"`.

## 6. Guidelines

- [x] 6.1 Document the ambient light option in `guidelines/UI.md` (placement, default off, sensor-absent fallback). Verify: section present, cross-references the `dark-mode` spec.

## 7. Build and regression

- [x] 7.1 Verify full build compiles: `./gradlew :app:assembleMobileDebug`.
- [x] 7.2 Verify existing app tests still pass: `./gradlew :app:testDebugUnitTest`.
- [x] 7.3 On-device verification: enable option, cover sensor with hand → dark within debounce; uncover → light; disable → follow-system restored; device without sensor → no crash, system fallback. Verify via logcat `NaviVeylin` tag classification transitions.
