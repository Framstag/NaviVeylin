# Proposal

## Why

The car favourites screen shows a `Loading...` row for **seconds** the first time it is opened in a
session. Reported on device by the owner on 2026-10-10 (Android Auto): "seconds — noticeably stuck on
Loading", on the first open of a cold session.

**Root cause — the favourites observation resolves its provider on the car-app main thread.** The
chain, with the evidence for each link:

1. The session serves the real root screen before warmup finishes (`NavigationSession.kt:355-390`),
   while `startWarmup` builds the native client and initialises the favourites store on
   `Dispatchers.Default` (`NavigationSession.kt:419-456`).
2. Opening favourites starts the screen's observation: `FavoritesScreenObservations.start()`
   (`FavoritesScreenObservations.kt:37-45`) runs its body on `CarScreenObservations`' main dispatcher
   (`CarScreenObservations.kt:51-52`, default `Dispatchers.Main`).
3. That body calls `favoritesProvider.favoriteLocations()` on the main thread
   (`FavoritesScreenObservations.kt:41`), which resolves the Hilt repository synchronously
   (`AutoFavoritesProviderImpl.kt:28-29`, `repository.get()`), which constructs the `@Singleton`
   `OSMScoutClient` whose `@Provides` runs the stylesheet sync, the icon sync, `System.loadLibrary`
   and the native client build (`MapDownloadModule.kt:33-64`). When warmup has not reached its client
   step yet, all of that executes **on the car-app main thread**.
4. `FavoritesScreen.loaded` (`FavoritesScreen.kt:85`) flips only inside the async collect's first
   emission, so the first template is the loading row (`FavoritesScreen.kt:149-152`) and it stays
   until step 3 finishes; the host cannot deliver a template request while the main thread is busy.

**Second half, same seam.** `loaded` also depends on a coroutine hop rather than on state the screen
already holds, so even a warm store can produce a loading row for one template. That regressed when
the collect moved from the screen's `init` (`git show fe886dc:auto/src/main/java/com/naviveylin/auto/FavoritesScreen.kt`)
to `onStart` via `FavoritesScreenObservations`; commit `7ad154c` carries it and the in-flight change
`fix-details-screen-observation-scope` owns that switch. The window shrank from a full host round trip
to one main-queue hop, which is exactly the reported "sometimes".

**Repro.** Cold session (`adb shell am force-stop com.naviveylin`, start Android Auto, open
Favourites), read `adb logcat -s NaviVeylin`: the `WARMUP` step lines run on while the head unit shows
`Loading...`. No red test exists yet; task 1 of this change writes it.

**The defect contradicts** spec `auto-map-renderer`, requirement "Renderer initialization off the
car-app main thread", scenario "Provider resolution does not build the client"
(`openspec/specs/auto-map-renderer/spec.md:178-182`): "the native client is not built on the calling
(host) thread AND the client is built on a background dispatcher". It is reinforced by
`car-host-fault-isolation`, "Host callbacks answer promptly" (`spec.md:12-20`) — a template request
cannot be answered promptly while the main thread is building the client.

**Single resulting fix.** A car screen's observation resolves its provider on a background dispatcher
instead of on the main dispatcher, and the favourites screen derives its loading row from the store's
real readiness instead of from an async first emission. One root cause — a provider resolved on the host
thread from an observation body, for which no state the screen can hold compensates — one fix, applied
at the three call sites that carry the expression, host-decidable and provable without a device: the
entry is loop-eligible. Alternatives are eliminated rather than weighed: restoring the `init`-time
collect moves the same host-thread resolution into the pushing click callback; resolving the provider
only in the session's warmup cannot help, because the root screen is served before warmup completes and
a screen start can still ask first; pre-warming in a constructor is the same first touch, only earlier;
and inferring readiness from the first emission is impossible, because the repository's initial value
IS an empty map (`app/src/main/java/com/naviveylin/data/FavoriteRepository.kt:29`), so "uninitialised"
and "ready but empty" are indistinguishable — that indistinguishability *is* the second half of the
defect.

## What Changes

- `FavoritesScreenObservations` resolves the favourites provider on an injectable background dispatcher
  (`Dispatchers.Default` by default) before it builds the combined flow, so the native client is never
  constructed on the thread that asks for it. `MapScreenObservations` (`:46`) and
  `DetailsScreenObservations` (`:52`) carry the identical expression and are contained the same way — one
  rule, one parameter; leaving them would implement the requirement for a single screen.
