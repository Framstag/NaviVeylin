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
  the current viewport (`MapRenderer.kt:484-489`). Before any frame is displayed there is nothing to invalidate,
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

## Round 2 — the reviewer's three blocking findings

Review round 1 failed (`evidence/review-round-1-requested.md`, `reviewer-report-1.md` in the item record) with
three findings. Round 1 pushed the flag itself from the widget, which made the widget a second writer with its
own record. The corrections replace the writer, not the effect's placement.

**1. The ordering half was exercised by no case.** Round 1 asserted the *content* of `client.styleFlags` after a
frame had been observed; a push that ran after the first frame passed identically, and the mutation that should
prove the placement was the only thing the case could catch. Two things were wrong with that reasoning:

- the renderer requests frames **asynchronously**, so native call order can never show when the *request* was
  made — the round-1 mutation (moving the effect) could not fail the case either, measured 2026-11-04;
- what the requirement actually protects is the variant the **first frame is rendered under**.

`FakeOSMScoutClient` now records `daylightAtRender` — the flag value in effect at each render — and both variant
cases assert it before anything else. Mutation: `if (!hadFrame) return@LaunchedEffect` before the push (a push
that lands only after a frame exists) fails both cases on that assertion
(`evidence/revert-check-3.2a.log`, `…3.2a-red.xml`: `expected:<[false]> but was:<[null]>`).

**2. The widget was a second writer that could not satisfy the dedupe key.** §15 keys the app-side decision on
the pair (style name, `daylight`), and a per-writer record cannot see another writer's value: a mini-map mount
reloaded the whole stylesheet set for a value the client already carried, which drops the tile cache (`§17`) and
no `map-styles` delta legalised it.

Correction: `MapDaylightApplier` (`app/…/ui/map/MapDaylightApplier.kt`) is one record per client and value, and
**every phone writer** goes through it — `MapCanvasViewModel.ensureMapStyle`/`pushDaylight` and the widget. It
records only after `wasLastStyleLoadSuccessful()` returned true, so a rejected reload is retried by the next
writer, and `force` carries the reasons `MapStyleApplyReason` names (`STARTUP_DB_READY`, `FIRST_RENDERED_FRAME`).
A stylesheet load records the value it installed, because `loadStyleSheet` hands the load the enabled flags.
The budget is therefore one reload **per changed value per client**, which is what §15 and `map-styles` already
state — no exception, no new delta. Mutation: `force = true` at the widget's push fails
`aClientThatAlreadyCarriesTheVariantIsNotReloaded` (`evidence/revert-check-3.3.log`).

**3. The widget's load path reported nothing.** §15a's "every load path reports the failure once" applies to the
flag reload too, and `MapStyleLoadReporter` is reachable from `:core`. The widget now reports through
`rejectedVariantMessage` (one `DiagnosticsLog` entry plus the shared wording) and hands the message to an
optional `onStyleLoadFailure` sink, which `SearchDialog`/`PoiResultsWithMap` and `LocationDetailsDialog` wire to
`uiState.snackbarMessage`; with no sink the message is logged. Mutation: reducing the `FAILED` branch to `Unit`
fails `aRejectedVariantReloadIsReported` (`evidence/revert-check-3.4.log`).

What this costs: three signatures gain the optional sink, and the widget keeps a remembered value — but only
for its **own re-render** (`renderedDark`), not as the flag's record, which stays the applier's. What it buys: one
recorded value per client, so no surface can hold the client at a variant the other surface does not expect, and
the reload budget is the one §15 states.

**Round 1 claims this retires.** "One extra stylesheet load per mini-map mount" (design, blast radius) is gone —
measured by the dedupe case. "No signature change" is false now. "Composition order proves the push precedes
the first frame" is replaced by the first-frame-variant record, because order of *requests* is not observable
with an asynchronous renderer.

