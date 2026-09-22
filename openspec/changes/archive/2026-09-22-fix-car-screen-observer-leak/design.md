# Design

## Context

Motivation and the observed accumulation: see `proposal.md` — Why. Requirements: see
`specs/auto/screen-observation/spec.md`.

Current state that shapes the approach:

- The three car map screens each own `private val scope = CoroutineScope(SupervisorJob() +
  Dispatchers.Main)` created in the constructor and cancelled only in `onDestroy`
  (`MapScreen.kt:72`, `NavigationScreen.kt:64`, `FreeDrivingScreen.kt:49`). All constructor-time and
  all start-time work runs in that one scope, so cancellation is all-or-nothing per screen.
- `startObserving()` is called from `onStart` (`MapScreen.kt:289`, `NavigationScreen.kt:389`,
  `FreeDrivingScreen.kt:353`) and guards only on `observeJob != null`; `stopObserving()` cancels
  only `observeJob` (`MapScreen.kt:792`, `NavigationScreen.kt:661`, `FreeDrivingScreen.kt:571`).
- The screens already have a per-start/per-stop notion for other work: `rendererGate.resume()`
  / `pause()`, `surfaceHost.attach` / `detach`, `autoZoomController.suspend()` — the observation
  lifecycle is the one piece that was not brought under it.
- Work that must survive a stop already exists and must not be swept up: the free-driving
  stale-speed ticker (`FreeDrivingScreen.kt:281`, launched from `init`), the renderer's own
  render/extrapolation/zoom-walk loops (owned by `AutoMapRenderer`, `AutoMapRenderer.kt:83`), and
  the session's observers (`NavigationSession`, which keeps trip publishing alive by design — see
  `TODO.md` §56).
- `:auto` declares `androidx.car.app:app:1.7.0`, `androidx.core:core-ktx`,
  `kotlinx-coroutines-core` and hilt; it does **not** declare `androidx.lifecycle:lifecycle-runtime-ktx`
  (`auto/build.gradle.kts:119-131`). `guidelines/Design.md` §4 governs the threading model: main
  thread for library/host interactions, native and file work on background dispatchers.

## Goals / Non-Goals

**Goals:**

- Make "exactly one instance of each observation per started period" structural rather than a
  convention every new collector has to remember.
- Give the three screens one shared seam, so a fourth screen cannot repeat the defect.
- Keep every observation's own dispatcher and background work unchanged — only its *multiplicity*
  and *lifetime* change.
- Keep the fix reviewable in one session per screen and reversible by reverting commits.

**Non-Goals:**

- `DetailsScreen`'s init-scoped observers (they run while invisible instead of growing per start):
  recorded as `TODO.md` §57, out of scope here, and to be moved onto this seam when picked up.
- Session-scoped host-mutation gating (`SessionHostGate`) and trip publishing while backgrounded:
  the contract of `car-host-fault-isolation` / `TODO.md` §56, deliberately untouched.
- The main-thread/JNI violations found in the same review (`TODO.md` §52 `pushDark` from
  `onCarSurfaceAvailable`, §53 the native client resolved in `onCarClick`), the surface-release
  bookkeeping (§54), the per-invalidate lane image (§55) and the render-buffer churn (§49).
- Any phone-side observation structure.

## Decisions

### D1 — One child scope per started period, not a job list and not `repeatOnLifecycle`

**Chosen:** each screen owns an observation scope that is created on `onStart` and cancelled on
`onStop`; all observations are launched into it.

Alternatives:

1. *Keep one `MutableList<Job>` and cancel every entry in `stopObserving()`.* This is the minimal
   edit, but it preserves the failure mode: a new collector is correct only if its author remembers
   to add it to the list. The defect arose exactly this way, three times.
