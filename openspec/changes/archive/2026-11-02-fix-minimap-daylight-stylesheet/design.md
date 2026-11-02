# Design — the mini map pushes the daylight stylesheet variant

## Root cause

`MiniMap` created its own `MapRenderer` from the shared `OSMScoutClient` (`MiniMap.kt:99-102`) but never wrote the
stylesheet `daylight` flag that renderer's frames depend on. `isDarkPresentation` reached the Compose overlay
palette only (`:238`), never the client. The widget therefore rendered under whatever variant the main map's
`ensureMapStyle` had installed, and — because it owns its own `MapRenderer` with its own `TileCache` and front
buffer — a later variant change invalidated the main map's frame but left the mini map's bitmap and tiles at the
old variant. This explains both halves of the symptom: a stale variant after a change, and no assertion of the
variant the widget itself needs.

## The single fix

In `MiniMap.kt`, beside the renderer creation and **before** the size/location render effect:

```kotlin
var pushedDaylight by remember(renderer) { mutableStateOf<Boolean?>(null) }

LaunchedEffect(renderer, isDarkPresentation) {
    val daylight = !isDarkPresentation
    if (pushedDaylight == daylight) return@LaunchedEffect
    val rendered = pushedDaylight != null && renderer.frameFlow.value.bitmap != null
    pushedDaylight = daylight
    client.setStyleSheetFlag(MapCanvasViewModel.STYLE_FLAG_DAYLIGHT, daylight)
    if (rendered) renderer.invalidateStyle()
}
```

Why this shape:

- **Keyed on the value.** `LaunchedEffect` restarts only when `isDarkPresentation` changes, and the guard makes a
  re-entered effect with the same value a no-op. The push is one native call per distinct value, which is the
  dedupe `guidelines/MapStyling.md` §15 requires of every writer ("phone `stylePushedToNative` re-pushes on the
  first rendered frame, car `CarDaylightApplier` … by value").
- **Before the first frame.** The effect is composed before the `LaunchedEffect(canvasSize, lat, lon, initialMag)`
  that issues the first `requestRender`, so the first frame is rendered under the pushed variant. `DBThread::
  SetStyleFlag` queues on the same DB thread that renders, so the order is preserved even when the native side is
  still warming up; §15's startup-race rule makes a pre-database push safe.
- **`invalidateStyle()` only after a frame exists.** It clears the widget's tile cache and forces a full render at
  the current viewport (`MapRenderer.kt:480-486`). Before any frame is displayed there is nothing to invalidate,
  and the pending first render already carries the new flag. The `frameFlow.value.bitmap != null` read avoids
  rendering the renderer's default viewport when the value changes between composition and the first frame.

Threading: the push is a synchronous JNI call that queues work on the DB thread; `invalidateStyle()` submits
through the renderer's existing debounced scope. Both happen on the composition's effect dispatcher, as
`ensureMapStyle` does on the main map. No new lifecycle: the effect lives inside the widget and dies with it.

## Alternatives the evidence eliminated

1. **Leave the variant to the main map's push (today's state).** Eliminated: the widget is specified to render
   "without requiring state from the main map screen" (`openspec/specs/mini-map/spec.md:62`), it cannot see whether
   the installed variant is current, and its own cache and front buffer are not invalidated by the main map's
   `invalidateStyle()`. The red case `miniMapReRendersWhenThePresentationChanges` fails on exactly this.
2. **Read the installed flag back from the client before pushing.** Eliminated: the Java client has no accessor —
   `OSMScoutClient.java` exposes `setStyleSheetFlag`, `getActiveStyleSheet`, `wasLastStyleLoadSuccessful`, but no
   flag getter; `DBThread::GetStyleFlags` exists only in C++ (`DBThread.cpp:503`) and is not bridged. A second
   source of truth would be needed and could still disagree.
3. **Take a `applyDaylight: (Boolean) -> Boolean` callback from the embedding screen**, like
   `CarDaylightApplier`. Eliminated: the embedders (`SearchDialog`, `LocationDetailsDialog`) have no applied-style
   state of their own — they read `MapCanvasUiState.isDarkPresentation` — so the callback would carry no
   information beyond the parameter the widget already has, while moving the widget's lifecycle obligation onto
   every caller, contradicting the "Reusable embeddable widget" requirement.
4. **Recreate the renderer on a presentation change.** Eliminated: it tears down and rebuilds the coroutine scope,
   bitmaps and tile cache for a flag write, and races the widget's `DisposableEffect` shutdown; the renderer already
   has `invalidateStyle()` for exactly this.

## Blast radius

- `MiniMap` only: one effect, one remembered value; no signature change, no new public API.
- The second writer of the flag is value-deduped and writes the same resolved value the main map writes, so no
  surface can observe a different variant. The native call is one stylesheet load per distinct value per widget
  mount — the same trigger §15 names a "real presentation change", not a per-frame one.
- Tests that mount `MiniMap` with a `FakeOSMScoutClient` now see a `daylight` push; the existing 7 cases assert
  renders and viewports, not flags, so they are unaffected (verified by the focused run).

## Verification

- Focused: `./gradlew :app:testMobileDebugUnitTest --tests "com.naviveylin.ui.map.MiniMap*"` — red before the fix
  (3 failures), green after.
- Revert-check: delete the push from the effect (`// REVERT-CHECK MUTATION`), the three cases fail; restore and
  re-run forced green.
- Gate: `./tools/loop/gate.sh` once, on the whole change.
- On-device measurement is the dialog's own bitmap after a presentation flip; the JVM case asserts the flag push
  and the re-render request, which is the app-side half this change owns. The stylesheet variant itself is the
  native contract §15 already states (`SetStyleFlag` → `LoadStyleInternal`).
