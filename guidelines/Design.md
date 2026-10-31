# Design Guidelines — Architecture & Conventions

The architecture's normative rules — module layering, state, threading and the engineering principles — and the
index to the three documents the rest of the architecture is cut into. The four documents answer the four kinds
of change that read them: `Design.md` (this file: §1 module layering, §3 ViewModel & state, §4 threading, §12
engineering principles), `guidelines/DesignStack.md` (§2 stack & UI patterns, §9 data, persistence & search),
`guidelines/DesignNative.md` (§5 the native boundary, split into §5a and §5b) and
`guidelines/DesignCrossVariant.md` (§6 rendering pipeline, §7 follow mode & GPS, §8 Android Auto & cross-variant
split into §8a and §8b, §10 build & release, §11 testing). A section that moved keeps its number, and the index
entry below names the document that now carries it.

**Rule naming** — a rule begins with a bold `Rule:` token, and the first bold span of its bullet is the rule's
name; a name is unique across `guidelines/*.md`, and another document references it by the rule-reference form
the route check resolves.

**Maintenance rule** — when a change supersedes a principle in one of the four documents, update that document in
the same change; the supersession itself is recorded in `guidelines/Design.why.md` §13.

**Severity** — **MUST** = hard invariant, breaking it is a bug. Unmarked = strong preference.

---

## 1. Module layering

- **Rule: only `:app` touches the JNI layer and Android context-bound storage** **MUST**: `:app` is the only module that touches the JNI layer and Android
  context-bound storage.
- **Rule: `:core` holds the shared, Android-lean logic both variants use** `:core` = shared, Android-lean logic used by both `:app` and `:auto`
  (resolution, formatting, projection, prediction, mappers). `:auto` depends
  on `:core` + the JNI jar, **never** on `:app`.
- **Rule: a shared behaviour lives once, in `:core` beside the state it reads** Shared **behaviour** lives in `:core` next to the state it reads, so a rule
  cannot exist twice: `Manoeuvre`/`manoeuvreFromStep`, `turnTypeFor`,
  `ManoeuvreLabels`, `ManeuverSymbols`, `RouteStepGuidance` and the route-row
  policy (`RouteRows.routeRows`, `RouteRow`) are read by both variants. A
  surface that needs a rule the other one already applies imports it — it does
  not derive a second version (change `share-route-rows-in-core`).
- **Rule: `:auto` consumes app functionality through `:core` provider interfaces** `:auto` consumes app functionality through provider interfaces defined in
  `:core`, implemented in `:app` with Hilt. Keep providers focused; split when
  they grow.
- **Rule: one shared state contract between the variants, extended additively** One shared state contract between variants (`NavigationState`); extend it
  additively (new fields defaulted) so existing code compiles unchanged.
- **Rule: one app module serves all form factors via resource qualifiers** Single app module for all form factors via resource qualifiers / window
  size classes.

## 2. Stack & UI patterns

Moved to `DesignStack.md` §2 — the stack and UI-pattern rules. The recipes for adding a dependency, a screen, a
full-screen sheet, a details sheet or a native function are procedure and live in `guidelines/Design.recipes.md` §2.

## 3. ViewModel & state

- **Rule: one ViewModel per concern, owning its state as one immutable UiState `StateFlow`** One ViewModel per concern; each owns its state as an immutable UiState data
  class in a single StateFlow. Cross-ViewModel communication via collected
  flows, never two-way references.
- **Rule: state co-locates with the data it displays** State co-locates with the data it displays. Don't create a ViewModel for
  state a screen already owns, and don't push purely presentational logic
  (formatting, grouping) into one — it belongs in the composable layer.
- **Rule: displayed frames are emitted atomically in one emission** **MUST**: displayed frames are emitted atomically — bitmap, viewport, and
  overlay snapshot travel in ONE emission. Never combine separate sub-flows
  for what is drawn; mixed-frame states jump.
