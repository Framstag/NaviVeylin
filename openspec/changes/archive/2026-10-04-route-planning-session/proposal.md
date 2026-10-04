# Proposal

## Why

A calculated route cannot be analysed on the phone: the route panel is a `ModalBottomSheet` over the
live map, so the map is largely covered, and the step list is passive — `RouteStepDisplay`
(`ui/route/RoutePanelViewModel.kt:48`) carries text, distance, time and turn type only, with no
position, and the polyline is a densified point list from `TransformRouteDataToPoints` with no link
to the instruction nodes. Tapping a step therefore cannot show anything on the map.

The panel also has no defined end: `route-panel-ui` says dismissing it leaves the polyline and markers
on the map indefinitely, so a route can outlive the intent that created it. And because one camera
serves both the map and the panel, the panel has to report its covered height so the overview can fit
the visible area (`route-map-overview`, Decision 8) — a cross-surface measurement with a settle delay
and two guards, which exists only because planning and browsing share a viewport. `openRoutePanelWithStart`
even disables follow mode so that fit cannot be suppressed.

## What Changes

- Route planning becomes a **session** on the phone map: an overlay with a defined lifecycle that ends
  in exactly one of two ways — **Start Navigation** or **Cancel/End** — plus a short grace period when
  navigation is stopped (Restart / End now). A route never stays on the map after its session ends.
- The session **owns the camera and the route overlay while it is active** (it is a camera lease on top
  of BROWSE/FREE_DRIVE/NAVIGATION — no fourth map mode). Fitting the route overview becomes a session
  action at a known anchor state, so the covered-height measurement, its settle delay and both of its
  guards go away.
- The planning overlay is **collapsible with three anchors** (expanded / compact / hidden) on the phone,
  and **docks to the side** when width allows (tablet, foldable, landscape), so the same overlay gives
  the map room for analysis on every form factor. `SearchDialog` already uses this `BoxWithConstraints`
  branch pattern.
- **Route analysis**: tapping a step in the list focuses the camera on that manoeuvre and highlights the
  polyline segment belonging to it. Step positions are added on the native side (submodule patch) and a
  pure Kotlin mapping turns the instruction anchors plus the polyline into per-step polyline ranges.
- The segment highlight is drawn as an app-side Compose overlay above the map bitmap along the existing
  `ProjectionUtils.geoToScreen` path — the native renderer keeps drawing the plain route polyline.
- `RouteSummaryDialog` stops being a planning surface: the step list is already inline in the session
  overlay, so the full-screen overlay keeps only its navigation role.
- **BREAKING (spec-level, phone only)**: `route-panel-ui`'s "dismissing SHALL NOT clear the route" and its
  "the route panel SHALL be a modal bottom sheet" requirement are replaced by session semantics.
  User-visible behaviour changes: dismissing/cancelling a plan now removes the route from the map, and
  the planning UI is no longer a drag-dismissable bottom sheet.

Not in scope: Android Auto / AAOS (the car has its own destination picker and guidance surfaces),
route calculation itself, vehicle profiles, and reroute behaviour during navigation.

## Capabilities

### New Capabilities

- `route-planning-session`: the planning session on the phone map surface — its states
  (editing → calculating → reviewing ↔ analysing → stopped-grace), its only exits (Start Navigation,
  Cancel/End), the stop-navigation grace with Restart/End, the collapsible overlay anchors and the
  width-based docking, and the camera/overlay lease the session holds while active.
- `route-analysis`: inspecting a calculated route on the map — the step list as a selectable list,
  selecting a step focusing the camera on its manoeuvre, highlighting that step's polyline segment, and
  clearing the selection.

### Modified Capabilities

- `route-panel-ui`: the panel is no longer a modal bottom sheet and no longer dismiss-preserves the
  route; its start/destination/vehicle/calculate/clear contract stays, its dismiss and Start/Stop
  Navigation requirements move to session semantics.
- `route-map-overview`: the route overview fit is performed by the session at its own anchor state; the
  "canvas minus the area covered by the open route panel" visible-area rule and both fit guards are
  removed, and the fit no longer needs a settle delay.
- `route-summary-dialog`: its planning role (shown after calculation, dismiss returns to the route panel)
  is removed; it remains the navigation-time summary/highlight surface.
- `routing-summary`: the `RouteSummary` composable gains the analysed-step selection (a selectable step
  row and the selected-step highlight) in addition to the current navigation-step highlight.
- `map-modes`: an active planning session suspends the FREE_DRIVE preset (as a manual interaction does)
  and holds the camera; ending the session leaves the existing re-center affordance to restore it.

## Impact

**Specs** (previous specifications changed): `openspec/specs/route-panel-ui/spec.md`,
`route-map-overview/spec.md`, `route-summary-dialog/spec.md`, `routing-summary/spec.md`,
`map-modes/spec.md`. New: `openspec/specs/route-planning-session/spec.md`,
`openspec/specs/route-analysis/spec.md`.

**Phone/tablet UI (`:app`)**:
- `ui/route/RoutePanel.kt` — becomes the session overlay (anchors, docking, selection-aware step list)
- `ui/route/RoutePanelViewModel.kt` — session state, terminal transitions, grace, selected step
- `ui/route/RouteSummary.kt` — step rows become selectable, selected-step highlight
- `ui/route/RouteSummaryDialog.kt` — planning role removed
- `ui/map/MapCanvasScreen.kt` — overlay composition, back handling, docking branch
- `ui/map/MapCanvasViewModel.kt` — camera lease, session-owned fit; covered-height plumbing removed
  (`sheetCoveredHeightPx`, `routePanelCoveredHeightPx`, `scheduleRouteOverviewFit`'s settle delay/guards)
- `ui/map/MapRenderer.kt` — unchanged native route drawing; the step highlight is a Compose overlay layer
  next to `ui/map/LocationMarkerOverlay.kt` (draw-order decision in design)
- new pure helper (`:core` or `ui/route`) — instruction anchors + polyline → per-step polyline ranges
- new overlay composable for the highlighted segment

**New/changed Android components**: no new manifest entry, no new permission, no nav-graph change — the
session composes over the map screen via a state flag, as `FavoritesSheet` and `SearchDialog` already do.
`MainActivity` remains the single activity; back handling follows `map-canvas-screen` ("dismiss the
topmost open overlay").

**Native / JNI**: **submodule patch, minimal and upstreamable** — `libosmscout-client-java`'s
`OSMScoutClient.cpp` (`CollectCallback::BeforeNode` already holds `node.GetLocation()` and
`node.GetDistance()`) carries the instruction position into the Java route result, and the matching field
is added to `RouteEntry.java` / `RouteInstruction.java`. Those Java files are **not** among the five files
that `:osmscout-client-java` overrides, so no bridge-module override is needed; the submodule SHA is
bumped in the same change. If the design decides the navigation instruction model is the better home, the
`:osmscout-client-java` override list is unaffected either way.

**Guidelines**: `guidelines/UI.md` (§6a search surface pattern for the docking branch, §7 map modes for
the lease), `guidelines/Design.md` (overlay composition, state ownership, threading of the grace timer),
`guidelines/MapRendering.md` (route overlay + Compose overlay draw order).

**Nature and rollback**: spec-level breaking for the phone planning surface, additive for analysis
(segment highlight is new). Rollback path: the session is one state flag plus one controller; reverting to
the sheet restores `route-panel-ui`/`route-map-overview`'s previous text, and the native field addition is
additive and can stay unused. No persistence or data-format change, so no migration is involved.
