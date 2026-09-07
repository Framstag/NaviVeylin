# Proposal: Fix stale route after reroute + auto-zoom re-engage affordance

## Why

Two phone bugs around reroute, observed on device after the reroute UX changes landed:

1. **Route not refreshed on the map after a successful reroute.** Turn instructions update to the new route, but the map keeps drawing the old route until the user pans/zooms hard enough to force a full native render. Root cause found in `MapRenderer.submitDebounced`: the blit fast-path (`trySubRegionBlit` covers the viewport movement) executes `pendingRender = null; return` — discarding **any** pending render, including forced ones. The `routeResultFlow` collector calls `mapRenderer.setRoute(...)` (submits a `forceFullRender=true` job) and then `renderMap()` (submits a non-force job); when the camera did not move (reroute does not pan the camera), the second submission's blit **covers** the viewport and deterministically kills the forced route render. Follow-mode GPS ticks keep blitting (each covered), so no full render is ever enqueued; the new route data sits in the renderer but the old frame stays on screen.
2. **Auto zoom dies silently after manual interaction.** A pan sets `followMode=false`, which gates the entire follow-render branch including the auto-zoom computation, so auto zoom stops. Worse: a pinch/button zoom while still in follow mode sets `autoZoomSuspended=true` (MapCanvasViewModel) with **no visible affordance** — the re-center button only appears when `followMode=false`, so `followMode=true + autoZoomSuspended=true` leaves the user with permanently dead auto zoom and no way to notice or recover.

## What Changes

- **Do not discard forced renders in the blit fast-path** (`MapRenderer.submitDebounced`): when the sub-region blit covers the viewport, only drop a pending render if it is not a forced render (`pendingRender?.forceFullRender != true`). A pending forced render (route set/clear, favorites, stylesheet switch) still runs after the debounce, so overlay changes appear even when the camera never moved. Fixes Bug 1 and the same latent race for `clearRoute`, `setFavoriteLocations`, `setSearchSelected`, and stylesheet switches.
- **Expose auto-zoom suspension state**: surface `autoZoomSuspended` (currently a private `@Volatile var` in `MapCanvasViewModel`) so the UI can react to it.
- **Re-center button appears when auto-zoom is suspended while navigating**: extend the existing re-center button visibility condition from `!followMode` to also cover `navigating && autoZoomSuspended`. Tapping it re-engages follow mode and unsuspends auto-zoom (existing `onToggleFollowMode(true)` already resets suspension). Bug 2 behavior (manual interaction stops auto zoom, no auto re-engage) stays as decided; the fix is making the re-engage affordance reliably visible.
- **No auto re-engage**: pan/zoom during navigation intentionally keeps stopping follow/auto-zoom; recovery is the button only (design decision, recorded here and in design.md).

## Capabilities

### New Capabilities

None — both fixes map onto existing capabilities.

### Modified Capabilities

- `map-render`: the sub-region blit fast-path must never discard a pending forced render; overlay-only changes (route/favorites/clear/stylesheet) render even when the viewport did not move.
- `map-recenter-button`: re-center button also appears when auto-zoom is suspended during navigation (follow mode still on); spec gains the suspended-while-navigating scenario. Existing scenarios (pan/zoom suspend follow) unchanged.
- `reroute-route-visibility` (in-progress change `fix-reroute-route-drawing`, uncommitted): untouched — route-stays-drawn + failure-surfacing requirements remain; this change makes the refreshed route actually render. No delta here.

## Impact

- **Code**:
  - `app/src/main/java/com/naviveylin/ui/map/MapRenderer.kt` — `submitDebounced`: blit-covered path keeps a pending forced render (`~35 lines`); no other renderer change.
  - `app/src/main/java/com/naviveylin/ui/map/MapCanvasViewModel.kt` — expose auto-zoom suspension in `uiState` (new field, e.g. `autoZoomPaused`), keep `autoZoomSuspended` semantics; reset on `startNavigation`/follow toggle (already exists).
  - `app/src/main/java/com/naviveylin/ui/map/MapCanvasScreen.kt` — re-center button condition: `(!state.followMode || (state.autoZoomPaused && navState.isNavigating)) && gpsFixQuality != NONE`; `reCenterAction` unchanged (already re-enables follow + unsuspends).
- **Tests**:
  - `app/src/test/java/com/naviveylin/ui/map/` — Robolectric test: `setRoute` followed by a zero-shift `renderMap()` still enqueues/renders the forced route job (blit must not kill it).
  - `RoutePanelComposeTest.kt` / new `MapCanvasScreen` follow-up: re-center button visible when `autoZoomPaused && navigating`, hidden otherwise; visible when `!followMode`.
  - Existing suites (`RoutePanelViewModelRerouteTest`, navigation tests, `run-tests` regression) must stay green.
- **No native change**: no submodule patch, no JNI, no vcpkg/CMake impact.
- **Scope**: phone variant. Android Auto/AAOS unaffected — car surfaces have their own render pipeline (car `MapController` + `NavigationState.routeLats`) and no phone-style auto zoom; car follow/smooth-follow behavior unchanged (see `auto-smooth-follow` / `auto-speed-zoom`).
- **Additive**, not breaking. Rollback: revert the three Kotlin files; MapRenderer returns to dropping forced renders on blit, button condition to `!followMode`.
- **Guidelines**: `guidelines/MapRendering.md` — document the blit fast-path contract: sub-region blit is a tile preview only and must never suppress an overlay/forced render pass. `guidelines/UI.md` — re-center button visibility rules updated for the new suspended-while-navigating case.
