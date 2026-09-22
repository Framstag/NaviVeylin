# Design

## Context

See `proposal.md` — Why. Constraints that shape the approach, established by reading the car-app
1.7.0 sources and the current code:

- **Host callbacks run on the app main thread** (`AppManager.setSurfaceCallback` javadoc;
  `RemoteUtils.dispatchCallFromHost` → `ThreadUtils.runOnMain`) and an exception thrown inside one is
  rethrown on that thread (`RemoteUtils.java:153`) — i.e. it kills the process.
- **A host callback that does not return promptly is a host problem, not an app problem.** Nothing in
  the app can observe the host's timeout, which is why promptness is a hard requirement.
- **Screen lifecycle ordering** (`ScreenManager.java`): `pushInternal` → new screen `ON_START` (:330)
  then old screen `ON_STOP` (:336); `popInternal` → new top `ON_START` (:363) then popped screen
  `ON_STOP`+`ON_DESTROY` (:371); app `ON_PAUSE`/`ON_STOP` stops the top screen (:475-485). Every
  `:auto` screen currently registers its `SurfaceCallback` in `onStart` and unregisters in
  `onDestroy`, so a transition re-registers the callback *before* the outgoing screen's `onStop`
  runs.
- **One host-side surface per session**: the app side holds several `Surface` objects wrapping what
  the code already documents as one shared buffer queue ("the host reuses ONE display surface across
  screens"), and each renderer releases "its" object independently
  (`AutoMapRenderer.onSurfaceCreated`/`onSurfaceDestroyed`/`releaseSurface`/`shutdown`).
- **The notification and the trip are the only things the host renders while the app is
  backgrounded**; both are fed from collectors that are not lifecycle-gated.
- `guidelines/Design.md` §4 (no native calls on the main thread), §8 (surface lifecycle is an
  invariant); `guidelines/UI.md` §1/§3/§3a; `guidelines/MapRendering.md` §14/§15a.

**In-flight overlap:** `auto-navigation-hints` (change `car-turn-by-turn-rail-widget`) owns the
rail-widget notification contract and the trip-publishing cadence; `navigation-ongoing-notification`
(change `background-navigation-notification`) owns the notification's content and lifecycle. This
change's cadence/fault rules live in the new capability so both can archive in any order; a task
reconciles the wording.

## Goals / Non-Goals

**Goals:**

- No car-facing action of the app can kill or wedge the host: callbacks answer promptly, faults
  degrade, `invalidate()` is main-thread, one owner per surface.
- Bounded host traffic and bounded periodic native work for a backgrounded driving session.
- The app's own diagnostics are sufficient to correlate a host failure after the fact.

**Non-Goals:**

- Not the `ClientData::knownPaths` JNI race (app-process crash, submodule patch, own change).
- Not the render-pipeline performance work beyond the cadence bound (per-frame overlay allocations,
  the 1.2× overrun cost) — a rendering-perf change.
- Not the phone UI or the phone renderer; not the map download path.
- Not the host's own bugs: the change removes the app's ability to trigger them, and the bisect runs
  only *attribute* a reproduction.

## Decisions

### D1 — Surface lifetime: session-owned registration, single owner

Chosen: a session-scoped surface host registers the `SurfaceCallback` **once per session** (with the
session's own lifecycle) and dispatches to the current owner screen; a screen adopts the surface into
its renderer and releases it only when the host reports it destroyed or the session ends. Surface
attributes that arrive while there is no owner (during a transition) are retained.

- Alternatives: (a) keep per-screen registration, identity-guard the unregister and drop the
  release-on-stop — smaller diff, but the host still receives a re-registration per screen start, and
  the library's new-screen-before-old-stop ordering stays a hazard; (b) keep release-on-stop plus the
  `invalidate()`/`MAX_SURFACE_REFRESH_ATTEMPTS` recovery — rejected: that recovery *is* the observed
  defect (the outgoing screen releases a producer the incoming screen draws through, and the app
  answers by asking the host for another surface).
- Consequences: the surface's Java object is owned by one component, so cross-release is structurally
  impossible; the session must forward surface events to the owner, and the renderer's "surface
  destroyed" path becomes an explicit session signal. `AutoMapRenderer` loses its
  `previous.release()`/`releaseSurface()` ownership duties and keeps only "stop drawing".

### D2 — The native client is never built on the host thread

Chosen: the car providers (`AutoClientProvider`, `AutoFavoritesProvider`, `AutoLocationProvider`,
`AutoSettingsProvider`) and `FavoriteRepository` are injected as `Provider`/`Lazy`, so resolving a car
provider in a screen constructor or a host callback does not construct `OSMScoutClient`; host
callbacks only buffer `surfaceDpi` (the `RendererGate`'s pending-slot mechanism already exists for
this), and the client is built inside the session's background warmup or the screen's init coroutine.
The client build's duration and thread are logged.

- Alternatives: (a) hand the screens a `CompletableDeferred<OSMScoutClient>` and make every use
  suspend — more invasive, changes many call sites, and the screens already have a readiness path;
  (b) block the host's `onCreateScreen` until warmup finishes — rejected: it makes the host wait
  exactly as long as the build; (c) keep the eager injection and only document it — rejected: it is
  the defect.
- Consequence to watch: the phone path keeps its eager injection; only the car providers become lazy.

### D3 — Fault isolation at the boundary, not per call site

Chosen: (a) a `SurfaceCallback` decorator wraps every `:auto` screen's callback in a guarded
invocation (log + degrade), so a throwing callback can never reach the process; (b) the host-facing
API calls (`NavigationManager.navigationStarted/Ended/updateTrip/setNavigationManagerCallback/
clearNavigationManagerCallback`) are guarded in `NavigationManagerController` **including the trip
construction**, which currently sits outside the `runCatching`; (c) the notification build/post path is
guarded.

- Alternatives: (a) per-call-site `try/catch` — rejected: it is the current state and it was missed
  at exactly the call that matters; (b) a `CoroutineExceptionHandler` on the session scope —
  rejected: it swallows the fault after the state is already wrong and does not cover the main-thread
  rethrow path; (c) make the mapper total (`carTripFor` never throws) — the mapper cannot guarantee
  that for library validators, so it is used as an extra layer, not as the isolation mechanism.

### D4 — `invalidate()` is marshalled to the main thread

Chosen: the screen owns the marshalling — the renderer's surface-failure callback and any background
component call a screen-scoped `requestTemplateRefresh()` that posts to the main thread (the screens
already run on `Dispatchers.Main`).

- Alternatives: (a) let the renderer call `Screen.invalidate()` and rely on the library — rejected:
  the javadoc requires the main thread and races `onGetTemplate`; (b) a global main-`Handler` helper
  in `:core` — acceptable, but the screen scope is already the right lifetime and keeps the failure
  path (screen stopped → no refresh) in one place.

### D5 — Content-deduplicated notification posts, absolute style-flag pushes

Chosen: a pure "did the host-visible notification content change" comparison (manoeuvre, distance
bucket, current road, remaining distance, arrival second, free-driving street/speed) drives the
re-post, mirroring `NavigationTemplateMapper.hasTripChanged`; the day/night flag is pushed by
comparing against the **last pushed value** instead of resetting the applier's dedup before every
push (the current reset makes every 5 s settings re-read push a flag and force a full render).

- Alternatives: (a) time-based throttle (e.g. at most one post per 2 s) — rejected: it neither
  prevents a flood when the content changes quickly nor keeps content fresh when it changes slowly;
  (b) post only on manoeuvre change — rejected: distance/remaining-time staleness is user-visible;
  (c) keep `reset()` and compare natively — rejected: the native compare still costs a stylesheet
  reload per push, which is the work being removed.

### D6 — Lifecycle-gated host mutations, ungated guidance

Chosen: the navigation session's observers keep running while the app is backgrounded (guidance and
the cluster/HUD must keep updating), but the *host-mutating* actions are gated on the session being
started: no `ScreenManager.push`/`popToRoot`, no `AppManager.invalidate()`, no
`navigationStarted/Ended` transition from a stopped session. On session start the state is
re-synchronised (a screen sync + navigation-state re-check), so a transition that happened while
stopped is applied once instead of being lost.

- Alternatives: (a) stop the observers while stopped — rejected: it would stop the guidance updates
  the capability requires; (b) keep the mutations and rely on the library to ignore them — rejected:
  they are host IPC/template churn from an invisible app, exactly the traffic this change bounds;
  (c) gate everything including trip publishing — rejected (see above).

### D7 — One full render in flight

Chosen: the full-render request from the extrapolation loop is gated on "no full render in flight"
plus the measured duration of the last render (interval = max(current constant, last render duration)),
so a slow render cannot be re-queued behind itself while the loop still serves sub-region blits.

- Alternatives: (a) raise the fixed interval to e.g. 500 ms — rejected: it loses responsiveness where
  renders are fast and still oversubscribes on a slow head unit; (b) drop the full-render request
  entirely when blits are servable — rejected: the display would drift past the overrun margin and
  the map would stop following; (c) a dedicated single-slot channel — equivalent, more machinery.

### Threading and lifecycle model (new/changed components)

| Component | Thread | Lifecycle |
|---|---|---|
| Session surface host (`:auto`) | main only (library dispatches there) | created with the session, registers the callback once, unregisters + releases on session destroy |
| Screen surface adoption / renderer stop | main only | screen `onStart` adopts, `onStop` stops drawing (no release), `onDestroy` detaches from the renderer; renderer's loops are gated per renderer |
| Renderer init coroutine | `Dispatchers.Default` for the heavy work, constructed + published on main | cancelled on screen destroy; a constructed-but-unpublished renderer is shut down (spec: `auto-map-renderer`) |
| Notification content collector (`:app` service) | main | service `onDestroy` cancels; post only on content change |
| Session observers (`:auto`) | main | started with the session; host mutations gated on the session being started; cancelled on session destroy |
| `NavigationManagerController` | main (library contract) | guarded calls; `navigating` flag owned by the session's started state |

## Risks / Trade-offs

- **[Dropping release-on-stop re-introduces the "host re-delivers a locked surface" symptom]** →
  the session-owned registration means the host sees one registration per session and is told about
  the end (`setSurfaceCallback(null)` on session destroy), so a fresh surface is requested once, not
  per transition; the existing surface-failure diagnostics (now main-thread-marshalled) stay as the
  safety net; verified on the AVD by the screen-transition run (proposal's bisect set) before the
  change is archived.
- **[DI lazification changes the phone path]** → only the four car providers and
  `provideFavoriteRepository` gain `Provider`/`Lazy`; a unit test asserts that resolving them builds
  nothing, and the phone suite must stay green.
- **[Gating the session observers drops a state transition that happened while stopped]** → the
  session-start resync applies the current state once; a unit test drives the transition while
  stopped and asserts exactly one application after start.
- **[Cadence bound delays a legitimate re-render]** → the bound is
  `max(existing constant, last measured duration)`, so it never exceeds the render cost; the
  diagnostics counters (`fullRenderCount`, `lock OK` log lines) give the measurement.
- **[Host crash is the host's own bug]** → the change still removes the app's triggers and the bisect
  runs attribute the reproduction; a surviving host with the notification extender disabled is a
  legitimate mitigation (the rail widget is the only car surface the app feeds while backgrounded).

## Migration Plan

No data, settings or manifest migration; both flavours ship as one change. Land in this order so each
step is independently verifiable and revertable: D1 (surface ownership, largest) → D3/D4 (fault
isolation + main-thread invalidate) → D2 (DI laziness) → D5/D6/D7 (cadence, gating) → diagnostics →
guideline updates → verification runs (unit, build, AVD). Rollback is `git revert` per step; nothing
here has a persistent side effect, so no feature flag is needed.

## Open Questions

None that can be deferred: the surface-lifetime rule (D1) is the one decision that could change the
specs, and it is settled in the spec (`car-host-fault-isolation` — "Single-owner car surface") with the
alternatives above recorded.
