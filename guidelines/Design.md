# Design Guidelines — Architecture & Conventions

Common design principles distilled from the OpenSpec changes (`openspec/changes/**`)
and consolidated specs (`openspec/specs/**`).

**Document map** — pick the right doc: `Design.md` = architecture principles
(this file); `UI.md` = UI rules (phone + Android Auto); `MapRendering.md` =
render-pipeline details and pitfalls; `AGENTS.md` = project facts, build,
logging, stylesheet mechanics.

**Maintenance rule** — when a change supersedes a principle here, update this
document in the same change (see §13 for how supersessions are recorded).

**Severity** — **MUST** = hard invariant, breaking it is a bug. Unmarked =
strong preference.

---

## 1. Module layering

- **MUST**: `:app` is the only module that touches the JNI layer and Android
  context-bound storage.
- `:core` = shared, Android-lean logic used by both `:app` and `:auto`
  (resolution, formatting, projection, prediction, mappers). `:auto` depends
  on `:core` + the JNI jar, **never** on `:app`.
- `:auto` consumes app functionality through provider interfaces defined in
  `:core`, implemented in `:app` with Hilt. Keep providers focused; split when
  they grow.
- One shared state contract between variants (`NavigationState`); extend it
  additively (new fields defaulted) so existing code compiles unchanged.
- Single app module for all form factors via resource qualifiers / window
  size classes.

## 2. Stack & UI patterns

- Single-activity architecture: screens are composables, not activities.
  Compose + Material 3, Navigation Compose, ViewModel + StateFlow, Hilt,
  Coroutines. Google Play Services may be used (Fused location, car
  MapController) but MUST NOT be a hard dependency — provider abstraction,
  runtime availability check, and fallback (e.g., LocationManager) keep it
  replaceable by design. No Google Maps SDK (rendering is libosmscout); no
  Google account required; sideloading is a supported distribution path.
- Persistence is JSON-file based (JNI favorites, settings, search history);
  don't add a database layer that duplicates what the native layer already
  persists.
- Bottom sheets are the standard overlay pattern; full-screen sheets for
  management surfaces that keep the map alive underneath. Both compose on top
  of the map screen via state flags — no nav-graph changes.
- Always-visible navigation info lives in overlays on the map canvas, not
  dialogs.
- Dialogs that must cover a bottom sheet live at top-level screen scope
  (separate windows can hide behind the sheet window).

## 3. ViewModel & state

- One ViewModel per concern; each owns its state as an immutable UiState data
  class in a single StateFlow. Cross-ViewModel communication via collected
  flows, never two-way references.
- State co-locates with the data it displays. Don't create a ViewModel for
  state a screen already owns, and don't push purely presentational logic
  (formatting, grouping) into one — it belongs in the composable layer.
- **MUST**: displayed frames are emitted atomically — bitmap, viewport, and
  overlay snapshot travel in ONE emission. Never combine separate sub-flows
  for what is drawn; mixed-frame states jump.
- Prefer a state field over a separate flow for derived signals.
- Every async operation has an explicit error state surfaced to the user;
  errors are user-actionable; the process never crashes on data inconsistency.
- Settings persist as one `@Serializable` AppSettings JSON; new fields need
  defaults and must decode leniently; enum names are stable on disk.
- Where one value drives several layers (e.g. theme + native style flag),
  resolve it once in a single source and fan out — one truth, no divergence.
- Cross-thread shared state uses atomics/volatiles; flags read at job
  execution time, never snapshotted into queued work.
- Platform-object side effects (window refs, keep-screen-on) live in
  `DisposableEffect`, not the ViewModel.

## 4. Threading

- **MUST**: never call native/JNI code on the main thread; run it on
  background dispatchers with timeouts and loading UI. This includes the car-app
  host callbacks (they run on the main thread by library contract): they may only
  retain the state they received — resolving a provider, a database query or a
  file read there blocks the host's call and can take the host down.
