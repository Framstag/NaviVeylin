# Design cross-variant guidelines — rendering, follow, the car path, build and testing

The seams between the phone and the car variant and the build/test rules that follow from them, carried over
under their own numbers §6 (rendering pipeline), §7 (follow mode & GPS), §8 (Android Auto & cross-variant, split
into §8a the car host path and the surface, and §8b the screen-observation seam, parity and distribution), §10
(build & release) and §11 (testing), from the `Design.md` document this cluster was cut out of.

**Rule naming** — a rule begins with a bold `Rule:` token, and the first bold span of its bullet is the rule's
name; a name is unique across `guidelines/*.md`, and another document references it by the rule-reference form
the route check resolves.

---

## 6. Rendering pipeline

- **Rule: double-buffered rendering - the front buffer is the only display source** **MUST**: double-buffered rendering — native writes to the back buffer, the
  front buffer is the only display source, swap is atomic under a lock.
- Stale renders are discarded by epoch comparison; epochs increment on
  meaningful changes (zoom/rotation/style/overlay), never on pure position
  updates — a running render must be allowed to finish.
- Render larger than the screen (overrun) and blit sub-regions for small
  pans; full re-render only beyond the margin.
- **Rule: caches hold only immutable content; overlays are never baked in** **MUST**: caches hold only immutable content keyed by render-affecting
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

- **Decide who paces a transition before coding it** (§40.58): when two layers can both own it, pick
  render-paced or fix-paced first; never call a stateful controller twice for a check-then-use pair (the
  second call sees the state the first one just moved); and scope a renderer-wide rule behind an explicit
  opt-in flag instead of a global branch.

## 7. Follow mode & GPS

- **Rule: the marker sits on the raw fix; only the camera is smoothed** **MUST**: the marker sits on the raw (or navigation-filtered) fix; only the
  camera is smoothed — a smoothed marker drifts off the road.
- One signal, two bearings: heavy smoothing for map rotation (churn-free),
  freshest signal for the marker arrow (lag-free). Signal quality is derived
  in the location layer; render protection (deadbands, rate clamps, throttle)
  stays in the render layer.
- Derive rotation from course history, not raw bearing; reset history on
  sharp turns and teleports.
- **Rule: prediction between fixes is display-only** Between fixes, extrapolate the display (fix + speed × heading × Δt) and
  ease corrections on fix arrival. **MUST**: prediction is display-only — the
  navigation engine receives real fixes only. Gate the loop when stationary,
  follow off, or the surface is invalid.
- Prefer receiver speed over derived speed; clamp conservatively while
  decelerating; treat zero/unknown speed as stopped.
- Details: `guidelines/MapRendering.md`.

## 8. Android Auto & cross-variant

The cross-variant rules, split under this unchanged number: §8a is the car host path and the surface, §8b the
screen-observation seam, cross-variant parity and distribution.

## 8a. The car host path and the surface

- **Every car guidance surface reads the step model** (change `route-steps-live-guidance`):
  `NavigationTemplateMapper` builds the host `Maneuver` from the step's manoeuvre kind and move
  (`maneuverTypeForStep`, with the exit ordinal on the number-bearing type), the cue and road from
  `ManeuverLabels`, and the trip's step estimate from `remainingMeters` / `remainingSeconds`. The
  roundabout exit number is supplied **both** to the host and as the app's own numbered glyph, because
  which of the two a given host draws is not observable from a test here. `ManeuverGlyphs.forStep` is
  the only icon entry point a car surface needs.

- The session owns a simple screen stack (push/popToRoot); no routing
  library. Replace, don't stack, when a screen supersedes another (navigation
  replaces the map).
- Respect host constraints — they are invariants: pane rows are
  non-actionable, list actions are icon-only, hosts may not forward surface
  gestures. Use action strips and pane buttons accordingly; interactive
  actions are parked-only where the host supports it. Prefer host-rendered
  panels where positioned/controllable; draw on the surface only what the
  host cannot render, inside the stable area.
