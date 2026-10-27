# Review round 1 — the three blocking findings, and what closed them

The review (`evidence/review-round-1-requested.md` announced it; its report is the round's input) left three
blocking findings on an otherwise clean change. Nothing in the production diff was in dispute, so the round is
test, evidence and guideline work on the same tree — no production line changed but the four `REVERT-CHECK
MUTATION` edits below, each of which was reverted before the retained green run.

## Finding 1 — the requirement's "render in every mode" clause had no case

`openspec/specs/location-options-ui/spec.md`, requirement "Long-press timeout control", states "The control SHALL
render in every mode", and only one Browse-mode case asserted the row.

Three cases added, one per mode, in the shape the neighbouring max-speed requirement already has
(`assertMaxSpeedSlidersVisible()` × 3):

| scenario (delta + merged spec) | case | where |
|---|---|---|
| The control renders in browse | `longPressTimeoutSliderIsReachableInBrowse` | `LocationOptionsOverlayComposeTest.kt:634` |
| The control renders in free drive | `longPressTimeoutSliderIsReachableInFreeDrive` | `LocationOptionsOverlayComposeTest.kt:641` |
| The control renders in navigation | `longPressTimeoutSliderIsReachableInNavigation` | `LocationOptionsOverlayComposeTest.kt:648` |

Each opens the sheet in its own `MapMode` and asserts the row's label and the `longPressTimeoutSlider` tag are
displayed (`assertLongPressTimeoutSliderVisible()`, `LocationOptionsOverlayComposeTest.kt:628`). The scenarios are
in both the delta (`specs/location-options-ui/spec.md`) and the merged `openspec/specs/location-options-ui/spec.md`,
and every scenario names exactly one case.

Green: `./gradlew :app:testMobileDebugUnitTest -PforceTests --no-build-cache --tests
"com.naviveylin.ui.map.LocationOptionsOverlayComposeTest"` — tests="27" failures="0" errors="0",
`2026-10-27T18:42:42.316Z`, `focused/TEST-com.naviveylin.ui.map.LocationOptionsOverlayComposeTest.xml` (24 before
the three cases, 27 after).

## Finding 2 — the compiled half of the gate carried no executed count

`gate.sh` reads a `<suite>: all N cases passed` line and Gradle prints none, so its tally for the compiled step is
`gate-tallies/gradlew_test_-PforceTests_-PjvmTestsOnly_--no-build-cache.xml` — tests="0" failures="0" errors="0",
`time="394"`, `BUILD SUCCESSFUL in 6m 34s` — while the 764 in the gate's summary line is the seven shell suites'
sum (197 + 6 + 29 + 119 + 125 + 34 + 254).

The step was re-run on the corrected tree with the profile's own command (`tools/loop/loop.conf`,
`LOOP_GATE_COMPILED_CMD`): `./gradlew test -PforceTests -PjvmTestsOnly --no-build-cache` → `BUILD SUCCESSFUL in
6m 16s` (376 s), console log `jvm-tallies.log`. Each module's sum over its task's result XMLs is its own tally
under `module-tallies/`, written by the retained `module-tallies/module-tallies.py`:

- `:app` mobile: tests="2124" failures="0" errors="0" (292 classes, `module-tallies/app-mobile.xml`)
- `:app` automotive: tests="2124" failures="0" errors="0" (292 classes, `module-tallies/app-automotive.xml`)
- `:auto`: tests="885" failures="0" errors="0" (92 classes, `module-tallies/auto.xml`)
- `:core`: tests="646" failures="0" errors="0" (61 classes, `module-tallies/core.xml`)
- `:osmscout-client-java`: tests="116" failures="0" errors="0" (23 classes, `module-tallies/osmscout-client-java.xml`)

5 895 tests, 0 failures, 0 errors. This is the compiled step alone, not a second `gate.sh`: the round changes no
shell suite's inputs, so the seven suites of the round-1 gate stand as recorded in `gate.md`.

## Finding 3 — no revert-check for the new guards

One mutation per guard, each marked `// REVERT-CHECK MUTATION`, run against the case it protects, then restored
(`git diff -- app/src/main` empty, `grep -rn 'REVERT-CHECK MUTATION' app/src` → no match). Each failure is at the
assertion that describes the invariant, not at a premise or a compile.