2. *`lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) { … }`.* Idiomatic Android and
   self-cancelling, and the car `Screen` is a `LifecycleOwner`. Rejected: it needs
   `androidx.lifecycle:lifecycle-runtime-ktx`, which `:auto` does not declare, and the car-app
   `Screen` lifecycle is a library-owned registry whose STARTED/STOPPED edges the screens already
   observe through `addObserver` — adding a second, ktx-only path for the same edges buys nothing
   here and splits the per-start bookkeeping across two mechanisms.
3. *Chosen:* a scope whose cancellation is the single stop action. Nothing can be launched "beside"
   it by accident: an observation that is not in the scope does not exist, and the invariant holds
   by construction.

### D2 — One shared seam in `:auto`, not three hand-written copies

**Chosen:** a small `internal` helper (e.g. `CarScreenObservations`) in
`auto/src/main/java/com/naviveylin/auto/`, constructed by each screen with the main-thread
dispatcher, exposing a `start(...)`/`stop()` pair plus the launch entry point for observations and a
test-visible count of live observations. The three screens replace `startObserving()` /
`stopObserving()` bodies with calls into it.

Alternatives:

1. *Fix each screen in place* (three copies of a `var observeScope`). Smallest diff per file, but the
   three copies are what drifted in the first place, and the multiplicity invariant would again be
   asserted three times, differently.
2. *Put the seam in `:core`.* Rejected: `:core` has no car-app dependency and the seam is about
   car-screen lifecycle; the screens' shared surface-owner seam lives in `:core` only because the
   *interface* has to be visible to `:app`'s Hilt wiring. Nothing outside `:auto` needs this.
3. *Chosen:* one `:auto` helper, unit-tested without a `CarContext`, used by all three screens.

**What each screen also gets (decided during apply):** a `<Screen>Observations` class
(`MapScreenObservations`, `NavigationScreenObservations`, `FreeDrivingScreenObservations`) that
holds *what* is observed — one `observe(key)` registration per source, with the screen's effects
passed in as callbacks — while `CarScreenObservations` keeps ownership of *how long* it lives.

The reason is testability, and it is a hard constraint rather than a preference: the three map
screens cannot be constructed in `:auto` unit tests at all. `MapScreen.kt:90`
(`surfaceHost = entryPoint.autoSurfaceHost()`) plus its `favoritesProvider`/`settingsProvider`
resolve the Hilt entry point in the constructor (`EntryPointAccessors.fromApplication(
carContext.applicationContext, AutoEntryPoint::class.java)`), and `:auto`'s tests have no Hilt
application component — the existing `MapScreenTest`/`FreeDrivingScreenTest` record that
constraint ("the screen itself needs a live CarContext + Hilt entry point"), and
`testCarContext()` is a `mockk<CarContext>` that stubs only `getString` and the back dispatcher.

Alternatives considered here: add an `internal` entry-point override to each screen (a
production test hook, plus per-screen `CarContext` stubbing and taming the constructor-time init
coroutines), or drop the per-screen coverage and rely on the seam test plus the on-device run
(leaves the screens' own registration untested). The wiring class was chosen because it gives the
spec's scenarios real assertions — the seam plus the mockable `:core` providers plus counting
effects, with no `CarContext`, no new dependency and no test hook in production code — at the
cost of moving each screen's registration (not its bodies: the effects stay on the screen) into
that class.

### D3 — Stop cancels; only the observations stop

**Chosen:** `stop()` cancels every observation. Constructor-time work (`scope` in `init`: renderer
init, surface-DPI collector, initial settings load), the renderer's own loops, and the
free-driving stale-speed ticker stay exactly where they are, launched from the screen's constructor
scope.

Alternatives:

1. *Keep the collectors running and gate their effects on `lifecycle.currentState.isAtLeast(STARTED)`.*
   This is what `TODO.md` §57 describes for `DetailsScreen`; rejected here because it keeps the
   native lookups, the viewport commits and the render requests running for a screen nobody sees,
   which is the cost this change is removing — and the gating has to be repeated at every effect
   site.
2. *Chosen:* cancel, and re-establish on start. A `StateFlow` re-collects its current value, so the
   "apply what changed while stopped" scenario in the spec still holds — once per start, which is
   also what makes it testable.

### D4 — Main-thread construction; background work stays inside the observations

Threading model, per `guidelines/Design.md` §4:

- The seam is main-thread only: constructed by the screen's constructor (which runs on the car-app
  host thread) and driven from the screen's `DefaultLifecycleObserver` callbacks and screen
  functions, all of which are main-thread. `start()`/`stop()` are cheap: create/cancel a
  `CoroutineScope(SupervisorJob() + Dispatchers.Main)` and read/clear a job count.
