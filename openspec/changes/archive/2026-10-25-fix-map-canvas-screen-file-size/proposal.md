# Proposal — fix-map-canvas-screen-file-size

## Why

`app/src/main/java/com/naviveylin/ui/map/MapCanvasScreen.kt` is **3 384 lines**, past the `source-size` row's
bound of 1 500 — item #298, class `improvement`, and the improvement is a non-defect: the row counts lines per
tracked file and files one item per path, and the item closes when that path drops under the bound
(`tools/sensors/sensors.json:74`, `"threshold": 1500, "comparison": "greater-than"`, owned by
`guidelines/Backlog.md:707` "One row watches a source unit's size", over the owner's recorded decision of
2026-10-21: 1 500 lines per file, tests included).

**Root cause (one).** The file's only large declaration is the one composable that carries the whole phone map
screen — `MapCanvasScreen.kt:195` — and it holds every topic of that screen: the ViewModel wiring effects, the
~48 remembered display/hold/crossfade/gesture values, the follow display loop, the five overlay band contents
and the file-foot helper composables and geometry helpers — so the file's length is the sum of every topic's
code, and each topic that grows there grows that sum.

**Red case — the number the delta must move, taken with the row's own measurement.** A non-defect has no
failing JUnit case to offer unless the build grows a second copy of the row, so the red case is the row's probe
(`evidence/red-probe-head.log`):

```
$ SENSORS_THRESHOLD=1500 SENSORS_CONDITIONS='[{"state":"OPEN","condition":{"path":"app/src/main/java/com/naviveylin/ui/map/MapCanvasScreen.kt"}}]' bash tools/sensors/metrics/source-size.sh
{"path":"app/src/main/java/com/naviveylin/ui/map/MapCanvasScreen.kt","fingerprint":"ac5ac11409706f8e0b727ebeabd3edf841a68ec48f6294830690cb242f09f3ac","measurement":"3384","verdict":"violation","baseline":"","title":"the source file is past the size bound","claim":"app/src/main/java/com/naviveylin/ui/map/MapCanvasScreen.kt is 3384 lines, past the row's bound of 1500"}
```

On base `635a4da3` with a clean tree, 2026-10-25T19:01:59Z (the probe's own `git status --porcelain | wc -l`
line reports 0 changed paths). After the split the same probe prints
`"measurement":"1109","verdict":"clear"` under the same fingerprint (`evidence/green-probe.log`) — the row's
`clear` finding is what closes the item (`tools/sensors/metrics/source-size.sh`, the clearing pass over the open
conditions).

**The invariant the split keeps.** Nothing was retyped: each moved block is the same statements in a new file.
A composable body in another file cannot see the screen's remembered `var … by remember { mutableStateOf(…) }`
locals, so the values a moved block reads *and* writes are threaded as `MutableState` objects and their
references renamed from `x` to `xState.value` — **208 such references**, counted per file in
`evidence/line-accounting.log` (99 in the map band, 78 in the follow loop, 23 in the modal bands, 7 in the
chrome band, 1 in the parts file), and 44 remembered values in the screen now carry the `val xState` /
`var x by xState` pair the threading needs. Everything else — the band wrappers, the `when`/`if` structure, the
call ordering, the log lines and their arguments — is unchanged.

**The two shipped cases the split touches, and why they survive.** `MapMenuBackOrderComposeTest` reads
`MapCanvasScreen.kt` for the `zIndex(MapLayer.…)` order of the chrome, menu and modal bands: the five band
wrappers stayed in the screen (`grep -c 'zIndex(MapLayer' MapCanvasScreen.kt` → 5), so that case is untouched
and green. `FollowBlitCallSiteTest` pins the seam call to the file it was in; the loop moved, so its file path
is realigned to `MapCanvasFollowDisplay.kt` and **its assertions are unchanged** (`evidence/line-accounting.log`
carries the 8-line test diff: path, one KDoc sentence, one assert message).

