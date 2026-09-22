# Design

## Context

See `proposal.md` — Why. The technical state that shapes the approach:

- `AutoMapRenderer` no longer releases surfaces (change `fix-aaos-host-crash`,
  `auto/src/main/java/com/naviveylin/auto/AutoMapRenderer.kt`): `onSurfaceCreated` no longer releases
  a replaced surface, `onSurfaceDestroyed`/`shutdown` set the reference to null, and
  `detachSurface()` (`:576`) clears surface, size, `surfaceFailed` and the overrun buffer. That method
  has no caller.
- `SessionCarSurfaceHost` (`auto/src/main/java/com/naviveylin/auto/SessionCarSurfaceHost.kt`) is the
  single `SurfaceCallback`; it holds `active` + `owner`, a permanent identity set `released`
  (`:48`, used at `:200`), and hands the retained surface to a new owner from `attach()` (`:72`).
- Every screen builds its own `AutoMapRenderer` behind its own `RendererGate` (`MapScreen.kt:129`,
  `NavigationScreen.kt:211`, `FreeDrivingScreen.kt:185`, `DetailsScreen.kt:96`); screens
  `attach`/`resume` in `onStart` and `pause`/`detach` in `onStop`.
- `CarScreenObservations` (`auto/src/main/java/com/naviveylin/auto/CarScreenObservations.kt`) creates
  `CoroutineScope(SupervisorJob() + dispatcher)` per started period and `launch`es each observation
  body with no exception handler.
- `provideCarSurfaceHost()` is `@Singleton` (`app/src/main/java/com/naviveylin/di/AutoServiceModule.kt`)
  and `startSession()` returns early while `registered` (`:51`).
- Native constraints: the stylesheet flag reaches `dbThread->SetStyleFlag` in
  `libosmscout-client-java/src/OSMScoutClient.cpp`, i.e. a stylesheet reload scheduled from whatever
  thread calls it — a host callback must not be that thread (spec: Host callbacks answer promptly).
  Nothing in this change touches the submodule or the `:osmscout-client-java` overrides.

## Goals / Non-Goals

**Goals:**

- At most one renderer locks the session surface at any moment, including the window in which the
  host starts the incoming screen before stopping the outgoing one.
- Surface lifetime bookkeeping that survives the host's delivery patterns (replace, duplicate
  destroy, re-delivery of an instance) without over- or under-releasing.
- A session that starts after a dropped connection registers its own callback and never calls host
  APIs through a dead `CarContext`.
- No observation fault can kill the process; no host callback performs native work.
- Release the per-screen overrun frame buffer when its screen is not started (the memory half of the
  same stop path, TODO.md §62).

**Non-Goals:**

- Collapsing the four per-screen renderers into one session-scoped renderer (the root cause of both
  the concurrency and the memory ledger). Deferred, see Open Questions.
- The phone path, the notification/trip cadence, the native `knownPaths` race (TODO.md §48) and the
  tile-cache configuration (TODO.md §63).
- Any change to template content, screen hierarchy or user-visible labels.

## Decisions

### D1 — The outgoing owner is revoked at supersession, and a stopped screen detaches

Two complementary edits, both on the existing lifecycle seams:

1. `SessionCarSurfaceHost.attach(newOwner)`: when a different owner is already attached, the session
   tells that owner to stop drawing **before** the surface is handed to the new owner. `CarSurfaceOwner`
   (`core/src/main/java/com/naviveylin/core/CarSurfaceHost.kt`) gains
   `fun onCarSurfaceRevoked() { onCarSurfaceDestroyed() }` — a default that delegates to the existing
   destroy callback, so the four screens need no edit for the common case and a screen that later
   wants different revocation semantics can override it.
2. Each screen's `onStop` calls `rendererGate.detachSurface()` after `rendererGate.pause()`
   (`MapScreen.kt:322`, `NavigationScreen.kt:424`, `FreeDrivingScreen.kt:377`, `DetailsScreen.kt:252`,
   with a `detachSurface()` pass-through already present on `RendererGate` at `:320`).

Alternatives considered:

- *Revocation only* — closes the transition window but leaves a stopped renderer holding a surface
  reference and its ~3.7–8 MB overrun buffer (TODO.md §62), and a resumed renderer could blit a frame
  that predates the stop.
