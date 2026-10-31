# UIPhoneMap — why (rationale)

The rationale layer of `guidelines/UIPhoneMap.md`: the measurements a bound was arrived at, the folds that
produced a rule, the rejected alternatives and the history. Read beside the section its `## §N` anchor
names; it is not a document a route names.

## §7a The re-center button's thresholds and dwell

The BROWSE appear/hide thresholds are **screen pixels**, so the value means the same visible displacement
at every zoom. The dwell completes on elapsed time, not on the next state change, because at a standstill
the location provider goes silent (min-distance throttling) and nothing else emits — the shared
re-evaluation tick is what reveals the button with no further fix, pan or zoom. That is why the canvas
metrics are part of the observed state the rule is derived from: they are published by the screen's first
`onSizeChanged`, exactly like the viewport, so the first layout, a rotation or an unfold re-derives the
offset and arms the dwell just as a fix or a pan does.

## §7b The route-planning card's history

The third anchor and its route-ready pill existed until 2026-10-05 and were removed: the pill had no exit
of its own and back was not captured there, so it was a dead end — the card is now the whole phone surface.
The card's controls became one glyph, one meaning on the same date (owner finding, 2026-10-05). The pinned
action band's height **is the band's own content at the current font scale**: a fixed `cardCap - 120 dp`
reservation squeezed the labelled End action at font scale 2.0 — measured 2026-10-09 on the host (window
411 x 891 dp at 420 dpi, `RoutePanelActionBandScaleTest`, whose cases print their geometry into the JUnit
XML's `system-out`) as 42.67 dp of action height where the action's own content needs 53.33 dp, and on the
AVD as the action leaving the card entirely (`TODO.md` §138, change `fix-pinned-band-height`). Reviewing a
route, the pinned row is `Start/Ziel ändern` + `Navigation starten` — a recalculation of the same route did
nothing useful (owner finding, 2026-10-03). The analysis's own history: reported on device 2026-10-10, the
segment of the auto-analysed first step stayed drawn on the route's opening leg for the whole drive (change
`fix-navigation-start-segment-highlight`). The right-side control column's inset is one value,
`MapCanvasUiState.overlayCoveredPx`, because the min strip used to cover the zoom-out button (owner finding,
2026-10-03).

## §8b The tap-target fold and the card-height measurements

The tap-target rule is held against **every** actionable target, not against the container's two detail
regions: a card-wide `.clickable` added back around the row left every geometry case green, because those
read the two regions instead of the set of targets that overlap the control (measured 2026-10-22: the fold
in place, 13 tests with one failure — the new case alone — and every pre-existing case green; change
`fix-stop-control-semantics-guard`). Reading the container first was the original defect:
`NavigationStateOverlay` carried `.clickable` on the whole card, so a tap aimed at the stop control's
coordinates was handled by the container and the driver never reached the stop (change
`fix-nav-overlay-stop-tap`). The progress rings replaced two full-width bars that cost 37 dp of the card's
145 dp, and the card measured **145.0 dp before → 120.0 dp after** (forced Robolectric case
`NavigationStateOverlayComposeTest.cardHeightStaysWithinTheBound`, mdpi, measured 2026-10-13; the saving is
25 dp rather than the removed block's 37 dp because the row grew around the larger slot, its height being
driven by the value's text line rather than by the 48 dp stop control). The arc stroke was measured
`3 px at 1x = 3.0 dp`, `6 px at 2x = 3.0 dp`. Composing both cards left the session card (composed first,
same bottom edge) with **no visible or tappable pixel**, so a read-only review of the running navigation
was unreachable although its state was correct (measured 2026-10-06, change `fix-route-session-stop-path`
task 5.1 run (b): card bounds `top=2059 h=341` inside the status card's band, zero full-width pixels of a
probe bar drawn, no node of the card in the accessibility dump).

## §8c The overlay controls' derivations

Reading a theme role for the compass was the defect: the dark scheme's `on*` roles are light, so the needle
landed on a light fill at 1.04:1. The per-presentation palette gives 11.7-15.4:1 light and 7.7-8.5:1 dark.
`CompassButton`'s needle was stroked `strokeWidth = 3f` while its sibling `needleLength = 10.dp.toPx()` and
its own rim (`1.dp.toPx()`) were density-aware — 3 dp at 1x against 0.86 dp at 3.5x (change
`fix-compass-needle-stroke-density`, `TODO.md` §72). Its guard case measures the drawn pixels and prints
them into the JUnit XML's `system-out` (`CompassNeedleStrokeTest`: `CompassNeedleStroke 1x=4px (4.0dp)
4x=12px (3.0dp)` after the fix — the ±1 px antialiased edge counted once — against `4x=4px (1.0dp)` on
HEAD, re-measurable by reverting `3.dp.toPx()` to `3f`). The speed badge's warning red is fixed, not
theme-derived, so white text stays readable in both light and dark schemes — the M3 dark-scheme `error`
color is a light pink that would fail contrast with white text.

## §8a The daylight palette's contrast derivations

Way labels are drawn after way fills and carry no halo, so every road fill is a black label's backdrop.
The former motorway `#4440ec` gave only 3.19:1; the new fills give 5.97 / 8.97 / 8.95 / 14.57. Highway
shields draw WHITE text, so their own darker constant `darken(base, 0.45)` (`#454387` / `#5a5987` /
`#874c4c`) gives white text 8.73 / 6.53 / 6.63; reusing the road fill would leave shield text at
3.52 / 2.34 / 2.35, and the trunk and primary shields were already failing at 3.83 / 3.90. The
`thinXColor` hairline (the `<0.45mm:3px` branch, which the renderer enters only below 0.45 mm *and* 3 px) is
judged on 1.6:1 plus a 25-degree hue separation from land, not on the text threshold — the palette already
ships `thinSecondaryColor` at 1.22:1 and `thinTertiaryColor` at 1.08:1, and a luminance-only floor of 2.0 is
unsatisfiable for the new trunk and primary fills, which are themselves only 2.02:1 and 2.03:1 against land.
`motorwayJunctionLabelColor` relies on `style: emphasize` (the Cairo painter strokes a white glyph halo),
which is part of the requirement, not an optional extra.

