# Design — fix-car-favorites-loading-stall

## What the evidence established

```
  session create
    |  onCreateScreen -> RootScreen (a menu) served at once, warmup NOT finished
    |  warmup (Dispatchers.Default): client() ......... heavy: stylesheet sync,
    |                                                  icon sync, dlopen, native build
    |
    |  driver taps Favourites          <- usually the FIRST touch of the client in
    |     onStart -> observation           the session, because the root is a menu,
    |       -> favoriteLocations()          not the map
    |          -> repository.get() -> Hilt @Singleton OSMScoutClient
    |               warmup is mid-construction, so the MAIN thread blocks on the
    |               singleton lock (or builds it if warmup has not started)
    |       -> first emission cannot happen until that returns
    |       -> the first template is the loading row, for as long as the build takes
```

Two faults in one path: the provider resolution runs on the car-app main thread (the spec
violation, `auto-map-renderer` — "Provider resolution does not build the client"), and the loading
row's end is gated on an async first emission rather than on the store's real state (so a ready store
can be shown as loading, and — worse — an *un*ready store would be shown as empty the moment the
emission arrives with the repository's initial empty map).

## D1 — Where the provider resolution runs

**Chosen: inside a background context in the observation body, on an injectable dispatcher.** The in-module
precedent is `MapScreen`'s `backgroundDispatcher: CoroutineDispatcher = Dispatchers.Default`
(`MapScreen.kt:69-75`, used at `:304`, `:369`), documented as "every hop that touches the native client"
and proven by `MapScreenTest.theProductionDefaultResolvesTheNativeClientOnABackgroundThread` (`:301`).
`FavoritesScreenObservations` takes the same parameter and wraps its provider resolution in it:

```kotlin
internal class FavoritesScreenObservations(
    private val observations: CarScreenObservations,
    private val favoritesProvider: AutoFavoritesProvider,
    private val backgroundDispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val onFavorites: (FavoritesSources) -> Unit
) {
    fun start() {
        observations.start()
        observations.observe(KEY_FAVORITES) {
            val sources = withContext(backgroundDispatcher) {
                FavoritesSources(
                    favoritesProvider.favoriteLocations(),
                    favoritesProvider.groupOrder(),
                    favoritesProvider.starredOrder(),
                    favoritesProvider.storeReady()
                )
            }
            combine(sources) { ... }.collect { onFavorites(it) }
        }
    }
}
```

`CarScreenObservations.observe(key) { ... }` already takes a `suspend CoroutineScope.() -> Unit`, so no
seam change is needed. Effects stay on the scope's main dispatcher, which is what
`car-host-fault-isolation` — "Template invalidation is main-thread only" — requires. `FavoritesScreen`
passes its own injectable `backgroundDispatcher` through, so a case can exercise the default exactly as
`MapScreenTest` does.

Alternatives, each eliminated by the root-cause evidence:

- **Move the collector back into the screen's `init`** (the shape before `7ad154c`). Impossible: the
  constructor runs inside `armScreenPush`'s click callback, on the main thread, so
  `repository.get()` there is the same host-thread resolution — it moves the stall earlier (where the
  driver experiences a frozen app instead of a loading row) and the emission still gates on it.
- **Resolve the provider in the session's warmup only.** Impossible: `NavigationSession.onCreateScreen`
  serves the root screen before warmup completes (`:355-390`) and the root is a menu, so a screen start
  can still ask first; Hilt's singleton lock then puts the wait on the asking thread either way.
- **Move every `CarScreenObservations` body to a background dispatcher.** Out of scope, not merely
  bigger: it rewrites the dispatcher contract of six screens and every effect would have to hop back to
  main for `invalidate()` and the renderer calls. The root cause is one call site resolving on the wrong
  dispatcher, and the seam already permits the fix.
- **`Dispatchers.Main.immediate` for the observation scope.** Impossible: it runs the body inline in
  `onStart`, i.e. the resolution happens on the main thread by construction — the violation itself.

## D2 — How the screen learns the store is ready

**Chosen: the store's initialisation outcome as its own signal.** `FavoriteRepository` already holds
`loaded` (`FavoriteRepository.kt:63`, set at `:98`); it is published as
`AutoFavoritesProvider.storeReady(): StateFlow<Boolean>`, so the screen renders the loading row while the
store is uninitialised and the store's real content once it is. One meaning: *the initialisation attempt
has finished*.

Alternatives:

- **Infer readiness from the first emission.** Impossible: the repository's initial value IS an empty
  map (`FavoriteRepository.kt:29`), so "not loaded" and "loaded, holds none" are the same value. That
  indistinguishability is the second half of the defect, not a way to fix it.
- **A wall-clock grace period before showing the empty state.** Impossible: the native build's duration
  is unbounded (the warmup itself needs a 45 s timeout, `WARMUP_TIMEOUT_MS`), so every choice of period
  either lies to the driver or waits too long.
- **A failed initialisation leaves the store unready forever.** Impossible: a permanent loading row
  hides a failure and is worse than an honest empty state. The signal therefore reports the attempt's
  completion, success or failure; a failed init leaves the store empty, the screen shows its empty
  state, and the failure is logged (`DiagnosticsLog`) and surfaced through the existing startup-failure
  path.

## D3 — How the first template carries the state without an extra hop

**Chosen: the observation publishes the resolved values immediately after it resolves them**, before it
starts collecting, and the screen derives its states from the publication plus readiness:

```
   store ready   state published   template
   -----------   ---------------   -------------------------------------------
   false         either            loading row   (store genuinely uninitialised)
   true          false             loading row   (the publication is still in flight)
   true          true              the list, or the empty state when genuinely empty
```

`loaded` keeps meaning "the observation has published at least one state"; the empty state additionally
requires `storeReady == true`. The middle line is deliberate and is what the device run measured: the row is
what the screen shows while no state exists yet, and on `emulator-5554` it was visible for 4927 ms with the
store already ready, because the session's first render congested the main dispatcher (TODO.md §176). The
requirement therefore bounds the published state (18-45 ms measured) and the never-empty rule, not the
instant of the first template.

Alternatives:

- **Read `.value` in `buildTemplate()`.** Impossible: `buildTemplate` is a host callback, so the read
  would resolve the provider on the host thread — the violation this change removes.
- **Keep the emission as the only trigger and rely on it being fast.** Impossible: the emission cannot
  precede the resolution, so a known state could still be rendered as loading for the width of a
  dispatcher hop, which is the "sometimes" the owner reported.
- **Send the readiness flag separately from the values.** Rejected as a source of a wrong intermediate
  template: a ready flag observed a moment before the values would render the empty state over data
  that exists. Both ride one combined emission, as the existing three sources already do.

## Threading and lifecycle (Design.md §4)

- `FavoritesScreenObservations.start()` is called from `onStart` on the main dispatcher;
  `CarScreenObservations` owns the started period and one instance per key (unchanged).
- The body hops to `Dispatchers.Default` only around the provider resolution (the first touch, and a
  cheap no-op afterwards because the Hilt singleton and the repository are cached). It returns to the
  scope's dispatcher for `onFavorites`, which writes the screen's fields and calls `invalidate()` — main
  thread, as `car-host-fault-isolation` requires.
- No new lifecycle: `stop()` cancels the period's scope as today, so a stopped screen neither resolves
  the provider nor invalidates the host; a state received while stopped is still delivered once on the
  next start because every source is a `StateFlow`.
- `FavoriteRepository.storeReady` is a `MutableStateFlow<Boolean>` written where `loaded` is written,
  on the repository's own background work; readers get the current value without a hop.

## Risks

| risk | assessment and mitigation |
|---|---|
| One extra dispatcher hop per screen start | The hop surrounds only the resolution; after the first session-wide resolution it is a lock check. The spec requires the update, not its instant, and no template is invalidated off-main. |
| New API on `AutoFavoritesProvider` (`:core`) | The archived `fix-aa-favorites-latency` design deferred exactly this ("`initialized` flag on the repository — Deferred"); this change reverses that on the owner's report. `AutoFavoritesProviderImplTest` and `AutoProviderLazinessTest` pin it (the laziness contract is unchanged: resolving the provider still builds nothing). |
| The threading case needs a real background dispatcher | It cannot use the test dispatcher (a `TestDispatcher` runs on the calling thread, which would make the assertion vacuous). Two cases cover it, both following existing in-module patterns: with an injected dispatcher, `MapScreenTest.theTapPathDoesNotResolveTheNativeClientOnTheHostThread` (`:252`) — record the thread each accessor is called on, assert none is the main thread while the injected dispatcher owns the hop; and with the production default, `MapScreenTest.theProductionDefaultResolvesTheNativeClientOnABackgroundThread` (`:301`) — read only the scalar the fake publishes, pump the test scheduler from `Dispatchers.Default` until it arrives (`awaitBackgroundClientResolution`, `:329`), with a bound only as a backstop and no wall-clock assertion (`unit-test-suite-runtime`). |
| `FavoritesScreenTest.showsLoadingBeforeFirstEmission` changes meaning | Its fake provider grows the readiness signal, and the case is rewritten to assert the seeded first template; the other cases in `FavoritesScreenTest` and `FavoritesScreenObservationsTest` stay green. |
| Emulator-only evidence | The stall is observable on a stationary AAOS emulator from the logs; nothing in this change needs driving. If no device is available the measurement is reported as not taken, not implied. |

## Verification

- Unit, red on HEAD before the fix: the accessor threads recorded by a fake `AutoFavoritesProvider` are
  not the main thread — once with an injected dispatcher, once against the production default
  (`FavoritesScreenObservationsTest`, `FavoritesScreenTest`) — each with a vacuity guard that all four
  accessors were reached.
- Unit: the four scenarios of the new `auto-favorites` requirement — loading row only while
  uninitialised; a ready populated store in the first template; a ready empty store shows the empty
  state; readiness completing while the screen is open (`FavoritesScreenTest`,
  `FavoritesScreenObservationsTest`).
- Unit unchanged and green: the group-order, starred-order, stop/start and no-reorder-affordance cases in
  the same two classes.
- Revert-check: remove the `withContext(Dispatchers.Default)` from the observation body, the threading
  case must fail; restore, forced green.
- On device (AAOS emulator, `device-check`; runs 2026-10-10 in `.pi/logs/favorites-loading-5.1/`, logcat via
  `-s Diag/SCREEN Diag/WARMUP Diag/HOST` and screenshots read with `pixel-check`'s OCR helper): settled
  chain end to end **18 ms** and **45 ms** (`resolution entered +7/+20 ms` on a `DefaultDispatcher` worker,
  `done +16/+44 ms` on main, `published +18/+45 ms`, `storeReady=true`), screenshots `Favoriten |
  Favorites | Tacos Fever`, `grep -ciE 'fatal|exception|crash|assert'` = 0. Two earlier runs measured
  2156 ms and 4927 ms for the same chain with the store already ready, with the loading row visible in
  between (`win-b.png`) — filed as TODO.md §176, not this change's subject. What settles the claims: the
  hop enters a background worker within 7-20 ms of the observation starting (the host thread does not
  resolve the provider), the published state of a ready store is shown without a loading row, and an
  unready store is never shown as empty. The pre-ready window itself is **not exercisable** on this
  emulator: its warmup (client build 395-997 ms) completes before the map template is paintable
  (`Favorites ready` 07.539 < `Warmup complete` 07.563 < first surface 08.569 < earliest reachable screen
  start 09.339), so no Favourites start can overlap a warmup step here.
- Guidelines: `guidelines/UI.md` §3a gains the observation-body case of the first-touch rule, and
  `guidelines/Design.md` §13 "Appendix B — Provenance" records the reversal of the deferred readiness
  flag.

## Rollback

Revert the change's commits. No persisted state, native artefact or store format is touched; the store's
readiness signal is additive and unused by the phone path.
