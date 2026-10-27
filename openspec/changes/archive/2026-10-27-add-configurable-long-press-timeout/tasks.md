# Tasks — add-configurable-long-press-timeout

## 1. The handler's timeout becomes configuration

- [x] 1.1 `MapGestures.kt`: add `longPressTimeoutMs: Long` to `mapGestureHandler`, defaulting to
      `LocalViewConfiguration.current.longPressTimeoutMillis` read inside `composed`, replace the `500L` literal with
      it, and key the pointer block on the resolved value. Verify: `./gradlew :app:testMobileDebugUnitTest --tests
      "com.naviveylin.ui.map.MapLongPressTimeoutComposeTest"` — the existing red case
      (`longPressHonoursTheConfiguredPlatformTimeout`) turns green (spec: `map-long-press-timeout` — The long-press
      timeout is a configuration, not a literal).
- [x] 1.2 `MapLongPressTimeoutComposeTest`: add the app-configured case
      (`longPressUsesTheConfiguredAppTimeout`, 900 ms through the new parameter). Verify: the same focused command
      reports `tests=2 failures=0`.

## 2. The value persists

- [x] 2.1 `SettingsStorage.kt`: add `longPressTimeoutMs: Int = 500` to `AppSettings` with the range constants and the
      clamping accessor. Verify: `./gradlew :app:testMobileDebugUnitTest --tests
      "com.naviveylin.data.AppSettingsTest"` — defaults, round trip and clamping cases pass (spec:
      `map-long-press-timeout` — The long-press timeout is a persisted setting).
- [x] 2.2 `MapCanvasUiState.kt` + `MapCanvasViewModelSettings.kt`: carry the field in the canvas state, add
      `onSetLongPressTimeoutMs` (state first, then persist), and restore the clamped value on load. Verify:
      `./gradlew :app:testMobileDebugUnitTest --tests
      "com.naviveylin.ui.map.MapCanvasViewModelLongPressTimeoutTest"`.

## 3. The sheet edits it

- [x] 3.1 `LocationOptionsOverlay.kt`: two required parameters and the always-visible labelled slider
      (`longPressTimeoutSlider` test tag, 250–1000 ms, 50 ms steps, value shown in ms), with the strings added to
      `app/src/main/res/values/strings.xml`. Verify: `./gradlew :app:testMobileDebugUnitTest --tests
      "com.naviveylin.ui.map.LocationOptionsOverlayComposeTest"` — the new case passes and the ten existing
      constructions compile with the two new arguments (spec: `location-options-ui` — Long-press timeout control).
- [x] 3.2 `MapCanvasScreenChromeBand.kt`: all three sheet constructions pass `state.longPressTimeoutMs` and
      `viewModel::onSetLongPressTimeoutMs`. Verify: `./gradlew :app:assembleMobileDebug` compiles, and `grep -c
      "longPressTimeoutMs =" app/src/main/java/com/naviveylin/ui/map/MapCanvasScreenChromeBand.kt` is 3.
- [x] 3.3 `MapCanvasMapBand.kt`: pass `state.longPressTimeoutMs.toLong()` to `mapGestureHandler`; this is what makes
      the sheet's value reach the gesture. Verify: `./gradlew :app:assembleMobileDebug`, and the focused gesture
      suite still reports `tests=2 failures=0`.

## 4. Classification and gate

- [x] 4.1 `tools/feature-list/specs.json`: add the new capability's classification line (`object-info`,
      `userVisible: true`, `surfaces: ["phone"]`). Verify: `./tools/gen-feature-list.sh --check-classification`
      exits 0 (guidelines/FeatureList.md, section "What a change owes when it touches this").
- [x] 4.2 Run the project's gate once from the change root: `./tools/loop/gate.sh`. Copy the tallies it writes into
      `openspec/changes/add-configurable-long-press-timeout/evidence/` and quote its `LOOP-GATE`/suite lines in the
      report; do not re-run it.

## 5. Evidence and archive

- [x] 5.1 Copy every cited XML into the change's `evidence/`, then run `bash tools/loop/evidence-check.sh
      openspec/changes/add-configurable-long-press-timeout --root .` and fix what it reports.
- [x] 5.2 Archive the change, confirm the directory exists under `openspec/changes/archive/` and that both deltas
      merged into `openspec/specs/map-long-press-timeout/spec.md` and `openspec/specs/location-options-ui/spec.md`.
