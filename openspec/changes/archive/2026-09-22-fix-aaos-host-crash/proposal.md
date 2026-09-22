# Proposal

## Why

On an Android Automotive OS head unit the **car host** (templates host / car UI) crashes after
seconds to minutes while NaviVeylin drives — with the app still alive and often in the background,
so from the user's seat Android Auto "crashed", not the app. No log was captured, so the first job
of this change is to make the app incapable of taking the host down, on the paths that can do it at
all: the car `Surface`, the host callbacks, the rail-widget turn-by-turn notification and the
cluster/HUD trip metadata.

Why now: the car-facing surfaces were added in the last two days (`car-turn-by-turn-rail-widget`,
`NavigationNotificationBuilder` with `CarAppExtender`, `NavigationManagerController` trip
publishing), the surface-release ordering workaround in `:auto` predates them, and routing-active +
backgrounded is exactly the state in which the app has no surface of its own and the host does all
the rendering.

An app can only crash a *host* four ways: (1) a malformed or oversized Binder payload, (2) a host
callback that is not answered promptly, (3) misuse of a shared kernel resource (the car `Surface`),
(4) resource pressure that gets the host killed. This change closes (2) and (3) and bounds the app's
footprint for (4); (1) is bounded by construction (dedup + validated payloads) and is listed as a
verification item.

## What Changes

- **Car surface ownership (the release ordering).** The car-app library starts the *incoming* screen
  before it stops the *outgoing* one (`ScreenManager.pushInternal` :330/:336, `popInternal`
  :363/:371), and every `:auto` screen registers its `SurfaceCallback` in `onStart` but unregisters
  only in `onDestroy`. The outgoing screen's `onStop` then calls `Surface.release()` on a producer the
  incoming screen is already drawing through, and the same release fires on every backgrounding
  (`ScreenManager` :475-485). Replace per-renderer "release my instance" with **one owner per
  session**: a session-scoped surface registry, identity-guarded unregistration, release only on the
  host's `onSurfaceDestroyed` (or session destroy). Remove the `invalidate()`-to-get-a-fresh-surface
  workaround that currently hides the defect.
- **Host callbacks are answered promptly.** No `:auto` screen or constructor resolves the Hilt
  `OSMScoutClient` (which synchronously syncs stylesheets, `dlopen`s and runs the native build) on the
  host thread: `onSurfaceAvailable` only buffers the surface DPI, and the screen constructors no
  longer pull the client through `autoFavoritesProvider()`/`autoLocationProvider()`. The DI graph
  hands out providers lazily.
- **Nothing thrown on the host path reaches the process.** `NavigationManagerController.publishTrip`
  computes the trip *inside* the guarded call, `NavigationManager` lifecycle calls are guarded, and
  every host callback of every `:auto` screen is wrapped so a fault degrades (log + no-op) instead of
  killing the app — the car-app library rethrows an app exception on the main thread.
- **`invalidate()` is main-thread only.** The renderer's surface-failure callback (currently invoked
  from the render/extrapolation loops) posts to the main thread.
- **Bounded host-facing traffic while the app is backgrounded.** The ongoing notification is
  re-posted only when its host-visible content changed (currently every navigation-state emission,
  ~1 Hz), the navigation session no longer pushes/pops screens or calls the host navigation API
  without a lifecycle gate, and the render side stops doing periodic full work: no stylesheet reload
  plus full render every 5 s from the settings re-read, and a full-render cadence that accounts for
  the measured render duration instead of a fixed 200 ms.
- **Diagnosability.** The app records what it sent the host and when (client build duration and
  thread, notification re-posts, trip updates, screen push/pop, surface acquire/release), so a
  reproduction yields app-side timestamps around the host's death even without a captured logcat.

**Not BREAKING.** No public API, resource, manifest, flavour or Gradle task surface changes; no native
change. Additive behavior hardening on the car path.

**Rollback:** revert the change's commits — the previous release-on-stop/invalidate-recovery
behaviour returns. The surface registry can be reverted independently from the DI/provider
lazification; nothing else depends on either.

## Capabilities

### New Capabilities

- `car-host-fault-isolation`: the car session never faults the host — prompt host callbacks, no
  escaping exception on the host path, main-thread `invalidate()`, single-owner car surface released
  only on the host's signal, and bounded host-facing traffic (notification/trip/screen/template)
  including while the app is backgrounded.

### Modified Capabilities

