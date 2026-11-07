# Proposal

## Why

A reroute currently takes the map away. `NavigationSession.pushCalculationNotice()` pushes
`RouteCalculatingScreen` — a `PaneTemplate` — over `NavigationScreen` once a calculation outlasts
`CALCULATION_NOTICE_DELAY_MS` (400 ms), and the pushed screen is what the host renders: the driver
off route, waiting for the road network, sees a pane and no map. The cost is larger than a hidden
bitmap, because `NavigationScreen.onStop` runs `rendererGate.pause()` and `detachSurface()`, so the
renderer pauses and the surface is re-attached on return — mid-guidance, once per reroute.

Two shipped requirements claim otherwise and cannot be satisfied: `auto/navigation-view` — "Reroute
keeps the navigation view live under the notice" says the navigation view remains "displayed
underneath", and `car-host-fault-isolation` — "Error raised while navigating" says the same of the
error notice. The Car App Library has no overlay layer: a pushed `Screen` **is** the screen. The
specs describe a stack, not a display.

The platform does offer what the notice was reaching for. `NavigationScreen` already draws on the map
surface through `rendererGate.setOverlayDrawer { canvas, w, h -> … }`, bounded by the host-reported
`stableArea`, and already uses that path for text the template cannot carry
(`MapUnavailableOverlay` — "the template has no content slot, so the app draws it"). A reroute is the
same case with a better answer than a pane.

## What Changes

- **A reroute no longer pushes a screen.** While navigation is active the car keeps
  `NavigationScreen` as the visible screen for the whole calculation, however long it runs, and draws
  the wait on the map surface: a strip in the top-left of `stableArea`, opposite the right-edge
  indicators, reading the calculation state — never covering the route, which stays drawn and
  usable.
- **The strip carries the percentage**, in the existing 5 % step (`CALCULATION_PERCENT_STEP`,
  `displayedCalculationPercent()`), which now bounds re-render requests rather than host pushes.
- **A failed reroute is reported on the strip, not in a pane** ("Recalculation failed", same dwell),
  so a failure no longer blanks the map either.
- **The error notice's dwell rises from 4 s to 10 s** (`ERROR_DISPLAY_MS`), for the failures that do
  still show a pane.
- **The cold start notice is unchanged.** With no navigation view and no map to protect, a route
  calculation before navigation starts keeps its pane, its 400 ms delay, its 700 ms dwell, its Cancel
  and its host-push budget. `RouteCalculatingScreen` keeps one caller, not two.
- **Off route gains a car cue, and it is not the strip**: the instruction panel states it — a Step
  whose cue is the off-route wording, with the route's next manoeuvre still offered as the next step
  — and the state is spoken once. The on-route rule that the panel keeps describing the route the
  vehicle is still on is suspended while the vehicle is known off route. The trip card keeps
  reporting an unknown estimate (`Trip.Builder().setLoading(true)`), which it already does through
  `NavigationTemplateMapper` and `state.isRerouting`.
- **No Cancel during a reroute — accepted, and recorded.** The surface takes no taps
  (`auto-map-layout`: the AAOS template host does not forward surface gestures) and the pane is
  gone, so a hung reroute has no exit. A later Cancel would have to leave navigation for free-driving
  mode at the same moment; that is out of scope here and named in design.
- **No stale-route styling.** The drawn route stays exactly as it is; a "this line is old" treatment
  is deferred on purpose.
- **The phone is unchanged.** It keeps its card tint and has no percentage. The car's strip plus
  panel is a deliberate cross-variant deviation, recorded in `UI.md` §8d.

Additive, not breaking: no user-visible removal, no state, storage or API shape change, no data
migration. Rollback is deleting the overlay element and restoring the `pushCalculationNotice()` call
on the navigating branch — the notice path is untouched and still exercised by cold start.

No native change: nothing here touches JNI, the bridge module or the libosmscout submodule. The
overlay is Android `Canvas` drawing on a surface the session already owns.

## Capabilities

### New Capabilities

- `auto/off-route-indicator`: the car's presentation of "the vehicle is no longer on the planned
  route" — the instruction panel states it while the vehicle is off route, the route's next manoeuvre
  stays readable as the next step, the on-route panel rule is suspended for that state, and the cue
  ends when the vehicle is back on a route. Counterpart of the phone's `off-route-indicator`, which
  owns the status-card tint.

### Modified Capabilities

