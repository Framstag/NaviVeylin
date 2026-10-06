# Design

## Context

The pieces that shape the approach (all phone `:app`; the car shares only the `NavigationEngine`, which
this change does not touch):

- **Two stop paths, neither session-aware.** `MapCanvasScreen.kt:2380-2382` (routing status card) and
  `:2074-2076` (the session card) both call `navigationViewModel.stopNavigation()` +
  `routePanelViewModel.setNavigating(false)` + `routePanelViewModel.clearRouteFromMap()`.
  `RoutePanelViewModel.setNavigating(false)` → `onNavigationStopped()` (`:753-790`) *would* start the
  grace period for an open session, but the immediate `clearRouteFromMap()` hides the route the stopped
  state must keep drawn.
- **A third stop path already exists and hides the route too.** `NavigationViewModel.setRoutePanelViewModel`
  (`:75-103`) observes the engine: on `isNavigating` false it calls `vm.setNavigating(false)` **and**
  `vm.clearRouteFromMap()`. It fires for the notification shade's stop, a car stop and the panel's own
  stop alike, so any fix in the screen alone is defeated by the adapter.
- **The pre-navigation mode snapshot can lose a race.** `MapCanvasScreen.kt:166-168` wires a follow-mode
  callback that calls `viewModel.onToggleFollowMode(true)` when a phone-initiated session starts, and
  `MapCanvasViewModel.kt:2886-2907` independently snapshots `followMode` when `navState.isNavigating`
  flips true. Two collectors on the same flow; whichever runs first decides whether `preNavFollow`
  records BROWSE or the forced-on `true` — which is exactly the measured
  "landed in FREE_DRIVE though the label before navigation said BROWSE" (`TODO.md` §140). This is the
  hypothesis the measurement must settle, not an established cause.
- **A session opened during navigation clears the route it should review.** `openRoutePanelWithStart`
  (`MapCanvasViewModel.kt:3077-3102`) calls `vm.setDestLocation(entry)`, and `setDestLocation` →
  `clearRouteIfNeeded()` (`RoutePanelViewModel.kt:397-409`) nulls `routeEntry`/`routeSteps` and sets
  `RouteState.Idle`. The adapter had already adopted the engine's route into the panel
  (`vm.adoptRoute(route, vehicle)`, `NavigationViewModel.kt:87`), so opening the session throws it away.
- **The Stop Navigation action is inside the route branch.** `RoutePanel.kt:578` renders it only within
  `is RouteState.Done`; after the clear above the panel is in `Idle`, so the read-only review shows the
  location fields and swap control and offers no Stop Navigation and no step navigator — matching the
  device dump in `TODO.md` §140.
- `RouteInstruction` (Java, `libosmscout-client-java`) already carries `distanceTo`, `timeTo`,
  `legDistance`, `turnType`, `streetName`/`description`; the adopted `RouteEntry` carries
  `descriptions` and the per-step arrays `instructionValues` reads. No new native data is needed.
- `openSession()` (`RoutePanelViewModel.kt:707-717`) already lands a session opened while navigating in
  `REVIEWING`. The state machine is correct; the surface and the route it reviews are not.

## Goals / Non-Goals

**Goals:**

- Every phone stop control ends navigation through one session-aware path: an open session enters
  `RouteSessionState.STOPPED` (route stays drawn, Restart and End offered, bounded grace), and a stop
  with no session open ends navigation and clears the route.
- A session opened during navigation is the documented read-only review of the route navigation runs:
  summary, steps with the current step marked, Stop Navigation offered, fields not editable, no
  calculate/clear offered.
- The map returns to the mode that was active before navigation started, on every stop path — proven by
  measurement on the card path, not assumed.
- Each of the above is pinned by a revert-checkable test or a recorded on-device number.

**Non-Goals:**

- The stop control's tap geometry, 48 dp hit area and disjoint regions (`fix-nav-overlay-stop-tap`,
  archived; unchanged here).
- The card's action band at font scale 2.0 (`TODO.md` §138).
- Any car-surface change; `:auto` templates keep `onStopNavigation = null`.
- The engine's reroute-lease behaviour (`§126`), `LoadingScreen` (`§127`), the `@Config` sandbox pin
  (`§128`), and the notification's trip publishing while stopped (`§56`).
- Planning a *new* route while navigation is active: the spec makes that session read-only, so the
  narrowing (no calculate/clear) is intended, not a regression.

## Decisions

### D1 — Measure the card stop path on a clean build before touching the mode restore

Alternatives:

