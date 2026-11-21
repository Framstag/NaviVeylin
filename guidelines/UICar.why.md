# UI (car) — why (rationale)

The rationale layer of `guidelines/UICar.md`: the reasoning behind a car rule, and the measurements a
bound was arrived at. Read beside the section its `## §N` anchor names; it is not a document a route
names.

## §3a The wait that is not the loading row's deadline

Issue #110, change `fix-car-favorites-cold-template`: the hop onto the shared background dispatcher
entered 623–1715 ms after the observation started while the store had been ready for seconds, and on
device the published state then arrived in 1–9 ms instead of 623–1719 ms once
`AutoFavoritesProvider.snapshotIfResolved` handed its current state out without a dispatch.

Issue #995, the same screen's other stall: the readiness the row waits on is produced by
`FavoriteRepository.init`, and the only car caller was the session's warmup step (`Initializing
favorites`, inside `withTimeoutOrNull`). A warmup that timed out before that step, was cancelled or
was retried therefore left the process with an unready store, and the observation — which resolved
the provider and collected four flows but never initialised the store it read — published
`storeReady=false` for as long as the screen lived, so the row was never replaced. The same seam has
a second half: `CarScreenObservations` confines a fault to its observation and nothing re-establishes
it within the started period, so one transient failure wedged the period the same way. Both halves
are one fix — the observation owns the readiness it depends on and retries its hop once — and the red
runs are `FavoritesScreenObservationsTest.anUnreadyStoreIsInitialisedOnceAndItsRowsRender`
(`init` never called, nothing published) and
`FavoritesScreenObservationsTest.anObservationFaultIsRetriedOnceAndTheListPublishes` (the faulting
hop attempted once, nothing published).

## §3c The route-calculation notice's dwell

Measured 2026-10-05: an 847 ms route showed the notice for 443 ms, which is why the minimum visible
dwell exists (§133, issue #84).