- **Rule: a derived signal is a state field, not a separate flow** Prefer a state field over a separate flow for derived signals.
- **A `Map` is never the carrier for "same contents, different order".** A `StateFlow` drops an emission
  equal to its current value and `Map.equals` ignores iteration order, so an order-only change never reaches
  a collector — it "works" only while the values happen to be identity-unequal (`FavoriteLocation` read
  through the JNI bridge: accidental, not designed). Give a sequence its own `List` channel and pair it with
  the contents through a shared helper (`orderedGroupNames`, `:core`), so an entry the channel does not name
  yet cannot vanish from a list. Before leaning on a derived value as a contract, ask what makes it **change**,
  not what it contains (spec `group-ordering`; `guidelines/UI.md` §1).
- **Rule: every async operation surfaces an explicit, user-actionable error state** Every async operation has an explicit error state surfaced to the user;
  errors are user-actionable; the process never crashes on data inconsistency.
- **Rule: settings persist as one `@Serializable` JSON, fields defaulted and decoded leniently** Settings persist as one `@Serializable` AppSettings JSON; new fields need
  defaults and must decode leniently; enum names are stable on disk.
- **Rule: a value that drives several layers is resolved once and fanned out** Where one value drives several layers (e.g. theme + native style flag),
  resolve it once in a single source and fan out — one truth, no divergence.
- **Rule: process-global state two surfaces share has one owning seam** Process-global state that two surfaces share (location subscription, settings
  file, native cache capacity, car-session presence) is owned by one seam with an
  explicit rule, never by per-surface start/stop pairs — see §12, "Single owner per
  process-global resource".
- **Rule: navigation state is process-owned by exactly one engine** **MUST**: navigation state is process-owned. Exactly one process-scoped
  `NavigationEngine` (`:app`, `@Singleton`, bound to `core.NavigationViewModel`,
  resolved by the car through `AutoEntryPoint`) owns the native
  `NavigationController`, the navigation session, route acquisition and the
  reroute policy (spec `navigation-engine`, change `one-navigation-engine`).
  Every surface — phone map, Android Auto, Android Automotive OS — is a pure
  observer of that one state.
- **Rule: per-surface view state never moves into the engine** **MUST**: per-surface view state never moves into the engine. Follow mode, free
  driving, viewport, zoom, map rotation, vehicle anchor preset, overlay layout and
  render state stay with the surface that displays them; starting or stopping
  navigation on one surface must not move another surface's view (spec
  `navigation-engine` — Per-surface view state never moves into the engine).
  The phone keeps a thin surface adapter (`NavigationViewModel`) that owns those
  reactions — follow mode for a session the phone itself started, the route
  panel view, the mode snapshot/restore.
- **Rule: cross-thread shared state uses atomics or volatiles, read at execution time** Cross-thread shared state uses atomics/volatiles; flags read at job
  execution time, never snapshotted into queued work.
- **Rule: platform-object side effects live in `DisposableEffect`, not the ViewModel** Platform-object side effects (window refs, keep-screen-on) live in
  `DisposableEffect`, not the ViewModel.
- **Progress and wall-clock state start at the invariant, not at the data-class default** (§40.61): initialise
  a progress state to "0 % travelled" (`remainingDistance = totalDistance`) rather than the field default,
  and give a state machine with wall-clock state a "never set" sentinel plus an explicit first-event branch —
  otherwise the first tick reads a plausible-looking zero that was never measured.

## 4. Threading

- **Rule: native/JNI code is never called on the main thread** **MUST**: never call native/JNI code on the main thread; run it on
  background dispatchers with timeouts and loading UI. This includes the car-app
  host callbacks (they run on the main thread by library contract): they may only
  retain the state they received — resolving a provider, a database query or a
  file read there blocks the host's call and can take the host down.
- **Rule: a diagnostic handler never performs filesystem work on its caller** **MUST**: a diagnostic handler never performs filesystem work on its caller.
  `DiagnosticsLog.log`/`logThrowable` buffer the line in memory (plus a logcat
  mirror) and one worker thread owns the file — the caller's thread may be a car
  host callback, and the log must not be the reason it answers late. Readers
  (`readEntries`/`exportText`) do read the file: call them from a background
  dispatcher (`readEntriesAsync`/`exportTextAsync`), never from a template build
  or from composition. The one deliberate exception is the uncaught-exception
  handler, which writes its trace on the dying thread — a buffered line might
  never be flushed there, and a crash trace must land.
- **Rule: native callbacks are marshalled to the main thread through the ViewModel scope** Native callbacks arrive on native threads — marshal state updates to the
  main thread via the ViewModel scope.