- The loading row is tied to the store's real state instead of to an async first emission. The store's
  initialisation outcome (`FavoriteRepository`'s existing `loaded` flag, published as its own `StateFlow`
  through `AutoFavoritesProvider`) becomes observable, and the screen renders the loading row only while
  the store is uninitialised, the list once it is ready and holds favourites, and the empty-state message
  only when it is ready and genuinely empty.
- One coordinate-free diagnostics line (`SCREEN` tag, beside the existing `WARMUP` steps): the
  observation records its start and the publication of its first state with the elapsed time, so the wait
  is measurable on device. No coordinates, per `guidelines/Logging.md` §3 and the `checkNoCoordinatesInLogs`
  gate.
- `FavoritesScreenTest.showsLoadingBeforeFirstEmission` changes: the first template is asserted against
  the store's real state, and each state the added requirement names gets its own case.

Not changing: the loading row stays a legal state of the other car screens that use it
(`PreferencesScreen`, `SearchHistoryScreen`, `VehicleAnchorPickerScreen`, `OverspeedDeltaPickerScreen`,
`DiagnosticsScreen`), so `R.string.loading` and its German translation stay. `SearchScreen.kt:152`
reads `favoriteLocations().value` on a click path — a different shape, owned by the host-callback rule,
recorded in `TODO.md`, not fixed here.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `auto-map-renderer`: the requirement "Renderer initialization off the car-app main thread" gains the
  call site this defect proved — a screen's observation body started from a lifecycle callback counts
  as the host thread for provider resolution — plus two scenarios naming the cases that exercise them.

- `auto-favorites`: one requirement is ADDED, "The favourites list distinguishes a loading store from an
  empty one", because the loading row is the surface where the second half of the defect shows and no
  existing requirement said when that row may appear. It adds a state distinction, not a browsing
  behaviour: the group order, starred order and selection behaviour of the screen are unchanged.

## Impact

Affected modules and files:

- `:auto` — `auto/src/main/java/com/naviveylin/auto/FavoritesScreenObservations.kt` (resolve on the
  injectable background dispatcher), `FavoritesScreen.kt` (the dispatcher parameter and the three states),
  `MapScreenObservations.kt`, `DetailsScreenObservations.kt` (same containment of the same expression),
  and the shared observation seam `CarScreenObservations.kt` if the design puts the background context
  there.
- `:core` — `core/src/main/java/com/naviveylin/core/AutoFavoritesProvider.kt` gains `storeReady()`, the
  store's initialisation outcome as its own signal.
- `app` — `app/src/main/java/com/naviveylin/data/FavoriteRepository.kt` publishes its existing `loaded`
  flag, and `app/src/main/java/com/naviveylin/di/AutoFavoritesProviderImpl.kt` forwards it. The laziness
  contract asserted by `app/src/test/java/com/naviveylin/di/AutoProviderLazinessTest.kt` stays: resolving a
  provider builds nothing, the first use builds the client.
- Tests — `auto/src/test/java/com/naviveylin/auto/FavoritesScreenTest.kt`,
  `FavoritesScreenObservationsTest.kt`, `MapScreenObservationsTest.kt`, `DetailsScreenObservationsTest.kt`,
  and `app/src/test/java/com/naviveylin/di/AutoFavoritesProviderImplTest.kt`; the red-on-HEAD threading
  cases are new.
- No manifest, resource, Gradle or dependency change. No native/JNI change: this resolves an existing
  `OSMScoutClient` differently, so there is no submodule patch and no bridge-module override.
- Guidelines: `guidelines/UI.md` §3a ("Map renderer startup (never on the host thread)") owns the
  first-touch rule and is worded for the renderer path; it gains the provider-resolution-from-an-
  observation case. `guidelines/Design.md` §8 ("Android Auto & cross-variant") owns the seam, and its §13
  "Appendix B — Provenance" records the reversal of the earlier deferral. `guidelines/Logging.md` §3
  constrains the new diagnostics line.
- Scope: car only (`:auto`, one screen class shared by Android Auto and Android Automotive OS). No
  phone behaviour changes; the phone's own `initMap` path is already off-main.
- Additive, not breaking: the favourites path loses a wait and gains one state distinction, and the
  store's data and order channels are unchanged. Rollback is a revert of one commit — no data migration,
  no native artefact, no migration of persisted state.
