# Design

## Context

See `proposal.md` — Why. Constraints that shape the approach:

- The route polyline is drawn **natively** (`OSMScoutClient.renderWithRouteAndPois`, called from
  `MapRenderer.executeRender` / `renderFromTiles`), one colour, on both the tile path and the full-render path.
- `TileCache` is **per renderer instance** (`MapRenderer.tileCache`, 200 tiles). A second renderer duplicates
  that cache and its render targets, against a measured native retention ceiling of ~220 MB
  (`MemoryPressureResponder`; the `reduce-render-peak-memory` change is in flight).
- `RouteStepDisplay` (`ui/route/RoutePanelViewModel.kt:48`) has no position; `RouteEntry` carries
  `latitudes/longitudes` (densified points from `TransformRouteDataToPoints`) and `descriptions[]`
  (formatted strings built by a separate `DescCallback` in the same
  `RouteDescriptionPostprocessor::GenerateDescription` pass).
- The route-planning UI is one `ModalBottomSheet` (`ui/route/RoutePanel.kt`) reporting its covered height to
  `MapCanvasViewModel`, which fits the overview after a settle delay with two guards.
- `SearchDialog` already proves the width-branch (`BoxWithConstraints`) two-pane pattern on this screen.

## Goals / Non-Goals

**Goals**: one session with defined exits; analysis on the map the user will drive in; no second renderer;
one behavior for phone and wide layouts; the fit owned by the session instead of measured across surfaces.

**Non-Goals** (design-level): route calculation changes; a native step index; navigation-time turn logic;
Android Auto surfaces; rewriting the route summary component.

## Decisions

### D1 — Analysis happens on the main map; the session is an overlay, not a second map

| Alternative | Why not |
|---|---|
| **A**: full-screen dialog with its own map (`MiniMap`, extended with route params) | Duplicates `MapRenderer` + its 200-tile cache and render targets against a memory-pressure regime; two renderers contend on the native render path; `MiniMap` is north-locked, pan/zoom only, and the dialog would still have its own inset math. Analysis needs the big, gesture-free surface. |
| **B (chosen)**: session overlay over the existing map, holding a camera/overlay lease | Zero new renderer; the map is already the largest surface and the one the user will drive in; `SearchDialog`'s width branch gives the wide-layout docking for free. |
| **C**: own map on wide screens, main map on phone | Two behaviors to keep in parity (UI.md §1) for no benefit; keeps the renderer cost on exactly the surfaces where the main map is already big. |

### D2 — The session is a lease on top of the three map modes, not a fourth mode

`map-modes` fixes exactly three states (BROWSE / FREE_DRIVE / NAVIGATION). The session therefore:
suspends the FREE_DRIVE preset when opened from FREE_DRIVE (the same suspension a manual interaction causes),
and owns the camera + route overlay while active. `MapMode` stays derived from navigation/follow; the lease is
an orthogonal flag.

*Alternatives*: a fourth `MapMode.ROUTE_PLANNING` (forces a `map-modes` rewrite and touches every preset
consumer); the current approach of force-disabling follow mode (leaves the map in a state the user did not
choose — no re-center affordance afterwards).

### D3 — Session state and its terminal transitions live in the route state owner

The existing `RoutePanelViewModel` becomes the session's state owner (it already holds start/destination/
vehicle/route/steps and the favourite/search helpers); it gains an explicit session state
(`EDITING`, `CALCULATING`, `REVIEWING`, `ANALYSING`, `STOPPED`, `ENDED`) and the terminal transitions:
`START_NAVIGATION` and `END` (cancel). `END` performs one thing: clear the route from the map, reset state,
release the lease. `MapCanvasScreen` keeps composing either the session overlay, the summary dialog, or
neither — still a state flag, no nav-graph change.

*Alternatives*: a new `RouteSessionController` (a second owner of route state, which the panel already
holds — a split state machine); a nav destination for the session (breaks the "overlay composes on top of
the map" pattern in `Design.md` and hides the map behind a route change).

### D4 — Grace period is a coroutine deadline, not persisted state

Stopping navigation starts one deadline in the session's `viewModelScope` (main dispatcher for the state
write). `Restart` cancels it; expiry ends the session. Nothing is persisted, so process death cannot
resurrect a stopped session (spec: `route-planning-session` — grace state does not survive process death).
Expiry while the app is backgrounded ends the session without any UI work.