- **Rule: the navigation engine follows the process-lifetime rule** **MUST**: the navigation engine follows the process-lifetime rule (`:app`
  `NavigationEngine`, change `one-navigation-engine`): one
  `SupervisorJob + Dispatchers.Main` scope created once and never cancelled (the
  process is its owner, so it has no `onCleared`), native calls (route
  calculation, controller start/stop, road lookup) on `Dispatchers.Default`/`IO`,
  and navigation-state publication on the main dispatcher so surface collectors
  stay main-confined. `stopNavigation()` is its release point: it stops the native
  controller, resets the reroute gate and stale-speed state, clears the position
  flow, releases the location lease and stops the current voice utterance.
- **Rule: the engine's scope carries its own fault handler** **MUST**: the engine's scope carries **its own fault handler**
  (`engineFaultHandler`, change `fix-navigation-engine-fault-isolation`): a fault in
  any engine coroutine — the stale-speed ticker, route acquisition, the location
  feed, a road lookup — is recorded (`ENGINE_FAULT`, with the faulting coroutine and
  the first stack frame, no coordinates) and raised as an engine-wide error on the
  main dispatcher, and the engine ends the affected attempt through `stopNavigation()`
  while staying usable afterwards. A `SupervisorJob` does not swallow a failure: an
  uncaught throwable reaches the main thread's uncaught handler and kills the
  process, and the engine is what every surface — including a live car session the
  template host is bound to — depends on.
- **Rule: the engine's native boundary confines any throwable, not only exceptions** **MUST**: the engine's native boundary is confined for **any throwable, not only
  exceptions** (`catch (e: Throwable)` at the route-calculation, start and road-lookup
  calls): a linker or class-initialization failure (`UnsatisfiedLinkError`,
  `ExceptionInInitializerError`, `NoClassDefFoundError`) is an `Error`, so an
  `Exception` catch lets it escape the engine and kill the process. Confine it, report
  it, keep the app alive — a road-name lookup stays cosmetic and never stops
  navigation.
- **Rule: the engine holds no location subscription outside navigation** **MUST**: the engine holds no location subscription outside navigation. It
  acquires the refcounted lease (`LocationService.acquire(LocationConsumers.NAV_ENGINE)`)
  on navigation start, releases it on stop/arrival, and never calls
  `startLocationUpdates()` itself; no surface feeds the engine its own fixes.
- **Rule: a route attempt releases only the location lease it took** **MUST**: a route attempt releases a location lease only when **it** took it
  (change `fix-reroute-lease-release`, spec `navigation-engine` — A failed route attempt
  releases only the lease it took). `calculateAndStart` captures
  `ownsLease = !state.isNavigating` before its native call, and both failure handlers
  (`onError` and the synchronous `catch`) release only when it is true: a reroute that
  fails while guidance is live reuses the lease the running navigation took
  (`startInternal`), and releasing it stops device updates by the last-release rule
  (`location-updates-lease`) until some other consumer takes a lease — the guidance then
  runs on a frozen position with no error that would explain it. The cancel path
  (`cancelAcquisition`) applies the same ownership rule, so a new attempt path that takes
  its own lease MUST state its ownership rather than assume the failure path fits.
- **Rule: the attempt that raised a shared-state flag clears it on every terminal outcome** **MUST**: the attempt that raised a shared-state flag clears it on **every** terminal
  outcome, through one writer. `isRerouting` is the worked case (change
  `fix-reroute-failure-state-flags`, spec `rerouting-visual-feedback` — Rerouting state is
  exposed in navigation state): `confirmReroute` sets it, `onRouteInstructions` clears it on
  success, and the two failure handlers plus `cancelAcquisition` clear it through
  `NavigationState.withoutRerouteAttemptFlag()`. A flag whose only clearer is the success
  path stays set for the whole session when the attempt fails, and the car maps
  `isRerouting` to a loading trip with no turn (`NavigationTemplateMapper`), so the driver
  gets a spinner instead of guidance. Place the clear **after** the `endCalculation` token
  guard: a superseded attempt failing late must not end the attempt a newer request runs.
  The sibling state keeps its own rule — a failed or cancelled reroute does **not** clear
  `isOffRoute`, because the vehicle is still off route.