1. **Assume the follow-snapshot race** described in Context and fix the ordering immediately. Cheapest,
   but `TODO.md` §140 explicitly says the cause is not established and the run was made from a tree
   carrying other in-flight work; a fix aimed at the wrong cause leaves the defect while looking done.
2. **Logcat only** — `NavigationEngine`'s `stopNavigation: stopped` and `RoutePanelVM`'s grace line show
   *that* navigation stopped and whether a grace started, but neither the map mode nor the session state.
3. **UI-dump + logcat + mode label (chosen).** On a clean build: record the Drive-control label before
   navigation (`start_free_drive` = BROWSE, `exit_free_drive` = FREE_DRIVE), start navigation, tap the
   card's stop control at its measured node centre, then record the Drive-control label, the session
   state (grace line present or absent), and the `NaviVeylin`/`NavigationEngine` lines. Repeat with a
   session open during navigation (map long-press → candidate → the panel's entry) and with no session.

Rationale: three of the four facts the fix depends on are only visible together — the mode, the session
state and which control ran. All numbers are coordinate-free (mode name, session state, node identity,
boolean flags) per `guidelines/Regulatory.md` §9. If the run shows the snapshot is taken correctly and the
mode is still wrong, the change records that and looks at the mode derivation (`MapCanvasViewModel.kt:339-341`)
instead; if it shows the card path is fine and only the panel path was broken, the mode half shrinks to a
test.

**Measured outcome (2026-10-06 07:04-07:10, base = `HEAD a7e73e7` + the 98 uncommitted files other
in-flight work shares; APK built 07:04:04, installed 07:04:09 on `emulator-5554`, x86_64, API 37,
de-DE, maps `north-rhine-westphalia`).** Route: Dortmund (Kampstraße) → Bochum, 21,6 km remaining in the
card.

- **Run (a)+(c) — no session open.** The map stood in BROWSE before the search (Drive control
  `Freie Fahrt starten`); starting navigation dismisses the session, so none was open at the stop. The stop
  control's own node is `content-desc="Navigation beenden"`, `clickable="false"`, bounds
  `[975,2222][1038,2285]`; the clickable node over it is `[943,2190][1069,2316]`, and the details regions
  `[32,2018][1069,2190]` / `[32,2191][943,2317]` are disjoint from it (`fix-nav-overlay-stop-tap` in place).
  Tapping the node centre ran the stop — `NavigationEngine: stopNavigation: stopped` at 07:07:17.120 — and
  then logged **`MapCanvasVM: setNavigationViewModel: navigation ended, mode restored follow=true
  suspended=false, mag=15.0`**; the Drive control afterwards read `Freie Fahrt beenden` → **FREE_DRIVE
  although the pre-navigation mode was BROWSE**. No `RoutePanelVM` line (no session open) — correct for
  that configuration. **Defect 1 of `TODO.md` §140 reproduces.**
- **Run (b) — session opened during navigation** (map long-press → candidate → `Route berechnen` →
  `openRoutePanelWithStart`). The review rendered only the header `Route · 51.51724, 7.46530`, its collapse
  and close controls, the two location fields (`clickable="true"`: `Aktueller Standort`,
  `51.51724, 7.46530`) with their clear buttons, and `Start und Ziel tauschen` — **no Stop Navigation inside
  the session, no step navigator, no route summary or step list**. The only stop control on screen was the
  status card's. Tapping it: `stopNavigation: stopped` at 07:09:14.303, `mode restored follow=true`
  (correct here — the pre-navigation mode was FREE_DRIVE), and **45 s later
  `RoutePanelVM: session grace period expired - ending the session` at 07:09:59.304**.
- **Correction to `TODO.md` §140.** The session **does** enter its stopped state through the card's stop:
  the grace job starts and expires on time. What is missing is (i) the stopped state's **visible Restart and
  End** — the dump right after the stop still shows the editing content (vehicle selector, `Berechnen`) — and
  (ii) the surface: after the grace expired the card was **still on screen**, now with empty `Startort`/
  `Ziel` and `Berechnen`, contradicting `route-planning-session` ("Grace expiry closes an open surface", "No
  surface survives the session"). §140's "the session never entered its stopped state" and "no grace line
  followed" are therefore not what the card path does when a session is open.
- **Not measured**: whether the route polyline was still drawn during the grace. The map is a canvas, so the
  accessibility dump carries no node for it and no pixel verdict applies to a polyline.
- **Both §140 defects reproduce for the code paths the change names**; the measured session behaviour adds
  the surface/stopped-state gap above, which task 6.2 files (see the pause note in the apply record).

### D2 — One session-aware stop path, owned by the screen

Alternatives:

1. **Keep the three call sites, duplicate the session decision in each.** Smallest diff, but the three
   already drifted once (the card and the panel disagree today), and the adapter's path would still hide
   a route the stopped state must keep.
2. **Decide inside `RoutePanelViewModel`** (a `onStopRequested(): Boolean` returning whether a session was
   open). Hides navigation control in the panel and gives the wrong owner: the panel is phone-only while
   the stop is a navigation fact.
3. **Decide inside `NavigationViewModel`.** It already owns the engine adapter, but it is surface-agnostic
   by design (the car resolves the same engine), and the car has no session — the panel would have to be
   injected into it.
4. **One callback owned by `MapCanvasScreen`, plus a session-aware end branch in the adapter (chosen).**
   The screen builds a single `stopNavigation()` lambda used by the routing status card, the expanded
   details view and the panel's Stop Navigation: it calls `navigationViewModel.stopNavigation()`.
   `RoutePanelViewModel.setNavigating(false)` stays the single notification into the session, and the
   adapter's end branch stops calling `clearRouteFromMap()` when the session is in `STOPPED` (the session
   then owns the route's visibility for the grace window and clears it when the grace expires).

Rationale: one implementation of "who clears the route", the engine adapter keeps working for the
notification and car stop paths, and the panel never decides navigation policy. The invariant — "a stop
control never clears a route the stopped state is displaying" — is testable at the adapter and at the
ViewModel.

### D3 — The read-only review reuses the adopted route; opening a session stops clearing it

Alternatives:

1. **Build a second review model from `NavigationState`** (`instructions`, `routeLats`/`routeLons`,
   `totalDistance`, `remainingDistance`). Works without an adopted `RouteEntry`, but creates a second
   route model beside the panel's `routeEntry`/`routeSteps` — the duplication `guidelines/Design.md` §12
   forbids and the reason the current display and the panel could disagree.
2. **Have the engine publish the `RouteEntry` in `NavigationState`.** Largest: a `:core` + JNI surface
   change for data the adapter already has in hand (`engine.acquiredRoute`).
3. **Keep `adoptRoute` as the single route source; do not clear while navigation is active (chosen).**
   `clearRouteIfNeeded()` gains the navigation condition (or the callers stop calling it while
   navigating), so a session opened during navigation keeps the route the adapter adopted. The panel's
   route branch and its Stop Navigation action render on `navigationActive` rather than only on
   `RouteState.Done`, so the review is complete even when the panel never calculated the route itself.

Rationale: one route model, one step list, one length source (`routeLengthMeters`), and the review shows
exactly the route the map is drawing. The step navigator already exists and is fed by `routeSteps`.

### D4 — The mode restore keeps its single source of truth

Alternatives:

1. **Capture the mode in the stop callback and apply it there.** A second place that decides the mode;
   the existing snapshot/restore becomes dead or contradictory.
2. **Record the mode explicitly instead of the follow/suspension pair** (`preNavigationMode: MapMode?`).
   Cleaner conceptually, larger: the derivation (`:339-341`) is `followMode || driveSuspended`, and a
   third "the mode is also a lease" case already exists (session/follow). It also touches the viewport
   re-assertion block that the existing restore owns.
3. **Move the snapshot capture into the adapter, before it forces follow on (chosen, only if D1 confirms
   the race).** `NavigationViewModel.setRoutePanelViewModel` already knows the moment; it records the
   surface's `followMode`/`driveSuspended` (through the existing `onFollowModeChanged` seam) *before*
   invoking `onToggleFollowMode(true)`, so the snapshot cannot observe the forced-on value. If D1 shows
   the ordering is already correct, this decision degrades to "no code change; add the test".

Rationale: the fix stays at the one place that already owns the pre/post-navigation transition, and it
removes the ordering assumption instead of adding a second mode authority. The alternative 2 shape is
recorded in `design.md` of this change as the fallback if the measurement shows the ordering assumption
cannot be removed at the adapter.

**Decision from D1's measurement (2026-10-06):** the restore logged `mode restored follow=true` after a
navigation that started in BROWSE, so the snapshot does observe the adapter's forced-on value — **task 3.5
applies** (move the capture into the adapter, before it invokes `onFollowModeChanged(true)`), and task 3.6
pins it.

### D5 — The stopped state keeps the route through the grace window

The session already owns the grace (`RoutePanelViewModel.kt:769-790`) and `endSession()` clears the route
when the grace expires. What changes is that nothing else hides it in the meantime: the screen's stop
callback no longer calls `clearRouteFromMap()`, and the adapter's end branch consults the session state.
Alternative: give the grace an explicit "route visible" lease the map honours — more machinery for the
same result, and a second visibility authority beside `_routeVisible`.

### D6 — The stopped state is rendered, and Restart is wired to the engine

Measured: `RouteSessionState.STOPPED` is reached and the grace expires on time, but `sessionState` is read
by **no** composable and no screen (`grep -rn sessionState app/src/main/java` → the ViewModel only), so the
stopped state shows the editing content, and `restart` has no string in either resource set.

Alternatives:

1. **Derive the stopped state in the screen from `isNavigating == false && session is open`.** No new
   surface state, but it makes the screen guess a state the session already owns, and it breaks the moment
   the session is `EDITING` with navigation stopped.
2. **Expose the session state to the panel and render the stopped branch from it (chosen).** `sessionState`
   already is a `StateFlow` on the ViewModel; the panel collects it (it already collects `uiState`) and
   renders Restart + End instead of the planning actions. Restart calls the screen's callback, which starts
   navigation on the session's route (`NavigationViewModel.start(routeEntry, vehicle)`, no recalculation)
   and reports it to the session (`onNavigationRestarted()`); End calls `endSession()` + the existing
   surface-close path.
3. **Reuse the planning actions with a flag.** Two meanings behind one control; a driver cannot tell
   whether the primary action starts or restarts.

Rationale: the session state machine is the owner, the panel is a view of it, and the strings stay
localized (a new `restart` entry in `values`/`values-de`).

### D7 — A session that ends by itself closes its surface
Measured: after the grace expired the card was still on screen with empty `Startort`/`Ziel`. `endSession()`
is called from the grace job inside the ViewModel; only the screen's `onEndSession` callback closes the
surface (`dismissRoutePanel()`), so the self-ending path has no way to.

Alternatives:

1. **Let the screen observe `sessionState` and dismiss the panel when it becomes `INACTIVE` while shown
   (chosen).** One observer in `MapCanvasScreen` (already the place that owns `showRoutePanel`), no new
   event type, and it closes every self-ending path (grace expiry today, any future one).
2. **Emit a one-shot `sessionEnded` event the host collects.** More explicit, but adds an event channel
   beside the state flow that already carries the information, and a dropped event leaves the surface.
3. **Have `endSession()` take a callback to the host.** Couples the ViewModel to the screen instance and
   does not survive a configuration change.

Rationale: `showRoutePanel` is screen state, so the screen is the right owner of "the panel is gone"; the
dismiss call is idempotent (`endSession()` returns early when already `INACTIVE`).

### D8 — One owner for the phone's bottom band (found while running 5.1)

The 5.1 run (b) did not reproduce the change's own goal: the session card was composed, its state was
right (`anchor=COMPACT session=REVIEWING navActive=true steps=18 route=true`), and **nothing of it was
visible or tappable**. Two measurements settled it:

- the card's layout bounds were `left=0 top=2059 w=1080 h=341` — inside the band the navigation status
  card occupies (its tap regions start at y 2018);
- a temporary 24 dp red probe bar at the card's top (the only change in that build) drew **0** full-width
  pixels while navigation ran, and the same probe drew a full-width band (`rows 2059-2121`, 1080 px per
  row) once the status card was suppressed. The panel's nodes were absent from the `uiautomator` dump in
the same state and present afterwards — one cause, not two.

`MapCanvasScreen` composes the session card *before* the navigation overlays, so the status card (same
bottom edge, comparable height) drew over it. Decision: **the session's card owns the band while it is
shown** — the status card is not composed then (`if (!state.showRoutePanel)`), and returns when the
session's surface closes (the screen's `overlayBottomInset` is now fed by whichever card is in the band).
Alternatives rejected: raising the card's z-order (keeps two stop controls in the tree and leaves the
occluded one to the accessibility/tap-consumer rules), and opening the session EXPANDED while navigating
(the card's action band sits at its bottom, so the actions would stay behind the status card).

Second gap from the same run: a session opened during navigation lands COMPACT (`openSession`), and the
compact strip rendered the step navigator plus End — **no Stop Navigation**, which the review's
requirement demands. The review's Stop is now one composable used by both phone frames (`ReviewStopAction`
— MAX's action band and the compact strip). The route summary stays a MAX-only element by the anchor
design (D10 in `route-planning-session`), which the run confirmed as reachable via the strip's step name
(`analysis stays available`).

Verified after the fix on the same device: the review's nodes are in the dump (`Nächster Schritt`,
`Vorheriger Schritt`, `2 / 18`, the step name, `Navigation beenden`), the status card is absent while the
session is open and back after it closed, the review's Stop ends navigation (`stopNavigation: stopped`),
the stopped state offers `Navigation fortsetzen` and End, Restart logs `start: started vehicle=CAR` with no
recalculation, and the grace expiry (`session grace period expired - ending the session`) leaves no card.

