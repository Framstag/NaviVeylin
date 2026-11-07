# Design — add-configurable-long-press-timeout

## Context

See `proposal.md` — Why for the absence and the red case. The current state that constrains the approach:

- One handler decides every map gesture: `Modifier.mapGestureHandler(callbacks)` (`MapGestures.kt:70`) runs inside
  `composed`, holds its gesture state in a `pointerInput` block, and its first phase is
  `withTimeoutOrNull(500L) { … }` (`MapGestures.kt:87`). The block is keyed on `Unit`, so it is installed once per
  composition.
- It has exactly one production caller, `MapCanvasMapBandContent` (`MapCanvasMapBand.kt:64`, the handler at
  `MapCanvasMapBand.kt:198`), which already receives the rendered `MapCanvasUiState` as a parameter — the value is
  therefore readable in composition, and the band recomposes when the state changes.
- Settings that the phone edits live in one place: `AppSettings` in the settings JSON, written by
  `SettingsStorage.update { … }` on its own `ioDispatcher` under a mutex (`SettingsStorage.kt:148`), surfaced to the
  canvas by `MapCanvasUiState`, restored on load by `MapCanvasViewModelSettings` (the restore block at
  `MapCanvasViewModelSettings.kt:397`), and edited in the location-options sheet.
- Time in a Compose UI test is the test clock's: `mainClock.advanceTimeBy` drives the handler's own timeout, while
  `waitForIdle` does not advance time — which is what `MapLongPressTimeoutComposeTest`'s two cases rest on: each
  holds the press, advances the clock and reads the delivery
  (`evidence/focused/TEST-com.naviveylin.ui.map.MapLongPressTimeoutComposeTest.xml`). `Thread.sleep` is refused by
  the build's `checkNoWallClockWaits` (`guidelines/Build.md`, section "Wall-clock waits and the conversion
  measured").

## Goals / Non-Goals

**Goals** — the map's long-press duration is configuration in both senses: overridable by the caller (the app)
and defaulting to what the platform configures; the app's value persists and is editable on the phone; a press
shorter than the configured duration never opens the description.

**Non-Goals** — no per-surface (car) control and no `AutoSettings` field: no car surface has a long-press gesture.
No change to what a long press *does* (candidate lookup, ranking, description), and no change to the pan/zoom/
rotate phases. No general gesture-settings framework.

## Decisions

### D1: The duration is a parameter of the handler, defaulting to the platform's value

`mapGestureHandler(callbacks, longPressTimeoutMs: Long? = null)`: a null means "the platform's", resolved inside
`composed` as `longPressTimeoutMs ?: LocalViewConfiguration.current.longPressTimeoutMillis`, and the pointer block is
keyed on that resolved value (`pointerInput(resolvedLongPressTimeoutMs)`), so a changed value re-keys the handler for
the next gesture. Neither Compose case exercises the re-key itself — both fix the value before `performTouchInput` —
so what the cases pin is the resolution order (the app's value wins, the platform's applies when the app configures
none), not the re-key.

*Alternatives considered.*

- **Read `LocalViewConfiguration` inside the handler and nothing else.** Smallest diff, but then the app's own
  setting has nowhere to go: the sheet's value could only reach the handler through a second composition local,
  which is the same parameter with more indirection and a hidden contract.
- **Inject a clock (`nowMs: () -> Long`) plus the duration.** Would make the timeout testable without Compose's
  clock, but it also moves the waiting out of `withTimeoutOrNull` into hand-written deadline arithmetic inside a
  pointer loop that must not block on events — more code, more failure modes, and the test seam already works
  (`mainClock` drives the timeout; measured above).
- **Keep the literal and add the setting only.** That is the item's defect one layer up: a configured value the
  handler ignores.

*Risk.* `pointerInput` re-registers when the duration changes; a gesture in flight when the user moves the slider is
ended by the re-registration — acceptable (the sheet covers the map while the slider moves, and a long press is
500–1000 ms of an interaction the user is not performing at that moment).

### D2: The persisted value lives in `AppSettings`, clamped at the storage edge

`AppSettings.longPressTimeoutMs: Int = 500`, with `LONG_PRESS_TIMEOUT_MIN_MS = 250`, `…_MAX_MS = 1000`,
`…_STEP_MS = 50` and `…_DEFAULT_MS = 500` beside it, and an `effectiveLongPressTimeoutMs` accessor that clamps.
The setter writes the clamped value; the load path and the canvas state read the clamped accessor.

