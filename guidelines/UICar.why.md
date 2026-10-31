# UI (car) — why (rationale)

The rationale layer of `guidelines/UICar.md`: the reasoning behind a car rule, and the measurements a
bound was arrived at. Read beside the section its `## §N` anchor names; it is not a document a route
names.

## §3a The wait that is not the loading row's deadline

Issue #110, change `fix-car-favorites-cold-template`: the hop onto the shared background dispatcher
entered 623–1715 ms after the observation started while the store had been ready for seconds, and on
device the published state then arrived in 1–9 ms instead of 623–1719 ms once
`AutoFavoritesProvider.snapshotIfResolved` handed its current state out without a dispatch.

## §3c The route-calculation notice's dwell

Measured 2026-10-05: an 847 ms route showed the notice for 443 ms, which is why the minimum visible
dwell exists (§133, issue #84).