- **MUST**: a diagnostic handler never performs filesystem work on its caller.
  `DiagnosticsLog.log`/`logThrowable` buffer the line in memory (plus a logcat
  mirror) and one worker thread owns the file — the caller's thread may be a car
  host callback, and the log must not be the reason it answers late. Readers
  (`readEntries`/`exportText`) do read the file: call them from a background
  dispatcher (`readEntriesAsync`/`exportTextAsync`), never from a template build
  or from composition. The one deliberate exception is the uncaught-exception
  handler, which writes its trace on the dying thread — a buffered line might
  never be flushed there, and a crash trace must land.
- Native callbacks arrive on native threads — marshal state updates to the
  main thread via the ViewModel scope.
- Prefer coroutines over raw threads: conflated channels/StateFlow for
  queues, debounce by delay, cancellation via viewModelScope.
- Debounce high-frequency inputs (search keystrokes, GPS-driven renders)
  before they hit expensive paths.
- **MUST**: never hold the render-target pool's lock across a renderer lock or
  a render/draw. `RenderBitmapPool.acquire`/`release` (change
  `fix-render-buffer-reuse`, spec `render-performance`) take and drop the pool
  lock for bookkeeping only; the car takes `surfaceLock` and the phone
  `MapRenderer.bufferLock` **after** acquiring and **releases at the end of**
  the critical section, so no lock order exists between the pool and a
  renderer, and no suspension point sits inside the display lock.
- One source of truth per data signal; consumers never re-derive provider or
  pipeline knowledge.
- Play Services capabilities are optional: access via provider abstraction
  with runtime availability check and fallback (Fused vs LocationManager) —
  never a hard dependency.

## 5. Native boundary

- **MUST**: the native core stays platform-pure — no Android dependencies
  outside the frozen upstream dir, enforced by a CI gate. Platform
  integration is an app-owned bridge (log sink, load order).
- Keep the JNI surface small and explicit: integer handles for native
  objects, batched marshaling, hot paths stay in C++.
- **MUST**: C++ exceptions convert to Java exceptions at the boundary; native
  code never aborts the process (asserts kill it).
- Native method contracts are API; fix behavioral issues in Kotlin, not by
  changing native semantics.
- A defect *inside* the library behind an unchanged JNI contract (e.g. its
  transliteration or matching logic) is fixed upstream as a minimal,
  upstreamable patch instead of being worked around in Kotlin: a workaround
  would have to guess at the library's internal semantics and would leave
  every other caller of the same code broken.
- Mirror upstream APIs exactly so submodule syncs stay clean; keep submodule
  patches minimal and upstreamable.
- Android-specific deviations live as local overrides in a bridge module,
  never patched into the submodule.
- Serialize writes in native shared services; a render mutex serializes
  concurrent renders.
- Logging: native via `osmscout::log` + app-owned bridge; Kotlin via
  `android.util.Log` with per-class TAG. Stylesheets: submodule is the single
  source of truth, synced at build time. Mechanics: `AGENTS.md`.

## 6. Rendering pipeline

- **MUST**: double-buffered rendering — native writes to the back buffer, the
  front buffer is the only display source, swap is atomic under a lock.
- Stale renders are discarded by epoch comparison; epochs increment on
  meaningful changes (zoom/rotation/style/overlay), never on pure position
  updates — a running render must be allowed to finish.
- Render larger than the screen (overrun) and blit sub-regions for small
  pans; full re-render only beyond the margin.
- **MUST**: caches hold only immutable content keyed by render-affecting
  state; overlay or position changes never invalidate them. Overlays are
  layered on top of the display frame, never baked into buffers or tiles.
- Emit frames only on change; every path must deliver a correctly-sized
  frame.
- Mode/style switches invalidate everything (epoch bump + cache clear +
  forced full render); style flags are pushed absolutely, never toggled.
- Rotation changes force a full render so labels render correctly; keep angle
  math normalized and consistent with the native convention.
- Overlay projection always uses the viewport of the frame actually
  displayed, and the same projection math as the renderer.
