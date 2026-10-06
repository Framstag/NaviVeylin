# Design

## Context

`NavigationEngine` (`app/src/main/java/com/naviveylin/navigation/NavigationEngine.kt`) is a Hilt
singleton built by an `@Inject` constructor (`:80-88`), bound to `NavigationViewModel` in
`app/src/main/java/com/naviveylin/di/NavigationViewModelModule.kt:24-28`. It owns a process-lifetime
`SupervisorJob() + Dispatchers.Main` scope with the shared engine fault handler (`:99`), publishes all
navigation state through `private val _state = MutableStateFlow(NavigationState())` (`:131`), and runs
native work on `Dispatchers.Default` (`:410`, `:512`, `:833`) and `Dispatchers.IO` (`:908`).

Its init block launches the stale-speed ticker (`:194-211`): a `while (true) { delay(1_000); … }` loop
on `Dispatchers.Default`, whose body is

```kotlin
if (_state.value.isNavigating && SpeedStaleness.isStale(lastFixTime, System.currentTimeMillis())) {
    _state.value = _state.value.copy(currentSpeedKmH = 0.0)      // :207 — the defect
}
```

The project's rule that makes this a defect is already written down: `guidelines/Design.md` §4 and the
spec's "navigation state publication SHALL be main-thread confined" — 25 of the 27 assignment sites of
`_state` publish inside `scope.launch(Dispatchers.Main)` or `withContext(Dispatchers.Main)`. Two sites do
not, and both write a **whole snapshot**, so a publication that lands between their read and their write
is reverted:

- the ticker (`:207`, `Dispatchers.Default`, once per second while navigating) — the one the failures
  recorded;
- the road-info lookup's publish (`:911`, inside `scope.launch(Dispatchers.IO)`) — the same shape at a
  lower rate, and the reason the confinement rule has to be enforced by the seams rather than by
  convention.

The remaining sites are main-confined by construction: `startInternal`, `beginCalculation`,
`publishCalculationProgress`, `endCalculation`, `stopNavigation`, `clearError`, `reportError` and
`updateRoadInfoFromPosition` (`:888`) are called from Main coroutines or from the main-thread surface
entry points.

Evidence that this is the mechanism (not a slow clock): `core/src/main/java/com/naviveylin/core/NavigationState.kt:58`
declares `val maxSpeedKmH: Double = Double.NaN`, and the recorded failure is
`NavigationEngineTest.kt:120` `assertEquals(100.0, state.maxSpeedKmH)` →
`expected:<100.0> but was:<NaN>`. A value can only travel from `100.0` back to the default `NaN` by
restoring a snapshot taken before the write. The other recorded victims (`lane guidance mirrored`,
`instructions mirrored`) are the same loss on other fields, which is why the victim moves between runs
and flavors.

Constraints that shape the approach:

- `SpeedStaleness` (`core/src/main/java/com/naviveylin/core/SpeedStaleness.kt`) is already a pure
  predicate taking `nowMs` — the time seam exists, the engine just does not use it.
- The tests construct the engine directly, not through Hilt: `NavigationEngineTest.kt:59`
  `engine = NavigationEngine({ client }, LocationService(context), context)`; the same three-arg form
  appears in `NavigationEngineRerouteTest`, `NavigationEngineAcquisitionTest`,
  `NavigationEngineCalculationCancelTest`, `NavigationEngineCalculationStateTest` and
  `NavigationEngineFaultIsolationTest` (a non-test source, `:osmscout-client-java`-independent, never
  built by those classes).
- `NavigationEngineTest` and `NavigationEngineRerouteTest` wait on a wall-clock deadline:
  `NavigationEngineTest.kt:62-70` (`awaitState`), `:72-80` (`startNavigating`),
  `NavigationEngineTest.kt:159-171` (`roadInfoThrottleHoldsItsWindow`, sleeps), and
  `NavigationEngineRerouteTest.kt:69-85` (`awaitState` + `advanceUntilIdleBlocking` on the Robolectric
  looper).
- The engine's own tests must not be built on the project's Robolectric sandbox rules: no
  `@Config(sdk = …)` may be added, and `FakeOSMScoutClient` keeps its default sandbox (`AGENTS.md`,
  JNI stub classloader rule).
