# Design

## Context

The car favourites screen observes its state through `FavoritesScreenObservations`, whose body runs on the
car-app host thread (main). Resolving the provider there would block the host on the native client's first
touch, so the body hops to a background dispatcher before touching it. Issue #110 measured that hop on a
cold session: `favorites resolution entered` arrived 623–1715 ms after `favorites observation start`, while
the store had been ready since the warmup, so the loading row stood on screen although the list was known.
The seconds are on the **background** side — `resolution done` and `state published` follow the entry within
2–3 ms, both on main.

The fix must remove the screen's dependence on that hop for the first template without giving up the reason
the hop exists.

## Goals / Non-Goals

- Goal: a resolved store's state reaches the first template without waiting for the hop.
- Goal: an uninitialised store is still never presented as empty, and the host thread still never resolves
  the provider.
- Non-Goal: making the hop fast. Its delay is not this screen's to fix (see D3).
- Non-Goal: the phone favourites surface — it has no such observation.

## Decisions

### D1 — The observation publishes the resolved store's state before the hop

`FavoritesScreenObservations.start()` publishes `favoritesProvider.snapshotIfResolved()` through the existing
`publishOnce` dedup, then runs the hop exactly as before. The hop's own publish repeats the same state and is
dropped, so a started period still applies exactly one state per change. The loading row therefore stands only
for a provider that has not resolved the store — which is what the row means
(`auto-favorites` — "The favourites list distinguishes a loading store from an empty one").

### D2 — The snapshot is answered by the provider, from the resolved store, and is `null` before that

`AutoFavoritesProvider.snapshotIfResolved()` returns the four observed sources as one `FavoritesSnapshot`
when the provider has already resolved the repository, and `null` otherwise. The implementation tracks its
own first resolution (a `@Volatile` flag set after `Lazy.get()` returns), so this method is never the call
that builds the native client, and it reads the resolved store's live `StateFlow.value`s — plain field reads,
so the host thread may call it.

The four values are read together and published as one state, the same shape the combined flow emits, so a
screen can never render a torn list (design D6 of `fix-car-favorites-loading-stall`).

### D3 — The alternatives the root-cause evidence eliminated

- **Give the hop a dispatcher the renderer does not share** (the first candidate, from the dispatcher
  question): impossible as the fix, because the wait is not pool occupancy. In `cold5` the hop ran on
  `DefaultDispatcher-worker-3` while a *different* worker (`5930`) parsed the stylesheets, and it still waited
  1052 ms — a worker was free, and the thread was not scheduled. The constraint is CPU time during the first
  frame, which every dispatcher's thread pays equally. It would have moved the queue, not removed it.
- **Move the renderer off `Dispatchers.Default`**: same measurement, same answer. The render's CPU demand is
  the constraint, not the pool it runs on; and the renderer's own dispatcher is owned by
  `auto-map-renderer`, not by this screen.
- **Read the state in `buildTemplate()`** (the rejected mechanism of `fix-car-favorites-loading-stall`'s D3):
  a host callback must not be the first touch of the provider, and reading there would bypass the readiness
  gate the loading row exists for.
- **A session-scoped cache written at warmup**: needs the warmup to own a second copy of state that the store
  already holds and keeps current; the provider's live flows are that cache, so a second one can only go
  stale.

## Risks / Trade-offs

- A screen started before the warmup resolves the store keeps the loading row for the whole hop — unchanged
  from today, and correct: there is no state to show.
- `snapshotIfResolved()` returns the store's *current* values, not a copy taken earlier, so a change made
  between the snapshot and the hop's publish is already in the snapshot; the hop then publishes an equal
  state and `publishOnce` drops it. No window shows a stale list.
- Interface method with a `null` default: a fake or another implementation that does not override it keeps
  today's behaviour. The one `mockk`-based fake in `ClickPathDefersAndConfinesTest` stubs it explicitly.

## Migration Plan

None: no persisted state, format or native artefact. Additive interface method; revert the commits to roll
back.

## Verification

- Unit, `:auto` (Robolectric — `DiagnosticsLog` writes through `android.util.Log`):
  `FavoritesScreenObservationsTest.aResolvedStoresStateReachesTheScreenBeforeTheBackgroundHop` drives the
  production path with a resolved snapshot and a hop whose dispatcher is on its own, never-advanced
  scheduler, so only the synchronous publish can have happened. Its guard
  `anUnresolvedProviderPublishesNothingBeforeTheHop` keeps the null answer honest.
- Unit, `:app`: `AutoFavoritesProviderImplTest.snapshotIsNullUntilTheProviderHasResolvedTheStore` and
  `snapshotReportsTheResolvedStoresCurrentState`.
- Revert-check: remove the synchronous publish, the named case must fail
  (`:auto:testDebugUnitTest --tests '*FavoritesScreenObservationsTest*'` → tests=7 failures=1), restore,
  forced green.
- On device (AAOS `emulator-5556`, automotive debug, the recipe of `.pi/logs/run-110.sh`): six cold runs at
  `load average` 15.8–18.4 — `favorites state published after 1–9 ms` in all six, including a run whose hop
  entered at 866 ms; the loading row is gone within the first two screencap frames after the tap. On HEAD the
  same recipe measured `state published` at 623–1719 ms.
