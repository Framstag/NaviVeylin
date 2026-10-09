# Design

## Context

`MapCanvasScreen` builds the phone chrome as one band (`MapLayer.CHROME`, `MapCanvasScreen.kt:1663`).
Inside it the band composes **one** right-side widget column, from three mutually exclusive branches:
landscape browse (`:1707`), portrait browse (`:1847`), navigation (`:2043`). `MapRightWidgetColumn`
publishes its measured width through `LocalOverlayWidthProbe` (`staticCompositionLocalOf`, default
no-op, `:3081`, read at `:3137`), the band's lambda writes it into the remembered `overlayRightInset`
(`:393`), and `LaunchedEffect(navState.isNavigating, …insets…)` pushes it with
`viewModel.setMapOverlayInsets(top, bottom, right)` (`:2230-2235`). The ViewModel resolves the active
preset against those bands (`MapCanvasViewModel.publishResolvedAnchor` → `core/VehicleAnchor.kt`
`resolveAnchorFraction`) and publishes `uiState.resolvedAnchor`, which the render target, the marker
projection and the follow blit offset all consume (`openspec/specs/smooth-follow`, "Single resolved
anchor across render, blit and marker"; `guidelines/MapRendering.md` §1.1).

On HEAD the provider wraps the browse `BoxWithConstraints` only (`:1671-1673` closes at `:1966`), so
the navigation branch at `:2043` — the band's own copy of the same column — reads the default no-op.

Measured on the host (window `w411dp-h891dp`), both anchors `MIDDLE_FAR_RIGHT`, follow mode with a GPS
fix, printing the resolved fraction:

| path | `overlayRightInset` source | `resolvedAnchor.fx` |
|---|---|---|
| browse band, then navigation — **phase-A scratch probe, not retained** | the browsing column's last width, stale across the mode switch | `0.7773722627737226` |
| band composed while navigating (car-session resume) | nothing — the navigation column is outside the provider | `0.9` = the raw preset |

The second row is the `system-out` of `MapNavColumnWidthProbeTest`'s first case (RED run on HEAD: XML
`tests="2"` `failures="1"`, `timestamp="2026-10-09T20:05:03.852Z"`, copied to
`evidence/TEST-MapNavColumnWidthProbeTest-red-on-HEAD.xml`). The **first row is not from that XML**: it
is the phase-A scratch-probe measurement, the same run that first showed the browsing band's last width
surviving the mode switch, and it is **not retained** — see the paragraph below.

`0.9` is the preset's own fraction: with no right band measured, `resolveAnchorFraction` returns the
preset unchanged, so the vehicle marker lands under the compass / speed / zoom column.

The two branches do **not** measure the same column, which matters for the stale-value path: the
post-fix rows print the covered band as a fraction of the window (`bandWidthFraction browse=0.13625304136253047 navigating=0.15571776155717765`, the covered preset's fraction inverted through
`resolveAnchorFraction`'s 10 % visible-extent margin) — **56 px for the browsing column and 64 px for the
navigation one in the 411 px window**, because the navigation branch passes `reserveSpeedSlot = true`
(`:2043`) and so reserves the speed-limit slot the browsing branch does not. The browsing branch's stale
number is therefore not only stale but 8 px short. The HEAD row for that path (a surface that composed
in browse mode first, then started navigating: `resolvedAnchor.fx = 0.7773722627737226` while navigating,
i.e. the browsing 56 px) was measured in the phase-A probe run and is **not retained** (the probe file
was a scratch; the run left no XML) — re-creatable by composing the screen, starting navigation without
a car session and reading `resolvedAnchor.fx` (0.7773722627737226 on HEAD, 0.7598540145985402 after this
fix).

**Why this is not just "the entry's wording"**: a browse-first band keeps a stale-but-equal width,
because the same composable renders both branches — that path is invisible. The *reachable* defective
path is a band that composes with navigation already active, and it is specified behaviour, not a corner
case: a live car session disposes the phone canvas — `MapCanvasScreen.kt:246-252` returns before the
band's remembered inset state exists, and `guidelines/MapRendering.md`, section 18 / `guidelines/UI.md` §10a
state that nothing of the canvas survives the suspension — so *Show map here* recomposes the band in its
navigation branch with `overlayRightInset` back at 0 for the rest of the session.

## Goals / Non-Goals

**Goals**

- The chrome band measures the widget column in **every** mode it composes one in, so a right-edge
  preset resolves left of the column on a surface whose band composes during navigation — and where the
  browsing branch's stale value survived a mode switch, the navigating band now carries the navigation
  column's own (wider) measurement.
- A host case that fails on HEAD for the anchor fraction and passes after, with its numbers in the
  XML's `system-out`.

**Non-Goals**

- Not changing the collision rule, the presets, the `0.1..0.9` clamp, the inset publication, the band
  stack, the widget column's content or its placement.
- Not touching the car surface's own pane clamping (`clampAnchorOutOfPane`) or the Android Auto
  renderer.
- Not proving the driver-visible framing on a device: the pixel position of the marker against the
  column's dumped bounds at both orientations stays a `pixel-check` follow-up task (`TODO.md` §151's own
  verification proposal), never implied done here.