- No device and no native artifact is needed for any part of this change; the JNI client is a fake in
  every affected class.

## Goals / Non-Goals

**Goals**

- No writer of the engine's shared state can revert a field another writer published (the observable
  behaviour, not just the implementation).
- The engine's time decisions are taken from one injected time source, and its off-main work runs on an
  injected dispatcher, so a test controls both.
- The three formerly flaky cases and the two wall-clock helpers decide their outcome from behaviour
  under test; each new guard is falsified by a named single mutation.
- The suite's failure set at the declared concurrency equals the single-JVM failure set
  (`unit-test-suite-runtime`), which is what the flakes currently violate.

**Non-Goals**

- `TODO.md` §126 (a failed reroute releasing the nav lease), §127 (`LoadingScreen` dead code), §128
  (`@Config(sdk = [34])` in `NavigationEngineAcquisitionTest`) — separate entries; this change touches
  those files only where a construction site has to follow the new constructor.
- A new buildSrc scanner for non-atomic state writes. The precedent gates
  (`checkNoCoordinatesInLogs`, `checkHardcodedStrings`) scan for a rare literal; `_state.value =
  _state.value.copy(` is the codebase's *normal* form elsewhere (phone ViewModels), so a repo-wide gate
  would be a false-positive generator. The guard here is the behavioural case plus the revert-check
  mutation (D3).
- Moving the ticker off `Dispatchers.Default` in production, or making its cadence configurable.
- Any change to rendered output, persisted state, the notification path, or the car surface.

## Decisions

### D1 — How the time source and the dispatchers reach the engine

**Chosen: constructor parameters with Hilt providers and qualifiers (Alt A).**

```kotlin
class NavigationEngine @Inject constructor(
    private val client: Lazy<OSMScoutClient>,
    private val locationService: LocationService,
    @param:ApplicationContext private val context: Context,
    private val timeSource: EngineTimeSource,                       // fun interface nowMillis(): Long
    private val dispatchers: EngineDispatchers                      // (compute, io)
)
```

`EngineTimeSource` (a one-method `fun interface` in `:core`, next to `SpeedStaleness`, with a
`System` singleton reading `System::currentTimeMillis`) and `EngineDispatchers` (a small holder whose
defaults are `Dispatchers.Default` for compute and `Dispatchers.IO` for the native lookup) get
`@Provides` entries in `app/src/main/java/com/naviveylin/di/AppModule.kt` (the place the other
process-scoped providers live). Every `System.currentTimeMillis()` in the engine (`:205`, `:280`,
`:540`, `:713`, `:808`, `:894`) reads the injected source; the `Dispatchers.Default` sites (`:410`,
`:512`, `:833`) and the `Dispatchers.IO` site (`:908`) take the pair's values. The ticker keeps a
private `TICKER_DISPATCHER = Dispatchers.Default` constant, because its endless `delay` loop must not
land on a test scheduler (the existing comment at `:199-200` states exactly that). The road-info
lookup hops before publishing: the native `getRoadAt` stays on the io dispatcher and the result is
published inside `withContext(Dispatchers.Main) { _state.update { … } }`, which is the confinement the
spec already requires and removes the second off-main writer.

- Why: one construction path (Hilt and tests both use the `@Inject` constructor), the production
  default is explicit at the provider rather than hidden in a test-only overload, and the seam is
  compile-enforced — a new `System.currentTimeMillis()` in the engine is visible in review.
- Risk: adding two parameters to an `@Inject` constructor means Dagger needs both bindings; a missing
  qualifier fails the build loudly (acceptable, and covered by the compile task).
- Risk: the injected dispatcher pair also moves the reroute math and the cancel path onto the test
  scheduler in tests — intended, but it means those classes must not assume a real thread; their
  assertions are state-based already.

**Alt B — a secondary constructor for tests.** Keep the three-arg `@Inject` constructor and add a
five-arg secondary one that Hilt never sees. *Rejected:* two ways to build the singleton, the
production path is the one nobody writes by hand, and the extra parameters stay undocumented by DI.

