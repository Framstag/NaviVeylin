# Route Overview Fit — Design

## Context

See proposal.md — Why. The phone draws a calculated route via the route-result collector in `MapCanvasViewModel.setRoutePanelViewModel` (`mapRenderer.setRoute(...)` + `renderMap()`), but never touches the camera. The screen feeds canvas pixels through `setScreenSize(width, height)` into `screenWidth`/`screenHeight`, and a bbox→magnification helper already exists (`computeAreaZoom(bbox, vpWidth, vpHeight)`, used for area favorites, clamped 14–20). Specs: `route-map-overview` (this change), `reroute-route-visibility` (route drawn/visible behavior, unchanged).

## Goals / Non-Goals

**Goals:**
- Fit camera to route bounding box exactly once per freshly calculated route, in the one place all route draws funnel through.
- Suppress the fit while navigating or following so the driver's viewport is never yanked.
- Reuse existing zoom math; no new dependencies.

**Non-Goals:**
- Changing route drawing/visibility semantics (`setRoute`, markers, `clearRoute`, visibility toggling) — untouched.
- Changing navigation-start follow behavior (GPS centering, mag-15.0 initial) — untouched.
- Android Auto / Automotive OS overview — separate rendering path (`AANavigationController`), out of scope.

## Decisions

### 1. Fit inside the route-draw collector (not the calc event)
Fit runs in `setRoutePanelViewModel`'s `combine(routeResultFlow, routeVisible)` collector, right after `mapRenderer.setRoute(...)`. Every route-draw path (initial calc, visibility re-show, reroute result swap) passes through here, so one hook covers all.
- *Alternative: fire from `routeCalculatedEvent` in `RoutePanelViewModel`.* Rejected: that ViewModel has no canvas size (would need cross-VM plumbing) and misses the visibility-toggle path; the reroute consumer in `NavigationViewModel` already uses the event for auto-restart and must not be conflated with camera work.

### 2. One-shot fit per result (stale-result guard)
Track `lastFittedResult: RouteResult?` in the ViewModel. Mark it at result arrival (so re-emissions never reschedule); on `clearRouteSignal` reset it to `null` so an identical re-calc can refit. The actual camera work then runs after a short settle delay (Decision 8). This satisfies the "stale re-emission must not move a user-moved viewport" scenario.
- *Alternative: fit on every collector emission.* Rejected: screen re-entry re-subscribes the collector and StateFlow re-emits the stored pair — would re-yank the camera away from a user-panned viewport (explicit spec scenario).

### 3. Reuse `computeAreaZoom` with `minZoom` and `dpi` parameters
Add `minZoom: Double = MIN_AREA_ZOOM` and `dpi: Double = ProjectionUtils.REFERENCE_DPI` to `computeAreaZoom`; the route fit passes `MIN_MAG = 4.0` as floor and the phone's display DPI. Every other caller (area favorites, POI/radius search fit) passes its display DPI too, because the helper's zoom is only meaningful against the renderer's ground resolution.
- *Alternative: separate route-fit zoom function.* Rejected: duplicates the meters→magnification formula in two places that would drift.
- *Ground-resolution correction (found during apply, approved as part of this change):* the formula computed `metersPerPixel = circumference / (256 * 2^mag)` — an equator-referenced, 96-dpi value — while the renderer draws at the display DPI (`client.setMapDpi(density)`, `ProjectionUtils.computeScale` scales by `REFERENCE_DPI / dpi`, `MapRenderer.tileSizePx = 256 * dpi / 96`; the car-side fit in `DetailsScreen.fitZoom` already divides by the surface DPI). Mercator ground distances additionally shrink by `cos(lat)` relative to the equator, which the formula only applied to the longitude span. Corrected form: `metersPerPixel = circumference / (256 * 2^mag) * (REFERENCE_DPI / dpi) * cos(lat)`, solved for the magnification that fits `maxMeters` into `fitSizePx`. Fits previously over-zoomed by `dpi / 96 * (1 / cos(lat))` — ~1.1 level at 48° north on a 160-dpi display, ~2.3 levels on a 420-dpi phone — so a "fitted" route overflowed the viewport. Defaults keep the 96-dpi behavior for pure math callers/tests.
- Why floor 4.0: `MIN_MAG` is the render-stability clamp (z < 4 stalls native rendering); a 500 km trip needs ≈ mag 11, a 1000 km trip ≈ 10 — 14 would overflow the viewport for any trip past ~30 km. Compute is rounded to whole levels (matches favorites behavior, avoids fractional-zoom stutter); the route fit additionally verifies the projected bbox (Decision 8), so rounding or rotation can never clip it.