## Decisions

### D1: the provider moves from the browse branch to the band

**Chosen**: the `CompositionLocalProvider` that publishes `LocalOverlayWidthProbe` is provided **once
around the chrome band's content** instead of around the browse `BoxWithConstraints` inside it. The
three column call sites (`:1707`, `:1847`, `:2043`) are branches of the same band and only one of them
is composed at a time, so widening the scope cannot double-publish, and the inset keeps exactly one
writer. The band's content keeps its current indentation — the file already places the provider's body
at the same depth as the provider (`:1671-1674`), so the move is the provider block plus its closing
brace, not a re-indent of ~520 lines.

*Alternatives eliminated by the evidence*

- **Leave it and rely on the browse-first stale width**: refuted by measurement — the two rows of the
  table differ only in *how* the band entered composition, and the car-session resume path
  (`MapCanvasScreen.kt:246-252`, `guidelines/MapRendering.md`, section 18) composes the band with no remembered
  inset at all, so the driver's phone loses the clearance for the whole session.
- **Provide the probe twice, once per branch** (a second provider around the navigation block only):
  the same lambda written twice, and "which provider owns `overlayRightInset`" becomes a question a
  reader has to answer by counting; `guidelines/UI.md`, section 11 band rule is one contract per band, not per
  branch.
- **Pass the width as a parameter through the column's call sites** (`onWidthChanged: (Int) -> Unit`
  instead of a composition local): three call sites plus the shared composable's signature change, for a
  value the band already owns; it also re-opens the class of defect this fix closes — a new branch that
  forgets the parameter fails silently in the same way, where the band-wide provider covers every branch
  by construction.
- **Reset `overlayRightInset` to 0 on mode change so the stale value cannot masquerade as a
  measurement**: it makes a browse-first band *worse* for the rest of the navigation (the anchor jumps
  under the column until something re-publishes) and leaves the actual defect — the navigation column
  never publishing — in place.
- **Publish the column's width from the ViewModel side** (compute the column's size without the
  composition local): the width is a layout fact of a composable the band composes and the screen
  already has the writer (`setMapOverlayInsets`); a second source of the number is the duplicate-writer
  class `guidelines/Design.md`, section 12 rejects.

## Risks / Trade-offs

- **Framing change during navigation (intended)**: the navigation band's right inset becomes the
  navigation column's own measured width — `0.15571776155717765` of the window (64 px of 411) — where
  before it was 0 on a band composed during navigation (`fx` resolved `0.9` → `0.7598540145985402`) and
  the browsing column's stale 56 px on a browse-first band (`fx` `0.7773722627737226` →
  `0.7598540145985402`, an 8 px under-reservation of the wider navigation column). That is the fix; the
  pixel check on a device stays a follow-up.
- **Two branches, one local**: the provider is a `staticCompositionLocalOf`, read only by
  `MapRightWidgetColumn` (`:3137`) — `grep` finds no other reader, so widening its scope cannot change
  any other composable's behaviour.
- **The Compose test's clock**: `MapCanvasScreen`'s frame loop (`:578`) means the screen's composition
  never becomes idle, so the new case drives `mainClock` by hand and reads ViewModel state rather than
  querying nodes. It is the real screen
  — the strongest host proof available — but it is not the `waitForIdle` idiom the band harnesses use,
  which the case's own KDoc states.
- **No test expectation moves**: no test asserts the provider's scope, the navigation right inset, or
  `0.777…`/`0.9` for this window; the neighbouring collision cases inject their insets directly
  (`MapCanvasViewModelVehicleAnchorTest#overlayInsetsResolveTheAnchorIntoTheVisibleArea`) and are
  untouched.