- *Detach only* — does not close the window: the host starts the incoming screen first, so the
  outgoing screen is still started (and still holds the surface) while the incoming one already
  renders.
- *Ownership predicate in the renderer* (`isCurrentOwner()` consulted before every lock) — closes the
  window too, but pushes session state into the renderer and its render thread; revocation at the
  single place that already knows the owner is smaller and testable without a real render.

Rationale: the session is the only component that knows which screen is the owner, so revocation
belongs there; the stop path is the only place that knows the screen's rendering life ended, so the
detach belongs there. Together they give the invariant "a renderer locks the surface only while its
screen is the current owner".

### D2 — Release bookkeeping stays identity-based, and adoption consults it

`released` stays as it is (identity set with weak keys) and gains one consumer: `onSurfaceAvailable`
checks it. An instance found in `released` is a re-delivery of a released surface: it is removed from
the set, the event is logged (a re-delivery after a destroy is worth knowing about), and the instance
is adopted as the live delivery. `onSurfaceDestroyed` and `endSession` keep their current structure.

Alternatives considered:

- *Current slot plus a "release owed" flag* (the fix candidate recorded in TODO.md §54) — cannot
  distinguish "already released" from "never adopted" once the slot is empty, so a duplicate destroy
  of a superseded instance would release twice: exactly the over-release the set prevents.
- *No set, release on every signal* — same over-release risk, and releasing a buffer queue twice is
  the one operation that breaks the host's compositor.
- *Per-delivery token handed to the owner* — precise, but it makes the screen's identity part of the
  release decision, which the session must own (D1).

Residual risk: `WeakHashMap` keys can be collected while the set is live; a collected key means the
`Surface` object is unreachable, and a later delivery is necessarily a different object, so no
double release can result.

### D3 — `startSession` registers per context, not once per process

`startSession(context)` keeps its idempotence for the same context and replaces the registration when
the context differs: `runCatching { old.getCarService(AppManager::class.java)?.setSurfaceCallback(null) }`
(guarded — the old context may already be dead), then register on the new one. The `registered`
boolean becomes "the context currently registered" (`CarContext?`), and `endSession()` clears it and
tolerates a context that is not the registered one.

Alternatives considered:

- *Make the surface host a per-session instance (`@Provides` instead of `@Singleton`)* — the cleanest
  model, but Hilt has no session component: the session *and* each screen resolve it through
  `AutoEntryPoint.autoSurfaceHost()`, so a per-session instance needs a manual scope holder with its
  own lifetime rules. It also does not by itself fix a session whose `endSession` never ran.
- *Reset in `endSession` only* (status quo) — the failure mode is precisely a session that never
  reaches `endSession` after the host dropped the connection.

### D4 — Fault confinement via a scope-level `CoroutineExceptionHandler`

`CarScreenObservations.start()` builds `CoroutineScope(SupervisorJob() + dispatcher + handler)`; each
observation is launched with `CoroutineName(key)` so the handler can log which key faulted. The
handler logs through `DiagnosticsLog` (non-blocking, worker-owned file I/O — see
`fix-diagnostics-log-host-path-io`) and does nothing else.

Alternatives considered:

- *`runCatching` around each `block`* — equivalent for ordinary exceptions but must rethrow
  `CancellationException`, and it would look like a caught-and-continued observation rather than an
  ended one; it also cannot see the key without threading it through the call.
- *Handler per observation* — same effect, more allocations and a handler per key to keep alive.

A scope-level handler is not invoked for normal cancellation (`stop()`), so the stopped-period
semantics of the capability are untouched.

### D5 — The day/night flag becomes published state; the tap path resolves the client late

The surface-delivery callback publishes the resolved presentation into the renderer gate (a
`MutableStateFlow<Boolean>` alongside `surfaceDpi`, `RendererGate.kt:64`) and returns; the existing
background collector (`MapScreen.kt:298-307` shape) applies it with
`client.setStyleSheetFlag("daylight", …)` inside `runCatching` on `Dispatchers.Default`.
`CarDaylightApplier` gains a one-shot "applied for this presentation and this client readiness" state
so a re-delivery does not re-push an unchanged flag. `MapScreen.onCarClick`
(`MapScreen.kt:619-631`) and `NavigationScreen`'s equivalent move
`entryPoint.autoClientProvider().client()` inside their existing `withContext(Dispatchers.Default)`
block.

