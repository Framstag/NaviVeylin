# Design — fix-fix-quality-tick-main-race

## Context

See `proposal.md` — Why. The state that shapes the approach:

- The tick (`MapCanvasViewModel.kt:1018-1023`) launches on the ViewModel's scope (main dispatcher) and
  hops to the tick dispatcher only for the `delay`; the increment therefore executes on the main
  dispatcher and wakes the main-confined collector. Every ViewModel built in a test that is not
  cancelled keeps doing this once a second, from a `Dispatchers.Default` worker.
- `kotlinx-coroutines-test` 1.9.0 `TestMainDispatcher` wraps its delegate in
  `NonConcurrentlyModifiable` (read and write, both `CommonMain`): a read (`dispatch`, `isDispatchNeeded`,
  `immediate`) that happens while `setMain`/`resetMain` runs is recorded and **thrown on the next
  modification** — hence a failure in the *rule* of a later, unrelated test
  (`MainDispatcherRule.starting/finished`), never in the leaker.
- The two existing tickers in the codebase (`MapCanvasViewModel` stale speed `:1090-1108`,
  `NavigationEngine` `:198-205`) run their whole loop on `Dispatchers.Default` with the real clock and
  are documented as the pattern (`TODO.md` §40.C.16): an endless delay loop on the test scheduler keeps
  every `advanceUntilIdle` non-idle.
- The tick must keep feeding the debounced publication path: `debounce(2_000L)` is what suppresses
  flicker when the reported accuracy hovers around the 50 m threshold, and it must apply to a
  tick-detected change exactly as it applies to a fix-detected one.
- The tick and the pipeline must classify identically (one definition per `guidelines/Design.md` §12),
  and the platform source read (`LocationManager.isLocationEnabled`, a binder call) must stay off the
  main thread (`guidelines/Design.md` §4).

## Goals / Non-Goals

**Goals**

- A tick that reads nothing and changes nothing must not touch the main dispatcher — so a ViewModel that
  outlives its test cannot fail a later test.
- The published quality, the debounce and the consumer contract stay exactly as `fix-stale-fix-quality`
  specified them.
- One derivation for the tick and for the fix-driven path.

**Non-Goals**

- Enumerating or fixing the individual test classes that leak a ViewModel (the teardown rule in
  `guidelines/Build.md` §6 and its rule-based guard remain the tool for background work that is harmful
  when leaked).
- Any change to the tiers, the age limit, the consumers, the UI or the car surfaces.

## Decisions

**D1 — The tick loop's home dispatcher is `Dispatchers.Default`, so the tick cannot return to the main
dispatcher.** Alternatives: (a) keep the loop on the ViewModel scope and hop for the delay (today's
shape — the defect: a hop per tick, hence a main-dispatcher read per tick from a real thread); (b) move
the whole pipeline off the main dispatcher with `flowOn(fixQualityTickDispatcher)` — tried first and
rejected by evidence: it breaks the virtual-time expectations of six existing cases and the
browse-re-center cases (15 failures in one focused run) because their quality-dependent assertions rely
on the pipeline being driven by the test scheduler, and it puts the state writes on a background
dispatcher, against §4's main-confined state publication; (c) a dispatcher hook read *inside* each
iteration, which cannot make the loop's home off-main without reintroducing the hop for the loop's own
bookkeeping. Chosen: (a)'s inverse — the loop is launched *on* the tick dispatcher and its home is never
the main dispatcher, matching the two existing tickers.