**Mutation of the re-render half** (round 1's other open NOTE): `if (hadFrame) renderer.invalidateStyle()`
reduced to `if (false && …)` fails `presentationChangeReRendersTheMiniMap` with a render-count timeout
(`evidence/revert-check-3.2b.log`), so the half task 3.1 left unmutated is now falsified.

## Round 3 — where the record and the re-render part company

Round 2 wired both to the applier's answer, which is wrong in one direction: on a presentation flip the view
model pushes first (its collector runs before Compose recomposes the dialog), so the widget's `push` answers
`UNCHANGED` — and round 2 returned without invalidating. The mini map kept the other variant: the second half of
item #729, on the path its own scenario forbids. The mirror case (widget first, main map second) lost the main
map's re-render the same way.

**The rule now.** Two facts, two owners:

- *Which variant does the client carry?* — `MapDaylightApplier`'s record, one per client. It decides whether a
  native call is needed, and a palette beside the bitmap reads it, so the palette follows the applied variant
  even after a rejected reload (`guidelines/UI.md` §9).
- *Which variant did this surface last draw?* — the surface's own value: the widget's `renderedDark` (with
  `appliedDark` for its palette) and the view model's `appliedMapStyle`, plus the `force` reasons
  (`STARTUP_DB_READY`, `FIRST_RENDERED_FRAME`) that exist because the native side may have dropped the earlier
  push. A change there invalidates that surface's own renderer, whoever performed the native call, and does not
  depend on the applier's answer.

**A rejected reload keeps the record it had.** Round 2 cleared it, which lost `applied()` (the palette fell back
on the *requested* variant), made the next writer re-push a value the client already carried, and — because
`pushDaylight` also overwrote `appliedMapStyle` on `FAILED` — made a successful retry skip its own re-render. Now
nothing is written on `FAILED`: `applied()` still names the variant the client carries, the target value stays
unrecorded so the retry pushes, and the retry's `changed` is true so it re-renders.

**Evidence.** `aValueAnotherWriterAppliedStillReRendersTheWidget` (mutation: the outcome-dependent invalidation,
`evidence/revert-check-3.5.log`) and `aRejectedReloadKeepsTheVariantTheClientCarries` (mutation: clearing the
record, `evidence/revert-check-3.7.log`); `MapCanvasViewModelViewportRestoreTest` pins the forced re-render the
round-2 gate caught (mutation: `changed || force` → `changed`, `evidence/revert-check-3.8.log`).

**One seam, one report.** `rejectedLoadMessage` is the single call site of
`MapStyleLoadReporter.reportFailure` on the phone — the view model's `reportStyleLoadFailure` and the widget's
`rejectedVariantMessage` both go through it (§15a's "one place").

**What round 2 got wrong about its own record.** Its "no remembered value" was false (the widget remembers what
it drew), its `FAILED` branch destroyed the record, and four of its re-dated red halves were round 2's tallies —
all nine are re-run on the sources that land (`evidence/revert-check-3.2a…3.4-red.xml` at `22:05–22:08Z`; 3.5–3.9
as dated in `tasks.md` 6.9).

## Round 4 and 5 — what stayed and what was withdrawn

Round 4 asked for a case and a mutation behind `pushDaylight`'s `FAILED` return (done: 6.10, revert-check 3.9),
and round 5 asked the same for a widget edit that compared the re-render against `MapDaylightApplier.applied`.
That edit is **withdrawn**: the effect re-runs only when `isDarkPresentation` or the renderer changes, so
`renderedDark` is only ever compared within an effect run that the requested value started — the two comparisons
differ in exactly one reachable state (a rejected reload, where comparing against the carried value would skip an
invalidation that leaves the same pixels), and no case could distinguish them without inventing a third writer.
The guard therefore compares the requested value again, and what round 5 keeps is the **palette**: it reads
`applied()`, so after a rejected reload it draws the variant the client carries. The record behind it is
`MapDaylightApplierTest` "aRejectedReloadKeepsTheVariantTheClientCarries" (3.7); the drawn colour itself has no
case, and `tasks.md` 6.8 says so.
