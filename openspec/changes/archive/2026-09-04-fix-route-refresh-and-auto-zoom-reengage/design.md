# Design: Fix stale route after reroute + auto-zoom re-engage affordance

See proposal.md for motivation and scope. Requirements live in `specs/map-render/spec.md` and `specs/map-recenter-button/spec.md`.

## Context

Phone map pipeline: `MapRenderer` renders at overrun size (1.2×) and serves small viewport shifts by blitting the visible sub-region of the front buffer instead of re-rendering tiles. `submitDebounced` (MapRenderer.kt:432) collapses submissions into one pending render per debounce window; when a non-zoom submission's blit covers the viewport it sets `pendingRender = null; return` (line 454-457). That discards any pending job, including `forceFullRender=true` overlay jobs (route set/clear, favorites, stylesheet switch) — the `routeResultFlow` collector's `setRoute(...)` + trailing `renderMap()` (MapCanvasViewModel.kt:1734-1746) hits this deterministically on reroute because the camera does not move. Follow-mode GPS ticks keep blitting (each covered), so no full render is ever re-enqueued: the new route data sits in the renderer but the old frame stays on screen until a gesture breaks the blit chain.

Auto-zoom lives inside the follow-render branch (gated at MapCanvasViewModel.kt:652). Pan sets `followMode=false` → auto zoom unreachable. Pinch/button zoom sets `autoZoomSuspended=true` (a private field, line 243) while `followMode` stays true → the re-center button (condition `!followMode && gpsFixQuality != NONE`, MapCanvasScreen.kt:1076/1220) never appears, so suspension is silent and irreversible until speed-band change or follow toggle.

## Goals / Non-Goals

**Goals**
- Forced overlay renders always execute, with or without camera movement; blit remains the pan/scroll optimization.
- Auto-zoom suspension is visible via the existing re-center button while navigating; button re-engages follow + auto zoom.
- Keep the decided UX: manual interaction stops auto zoom; no auto re-engage.

**Non-Goals**
- No changes to reroute trigger timing (fast-reroute-trigger), route clearing semantics (fix-reroute-route-drawing), or car/AAOS pipelines.
- No new auto-zoom tuning (bands, floors, cooldowns) — only suspension visibility.
- No auto re-engage timers.

## Decisions

### D1 — Protect forced renders from the blit fast-path (Bug 1)

**Chosen: guard in `submitDebounced`.** Change the blit-covered branch to:
```kotlin
if (!isZoom && blitCovered) {
    // Only a pending non-forced render may be discarded: the blit preview
    // covers a tile-only change. A pending forced render (route, favorites,
    // clear, stylesheet, epoch bump) must still run so overlay content
    // updates even when the camera never moved.
    if (pendingRender?.forceFullRender != true) {
        pendingRender = null
    }
    return
}
```
`PendingRender` already carries `forceFullRender` (MapRenderer.kt:204-209) — no struct change. The forced job's debounce signal was already sent by its own submission, so the loop still wakes and enqueues it. The blit frame still emits immediately as preview; the forced render supersedes it one debounce later (sub-second).

| Alternative | Assessment |
|---|---|
| **B: `setRoute`/`clearRoute` bypass debounce, enqueue directly** | Fixes route but not favorites/stylesheet/epoch-bump jobs; duplicates queue-management logic; two submission paths to keep in sync. More invasive. |
| **C: remove the blit-kill (`pendingRender = null`) entirely** | Always full-render after every covering blit — eliminates the optimization the `canvas-overrun` capability exists for; regression risk in follow-mode smoothness. |
| **D: emit frame + keep pending only when overlays changed** | Requires diffing overlay state at every submission — allocation/compare overhead in a hot path for a rare event. |

Chosen A keeps the optimization for the common case (tile pans) and fixes the rare overlay case with a one-line condition. Risk: pending forced render with a *stale* blit preview emitting first — harmless (same frame, replaced quickly). Risk: starvation if forced renders queue faster than renders complete — not reachable via the debounce (one pending slot, CONFLATED queue keeps latest).

### D2 — Expose auto-zoom suspension to the UI (Bug 2)

