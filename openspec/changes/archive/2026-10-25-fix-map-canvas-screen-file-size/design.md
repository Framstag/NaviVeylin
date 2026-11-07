# Design — fix-map-canvas-screen-file-size

## Root cause (one)

`MapCanvasScreen.kt:195` is the file's only large declaration, and that one composable carries every topic of
the phone map screen: the ViewModel wiring effects, the ~48 remembered display/hold/crossfade/gesture values,
the follow display loop, the five overlay band contents and the file-foot helpers. The bound is per file
(`tools/sensors/metrics/source-size.sh` counts `git ls-files -z '*.kt' '*.java'`), so a file that carries every
topic is over it exactly when the topics collectively are — nothing else adds a line to it.

## The single fix

Split the screen's one composable body by what it composes, and move each block into its own file:

1. the file-foot helpers and on-map controls (the 679-line tail) → `MapCanvasScreenParts.kt`;
2. the four front band contents (`CHROME`, `MENU`, `MODAL`, `SNACKBAR`) → `MapCanvasScreenChromeBand.kt` and
   `MapCanvasScreenModalBands.kt`;
3. the follow display loop → `MapCanvasFollowDisplay.kt`;
4. the backmost band content (`MAP`) → `MapCanvasMapBand.kt`.

Each moved block becomes a `@Composable internal fun …` beside the screen, and the screen keeps the band
wrapper it belongs to:

```kotlin
Box(modifier = Modifier.fillMaxSize().zIndex(MapLayer.CHROME.z).testTag(MapLayer.CHROME.tag)) {
    MapCanvasChromeBandContent(state = state, …)
}
```

The wrapper staying put is what keeps two properties the spec pins, at no extra cost:
`guidelines/UI.md:1294` "11. Phone overlay layering (the band stack)" — the band, not the file position,
decides stacking — and the back-handler registration order, which `MapMenuBackOrderComposeTest` reads out of
the wrapper order in `MapCanvasScreen.kt` (`grep -c 'zIndex(MapLayer'` → 5, unchanged).

**Why not extension functions, the shape the two earlier file-size changes used.** `AutoMapRenderer` and
`MapCanvasViewModel` are classes, so their members could move out as extension functions with nothing retyped.
A composable body is not a member: the values it reads are locals of the composing function, and a body in
another file sees only what it is handed. So the fix is parameter threading, and the threading rule below is
the whole of the mechanism.

## The threading rule (why `MutableState` and not values)

A moved composable is called from the screen on every recomposition, so a **value** parameter would be fresh
each time — except where the block starts something that outlives one composition. The follow display loop is
exactly that: its `LaunchedEffect` body starts once and runs for the screen's lifetime, so a value captured
there is the *first* composition's value forever. That is why

- a value the block only *reads in composition* is a plain parameter (`state`, `navState`, `viewModel`,
  `surfaceColor`, `isLandscape`, the action lambdas);
- a value the block *writes* is a setter lambda (`onRightInset`, `onMenuExpanded`, `onShowNavDetails`);
- a value the block *reads and writes*, or any value the loop reads, is the `MutableState` object itself, and
  the screen keeps the delegate it already had:

```kotlin
val zoomAnimScaleState = remember { mutableStateOf(1f) }
var zoomAnimScale by zoomAnimScaleState
```

The delegate is what leaves every other line of the screen unchanged: `zoomAnimScale` still reads and writes
the same state from the draw code, the gesture callbacks and the band files, and the loop reads the same object
through `zoomAnimScaleState.value`. Two consequences worth stating because they are the change's only
non-mechanical edits: a reference inside a moved block was renamed `x` → `xState.value` (208 of them), and two
log lines inside the follow loop had their interpolation changed from `$x` to `${xState.value}` so the printed
text is byte-identical to before.

## The recorded decision, and the number its estimate did not carry

