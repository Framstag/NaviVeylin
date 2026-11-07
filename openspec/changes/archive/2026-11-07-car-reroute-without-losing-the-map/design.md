# Design

## Problem restated

`NavigationSession.pushCalculationNotice()` pushes `RouteCalculatingScreen` (a `PaneTemplate`) over
`NavigationScreen` when a calculation outlasts `CALCULATION_NOTICE_DELAY_MS` (400 ms). A pushed
`Screen` **is** the displayed template, so a reroute removes the map for the calculation's whole
duration; `NavigationScreen.onStop` then runs `rendererGate.pause()` and `detachSurface()` and the
renderer restarts on return. Two shipped requirements describe that as an overlay
(`auto/navigation-view` — "Reroute keeps the navigation view live under the notice",
`car-host-fault-isolation` — "Error raised while navigating … shown over the navigation view").
The platform has no overlay layer, so those sentences were never true.

## Decision 1 — where the wait lives while navigating

| option | assessment |
|---|---|
| **A. app-drawn strip on the map surface** (chosen) | No screen push, no renderer pause, map interactive. Precedent and infrastructure exist: `rendererGate.setOverlayDrawer`, `stableArea`, `MapUnavailableOverlay` was built for exactly "the template has no content slot". Cost: one `Canvas` element, testable as a pure geometry+draw unit. |
| B. host `Action` in the action strip | Host-rendered, zero drawing, but an `Action` needs a click listener — a label that cannot be tapped is a fake affordance — and the strip already carries zoom in/out, so it competes for the same two slots. No colour. |
| C. `TravelEstimate.setTripText` | Free text on the navigation template, but that slot is the street name's (`auto/navigation-view` — Current street name), so the wait would hide the road name. No colour, no percentage. |
| D. keep the pane, shorten it | Does not solve the complaint: any duration hides the map, and the renderer still pauses. |

