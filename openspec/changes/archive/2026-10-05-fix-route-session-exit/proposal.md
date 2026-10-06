# Proposal — fix-route-session-exit

## Why

The phone route-planning session has no working negative exit. Owner finding on the phone,
2026-10-05: *"I can switch between min and max view, I can press x in both, but I cannot leave the
stateful navigation without starting a route. If I press x in the max variant I get a small 'Route'
overlay that I cannot close."*

Cause is visible in the code, no device instrumentation needed:

- The header's `X` in **max** is `routeOverlayMinimize` — `RoutePanel.kt:241` calls
  `setOverlayAnchor(RouteOverlayAnchor.HIDDEN)`. Only the labelled "End analysis" text button
  (`RoutePanel.kt:583`, `routeEndSession`) ends the session in max. In **min** the same glyph *is* the
  End action (`routeEndSessionCompact`, `RoutePanel.kt:843`). One glyph, two meanings.
- `RoutePanelViewModel.endSession()` (`:720-727`) clears the route, sets `_sessionState = INACTIVE`
  and sets the anchor to `HIDDEN` — but it never clears `MapCanvasUiState.showRoutePanel`. Only
  `MapCanvasViewModel.dismissRoutePanel()` (`:3109`) does, and nothing observes `sessionState` to hide
  the surface (`MapCanvasViewModel.kt:2991` reads it for the camera lease only). So **every** UI exit —
  the max "End analysis" button, the min `X`, a grace-period expiry with the card open — leaves the
  screen composing the `HIDDEN` branch (`MapCanvasScreen.kt:2057`) over a route that is already gone.
- The hidden anchor is a dead end: `RouteReadyPill` is `clickable(onExpand)` only, and the session's
  `BackHandler` is `enabled = … overlayAnchor != HIDDEN` (`MapCanvasScreen.kt:2050-2053`), so at the
  pill the back gesture is not captured and reaches the activity. Tapping the pill re-expands a
  **session-less** overlay (`INACTIVE`, route cleared), whose End action drops it back to the pill.
- The trap is unverified by tests: `RoutePanelComposeTest.theCardOffersAWayOutOfTheAnalysisInBothStates`
  (`:254`) *asserts* the buggy end state (`RouteOverlayAnchor.HIDDEN`) and never composes
  `MapCanvasScreen`; no test states the screen-level invariant "session ended ⇒ no session surface".

Spec and guideline already disagree about this state: `route-planning-session` demands **three**
anchors including a hidden pill, while `guidelines/UI.md` §537 documents **two** card heights
(max/min). The pill is the only reason the trap exists, and the owner directive is that it is not
needed: the flow should stop at the card and return to browse.

Doing this now: it removes a state, gives `X` one meaning, and closes the missing "the surface is gone
when the session is gone" contract before more exit paths (grace expiry, docked panel) are built on it.

## What Changes

- **The header's `X` ends the session in both card states.** In max it SHALL end the session (clear the
  route, return to browse) instead of minimising; min keeps its existing End control. Collapsing to min
  stays the `^`/`v` control and the step-name tap.
- **Ending the session removes its surface.** The card SHALL ask the screen to close as part of ending —
  a new `onEndSession` callback on `RoutePanel` wired to `MapCanvasViewModel.dismissRoutePanel()`, the
  same explicit pattern `onStartNavigation` already uses. No session, no card, no pill.
- **The hidden anchor and the route-ready pill are removed.** `RouteOverlayAnchor` loses `HIDDEN`;
  `RouteReadyPill.kt`, its test and the `route_overlay_minimize` string are deleted. The default
  `RoutePanelUiState.overlayAnchor` becomes the compact anchor (it currently defaults to `HIDDEN`
  "so a session whose…" and would be an invalid value), and `phoneCardHeightDp` loses its `HIDDEN`
  branch.
- **Back is captured whenever the card is shown** — the `overlayAnchor != HIDDEN` exemption in the
  session's `BackHandler` disappears with the anchor.
- **The labelled "End analysis" action stays** in max (spec: the explicit End action in both states);
  the header `X` becomes a second affordance for the same outcome, and its content description is the
  End wording, not "Minimize".
