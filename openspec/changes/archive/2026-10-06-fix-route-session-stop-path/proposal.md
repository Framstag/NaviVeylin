# Proposal

## Why

`TODO.md` §140 records two related defects on the phone, both found by device measurement on 2026-10-05
and 2026-10-06 and both left out of `fix-nav-overlay-stop-tap` (which fixed only the stop control's tap
geometry — archived 2026-10-06):

1. **Stopping navigation from the routing status card does not enter the session's stopped state.**
   `MapCanvasScreen.kt:2380-2382` (card) and `:2074-2076` (the session card) both call
   `navigationViewModel.stopNavigation()` + `routePanelViewModel.setNavigating(false)` +
   `routePanelViewModel.clearRouteFromMap()`. The route is cleared outright, no
   `RoutePanelVM: session grace period expired` line follows, and the map was measured in FREE_DRIVE
   although the pre-navigation mode was BROWSE. Two existing contracts are contradicted:
   `route-planning-session` — "Grace period after navigation is stopped" (route stays drawn, Restart
   and End offered, bounded grace) and `map-modes` — "Navigation end restores prior mode".
2. **A session opened during navigation is not the documented read-only review.** `openSession()`
   (`RoutePanelViewModel.kt:708-717`) sets `REVIEWING` while navigating, but the panel renders its
   action row — including Stop Navigation — only inside `RouteState.Done` (`RoutePanel.kt:578`), and a
   session opened during navigation carries no route of its own. The device dump showed the header,
   the two editable-looking location fields and the swap control, but **no** `Navigation beenden`
   button and no step navigator (`TODO.md` §140, second measurement). The spec says the review SHALL be
   read-only, SHALL offer the analysis of a step and SHALL offer Stop Navigation.

The two are one defect family: because the only reachable stop is the status card's, and that path
bypasses the session, `RoutePanelViewModel.onNavigationStopped()` → `RouteSessionState.STOPPED` plus its
45 s grace job have **no reachable UI path** on the current build. The stopped state and the read-only
review are therefore only reachable in states the UI does not produce.

Why now: the phone is the only surface where this is reachable (the car surfaces pass
`onStopNavigation = null` and have no planning session), the defect is user-visible (a driver who stops
navigation loses the route instead of getting Restart/End, and can land in the wrong map mode), and the
whole fix is in-tree Compose + ViewModel Kotlin — no submodule, no native code, no new dependency.

The cause of the **mode** half is explicitly *not* established (`TODO.md` §140: "one run, on a build
produced from a working tree that also carried other in-flight work … so the cause is not established.
Re-check on a clean build before scoping a fix"). This change therefore measures it first and fixes the
code path that the measurement proves; the session/grace half is established from code and device
evidence and does not depend on that diagnosis.

## What Changes

- **One stop path for every stop control.** The status card, the expanded details view and the session's
  own Stop Navigation route into a single phone stop callback. It ends navigation and then, if a session
  is open, moves it into the documented stopped state (route stays drawn, Restart and End offered,
  bounded grace); with no session open it clears the route from the map as today. No stop control
  clears the session behind the session's back anymore.
- **The map mode is restored on every stop path.** The pre-navigation mode snapshot/restore
  (`MapCanvasViewModel.kt:2886-2907`) is verified for the card's stop path on a clean build; if the
  measurement shows the snapshot is taken or restored wrongly for that path, the cause is fixed so all
  three stop paths land in the mode `map-modes` names (BROWSE for a browse-before-navigation user).
- **A session opened during navigation becomes the documented read-only review.** When navigation is
  active and the session has no route of its own, the panel presents the review from the shared
  navigation state (`NavigationState.instructions`, `routeLats`/`routeLons`, `totalDistance`,
  `remainingDistance`, `destinationName`): the route summary, the step navigator over the navigation's
  instructions, the read-only location line, and the Stop Navigation action. The location fields SHALL
  NOT accept input and the calculate/clear actions SHALL NOT be offered in that state.
- **The stopped state becomes reachable, observable and terminal.** Stopping navigation from either the card or
  the read-only review while a session is open enters `RouteSessionState.STOPPED`, the session surface renders
  the stopped state with Restart and End, and the grace period ends the session with the route cleared. The
  session's surface closes when the session ends by itself, so no card outlives its session. (Measured
  2026-10-06: the session already entered `STOPPED` and the 45 s grace already expired, but the stopped state had
  no UI — the card kept showing the editing content — and after the expiry the card stayed on screen with empty
  fields. Both are required by `route-planning-session` today, so this change makes existing requirements hold
  rather than adding new ones.)
- **Diagnosis first, with numbers.** Before any behaviour change, the card stop path is measured on a
  clean build of the attached emulator: the map mode before and after (Drive-control label), whether a
  session was open, the `NavigationEngine`/`RoutePanelVM` log lines, and the resulting session state.
  Coordinate-free only (mode name, session state, node identity/bounds, log lines) per
  `guidelines/Regulatory.md` §9.

Not in scope: the stop control's tap geometry and 48 dp hit area (`fix-nav-overlay-stop-tap`, archived);
the card's action-band overflow at font scale 2.0 (`TODO.md` §138); any car-surface change; the
`NavigationEngine`'s reroute-lease behaviour (`§126`); `LoadingScreen` dead code (`§127`).