*Alternatives*: `WorkManager`/alarm (overkill, and would still have to be suppressed after process death);
"end on next user action" (the route would linger indefinitely — the behaviour this change removes).

### D5 — Per-instruction positions are added as index-aligned arrays beside `descriptions[]`

The native delta stays minimal and upstreamable (submodule patch, `naviveylin-local` + gitlink bump):

```
CollectCallback / DescCallback::BeforeNode(node)   -> stage node.GetLocation()
every place a description line is pushed            -> push the staged position into the same vector
RouteEntry: + double[] instructionLats, instructionLons     (index-aligned with descriptions[])
```

Both `calculateRoute` JNI paths build these arrays (`OSMScoutClient.cpp` ~6294 and ~6903), so both get the
same addition. Staging in `BeforeNode` and appending where the line is appended is what keeps the alignment
exact: a node that produces no description line must not produce a position either.

*Alternatives*: **structured model** — expose the built `JavaRouteInstruction[]` (it already carries
turn type, distances and next-next hints) for a calculated route and rebuild the planning step list on it,
which would delete the `"[1.2 km, 5 min]"` string parser in `RoutePanelViewModel`; rejected **for this
change** because it needs a new native entry point plus a `RouteStepDisplay`-wide rework, and the specs do
not require it. It is the natural follow-up change (recorded in Open Questions).
**node index**: `RouteDescription::Node` exposes no polyline index, and the polyline is a densified point
list, so an index would have to be derived natively anyway.

### D6 — Step → polyline range is a pure Kotlin function, monotonic nearest-vertex

`stepSegments(polyline, anchors) -> IntRange per step`: for instruction `i` find the nearest polyline vertex
at or after the vertex chosen for instruction `i-1`. Instructions arrive in route order, so the constraint
prevents a match jumping to a parallel leg of a self-crossing route; the segment for step `i` is
`vertex(i) .. vertex(i+1)`, and the last step ends at the final vertex. Degenerate inputs (empty polyline,
single instruction, duplicate anchors) return an empty range for the affected step instead of throwing.

*Alternatives*: **unconstrained nearest vertex** (wrong on loops); **distance-based matching** (cumulative
instruction `distanceTo` vs cumulative polyline geodesic distance) — more robust in principle, but depends on
Kotlin and libosmscout agreeing on the distance model; kept as a fallback if on-device analysis shows a
mismatch. **native index**: see D5.

### D7 — The analysed segment is drawn as a Compose overlay layer above the frame bitmap

New overlay composable alongside `LocationMarkerOverlay`, drawing the step's polyline range with
`ProjectionUtils.geoToScreen` against the emitted frame's viewport. Draw order: frame bitmap → analysed
segment → markers/pins, so pins never hide. Because it sits above the composited bitmap, it works on the
tile path and the full-render path without touching either.

*Alternatives*: **native renderer support** (new overlay input): every tile render would redraw it, the
colour would have to be resolved natively per presentation, and the highlight would be baked into cached
tiles — a stale-highlight class of bug. **Blending the highlight into the bitmap in Kotlin** (mutating the
frame): forces a copy per frame and invalidates `RenderBitmapPool` reuse.

### D8 — Overlay anchors are a fixed two-state bottom card, the hidden anchor is a pill

Phone: the overlay is a **plain `Surface` card with two fixed heights** plus a **hidden** state (whole map
free, only the route-ready pill). **MAX** (at most 45 % of the screen height) carries the location fields,
the route's step list — scrolling *inside* the card — and the session actions, so the map always keeps at
least 55 % for the route; **MIN** hugs its content (at most 18 %, capped at one control row) and
carries only the analysed step:
its position, its name (the way back to MAX) and the two step controls. With no route the card stays in MAX,
because there is nothing to minimise to. The card reports the height it has to `MapCanvasViewModel` for the
overview fit. Wide layout: the same content composed in a docked panel (width branch) with the selectable
list; the card is not used there.

*Why the two states are the list and the step*: the owner's directive (2026-10-03) is that the route's list
is what belongs at the bottom, and that a selection should hand the map back — so selecting a row analyses
that step and collapses to MIN, while the step name in MIN brings MAX back. That also removed the reason the
first revision needed a pinned navigator *and* a pinned action band in one card: MAX has the list and the
actions, MIN has neither.

