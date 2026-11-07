# Item #97 — verdict `skipped` / `falsified`

- item: **#97** — "§152 The follow-anchor insets during a navigating session panel follow the chrome
  the panel covers"
- run base: `9aac52b5d9778a6d5cc2a92cb95a35dc9c7eb00e`; worktree HEAD: `e0598b7` (worktree clean when this
  item started and clean when it ended — nothing in the tree was changed)
- measuring block: `measure.txt` in this directory (`./tools/loop/measure.sh`, one call, exit 0)
- gate: **not run**. The tree is unchanged, so no compiled file moved and there is no delta to gate.
  The probe case below stands in as the measurement.

## The claim, and what the tree shows

The entry says, of a session panel open **during navigation**:

> While the panel is open during navigation, the published bottom inset still describes the routing
> status card (which is suppressed) and the widget column (which the card covers), while the band the
> anchor actually has to avoid is the card

The tree carries the opposite wiring, at the same two places in the run's base and in HEAD
(`git show 9aac52b:app/src/main/java/com/naviveylin/ui/map/MapCanvasScreen.kt | grep -n overlayBottomInset`
returns the same four sites as the working tree):

- `app/src/main/java/com/naviveylin/ui/map/MapCanvasScreen.kt:2533-2536` — the session card's
  `onOverlayHeightChanged` writes the card's measured height into `overlayBottomInset`
  (`viewModel.setOverlayCoveredPx(height); overlayBottomInset = height`). The status card's writer
  (`:2302-2313`) is guarded by `if (!state.showRoutePanel)`, so the two writers are mutually exclusive —
  one writer per inset, as the entry's fix candidate asks.
- `app/src/main/java/com/naviveylin/ui/map/MapCanvasScreen.kt:2362-2366` — the inset effect publishes
  `bottom = if (navState.isNavigating) overlayBottomInset else …`, i.e. while navigating the published
  bottom inset is exactly the value the card wrote.

So the entry's scenario was already wired when it was recorded (the change that filed it, `fix-phone-map-layer-stack`,
landed the card→inset line), and it is not a defect of the tree today.

## The measurement (probe, not a change)

The probe hosts the **real** `MapCanvasScreen` (the harness of
`app/src/test/java/com/naviveylin/ui/map/MapNavColumnWidthProbeTest.kt`: hand-driven Compose clock, since the
screen never goes idle), with a bottom-anchored preset (`BOTTOM_CENTER`, fy = 0.9, settings
`routingAnchorId`/`freeDrivingAnchorId`) so that `uiState.resolvedAnchor.fy` carries the size of the
published bottom band: `resolveAnchorFraction` maps a covered bottom band to
`(H − band − 0.1·(H − band)) / H`.

Command (from the change root):

```
./gradlew :app:testMobileDebugUnitTest --tests "com.naviveylin.ui.map.ProbeFollowInsetDuringNavSessionTest"
```

Output (also in `probe97-junit.xml`, `system-out`; source in `probe97-source.kt`):

```
PROBE97 navigating=1 cardComposed=true rootPx=891.0 cardPx=378 fyWithoutPanel=0.7707070707070708 fyWithPanel=0.5181818181818182 delta=0.2525252525252526 expectedFyIfCardIsTheInset=0.5181818181818182
PROBE97-browse navigating=0 cardComposed=true rootPx=891.0 cardPx=378 fyWithoutPanel=0.9 fyWithPanel=0.9 expectedFyIfCardIsTheInset=0.5181818181818182
```

- **Navigating, card open:** `fy = 0.5181818182`, which is exactly
  `(891 − 378 − 0.1·(891 − 378)) / 891` — the card's 378 px is the published bottom inset. With the card
  closed the same anchor resolves to `0.7707` (the routing status card's 128 px). The entry's premise is
  contradicted by the number it asked for.
- **Browsing, card open (measured for completeness, not part of the claim):** `fy = 0.9` unchanged — the
  card feeds the inset only on the navigating branch, so a session card open *outside* navigation does not
  move the anchor out of the band it covers. That is the entry's own deferred question ("whether the follow
  anchor should also be clamped out of the card band is a decision for the session surface"), at a place the
  entry does not name. It is a separate item, not this one's fix candidate.

## Residue

Nothing to apply, nothing to archive. The navigating scenario the entry names is correct on HEAD and was
correct at the run's base; the browse-mode gap the probe shows second is a different, decision-bearing
finding that a person may want to file on its own terms.