- Performance budget: hot paths are allocation-free (no per-frame
  bitmap/array churn); measure before optimizing; keep renders under the frame
  budget.
- Pipeline details and pitfalls: `guidelines/MapRendering.md`.

## 7. Follow mode & GPS

- **MUST**: the marker sits on the raw (or navigation-filtered) fix; only the
  camera is smoothed — a smoothed marker drifts off the road.
- One signal, two bearings: heavy smoothing for map rotation (churn-free),
  freshest signal for the marker arrow (lag-free). Signal quality is derived
  in the location layer; render protection (deadbands, rate clamps, throttle)
  stays in the render layer.
- Derive rotation from course history, not raw bearing; reset history on
  sharp turns and teleports.
- Between fixes, extrapolate the display (fix + speed × heading × Δt) and
  ease corrections on fix arrival. **MUST**: prediction is display-only — the
  navigation engine receives real fixes only. Gate the loop when stationary,
  follow off, or the surface is invalid.
- Prefer receiver speed over derived speed; clamp conservatively while
  decelerating; treat zero/unknown speed as stopped.
- Details: `guidelines/MapRendering.md`.

## 8. Android Auto & cross-variant

- The session owns a simple screen stack (push/popToRoot); no routing
  library. Replace, don't stack, when a screen supersedes another (navigation
  replaces the map).
- Respect host constraints — they are invariants: pane rows are
  non-actionable, list actions are icon-only, hosts may not forward surface
  gestures. Use action strips and pane buttons accordingly; interactive
  actions are parked-only where the host supports it. Prefer host-rendered
  panels where positioned/controllable; draw on the surface only what the
  host cannot render, inside the stable area.
- **MUST**: the car-app surface lifecycle is strict and **session-owned** —
  one surface-callback registration per session, one owner screen at a time, and
  the surface released exactly once: on the host's destroy signal or at session
  end, never by a screen that merely stops. The library starts the incoming
  screen *before* it stops the outgoing one, so a release in `onStop`
  disconnects the buffer queue the incoming screen is drawing through (observed
  as `lockCanvas` failures the host then re-delivers around). A host destroy is
  **scoped to the surface instance it names**: a destroy of a superseded surface
  (the host delivered a newer one first — AAOS does re-deliver after a transition)
  releases that instance only and must not clear or release the live one.
  Lifetime is tracked **per delivery**, not per instance: an instance delivered
  again after its release is a new delivery (it may be drawn on and is released
  once more), and a released instance is never adopted for drawing.
  **One renderer locks the surface at a time**: the session revokes the surface
  from the owner it supersedes (`CarSurfaceOwner.onCarSurfaceRevoked`, delivered
  from `attach`) *before* the incoming owner is told it may draw, and a screen
  that stops detaches (`rendererGate.detachSurface()`), so a stopped screen's
  renderer holds no surface reference and no overrun frame buffer and re-acquires
  the surface through the session on its next start. A session registers with
  **its own** `CarContext` (a second session replaces the registration), so a
  session that follows a dropped connection never calls host APIs through a dead
  host. Pause/resume renderers on screen stop/start; unlock in `finally`; validate
  before drawing; never lock or draw a released or replaced surface; recover from a
  dead surface with a **main-thread**, capped invalidate.
- **MUST**: nothing native runs on a host callback or in a screen constructor —
  car providers are resolved off the host thread (lazy `Provider`/`Lazy`), a
  host callback only retains state (including the surface **tap** path and the
  **surface delivery**: the native client is resolved inside the background block,
  and the stylesheet day/night flag is *published* (`RendererGate.daylightPush`) and
  applied by a background collector instead of being pushed from
  `onCarSurfaceAvailable`, which reloads the style variant on the DB thread), and no
  fault escapes a host callback or a host-facing path (trip build, host navigation
  call, notification build/post, frame draw): the car-app library rethrows an app
  exception on the main thread, which kills the process. Degrade and log instead.
  **Every** car screen's template build goes through the `car*Template` wrappers
  (`SafeScreen.kt`) — the map/navigation/free-driving screens were guarded while
  eleven other screens' `onGetTemplate` could still kill the process.