Risk of A: the strip competes for pixels with the right-edge indicators and the attribution. Mitigated
by the placement rule and by `stableArea` (the host's own visible bounds, already used by both).

## Decision 2 — how the strip is drawn

| option | assessment |
|---|---|
| **A. a new drawer element beside `SurfaceIndicators` / `SurfaceAttribution` / `MapUnavailableOverlay`** (chosen) | Each of those is a pure `draw(canvas, width, height, density, …)` plus a `geometry(...)` function returning a `Rect` — unit-testable without a host, the pattern the module already uses for surface text. |
| B. extend `SurfaceIndicators.draw` | Couples two unrelated responsibilities (right-edge telemetry vs a transient status) in a function that already carries nine parameters. |
| C. draw inside `NavigationScreen`'s lambda | Untestable without a host; the lambda is already the longest method in the screen. |

The strip takes `stableBounds`, `surfaceWidth/Height`, `density`, the presentation flag, and a small
`state` value (waiting/idle, percent?, failed?). The same function serves both presentations.

## Decision 3 — the off-route cue

| option | assessment |
|---|---|
| **A. host instruction panel states it** (chosen) | Largest host-rendered text area, and the one place whose content is *false* while off route — the panel would otherwise show a manoeuvre for a road the vehicle is not on. `Step.Builder().setCue(…)` takes free text, so no new API. |
| B. trip card text | One small line in the ETA card, sharing space with the unknown estimate; easy to miss. |
| C. surface element | Keeps the panel stale (the defect B fixes) and puts a second text block on the map. Rejected on the owner's decision that off-route is not the overlay's job. |
| D. keep the old behaviour (nothing) | The car has no off-route cue at all today; the phone has had one since `off-route-indicator`. |

The panel keeps the route's next manoeuvre as its next step, so "where does this route still go" is
readable while the current step states the off-route condition. `isOffRoute` already exists in
`NavigationState`, so no new state.

Risk: `auto/navigation-view`'s two panel requirements describe the on-route case. The new capability
`auto/off-route-indicator` states the exception explicitly rather than rewriting two long requirements,
and its description names them as the rule it excepts.

## Decision 4 — the spoken cue

| option | assessment |
|---|---|
| **A. announce once per departure, following the GPS hold pattern** (chosen) | `NavigationEngine` already owns a held-state announcement for the GPS fix state (`voice-guidance` — A GPS announcement is spoken only after the state held): same shape, same switch handling, same silence outside a session. |
| B. announce on every detection | Off-route flickers at a junction or in a tunnel guard; the GPS rule exists because of exactly that. |
| C. no announcement | Leaves the car with a visual cue only, in the one context where the driver is looking at the road. |

The car surface does not speak; the phone's TTS does, so the announcement is engine-level and reaches
both surfaces (see `PreferencesScreenMapper.KEY_GPS_ANNOUNCEMENTS` on which surface owns which
switch). Scope stays deliberate: the phone gains speech, not a new on-screen element.

## Decision 5 — a failure during navigation, and the dwell

| option | assessment |
|---|---|
| **A. report on the strip; every remaining pane dismisses itself after 10 s** (chosen) | A failed reroute is the same "the map must stay" case as a running one. `ERROR_DISPLAY_MS` exists (4 s) and already dismisses by identity with a deferred removal; the change is the value and the routing of the calculation failure away from `showError`. |
| B. pane for the failure, 10 s | Blank map for 10 s precisely when the driver is off route and the app has given up. Contradicts the change's own premise. |
| C. pane, no timer | Already rejected in practice: the timer predates this change. |

## Decision 6 — no Cancel during a reroute

Accepted, not overlooked. The surface does not forward gestures to the app
(`auto-map-layout` — the AAOS template host does not forward surface gestures), the pane is gone, so
there is no exit while a reroute hangs. The alternatives were a host action strip `Action` (competes
with zoom, and the exit is not "cancel the calculation" but "stop navigating") and switching to
free-driving mode, which is a behaviour change of its own. Recorded here so the next change finds the
constraint rather than rediscovering it.

## Decision 7 — the percentage step

`CALCULATION_PERCENT_STEP` (5) and `displayedCalculationPercent()` stay, and move beside the strip
rather than the screen class. Their reason changes from "bound host pushes" to "bound render
requests": a per-percent update would ask the renderer for a frame up to 100 times per calculation.

## Decision 8 — the renamed requirement

`auto/navigation-view`'s "Reroute keeps the navigation view live under the notice" is `RENAMED` to
"Reroute keeps the navigation view on screen" and `MODIFIED` in the same delta. Its existing scenario
name "Reroute notice over live navigation" is kept verbatim (a renamed scenario is a removal plus an
addition for the validator) and rewritten to say the wait is drawn *on* the live navigation view.

## Threading and lifecycle

- **Strip drawing** runs inside the renderer's `overlayDrawer` lambda, i.e. on the thread that composes
  the frame, on the map surface the session owns. It reads a snapshot the screen publishes
  (`@Volatile state` on the screen, written from the state collector), never the ViewModel, and never
  blocks. No host call is made from it.
- **Percent changes** are published to that snapshot from `NavigationScreen`'s state collector and
  request a frame (`rendererGate`), never a template `invalidate()`: a percentage is not a host
  concern any more.
- **The failure dwell** (10 s, and the strip's own report window) is one cancellable job on the
  session's `scope`, following `scheduleErrorNoticeDismissal`: cancel on a new calculation, on
  session destroy, and defer the host removal when the session is not started.
- **The off-route panel state** is built in `NavigationTemplateMapper.routingInfoFromState` and
  reaches the host only through the existing template rebuild path (main thread, guarded).
- **The announcement** is held and spoken on the engine's own coroutine, exactly as the GPS state is,
  and is cancelled by the session end.

## Files

- `auto/src/main/java/com/naviveylin/auto/NavigationSession.kt` — arm/push the notice only while
  `!isNavigating`; route a calculation failure to the surface instead of `showError` while navigating;
  `ERROR_DISPLAY_MS` 4000 → 10000; new strip dwell job.
- `auto/src/main/java/com/naviveylin/auto/NavigationScreen.kt` — publish the strip snapshot; draw it
  in the existing `setOverlayDrawer` lambda; request a frame on a displayed-percent change.
- `auto/src/main/java/com/naviveylin/auto/NavigationTemplateMapper.kt` — the off-route current step
  (`state.isOffRoute`), keeping the next-step manoeuvre and `loadingTrip()` unchanged.
- new `auto/src/main/java/com/naviveylin/auto/CalculationStrip.kt` (name in tasks) — `geometry(...)` +
  `draw(...)`, following `SurfaceAttribution`'s shape.
- `auto/src/main/res/values/strings.xml`, `values-de/strings.xml` — strip and panel wording.
- `app/src/main/java/com/naviveylin/navigation/NavigationEngine.kt` — the hold-and-announce of the
  off-route state; `AndroidVoiceOutput` unchanged.
- Tests under `auto/src/test/java/com/naviveylin/auto/` and `app/src/test/java/com/naviveylin/`.

## Verification

Unit (`auto:` module suite, `--tests` filtered):

- `CalculationStripTest` — geometry: inside `stableArea` when known, inside the surface when not, clear
  of the right-edge column and the attribution rect; palette follows the presentation flag.
- `NavigationTemplateMapperTest` — off route: the current step carries the off-route wording and the
  next step still names the route's manoeuvre; on route unchanged.
- `RouteCalculatingScreenTest` — the cold-start caller and its percentage step still hold.
- a session test that no screen is pushed for a calculation that runs while navigating, and that one
  is pushed for a calculation that runs while not navigating.
- a dwell test that a notice is dismissed after `ERROR_DISPLAY_MS` = 10 s (test-clock advance, no wall
  clock) and that the removal is owed rather than made while stopped.
- `GermanTranslationCompletenessTest` — the new keys exist in both languages.

On device (AAOS AVD, `guidelines/BuildNative.md` §10a/§10b), because a visual claim needs a
measurement, not an impression:

- trigger a reroute with a deliberate delay and read the coordinate-free diagnostics line the session
  writes for the strip (`strip pct=<bucket> shown=1`), which is what makes the drawn percentage
  verifiable on a host whose template tree no UI dump can see — the same instrument the notice's
  `calculation notice push pct=` line is today;
- capture one screenshot at the reroute moment: the strip's bounding box must lie inside the region
  reported by `stableArea` and must not touch the rose/badge column; the map, the vehicle marker and
  the route must all be visible in the same frame;
- with the calculation forced to fail, assert the map is still on screen and the failure report ends
  by itself within the dwell, with no screen push in the session log.

## Risks

- **Strip vs. host chrome.** `stableArea` can be unknown early in a surface's life; the element falls
  back to the surface top edge (as `SurfaceAttribution` does for the bottom) and may sit under host
  chrome for one frame. Accepted.
- **Two reports at once** (a failure while a new calculation starts) — the strip has one state, and the
  later state wins by the same "one notice" reasoning the pane used.
- **The off-route panel state flickers** at a junction boundary; the state is `isOffRoute`'s, so the
  flicker is whatever the engine already emits for the phone's tint. Not introduced here.
- **Rollback**: delete the strip element and the off-route step, restore the navigating
  `pushCalculationNotice()` call and `ERROR_DISPLAY_MS`, drop the announcement. No state, storage or
  API migration is involved.

## Out of scope

- Any styling of the drawn route as stale (owner decision: later).
- A Cancel affordance for a reroute, and the free-driving switch it would require.
- A percentage on the phone (the car/phone deviation is recorded in `guidelines/UI.md` §8d during
  apply).
- Fancier cold-start presentation.

## Open decisions

none
