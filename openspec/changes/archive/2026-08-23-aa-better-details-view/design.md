# aa-better-details-view — Design

## Context

See proposal.md — Why. Current state: `DetailsScreen.kt` (`:auto`) renders a `PaneTemplate` with header title "Location", a title row (address + coordinates), bare region/postal rows (no label), labeled description rows, and pane actions "Navigate here" (primary) + "Clear" (map-tap flow only). The phone variant (`LocationDetailsDialog`) shows a mini map, a name→address→label title, and a "Show on map" action. `MapScreen.kt` already renders libosmscout maps via `AutoMapRenderer` + `SurfaceCallback` (register on start, release on stop, unregister on destroy, surface-failure recovery via `invalidate`). Car-app 1.7.0 (Car API 7) supports `PaneTemplate` as `MapWithContentTemplate` content and up to 2 pane actions; `MapWithContentTemplate` also supports an `ActionStrip` (up to 4 actions).

## Goals / Non-Goals

**Goals:**
- Details screen shows a map preview (destination marker) with the pane overlaid
- Every attribute row labeled; title derived from destination identity
- Second action "Show" (browse map centered on destination); "Navigate here" stays primary
- Reuse existing `AutoMapRenderer`/surface plumbing; no new dependencies

**Non-Goals:**
- Interactive gestures on the details map preview (static preview; "Show" leads to the full interactive browse map)
- Favorites management in the auto details screen (phone-only, separate `auto-favorites` spec)
- Changing the candidate-picker flow or deep-link behavior
- Phone app changes

## Decisions

### 1. Template: `MapWithContentTemplate` with `PaneTemplate` content
`DetailsScreen.onGetTemplate()` returns `MapWithContentTemplate` built via `MapTemplateFactory.buildTemplate(mapController, paneTemplate)` (existing helper). The pane carries the header (title = destination name, `Action.BACK` start action), the labeled rows, and the two actions. The map surface shows the destination with a marker.

**Alternatives considered:** keep `PaneTemplate` (no map) — rejected, the user explicitly wants a map preview like the phone. `MapTemplate` with a separate details screen — rejected, no content slot and no pane. `NavigationTemplate` — rejected, reserved for active navigation.

### 2. Map preview: own `AutoMapRenderer` instance, static (no gestures)
`DetailsScreen` registers its own `SurfaceCallback` (mirroring `MapScreen`'s lifecycle pattern: register on start, release on stop, unregister on destroy, capped `invalidate` recovery for dead surfaces) and owns an `AutoMapRenderer` centered on `(lat, lon)` at the caller's `mag` (default `DEFAULT_MAGNIFICATION` = 15). On surface available: `client.setMapDpi(surfaceDpi)` + `renderer.updateProjectionDpi(surfaceDpi)` (same as `MapScreen`), then `renderer.setDestinationMarker(lat, lon, name)`. No gesture callbacks — the preview is static; the destination marker is the only overlay.

**Alternatives considered:** extract a shared map-surface host class from `MapScreen` — rejected (large refactor of a working, quirk-laden screen; the surface quirks are screen-specific). Interactive preview (pan/zoom) — rejected (driver-safety + complexity; "Show" provides the full interactive map). Reuse `MapScreen`'s renderer instance — rejected (screens are pushed/popped; each screen owns its surface lifecycle).

### 3. "Show" action: `popToRoot` + push `MapScreen` with `initialCenter`
Add optional `initialCenter: Pair<Double, Double>? = null` to `MapScreen`; when set, `computeInitialViewport()` returns it (at `DEFAULT_AA_ZOOM`) instead of the saved viewport / map bbox. "Show" handler: `screenManager.popToRoot()` then `screenManager.push(MapScreen(carContext, navigationViewModel, initialCenter = lat to lon))`. Works uniformly from all three entry points (search, POI, map tap).

**Alternatives considered:** callback into the existing `MapScreen` instance to re-center — rejected (cross-screen state channel, fragile; the map-tap `MapScreen` sits under the details screen and its viewport is private). Push a second `MapScreen` without popping — rejected (stack grows; back would return to the stale details screen).

### 4. Remove the "Clear" action
Pane allows at most 2 actions; "Navigate here" + "Show" fill the budget. `MapScreen`'s selection state (`selectionLat/Lon`, `hasSelection`) is write-only — set in `onLocationSelected`, never read — so `clearSelection()`, the `showClear`/`onClear` constructor params, and the "Clear" action are vestigial. Remove them; back from the details screen returns to `MapScreen` (spec: "Back from details screen does not navigate").

**Alternatives considered:** keep "Clear" for the map-tap flow and drop "Show" there — rejected (inconsistent UX; "Clear" has no observable effect today). Keep all three — rejected (exceeds the 2-action pane limit).

### 5. Title: name → address → label → "Location"
Header title resolves from the description's `General/Name` entry (same lookup as the phone), else the resolved address (street + house number, else admin region), else `nameHint`, else "Location". Title updates on `invalidate()` when the async address/description arrive (same pattern as the rows today).

**Alternatives considered:** keep "Location" — rejected (the user's complaint; the phone derives the title from the destination identity).

### 6. Labeled rows with priority order (4-row pane cap)
`buildDetailsRows` returns rows in priority order, each with label as title and value as text:
1. "Coordinates" — always: `lat, lon` (5 decimals)
2. "Address" — street + house number when either is present
3. "Area" — admin region, else postal area, when present
4. Description entries (label/value) fill remaining slots up to the 4-row cap

**Alternatives considered:** keep the current structure (bare region rows) — rejected (inconsistent labels). Description entries before coordinates/address — rejected (coordinates + address are the primary identity; description is enrichment).

### 7. Actions: pane-level, "Navigate here" primary + "Show" secondary
`buildDetailsPane` gains a `showAction` parameter; actions are `Navigate here` (`FLAG_PRIMARY`) + `Show`. If a host fails to render pane actions inside `MapWithContentTemplate` content, fall back to `MapWithContentTemplate.setActionStrip` (up to 4 actions) — verified on the AAOS emulator before landing.

**Alternatives considered:** row actions — rejected (pane rows are non-actionable on car hosts, documented in the original change). Action strip only — rejected (pane actions are the documented pattern; strip is the fallback).

## Risks / Trade-offs

- [Pane actions not rendered in `MapWithContentTemplate` content on some hosts] → Fallback to `setActionStrip` (decision 7); verify on AAOS emulator.
- [Surface lifecycle quirks (host locks re-delivered surfaces)] → Mirror `MapScreen`'s proven pattern (release on stop, capped invalidate recovery).
- [Map preview zoom wrong for search/POI flows] → `mag` defaults to 15 (existing `DEFAULT_MAGNIFICATION`); map-tap passes the viewport zoom.
- [4-row cap hides description entries] → Priority order (decision 6) keeps the most useful attributes; description fills remaining slots.
- [Title flicker when address/description arrive async] → Same invalidate pattern as today; coordinates shown immediately.

## Migration Plan

Additive within `:auto`. Land in one change: `MapScreen.initialCenter` param (compile-safe), then `DetailsScreen` template/rows/actions/title rework, then entry-point call-site updates (drop `showClear`/`onClear`), then test updates. Rollback = revert; phone app untouched.