- **MUST**: **every host mutation goes through the guarded seam**, not only the
  template builds and the host callbacks. `CarHostGuards.kt` holds
  `guardedHostCall(what, tag) { … }` (one `ScreenManager` push/pop/popToRoot/remove,
  confinement + a `HOST` diagnostics entry per rejection, unthrottled),
  `armScreenPush(carContext, scope, what) { screen }` and
  `armShowOnMapSwap(...)` for the click paths — a template row action **arms** the
  navigation and returns: the target screen is built and pushed on the main thread
  afterwards, never on the host's answering path (the library rethrows there, and
  building a screen resolves the entry point, first-touches providers and starts a
  renderer), and `dismissErrorNotice(...)` for the deferred notice teardown. A
  rejection is a logged no-op, and each of the four `ScreenManager` operations has
  exactly one seam call site per screen — never a bare `screenManager.push(...)` in a
  click listener.
- **MUST**: every **scope that owns host-mutating work carries the shared fault
  handler** (`carFaultHandler` / `carSessionScope` / `carScreenScope`): the session
  scope, each car screen's own scope (including the renderer's) and the screen
  observation seam (`CarScreenObservations`). An exception from a coroutine body
  otherwise reaches the main thread's uncaught handler and kills the process — an app
  process that dies while a car session is live is what takes the templates host down
  with it. A confined fault ends that piece of work (one child of the scope's
  `SupervisorJob`), never a sibling.
- **MUST**: the session's **screen-stack bookkeeping follows the mutation that
  succeeded** (`SessionScreenStack`, `FreeDrivingRestoreGate.recordPush(landed)`): a
  push the host refused records nothing, so the next state emission retries instead of
  leaving the driver without a navigation view. Popping is scoped: the stack is popped
  back to the root only for the session's own transitions, and a transient overlay
  (the error notice) is removed by identity (`ScreenManager.remove`) — `popToRoot()`
  took the navigation view down with the notice while the session still believed it
  was shown. A host mutation **scheduled** while the session was started (the notice's
  auto-dismiss) is discarded when it would run after the session stopped or ended, and
  its removal is owed to the next started sync.
- **MUST**: **every car screen builds its own scope through `carScreenScope(name)`**
  and cancels it in `onDestroy` — never `CoroutineScope(SupervisorJob() +
  Dispatchers.Main)` by hand, which is how the fault handler went missing. A click
  path that arms a push relies on that cancellation: a screen popped away before its
  deferred push runs dies with its scope instead of pushing onto the new stack.
- **MUST**: while the car app is not the visible car app, host traffic is
  bounded — the session mutates no host state (no screen push/pop, no template
  invalidate, no host navigation-state change) and the ongoing notification is
  re-posted only when its host-visible content changed; guidance updates and
  trip metadata keep flowing. A template is rebuilt only when its displayed content
  changes materially: the distance values are compared as the host displays them
  (the shared rounding), not metre by metre, and a template asset the host receives
  per rebuild (the lane-guidance image) is reused while its state is unchanged.
  A session restores a still-active free-driving mode **at most once**.
- **MUST**: a car screen's shared-state observations are scoped to its **started
  period** — one instance of each per start, all cancelled on stop. The host stops
  and starts a screen on every background round trip and on every push/pop of
  another screen, so per-collector job bookkeeping leaks: the screen it belonged to
  ran a second copy of the GPS, favorites, dark-mode and basemap observations after
  every start, and each copy kept requesting renders, native lookups and template
  refreshes (the count grew monotonically with the number of starts).
  `CarScreenObservations` (`:auto`) owns the lifetime and a per-screen
  `<Screen>Observations` class owns what is observed; a stopped screen touches
  neither the renderer nor the host, and work that must survive a stop (e.g. the
  free-driving stale-speed ticker) stays on the screen's own scope. See
  `openspec/specs/auto/screen-observation/spec.md`.
