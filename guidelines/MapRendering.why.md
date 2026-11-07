# MapRendering — the rationale

This is the rationale layer of `guidelines/MapRendering.md`: the history, the derivation, the dated
measurements and the rejected alternatives behind the rules the base document states, read against the
numbered section the base document carries. An anchor here names a section number the base document
carries.

## §1 Render Pipeline Architecture — the anchor's history and measurements

The blit seam's history, excised from §1a:

- The screen's previous inline `OverrunWindow.shiftPx(…, activeFollowAnchor.fx,
  activeFollowAnchor.fy)` call site was invisible to every case and needed two fixes in one day
  (issue #18, 2026-09-27).
- A caller that rebuilds the state around the preset is refused; the guard reads the code around the
  seam call, so `offsetFor(ui.copy(resolvedAnchor = ResolvedAnchor(ui.activeFollowAnchor.fx,
  ui.activeFollowAnchor.fy)), …)` fails it (measured 2026-10-21: `callSiteRawReads=2`, mutated run red).

