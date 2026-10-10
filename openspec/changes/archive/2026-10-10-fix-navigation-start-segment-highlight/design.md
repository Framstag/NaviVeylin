# Design: fix-navigation-start-segment-highlight

## Context

See `proposal.md` — Why for the defect, the root cause and the evidence. What shapes the approach is
where the analysis lives and who writes it:

| representation | declared | read by |
|---|---|---|
| `RoutePanelUiState.analysedStepIndex`, `.analysedSegment` | `RoutePanelViewModel.kt:147`, `:152` | the map overlay (`MapCanvasScreen.kt:1583` → `RouteSegmentHighlightOverlay`), the step list's marked row (`RouteSummary.kt:120-125`) and the min strip's navigator (`RoutePanel.kt:800-820`) |
| `_analysedSegmentRange`, `_analysedAnchor` | `RoutePanelViewModel.kt:222-223` | the map's step-focus collector (`MapCanvasViewModel.kt:3158-3170`) |

Five writers, and only two of them end the analysis:

| writer | ends it? |
|---|---|
| `analyseStep()` `:575-590` | creates, in that order (segment before anchor, the contract at `:583-589`) |
| `clearAnalysedStep()` `:593-597` | **yes** — the UI-state pair, the range and the anchor |
| `clearRoute()` `:856-874` | yes, by resetting the whole state |
| `onNavigationStarted()` `:744-751` | **no** — it nulls the range and the anchor and leaves the pair |
| `endSession()` `:729-737` | **only when not navigating** — `if (!_uiState.value.isNavigating) clearRoute()` skips it for a review ended during navigation |

A session in `INACTIVE` closes its surface (`MapCanvasScreen.kt:233-239`, `RoutePanel.kt:137-139`), so
after Start Navigation the highlighted segment is the only thing left on screen that a session owned.

Constraints: the analysis is deliberately **not** part of `RouteSessionState` (`RoutePanelViewModel.kt:66-69`
states why: it is an overlay on reviewing, and a second enum could contradict the selection). All the
writes are main-dispatcher writes inside `viewModelScope`; `setNavigating` is called from
`NavigationViewModel`'s collect (`NavigationViewModel.kt:109`). Nothing here is native or persisted.

## Goals / Non-Goals

**Goals**

- One rule with one home: the analysis ends whenever the session that owns it ends, by whichever exit.
- A route adopted with no session open carries no analysis, so no later event of the drive re-lights one.
- A step stays selectable during a navigation-time review, exactly as `route-planning-session` requires.

**Non-Goals**

- Not changing how the highlight is drawn, its palette or its band in the overlay stack (`UI.md` §11 lists
  the band; `MapCanvasScreen.kt:1570-1590` composes the layers). The defect is lifetime, not rendering.
- Not collapsing the two representations into one. That refactor would touch the camera's anchor/segment
  ordering contract (`:583-589`) and every reader listed above; this defect does not need it.
- Not moving the analysis into `RouteSessionState` — the existing comment's reason still holds.
- Not changing the auto-analysis of a *fresh calculation* while its session is open, and not touching the
  car variant, which reads neither field.

## Decisions

### D1 — End the analysis in one helper, and call it from every session exit

`onNavigationStarted()` and `endSession()` end the analysis through the same private body
`clearAnalysedStep()` already has: the UI-state pair, `_analysedSegmentRange`, `_analysedAnchor`. The public
`clearAnalysedStep()` keeps its meaning (the user cleared the selection); the exits call the same private
helper, so the next exit cannot clear half of it again.

Alternatives, and why the evidence eliminates each:

| option | why it is impossible |
|---|---|
| Fix `onNavigationStarted()` only | The reroute path re-lights the mark: `NavigationViewModel.kt:112-116` calls `adoptRoute` on every `engine.acquiredRoute` change, and a review ended during navigation keeps the pair too. The same report would return on the next drive with a reroute. |
| Gate the overlay's drawing on `isNavigating` (or on the session state) in `MapCanvasScreen` | Leaves the pair set, which spec `route-analysis`'s "no step SHALL be analysed" forbids, and the panel's own readers (marked row, navigator) would keep showing a selection nothing owns. It also moves one rule to a second place. |
| Derive the highlight from the flows the camera already reads (`analysedSegment ?: analysedSegmentRange`, `MapCanvasViewModel.kt:3170`) and have the map republish it | The overlay and the row read panel state, not the map's; this adds a third writer for one signal and inverts the reading order the map VM documents. |
| Derive instead of clear: keep the pair and render the highlight only while a session is open | The analysis would still be in the state the spec says must hold none, and `_analysedAnchor` is a one-shot event that cannot be derived from "a session is open" — a re-open would need a synthetic anchor. |

### D2 — `adoptRoute()` creates an analysis only while a session is open

An adoption publishes `routeEntry`, `routeSteps`, `stepAnchors` and the geometry unchanged; the
`analysedStepIndex`/`analysedSegment` pair is written only when `_sessionState` is not `INACTIVE`.

