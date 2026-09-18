## Context

See `proposal.md` — Why. Current state that shapes the approach:

- `NavigationSession.startWarmup()` builds the Hilt `OSMScoutClient` singleton (native `build()`: dlopen + stylesheet sync + renderer setup) on `Dispatchers.Default`, time-boxed. `onCreateScreen()` runs on the main thread and returns `MapScreen(...)` as the stack root.
- `MapScreen`'s `init` block assigns `mapRenderer.onSurfaceFailed = { ... }` (`MapScreen.kt:146`), forcing the `mapRenderer` lazy: `entryPoint.autoClientProvider()` (first touch can run the full native `provideOSMScoutClient` build on this thread if warmup has not beaten it), then `initialViewport` lazy → `computeInitialViewport()` → `latestSavedViewport()` (file IO) + `firstInstalledMapBbox()` (per-DB native `getDatabaseBoundingBox`, contending with warmup's `openDatabase`).
- `AutoMapRenderer` construction itself is cheap: fields + `startRenderLoop()`/`startExtrapolationLoop()` coroutines on `Dispatchers.Default`. It already tolerates a late surface (`onSurfaceCreated` stores dims; render loop waits).
- `NavigationScreen` (`:264`) and `DetailsScreen` (`:82`) have the same lazy-forced-in-init pattern; `FreeDrivingScreen` defers client access (lazy, never forced in init) and needs no change.
- `MapScreenTest` is placeholder-only; the repo has established unit-test patterns (Robolectric + mockk, injected test dispatchers, pure-Kotlin helpers under test).

## Goals / Non-Goals

**Goals:**
- `MapScreen` (and `NavigationScreen`, identical pattern) construct without touching the native client or resolving the initial viewport on the main thread.
- Template delivery and screen observers start immediately; renderer readiness is asynchronous.
- No map state is dropped across the readiness window; the most recent value wins.
- Extract the viewport-resolution and ready-buffer logic into pure, unit-testable Kotlin.

**Non-Goals:**
- No change to `AutoMapRenderer`'s drawing/render-loop behavior (ctor already off-main-safe).
- No new placeholder UI for the pre-ready window (first paint after ready matches today's surface-arrival behavior; a frozen main thread is the problem, not the blank-first-frame, which already occurs).
- No warmup re-ordering/parallelization (favorites ordering from `fix-aa-favorites-latency` is preserved; the client build stays where it is).
- `DetailsScreen`'s own renderer untouched (DetailsScreen's `MapScreen` constructions benefit automatically; the pattern was applied to the three surface screens that force the renderer in init — `MapScreen`, `NavigationScreen`, `FreeDrivingScreen`. FreeDriving was added after the initial "safe" assessment proved wrong, task 3.2).
- No native/JNI or submodule changes.

## Decisions

### D1 — Ready-handle with last-wins replay buffer (not a proxy, not suspend/join)

The screen holds the renderer behind a readiness handle; pre-ready events are captured as a small bounded set of "last value" slots replayed on readiness.

- **Alternative A — `CompletableDeferred<AutoMapRenderer>` + suspend/join at call sites**: most call sites (lifecycle observers, `SurfaceCallback`, settings appliers) are synchronous and cannot `join`. Rejected.
- **Alternative B — delegate/proxy renderer object buffering every call**: unbounded surface, hides lifecycle, hard to test. Rejected.
- **Chosen — explicit gate**: a small internal `RendererGate` holding `MutableStateFlow<AutoMapRenderer?>` plus last-wins slots for: surface (dims + DPI), dark presentation, follow re-center / north-up intent, latest GPS position, pending frame request. On readiness the slots replay in a defined order (surface → dark → viewport → marker → requestRender). Pure Kotlin, unit-testable without `CarContext`.

### D2 — Viewport resolution extracted and run on `Dispatchers.Default`; client first-touched off-main

`computeInitialViewport()` becomes a suspend/internal function executed via `withContext(Dispatchers.Default)`, and the init coroutine touches `entryPoint.autoClientProvider()` first, so the Hilt singleton (and any remaining native build) can never first-touch on the main thread. Rationale: the warmup may or may not have beaten the screen to the client; first-touch placement is the invariant we control.

### D3 — Init block freed of renderer work; lifecycle routed through the gate

- `init` no longer reads `mapRenderer`; `onSurfaceFailed` and `overlayDrawer` are wired in the gate's readiness observer.
- `onStart`: `registerSurfaceCallback()` immediately; `resume()` intent buffered if not ready, applied on ready.
- `onStop`: `releaseSurface()`/`pause()` no-op safely when not ready (nothing to release).
- `onDestroy`: cancel the init job if not ready (cancellation aborts the suspended viewport resolution), else `shutdown()` — no renderer leak and no work after destroy (specs: "Renderer still initializing when the screen stops").

### D4 — Shared gate used by `MapScreen` + `NavigationScreen`

Both screens have identical forcing patterns, so `RendererGate` (internal, `auto` module) is shared rather than duplicated. `NavigationScreen` gets the same constructor deferral and buffered lifecycle. Rollback path stays a single revert.

### D5 — Order of replay on readiness

Surface dims/DPI before dark before viewport before GPS marker, then `requestRender()`; each slot applies last-wins so bursts collapse to one replay per value. Rationale: a surface is required for any frame; dark affects style loading; viewport/marker need both.

## Risks / Trade-offs

- [Renderer ready after screen destroyed] → init job cancelled on destroy; gate drops replay when the owning screen is no longer started (`isDestroyed`/scope-cancelled check).
- [Behavior regression in `NavigationScreen`] → same gate, same tests; existing auto-screen tests + full `:auto` suite must stay green; on-device nav run verifies.
- [First GPS fix dropped during the window] → gate stores the latest position slot; replayed on ready so the marker appears at the current position immediately.
- [Blank map surface briefly after template delivery] → strictly better than the pre-fix frozen main thread; matches today's behavior once the surface arrives; no extra UI (Non-Goal).
- [Thread-safety of gate state] → all slots written on the main thread (lifecycle/surface/observers are main); reader applies on ready on main; `AutoMapRenderer` itself already marshals its render loop on `Default`.

## Verification

- Unit: pure-Kotlin tests for `RendererGate` (order, last-wins, destroy-cancels, no-replay-after-destroy) and for the extracted viewport-resolution function (saved viewport beats map bbox beats default; bbox failure falls through) — no `CarContext` needed.
- Regression: `mapRenderer`/`client` first-touch assertions — unit test that `MapScreen` construction does not invoke `autoClientProvider()` before the readiness coroutine starts (mockk on `AutoEntryPoint` path where feasible; Robolectric sandbox rules from `AGENTS.md` apply for any class loading the JNI stub).
- Build: `./gradlew :auto:testDebugUnitTest` + `:app:assembleAutomotiveDebug -Pandroid.injected.build.abi=arm64-v8a`.
- On-device (AAOS emulator): `adb logcat -s NaviVeylin` — warmup step timestamps (`SessionLog.warmupStep`) show client build on `Default`; template delivered before renderer-ready log line; map first frame renders on surface delivery; follow re-center after a `geo fix` works without a stale frame.

## Migration Plan

Additive app-side change in `:auto`. Deploy: land with the normal flavor builds (mobile uses `:auto` too — both flavors rebuild). Rollback: single revert; the screens return to the blocking lazy path. No data migration, no native change.