- **Rule: the car-app surface lifecycle is strict and session-owned** **MUST**: the car-app surface lifecycle is strict and **session-owned** —
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
- **Rule: nothing native runs on a host callback or in a screen constructor** **MUST**: nothing native runs on a host callback or in a screen constructor —
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
- **Rule: every host mutation goes through the guarded seam** **MUST**: **every host mutation goes through the guarded seam**, not only the
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
- **Rule: every scope that owns host-mutating work carries the shared fault handler** **MUST**: every **scope that owns host-mutating work carries the shared fault
  handler** (`carFaultHandler` / `carSessionScope` / `carScreenScope`): the session
  scope, each car screen's own scope (including the renderer's) and the screen
  observation seam (`CarScreenObservations`). An exception from a coroutine body
  otherwise reaches the main thread's uncaught handler and kills the process — an app
  process that dies while a car session is live is what takes the templates host down
  with it. A confined fault ends that piece of work (one child of the scope's
  `SupervisorJob`), never a sibling. **Unless that piece of work is one iteration of a
  continuing loop**: a fault then ends the *iteration*, not the loop (change
  `fix-car-render-loop-restart`, spec `car-host-fault-isolation`). The car map's three loops
  (render consumer, extrapolation/blit, zoom walk) each run their body through
  `RenderLoopSupervisor.runIteration(...)`, which skips the iteration, records it (loop name +
  throwable class, never a message or a coordinate) and 
  counts it; the loop keeps drawing. Before that, one throwing frame ended the render
  consumer for good — every later `requestRender()` was dropped and the car map stayed frozen
  with healthy host chrome until the driver re-entered the screen (`TODO.md` §51). A loop must
  never be silently abandoned: repeated consecutive faults (`CONSECUTIVE_RENDER_FAULT_THRESHOLD`
  within `RENDER_FAULT_WINDOW_MS`) re-create the renderer through the gate (bounded by
  `MAX_RENDERER_RECOVERIES_PER_PERIOD` per started screen period, re-armed in `onStart`), and
  when the budget is spent the map says so instead of drawing nothing — see
  `guidelines/MapCar.md` §14. A loop iteration **must not swallow
  `CancellationException`**: a cancelled scope has to end the loop, or nothing can stop it.
  The **process-scoped navigation engine** the
  session shares its process with is part of this rule (change
  `fix-navigation-engine-fault-isolation`): its scope carries `engineFaultHandler`, so
  an engine fault is confined in the engine — recorded and raised as an engine error —
  and never takes the session, its host traffic or the process down.
- **Rule: the session's screen-stack bookkeeping follows the mutation that succeeded** **MUST**: the session's **screen-stack bookkeeping follows the mutation that
  succeeded** (`SessionScreenStack`, `FreeDrivingRestoreGate.recordPush(landed)`): a
  push the host refused records nothing, so the next state emission retries instead of
  leaving the driver without a navigation view. Popping is scoped: the stack is popped
  back to the root only for the session's own transitions, and a transient overlay
  (the error notice) is removed by identity (`ScreenManager.remove`) — `popToRoot()`
  took the navigation view down with the notice while the session still believed it
  was shown. A host mutation **scheduled** while the session was started (the notice's
  auto-dismiss) is discarded when it would run after the session stopped or ended, and
  its removal is owed to the next started sync.
- **Rule: every car screen builds its own scope through carScreenScope(name)** **MUST**: **every car screen builds its own scope through `carScreenScope(name)`**
  and cancels it in `onDestroy` — never `CoroutineScope(SupervisorJob() +
  Dispatchers.Main)` by hand, which is how the fault handler went missing. The one
  deliberate exception is the process-scoped engine's own scope (`:app`
  `NavigationEngine`), which is created once for the process lifetime and carries
  `engineFaultHandler` for exactly that reason. A click
  path that arms a push relies on that cancellation: a screen popped away before its
  deferred push runs dies with its scope instead of pushing onto the new stack.