**Alt C — mutable internal seams.** `internal var timeSource: () -> Long = System::currentTimeMillis`
and `internal var background: CoroutineDispatcher = Dispatchers.Default`, set by a test before
`start()`. *Rejected:* mutable process-global-ish state on a singleton (`guidelines/Design.md` §12),
and the ticker is launched in `init`, i.e. before any test could set it — it would force the ticker to
start lazily, changing lifecycle for no product gain.

### D2 — How the tick's behaviour is proven without waiting for a real second

**Chosen: split the loop from its body and drive the loop on the test scheduler (Alt A).**

```kotlin
internal suspend fun runStaleSpeedTicker(intervalMs: Long = SPEED_STALE_TICK_MS) {
    while (true) { delay(intervalMs); tickStaleness() }
}

internal fun tickStaleness() {
    val current = _state.value                              // the guard reads once…
    if (!current.isNavigating) return
    if (!SpeedStaleness.isStale(lastFixTime, timeSource.nowMillis())) return
    _state.update { it.copy(currentSpeedKmH = 0.0) }         // …and never writes that snapshot back
}
```

`runStaleSpeedTicker` is `internal` (not `private` as first sketched) because the test drives the same
loop — that is the seam that keeps the wiring proven, and it is the same precedent as
`updateRoadInfoFromPosition`/`engineScope` in this file. `tickStaleness` takes no `nowMs` argument: the
injected clock already is the seam, and keeping the guard's read as a local is what makes the regression
shape (`_state.value = current.copy(…)`, task 4.1) both natural to write and falsifiable.

`init` launches `runStaleSpeedTicker()` on `TICKER_DISPATCHER` (production unchanged). A test launches
`runStaleSpeedTicker()` on the test scheduler, `advanceTimeBy(1_000)`, `runCurrent()`, asserts, and
**cancels the job before returning** — which is also what keeps `runTest` from reporting an unfinished
coroutine. `tickStaleness(nowMs)` with an explicit argument gives the field-level cases a direct call.

