# MapFollow — the rationale

This is the rationale layer of `guidelines/MapFollow.md`. An anchor here names a section number the
base document carries.

## §7 Bearing Smoothing (location layer) — why a bearing is smoothed

A raw fix bearing is noisy, so the displayed heading is smoothed before it drives the map rotation;
the bound the smoothing holds is stated in `guidelines/MapFollow.md` §7.

## §8 Marker Rules — why a marker is deduplicated

A marker is deduplicated so the drawn position does not jump when two fixes describe one stop; the
rule is stated in `guidelines/MapFollow.md` §8.

## §11 Auto-Zoom — the measurement behind the pin

The walk's pin on the render magnification lives exactly as long as the walk. Every magnification
committed outside the walk (`fitViewportToRoute`, the follow-engage preset, the mode presets, a
re-center) is silently discarded (measured 2026-10-13 on the phone: the route overview fit committed
mag 10.0 and the frame was rendered at the landed auto-zoom walk's 17.756, so the fitted route lay
outside the frame until an unrelated render put the camera back on the vehicle).