### Threading and lifecycle

No new component, dispatcher, scope or lifecycle owner. The stop callback is a Compose click handler on
the main thread; `RoutePanelViewModel`'s grace job stays on `viewModelScope`; the adapter's collector
stays where it is. All state read is main-thread confined (`guidelines/Design.md` §4). No native call, no
persisted field, no new Hilt binding — so no `:app` Gradle configuration change.

## Risks / Trade-offs

- **The adapter's end branch is easy to miss.** A fix in `MapCanvasScreen` alone leaves
  `NavigationViewModel.kt:96-99` hiding the route on every stop path. The design names it as part of the
  change; a case asserts the adapter keeps the route while the session is `STOPPED`.
- **The shade's stop now leaves the route and a Restart/End card when a session is open.** Intended by
  `route-planning-session`, but a visible behaviour change for a driver who stops from the notification;
  the on-device run covers it and the spec delta records it.
- **Not clearing the panel route during navigation narrows what the long-press entry can do.** While
  navigating, the session no longer plans a new route (it reviews the active one). This is the spec's
  read-only rule; if the owner wants route planning during navigation, that is a separate change against
  `route-planning-session`.
- **The mode diagnosis may fail to reproduce.** Then the change records the measurement, keeps the mode
  code unchanged, and the mode scenario stays a test-only addition — stated as the outcome rather than
  implied as a fix.
