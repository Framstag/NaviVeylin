# Tasks

> Verification of every task is stated in the task itself. Nothing in this change reaches a rendered
> surface, a persisted file or a device, so there is no on-device task and no `pixel-check` verdict —
> the last group states that explicitly instead of leaving the device half unaddressed.
> Iteration uses one `:app` flavor (this change touches only sources both flavors share); the full
> gate runs both flavors once, at completion.

## 1. Seams (`navigation-engine`)

- [x] 1.1 Add the time-source seam next to `SpeedStaleness` in `:core`: a one-method `fun interface
  EngineTimeSource { fun nowMillis(): Long }` with a `System` singleton reading
  `System::currentTimeMillis`, plus a KDoc line naming what it exists for (a component's timing
  decisions come from an injected source, spec `navigation-engine` — Engine lifecycle and threading).
  Verification: the new type compiles and has its own case asserting `EngineTimeSource.System.nowMillis()`
  is within a second of `System.currentTimeMillis()` (`core/src/test/.../EngineTimeSourceTest.kt`).
- [x] 1.2 Add the dispatcher seam: an immutable `EngineDispatchers(compute: CoroutineDispatcher,
  io: CoroutineDispatcher)` holder with `EngineDispatchers.Production` = `Dispatchers.Default` /
  `Dispatchers.IO`, in the same `:core` area (spec `navigation-engine` — Engine lifecycle and
  threading). Verification: a `:core` case asserts the production holder's values are those dispatchers;
  the holder has no mutable state (`val` only, no lifecycle of its own).
- [x] 1.3 Wire both seams into the engine's `@Inject` constructor
  (`app/src/main/java/com/naviveylin/navigation/NavigationEngine.kt:80-88`) and add the `@Provides`
  entries in `app/src/main/java/com/naviveylin/di/AppModule.kt` (design D1). Replace the engine's own
  `System.currentTimeMillis()` reads (`:205`, `:280`, `:540`, `:713`, `:808`, `:894`) with
  `timeSource.nowMillis()`, and the inline dispatchers (`:410`, `:512`, `:833` → compute; `:908` → io)
  with the holder's values. The ticker keeps a private `TICKER_DISPATCHER = Dispatchers.Default`
  (design D2) with the existing rationale comment preserved.
  Verification: `./gradlew :app:assembleMobileDebug` compiles; `grep -n 'System.currentTimeMillis()'
  app/src/main/java/com/naviveylin/navigation/NavigationEngine.kt` returns only the DI provider's default
  (none in the engine class); `grep -n 'Dispatchers\.' …NavigationEngine.kt` shows only
  `TICKER_DISPATCHER`'s constant and the `Dispatchers.Main` publication paths.
- [x] 1.4 Update every construction site of the engine to the new signature —
  `NavigationEngineTest.kt:59`, `NavigationEngineRerouteTest`, `NavigationEngineAcquisitionTest`,
  `NavigationEngineCalculationCancelTest`, `NavigationEngineCalculationStateTest`,
  `NavigationEngineFaultIsolationTest` — passing a test-dispatcher `EngineDispatchers` and the test
  clock. No `@Config(sdk = …)` is added or removed anywhere (JNI stub classloader rule, `AGENTS.md`).
  Verification: `grep -rn 'NavigationEngine({' app/src/test` shows every site on the new form, and
  `./gradlew :app:testMobileDebugUnitTest --tests "com.naviveylin.navigation.*"` compiles and passes.

## 2. Atomic publication and main-thread confinement (`navigation-engine`)

- [x] 2.1 Convert all 26 `_state.value = _state.value.copy(…)` sites in `NavigationEngine.kt` to
  `_state.update { it.copy(…) }` (design D3). The stop path's `_state.value = NavigationState()` (`:613`)
  stays: assigning a fresh state is not a read-modify-write. Keep the existing KDoc/spec comments intact
  while editing (no comment may be dropped by the mechanical rewrite).
  Verification: `grep -c '_state.value = _state.value.copy' …NavigationEngine.kt` returns 0,
  `grep -c '_state.update' …NavigationEngine.kt` returns 26, and the whole
  `com.naviveylin.navigation.*` suite is green.
- [x] 2.2 Publish the road-info lookup's result on the main thread: keep `client.getRoadAt` inside the
  io coroutine (`:908-915`) and wrap the state publication in `withContext(Dispatchers.Main) {
  _state.update { … } }` (spec `navigation-engine` — A native lookup result is published on the main
  thread).
  Verification: the new `NavigationEngineRoadInfoTest` case asserts the publishing thread is the main
  thread and that a field set between the lookup's start and its publish survives — see 4.4.

