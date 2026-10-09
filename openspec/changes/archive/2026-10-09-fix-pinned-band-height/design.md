# Design

## Context

`RoutePanel.kt` renders the phone card as a `Surface` capped at 45 % of the screen
(`.heightIn(max = cardCapDp.dp)`), whose content column carries **two** children in max: the scrolling
content (`body(false)` + `bodyList()`) and the pinned action band (`bodyActions(false)` /
`StoppedActions(...)`). Before this change the scrolling child was capped at
`heightIn(max = (cardCapDp - ACTIONS_BAND_DP).dp)` with `private const val ACTIONS_BAND_DP = 120f`
(`:115`), i.e. the band's room was a **constant** while the band's height is a **function of the font
scale**: every action label inside it (`Start/Ziel ändern`, `Navigation starten`, `Analyse beenden`) is
`sp`-sized text, and the band's height is label height + padding.

Measured on the host, window `w411dp-h891dp-420dpi` (the AVD's shape and density: 411 × 891 dp at
420 dpi), max card on a calculated route, `getBoundsInRoot()` in dp:

| font scale | card | action row | labelled End action | band |
|---|---|---|---|---|
| 1.0 | `[531.43, 890.67]` (hugs content) | `[766.86, 806.86]` | `[814.86, 854.86]` = 40.0 dp | 123.81 dp |
| 2.0 before (not retained — see below) | `[489.9, 890.67]` (= the cap) | `[770.67, 824.0]` | `[824.0, 866.67]` = **42.67 dp** | 120.0 dp (the constant) |
| 2.0 after | `[489.9, 890.67]` (= the cap) | `[752.0, 805.33]` | `[805.33, 858.67]` = **53.33 dp** | 138.67 dp |

The rows are not a probe's output: `RoutePanelActionBandScaleTest` prints each measurement
(`BandGeometry …`, the band's span being its first row's top to the card's bottom edge) before it
asserts, so the JUnit XML's `system-out` carries them —
`app/build/test-results/testMobileDebugUnitTest/TEST-com.naviveylin.ui.route.RoutePanelActionBandScaleTest.xml`,
round-2 gate `2026-10-09T17:48:01.013Z` (automotive `17:45:52.997Z`). The 1.0 and 2.0-after rows are
that case's two cases, re-checkable there. The 2.0-before row is **not retained**: the revert-check mutation of
`tasks.md` 4.1 restored the pre-fix layout exactly and the case printed this row into that run's red XML
(ts `2026-10-09T17:32:12.243Z`), but the later green runs overwrote that XML and Gradle copies no test stdout
into its console log — `/tmp/loop-138-fix1-revert.log` keeps the run's failure text (`Actual height is
42.666687.dp`, BUILD FAILED in 57s) only. The row therefore stands as recorded prose from that run,
re-creatable only by re-running the mutation.

At font scale 2.0 the content is taller than the cap, so the scrolling child takes its whole reduced cap
(`cap − 120 dp`) and the band is handed the remainder — exactly the constant — which is 18.67 dp less than
the band needs. `Column` gives the shortfall to its last child, so the labelled End action lost height
(42.67 dp, below the 48 dp tap target of `guidelines/UI.md` §8, its label clipped inside it). On the AVD at
font scale 2.0 `TODO.md` §138 measured the same shortfall at a larger band height: the action left the card
and reached no UI dump at all.

The host harness cannot reproduce the *wrapping* that makes the device's shortfall larger: Robolectric's
non-native graphics measures every character as ~1 px wide regardless of font scale, so the action labels
never wrap. What the host reproduces is the mechanism itself — the band handed a constant instead of its own
height — and it reproduces it as a falsifiable geometry (`42.67 dp` measured, `48 dp` required). The
containment claim on the device stays a device task.

## Goals / Non-Goals

**Goals**

- The pinned band's height comes from the band's own content at the current font scale; the card's shared
  height budget is the only thing deciding how much the scrolling content gets.
- The card keeps every property the spec already fixed: at most 45 % of the screen, hugging its content
  when the content is shorter, band pinned at the bottom edge, one vertical scroll region.
- A host case that fails on the pre-fix tree and passes after, with the numbers in its message.

**Non-Goals**

- Not changing the 45 % / 18 % shares, `phoneCardHeightDp`, or the min card.
- Not changing the band's content (labels, buttons, order) or capping the action typography.
- Not proving the device's containment here (needs the emulator; follow-up task).
- No change to the docked wide panel, which scrolls as a whole and has no pinned band.

## Decisions

### D1: the band is measured first and the scrolling content takes the remainder

**Chosen**: the scrolling child in max carries `Modifier.weight(1f, fill = false)` instead of
`heightIn(max = cardCapDp - ACTIONS_BAND_DP)`, and `ACTIONS_BAND_DP` is deleted. In a `Column` the
non-weighted children are measured first (the band, at its natural height) and weighted children then take
what is left, so the band's height is its content's by construction and grows with the font scale. The card
stays capped because the weighted child's incoming `maxHeight` is the remaining space, not an unbounded one;
`fill = false` keeps the hug-content behaviour the card owes a short route (a weighted child with
`fill = true` would stretch the card to the cap in every state).

*Alternatives eliminated by the evidence*

- **Retune the constant** (raise `ACTIONS_BAND_DP`, or take the room from the 45 % cap): a constant cannot
  hold at every scale — the measured band is 123.81 dp at scale 1.0 and 138.67 dp at scale 2.0 in one window,
  and it keeps growing — and raising it charges the step list for room the band does not need at the default
  scale, while lowering the cap breaks "the map keeps at least 55 % of the screen height".
- **Cap the action typography at large scale**: it trades the labelled exit's readability minimums
  (`guidelines/UI.md` §8) for geometry it does not fix — a larger scale still exceeds any fixed reservation.
- **Move the End action into the scroll region** (`TODO.md` §138's third candidate): the requirement pins
  the actions ("SHALL stay reachable without scrolling"), and an exit that needs a scroll is the defect the
  pinning fixed on 2026-10-03.
- **Measure the band with `onSizeChanged` and reserve the reported number**: it needs two frames and shows
  the squeezed layout in the first one — the very symptom — where the `Column`'s own child measurement order
  gets the right answer in one pass.

## Risks / Trade-offs

- **The step list loses the band's growth**: at font scale 2.0 on the measured window the scrolling region
  shrinks by 18.67 dp (280.77 → 262.09 dp — the card's height minus the band's, both from the printed
  geometry above — ~one step row). That is the intended trade: the card's share is
  capped by the spec and the actions are pinned, so the space can only come from the scrolling content.
- **A weighted child requires a bounded parent height**: the phone card's `Surface(heightIn(max = cardCapDp))`
  supplies it; the docked panel (no weight, whole column scrolls) is untouched, and min mode composes no
  weighted child at all.
- **The device's containment is not proven here**: the host case proves the band keeps its own height and the
  action keeps its tap target; `tasks.md` 4.1 carries the AVD measurement (font scales 1.0 / 1.3 / 2.0) as
  pending, never as done.