*Why not the Material3 sheet* (the first implementation; 2026-10-03 device findings): `ModalBottomSheet`'s
expansion is content-driven. The same route panel measured **36 %** of the screen height in the edit state
and **48 %** with a route at what the session called "compact", and ~**97 %** expanded. So the map was
never reliably free, the nominal covered fraction the fit used (0.22 × 2400 px = 528 px) was off by
**614 px** — the fit log said `coveredPx=528` while the card's top edge sat at `y=1258`, i.e. 1142 px — and
the expanded state hid the map completely. Fixed heights make the free area a property of the design
instead of a measurement of the content.

*Alternatives*: keeping the sheet and measuring its height (works, but the map still has no guaranteed
share and the "two anchors" are not two states); a custom `AnchoredDraggable` three-anchor overlay (more
code for strictly less determinism).

*What a fixed height costs* (device findings, 2026-10-03): a card that cannot grow has to spend its height
carefully. The vehicle selector is a pre-calculation control, so it gives its room to the step list as soon
as a route exists (the list has to start on screen); in MAX the session's actions are pinned in their own
band at the bottom edge with the scrolling content above them, and the two actions of a state share **one
row**; the clear action keeps its own row only in the docked layout, while the session's **explicit End action** takes a line of its own in the phone card (three buttons in one row clip their labels at phone width, so the band is 120 dp) and is repeated in the min strip, which has no header — leaving the analysis must not be reachable only by starting navigation (owner finding, 2026-10-03). Two bugs came out of the same rounds:
`BoxWithConstraints` without `fillMaxSize()` sizes itself to its content, so `align(BottomCenter)` aligned
the card inside its own box and the card rendered at the **top** of the screen while the fit used the height
it reported (hence the `routePanelCard` placement test); and nesting the list's own `verticalScroll` inside
the card's scroll made Compose measure a scrollable with infinite height — the list is rendered
`scrollable = false` in both card and docked panel, which scroll as a whole.

The card's height and the analysed step share one trap: because min hugs its content, the card grows
*after* a step is analysed (a two-line instruction), and any height reaction that runs afterwards moves the
map under the user. Splitting the two reactions settled it — the **overview** fit is driven by the reported
height and is re-applied on growth, while a **standing manoeuvre focus** re-places only: the focused
segment's midpoint goes to the centre of the new free band at the *same* magnification, and the zoom widens
only when the segment genuinely no longer fits (`refitManoeuvreFocus`). Re-fitting the route instead was the
"sudden zoom away from the visible step", ignoring the growth (the first fix) was the "segment still cut
off"; both were the same missing distinction (owner findings, 2026-10-03).

### D9 — Dismiss ordering follows the existing topmost-overlay rule

`map-canvas-screen` already requires back to dismiss the topmost overlay. Order: session overlay →
(nothing, app back behaviour unchanged). The session's own dismiss is a cancel; when a route exists only
inside a navigation session, ending the review does not touch the route (spec). There is exactly **one**
dismissible session surface on the phone: the summary dialog is gone (D10), so the mutual-exclusion rule
between overlay and dialog has nothing left to order.

### D10 — One session surface: the dialog is deleted, the list is the card's max state