| option | why it is impossible |
|---|---|
| Keep today's write and rely on the next session exit to clear it | There is no next exit: with no session open there is nothing to end, which is exactly the reported symptom surviving the D1 fix. |
| Drop the analysis from `adoptRoute()` unconditionally | An adoption during an open review must leave the review usable, and the previous route's step indices do not address the new route, so the selection cannot simply be preserved. `RoutePanelViewModelStepAnalysisTest.kt:330` pins this behaviour. |
| Pass the caller's session state into `adoptRoute()` as a parameter | The panel already owns that fact in `_sessionState`; a parameter is a second place for it to be wrong, and the caller (`NavigationViewModel.kt:115`) would have to learn the panel's session rules. |

### D3 — The visual claim is settled by a measurement, not by looking

The highlight's presence is already instrumented: the overlay logs `range=… bboxPx=[…]` under the
`RouteHighlight` tag whenever the drawn box changes (`RouteSegmentHighlightOverlay.kt:93-115`), pixels and
a range only — no coordinate, per `guidelines/Logging.md` §3 and `Regulatory.md` §9. On device the check is
two-fold: after Start Navigation no further box may be logged, and `tools/measure-highlight.py`'s verdict
on the frame must be its no-highlight one; with a session open and a step analysed it must report the
highlight inside the free map area. "The map looks right" is not a verification.

## Risks / Trade-offs

- **A session opened during navigation after an unasked adoption lands with nothing analysed** → intended
  by the added requirement; the min strip's navigator coerces a null index to 0
  (`RoutePanel.kt:848-851`), so it still names a step and stays usable, and the camera stays with the
  navigation fit instead of jumping to a leg the user just asked to see. That coercion already contradicts
  `route-analysis`'s "With no step analysed the indicator SHALL name no step" on every path where nothing
  is analysed — today after a plain `clearAnalysedStep()` — so this change does not introduce it, and
  widening the delta to it is what the specs rule forbids; the change's bookkeeping records it as a
  follow-up instead.
- **The same code path that keeps the route for navigation also clears the analysis** → the route lives in
  `_routeVisible`/`_routeResultFlow` (`:518`, `:524-528`), untouched; `RoutePanelViewModelSessionTest.kt:259`
  guards it and gains the analysis assertions beside them.
- **Clearing on `endSession()` while navigating drops a review's selection the user may still want** →
  navigation shows no surface carrying it, and no analysis may exist without a session, so nothing visible
  is lost.
- **The analysis still has five writers** → the helper makes the ending one call, but nothing structurally
  forbids a sixth writer from forgetting it. The three exits are pinned by unit cases; a broader fix
  (folding the analysis into the session state) is deliberately out of scope above.

## Migration Plan

One commit in `app` plus two test files and one guideline sentence. No persisted state, no database, no
native artefact, no Gradle, manifest or resource change. Rollback is a revert of that commit: behaviour
returns to the highlight outliving its session, and nothing has to be migrated.

## Verification

- **Red on HEAD first** (fix-loop rule, `revert-check` for the mutation): in
  `RoutePanelViewModelStepAnalysisTest.kt` — `start navigation ends the analysed step with the session`
  (all four of `analysedStepIndex`, `analysedSegment`, `analysedSegmentRange`, `analysedAnchor` null),
  `ending a navigation-time review ends the analysed step and keeps the route`, and
  `a route adopted without a session carries no analysis`. Each asserts the state a spec scenario names.
- **Kept green:** `ending the session drops the analysed step with the route`,
  `clearing the route drops the analysed step and the anchors`, the rewritten
  `an adopted reroute starts at its first step with a leg` (it opens a session first), the whole of
  `RoutePanelViewModelSessionTest.kt`, and `MapCanvasViewModelStepFocusTest.kt:166`, `:209`, which read
  `analysedSegmentRange`.
- **On device (phone):** plan a route, tap Start Navigation → `adb logcat -s RouteHighlight` logs no box
  after the tap and `tools/measure-highlight.py` reports no highlight on the frame, while the route stays
  drawn. Then open the session during the drive and analyse a step → a box is logged and the verdict is
  inside. A reroute during that drive keeps the mark only while the session is open.
- No GPX replay is needed: the reroute path is exercised by the adoption case, and its on-device
  confirmation is one reroute during a drive.

## Threading and lifecycle

Unchanged, per `guidelines/Design.md` §4: every write stays inside the panel's existing `viewModelScope`
on the main dispatcher, `setNavigating` keeps arriving from `NavigationViewModel`'s collect
(`NavigationViewModel.kt:109`), no new coroutine, dispatcher, flow or lifecycle owner is introduced, and no
native call is added or moved.

## Open Questions

None: the one question the proposal left open — whether a step may still be analysed while navigation is
active — is answered by `route-planning-session`'s existing scenario "Analysis stays available during
navigation", so it changed the specs, not a deferral.