- Cross-variant parity: same labels and visual hierarchy wherever the
  platform allows; deviate only as much as required. Shared logic and data
  have one source; one variant's UI is a thin adapter.
- **MUST**: distribution — phone and AAOS are separate build flavors under one
  applicationId; the automotive flavor declares the automotive hardware
  feature and drops the projection metadata. A single AAB declaring both is
  rejected by Play.
- OSM attribution is visible on the map and the licence is reachable at all
  times (OSMF guidelines), on both flavors.
- UI rules and parity decisions: `guidelines/UI.md`.

## 9. Data, persistence & search

- Maps and other large files live in app-internal storage; downloads use
  temp-suffix files so partials are never picked up; register storage with
  the native layer at startup. Long-running work (downloads) keeps the
  process alive (wake lock / foreground service).
- Persist after completed operations, not debounced; persist on every
  meaningful change, not only lifecycle events (crash safety). Repositories
  wrap JNI calls in background dispatchers and expose reactive state.
- Use Android-compatible HTTP (`HttpURLConnection`); avoid `java.net.http`.
- Expensive scoped lookups are resolved once, cached, and reused within a
  movement threshold; release native handles on close.
- External data access (contacts etc.) is read-on-demand, never persisted,
  and permission-gated.
- **MUST**: search never crashes the process, whatever the data consistency.
- **MUST**: name matching is the map library's job: the app passes the query through
  and never re-implements matching per surface, so the phone dialog and the car
  template match identically (spec: `search-name-matching`). A matching defect
  behind an unchanged JNI contract is fixed in the library (§5), never filtered or
  patched around per surface.

## 10. Build & release

- One version-bumping command, gated at configuration time (versions are
  consumed there); state survives clean builds; version exposed via
  BuildConfig. Everything else is deterministic and never touches version
  state.
- CI pins toolchain commits (dependency-cache stability), derives all config
  from env, verifies silently-tolerated steps explicitly, and uses caches
  aggressively.
- Native libs aligned for 16 KB pages; R8 keep rules for JNI bridge classes.
- Mechanics: `AGENTS.md`.

## 11. Testing

- Extract pure, JVM-testable logic from rendering and template code; test
  state transitions rather than timing; use injectable seams for provider
  branches.
- **MUST**: JNI-dependent unit tests use the host stub and the Robolectric
  classloader rule — never mix sandbox configs on tests that load the native
  stub.
- Compose UI tests for gesture/panel logic: extract gesture handlers into
  reusable `Modifier` factories; keep presentation composables
  callback-driven.

## 12. General engineering principles

- **Single source of truth**: shared logic is extracted once into `:core` or a
  named helper — never duplicated per variant, screen, or module. The render
  target's reuse rule has exactly one owner: `RenderBitmapPool` (`:core`) hands
  out, takes back and recycles the ARGB_8888 targets both renderers draw into
  (change `fix-render-buffer-reuse`, spec `render-performance`) — a renderer
  never keeps a reuse rule of its own and never recycles a pooled target.
- **Reuse over reinvention**: reuse proven mechanisms (invalidation paths,
  existing pipelines, projection helpers, controllers) instead of adding
  parallel implementations.
- **One owner per concern**: an abstraction (ViewModel, repository, provider)
  is justified by a real second consumer — not by structure.
- **No dead code**: dead code contradicts specs and invites misuse; remove it
  and fix the spec in the same change. Keep spec ↔ code in sync.
- **Keep contracts stable**: mirror upstream APIs, absorb platform
  differences in adapters, change shared/native behavior only as a last
  resort.
- **Make it testable**: extract pure functions from drawing and UI assembly;
  keep presentation composables callback-driven.
- **Survive restarts**: user choices and state that must persist live in the
  settings/persistence layer — in-memory-only resets are inconsistent.
- **Additive changes**: land changes additively; keep rollback a simple
  revert; prefer defaulted fields over data migrations.
