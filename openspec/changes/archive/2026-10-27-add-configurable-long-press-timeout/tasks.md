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

## 6. Review round 1 — its three findings

- [x] 6.1 **The requirement's "render in every mode" clause gets cases** (finding 1). Three cases added to
      `LocationOptionsOverlayComposeTest`: `longPressTimeoutSliderIsReachableInBrowse`
      (`LocationOptionsOverlayComposeTest.kt:634`), `…InFreeDrive` (`:641`) and `…InNavigation` (`:648`), each
      opening the sheet in its own mode and asserting the row's label and the `longPressTimeoutSlider` tag are
      displayed through `assertLongPressTimeoutSliderVisible()` (`:628`) — the shape the three max-speed cases
      `slidersAreReachableInBrowse/FreeDrive/Navigation` already have. The clause is now exercised by three
      scenarios, one per case, in both the delta (`specs/location-options-ui/spec.md`) and the merged
      `openspec/specs/location-options-ui/spec.md` (spec: `location-options-ui` — Long-press timeout control).
      Verify: `./gradlew :app:testMobileDebugUnitTest -PforceTests --no-build-cache --tests
      "com.naviveylin.ui.map.LocationOptionsOverlayComposeTest"` → tests="27" failures="0" errors="0",
      `2026-10-27T18:42:42.316Z` (`evidence/focused/TEST-com.naviveylin.ui.map.LocationOptionsOverlayComposeTest.xml`).
- [x] 6.2 **The app-timeout case pins the platform's value instead of inheriting it** (review round 1, NOTE):
      `longPressUsesTheConfiguredAppTimeout` now passes `platformLongPressTimeoutMs = 500L` beside the app's 900 ms
      (`MapLongPressTimeoutComposeTest.kt:95`), so the case's own arguments are what discriminate the two sources.
      Verify: the same full JVM run's
      `evidence/focused/TEST-com.naviveylin.ui.map.MapLongPressTimeoutComposeTest.xml` — tests="2" failures="0"
      errors="0", `2026-10-27T18:44:12.519Z`.
- [x] 6.3 **The compiled step's executed count is evidence** (finding 2). `gate.sh` reads a `<suite>: all N cases
      passed` line and Gradle prints none, so the gate's own tally for that step is tests="0"
      (`evidence/gate-tallies/gradlew_test_-PforceTests_-PjvmTestsOnly_--no-build-cache.xml`). The step was re-run
      with the profile's own command — `./gradlew test -PforceTests -PjvmTestsOnly --no-build-cache`, 376 s,
      `BUILD SUCCESSFUL in 6m 16s` (`evidence/jvm-tallies.log`) — and each module's sum is written as a tally of its
      own under `evidence/module-tallies/` by the retained `evidence/module-tallies/module-tallies.py`:
      `:app` mobile 2124/0/0 (292 classes), `:app` automotive 2124/0/0 (292), `:auto` 885/0/0 (92), `:core` 646/0/0
      (61), `:osmscout-client-java` 116/0/0 (23) — 5 895 tests, 0 failures, 0 errors. `evidence/gate.md` now quotes
      them beside the command (`guidelines/Build.md` §4b, "Report tallies, not vibes"). The round-1 gate's seven
      shell suites are unchanged and stand as recorded: the correction moves no suite of theirs.
- [x] 6.4 **Revert-checks: one mutation per new guard** (finding 3), each marked
      `// REVERT-CHECK MUTATION`, run against the cases it protects, restored, and retained as its own XML:
      - 6.4a the clamp at the storage edge — `clampLongPressTimeoutMs` made an identity (`SettingsStorage.kt:181`)
        fails `MapCanvasViewModelLongPressTimeoutTest#aValueOutsideTheRangeIsClampedBeforeItIsStored` with
        `expected:<250> but was:<40>` at `MapCanvasViewModelLongPressTimeoutTest.kt:106` — tests="2" failures="1",
        `2026-10-27T18:37:08.255Z` (`evidence/mutation-A-clamp-MapCanvasViewModelLongPressTimeoutTest.xml`).
      - 6.4b the persist write — the `settingsStorage.update` in `onSetLongPressTimeoutMs`
        (`MapCanvasViewModelSettings.kt:61`) removed fails `longPressTimeoutAppliesAndPersists` with
        `expected:<900> but was:<500>` at `MapCanvasViewModelLongPressTimeoutTest.kt:89` and the clamp case's own
        storage assertion with `expected:<250> but was:<500>` at `:110` — tests="2" failures="2",
        `2026-10-27T18:37:41.041Z` (`evidence/mutation-B-persist-MapCanvasViewModelLongPressTimeoutTest.xml`).
      - 6.4c the sheet's report to its caller — the slider's `onValueChange` (`LocationOptionsOverlay.kt:457`) made
        empty fails `longPressTimeoutSliderShowsAndWritesTheValue` with `the sheet reports the moved value
        expected:<[900]> but was:<[]>` at `LocationOptionsOverlayComposeTest.kt:695` — tests="1" failures="1",
        `2026-10-27T18:38:03.145Z` (`evidence/mutation-C-sheet-report-LocationOptionsOverlayComposeTest.xml`).
      - 6.4d the render-in-every-mode guard the new cases assert — the long-press row wrapped in
        `if (mode == MapMode.BROWSE)` fails `longPressTimeoutSliderIsReachableInFreeDrive` (`:644`) and
        `…InNavigation` (`:651`) at `assertLongPressTimeoutSliderVisible` (`:629`) while the Browse case stays green
        — tests="27" failures="2", `2026-10-27T18:38:32.852Z`
        (`evidence/mutation-D-render-mode-LocationOptionsOverlayComposeTest.xml`).
      Restored after each: `git diff -- app/src/main` empty and `grep -rn 'REVERT-CHECK MUTATION' app/src` → no
      match; the green run over all five affected classes is `6.3`'s JVM run, whose copies are
      `evidence/focused/`.
- [x] 6.6 **The owning guideline carries the rule** (review round 1, NOTE): `guidelines/UI.md`'s phone-only
      settings list gains the long-press delay row (`guidelines/UI.md:962`) beside the overspeed delta and the
      per-vehicle maximum speeds, and `proposal.md` names that list as an owning section. Verify:
      `bash tools/check-doc-routes.sh` (no new route; every anchor it cites still resolves).
- [x] 6.7 **The change's own prose matches the tree** (review round 1, NOTEs): `proposal.md` and `design.md` state
      the handler as implemented (`longPressTimeoutMs: Long? = null`, resolved in `composed`) and say the re-key
      itself is not exercised by the two Compose cases; `design.md`'s deleted scratch probe is replaced by those
      cases and D3's uncited accessibility-delay rationale by what the constants say; the clamp scenario's WHEN
      names the loaded settings rather than a file, and the 50 ms step belongs to the sheet's slider, not to the
      storage's reader. Verify: `openspec validate --specs`.
- [x] 6.5 `bash tools/loop/evidence-check.sh openspec/changes/archive/2026-10-27-add-configurable-long-press-timeout
      --root .` reports no dangling pointer, the delta is archived and merged, and the tree carries the corrected
      spec, cases and evidence.