- Why: the wiring (the loop calls the body once per interval while navigating) and the behaviour (the
  body's transition) are both proven in virtual time, and both are falsifiable through the new seams.
- Risk: a test that forgets to cancel the ticker job hangs its own `runTest` — the case must cancel in
  a `finally` (or hold the job in a rule-level teardown); the tasks name this.
- Risk: `advanceTimeBy` + `delay(1_000)` on the test scheduler is deterministic only if nothing else in
  the test depends on real time; both affected classes are converted in the same change for that
  reason.

**Alt B — keep the loop in `init` and test only `tickStaleness`.** *Rejected:* that is the
`FollowAnchorFramingTest` shape `TODO.md` §111 exists for — the body proven, the call site not.

**Alt C — put the loop on the injected `background` dispatcher too.** *Rejected:* `runTest` fails with
an unfinished coroutine for a process-lifetime endless loop on its scheduler; making it cancellable
means the same extraction as A, plus a dispatcher coupling that buys nothing.

### D3 — Scope of the atomic-write conversion

**Chosen: convert all 26 `_state.value = _state.value.copy(…)` sites (and `:613`'s whole-state
assignment stays as it is — a fresh `NavigationState()` is not a read-modify-write) to
`_state.update { … }`, and guard the invariant with one behavioural case (Alt A).**

- Why: one shape in the file means the next author has no "which writer am I?" question; `update` is a
  CAS loop and costs the same on the main thread; the invariant then holds by construction for every
  future writer.
- The guard case is deterministic because the injected clock (D1) is the interleave hook:
  `aStaleSpeedTickDoesNotRevertAConcurrentPublication` blocks inside the injected `nowMillis()` with a
  latch, publishes `maxSpeedKmH = 100.0` on the test thread while the tick waits, releases the latch,
  and asserts `maxSpeedKmH` is still `100.0`. The **mutation** that must fail it is the regression
  shape — hoisting the snapshot read out of the update and writing it back:
  `val snapshot = _state.value; …; _state.value = snapshot.copy(currentSpeedKmH = 0.0)`.
- Risk: the mutant is only falsifiable because the read is hoisted *before* the clock call. A mutant
  that reads after the clock call is behaviourally indistinguishable in a single-threaded test; the
  design accepts this and records the mutation shape in the task, so the falsification is honest rather
  than implied. (This is the project's accepted "structural guard" case, `revert-check` skill.)

**Alt B — convert only the ticker.** *Rejected:* the smallest diff, but two shapes stay in the file and
the next background writer re-introduces exactly this bug.

**Alt C — convert all sites plus a buildSrc gate.** *Rejected:* see Non-Goals — the pattern is the
normal form in the app's ViewModels, so the gate would need a per-file allowlist to be useful, which is
more machinery than the invariant is worth today.

### D4 — Removing the wall-clock waits from the two test classes

**Chosen: inject the test dispatcher as the engine's `background` and await observable state (Alt A).**
With the calculation coroutine and the reroute math on the injected dispatcher, a callback lands on the
test scheduler, so `awaitState { … }` (deadline + `Thread.sleep`) becomes
`runCurrent()`/`advanceUntilIdle()` followed by a direct assertion, and `advanceUntilIdleBlocking()`
(the Robolectric looper pump) is no longer needed in `NavigationEngineRerouteTest`.

- Why: the waits exist only because work ran on a real thread; removing the real thread removes the
  need for a deadline, which is precisely the `unit-test-suite-runtime` delta.
- Deterministic falsification for "the native call runs on the injected dispatcher": the fake client
  records `Thread.currentThread().name` at the call, and the case asserts it is the test thread —
  restoring `Dispatchers.Default` at `:410` fails that assertion deterministically (no race window).
- Risk: `LocationService` in these tests is the real one (constructed with the Robolectric context);
  its feed may still deliver on its own thread. Cases that await a fix therefore keep a bounded await
  on the *state* — but the bound must not be a wall-clock deadline; they await
  `engine.state.first { … }` on the test scheduler with `withTimeout` **inside a virtual-time test**
  (`runTest`'s own timeout is virtual), which is the same deterministic form. If a case turns out to
  need real-thread work from `LocationService`, it is converted to feed fixes through
  `processLocation(...)` (already the pattern in `NavigationEngineTest`) rather than waiting.

**Alt B — keep the waits and widen the deadline to a named constant.** *Rejected:* still wall clock,
still violates the delta, and hides the slower case instead of removing the dependency.

**Alt C — replace the pump with `state.first { … }` only.** *Rejected:* on its own it does not help —
the publisher is on the test dispatcher but the callback arrives on a real thread, so the state never
changes unless something drives that thread.

### D5 — Threading model and lifecycle per component

| Component | Home (production) | Home (tests) | Lifecycle |
|---|---|---|---|
| `NavigationEngine` scope | `SupervisorJob() + Dispatchers.Main` + engine fault handler (`:99`) | unchanged | process lifetime, never cancelled |
| staleness ticker loop | `TICKER_DISPATCHER` = `Dispatchers.Default`, started in `init` | the test scheduler, started by the case and cancelled in `finally` | process lifetime; only the cancel-by-test form ends earlier |
| staleness tick body | runs on the loop's thread, publishes through `update` | called directly with an explicit `nowMs` | no state beyond `_state` and `lastFixTime` |
| route calculation / reroute math (`:410`, `:512`, `:833`) | injected compute dispatcher (`Dispatchers.Default`) | the test dispatcher → `advanceUntilIdle()` | per attempt, token-guarded by `beginCalculation`/`endCalculation` |
| road-info lookup (`:908`) | injected io dispatcher for `getRoadAt`, result published via `withContext(Dispatchers.Main)` | the test dispatcher if a case needs it | per position update, throttled by `lastRoadInfoTime` |
| state publication | main-confined (`scope.launch(Dispatchers.Main)` / `withContext(Dispatchers.Main)`), always `update { }` | same, on the test scheduler | process lifetime |

No new component is introduced: the seams are constructor values, and the only new types are the
one-method `EngineTimeSource` and the `EngineDispatchers` holder — both immutable, with no lifecycle of
their own.

## Risks / Trade-offs

| Risk | Mitigation |
|---|---|
| The extra constructor parameters break a construction site the change does not list | The compile of `:app` fails loudly; the task lists all six known sites and greps for the three-arg form before finishing |
| A converted case still passes for the wrong reason (asserts a value that the removed polling had produced) | Every converted case is re-run against the *pre-change* code path where the guard is meaningful; the task requires the mutation to be named before the case is written (`revert-check` skill) |
| The atomicity case is mistaken for a load-robustness proof | The delta's "Load does not change the failure set" scenario is verified by repeated forced suite runs at the declared concurrency, recorded with tallies — not by the single case |
| `_state.update` inside a hot listener path adds a CAS retry | Writers are listener callbacks (≤ a few per second) and the tick (1/s); `update` on an uncontended `MutableStateFlow` is a compare-and-set, and the state object is immutable data |
| A peer session is editing the same tree (an unrelated full gate ran during this change's planning) | Apply runs after the tree is quiet, verified with `pgrep -af 'gradle-wrapper\.ja[r]'` and `find app/src -newermt '-10 minutes'` (`AGENTS.md`, rule 4) |

## Verification

**Unit tests (JVM only — no device, no native build, no instrumented test):**

- `NavigationEngineTest` — new: `aStaleSpeedTickDoesNotRevertAConcurrentPublication` (the clobber case,
  latch-forced through the injected clock); new: `theStaleSpeedTickerDecaysOnlyPastTheWindow` (loop
  driven with `advanceTimeBy(1_000)` on the test scheduler, job cancelled in `finally`); converted:
  `staleSpeedTickerZeroesAStaleSpeed` (no real sleep), `listenerCallbacksDriveTheSharedState` (its
  lane/instruction/max-speed assertions now run after the tick can no longer revert them),
  `roadInfoThrottleHoldsItsWindow` (injected clock instead of `Thread.sleep`).
- `NavigationEngineRerouteTest` — converted helpers (`awaitState`, `startSession`, the looper pump
  removed); one case asserts the recorded call thread is the test thread.
- `NavigationEngineRoadInfoTest` — one case asserts the lookup's publish happens on the main thread and
  does not revert a concurrently set field (the hopped-publish guard for `:911`).
- `NavigationEngineCalculationCancelTest`, `NavigationEngineCalculationStateTest`,
  `NavigationEngineFaultIsolationTest`, `NavigationEngineAcquisitionTest` — construction sites updated;
  their existing lease/state assertions must stay green unchanged.

**Revert-checks (one mutation each, per the `revert-check` skill):**

1. Atomicity: hoist the tick's snapshot read and write it back (`val snapshot = _state.value; …;
   _state.value = snapshot.copy(currentSpeedKmH = 0.0)`) → `aStaleSpeedTickDoesNotRevertAConcurrentPublication`
   must fail on the `maxSpeedKmH` assertion; restore, force green.
2. Injected clock: make `tickStaleness()` read `System.currentTimeMillis()` directly →
   `staleSpeedTickerZeroesAStaleSpeed` (which advances only virtual time and never sleeps) must fail;
   restore, force green.
3. Background dispatcher: restore `Dispatchers.Default` at the calculation launch → the recorded-thread
   case must fail; restore, force green.
4. Hopped publish: publish the road-info result directly on the io coroutine instead of through
   `withContext(Dispatchers.Main)` → the `NavigationEngineRoadInfoTest` case's thread assertion must
   fail; restore, force green.

**Gate (per `guidelines/Build.md` §2 and the `build-test-gate` spec):** the change touches only sources
both `:app` flavors share, so one flavor's suite per iteration:
`./gradlew :app:testMobileDebugUnitTest --tests "com.naviveylin.navigation.*"` for the loop, then the
full forced gate once (`./gradlew test -PforceTests --no-build-cache`) with the executed-task count and
per-module tallies quoted. The load dimension of the delta is verified by repeating the forced `:app`
suite at the declared two forks (at least twice) and comparing the failing-class set with a
single-JVM run (`-PforkEvery`/`--max-workers=1` only as a diagnostic, not as the procedure), recording
both sets — a suite whose failure set moves between the two is the delta's failure condition.

**On-device checks:** none. Nothing in this change reaches the rendered map, the car surface, the
notification or persisted state, so there is no visual property to measure with `pixel-check`, no
diagnostics line to read on device, and no AVD dependency — the design states that explicitly rather
than leaving the device half unaddressed.

**Documentation:** `guidelines/Build.md` §4 gains one bullet — a background tick that publishes shared
state publishes through an atomic update, never a whole-snapshot `copy` write, and its timing decision
reads the component's injected time source.