- **Robolectric cannot prove device mode or pointer behaviour.** The Compose/unit cases assert state,
  callback identity and bounds; the device run settles the mode and the session transition. The
  verification section states which claim rests on which.
- **The stopped state's Restart must not recalculate.** `NavigationViewModel.start(routeEntry, vehicle)`
  starts on the given route; if it ever routed again, the driver would wait for a calculation they already
  paid for. The case asserts navigation starts on the same `RouteEntry`.
- **The session-end observation must not close a panel the user just opened.** The observer fires only on a
  transition into `INACTIVE` while the panel is shown, never on `INACTIVE` staying `INACTIVE`; the case set
  covers opening a session, ending it by hand and letting the grace expire.
- **`RouteState` keyed rendering.** Moving the action row out of the `Done` branch must not make Start
  Navigation appear in a read-only review; the case set covers both directions (navigating → Stop only;
  not navigating → Start only).

## Verification

- **Unit / ViewModel** (`app/src/test/java/com/naviveylin/ui/route/RoutePanelViewModelSessionTest.kt`,
  `app/src/test/java/com/naviveylin/navigation/`): opening a session while navigating keeps the adopted
  route and enters `REVIEWING`; the card's stop with a session open enters `STOPPED` and the grace ends
  it after `GRACE_PERIOD_MS`; with no session open the stop does not enter `STOPPED`; the adapter's end
  branch keeps the route visible while the session is `STOPPED` and hides it when no session is open.
  Each new invariant gets one revert-check (single mutation, the named case must fail, restore, forced
  green with `--rerun-tasks`) per the `revert-check` skill.