- **Rule: prefer coroutines over raw threads** Prefer coroutines over raw threads: conflated channels/StateFlow for
  queues, debounce by delay, cancellation via viewModelScope.
- **Rule: high-frequency inputs are debounced before they reach an expensive path** Debounce high-frequency inputs (search keystrokes, GPS-driven renders)
  before they hit expensive paths.
- **Rule: a recurring re-evaluation does not return to the main dispatcher between periods** **MUST**: a recurring re-evaluation must not return to the main dispatcher
  between periods. Launch its loop *on* the background dispatcher it ticks on and
  let it report a **change** only — the stale-speed ticker
  (`MapCanvasViewModel`, `NavigationEngine`) and the fix-quality ticker do this:
  `Dispatchers.Default`, the real clock, period/clock/limit as test hooks
  (`TODO.md` §40.C.16), and the derivation itself re-run on the background
  dispatcher so that an unchanged value costs no main-dispatcher traffic at all.
  A loop whose home is the ViewModel scope reads `Dispatchers.Main` once per
  period from a real thread pool; in production that is a dispatcher read, and for
  a state holder that outlives its test it fails a later test with
  `Dispatchers.Main is used concurrently with setting it` (`guidelines/Build.md`
  §4). Keep the publication path — including its debounce — the single writer, so
  a tick-detected change is published exactly like an input-detected one.
- **Rule: the render-target pool's lock is never held across a renderer lock** **MUST**: never hold the render-target pool's lock across a renderer lock or
  a render/draw. `RenderBitmapPool.acquire`/`release` (change
  `fix-render-buffer-reuse`, spec `render-performance`) take and drop the pool
  lock for bookkeeping only; the car takes `surfaceLock` and the phone
  `MapRenderer.bufferLock` **after** acquiring and **releases at the end of**
  the critical section, so no lock order exists between the pool and a
  renderer, and no suspension point sits inside the display lock.
- **Rule: one source of truth per data signal** One source of truth per data signal; consumers never re-derive provider or
  pipeline knowledge.
- Play Services capabilities are optional: access via provider abstraction
  with runtime availability check and fallback (Fused vs LocationManager) —
  never a hard dependency.
- **Read the *constant's* deprecation text before building on a lifecycle signal.** A method can survive
  while the levels it reports are never delivered: `ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW`,
  `_RUNNING_CRITICAL`, `_RUNNING_MODERATE`, `MODERATE` and `COMPLETE` carry "Apps are not notified of this
  level since API level 34", and `onLowMemory()` is deprecated since API 35 — a release designed on that
  ladder would have shipped **inert** on Android 14+ while this app targets 36. The working signals are the
  still-delivered levels (`TRIM_MEMORY_UI_HIDDEN`, `TRIM_MEMORY_BACKGROUND`) plus a poll of
  `ActivityManager.MemoryInfo` (`lowMemory`/`availMem`/`threshold`). Two rules from the same defect class:
  pass intent as an explicit flag, never infer it from a display string (a trigger label `poll-low` compared
  against `"poll"` silently scoped the poll out), and assert the **negative** case (a comfortable device
  releases nothing) — the positive cases alone pass on an unguarded implementation.
- **A process-scoped ticker a test constructs keeps running for the whole suite's JVM.** Give every
  long-lived loop a test switch (and `runCatching`-wrap the body: a poll must never take anything down with
  it) — a responder built with polling left enabled fired 30 s later inside an unrelated Robolectric test.
