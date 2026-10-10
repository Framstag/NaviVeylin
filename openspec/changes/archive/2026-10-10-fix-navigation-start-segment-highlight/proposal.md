# Proposal

## Why

Starting turn-by-turn navigation on the phone leaves the analysed step's cyan segment **drawn on the
route's first leg for the whole drive**. Reported on device by the owner on 2026-10-10: "If I start the
navigation on phone the first segment is still marked … on start of the routing there must not be any
highlighted segments."

**Root cause — the analysis is two writes with two readers, and every session exit except `clearRoute()`
clears only one of them.** The chain, with the evidence for each link:

1. A step's analysis lives in two places. `RoutePanelUiState.analysedStepIndex` / `.analysedSegment`
   (`RoutePanelViewModel.kt:127-152`) are the **UI state** the map overlay is fed from, and
   `_analysedSegmentRange` / `_analysedAnchor` (`:222-223`) are the **flows** the map camera fit reacts
   to. `MapCanvasScreen.kt:1583` passes `routeState.analysedSegment` — the UI-state field — into
   `RouteSegmentHighlightOverlay`, which draws whenever that range is non-null
   (`RouteSegmentHighlightOverlay.kt:76-80`). The flows are not its input: `MapCanvasViewModel.kt:3170`
   reads `analysedSegment ?: analysedSegmentRange` only for the camera.
2. `onNavigationStarted()` (`RoutePanelViewModel.kt:744-751`) sets `isNavigating`, nulls
   `_analysedAnchor`, nulls `_analysedSegmentRange`, and sets the session `INACTIVE` — and leaves
   `uiState.analysedStepIndex` / `analysedSegment` untouched. Its own comment claims otherwise: "the
   analysis is over with the session (spec: route-analysis)".
3. The two exits that *do* clear the pair go through `clearAnalysedStep()` (`:593-597`) or reset the whole
   state in `clearRoute()` (`:856-874`). `endSession()` (`:729-737`) skips `clearRoute()` when
   `isNavigating`, so ending a review **during** navigation leaves the pair set as well — the second
   instance of the same omission, reachable by a second user gesture.
4. Why it is the *first* segment: a calculation (`:495-503`) and an adoption (`:833-841`) both analyse
   `currentStepOf(steps)` — the first step carrying leg values — and `segmentOf()` (`:696-705`) resolves
   that step to the polyline range that *leads to* its manoeuvre, which for the first step with a leg
   starts at polyline vertex 0. So the stuck range is the route's opening leg.
5. The same field is written while driving: `NavigationViewModel.kt:112-116` calls `adoptRoute` whenever
   `engine.acquiredRoute` changes, so **a reroute mid-drive re-lights the highlight** — and, with a
   review open, replaces the user's selection. Fixing step 2 alone would report the defect again on any
   drive with a reroute.
6. The card itself is not the leftover: `MapCanvasScreen.kt:233-239` closes the surface as soon as the
   session is `INACTIVE` (`RoutePanel.kt:137-139`), so during navigation the highlighted segment is the
   only mark left on the map.