*Alternatives considered.*

- **A separate preferences file (JavaScout's shape: `config.properties`).** This app persists every setting in one
  JSON through `SettingsStorage` (`guidelines/Design.md`, section "Data, persistence & search": "the settings file
  under the same serialised transaction as every other setting"); a second store would be a second transaction, a second migration path and a
  second thing a reset has to clear.
- **Clamp in the UI only.** A hand-edited or restored-from-backup file could then install a 40 ms (twitchy) or
  4000 ms timeout; the storage edge is the one place every path crosses.
- **A "follow the platform" sentinel (0 = unset).** Honest about precedence, but unrepresentable in the sheet's
  slider without a fourth control ("use system"), and the platform default is the same 500 ms on Android, so the
  sentinel would carry no user-visible difference.

*Risk.* The field is phone-only; the car's settings write-back (`AutoSettings.toAppSettings`) copies onto
`current`, so it preserves the value (pinned by the existing behaviour of that function, not by a new test).

### D3: The sheet's control is a labelled slider over 250–1000 ms in 50 ms steps

Rendered like the overspeed delta control (row with label and current value, then a `Slider`) and always visible,
since the value is a map-wide setting in every mode.

*Alternatives considered.*

- **100–2000 ms in 100 ms steps.** Wider, but 2000 ms reads as a hang to a user who has not understood the
  setting, and the 100 ms step cannot express the platform's own 500 ms on a coarse track without landing between
  ticks.
- **A free-text millisecond field.** Re-opens validation, keyboard and locale surface for a value with a natural
  slider range, and the sheet has no text-entry row today.

*Risk.* Sixteen slider positions is coarse for a user who wants 520 ms; the range brackets Android's own default
(500 ms) without leaving it, so the platform's own long-press duration is reachable from the control.

### D4: The two sheet parameters are required, not defaulted

`LocationOptionsOverlay` and its content composable take `longPressTimeoutMs: Int` and
`onSetLongPressTimeoutMs: (Int) -> Unit` without defaults, so every construction has to say what it wants. The file
already rules this way for the max-speed and anchor rows (`LocationOptionsOverlay.kt:99-113`: an optional callback
"would let a call site compile into a slider whose selection is silently dropped").

*Alternatives considered.*

- **Default them (`= 500`, `= {}`), like the overspeed row.** Zero churn at the ten test constructions, but it is
  exactly the silent-drop shape the file documents; the three production sites would still have to be edited, so
  the saving is test-only.
- **Default them and add a lint/test that every call site passes them.** A second mechanism to enforce what the
  type system does for free.

## Blast radius

- One production composable (`MapCanvasMapBandContent`) changes what it hands to a modifier it already calls; the
  handler's other phases are untouched.
- Three production sheet constructions gain two arguments each; the ten test constructions gain the same two.
- One new JSON field: an older build reading it ignores it (`ignoreUnknownKeys`), a newer build reading an older
  file gets 500 ms. No schema version bump, no migration step.
- Nothing on the car path, nothing in `core`, no native or JNI change.

## Rollback

Revert the change: the field is additive JSON (an older build ignores it), the slider row disappears with the
parameters, and the handler returns to its literal. No data repair, no user-visible residue beyond the extra JSON
key.

## Threading and lifecycle

No new thread, dispatcher or scope. The duration is read once per gesture in composition on the main thread; the
persisted write goes through `SettingsStorage.update` on its existing `ioDispatcher` under its write mutex, exactly
as the overspeed delta does. The `pointerInput` block's lifetime is unchanged apart from its new key.

## Verification

- Host tests: the two gesture cases (`MapLongPressTimeoutComposeTest`, one of them the red case already measured on
  the base), the storage cases (`AppSettingsTest`), the view-model cases
  (`MapCanvasViewModelLongPressTimeoutTest`), the sheet case (`LocationOptionsOverlayComposeTest`).
- The gate (`./tools/loop/gate.sh`) once, quoted, with its tallies copied into `evidence/`.
- On-device: not required for this change's contract — the observable is a duration the host test drives with the
  test clock, and the control's geometry is asserted by the Compose UI case. A device check that re-measured the
  same duration would use `input motionevent` timings, which `guidelines/Build.md`, section "Device recipes that cost
  a round every time", already records as costing a
  round without settling anything the host case leaves open.
