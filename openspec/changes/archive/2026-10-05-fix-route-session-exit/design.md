# Design — fix-route-session-exit

## Context

See `proposal.md` — Why. The shaping facts:

- Three `RouteOverlayAnchor` values exist (`EXPANDED`, `COMPACT`, `HIDDEN`, `RoutePanelViewModel.kt:77`)
  and the anchor branch sits **outside** the wide/phone split (`MapCanvasScreen.kt:2056-2075`), so the
  hidden anchor's pill is reachable in the docked (landscape/tablet) layout too: its header close
  control calls the same `setOverlayAnchor(HIDDEN)`.
- The session state (`_sessionState`) and the surface flag (`MapCanvasUiState.showRoutePanel`) have
  **different owners**: the view model owns the session, the map screen's view model owns the flag.
  Nothing today links "session ended" to "surface closed" — that link is the defect.
- The card reports its covered height through `onOverlayHeightChanged` → `setOverlayCoveredPx`, and the
  height is only reset to 0 by the branch we are deleting (`LaunchedEffect(Unit)` in the hidden
  branch, `MapCanvasScreen.kt:2061`).
- The route-planning session is pure UI/Kotlin: `StateFlow` + Compose on the main thread, no
  dispatcher, no native call, no persistence. Nothing in this change touches the JNI bridge, the
  libosmscout submodule or `:auto`.
- `guidelines/UI.md` §537 already documents **two** card heights while the spec demands three anchors —
  the guideline is the target state, the spec is what this change moves.

## Goals / Non-Goals

**Goals**

- One meaning for the card's close glyph, and a session surface that cannot outlive its session.
- A stated, tested contract for "session ended ⇒ no surface, covered height 0" — the invariant whose
  absence let the trap ship.
- The smallest surface area that removes the trap: no new state machine, no new mode, no new class.

**Non-Goals**

- No redesign of what the card shows (list, actions, step navigator, field collapse) — the anchors
  keep their content.
- No change to the session's camera lease, the grace period, the docked panel's content or parity with
  Android Auto.
- No Android Auto / AAOS change: the car has no phone card and no `RouteOverlayAnchor`.

## Decisions

### D1. Remove the hidden anchor instead of giving it an exit (chosen)

Two anchors, `EXPANDED` and `COMPACT`; `RouteReadyPill`, its test and the `route_overlay_minimize`
string are deleted.

Alternatives:

1. *Keep the hidden anchor, add a close control to the pill and re-enable back there.* Smaller spec
   churn, but keeps a third state whose only purpose is the affordance that traps; the same glyph would
   still mean "minimize" in max and "end" on the pill; and the state would still be reachable while no
   route exists (nothing to minimize to), which is where the pill degenerates into a "Route" label with
   no content. Rejected on owner directive and on state count.
2. *Keep the anchor but never compose it on the phone (dock-only).* Rejected: the anchor branch is
   shared, so this needs the same branch surgery without removing the confusing state.
3. *(chosen)* Two anchors: the card is the whole phone surface, min already leaves 82 % of the screen
   free (18 % cap), and a session that wants even more map must end — which is exactly the flow the
   owner asked for.

Consequence: `RouteOverlayAnchor` loses `HIDDEN`; `RoutePanelUiState.overlayAnchor`'s default
(`RoutePanelViewModel.kt:142`, currently `HIDDEN` with a "session whose…" comment) becomes `COMPACT`,
the anchor `openSession()` already sets; `phoneCardHeightDp` loses its `HIDDEN -> 0f` branch.

Spec mechanics: because the schema forbids dropping a scenario inside a MODIFIED requirement
(`openspec validate` fails with "omits scenario(s) the current spec still has"), the single over-long
anchors requirement is REMOVED and replaced by two focused ADDED requirements — the two-anchor height
contract and the card-content/actions contract. Every surviving scenario keeps its name, so
traceability to the old requirement is preserved; only the hidden anchor's scenario goes, together with
the behaviour it described.

### D2. Explicit `onEndSession` callback, not an observation of `sessionState` (chosen)

`RoutePanel` gains `onEndSession: () -> Unit`; the screen passes `{ viewModel.dismissRoutePanel() }`,
exactly as it already does for `onStartNavigation`. The card's header close and the min End control
both call it; `endSession()` keeps clearing route + session state.

Alternatives:

1. *Observe `sessionState` in `MapCanvasViewModel` and hide the panel when it becomes `INACTIVE`.*
   Fewer call sites, but `openRoutePanelWithStart` sets `showRoutePanel = true` **and then** calls
   `openSession()` (`MapCanvasViewModel.kt:3098-3101`), so the collector would race the open: a
   `INACTIVE` emission (initial value, or `onNavigationStarted` at the moment navigation starts) could
   close a panel the same turn opened. It also couples two owners through a stream whose ordering is
   implicit. Rejected.
2. *Have `dismissRoutePanel()` remain the only closer and let the card call a `RoutePanelViewModel`
   function that closes both.* The panel view model has no reference to `MapCanvasUiState`; giving it
   one inverts the dependency. Rejected.
3. *(chosen)* The card asks the screen to close, the screen closes its own flag and ends the session —
   one direction of dependency, no ordering assumption, and the callback doubles as the test seam for
   the missing screen-level assertion.

`onStartNavigation` keeps its current shape (start navigation, then `dismissRoutePanel()`): its
"close" is not a session cancel and must not go through the same entry point.

### D3. The header's close is the End action in both anchors (chosen)

The header control stops calling `setOverlayAnchor(HIDDEN)` and calls `onEndSession`; its content
description uses the End wording (`route_end_session`), and the collapse control keeps `^`/`v` with
`route_overlay_expand`/`route_overlay_collapse`.

Alternatives:

1. *Keep "Minimize" on the header and rely on the labelled "End analysis" button.* That is today's
   state, and it is what the owner read as "no exit": the close glyph looks like the exit and is not
   one. Rejected.
2. *Drop the labelled "End analysis" button and let the X be the only exit.* Smaller card, but the
   spec's explicit labelled End action in both states is the discoverable affordance, and an icon-only
   exit on a card that also has a collapse icon is exactly the ambiguity being fixed. Rejected — both
   stay, same outcome, two affordances.

Collapsing to min stays available: the `^`/`v` toggle and the step-name tap in min (spec `route-panel-session`
— anchors), so "I can switch between min and max" keeps working after the X becomes the exit.

### D4. The covered height returns to zero when the card leaves composition (chosen)

A `DisposableEffect(Unit)` in `RoutePanel` reports `onOverlayHeightChanged(0)` in `onDispose`, replacing
the deleted hidden branch's reset. It fires for every path that removes the card — header close, min
End, system back, Start Navigation, grace expiry closing the surface — without each exit having to
remember it.

Alternative: reset `overlayCoveredPx` explicitly inside `dismissRoutePanel()` / the end wiring. Works,
but it is one more place per exit path and it was the shape that drifted before (the reset lived in the
branch that hid the card). `DisposableEffect` ties the value to the card's actual presence, which is
the invariant (`route-map-overview`: "Ending the session frees the whole map without moving the
camera").

### D5. Back is captured while the card is shown (chosen)

The `BackHandler` loses its `overlayAnchor != HIDDEN` exemption (`MapCanvasScreen.kt:2050-2053`); with
two anchors the condition is `state.showRoutePanel` alone. The back gesture on the card is already the
session's cancel exit (spec `map-canvas-screen` — back dismisses the topmost overlay), and it now ends
the session through the same `dismissRoutePanel()` path as the card's own exits.

## Risks / Trade-offs

- **[Removing a state someone may want: the fully free map while a session stays open]** → min already
  leaves 82 % of the screen free; the whole-map case is now "end the session", which returns the user
  to browse without losing the viewport (spec `route-planning-session` — Session holds the camera: the
  session leaves the map where it left it). If a free-map-while-planning mode is wanted later, it must
  be re-introduced as a deliberate state **with** its own exit and a test for the exit — not as an
  affordance that only expands.
- **[Stale covered height after the card leaves composition leaves the map controls inset and the fit
  short]** → D4's `DisposableEffect` reset, asserted in the new screen-level test (covered height 0
  after the session ends).
- **[Test churn: three test files state the old behaviour]** (`RoutePanelComposeTest:254`,
  `RoutePanelOverlayAnchorTest.minimizeHandsTheMapOver` + `HIDDEN` assertions at `:82/:143/:346`,
  `RouteReadyPillTest` deleted) → the old assertions are replaced by the exit assertions, and each
  rewritten assertion is named in `tasks.md`; no test is left asserting a hidden anchor.
- **[The labelled "End analysis" button may not be visible at all on a device at large font scale]**
  (the owner's report is consistent with never having seen it) → the header close is the affordance that
  must carry the exit, so the trap is closed regardless; the button's visibility is *measured* in the
  device task (UI dump bounds of the action band at font scale 1.0 and 2.0) rather than assumed.
- **[`route-planning-session`'s anchors requirement is far longer than the 500-char hint]** → it is an
  existing requirement and the rule is not to trim existing text for length; the MODIFIED block keeps
  it whole and only removes the hidden-anchor sentences.
- **[Docked layout: the anchor toggle is a visual no-op there]** → out of scope, but recorded as an open
  question below and flagged for `TODO.md`, not silently changed here.

## Migration Plan

One commit in `:app`, no data or persisted state involved, no migration.

Order: callback + wiring (D2, D4) → header close (D3) → enum/anchor removal (D1) → `BackHandler` (D5) →
pill/string/import deletion → test rewrites + the new screen-level test → `guidelines/UI.md` §537.

Rollback: revert the commit; the hidden anchor, the pill and the "Minimize" semantics return unchanged
(no storage, no manifest, no bridge, so no partial-rollback hazard).

## Verification

- Unit/compose: the rewritten `RoutePanelOverlayAnchorTest` and `RoutePanelComposeTest` cases, the new
  screen-level case (`showRoutePanel == false`, route cleared, `sessionState == INACTIVE`,
  `sessionHoldsCamera() == false`, covered height 0 after ending from the card), and the existing
  `MapCanvasViewModelSessionLeaseTest` / `RouteSessionDismissOrderingTest` unchanged and green.
- Revert-check: drop the `onEndSession` wiring (the card's close only calls `endSession()`), the new
  screen-level case must fail with a card still composed; restore, forced green (`--rerun-tasks`).
- On-device measurement (phone): a route is calculated and the session ended with the card's close; the
  UI dump (`adb shell uiautomator dump` + `adb pull`) SHALL contain no `RouteReadyPill`/`Route` pill
  node and no card node, and the diagnostics covered-height line SHALL read 0. The action band's bounds
  are dumped at font scale 1.0 and 2.0 to record whether the labelled End action is visible at large
  scale (`device-check`), and the card screenshot is measured with `tools/measure-highlight.py`
  (`pixel-check`) for the band property — the numbers, not "looks right", are the evidence.
- Not provable on a stationary emulator: nothing in this change depends on motion; the measurement above
  is sufficient.

## Open Questions

- Should the docked wide panel hide the anchor toggle (a no-op there, since the anchor only sizes the
  phone card)? Deferrable: it changes no requirement this change touches, and it is recorded for
  `TODO.md` rather than folded into this scope.
- Should a future "full map while the session stays open" mode exist at all? Deferrable; D1's risk
  section states the condition (own exit + test) under which it may return.