- **Rule: host traffic is bounded while the car app is not visible** **MUST**: while the car app is not the visible car app, host traffic is
  bounded — the session mutates no host state (no screen push/pop, no template
  invalidate, no host navigation-state change) and the ongoing notification is
  re-posted only when its host-visible content changed; guidance updates and
  trip metadata keep flowing (spec: `car-host-fault-isolation` — Trip metadata publishing is bounded by the
  navigation period: the trip send is what the start/end pair does not defer, the collector takes that
  decision from `tripSendAllowed`, and `:auto`'s `checkTripSendGate` refuses a collector that asks
  `SessionHostGate.allowHostMutation()`). A template is rebuilt only when its displayed content
  changes materially: the distance values are compared as the host displays them
  (the shared rounding), not metre by metre, and a template asset the host receives
  per rebuild (the lane-guidance image) is reused while its state is unchanged.
  A session restores a still-active free-driving mode **at most once**.
## 8b. The screen-observation seam, cross-variant parity and distribution

- **Rule: a car screen's shared-state observations are scoped to its started period** **MUST**: a car screen's shared-state observations are scoped to its **started
  period** — one instance of each per start, all cancelled on stop. The host stops
  and starts a screen on every background round trip and on every push/pop of
  another screen, so per-collector job bookkeeping leaks: the screen it belonged to
  ran a second copy of the GPS, favorites, dark-mode and basemap observations after
  every start, and each copy kept requesting renders, native lookups and template
  refreshes (the count grew monotonically with the number of starts).
  `CarScreenObservations` (`:auto`) owns the lifetime and a per-screen
  `<Screen>Observations` class owns what is observed; a stopped screen touches
  neither the renderer nor the host, and work that must survive a stop (e.g. the
  free-driving stale-speed ticker) stays on the screen's own scope. **All five screens** run their
  observations through that seam — the browse map (GPS position, favorites, resolved dark
  presentation, basemap revisions), the destination details (GPS position, favorites, basemap
  revisions), the favorites list (favorites + stored group order + stored starred order as one
  combined observation), navigation and free driving — while a screen's own one-shot work (renderer
  construction and publish, an address lookup, a settings load) stays on its `carScreenScope`, because
  it must run once per screen instance, not once per started period. Every observed source is a
  `StateFlow`, so re-establishing an observation on start applies the current value exactly once —
  that is how a change made while the screen was stopped reaches it, and it is why `auto-favorites`'
  "update when the stored order changes while the screen is open" means the **started period**: an
  observation kept alive across a stop is the defect this rule exists to prevent. See
  `openspec/specs/auto/screen-observation/spec.md`.
- **Rule: cross-variant parity - same labels and visual hierarchy wherever the platform allows** Cross-variant parity: same labels and visual hierarchy wherever the
  platform allows; deviate only as much as required. Shared logic and data
  have one source; one variant's UI is a thin adapter.
- **Rule: phone and AAOS are separate build flavors under one applicationId** **MUST**: distribution — phone and AAOS are separate build flavors under one
  applicationId; the automotive flavor declares the automotive hardware
  feature and drops the projection metadata. A single AAB declaring both is
  rejected by Play.
- **Rule: OSM attribution is visible and the licence is reachable at all times** OSM attribution is visible on the map and the licence is reachable at all
  times (OSMF guidelines), on both flavors.
- UI rules and parity decisions: `guidelines/UI.md`.
## 10. Build & release

- **Rule: one version-bumping command, gated at configuration time** One version-bumping command, gated at configuration time (versions are
  consumed there); state survives clean builds; version exposed via
  BuildConfig. Everything else is deterministic and never touches version
  state.
- **Rule: CI pins toolchain commits and derives all config from env** CI pins toolchain commits (dependency-cache stability), derives all config
  from env, verifies silently-tolerated steps explicitly, and uses caches
  aggressively.