**Test gap (the case that would have caught it).**
`RoutePanelViewModelStepAnalysisTest.kt:182` pins "ending the session drops the analysed step with the
route" and `:317` pins "clearing the route drops the analysed step and the anchors" — both for exits that
route through `endSession()` on an idle map or through `clearRoute()`. No case exercises
`setNavigating(true)`, and `RoutePanelViewModelSessionTest.kt:259` ("start navigation ends the session but
keeps the route drawn") asserts the session state and the kept route and asserts nothing about the
analysis. `RoutePanelViewModelStepAnalysisTest.kt:330` pins the adopted-reroute auto-analysis, which is
the write that has to become session-conditional. No case is red on HEAD yet; task 1 of this change writes
the three.

**Repro.** Plan a route, tap Start Navigation: the session ends, the card disappears, and the cyan
segment stays on the opening leg (`adb logcat -s RouteHighlight` prints the range once as the frame is
first drawn and never prints a cleared box, because the overlay only logs on change —
`RouteSegmentHighlightOverlay.kt:93-115`).

**The defect contradicts** spec `route-analysis`:

- "Highlight removed with the selection" (`openspec/specs/route-analysis/spec.md:150`): *WHEN a step's
  segment is highlighted AND the selection is cleared, the route is cleared, or the session ends THEN the
  highlighted segment SHALL no longer be drawn.* Start Navigation ends the session on those very lines
  (`RoutePanelViewModel.kt:750`), so the highlight must be gone.
- "Selection cleared on route change" (`spec.md:48`): the same three triggers, with *no step SHALL be
  analysed*.
- and `route-planning-session`, "Session lifetime and its only exits" (`spec.md:8`), scenario "Start
  navigation ends the session" (`spec.md:17`).

**Single resulting fix.** One rule, applied where the analysis is written: **an analysis exists exactly as
long as the session that owns it.** Ending the session ends the analysis, whichever exit ends it (Start
Navigation, End/Cancel during navigation); a route adopted while no session is open adopts its geometry
and steps but creates no analysis. One root cause — the UI-state half of the analysis is not part of what
a session exit clears — one fix at the three write sites that carry the omission, host-decidable, no
device needed: the entry is loop-eligible.

**Alternatives are eliminated rather than weighed.** A stricter "never highlighted while navigating" is
*contradicted* by `route-planning-session`, "Reviewing a route during navigation is read-only"
(`spec.md:187`), scenario "Analysis stays available during navigation" (`spec.md:197`): with the session
open during navigation "a step SHALL be analysable", and analysing a step is defined as highlighting its
segment. Clearing only the Start-Navigation path is eliminated by evidence 5: the reroute that follows
within minutes restores the mark. Making the highlight follow the driving step
(`NavigationViewModel.kt:120`'s `activeStepIndex`) is eliminated by the owner's own statement — no mark
when routing starts — and by nothing in the specs asking for it. Gating the *drawing* in the overlay on
`isNavigating` instead of ending the analysis is eliminated because the analysis would then still be
stale in the panel's state, which is what the same spec's "no step SHALL be analysed" forbids.

## What Changes

- `RoutePanelViewModel.onNavigationStarted()` ends the analysis with the session: the UI-state pair
  (`analysedStepIndex`, `analysedSegment`) goes the way of the flows and the anchor, through the same
  clear as `clearAnalysedStep()`.
- `RoutePanelViewModel.endSession()` ends the analysis on **both** of its paths. Today it clears the route
  (and with it the analysis) only when navigation is not running; a session ended while navigating keeps
  the route — correctly — and now drops the analysis alone.
- `RoutePanelViewModel.adoptRoute()` creates an analysis only while a session is open. An adoption that no
  session asked for — the reroute with no surface, or a car-only start observed on the phone — still
  publishes `routeEntry`, `routeSteps`, `stepAnchors` and the geometry, and leaves the analysis empty. An
  adoption during a review keeps today's behaviour (the new route's current step), because the step
  indices of the previous route no longer address the route being shown.
- The invariant gets one home instead of four call sites: a single private helper that ends the analysis
  (UI-state pair + `_analysedSegmentRange` + `_analysedAnchor`), called by every session exit, so the next
  exit cannot clear half of it again.
- Tests: a case per trigger the deltas name — Start Navigation, End during navigation, End while idle
  (unchanged, must stay green), an adoption without a session (no analysis published, route adopted), an
  adoption with a session open (today's behaviour), and a selection made in a review during navigation
  still highlighting. The existing `RoutePanelViewModelStepAnalysisTest.kt:330` case is rewritten to name
  the session state it needs.
- `guidelines/UI.md` §7a ("Route planning holds the camera") states the rule it is missing: the analysis,
  its camera anchor and its highlighted segment live exactly as long as the session, and the session's
  exits above all mean Start Navigation.

Not changing: the session's own exits, the anchors, the card's content, the camera fit, the overlay's
drawing or palette, and the auto-analysis of a freshly calculated route while its session is open
(`RoutePanelViewModel.kt:495-503` — the owner finding of 2026-10-03 that the min overlay shows a usable
entry from the start). The route stays drawn after Start Navigation, because navigation owns it
(`route-planning-session`, "Start Navigation ends the session and hands the map to navigation").

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `route-analysis`: "Highlight removed with the selection" and "Selection cleared on route change" gain the
  trigger this defect proved — a session that ends by **Start Navigation**, and one that ends during
  navigation, are "the session ends" — plus the rule that an analysis is created only by the session's own
  selector: a route adopted without an open session carries no analysed step. Each added scenario names the
  test case that exercises it.

- `route-planning-session`: "Session lifetime and its only exits" gains the consequence for both exits of
  the analysis the session owns; "Reviewing a route during navigation is read-only" keeps its "a step SHALL
  be analysable" and gains the one sentence that distinguishes it from the automatic analysis an unasked
  adoption must not create.

## Impact

Affected modules and files:

- `app` — `app/src/main/java/com/naviveylin/ui/route/RoutePanelViewModel.kt` (the ending helper,
  `onNavigationStarted`, `endSession`, `adoptRoute`). `app/src/main/java/com/naviveylin/ui/map/MapCanvasScreen.kt`
  and `MapCanvasViewModel.kt` need no change if the analysis is ended at the source; the design decides
  whether the overlay also gets a defensive guard, and its cost is one recomposition-free condition.
- Tests — `app/src/test/java/com/naviveylin/ui/route/RoutePanelViewModelStepAnalysisTest.kt` (the new
  red-on-HEAD cases and the rewritten adoption case), `RoutePanelViewModelSessionTest.kt` (the
  start-navigation case asserts the ended analysis too). No instrumentation, no device requirement: the
  rule is `RoutePanelViewModel` state, as the existing cases are.
- No manifest, resource, Gradle or dependency change. No native/JNI change: this is phone ViewModel state,
  so no submodule patch and no bridge-module override.
- Guidelines: `guidelines/UI.md` §7a owns the phone session card, its step navigator and the analysed
  segment ("Route planning holds the camera"); §11 ("Phone overlay layering", line 1161) lists the
  analysed-route highlight as a band and is unaffected. `guidelines/Design.md` §3/§4 own ViewModel state and
  threading; the fix adds one state write on the main dispatcher inside an existing function, so the
  threading model is unchanged.
- Scope: **phone only** (`app`). The car variant does not read this state: `analysedSegment` and
  `analysedStepIndex` have no reader outside `app/` (the car map's own renderer draws its route), so Android
  Auto and Android Automotive OS behaviour and templates are unchanged — no parity requirement is affected.
- Behaviour change, not breaking: no state shape, no JNI signature, no persisted data changes. The one
  visible difference is that a route adopted with no open session no longer arrives with a step selected;
  a session opened during navigation still selects the current step on calculation, and any step remains
  selectable. Rollback is a revert of one commit — nothing migrates, and no on-device or stored state
  depends on the analysis.
