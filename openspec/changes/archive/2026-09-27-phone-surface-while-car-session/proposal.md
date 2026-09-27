# Proposal

## Why

While a car session renders the navigation on the car display, the phone keeps a full-screen animated
map canvas alive for the same navigation. Measured 2026-09-27 on a Pixel 8 with a car session live
(phone UI toggled on/off, everything else unchanged):

| state | Graphics (EGL+GL) | Native heap | TOTAL PSS | malloced bitmaps |
|---|---|---|---|---|
| phone UI on screen | 132 MB (EGL 51.8 + GL 80.2) | 170.9 MB | 540.6 MB | 62.0 MB |
| phone UI backgrounded | 28.0 MB (GL only) | 171.2 MB | 444.5 MB | 76.6 MB |
| back on screen | 109 MB (EGL 31.1 + GL 78.1) | 166.2 MB | 521.0 MB | 62.0 MB |

- The phone UI's cost is **~96-148 MB of graphics**, and it is not the map *rendering*: native heap
  does not change at all when it goes away (the tile-data retention stays), and the app's bitmap
  cache is not released (62 → 77 MB, i.e. it stays).
- What that money buys: the Activity window's own buffer queue (EGL 31-52 MB — spent by *any* phone
  UI, including a status screen) plus the map's uploaded frames and gesture/animation layers
  (GL 48-80 MB; the map's overrun frame is 1296×2880 = 14.9 MB per bitmap, and a zoom gesture adds
  ~50 MB that is never given back).
- Why now: this phone is chronically out of memory and getting worse — LOW_MEMORY kills across all
  apps were 159 (Sep 24), 317 (Sep 25), 655 (Sep 26), 657 (Sep 27), with 85 already in hour 08. In the
  phone + Android Auto combination the process lmkd reaps is the host family (`gearhead:projection`
  249 MB, `:car` 197 MB, `:provider`/`:watchdog`/`:shared` ~101 MB each), because this app's
  foreground-service navigation process outranks it. Every hundred MB the phone surface does not need
  is a hundred MB the host is more likely to survive.

## What Changes

- **While a car session is active in the process, the phone presents a car-session surface instead of
  composing the map canvas.** The map canvas is not composed and the phone requests no renders, so the
  window's graphics work is a small static UI and the map's frame/animation buffers are not allocated.
- **The phone-owned render storage is released while suspended**: the bitmap tile cache and the phone's
  pooled render targets. The *shared* native tile-data cache is deliberately **not** trimmed — the car
  renders from it (that retention is the sibling change `bound-tile-data-retention`'s business).
- **The user can bring the map back** with an explicit action on the car-session surface (a passenger
  may want it). The override holds for the rest of the session and resets when the session ends.
- **The phone returns to the map when the car session ends**, with the viewport/mode it had (the car
  session does not touch the phone's view state).
- **The car-session surface is informative, not blank**: which session is active and the current
  guidance summary from the shared engine state, with the same labels the car shows
  (`cross-variant-ui-parity`), in German and English.
- **Not in scope**:
  - the shared native retention and its release under pressure (`bound-tile-data-retention`);
  - the phone canvas' frame/buffer strategy while the map *is* shown (`reduce-render-peak-memory`,
    option B);
  - the car surface, the car host, and the host's own lifecycle faults when the head-unit link drops;
  - a persisted "always suspend" preference (per-session override only, until a user asks otherwise).

## Capabilities

### New Capabilities
- none.

### Modified Capabilities

- `map-canvas-screen`: the screen gains a suspended presentation — while a car session is active the
  map canvas is not shown or rendered, a car-session surface takes its place, the user can override it
  for the session, and the map returns when the session ends.

## Impact

Affected files and modules:

- `app/src/main/java/com/naviveylin/ui/map/MapCanvasScreen.kt` — the composition decision (show the
  map canvas or the car-session surface) and the new surface's layout/actions.
- `app/src/main/java/com/naviveylin/ui/map/MapCanvasViewModel.kt` — a `phoneMapSuspended` state flag fed
  by the presence flow, the render-request gate while suspended, the override, and the release of the
  phone-owned caches (bitmap tile cache, pooled targets) on suspension and their reconstruction on
  resume.
- `app/src/main/java/com/naviveylin/navigation/CarSessionPresenceImpl.kt` and its interface
  (`core`) — the presence signal this consumes; it is process-scoped and in-memory already, and must
  not be changed in shape (the sibling change that introduced it is in flight).
- `app/src/main/java/com/naviveylin/ui/map/CarSessionIndicator.kt` — the existing advisory pill; its
  content folds into the new surface (or stays as the compact form of it).
- Resources: `app/src/main/res/values/strings.xml` + `values-de/strings.xml` for the new surface
  (German completeness is a gate in this repo).
- Tests: `MapCanvasViewModel` suspension/override/resume cases, a Compose test for the surface and the
  override action, and a regression case that no render is requested while suspended.
- Android components: no manifest change, no new activity/service; no Gradle change. Both flavors
  build (the trigger is a car session, which the automotive flavor also has — and there the phone
  surface does not exist, so the state flag is inert).

Guidelines affected and to be updated in the same change:

- `guidelines/UI.md` — the phone's car-session presentation and the override rule (the UI rules own the
  phone/Auto split).
- `guidelines/MapRendering.md` — that the phone canvas is disposed (not just hidden) while suspended,
  and what that releases and does not release (the shared native cache).

Native/JNI: **no submodule patch and no bridge-module override** — nothing native is added; the change
only stops calling the existing render path from the phone surface.

Change class: user-visible behaviour change by default (the map is not shown while the car drives) —
**mitigated, not hidden**: the override returns the map for the session, and a revert restores today's
behaviour in one flag. Rollback path: revert the suspension gate; no persisted state, no migration.

Scope: **phone only** (the trigger is the car session, the affected surface is the phone map). The car
side is untouched; on Android Automotive OS the flag is inert because there is no phone surface.

Previous specifications changed: `map-canvas-screen`. Related: `car-session-presence` (in flight with
`shared-resource-arbitration`; consumed, not modified), `cross-variant-ui-parity` (labels of the new
surface follow it), `map-render`/`render-performance` (the sibling memory changes).