The owner recorded the split shape on 2026-10-25 (item #298, `ITEM_DECISION`): *move the four band contents and
the 679-line tail byte-identical, move the follow loop into its own file with the setters threaded, realign
`FollowBlitCallSiteTest`'s path, land ≈1 419 lines, 5 files, no spec text touched* — and file the state-holder
extraction (its option 2, which would also serve `MapCanvasViewModel`) as a **new issue** rather than doing it
here. This change executes that option 1. Its estimate of ≈1 419 was made before the parameter call sites were
counted: measured per step, the four band contents, the tail and the loop alone left the screen at **1 641**
lines (1 109 + the 575 lines of the backmost band content the last step removed − the 43-line call site it
added), still 141 over the bound. The backmost band's content therefore moved as well and the file measures
**1 109** — 391 under the bound. That is the decision's shape with one more band content, not a different
shape: the state holder is still out of scope and still the follow-up issue.

## What each file holds

| file | lines | what |
|---|---|---|
| `MapCanvasScreen.kt` | 1 109 | `MapLayer`; the wiring effects; the remembered display/gesture state; the inset publisher; the local helpers the bands call; the five band wrappers and their calls |
| `MapCanvasMapBand.kt` | 677 | `MapLayer.MAP` content: canvas, gesture handling, analysed-route highlight, loading/error states |
| `MapCanvasScreenChromeBand.kt` | 629 | `MapLayer.CHROME` content: overlay layout, control columns, street-name pill, turn card, routing status, attribution, car-session indicator, the single `LocalOverlayWidthProbe` provider (`MapCanvasScreenChromeBand.kt:76`) |
| `MapCanvasScreenModalBands.kt` | 409 | `MapLayer.MENU`, `MODAL` and `SNACKBAR` contents: menu, every surface over the map, dialogs, snackbar host |
| `MapCanvasFollowDisplay.kt` | 427 | the follow display loop (`withFrameNanos` at `MapCanvasFollowDisplay.kt:101`, the blit seam call at `MapCanvasFollowDisplay.kt:284`) |
| `MapCanvasScreenParts.kt` | 762 | file-foot helpers and on-map controls (`MapMenu` at `MapCanvasScreenParts.kt:454`) |

## Alternatives the evidence eliminated, and the fork the owner had already settled

1. **Retune or baseline the row** (`tools/sensors/sensors.json:74`, `baseline`): impossible as a fix — the bound
   is the owner's recorded decision of 2026-10-21 (1 500 lines, file level, tests included,
   `guidelines/Backlog.md:707`), and a baseline would silence the row the item exists under.
2. **A guard test that asserts the screen's line count**: impossible — it duplicates the row, whose scope is
   every tracked `.kt`/`.java` file, and it would redden on the other files the same probe finds past the bound
   and that are other items' work.
3. **One composable for the whole remaining body** (a single `MapCanvasScreenBody(...)` with ~60 parameters):
   rejected — it moves the file's lines into a worse file without giving any block a name, and the band
   wrappers would lose the order the shipped back-handler case reads.
4. **Extracting band contents per branch** (browse vs navigation) instead of per band: rejected — it multiplies
   the parameter lists, and `guidelines/UI.md:1327` states the opposite rule ("A contract the whole band shares
   is provided for the whole band"), which the single `LocalOverlayWidthProbe` provider in the chrome band keeps.
5. **Keeping the loop in place and moving only the backmost band**: it also lands under the bound, but the
   owner's recorded decision names the loop's move and the seam case's realignment, and that question is
   settled — it is not re-opened here. (The state-holder *alternative* the decision's option 2 names is not
   this change: the decision defers it to a new issue.)

## Blast radius

- 5 files created, 1 rewritten; 5 test files and 2 guideline sentences realigned (path and comment only —
  assertions unchanged); 0 specs, 0 build logic, 0 resources, 0 manifest.
- Threading, ownership and lifecycle are untouched: no `LaunchedEffect` moved to a different position in
  composition, the follow loop still starts once per screen, the gestures still write the same remembered
  states, and the band composables still run on the composition thread the screen runs on. The loop's frame
  semantics — order of animation ticks, crossfades, prediction updates, offset clamps and refresh requests —
  are the same statements in the same order, which `guidelines/MapRendering.md:547` and
  `guidelines/Design.md:413` pin and the map suite's follow and framing cases cover.
- `:app` is compiled into both distribution flavors: both compile, and the gate runs the module's JVM suite.

## Why no delta

No requirement changes: the `source-size` row's bound, unit, scope and fingerprint are untouched, the screen's
entry point and parameters are unchanged, and the behaviour is line for line. `.openspec.yaml` declares
`skip_specs: true`; the requirement the change satisfies — `openspec/specs/backlog-sensors/spec.md:412` "A
source file's size is a declared check" — already exists and is what the red probe and the green probe are read
against.