- **Separate screen over mode flag**: when two states have different
  lifecycles, state, or exit semantics, use a separate screen, not a mode
  parameter.

## 13. Superseded decisions

- Dual-mode single APK (phone + AAOS in one artifact,
  `2026-08-22-add-android-automotive-os`) → two-flavor split
  (`specs/android-automotive-os`) — Play rejects the combination.
- GPS markers baked into the native render (`2026-07-29-draw-map`, auto
  phase 3) → display-layer overlays (`2026-08-14-fix-marker-visibility`) —
  cached buffers must stay marker-free.
- AA instruction hints surface-drawn (archived D4/W1) → host-rendered panel
  (`2026-08-23-aa-navigation-view`) — one source of instruction UI, never
  both.
- Bearing smoothing in the ViewModel → location layer
  (`2026-08-28-gps-bearing-smoothing`) — signal quality in the location
  layer, render protection in the render layer.
- Single render path with `blitSubRegion` → TILES/DIRECT split
  (`2026-08-17-render-mode-switch`) — dead code removed, spec corrected.

---

## Appendix A — Quick checklist (apply phase)

- [ ] JNI/native calls off the main thread, with timeouts + loading UI?
- [ ] Native callbacks marshaled to the main thread?
- [ ] Displayed frame emitted atomically (bitmap + viewport + marker)?
- [ ] Overlays never baked into buffers or tiles?
- [ ] Epoch bumped only on meaningful changes?
- [ ] Mode/style switch invalidates cache + forces full render?
- [ ] Marker on raw fix; only the camera smoothed?
- [ ] AA surface released on stop/destroy; renderer paused/resumed?
- [ ] Same labels in both variants where platform allows?
- [ ] New settings fields defaulted + lenient decode?
- [ ] Shared logic in `:core`, not duplicated?
- [ ] Change additive; rollback = simple revert?
- [ ] Pure logic JVM-testable; tests added?
- [ ] Spec ↔ code in sync (`openspec validate`)?

## Appendix B — Provenance

| Section | Key sources (`openspec/`) |
|---|---|
| 1 | `changes/archive/2026-07-26-android-project-setup`, `2026-08-27-search-address-book-persons`, `2026-08-05-auto-phase-2` |
| 2 | `2026-07-26-android-project-setup`, `2026-07-31-route-panel-ui`, `specs/app` |
| 3 | `2026-08-14-fix-marker-visibility`, `2026-08-03-dark-mode`, `2026-08-17-render-mode-switch` |
| 4 | `2026-07-29-add-location-search`, `2026-08-01-turn-by-turn-navigation`, `2026-08-13-gps-render-coalescing` |
| 5 | `2026-08-21-remove-android-logging-from-libosmscout`, `2026-08-11-search-fixes`, `2026-08-13-basemap`, `specs/osmscout-jni` |
| 6 | `specs/double-buffering`, `specs/tile-cache`, `specs/canvas-overrun`, `2026-08-17-render-mode-switch`, `2026-08-18-render-performance` |
| 7 | `2026-08-13-gps-render-coalescing`, `2026-08-28-gps-bearing-smoothing`, `2026-08-28-phone-follow-smoothing`, `specs/smooth-follow` |
| 8 | `2026-08-05-auto-phase-2/3`, `2026-08-22-free-driving-mode`, `2026-08-22-aa-ui-redesign`, `specs/android-automotive-os`, `specs/cross-variant-ui-parity` |
| 9 | `2026-07-28-initial-ui-map-download`, `2026-07-29-manage-favorites`, `2026-08-14-improved-fulltext-search`, `2026-08-27-search-address-book-persons` |
| 10 | `2026-08-19-release-target`, `2026-08-18-github-build` |
| 11 | `2026-08-03-arrow-renderer`, `2026-08-28-gps-strict-fallback`, `2026-08-14-fix-two-finger-rotation`, `AGENTS.md` |
| 12 | all of the above |