| guard | mutation | case that failed | red |
|---|---|---|---|
| clamp at the storage edge | `clampLongPressTimeoutMs` → identity (`SettingsStorage.kt:181`) | `MapCanvasViewModelLongPressTimeoutTest#aValueOutsideTheRangeIsClampedBeforeItIsStored` — `expected:<250> but was:<40>` (`MapCanvasViewModelLongPressTimeoutTest.kt:106`) | tests="2" failures="1" errors="0", `2026-10-27T18:37:08.255Z`, `mutation-A-clamp-MapCanvasViewModelLongPressTimeoutTest.xml` |
| apply and persist | the `settingsStorage.update` removed (`MapCanvasViewModelSettings.kt:61`) | `longPressTimeoutAppliesAndPersists` — `expected:<900> but was:<500>` (`MapCanvasViewModelLongPressTimeoutTest.kt:89`); the clamp case's storage assertion also reads `expected:<250> but was:<500>` (`:110`) | tests="2" failures="2" errors="0", `2026-10-27T18:37:41.041Z`, `mutation-B-persist-MapCanvasViewModelLongPressTimeoutTest.xml` |
| the sheet reports a move | the slider's `onValueChange` emptied (`LocationOptionsOverlay.kt:457`) | `longPressTimeoutSliderShowsAndWritesTheValue` — `the sheet reports the moved value expected:<[900]> but was:<[]>` (`LocationOptionsOverlayComposeTest.kt:695`) | tests="1" failures="1" errors="0", `2026-10-27T18:38:03.145Z`, `mutation-C-sheet-report-LocationOptionsOverlayComposeTest.xml` |
| render in every mode (finding 1's invariant) | the long-press row wrapped in `if (mode == MapMode.BROWSE)` | `longPressTimeoutSliderIsReachableInFreeDrive` (`:644`) and `…InNavigation` (`:651`) — `Action performScrollTo() failed`, no node matches "Long-press delay" (`assertLongPressTimeoutSliderVisible`, `:629`); the Browse case stayed green | tests="27" failures="2" errors="0", `2026-10-27T18:38:32.852Z`, `mutation-D-render-mode-LocationOptionsOverlayComposeTest.xml` |

Restored green over all five affected classes is the full JVM run of finding 2: `focused/`
(`LocationOptionsOverlayComposeTest` 27, `MapLongPressTimeoutComposeTest` 2, `MapCanvasViewModelLongPressTimeoutTest`
2, `AppSettingsTest` 21, `MapGestureComposeTest` 13), `18:42–18:44Z`.

## Smaller corrections the review's NOTEs named

- `longPressUsesTheConfiguredAppTimeout` now passes `platformLongPressTimeoutMs = 500L` beside the app's 900 ms
  (`MapLongPressTimeoutComposeTest.kt:95`), so the case's own arguments discriminate the two sources instead of
  Robolectric's environment (`focused/TEST-com.naviveylin.ui.map.MapLongPressTimeoutComposeTest.xml` — tests="2"
  failures="0" errors="0, `2026-10-27T18:44:12.519Z`).
- `guidelines/UI.md`'s phone-only settings list now carries the long-press delay beside the overspeed delta and the
  per-vehicle maximum speeds (`guidelines/UI.md:962`); the proposal names that list as an owning section.
- `proposal.md` and `design.md` state the handler's signature as implemented (`Long? = null`, resolved in
  composition), and say that the re-key itself is not exercised by the two Compose cases (they fix the value before
  the gesture) — the resolution order is what they pin.
- `design.md`'s deleted scratch-probe measurement ("after-waitForIdle presses=0") is replaced by the two cases that
  cover the same fact, and D3's uncited accessibility-delay rationale is replaced by what the constants say: the
  range brackets the platform's 500 ms default.
- The spec's clamp scenario says "the loaded settings carry …" (the case asserts the accessor, not a decoded file),
  and the "50 ms steps" clause is attributed to the sheet's slider rather than to the storage's reader, which
  deliberately does not snap.
- `gate.md` marks its `18:29Z` as an authored wall-clock (the gate prints no timestamp) and quotes the compiled
  step's executed count beside its command.

## Where the round's pointers stand

`bash tools/loop/evidence-check.sh openspec/changes/archive/2026-10-27-add-configurable-long-press-timeout --root .`
— PASS (C1 1 checked, C2 38 pairs confirmed, C3 none, C4 no marker under `app/src`, C5 529 file:line resolved).