- **In a bounded writer, the "here is what is missing" marker must not live in the ring it describes** — the
  bound evicts it (carry the count in the flush instead); the write target must be captured **with the
  batch** (a writer that reads its target at write time writes one target's lines into another); and a
  high-water flush only carries the entries present at the crossing, not the tail.
- **Rule: a dispatcher hop inside a suspend accessor needs its own test seam** **MUST**: a dispatcher hop inside a suspend accessor needs its own test seam. `isLocationSourceEnabled()`
  hopped to `Dispatchers.Default` to keep a binder call off the main thread, which made a virtual-time test
  non-deterministic (green alone, red with the suite) — the fix is an injectable dispatcher
  (`locationSourceDispatcher`), never a test-side timeout or a longer advance. Same rule as the tickers
  above: production code earns a seam wherever a test must control time.

## 5. Native boundary

Moved to `DesignNative.md` §5 (split into §5a, the boundary's shape, and §5b, the bridge's two sides).

## 6. Rendering pipeline

Moved to `DesignCrossVariant.md` §6.

## 7. Follow mode & GPS

Moved to `DesignCrossVariant.md` §7.

## 8. Android Auto & cross-variant

Moved to `DesignCrossVariant.md` §8 (split into §8a, the car host path and the surface, and §8b, the
screen-observation seam, parity and distribution).

## 9. Data, persistence & search

Moved to `DesignStack.md` §9.

## 10. Build & release

Moved to `DesignCrossVariant.md` §10.

## 11. Testing

Moved to `DesignCrossVariant.md` §11.

## 12. General engineering principles

- **Rule: single source of truth - shared logic is extracted once** **Single source of truth**: shared logic is extracted once into `:core` or a
  named helper — never duplicated per variant, screen, or module. The render
  target's reuse rule has exactly one owner: `RenderBitmapPool` (`:core`) hands
  out, takes back and recycles the ARGB_8888 targets both renderers draw into
  (change `fix-render-buffer-reuse`, spec `render-performance`) — a renderer
  never keeps a reuse rule of its own and never recycles a pooled target.
  The same rule covers a shared UI row: when two hosts put a tap area on it, that area is one
  parameter of the shared composable, not a copy of the control per host — `NavigationStatsRow`
  exposes `leadingModifier` for the strip a caller makes tappable, so the phone card's details tap
  and the stop control keep one implementation and one owner (change `fix-nav-overlay-stop-tap`,
  spec `navigation-status-details`; the tap-target rule itself is `guidelines/UIPhoneMap.md` §8b).
  The route's visibility has one owner at a time: **navigation** while it runs, and the **open
  session in its stopped state** for the grace window — the engine adapter clears the route on a stop
  only when no session took it over, and a session that ends (its grace expiring included) is the one
  that clears it and closes its surface (change `fix-route-session-stop-path`, spec
  `route-planning-session` — Grace period after navigation is stopped, Ending the session removes its
  surface). No stop control holds that decision of its own: the phone has one session-aware stop path
  and no second one beside it. The same "one owner per surface" rule holds for a *band* the phone
  shares between two composers: the session card and the navigation status card sit on the same bottom
  edge, and composing both hid the review entirely — the card that is shown owns
  the band and the other is not composed while it is (change `fix-route-session-stop-path`, design D8).
  The phone's overlay *stacking* has one owner as well: the five bands of `guidelines/UIPhoneMap.md` §11 decide
  which element may paint over which, so a band is not re-argued per composable and map chrome never
  lands on top of an open surface (change `fix-phone-map-layer-stack`, spec `map-canvas-screen`).
- **Rule: single owner per process-global resource** **Single owner per process-global resource**: when the phone UI and a car
  session share one process (always the case under Android Auto projection), the
  process-wide resources have explicit owners instead of per-surface toggles
  (change `shared-resource-arbitration`):
  - **Rule: location updates are leased, never toggled** **Location updates are leased**, never toggled: `LocationService.acquire(consumer)`
    hands out a `LocationLease` (consumers: `phone-map`, `phone-nav`, `car-session`,
    `car-nav`), updates run while at least one lease is held, and the last release
    stops them. A lease is released when its owner ends — the opposite of
    `DrivingModeProvider`'s retain-on-death semantic, because "who wants GPS?" must
    not outlive its owner while "is driving active?" must not be cleared by a dying
    surface. Consumer names are roles, not surfaces, so backgrounding one phone
    surface cannot cut the fixes another still needs (spec `location-updates-lease`).
  - **Rule: settings writes are transactions** **Settings writes are transactions**: `SettingsStorage.update { }` performs
    read-modify-write under one lock on top of the existing settings file; a writer
    persists only the fields it owns, and `save(document)` is routed through
    `update` so a whole-document write cannot interleave either. `load()` takes the
    same lock — a read must never observe a half-written file (spec
    `settings-persistence`).
  - **Rule: the native tile-data cache keeps the highest requested capacity** **The native tile-data cache keeps the highest requested capacity** and is never
    lowered by a *configuration* request (`NativeTileDataCache`), so the effective value does not depend
    on which surface opened databases first; a car-only process still runs on the car value
    (spec `native-tile-data-cache`). One deliberate exception: `MemoryPressureResponder` *releases*
    retention (`NativeTileDataCache.trim`) when the device is low on memory — a 30 s poll of
    `ActivityManager.MemoryInfo` — or when the platform reports the UI hidden / the process backgrounded
    **while no car session is live**; nothing native runs on the callback thread, and the release only
    lowers a cache capacity (never rendered content). The `onTrimMemory` levels it does *not* use and why:
    see `guidelines/MapStyling.md` §17.
  - **Rule: the car-session-presence signal is process-scoped and in-memory only** **The car-session-presence signal** (`CarSessionPresence`) is process-scoped and
    in-memory only: the phone shows an advisory indication and disables nothing. The
    phone UI is deliberately not locked while a car session is live (spec
    `car-session-presence`).
- **Reuse over reinvention**: reuse proven mechanisms (invalidation paths,
  existing pipelines, projection helpers, controllers) instead of adding
  parallel implementations.
- **One owner per concern**: an abstraction (ViewModel, repository, provider)
  is justified by a real second consumer — not by structure.
- **Rule: no dead code** **No dead code**: dead code contradicts specs and invites misuse; remove it
  and fix the spec in the same change. Keep spec ↔ code in sync.
- **Rule: keep contracts stable** **Keep contracts stable**: mirror upstream APIs, absorb platform
  differences in adapters, change shared/native behavior only as a last
  resort.
- **Make it testable**: extract pure functions from drawing and UI assembly;
  keep presentation composables callback-driven.
- **Rule: survive restarts** **Survive restarts**: user choices and state that must persist live in the
  settings/persistence layer — in-memory-only resets are inconsistent.
- **Rule: additive changes** **Additive changes**: land changes additively; keep rollback a simple
  revert; prefer defaulted fields over data migrations.
- **Rule: a separate screen over a mode flag** **Separate screen over mode flag**: when two states have different
  lifecycles, state, or exit semantics, use a separate screen, not a mode
  parameter.
- **Change hygiene when editing files (patch tooling).** One file per `edit` call when the anchors come from
  different files, and every `edits[]` entry holds exactly `oldText` + `newText` — a prose note key or a
  foreign anchor rejects the whole atomic call (three instances in one week). One writer per file per turn:
  a `sed -i` and an `edit` in the same message clobber each other, because the edit writes the file from its
  own read — use a single tool for both changes. Split a large or function-spanning `oldText` on function
  boundaries and never re-issue a rejected fragment unchanged (`TODO.md` §40.9–§40.11).
- **Never retype prose or code from memory into `oldText`** (§40.10) — copy it from the read output. Never
  emit overlapping or nested `oldText` regions in one call; for a pure insertion keep the whole matched block
  in `newText` and append to it. After a rejected `oldText`, **re-read the file before re-issuing** (§40.11):
  a corrective pass can silently duplicate a helper, and the duplicate then makes exact-match edits ambiguous
  — re-read every changed region before compiling, and make each anchor unique with its distinguishing
  neighbour line (§40.9).
- **Reason from the measurement, not from the model.** "No record in my run" is evidence about the run, not
  about the code: read the trigger's *condition* and construct it before writing a finding
  (`MemoryInfo.lowMemory` was reported unreachable and then fired mid-walk on the same build). A design
  table is a hypothesis, not evidence — read every assignment to a buffer before planning to remove it (one
  bitmap was counted three times as three separate holdings, so two tasks would have "saved" ~30 MB that
  never existed). And a measurement is meaningless without its context: quote the UI state, the render count
  and the session presence next to any footprint number (`guidelines/Build.md` §10).
- **Verify the premise before building a change on it** (§40.56): read what pins it (the tile-path condition
  in `TileCacheRenderTest`), cross-check the number through a second path (`ProjectionUtils` for the real
  pixels-per-degree, rather than re-deriving DPI × `cos(lat)` with the formula under test), and locate the
  "single source of truth" before a dedup change — removing the duplicate must not remove the only instance.

## 13. Superseded decisions

Moved to `guidelines/Design.why.md` §13; the provenance table is `guidelines/Design.why.md` Appendix B.