Alternatives considered:

- *Fire-and-forget `Dispatchers.Default` push from the callback* — still does work in the host
  callback (scope + dispatch + the state read), and it re-pushes on every delivery.
- *Drop the surface-time push and rely on the dark-mode observer* — reintroduces the startup race the
  push exists for (the flag pushed before the DB was initialising is lost).
- *Lazily resolved client field on the screen* — first resolution still happens on the host thread at
  the first tap; §53 is exactly that defect.

### D6 — `pause()` stays a pure stop; the detach is a separate call

`AutoMapRenderer.pause()` keeps its current meaning (refuse new frames) and `detachSurface()` keeps
theirs (drop the surface reference and the overrun buffer). The screens compose them in `onStop`.
Alternatives: folding the detach into `pause()` — fewer call sites, but `pause()` is also the "app
backgrounded, surface still mine" case, where keeping the buffer is desirable to avoid a full render
on return.

## Risks / Trade-offs

- [An extra full native render per screen start (detach forces `clearOverrunBuffer`, `resume()`
  requests a render)] → accepted: a start already renders; measure with the `guidelines/Build.md` §10
  counters (`lock OK` per fix) and compare renders-per-fix against the current baseline (9/9/9 for
  three round trips with eight fixes).
- [Revocation could be delivered to a screen that is mid-render] → the renderer drops a frame whose
  surface is no longer current (`isCurrentSurface`, `AutoMapRenderer.kt:1492`) and `detachSurface()`
  runs under `surfaceLock`, so the render thread either sees the old or the null reference.
- [A swallowed observation fault hides a renderer bug] → both the log line and the diagnostics tag
  name the observation key; the "still rendering" scenario keeps the screen observable from the
  outside.
- [Re-registration on a dead `CarContext`] → every host call in the swap is `runCatching`-guarded;
  the registration state is only updated after the new registration succeeds.
- [Per-delivery release could release a surface the host already re-delivered to another callback] →
  the app has exactly one callback per session, so "another callback" cannot exist; the set is keyed
  by instance, and an instance is only ever released while it is not `active`.

## Migration Plan

No data, resource or manifest migration; nothing is persisted. The five edits are independent and can
land in any order except that D1's revocation and D2's adoption both touch `SessionCarSurfaceHost` and
should be one commit. Rollback is a revert: the previous behaviour (permanent release set, no
revocation, `@Singleton` early return, unguarded observations, native push in the callback) returns
without migration.

## Verification

- Unit (`:auto`): `SessionCarSurfaceHostTest` — new cases for the re-delivered instance, the duplicate
  destroy, adoption of a released instance, revocation of the previous owner at attach, and
  re-registration with a different context; `CarScreenObservationsTest` — a throwing observation
  leaves its siblings running and is logged with its key; `AutoMapRendererSurfaceOwnershipTest` /
  `RendererGateTest` — `detachSurface()` clears the surface, the overrun buffer and `surfaceFailed`,
  and a detached renderer does not lock; `MapScreenTest` — surface delivery performs no client call
  (fake client recording the calling thread) and the tap path resolves the client off the main thread.
- On-device (`guidelines/Build.md` §10, AAOS AVD, automotive debug): a push/pop cycle and a background
  round trip with injected fixes, asserting `Surface created` for the incoming screen with the same
  surface id, no `releasing session surface` inside the transition, no `lockCanvas failed` /
  `surface invalid`, and one `renderer#… detaching surface` per stop; plus `dumpsys meminfo` before and
  after to confirm the stopped screens' overrun buffers are gone.
- Guideline updates: `guidelines/Design.md` (surface release per delivery; a stopped renderer holds no
  surface), `guidelines/MapRendering.md` (the stop-path handoff and the overrun-buffer lifetime),
  `guidelines/UI.md` (car surface lifecycle), `guidelines/Build.md` §10 (two more triage counters).

## Open Questions

- Whether to collapse the four per-screen renderers into one session-scoped renderer (one surface, one
  renderer). It would remove the class of bug this change patches and the largest part of the car
  memory ledger, but it changes per-screen viewport ownership and is a change of its own.
- Whether this host really re-delivers the same `Surface` instance after a destroy. The spec's
  per-delivery rule covers both cases; the implementation logs which one happens, so a device run
  answers it without a further decision.