- **Spec deltas**: `route-planning-session` (the "Session overlay anchors" requirement — three anchors
  in one long paragraph — is removed and replaced by two focused requirements for the two-anchor card
  and its content, so the hidden affordance and its scenario go; the session's exits gain the header
  close as an End action; a new requirement states that ending the session removes the session
  surface), `route-panel-ui` ("Collapsing is not dismissing" drops "or hidden anchor", the close
  control is named as a dismissal) and `route-map-overview` ("A shrinking overlay leaves the camera
  alone" drops "or to the hidden pill").
- **Guideline**: `guidelines/UI.md` §537 (phone route-planning layout) records that the card's `X` ends
  the session and that there is no hidden anchor or pill; §7/§7a are unaffected.
- **Scope: phone only** (`:app`). The `:auto` module has its own screens and no phone card; Android
  Auto and AAOS are untouched. No API, storage, JNI, manifest, resource-id or dependency change —
  one string removal and one content description.
- **Additive** for behaviour (the only removed behaviour is the hidden anchor, which the spec change
  names). Rollback: revert the one commit; the hidden anchor, pill and minimize semantics return with
  it. No data or persisted state is involved, so no migration and no partial-rollback hazard.

## Capabilities

### New Capabilities

- (none)

### Modified Capabilities

- `route-planning-session`: the session's exits (MODIFIED — the header close is an End action, and
  ending the session removes its surface); the over-long "Session overlay anchors" requirement is
  REMOVED and replaced by two focused ADDED requirements, "Session overlay anchors (max and min)" and
  "Planning card content and its pinned actions" (three anchors → two; the hidden anchor and its
  scenario go with it); ADDED requirement — ending the session removes its surface (card, affordance
  and covered-height claim).
- `route-panel-ui`: the dismissal requirement — collapsing reaches compact only; no hidden anchor is a
  "not a dismissal" state.
- `route-map-overview`: the shrinking-overlay scenario — the shrink is the collapse to min, the hidden
  pill no longer exists as a height-change trigger.

## Impact

Phone route UI (`:app`):

- `app/src/main/java/com/naviveylin/ui/route/RoutePanelViewModel.kt` — `RouteOverlayAnchor` loses
  `HIDDEN` (`:77`), `RoutePanelUiState.overlayAnchor` default (`:142`), `endSession()` (`:720-727`, and
  its anchor reset), KDoc.
- `app/src/main/java/com/naviveylin/ui/route/RoutePanel.kt` — the header `X` (`:241`) becomes the
  End/close action, a new `onEndSession` parameter is invoked by it and by the min control
  (`StepNavigator`'s `onEndSession`, `:738/:843`), `phoneCardHeightDp` (`:130`) loses the `HIDDEN`
  branch, comments that describe minimising are rewritten.
- `app/src/main/java/com/naviveylin/ui/route/RouteReadyPill.kt` — **deleted**.
- `app/src/main/java/com/naviveylin/ui/map/MapCanvasScreen.kt` — `BackHandler` (`:2050-2053`) loses the
  anchor exemption, the `HIDDEN` branch and `RouteReadyPill` composition (`:2057-2075`) are removed,
  `RoutePanel(...)` gains `onEndSession = { viewModel.dismissRoutePanel() }` (`:2077-2101`).
- `app/src/main/res/values/strings.xml` — `route_overlay_minimize` removed; the `X`'s content
  description uses the existing `route_end_session` wording. German values keep their entries for the
  remaining strings.

Tests:

- `app/src/test/java/com/naviveylin/ui/route/RouteReadyPillTest.kt` — **deleted**.
- `app/src/test/java/com/naviveylin/ui/route/RoutePanelOverlayAnchorTest.kt` —
  `minimizeHandsTheMapOver` becomes the session-exit case (header `X` ends the session, anchor is not
  a hidden one); `RoutePanelOverlayAnchor.HIDDEN` assertions (`:82`, `:143`, `:346-348`) go.
- `app/src/test/java/com/naviveylin/ui/route/RoutePanelComposeTest.kt` — the end-state assertion
  (`:244-256`) changes to "the session ended and the close was requested"; the min exit stays covered.
- **New test (the missing one)**: a screen-level test that composing a route session and ending it from
  the card closes the surface — `MapCanvasUiState.showRoutePanel == false`, route cleared, session
  `INACTIVE`, `sessionHoldsCamera()` false. This is the assertion whose absence let the pill trap ship,
  and it is the revert-check target (drop the `onEndSession` wiring, this test must fail).
- `app/src/test/java/com/naviveylin/ui/map/MapCanvasViewModelSessionLeaseTest.kt` and
  `RouteSessionDismissOrderingTest.kt` — unchanged expectations; re-run to confirm.

Specs: `openspec/specs/route-planning-session/spec.md`, `route-panel-ui/spec.md`,
`route-map-overview/spec.md` (deltas in this change).

Guidelines: `guidelines/UI.md` §537 (one paragraph). No `Design.md`, `MapRendering.md`, `Build.md` or
`Regulatory.md` impact; no `AGENTS.md` change (it documents no route-panel rule).

Native/JNI: none — no submodule patch, no bridge change.

Verification: unit/compose tests above for both flavors, plus an on-device check that measures — not
looks at — the exit: a UI dump of the card's action band at font scale 1.0 and 2.0 to settle whether the
labelled End action is visible at all (if it clips at large font scale, the header `X` is the affordance
that must carry the exit), and the logcat sequence for a session end (`RoutePanelVM` session lines,
`Diag` overlay-height line back to 0) with no pill left on screen.