### 4. Suppression conditions: navigating OR follow mode
Skip fit when `_navigationViewModel?.state?.value?.isNavigating == true` or `_uiState.value.followMode == true`.
- *Alternative: only `isNavigating`.* Rejected: a route calculated while free-driving (follow on, no route session) would snap the camera away from the car.
- Restart path detail: `startNavigation` calls `showRouteOnMap()` then sets `isNavigating = true` synchronously on the same dispatcher before the collector runs, so the flash-on-restart case is already excluded by the `isNavigating` guard.
- With Decision 7 in place the guard only ever protects reroute/restart and non-selection flows: every destination-selection path (favorite select, route-panel open, search, POI, contacts) disables follow mode first, so a deliberate calculation always fits.

### 5. Degenerate geometry + unknown canvas
- Build the bbox from the polyline plus start/dest, skipping NaN.
- No usable coordinates → leave the viewport untouched (spec: "Missing coordinates").
- Only endpoints (empty polyline) → center on endpoint midpoint, `NODE_ZOOM` (computeAreaZoom's existing degenerate fallback).
- `screenWidth <= 0 || screenHeight <= 0` (canvas not laid out) → skip fit entirely; layout precedes route calculation in practice and a wrong zoom is worse than none.

### 6. Threading
All work in the existing `viewModelScope` collector on the main dispatcher — same thread as every other viewport mutation (`updateCenter`, `updateMagnification`, `renderMap`). The bbox scan is a single O(n) pass over the polyline (≤ a few thousand points); no background thread needed. The settle delay (Decision 8) is a `delay()` in the same collector job; any pending fit is cancelled if a newer result arrives.

### 7. Destination selection disables follow mode
`onFavoriteSelected` and `openRoutePanelWithStart` SHALL set `followMode = false`, mirroring `onSearchResultSelected`, `onPoiEntryClick`, and `onAddressBookResultSelected` (which already disengage follow when a destination is picked). The favorites path is the one selector that does not — a bug found on-device: picking a favorite while free-driving left follow on, so the R2 guard suppressed the fit entirely. With this change the guard semantics stay as designed (Decision 4); selection-driven calculations always fit.
- *Alternative: relax the guard to `isNavigating`-only.* Rejected for this change: the R2 spec deliberately protects the free-drive viewport, and the selector-side fix covers the real failure without weakening the guard.
- Note: `disengageFollowMode()` is not used here — the selector paths set `followMode = false` directly (their existing pattern) rather than entering drive suspension.

### 8. Sheet-aware fit with settle-delay one-shot
When the route panel is open, the bottom of the canvas is covered by the `ModalBottomSheet`. Fitting to the full canvas hides the route's lower part behind the sheet (observed on-device: short route ≈ canvas center ≈ behind the panel). The fit therefore consumes the panel's **covered height** and fits within the visible map area:
- `RoutePanel` reports its current covered height in px into `MapCanvasViewModel` (`RoutePanelViewModel.sheetCoveredHeightPx`, a small `StateFlow<Int>`), measured from the Material3 sheet offset — `SheetState.offset` is internal in Material3 1.3.1, so the public `requireOffset()` is used. Anchors are `Hidden = canvasHeight` and `Expanded = canvasHeight - sheetHeight` (the sheet window is the same size as the edge-to-edge map canvas, passed in as `canvasHeightPx`), so `covered = canvasHeight - offset`, valid during partial drags.
- `fitViewportToRoute` uses `effectiveHeight = canvasHeight - coveredPx` for the magnification and moves the camera so the bbox midpoint lands on the visible-area center: the camera center is drawn at the canvas center, so it must sit `coveredPx / 2` px below the midpoint on screen (the content shifts up, clear of the panel), converted with `ProjectionUtils` at the display DPI and the viewport angle (exact, rotation included). `coveredPx == 0` (panel closed) keeps today's behavior; `coveredPx >= canvasHeight` skips the fit (nothing visible).
- **Fit verification**: the fitted magnification is verified by projecting the bbox corners into the visible band (`routeFitsVisibleArea`) and stepping one level out while the route would not fully fit — whole-level rounding can round the exact fit down by up to half a level, and a rotated view needs a larger screen hull than the north-up bbox. Start, target, and polyline therefore stay visible.
- **Settle delay**: the collector marks `lastFittedResult` and schedules the fit ~150 ms later (cancellable job). The result lands while the panel is still growing from Calculating→Done content, so fitting synchronously would measure the wrong sheet height (Decision 2). Before applying, the fit re-checks: guard conditions still pass, and the viewport snapshot at arrival is unchanged (user hasn't gestured during the window).
- The one-shot/stale semantics (Decision 2) are unchanged: no refit on re-emission, sheet height changes after the fit do not refit (the overview is shown once; the user knowingly shrinks the map by raising the sheet).

## Risks / Trade-offs

- [Dateline-crossing route yields lon span ≈ 360°] → fit clamps to mag 4 (whole-world view). Accepted: navigation routes are near the start/target; same limitation exists in `computeAreaZoom` today via favorites.
- [DPI / `cos(lat)` correction changes existing fit zooms (area favorites, POI search)] → Deliberate: those fits were over-zoomed by the same factor, so they showed less area than intended; the corrected value is what the renderer actually draws. Covered by the full unit suite (favorites/search/zoom tests stay green) plus the new fit tests, which assert the projected bbox.
- [Fit vs follow-mode GPS centering race at nav start] → `isNavigating`/`followMode` guards run before any fit; no race in practice (main-dispatcher ordering, see Decision 4).
- [Canvas size unknown at calc time] → fit skipped; worst case the old behavior (current viewport) persists for one render, no crash.
- [Polyline re-emission after clear re-fits identical route] → `lastFittedResult` reset on `clearRouteSignal`; matches "user cleared, recalculated same route → overview again" expectation.
- [Sheet height measured mid-animation (Calculating→Done growth) or mid-user-drag] → settle delay waits for layout settle; the 150 ms window is sized for recomposition, not for long user drags — worst case the overview uses a slightly stale inset, still better than the full-canvas fit. No refit after the fact (Decision 8).
- [Rotated map + covered-height center shift] → screen→geographic conversion uses `ProjectionUtils` (display DPI, current angle), and the fit verification projects the rotated bbox hull, so a rotated overview is checked exactly instead of approximated.
- [User gestures during the settle window] → viewport-snapshot check before applying the fit: any manual center/magnification change since arrival cancels the pending fit, preserving "stale re-emission must not move a user-moved viewport" even mid-window.
- [Harness gap: fit tests never wired a real `NavigationViewModel` (`?.state == null`), so guard branches exercised a null state] → test harness fix in scope (tasks 2.7–2.9): wire a real instance and drive follow/nav states explicitly.

## Migration Plan

Additive, non-breaking: `computeAreaZoom` signature gains defaulted parameters (existing call sites compile unchanged). Rollback = revert the collector addition and the parameters; no persisted state or data migration involved.

## Verification

- **Unit tests** (`MapCanvasViewModelRouteFitTest.kt`, mirroring `MapCanvasViewModelRouteVisibilityTest.kt` harness: FakeOSMScoutClient, real `MapRenderer`, `setScreenSize`, real `NavigationViewModel`): viewport center/magnification after calc matches bbox; no fit while navigating (panel calc and reroute) and no fit while follow mode on; navigation starting inside the settle window cancels the pending fit; fav-select / route-panel-open disengage follow and fit; no fit on stale re-emission (user-moved viewport kept); gesture inside the settle window cancels the pending fit; empty-polyline fallback; no-coords no-op; fully covered canvas skips the fit; **sheet-inset**: covered-height flow makes the fit use the reduced visible height, the bbox midpoint lands on the visible-area center and both endpoints stay above the panel's top edge (asserted through `ProjectionUtils`).
- **Ground-resolution correction**: the fit tests compare against `computeAreaZoom(..., dpi = <display DPI>)`, i.e. the DPI- and `cos(lat)`-corrected value; the full unit suite (favorites, POI search, zoom range, map) verifies the other callers stay green.
- **On-device**: calculate a 30–50 km route in the route panel → both markers + polyline visible **above the open panel**; pan/zoom then screen-recreate (rotate) → viewport kept, no re-fit; pick a favorite while free-driving → follow disengages and the overview fits; start navigation → follow centers on GPS as today; force reroute during driving → camera stays.

## Open Questions

None blocking. (Android Auto overview parity, if ever wanted, is a separate change.)