- `route-calculation-feedback`: the car wait notice becomes a not-navigating behaviour; a reroute's
  progress is carried by the map surface instead, and a failed reroute is carried there too. Affected:
  "Car wait notice while a route is being calculated", "The car notice never outlives its
  calculation", "A notice the navigation view replaces stays visible for a minimum dwell", "One
  wait-notice implementation on the car display".
- `auto/navigation-view`: a reroute keeps the map as the visible view, replacing "Reroute keeps the
  navigation view live under the notice"; the map-surface contents enumeration ("only the compass
  rose, speed badge and attribution") admits the calculation strip; the instruction panel gains the
  off-route state its "keeps describing the route" rules exclude.
- `auto-map-layout`: the calculation strip's place among the surface elements — inside `stableArea`,
  top edge, clear of the right-edge indicators and the attribution, and not a control (surface
  gestures do not reach the app).
- `rerouting-visual-feedback`: "Reroute calculation progress is exposed" points its notice scenario at
  the surface strip instead of the car notice, and the phone's red card tint keeps its own path.
- `car-host-fault-isolation`: "Host screen-stack mutations are balanced" scopes "the notice is shown
  over the navigation view" to the notices that still push one, and gains the bounded 10 s dwell of
  the error notice.
- `voice-guidance`: the off-route departure is announced once, following the held-state rule the GPS
  announcement already uses, and respects the surface's voice switch.

## Impact

Affected modules: `:auto` (the whole user-visible change) and `:app` (the announcement, which the
phone's TTS delivers for both surfaces). No change to `:core`'s `NavigationState` — `isOffRoute`,
`isRerouting` and `calculation` already carry everything the new elements read.

- `auto/src/main/java/com/naviveylin/auto/NavigationSession.kt` — the notice is armed and pushed only
  while navigation is not active; the calculation-failure path routes to the strip instead of the
  error notice; `ERROR_DISPLAY_MS` 4000 → 10000.
- `auto/src/main/java/com/naviveylin/auto/NavigationScreen.kt` — the overlay drawer gains the strip;
  the template rebuild carries the off-route Step.
- `auto/src/main/java/com/naviveylin/auto/NavigationTemplateMapper.kt` — `routingInfoFromState`
  builds the off-route current step while the vehicle is off route; `loadingTrip()` unchanged.
- `auto/src/main/java/com/naviveylin/auto/RouteCalculatingScreen.kt` — keeps its cold-start caller;
  its percentage step and display helper are shared with the strip.
- A new surface-drawing element beside `SurfaceIndicators.kt` / `SurfaceAttribution.kt` /
  `MapUnavailableOverlay.kt` for the strip, in the same `draw(canvas, …)` shape so it is unit-testable
  without a host.
- `auto/src/main/res/values/strings.xml` and `values-de/strings.xml` — the strip's and the panel's
  wording, both languages.
- `app/src/main/java/com/naviveylin/navigation/NavigationEngine.kt` and `AndroidVoiceOutput.kt` — the
  off-route announcement, following the GPS hold/announce pattern.
- Tests: `auto/src/test/java/com/naviveylin/auto/` (`RouteCalculatingScreenTest`,
  `NavigationTemplateMapperTest`, `NavigationTripTest`, session and dwell tests) and the German
  resource completeness test.

Guideline documents that follow the change in the apply pass: `guidelines/UICar.md` §3 (the notice
rules that stop applying to a reroute), `guidelines/UI.md` §8d (the recorded car/phone deviation),
`guidelines/MapCar.md` (the surface's drawn elements).

surface: 58 scenarios across 7 delta files — 29 added and 29 rewritten in place (copied whole from
their shipped blocks), no fixtures realigned; 10 cases named in an existing class, 9 in a new
`CalculationStripTest`, 6 in a new `OffRouteAnnouncementTest`, 9 declared `none` with the on-device
measurement that settles them (tasks 7.2) — command: `grep -c '^#### Scenario:'
openspec/changes/car-reroute-without-losing-the-map/specs/*/spec.md
openspec/changes/car-reroute-without-losing-the-map/specs/auto/*/spec.md` (4, 7, 3, 18, 6, 15, 5 =
58) against `grep -c '^#### Scenario:' openspec/specs/route-calculation-feedback/spec.md` and the
same for `rerouting-visual-feedback`, `car-host-fault-isolation` and `auto/navigation-view` for the
blocks the deltas copy.
