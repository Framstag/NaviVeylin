# Design

## Context

Issue #995 has two halves at one seam — the car favourites screen's observation produces a state or it
does not:

1. **Readiness is produced outside the screen.** `FavoriteRepository.init` publishes `storeReady=false`
   and sets it true in that call's `finally`; the only car caller is the session warmup step
   `Initializing favorites` (`NavigationSession.startWarmup`, inside `withTimeoutOrNull`). A warmup that
   times out before the step, is cancelled or is retried leaves an unready store, and
   `FavoritesScreenObservations` — which resolves the provider and collects four flows — never notices,
   so every later favourites screen shows the loading row for its lifetime.
2. **A fault is never repeated.** `CarScreenObservations` confines a fault to its observation (logged
   through `carFaultHandler`, not rethrown) and nothing re-establishes it within the started period, so
   one transient failure wedges the period exactly like (1). Both halves end in the same symptom and share
   one fix: the observation owns what it depends on, and cannot silently fail.

Specs in scope: `auto-favorites` (the loading row's meaning, a resolved store's state, readiness
completing while open) and `auto/screen-observation` (one observation per started period, a stopped
screen does no work), `auto-map-renderer` (nothing native on the host thread).

## D1 — The initialisation rides the hop that already exists, not a new one

The observation already hops onto `backgroundDispatcher` to resolve the provider. The readiness read and
the conditional `init` go inside that same `withContext` block, before the four source accessors, because
they need the same provider instance and the same off-host-thread guarantee
(`auto-map-renderer` — "A screen observation resolves its provider off the host thread"), and because
`init` is a suspend JNI + file-read path that must not touch `Surface`/host state at all.

*Rejected:* calling `init` from the screen's `onStart` — that is the host thread, the exact rule the
change exists to respect. *Rejected:* a second coroutine for the initialisation — two hops would race the
collection's first emission and could publish the store's pre-init state as final.

The accessor order matters: `storeReady()` is read **first**, so a store already initialised by the warmup
costs one flow read and no `init` (case `aReadyStoreIsNotInitialisedAgain`). `init` is called only when the
readiness flow's current value is false, so the observation is not a second initialiser for a warm store.

## D2 — One declaration of the favourites file, passed to the observation

The warmup builds `File(filesDir, FAVORITES_FILE).absolutePath`; the screen now builds the same path from
`carContext.applicationContext.filesDir` and hands it to `FavoritesScreenObservations(favoritesPath = …)`.
`FAVORITES_FILE` moves from `NavigationSession`'s companion to a top-level `internal const` in
`FavoritesScreenObservations.kt`, so a rename cannot leave the warmup reading one file and the screen
initialising another. The path is a constructor parameter rather than read inside the observation for two
reasons: unit cases can then assert *which* path `init` received without a `CarContext`, and the screen
stays the one place that knows its `CarContext`.

*Rejected:* the observation reading `filesDir` itself through a locale/provider seam — a second path
resolver is a second thing that can drift; the change is about one store, one file.

## D3 — Retry once, then report; no third outcome

`resolveSources` attempts the hop twice. On the first fault it logs
`favorites resolution attempt 1 failed (…)` and retries; on the second it logs
`favorites resolution failed twice … reporting the store unavailable` and returns null, and the observation
publishes `FavoritesListState(failed = true)` before ending. A `CancellationException` is rethrown, never
retried: it is the started period ending, not a fault, and retrying it would run work for a stopped screen
(`auto/screen-observation` — "A stopped screen performs no renderer or host work").

Two is the whole budget: one transient failure (the observed class — a native refusal while the client is
still being built, a first read before the file exists) is survivable; a store that faults twice is not
made readable by a third attempt within the same started period, and a longer loop would keep the row on
screen for a duration no bound covers. The next `onStart` establishes a fresh observation, which is the
retry the driver actually performs by reopening the list — and the unavailable row's hint says so.

The failure state is a **state**, not an absent one, because the screen must render something else:
"loading" claims an initialisation is in progress, and after both attempts there is none. The screen keeps
its four-branch order: unavailable → loading (nothing published yet, or store unready) → empty → list.

## D4 — The screen's state is one object, so the three new facts ride one callback

`FavoritesState` (private, four fields) becomes `FavoritesListState` (`internal`, four fields plus
`failed = false`), and `onFavorites` takes it as one parameter instead of four. The screen's `failed` flag
is then set in the same write as the other four, so no template can observe a half-applied state, and a
case can assert the failure state without instantiating a screen. `publishOnce`'s equality check covers
`failed` automatically, so a repeated failure state does not invalidate the host twice.

## D5 — What is not changed

- The phone path: this is the car screen's observation; `FavoriteRepository` and the phone's map view model
  are untouched, and the session warmup keeps its `init` (favourites stay available before the map's
  databases open — the warmup's own reason to exist).
- `CarScreenObservations`: no retry is added there. Every other car observation has its own seam, and a
  generic retry would hide the failures those screens do report; the favourites observation's retry is
  its own requirement.
- No new string is translated beyond the two the row needs (`favorites_unavailable`,
  `favorites_unavailable_hint`, English and German).