**The owning requirement.** `openspec/specs/backlog-sensors/spec.md:412` — "A source file's size is a declared
check" — states the row, the bound and the per-path identity; `unit-test-suite-runtime` states that every test
class of the module executes, which is what the focused run keeps whole. The guideline sections the delta
answers to are `guidelines/UI.md:1294` "11. Phone overlay layering (the band stack)" (the band is the only thing
that decides stacking, and it stayed in the screen), `guidelines/MapRendering.md:547` "13. Overrun Display
Window (pan and follow)" and `guidelines/Design.md:413` "7. Follow mode & GPS" (the loop's frame semantics),
`guidelines/Design.md:98` "2. Stack & UI patterns" (the split's shape) and `guidelines/Build.md:103` "2a. Agent
iteration protocol" (how this change was measured).

**Surface before the plan was fixed.** `grep -rl 'MapCanvasScreen' app/src auto/src core/src` → 28 files;
`grep -rl 'MapCanvasScreen.kt' app/src/test core/src/test` → 2 (the two cases above, one of which reads the
file); `grep -rn 'MapCanvasScreen.kt' openspec/specs guidelines tools | wc -l` → 1 (the `MapLayer` enum pointer
in `guidelines/UI.md:1297`, still true — the enum did not move). Five test comments named the file for code that
moved and are realigned to the new files (`MapLayerStackComposeTest` ×2, `RouteSessionCardExitTest` ×2,
`MapNavColumnWidthProbeTest`, `FollowAnchorFramingTest`, `MapCanvasViewModelFixQualityTest`), and two guideline
sentences that name the overlay-width provider's location now name `MapCanvasScreenChromeBand.kt`
(`guidelines/MapRendering.md`, `guidelines/UI.md`). The measurement is `evidence/surface.log`.

surface: 0 cases added, 1 case realigned (path only, assertions unchanged), 7 test comments realigned in 5 test
files, 2 guideline sentences realigned — `grep -rl 'MapCanvasScreen' app/src auto/src core/src` (28 files),
`grep -rl 'MapCanvasScreen.kt' app/src/test core/src/test` (2), `grep -rn 'MapCanvasScreen.kt' openspec/specs
guidelines tools` (1).

## What Changes

The file becomes six, each under the bound, and no statement changes meaning:

| file | what it holds | lines |
|---|---|---|
| `MapCanvasScreen.kt` | the screen: `MapLayer`, the ViewModel wiring effects, the remembered display/gesture state, the local helpers the bands call (`animateDiscreteZoom`, `panWindowOffset`, `commitPanCenter`, `requestPanRecenter`, `endCrossfade`, `freezeZoomAnimationForGesture`, `zoomAnimationAnchor`), the five band wrappers with their `zIndex`/`testTag`, and the inset publisher | **1 109** |
| `MapCanvasMapBand.kt` (new) | the `MapLayer.MAP` band content: the rendered frame, its gesture handling, the analysed-route highlight and the loading/error states | 677 |
| `MapCanvasScreenChromeBand.kt` (new) | the `MapLayer.CHROME` band content: the orientation-aware overlay layout, the control columns, the street-name pill, turn card, routing status, attribution, car-session indicator, and the single `LocalOverlayWidthProbe` provider | 629 |
| `MapCanvasScreenModalBands.kt` (new) | the `MapLayer.MENU`, `MapLayer.MODAL` and `MapLayer.SNACKBAR` band contents: the map menu, every surface that opens over the map, the permission and rationale dialogs, and the snackbar host | 409 |
| `MapCanvasFollowDisplay.kt` (new) | the follow display loop: the per-frame `withFrameNanos` iteration (zoom animation, render-completion crossfades, prediction feed, displayed centre, blit offset through `FollowBlitRules`, pan window through `PanWindowRules`, bounded-rate frame refresh) | 427 |
| `MapCanvasScreenParts.kt` (new) | the file-foot helpers and on-map controls: gesture/rotation geometry helpers, `KeepScreenOnEffect`, `MapMenu`, the overlay button style, the action/widget/compass columns, re-center and drive-mode buttons, `LocalOverlayWidthProbe`, the car-session presence helper | 762 |

**Additive, no breaking change.** No behaviour changes, no spec text, no build logic, no manifest, no resource.
The screen's public entry point (`MapCanvasScreen`, its parameters and defaults) is unchanged; the new
composables are `internal` to `:app`. Rollback path: revert this change's commit — the five new files
disappear, the 3 384-line file returns, and the red probe's `violation` at `measurement=3384` reproduces.

**Not a native/JNI change**: no submodule patch, no `:osmscout-client-java` override, no CMake input.

## Capabilities

### New Capabilities

(none)

### Modified Capabilities

(none) — no requirement changes, so `.openspec.yaml` declares `skip_specs: true` (see `design.md`, "Why no
delta"). The rule this change satisfies is the `source-size` row's, which
`openspec/specs/backlog-sensors/spec.md:412` already states and which this change does not alter: the bound, the
unit, the scope and the fingerprint are untouched, and the finding that closes the item is the row's own
clearing pass.

## Impact

Production sources (`:app`)
- `app/src/main/java/com/naviveylin/ui/map/MapCanvasScreen.kt` — from 3 384 to 1 109 lines
- `MapCanvasMapBand.kt`, `MapCanvasScreenChromeBand.kt`, `MapCanvasScreenModalBands.kt`,
  `MapCanvasFollowDisplay.kt`, `MapCanvasScreenParts.kt` — new, 677 / 629 / 409 / 427 / 762 lines

Test sources (`:app`)
- `FollowBlitCallSiteTest.kt` — the file it reads, one KDoc sentence, one assert message (assertions unchanged)
- `MapNavColumnWidthProbeTest.kt`, `FollowAnchorFramingTest.kt`, `MapCanvasViewModelFixQualityTest.kt`,
  `MapLayerStackComposeTest.kt`, `RouteSessionCardExitTest.kt` — comments only, realigned to the files the code
  moved to

Guidelines — two sentences naming a moved file
- `guidelines/MapRendering.md`, `guidelines/UI.md`

Specs — read, not edited
- `openspec/specs/backlog-sensors/spec.md:412`, `openspec/specs/unit-test-suite-runtime/spec.md`

Verification (each task's own step, `tasks.md`)
- the row's probe before and after (`evidence/red-probe-head.log`, `evidence/green-probe.log`)
- the line counts and the reference accounting (`evidence/line-accounting.log`)
- the focused map suite — 837 cases in 114 XMLs, none failing (`evidence/focused-tally.log`)
- `./tools/loop/gate.sh` once, because the change touches compiled paths
