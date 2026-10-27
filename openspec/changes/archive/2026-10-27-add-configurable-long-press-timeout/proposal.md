# Proposal — add-configurable-long-press-timeout

## Why

**The missing capability, and where it shows.** The phone map canvas decides a long press with a literal:
`withTimeoutOrNull(500L)` (`app/src/main/java/com/naviveylin/ui/map/MapGestures.kt:87`). That single number is the
whole long-press duration of the map: it gates `MapGestureCallbacks.onLongPress` (`MapGestures.kt:56`), which
`MapCanvasMapBand` (`app/src/main/java/com/naviveylin/ui/map/MapCanvasMapBand.kt:198`) wires to `fireLongPress`
(`app/src/main/java/com/naviveylin/ui/map/MapCanvasScreenParts.kt:109`) — the candidate picker and the description
sheet. Nothing configures it: `AppSettings` (`app/src/main/java/com/naviveylin/data/SettingsStorage.kt:36`) carries
no long-press field, and no production source of the app, the car module or the core module reads Compose's
configured long-press timeout (`grep -rn "longPressTimeoutMillis\|LocalViewConfiguration" --include='*.kt'
app/src/main auto/src/main core/src/main` → 0 matches). JavaScout, the reference app, reads one —
`Config.getLongPressTimeoutMs()` with a 500 ms default
(`app/src/main/cpp/libosmscout/JavaScout/src/main/java/com/framstag/libosmscout/Config.java:189`) — which is the
parity the item asks for: the timeout is configuration, not a constant in the handler.

**Acceptance scenario, red on the base (`3b6f2a24`), 2026-10-27T18:01:57Z.** The case composes the real gesture
handler under a configured 900 ms long-press timeout and holds a press for 600 ms of test time:

| case | XML | what it reads |
|---|---|---|
| `MapLongPressTimeoutComposeTest#longPressHonoursTheConfiguredPlatformTimeout` | `tests=1 failures=1 errors=0`, `evidence/red-long-press-platform-timeout.xml` | `a press held 600 ms is not a long press when the configured timeout is 900 ms expected:<0> but was:<1>` — the handler fired at its literal 500 ms, ignoring the configuration |

The case fails only for that reason: the second assertion of the same case (held past 900 ms → exactly one long
press) shows the press is still delivered, so the red is the *timeout*, not a missing long press. The test clock
drives the handler's own timeout (`composeRule.mainClock.advanceTimeBy`), so no case waits on the wall clock
(`guidelines/Build.md`, section "Wall-clock waits and the conversion measured").

surface: 2 cases added, 1 class added, 1 rewritten call site, 13 call sites realigned — `grep -rn
"mapGestureHandler" --include='*.kt' app/src` (4 pre-existing refs: 1 definition, 1 production call site, 2 in
`MapGestureComposeTest`), `grep -rn "LocationOptionsOverlay(" --include='*.kt' app/src/main` (3 production call
sites), `grep -rn "LocationOptionsOverlay(" --include='*.kt' app/src/test` (10 test call sites).

**Owning requirements.** New capability `map-long-press-timeout` — "The long-press timeout is a configuration, not
a literal" and "The long-press timeout is a persisted setting". Existing capability `location-options-ui` — "Long-press
timeout control", beside its existing "Overspeed warning delta control".

**Owning guidelines.** `guidelines/UI.md`, section "Phone map modes (Browse / Free drive / Navigation)" — the
location-options sheet and the state it edits — and `guidelines/Design.md`, section "Data, persistence & search" —
persist on every meaningful change, never debounced. `guidelines/Build.md`, section "Wall-clock waits and the
conversion measured" owns how the new case drives its timeout.

## What Changes

- **The gesture handler takes its timeout as configuration.** `mapGestureHandler` gains a
  `longPressTimeoutMs: Long` parameter whose default is the platform's configured long-press timeout
  (`LocalViewConfiguration.longPressTimeoutMillis`), replacing the literal `500L`. The pointer loop re-keys on the
  value (`pointerInput(timeoutMs)`), so a change applies to the next gesture without recomposing the whole canvas.
- **The value is a persisted setting.** `AppSettings.longPressTimeoutMs: Int = 500` — the settings file written
  before this change decodes to 500 (the platform default and JavaScout's), and any value is clamped to
  `250..1000` ms in `50` ms steps at the storage's edge, so a hand-edited file cannot install a timeout outside the
  range the sheet offers.
- **The phone edits it in the location-options sheet**, as a fourth always-visible setting: a labelled slider
  (`250..1000` ms, `50` ms steps, current value shown in milliseconds), applying immediately and persisting on
  every change. Phone-only: the car surfaces have no long-press gesture, so `AutoSettings` is untouched and the
  car's settings write-back preserves the phone's value (it copies onto `current`).
- **Not breaking.** A settings file from before the change keeps working and yields 500 ms; nothing else reads the
  new field. Rollback is a revert of the change: the extra JSON field is ignored by an older build
  (`ignoreUnknownKeys`), and the literal returns with the handler.

## Capabilities

### New Capabilities

- `map-long-press-timeout`: owns the contract between a configured long-press duration and the map canvas's
  long-press detection — the order of precedence (caller's value, else the platform's), the persisted field with
  its default and range, and that a press shorter than the configured duration never opens the description.

### Modified Capabilities

- `location-options-ui`: adds the long-press timeout control to the location-options bottom sheet (a slider over
  250–1000 ms in 50 ms steps, the current value shown, persisting immediately), next to the overspeed delta control.

## Impact

Affected files and modules:

- `app/src/main/java/com/naviveylin/ui/map/MapGestures.kt` — the parameter and the timeout it uses.
- `app/src/main/java/com/naviveylin/ui/map/MapCanvasMapBand.kt` — passes the state's value to the handler.
- `app/src/main/java/com/naviveylin/ui/map/MapCanvasUiState.kt` — the field the canvas draws the handler's value from.
- `app/src/main/java/com/naviveylin/ui/map/MapCanvasViewModelSettings.kt` — the setter, and the restore on load.
- `app/src/main/java/com/naviveylin/ui/map/MapCanvasScreenChromeBand.kt` — the three sheet call sites.
- `app/src/main/java/com/naviveylin/ui/map/LocationOptionsOverlay.kt` — the control and its two parameters.
- `app/src/main/java/com/naviveylin/data/SettingsStorage.kt` — the persisted field. `com.naviveylin.data` (phone
  persistence) only; `core` and `auto` are untouched.
- `app/src/main/res/values/strings.xml` — the control's label and value format.
- Tests: `MapLongPressTimeoutComposeTest` (new), `MapCanvasViewModelLongPressTimeoutTest` (new),
  `AppSettingsTest`, `LocationOptionsOverlayComposeTest` (its 10 constructions gain the two required arguments).
- `tools/feature-list/specs.json` — the classification line the new capability owes
  (`guidelines/FeatureList.md`, section "What a change owes when it touches this").

No new dependency, no native/JNI change, no Gradle configuration change, no car surface.