- `auto-map-renderer`: the "Renderer initialization off the car-app main thread" requirement is
  tightened — no native-client resolution from screen constructors or host callbacks, only buffered
  surface attributes, so a surface callback always returns promptly.
- `auto-smooth-follow`: the surface-lifecycle/extrapolation gating requirement is restated for
  per-session surface ownership (no per-renderer release of a shared host surface; the loop's gate is
  per renderer, the surface's lifetime is per session) and the full-render request cadence is bounded
  by the measured render duration.

**In-flight coordination (no delta here, must be reconciled, see Impact).** `auto-navigation-hints`
(change `car-turn-by-turn-rail-widget`) and `navigation-ongoing-notification` (change
`background-navigation-notification`) already specify the rail-widget notification contract and the
trip-publishing cadence. This change *refines* their update cadence and fault isolation; those live
in `car-host-fault-isolation` so the two in-flight changes can archive in any order, and a task
reconciles the wording before this change is archived.

## Impact

**`:auto` (the change's bulk)**

- `SurfaceGate` (new, session-scoped) + `RendererGate` (per screen): single-owner registration and
  release; the `RendererGate` keeps the `SurfaceCallback` identity so a stopping screen cannot clobber
  the owner's registration.
- `MapScreen.kt`, `NavigationScreen.kt`, `FreeDrivingScreen.kt`, `DetailsScreen.kt`: register/unregister
  through the gate, drop `releaseSurface()` on stop, wrap every host callback, post `invalidate()`,
  gate observers on the screen/session lifecycle, remove `MAX_SURFACE_REFRESH_ATTEMPTS` recovery.
- `AutoMapRenderer.kt`: no release of a surface it does not own; surface-failure callback marshalled
  to main; full-render request cadence bounded by measured duration; `shutdown()` releases only its
  own surface.
- `NavigationManagerController.kt`: guarded trip construction and host calls.
- `NavigationSession.kt`: lifecycle-gated host/screen mutations; observers stopped while the session
  is not started.
- `AutoInitialViewport.kt` / the screen init coroutines: construct + publish the renderer after the
  background work returns (no cancellation window that drops a live renderer).

**`:app`**

- `di/AutoServiceModule.kt` (`provideAutoClientProvider`, `provideAutoFavoritesProvider`),
  `di/AppModule.kt` (`provideFavoriteRepository`): `Provider`/`Lazy` indirection so resolving a car
  provider does not build the native client.
- `service/NavigationNotificationService.kt` (+ `NavigationNotificationBuilder.kt`): content-deduped
  re-post, guarded `startForeground`, and logging of each post.
- `navigation/AANavigationController.kt`, `navigation/NavigationStateProvider.kt`: stop-request and
  lifecycle-gated host interaction (aligned with the in-flight
  `background-navigation-notification`).

**Documentation**

- `guidelines/Design.md` §4 (threading) and §8 (Android Auto — surface lifecycle wording moves from
  "release every surface when replaced or destroyed" to single-owner/per-session semantics),
  `guidelines/UI.md` §1 (ongoing notification while background driving), §3/§3a (host template
  constraints, renderer startup), `guidelines/MapRendering.md` §14/§15a (AA renderer, stylesheet-flag
  push). `guidelines/Build.md` if the verification runs need a new measurement recipe.

**Native / JNI**

- No submodule patch and no bridge-module override in this change: every defect addressed here is on
  the Kotlin side of the boundary. The separately found `ClientData::knownPaths` data race in
  `openDatabase` (unsynchronised `push_back` + by-value copy while another thread opens a database) is
  an *app-process* crash, is documented in `TODO.md`, and belongs in its own change with a minimal,
  upstreamable submodule patch — it is not part of this one.

**Scope:** Android Auto projection *and* AAOS, i.e. the car path of `:auto` plus the car-facing
services in `:app`. The phone UI, the phone renderer and the map-download path are untouched.

**Verification:** unit tests for the surface registry, the content-dedup, the guarded trip/host
calls, the DI laziness and the lifecycle gating (existing `:auto` fakes, `RendererTestRule` teardown
rule); a build for both flavours; and on-device/AVD runs — `Automotive_Distant_Display_with_Google_Play`
AVD for the AAOS path plus a phone for projection — with the bisect-by-disable runs (skip the
`CarAppExtender`, no-op the trip publishing, gate the session observers) recorded as the evidence
that separates the senders.