**Chosen: new `autoZoomPaused: Boolean` in `MapCanvasUiState`**, mirrored from `autoZoomSuspended` (kept as the internal @Volatile source of truth; set in `updateMagnification` and on resume in `onToggleFollowMode(true)`/`startNavigation`). Screen condition becomes:
```kotlin
val showReCenter = (state.autoZoomPaused && navState.isNavigating) || !state.followMode
if (showReCenter && state.gpsFixQuality != GpsFixQuality.NONE) { MapReCenterButton(...) }
```
`reCenterAction` unchanged: `onToggleFollowMode(true)` + center + render (MapCanvasScreen.kt:559) — it already unsuspends (MapCanvasViewModel.kt:1880-1893) in both the follow-off and suspended-while-navigating cases. Note: it also resets magnification to 15 and recenters — existing re-center behavior, accepted for parity.

| Alternative | Assessment |
|---|---|
| **B: separate SharedFlow/snackbar "auto-zoom paused" hint** | More moving state; button is the requested affordance and already exists. |
| **C: always show button while navigating** | Visually noisy during normal driving; violates "hidden when auto-driving". |
| **D: new `resumeAutoZoom()` that skips the mag-15 reset** | Nicer zoom continuity but new API + divergence from existing re-center semantics; defer (see Open Questions). |

### D3 — Scope of the blit guard

The guard applies uniformly to all forced submissions (`setRoute`, `clearRoute`, `setFavoriteLocations`, `setSearchSelected`, stylesheet switch — all pass `force-fullRender=true`, MapRenderer.kt:270-414). No per-caller special-casing.

## Threading / Lifecycle

- `submitDebounced` and the guard run on the caller thread (main). `pendingRender` is `@Volatile`; the guard is a single read — no lock change. Debounce loop and render loop stay on the renderer scope (`scope.launch`, MapRenderer.kt:468-539).
- `autoZoomPaused` is part of `uiState` (main-thread StateFlow); `autoZoomSuspended` remains the internal @Volatile mirror. Both update on main (`updateMagnification`, `onToggleFollowMode`, `startNavigation`).
- No lifecycle changes: button visibility derives from existing state; `reCenterAction` already guards null location (snackbar fallback).

## Risks / Trade-offs

- [Forced render with unchanged camera shows "late" route (one debounce)] → Bound by pan/rotate debounce (~100-300ms); imperceptible for the rare reroute case. Verified by test D5.
- [Re-center tap while suspended resets magnification to 15 (existing behavior)] → Accepted; document in UI.md. Optional polish deferred (Open Questions).
- [Guard condition touches hot path] → One nullable read per submission; negligible. Covered by existing `canvas-overrun` performance expectations (spec scenario "Ordinary pan keeps the blit optimization").
- [Spec drift: implementation shows button bottom-left, spec says "right side"] → Existing drift in `map-recenter-button` (prefixed in this change's delta by removing placement wording); UI.md placement note updated.

## Verification

- **Unit (Robolectric, `MainDispatcherRule`)**:
  - MapRenderer: `setRoute` + zero-shift `renderMap()` → forced job enqueued and a new frame carrying the route is emitted without any camera move (observe via `frameFlow`; may need a test-visible hook for queued-job inspection).
  - MapRenderer: covering blit with NO pending forced render → no job enqueued (optimization intact).
  - MapCanvasViewModel: pinch-zoom during navigation → `uiState.autoZoomPaused == true`; follow toggle → false.
- **Compose (MapCanvasScreen)**: button matrix — visible when `autoZoomPaused && isNavigating`; visible when `!followMode`; hidden when auto-driving; hidden without GPS fix; tap restores and hides.
- **On-device (GPX replay)**: reroute during replay → new route appears on map without touching the screen (logcat `submitDebounced`/`debounce enqueue` pair after `onRerouteRequest: rerouting`); pinch during navigation → button appears; tap → zoom resumes, button hides.

## Migration Plan

Additive; no schema/data migration. Rollback = revert the three Kotlin files (`MapRenderer.kt`, `MapCanvasViewModel.kt`, `MapCanvasScreen.kt`) + guideline note. Deploy with next APK; `fix-reroute-route-drawing` and `fast-reroute-trigger` are independent and land first.

## Open Questions

1. Should the suspended-while-navigating re-center tap skip the magnification reset (Option D2-D) to avoid a zoom jump while the user had the camera placed manually? Deferrable — does not change specs/approach; small follow-up if users complain about the jump.