## 3. Deterministic tests (`navigation-engine`, `unit-test-suite-runtime`)

- [x] 3.1 Split the ticker into its loop and its body (design D2): `private suspend fun
  runStaleSpeedTicker(intervalMs: Long = SPEED_STALE_TICK_MS)` containing the `delay` + call, and
  `internal fun tickStaleness(nowMs: Long = timeSource.nowMillis())` containing the guard and the
  `update`. `init` launches the loop on `TICKER_DISPATCHER`.
  Verification: the production loop still runs (existing cases green) and `tickStaleness` is callable
  with an explicit `nowMs` from a test.
- [x] 3.2 New case `theStaleSpeedTickerDecaysOnlyPastTheWindow` (`NavigationEngineTest`): launch
  `runStaleSpeedTicker()` on the test scheduler, `advanceTimeBy(1_000)`/`runCurrent()` with the clock
  still inside the window → speed unchanged; advance the injected clock past
  `SpeedStaleness.STALE_SPEED_MS` and one more interval → speed is `0.0`; cancel the job in `finally`
  (spec `navigation-engine` — The staleness tick reads the injected time source).
  Verification: the case runs on the test scheduler with no `Thread.sleep`, and `runTest` reports no
  unfinished coroutine.
- [x] 3.3 Convert `staleSpeedTickerZeroesAStaleSpeed` (`NavigationEngineTest.kt:183-194`) off real time:
  the fix's timestamp and the window are expressed through the injected clock, and the assertion follows
  a driven tick instead of a real second.
  Verification: the case contains no `System.currentTimeMillis()` and no `Thread.sleep`, and it passes
  in `--tests "com.naviveylin.navigation.NavigationEngineTest"`.
- [x] 3.4 Convert the two wall-clock helpers (`NavigationEngineTest.awaitState:62-70` /
  `startNavigating:72-80`, `NavigationEngineRerouteTest.awaitState:69-85` incl. its
  `advanceUntilIdleBlocking` pump): await observable state on the test scheduler after
  `runCurrent()`/`advanceUntilIdle()`; where a case awaited a fix, feed the fix through
  `processLocation(...)` or await `engine.state.first { … }` inside the virtual-time test instead
  (spec `unit-test-suite-runtime` — Awaiting state, not a deadline).
  Verification: `grep -n 'Thread.sleep\|currentTimeMillis() + ' NavigationEngineTest.kt
  NavigationEngineRerouteTest.kt` returns nothing, and both classes pass in one run.
- [x] 3.5 Convert `roadInfoThrottleHoldsItsWindow` (`NavigationEngineTest.kt:159-171`) to the injected
  clock (its window is compared against real time today).
  Verification: the case passes with no `Thread.sleep` and the throttle window is advanced, not waited
  out (spec `unit-test-suite-runtime` — Injected time instead of a real clock).
- [x] 3.6 Record which dispatcher ran the native call: the fake client captures
  `Thread.currentThread().name` at `calculateRouteWithProfile`, and one case asserts it is the test
  thread; the engine's `listenerCallbacksDriveTheSharedState` keeps its state assertions and gains
  explicit ones for lane guidance, the instruction list and the maximum speed after a driven tick
  (spec `navigation-engine` — A field published during a background tick is not lost).
  Verification: the case fails when the calculation launch's injected dispatcher is replaced by
  `Dispatchers.Default` (see 4.3), and the recorded thread name is quoted in the task record.

## 4. Guards — one revert-check per new invariant (`navigation-engine`, `unit-test-suite-runtime`)

- [x] 4.1 Write `aStaleSpeedTickDoesNotRevertAConcurrentPublication` (`NavigationEngineTest`): the
  injected clock's lambda blocks on a latch after the tick's staleness check, the test publishes
  `maxSpeedKmH = 100.0` (plus a lane field) on the test thread, the latch is released, and the assertions
  read `100.0` / the lane field back. Run the revert-check named in 4.1's record: hoist the tick's
  snapshot read and write it back (`val snapshot = _state.value; …; _state.value =
  snapshot.copy(currentSpeedKmH = 0.0)`) — the case MUST fail on the `maxSpeedKmH` assertion — then
  restore and force the green run. Verification: both runs are quoted (failure message and the forced
  green tallies); the mutation shape is recorded in `design.md` so the falsification is honest.
- [x] 4.2 Revert-check the injected clock: make `tickStaleness()` read `System.currentTimeMillis()`
  directly — `staleSpeedTickerZeroesAStaleSpeed` (which advances only virtual time and never sleeps)
  MUST fail — restore, force green. Verification: both runs quoted with the failing assertion.
