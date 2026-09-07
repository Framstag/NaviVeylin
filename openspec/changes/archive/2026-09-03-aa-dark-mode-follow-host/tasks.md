# Tasks — AA dark mode follows host

Parent specs: `auto-map-renderer` (ADDED "Map follows host day/night"). Design: design.md.

## 1. Daylight applier

- [x] 1.1 Add `CarDaylightApplier` in `auto/src/main/java/com/naviveylin/auto/` mirroring `CarStyleApplier`: `apply(dark: Boolean)` calls `setStyleSheetFlag("daylight", !dark)` only on value change; on native failure resets the marker so the next push retries. Verify: new `CarDaylightApplierTest.kt` covers dedupe (same value → no second call), change → one call, failure → retry on next push.
- [x] 1.2 Verify `CarDaylightApplierTest` passes: `./gradlew :auto:testDebugUnitTest --tests "*CarDaylightApplier*"`.

## 2. Session host-dark flow

- [x] 2.1 In `NavigationSession`, add `MutableStateFlow<Boolean> hostDark` initialized from `carContext.isDarkMode()`; override `onCarConfigurationChanged(Configuration)` to update it from `configuration.uiMode` (`UI_MODE_NIGHT_MASK`). Verify: unit test constructs the session with a fake `CarContext`/config and asserts init value + update on config change.
- [x] 2.2 Verify session flow test passes: `./gradlew :auto:testDebugUnitTest --tests "*NavigationSession*"`.

## 3. MapScreen wiring

- [x] 3.1 `MapScreen` accepts the `hostDark` flow (constructor param, defaulted), collects it on `Dispatchers.Main`, applies via `CarDaylightApplier`, then `mapRenderer.requestRender()`. Verify: `MapScreenTest` covers initial dark → flag pushed before first render; live change → re-render requested.
- [x] 3.2 Verify `MapScreenTest` passes: `./gradlew :auto:testDebugUnitTest --tests "*MapScreen*"`.

## 4. NavigationScreen wiring

- [x] 4.1 `NavigationScreen` accepts the `hostDark` flow, collects and applies the same way. Verify: `NavigationScreenTest` covers initial state + live change.
- [x] 4.2 Verify `NavigationScreenTest` passes: `./gradlew :auto:testDebugUnitTest --tests "*NavigationScreen*"`.

## 5. Guidelines

- [x] 5.1 Document the AA daylight-flag contract in `guidelines/MapRendering.md` (host-driven, mirrors phone `pushDarkPresentation`, dedupe + retry semantics). Verify: section present, cross-references the `auto-map-renderer` spec.

## 6. Build and regression

- [x] 6.1 Verify full build compiles: `./gradlew :auto:assembleDebug` (or `:app:assembleMobileDebug` which includes `:auto`).
- [x] 6.2 Verify existing auto tests still pass: `./gradlew :auto:testDebugUnitTest`.
- [x] 6.3 On-device verification (emulator/head unit): toggle host day/night in AA developer settings — map surface switches live; AAOS head unit at night — dark map from start. Verify via logcat `NaviVeylin` tag flag pushes.