- Nothing in the seam touches the native client, the filesystem or the renderer; it neither owns nor
  starts any background work of its own.
- Each observation keeps its current shape: a main-dispatcher collector that does its native/file
  work inside `withContext(Dispatchers.Default)` (e.g. `MapScreen.kt:771`, `NavigationScreen.kt:637`,
  `FreeDrivingScreen.kt:552`). No observation gains or loses a dispatcher hop.
- Lifecycle: `start()` is idempotent (a second start without a stop is a no-op, matching the current
  `observeJob != null` guard); `stop()` is idempotent and safe before any start. Both are called
  from the screen's existing `onStart`/`onStop`, so the screen-level lifecycle stays the single
  source of truth for "started".

### D5 — Naming and placement follow the existing screen seams

`RendererGate`, `SurfaceHostGate`/`SessionHostGate`, `SettingsLoadGuard` and `CarStyleApplier` are the
established shape: a small pure/`internal` class in `:auto`, unit-tested directly, with the screen
holding the only instance and no DI registration. The observation seam and the three per-screen wiring
classes follow it, and are registered nowhere in Hilt.

The wiring classes' observation keys (`position`, `favorites`, `dark`, `basemap`,
`navigation-state`) are private per class: the key is what makes "at most one instance per started
period" hold in the seam, and it stays the wiring class's business.

## Risks / Trade-offs

- **A `StateFlow`-backed observation replays on every start, so a restart does extra work.** →
  Intended: that is the "apply the current state once on return" scenario. The per-start work is
  bounded by the number of sources (six), not by the number of historical starts.
- **The `MapScreen` periodic settings re-read lives inside the position collector and is
  fix-driven, so after this change it needs a GPS fix after a start.** → Unchanged from the first
  start (`MapScreen.kt:708-716`), and a real car has ~1 Hz fixes; the free-driving and navigation
  screens reload settings on start explicitly (`FreeDrivingScreen` `loadSettings`/`LoadSettingsGuard`,
  `NavigationScreen.reloadLiveSettings()`), so no specification regresses. Noted rather than changed,
  to keep this change behavior-neutral where the specs are already satisfied.
- **Cancelling a collector between its emission and its `withContext` result can drop a partially
  applied change** (e.g. a street-name lookup cancelled after the native call but before the label
  write). → Acceptable: the label is re-resolved from the current position on the next fix after
  start, and the stopped screen must not draw anyway.
- **Moving the `NavigationScreen` GPS collector into the scoped group changes when the last fix
  before a stop is committed.** → The renderer's own extrapolation gate and the session's navigation
  engine are unaffected (they do not depend on the screen's collector), and the marker is
  re-established from the next fix on start.
- **A future observation added outside the seam silently restores the defect.** → Mitigated by the
  seam owning the only launch entry point (an observation launched on the screen's constructor scope
  would be visible in review as an explicit `scope.launch` outside it) and by a per-screen
  regression test asserting the live-observation count is unchanged across repeated start/stop
  cycles.

## Migration Plan

No data, preference, manifest or API migration — the change is internal to `:auto` and lands in
three screen-sized steps (one screen each) plus the seam, so each step is independently verifiable
and revertable. Rollback: revert the change's commits; the previous per-start accumulation returns
with the previous `startObserving()`/`stopObserving()` bodies.

## Open Questions

None blocking. The `DetailsScreen` adoption (`TODO.md` §57) is a separate change and does not change
this one's specs, approach or task breakdown.