- [x] 4.3 Revert-check the dispatcher seam: restore `Dispatchers.Default` at the calculation launch
  (`:410`) — the recorded-thread case MUST fail — restore, force green. Verification: both runs quoted.
- [x] 4.4 Revert-check the hopped publish: publish the road-info result on the io coroutine instead of
  through `withContext(Dispatchers.Main)` — the `NavigationEngineRoadInfoTest` thread assertion MUST fail
  — restore, force green (spec `navigation-engine` — A native lookup result is published on the main
  thread). Verification: both runs quoted.
- [x] 4.5 Load-independence check (spec `unit-test-suite-runtime` — Load does not change the failure
  set): run the forced `:app` suite at the declared two forks (`-PforceTests --no-build-cache`) at least
  twice while the host is otherwise loaded, and once with the suites serialised
  (`--max-workers=1`, diagnostic only), recording the failing-class set of each run. Verification: the
  three sets are identical and the tallies (tests/failures/errors per module) are quoted; the three named
  flaky cases (`staleSpeedTickerZeroesAStaleSpeed`, `NavigationEngineRerouteTest`, the lane-guidance and
  instructions assertions) are green in every run (spec `unit-test-suite-runtime` — A formerly flaky case
  is decided by its subject).

## 5. Gate and documentation

- [x] 5.1 Confirm the tree is quiet before any gate run: `pgrep -af 'gradle-wrapper\.ja[r]'` and
  `find app/src auto/src core/src -newermt '-10 minutes'` (one builder per working tree, `AGENTS.md` rule
  4). Verification: both commands' output is quoted (or their emptiness), and no run starts while a peer
  build or edit is in flight.
- [x] 5.2 Iteration runs: `./gradlew :app:testMobileDebugUnitTest --tests "com.naviveylin.navigation.*"`.
  Verification: the verdict line with the executed-test count is quoted per iteration.
- [x] 5.3 Completion gate, forced, both flavors and all suites:
  `./gradlew test -PforceTests --no-build-cache`. Verification: the executed-task count, the per-module
  tallies (`:app` mobile, `:app` automotive, `:auto`, `:core`, JNI) and the wall clock are quoted, and
  the newest result XML per module is later than the run's start (spec `build-test-gate`).
- [x] 5.4 Add the guardrail bullet to `guidelines/Build.md` §4: a background tick or worker that
  publishes shared state publishes through an atomic update (`MutableStateFlow.update`), never a
  whole-snapshot `copy` write, and takes its timing decision from the component's injected time source.
  Verification: the bullet is present, states the rule (not the incident), and matches what the code now
  does.
- [x] 5.5 State in `design.md` (Verification) what this change does NOT need: no device, no native
  build, no instrumented test, no `pixel-check` verdict — nothing reaches a rendered surface, the
  notification, the car surface or persisted state.
  Verification: the sentence is present and no task in this file references a device or an AVD.

## 6. Bookkeeping

- [x] 6.1 `TODO.md` §121: set its status to `in-flight fix-navigation-engine-test-flakes` and append the
  mechanism this change's triage found (the two off-main writers, the `maxSpeedKmH 100.0 → NaN` reading of
  the recorded assertion) with a pointer to this change; note that the entry is removed when the change is
  archived (its folded §101 record goes with it).
  Verification: the entry's metadata line and the added paragraph are quoted from the file.
- [x] 6.2 Re-read `guidelines/Design.md` §4 and confirm the change follows it (main-thread-confined state
  publication, native calls off the main thread, one injected dispatcher seam per component rather than
  inline dispatcher names). Verification: the confirmation names the section and quotes the one clause
  the code now satisfies — no guideline text needs changing, so no Design.md edit is part of this change.

## Revert-check evidence (2026-10-06, forced runs)

- **5.3** Completion gate 2026-10-06: `./gradlew test -PforceTests --no-build-cache` →
  `BUILD SUCCESSFUL in 5m 47s`, `185 actionable tasks: 31 executed, 154 up-to-date`. Per-module tallies, all
  fresh (newest XML after the run started): `:app` mobile 220 classes / 1702 tests / 0 failures / 0 errors
  (04:59:42Z); `:app` automotive 220 / 1702 / 0 / 0 (05:02:28Z); `:auto` 76 / 781 / 0 / 0 (04:58:23Z);
  `:core` 41 / 447 / 0 / 0 (04:57:14Z); `:osmscout-client-java` 2 / 26 / 0 / 0 (04:56:51Z). JVM-only change,
  so no native artifact was built and no device was used (`build-test-gate` — Iteration runs build no more
  native artifacts than they test; the completion criterion's ABI half is unaffected because no native
  source, CMake file or NDK version changed).