"Show Route" opened a second surface for the same list, and the device run found it capped at 400 dp (so not
the "full-screen dialog" its own spec claimed), not reaching the screen bottom (`navigationBarsPadding`
applied to the `Surface` instead of to its content) and with rows that had **no click handler at all**
(`onStepSelected` was only passed on the panel's path). So the dialog is deleted outright and the phone has
exactly one surface. The first revision of the card then had no list on the phone at all (a pinned navigator
only); the owner's directive replaced that with the MAX/MIN split: the list *is* the max state's content,
selecting a row analyses it and collapses the card to MIN, and MIN's step name returns to the list.

*Alternatives*: fixing the dialog instead (full height, clickable rows) — keeps two surfaces with two
behaviours for one list, which is what produced the dead list; keeping the first revision's navigator-only
card — it worked, but the owner wants the list at the bottom, and a 19-step route cannot be reviewed
step-by-step by tapping a control 19 times.

## Threading and lifecycle

| Component | Thread / dispatcher | Lifecycle |
|---|---|---|
| Session state machine (`RoutePanelViewModel`) | main (viewModelScope) | one instance per map screen; state reset on `END` |
| Grace deadline | main, `delay` in viewModelScope, one `Job` | cancelled on Restart, on End, on screen destroy |
| Camera lease + session-owned fit (`MapCanvasViewModel`) | main | released when the session state stops being active |
| Step → polyline mapping (pure) | caller's thread, no allocation per frame | pure function, unit-tested |
| Segment highlight drawing (`Canvas`) | composition/UI | driven by selected-step state only |
| Native instruction positions | existing route-calculation JNI thread | written into the `RouteEntry` already crossing JNI |

## Risks / Trade-offs

- **Alignment drift in the native arrays** (a line pushed without a position) → the Kotlin mapping treats a
  shorter anchor array as "no position for the trailing steps" and draws no highlight rather than shifting
  every segment; a JNI-level assertion/count test pins `instructionLats.length == descriptions.length`.
- **Monotonic matching picks a wrong vertex on a folded route** (spur that returns near the previous leg) →
  the monotonic constraint makes a wrong match local, not global; on-device check with a route containing a
  U-turn; fallback is the distance-based matcher (D6).
- **Grace timer firing at an awkward moment** (user reopens the map exactly at expiry) → expiry is
  idempotent and only acts when the session is still in `STOPPED`; the state transition is the same cancel
  path as an explicit End.
- **Wide-layout parity drift** (docked panel diverging from the phone anchors) → one content composable, two
  placements; a test renders both placements and asserts the same actions are present.
- **A wrong covered height moves the route behind the card** → the card reports its fixed height, the fit
  uses exactly that number, and it re-runs when the height changes while a route is reviewed; the
  revert-check replaces the reported height with a nominal fraction and the "whole route lies above the
  card" case must fail.
- **Rejecting the second renderer keeps the map "shared"**, so a future feature that wants an independent
  map (e.g. an elevation profile view) will still need one → accepted; `MiniMap` (spec `mini-map`) remains
  the pattern for small independent views.

## Migration / rollback

No persistence or data-format change, so there is no data migration. Order of work: native positions →
pure mapping → highlight layer → session state machine + lease → overlay anchors/docking → spec/guideline
updates. Rollback is the reverse: the session is a state flag plus one state machine, so reverting the
overlay restores the sheet behaviour; the native arrays are additive and stay harmless if unused. The
spec-level rollback restores `route-panel-ui`/`route-map-overview`/`route-summary-dialog` to their current
text.

## Verification

- **Unit**: session state machine (every exit, grace expiry/restart/end, "no route after end"); step→polyline
  mapping (straight, folded, empty, single-step, duplicate-anchor); highlight geometry; lease behaviour in
  `MapCanvasViewModel` (fit only while the session owns the camera, no follow-tick move during a session);
  the step navigator (forward/back move and analyse, no-op at the ends, indicator matches the analysed
  step, survives a card state change); the fit's reported height (re-fit on height change, an unchanged
  height leaves a user-moved viewport alone); parity test for the phone card vs docked panel content.
- **Host JVM limit**: the JNI stub ships no symbols, so the native position extraction is verified
  on-device, not on the host (same limitation recorded for the instruction-distance fix).
- **On-device (phone + Automotive AVD for the phone surface)**: plan a route, collapse/expand the card at
  all three anchors (the whole route must stay above the card in both card states), move the step navigator
  through several steps (camera + highlight, dark and daylight), start navigation, stop and let the grace
  expire, check via `adb logcat -s NaviVeylin` and the `DiagnosticsLog` viewer that no route survives its
  session and that no coordinates appear in diagnostics.
- **Revert-checks** (one per new invariant): grace never firing → "no route outlives its session" must fail;
  the reported card height replaced by a nominal fraction → the "whole route lies above the card" case must
  fail; the navigator's forward control not analysing the next step → "next moves one step" must fail; the
  monotonic constraint removed → the folded-route case must fail; the alignment staging removed → the
  array-length test must fail.
- **Build**: all three ABIs (the native delta is C++), both flavors, plus the buildSrc tests via `build-app`.

## Open Questions

- Grace duration: default 45 s (single named constant); tunable after the on-device run without touching specs.
- Docked panel side (leading vs trailing) — a layout detail; the spec only requires docking.
- Whether the planning step list should later move onto the structured `JavaRouteInstruction` model (D5
  alternative) — a follow-up change, not needed for these specs.