**Spec status of the added scope**: the stopped state's Restart/End and the surface close on grace expiry are
**already** required by `route-planning-session` ("Grace period after navigation is stopped" — "the overlay
SHALL offer Restart and End"; "Ending the session removes its surface" — "Grace expiry closes an open
surface", "No surface survives the session"). No new requirement text is needed; the deltas in this change
only name the entry points and the review's source. The archive step must therefore check those existing
scenarios against this change's cases, not against a delta.

## Capabilities

### New Capabilities

None. The behaviour belongs to existing capabilities; this change makes them hold.

### Modified Capabilities

- `route-planning-session`: "Reviewing a route during navigation is read-only" gains the source rule
  (the review is built from the active navigation when the session has no route of its own) and the
  reachability of its Stop Navigation action; "Grace period after navigation is stopped" gains the
  entry points (the status card's stop control and the review's Stop Navigation both move an open
  session into the stopped state) and the boundary that stopping with no session open ends navigation
  without a grace.
- `map-modes`: "Map mode model" — the "Navigation end restores prior mode" contract gains a scenario that
  it holds for the status card's stop path, not only for the session's own stop.
- `navigation-status-details`: "Routing status card is clickable" — a tap inside the stop control's hit
  area SHALL end navigation through the one session-aware stop path, so an open session enters its
  stopped state instead of being cleared behind its back.

No capability is removed.

## Impact

**Affected modules/files** (all `:app`, phone UI + ViewModel; `:auto`, `:core` and the JNI module are
untouched)

- `app/src/main/java/com/naviveylin/ui/map/MapCanvasScreen.kt` — the card's stop callback (`:2380-2382`),
  the expanded details' stop callback (`:2074-2076`) and the panel's `onStopNavigation` argument
  (`:2074`) converge on one callback passed down as the session-aware stop.
- `app/src/main/java/com/naviveylin/ui/route/RoutePanel.kt` — the read-only review branch: the summary,
  the step navigator and the Stop Navigation action rendered from the navigation state when
  `navigationActive` and the session has no route; the fields' non-editable state; the stopped-state branch
  with Restart and End.
- `app/src/main/res/values/strings.xml` / `app/src/main/res/values-de/strings.xml` — the Restart label (and
  its German counterpart), unless an existing string already carries the meaning.
- `app/src/main/java/com/naviveylin/ui/map/MapCanvasScreen.kt` — the stopped state's Restart callback (start
  navigation on the session's route) and the observation that dismisses the panel when the session ends by
  itself.
- `app/src/main/java/com/naviveylin/ui/map/MapCanvasViewModel.kt` — the dismiss path used by that
  observation (or only the screen, if `dismissRoutePanel()` already covers it).
- `app/src/main/java/com/naviveylin/ui/route/RoutePanelViewModel.kt` — the review's route/steps sourced
  from the navigation state (`openSession`, a navigation-state input), and the stopped-state transition
  (`onNavigationStopped`, `startGracePeriod`) reachable from that surface.
- `app/src/main/java/com/naviveylin/ui/map/MapCanvasViewModel.kt` — only if the measurement shows the
  pre-navigation mode snapshot/restore (`:2886-2907`) is wrong for the card path; otherwise unchanged
  and the measurement is recorded.
- `app/src/test/java/com/naviveylin/ui/route/RoutePanelViewModelSessionTest.kt` (or a sibling) gains
  the review-source and stopped-state-entry cases; `RoutePanelComposeTest` gains the read-only review, the
  stopped state's Restart/End and the Stop-Navigation cases.
- `app/src/test/java/com/naviveylin/ui/navigation/NavigationStateOverlayComposeTest.kt` — the card's
  stop callback identity case (that it is the one session-aware callback).
- `app/src/test/java/com/naviveylin/ui/map/MapCanvasViewModelModeTest.kt` — the mode-restore case for the
  card's stop path alongside the existing ViewModel path.

**Android components**: no manifest, permission, resource, Service, `CarAppService`, template or AAOS
surface is touched. No new Hilt binding, no new dispatcher, no persistence change.

**Native/JNI**: none. No libosmscout submodule patch and no `:osmscout-client-java` override — the change
reads fields `NavigationState` already publishes (`instructions`, `routeLats`/`routeLons`,
`totalDistance`, `remainingDistance`, `destinationName`, `vehicle`). The `FakeOSMScoutClient` stubs and
the Robolectric classloader rule are unaffected; no `@Config` is added or removed.

**Guidelines referenced**: `guidelines/UI.md` §8 (phone overlay tap targets — the stop control stays the
48 dp disjoint control `fix-nav-overlay-stop-tap` introduced) and §3c (wait/notice rules, for the
stopped state's affordance); `guidelines/Design.md` §4 (state publication is main-thread confined — the
new panel state is derived from the existing `StateFlow`s) and §12 (single source of truth — one stop
path, one route-length source); `guidelines/Build.md` §2/§4/§10 (gate, revert-check discipline and the
on-device recipe). No guideline is superseded, so no guideline edit is required.

**Previous specifications changed**: `route-planning-session`, `map-modes`,
`navigation-status-details` (all deltas in this change's `specs/`).

**Change type and rollback**: additive, non-breaking — no public API, no persisted file, no state schema
and no user-visible label changes; the observable differences are that a stop with a session open lands
in the stopped state, that the review during navigation shows the route it reviews, and that the map
mode is the pre-navigation one. Rollback path: `git revert` of the change's commits; no submodule
gitlink and no `app/release-version.properties` state is involved.

**Scope**: phone only. The car surfaces deliberately carry no stop control and no planning session
(`navigation-status-details` — "The car card carries no stop control"); `:auto` is not modified and the
phone/car parity requirement does not apply to a surface the car does not have.