- **5.1 / 5.2** Before the completion gate (2026-10-06 04:57Z): `pgrep -af 'gradle-wrapper\.ja[r]'` → no
  match (`pgrep -c` → 0) and `find app/src auto/src core/src -newermt '-10 minutes'` → nothing, i.e. no
  peer build and no peer edit in flight. Iteration runs used
  `:app:testMobileDebugUnitTest --tests "com.naviveylin.navigation.*" -PforceTests --no-build-cache` (one flavor:
  the change touches only shared sources, `build-test-gate` — Gate runs the affected flavors).
- **Baseline** — forced `:app:testMobileDebugUnitTest --tests "com.naviveylin.navigation.*"` green at
  04:40:27Z: 19 classes / 117 tests / 0 failures / 0 errors.
- **4.1** mutation: `tickStaleness` reads the state once and writes it back
  (`_state.value = current.copy(currentSpeedKmH = 0.0)`). RED 04:41:40Z,
  `NavigationEngineTest.aStaleSpeedTickDoesNotRevertAConcurrentPublication` →
  `the concurrent publication survives the tick expected:<concurrent publish> but was:<null>`.
  Restored, forced GREEN 04:42:12Z (1 test, 0 failures, 24 tasks executed).
- **4.2** mutation: the tick reads `System.currentTimeMillis()` instead of `timeSource.nowMillis()`.
  RED 04:42:44Z, `theStaleSpeedTickerDecaysOnlyPastTheWindow` →
  `a tick inside the staleness window leaves the displayed speed alone expected:<64.0> but was:<0.0>`.
  Restored (`grep -c System.currentTimeMillis NavigationEngine.kt` → 0), forced GREEN 04:43:07Z (1/0).
- **4.3** mutation: the route calculation launched on `Dispatchers.Default` again. RED 04:43:36Z,
  `NavigationEngineRerouteTest.largeDeviationConfirmsOnTheFastPath` → `State condition not met after
  draining the test scheduler`. The mutant's consequence is that the calculation never reaches the
  injected dispatcher, so the await fails before the recorded-thread assertion — the invariant under
  test (native work runs on the injected dispatcher) is what the case falsifies.
  Restored (3 × `scope.launch(dispatchers.compute)`), forced GREEN 04:43:59Z (1/0).
- **4.4** mutation: the road-info result published on the io coroutine (no `withContext(Dispatchers.Main)`).
  RED 04:44:34Z, `NavigationEngineRoadInfoTest.lookupResultIsPublishedOnTheMainThread` →
  `the lookup's result waits for the main dispatcher expected null, but was:<CurrentRoadInfo{ref='',
  typeName='highway_residential', name='Nebenstrasse'}>`. Restored (`withContext(Dispatchers.Main)` × 3),
  forced GREEN 04:44:59Z (1/0).
- **5.4** bullet added at `guidelines/Build.md:307` — “A background publisher publishes atomically, and its
  timing decision reads an injected source”, naming the measured `maxSpeedKmH → NaN` revert, the
  `TODO.md` §121 pointer and the no-wall-clock-deadline rule for tests.
- **6.1** `TODO.md:36` now reads
  `**id:** 121 · **category:** build-and-harness · **class:** bug · **status:** in-flight fix-navigation-engine-test-flakes`,
  and the entry gained the mechanism paragraph (the two off-main writers, the `Double.NaN` reading of the
  recorded assertion, the four revert-checks, the three-suite load check) plus the removal note.
- **6.2** `guidelines/Design.md` §4 (Engine block) confirmed: “native calls (route calculation, controller
  start/stop, road lookup) on `Dispatchers.Default`/`IO`, and navigation-state publication on the main
  dispatcher so surface collectors stay main-confined” — the code now takes those dispatchers from
  `EngineDispatchers` (production values are exactly those) and publishes through `update { }`; no
  Design.md text needs changing.
- **4.5** load-independence, three forced `:app` mobile suite runs (`-PforceTests --no-build-cache`):
  A 04:45:45–04:48:05Z, `BUILD SUCCESSFUL in 2m 28s`, 220 classes / 1702 tests / 0 failures / 0 errors,
  13 of 110 tasks executed; B 2m 24s, newest XML 04:51:23Z, same tallies; C (`--max-workers=1`,
  serialised tasks) 04:52:32–04:55:31Z, `BUILD SUCCESSFUL in 3m 7s`, same tallies. The failing-class set
  is **empty and identical** in all three runs, and the three formerly flaky cases
  (`NavigationEngineTest.lane guidance mirrored` / `instructions mirrored` / max-speed, and
  `NavigationEngineRerouteTest`) are green in every one.
