# Proposal

## Why

Issue #995 (`agent/eligible`, `class/bug`, `category/favorites`): the readiness a car favourites screen
renders is produced by one `init` call that lives **outside** the screen. The observation resolves the
provider and collects four flows; it never initialises the store it reads.

**Root cause (read in this session, 2026-11-21, on `66d7618f`):**

1. `FavoriteRepository.init` (`app/src/main/java/com/naviveylin/data/FavoriteRepository.kt`) publishes
   `storeReady` false and sets it true only in the `finally` of that one call.
2. The only car caller of `init` is the warmup step `Initializing favorites` in
   `NavigationSession.startWarmup` (`auto/src/main/java/com/naviveylin/auto/NavigationSession.kt:466-476`),
   inside `withTimeoutOrNull(WARMUP_TIMEOUT_MS)`. A warmup that times out before that step, a cancelled or
   retried warmup, or a failure that skips the step therefore leaves the process with an unready store.
3. `FavoritesScreenObservations.start()` (`auto/src/main/java/com/naviveylin/auto/FavoritesScreenObservations.kt`)
   resolves the four sources and publishes their state; it never asks whether the store is ready, so every
   later favourites screen — the group level, a group, the starred level, each page — renders the loading
   row for its whole lifetime.
4. The second half is the seam: a fault inside the observation is confined by `CarScreenObservations`
   (logged through `carFaultHandler`, not rethrown) and nothing re-establishes the observation within the
   started period, so one transient failure wedges that period the same way.

**The defect contradicts** `openspec/specs/auto-favorites/spec.md` — "The favourites list distinguishes a
loading store from an empty one": the loading row is meant to stand only *while* the store's initialisation
has not completed, and here it stands for the process's lifetime. It also contradicts "Favorites appear
without re-entering the screen": the favourites never appear.

**Root cause proven by:** the case `FavoritesScreenObservationsTest.anUnreadyStoreIsInitialisedOnceAndItsRowsRender`.
It drives only API that exists on HEAD (a provider fake whose `storeReady` flow is false, with an `init`
recorder), so the red run is a direct one — not a revert-check: on HEAD `init` is never called and no state is
published. The second case, `anObservationFaultIsRetriedOnceAndTheListPublishes`, is red on HEAD for the
same reason (the faulting hop ends the observation with nothing published). The tally of both red runs is in
`evidence/red-on-HEAD-995.log`.

**The single resulting fix** — both halves of it belong to the one seam: the observation owns the readiness it
depends on. It resolves the provider, and when `storeReady` is false it calls `init` with the screen's own
favourites path once per started period, inside the background hop (never on the host thread, spec
`auto-map-renderer`); and it retries that hop once after a fault before ending, so the screen is given a
failure state rather than a wedge. No second fix is defensible — see design D3.

## What Changes

- `FavoritesScreenObservations` takes the favourites path its screen initialises an unready store with, and
  its hop now: resolves `storeReady` first, calls `favoritesProvider.init(path)` once for the period when the
  store reports itself unready, then resolves the four sources as before.
- The same hop is attempted twice: a fault is logged with the observation's key and retried once; a second
  fault ends the observation and publishes the failure state.
- The list state the observation publishes gains a `failed` flag (`FavoritesListState`), so the screen can
  tell "no state yet" from "the observation could not produce one".
- `FavoritesScreen` reads the favourites file it hands the observation
  (`carContext.applicationContext.filesDir/favorites.json`) and renders a new unavailable row when the state
  is the failure state, instead of the loading row. New strings `favorites_unavailable` /
  `favorites_unavailable_hint`, translated in `values-de`.
- One declaration of the favourites file name in `:auto` (`FAVORITES_FILE`), used by the session's warmup and
  by the screen — the two call sites cannot drift apart any more.
- Tests: `FavoritesScreenObservationsTest` (four new cases), `FavoritesScreenTest` (one new case).

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `auto-favorites` — three ADDED requirements: "The observation establishes the store it reads", "A failed
  resolution is retried once before the screen is told", "A store that cannot be observed is reported, not
  left loading". The existing loading-row requirement is unchanged; the new three bound *how* its "store
  has completed its initialisation" is reached and what happens when it cannot be.

## Impact

- `auto/src/main/java/com/naviveylin/auto/FavoritesScreenObservations.kt`
- `auto/src/main/java/com/naviveylin/auto/FavoritesScreen.kt`
- `auto/src/main/java/com/naviveylin/auto/NavigationSession.kt` (uses the one `FAVORITES_FILE` declaration)
- `auto/src/main/res/values/strings.xml`, `auto/src/main/res/values-de/strings.xml` (two strings)
- tests: `auto/src/test/java/com/naviveylin/auto/FavoritesScreenObservationsTest.kt`,
  `auto/src/test/java/com/naviveylin/auto/FavoritesScreenTest.kt`
- No native, no JNI, no manifest, no dependency, no persisted format: the file the screen initialises from is
  the file the warmup already used, so an unready process reads the same favourites the phone wrote.
- Additive; the phone path is untouched (`FavoritesScreenObservations` is a car screen). Rollback: revert the
  change's commits — no state, format or stored data is involved.
- The guideline sections the change answers to: `guidelines/UICar.md` §3a (car host path, host-thread
  safety), `guidelines/DesignCrossVariant.md` §8a, `guidelines/Logging.md` §3 (a diagnostics line carries no
  coordinate; only counts, identities and durations are added here).