- **Rule: native libs are aligned for 16 KB pages; R8 keeps JNI bridge classes** Native libs aligned for 16 KB pages; R8 keep rules for JNI bridge classes.
- Mechanics: `AGENTS.md`.

## 11. Testing

- **Rule: extract pure, JVM-testable logic from rendering and template code** Extract pure, JVM-testable logic from rendering and template code; test
  state transitions rather than timing; use injectable seams for provider
  branches.
- **Rule: JNI-dependent unit tests are designed around the host stub's classloader rule** **MUST**: JNI-dependent unit tests are designed around the host stub's classloader rule — one sandbox
  config per JVM; `guidelines/Build.md` §6 states the rule itself and its failure mode.
- **Rule: Compose UI tests extract gesture handlers into reusable `Modifier` factories** Compose UI tests for gesture/panel logic: extract gesture handlers into
  reusable `Modifier` factories; keep presentation composables
  callback-driven.
- **Check a helper's *visibility* before designing a test around it.** `internal` in `:core` is unreachable
  from `:app`/`:auto` tests (`DiagnosticsLog.flushNow`/`awaitDrained`/`workerThreadOrNull`), a JUnit rule
  exposed through a public property cannot be `internal`, and a public class cannot take an internal
  parameter type. Assert the property structurally instead, and read the KDoc of the seam you mean to drive.
- **Assert what the contract requires — never an invented count.** Read (or measure) the current behaviour
  before asserting a call/persist/emission count: a `StateFlow` re-delivers its value to every new collector
  (snapshot before the emission, assert the delta), a stress test must not compare two observations of shared
  state (derive every assertion from a single read, and stop all writers before the final-state assertion),
  and a count the code never had makes a test fail on **correct** code. Three documented instances (favorites
  persist, `CarScreenObservations`, `DiagnosticsLog`) — treat it as a reflex, and prefer re-expressing the
  assertion against the spec over pinning a number.
- **Assert the invariant, not "nothing happened", and make the whole failure path runnable.** An
  asynchronous seam makes emptiness/absolute assertions racy: assert the specific value, or the host-thread
  invariant, and poll with a bound. A test of fault confinement must be able to execute *every* line of the
  handler — a `Log`/`DiagnosticsLog` call inside `.onFailure { }` throws "not mocked" under plain JUnit and
  kills the collector *outside* the guard under test (run it under Robolectric). Never re-init a buffered
  logger between act and assert: `DiagnosticsLog.initForTest` clears the ring silently, and the failure reads
  as "the code logged nothing".
- **Attribute a diagnostics assertion to the lines its own case wrote.** The file `DiagnosticsLog.initForTest`
  installs is the process-wide sink, so work another class left running logs into it while a case holds it: a
  whole-file `contains("52.")`, an `entries.size` or an `entries.single()` measures those lines too and reddens
  the case for reasons its author cannot see. Filter to the marker only the subject's own message carries
  (`map source unknown value=`, `repository source without a base URL`, `HOST NOTIF post`, `ROUTE calc done:`,
  `HOST surface`) and keep the count on that list, so a duplicated or missing own line still fails. Filtering at
  *tag* level (`ENGINE_FAULT_TAG`) is the weaker form — a foreign record carrying that tag still counts — and is
  only for a record whose message has no marker of its own. The sink's own classes (`DiagnosticsLogTest`,
  `DiagnosticsLogRetentionTest`, `DiagnosticsLogWritePathTest`) are the exemption — the file is their subject;
  four cases that predate the rule still decide from the whole file and the requirement
  `unit-test-suite-runtime`, "A diagnostics assertion is attributed to the lines its own case wrote", names
  their paths. Measured twice — `SettingsStorageSourceTest`
  (2026-10-10, `car-favorites-group-level` task 4.2) and `NotificationHostSendLoggingTest` (2026-10-11,
  `fix-browse-recenter-dwell` task 5.7), change `fix-diagnostics-sink-attribution`.