**D2 — The tick reports a change, not a period.** The tick derives the quality itself and increments the
counter only when the derived value differs from the published one. Alternatives: (a) publish every tick
(today's shape) — the collector on the main dispatcher is woken per tick, which is the leak this change
removes; (b) have the tick publish the quality directly and skip the pipeline — rejected: it would bypass
`debounce(2_000L)`, and a hovering accuracy would then flicker the compass once per tick, which is
exactly what the debounce exists to prevent; (c) compare against the last value the *tick* derived
instead of the published one — equivalent in effect, but the published value is the one consumers see, so
it is the honest reference, and the extra ticks that arrive while a change sits inside the debounce are
dropped by the pipeline's own `distinctUntilChanged()`. Chosen: (a) inverted, with the pipeline
untouched.

**D3 — The derivation becomes one private `suspend fun deriveFixQuality(loc: GpsFix?)`.** Alternatives:
(a) duplicate the `when` in the tick — rejected: two definitions of the tiers drift, against §12;
(b) a non-suspend helper plus a caller-owned source read — rejected: the source read is a binder call and
each caller owns its dispatcher constraint (the tick is already off-main, the pipeline is main-confined),
so the `withContext(defaultDispatcher)` belongs inside the one definition. Chosen: the suspend helper,
called by both.

**D4 — The `fixQualityTickDispatcher` test hook is removed.** With D1 the loop captures its dispatcher at
launch, which happens in construction — before any test can set a hook — so the hook would be dead
surface (and a reader of it would believe the tick can be moved to a test scheduler). The remaining hooks
(`fixQualityTickMs`, `fixAgeLimitMs`, `nowMs`) are read per iteration/derivation and stay.

**D5 — The tick-driven cases of `MapCanvasViewModelFixQualityTest` wait in real time.** With the tick on
the production dispatcher, a transition only the tick can see (an aged-out fix, a disabled source) is a
real-clock event: the case pumps the ViewModel's scheduler (the pipeline and its debounce are virtual)
and polls the published quality, the pattern `MapCanvasViewModelSpeedWidgetTest.awaitSpeedCondition`
already uses for the stale-speed ticker. Fix-driven and debounce-driven transitions stay virtual
(`advanceTimeBy`). Alternative: hand the tick to the test scheduler — that is D4's dead hook.

**D6 — The guard test counts main-dispatcher dispatches from another thread.** Alternatives: (a) count
all dispatches and require zero — rejected by measurement: the case's own scheduler pumping queues
resumes onto the main dispatcher from the test thread, and construction-time component I/O
(settings/viewport/favorites/asset reads through `Dispatchers.IO`) resumes onto it from an IO worker, so
a strict "zero of anything" is noise-bound; (b) instrument the tick only — rejected: it would test the
implementation, not the contract. Chosen: count dispatches whose calling thread is not the case's own
thread, after letting the construction work settle; a tick that touches `Dispatchers.Main` shows up as
one per tick (measured: 10 in 300 ms with the pre-fix loop, 0 with this change).

## Risks

- **A tick-driven transition becomes slower than the debounce window?** No: the tick detects within one
  period (1 s in production) and the publication adds the unchanged `debounce(2_000L)`, the same bound as
  before. Verified by the existing aged-out/disabled-source cases, which now poll for the transition.
- **The tick's own derivation could publish a value the pipeline is about to publish differently.** Both
  call the same helper on the same inputs; the tick only *reports* a tick, so the pipeline remains the
  single writer. A change that arrives between the tick's derivation and its comparison merely causes one
  extra tick, which `distinctUntilChanged()` drops.
- **The tick now performs the source read (a binder call) itself.** It is already off the main thread and
  it already caused that read per tick through the pipeline's `map`; the read is unchanged in cost, only
  its dispatcher is (a background dispatcher, without an extra hop).
- **Real-time waiting could slow the suite.** Measured: the whole `:app` mobile suite is 3× green in
  ≈2-4 min per run, and the class itself is 22 s including compilation.
- **Residual leak surface stays:** a leaked ViewModel's tick still *reads* the location flow and derives
  (cheaply, off main) every second, and a genuine quality change would still resume the leaked
  collector on the main dispatcher once. Bounded to one read per real transition instead of one per tick
  — the remaining hygiene gap is `guidelines/Build.md` §6's teardown rule, recorded as a non-goal.

## Verification

- Unit (this change's guard): `MapCanvasViewModelFixQualityTest.aTickDoesNotDispatchOnTheMainDispatcher`
  → 0 dispatches from another thread in 300 ms of real ticks with an unchanged quality; revert-check with
  the pre-fix loop shape → 10 (the `DispatchedCoroutine.afterResume` stack of the CI failures).
- Existing quality contract: the other eight cases of the class (tiers, age-out without a new fix,
  disabled source, recovery on the next fix, unchanged quality publishes nothing, every consumer reads
  the shared quality) → green.
- Reproducer: `./gradlew :app:testMobileDebugUnitTest --tests "com.naviveylin.ui.map.*"` → 677 tests,
  0 failures (before the change on the same tree: 677 tests, 2 failures, both this exception).
- CI-exact gate: `./gradlew test` → `:app` mobile 1337/0, `:app` automotive 1337/0, `:auto` 701/0,
  `:core` 406/0, `:osmscout-client-java` 26/0 (3807 tests, 0 failures), and three further full `:app`
  mobile runs (1337 tests each) green with no `Dispatchers.Main is used concurrently with setting it`
  line in any log.
- On-device: unchanged behaviour, so the on-device checks of `fix-stale-fix-quality` (tasks 6.1-6.4)
  remain the ones that cover it; no new device check is required by a threading-only change. The
  `GPS fix quality: …` log line and the tick period are untouched (grep the log across a location-off
  toggling session as that change's task 6.1 prescribes).