- **Compose** (`RoutePanelComposeTest`): during navigation with an adopted route the review shows the
  route summary, the step navigator and a Stop Navigation action, and the location fields are not
  editable; without navigation the same panel shows Start Navigation and the editable fields; in the
  stopped state the panel shows Restart and End and offers neither Start nor calculate. Assert
  `getBoundsInRoot()`/`assertExists()` geometry, never pixel colours (Robolectric's shadow canvas
  discards `drawBitmap`, `TODO.md` §40.21).
- **On device** (`guidelines/Build.md` §10 recipe, `emulator-5554`, phone): D1's three runs (clean build,
  fresh UI dump per run, node identity + bounds): (a) start navigation, tap the card's stop control with
  **no** session open → `start_free_drive` afterwards and the route cleared; (b) open the session during
  navigation → the dump shows the review's Stop Navigation and the step entries; tap it → the session
  shows Restart/End, the route stays drawn, and `RoutePanelVM: session grace period expired` appears
  ~45 s later; (c) before navigation in BROWSE, start, stop from the card → the Drive-control label is
  `start_free_drive` again. Numbers only (label text, node identity, bounds, presence/absence of the
  grace line), never coordinates.
- **Not a pixel property**: the claimed properties are session state, callback identity and map mode;
  `tools/measure-highlight.py` finds the analysed-segment casing colour, not a mode or a session state,
  so its verdict is explicitly not applicable here.