### Kotlin / Compose / Robolectric traps (§40.13–§40.21, §40.28, §40.31, §40.33; §134.73, §134.76)

- **`while (isActive)` needs the explicit `kotlinx.coroutines.isActive` import** (§40.13).
- **`KProperty0.isInitialized` works only for `lateinit`** (§40.14); for `by lazy` state use a nullable `var`
  or a flag.
- **Test coroutine helpers need a receiver** (§40.15): declare them `private fun TestScope.foo()`;
  `advanceTimeBy(Long)` is an extension, `advanceUntilIdle` is a `TestScope`/`TestCoroutineScope` extension.
- **Lifetime tickers must run on a real dispatcher with test hooks** (§40.16): a
  `while (true) { delay(1s) }` loop on the test scheduler hangs every `runTest`.
- **Never pace a unit test off a timer or a debounce (`Thread.sleep`)** (§40.31): control the loop
  (`asyncLoopsEnabled = false/true`) and land a frame deterministically (`renderFrame()`).
- **Compose plurals containing `%d` need an explicit format argument** (§40.17):
  `pluralStringResource(id, count, count)`.
- **Never call `composeRule.setContent {}` twice in one test** (§40.18) — collect every value in the single
  composition.
- **Never nest two `verticalScroll` containers** (§40.19); give an embedded scrollable an opt-out parameter.
- **Robolectric's Compose root is clamped to 320×470 dp** (§40.20): assert against the screen, not hardcoded
  sizes, and read the failing bounds out of the assertion message.
- **The Robolectric shadow canvas discards `drawBitmap`/`drawPath` (0 opaque pixels) and
  `@GraphicsMode(NATIVE)` does not fix it here** (§40.21): assert the bitmap contract (size/config/caching/
  no-throw), leave visuals to on-device, and do not add a second sandbox config.
- **Verify constructor-argument edits against the file's imports in the same pass** (§40.28) — a rename
  dropped a still-used import and invented a non-existent class.
- **Compute `log2` expectations with a calculator, not mentally** (§40.33) — a wrong exponent reads as a
  product bug in a fit/zoom case.
- **In ViewModel tests, assert pre-state through an entry path that does not mutate the snapshot field**
  (§40.60): a path that writes the field before the collector resumes makes the pre-state assertion
  self-defeating.
- **Never mock a class that bears `native` methods or returns Kotlin flows** (§134.73): `native` cannot be
  relaxed or intercepted (`UnsatisfiedLinkError`), a relaxed mock of a `StateFlow`-returning member throws
  `KotlinNothingValueException` on `collect`, and a relaxed `Boolean` or object answer is `false`/`null` —
  stub `isValid = true` for a surface a renderer draws on. Use the module's fakes (`FakeOSMScoutClient`,
  `FakeMapScreenClient`, `FakeAutoRenderClient`, `FakeClientWithBbox`, `FakeStyleLoadClient`) and real
  `MutableStateFlow`s, and expect to grow a fake one native call at a time.
- **A car-app or sheet test drives and asserts the surface the way the host does** (§134.76): drive
  `LifecycleRegistry` through the states a host would (`INITIALIZED` → `CREATED` → `DESTROYED`, never straight
  to `DESTROYED`); `androidx.car.app.Screen`'s constructor is protected (subclass it);
  `ShadowDialog.getLatestDialog()` is null for a `Box` overlay (dispatch the back through the activity
  instead); Material3 `PartiallyExpanded` is a fraction of *content*, so keep the anchor state in the state
  owner; a node inside a `verticalScroll` in Robolectric's 320×470 dp window fails `assertIsDisplayed` —
  assert existence and re-check the product consequence; and Compose test symbols are either rule members
  (`onAllNodes`) or extensions (`assertIsDisplayed`) — read the whole error list before importing.

