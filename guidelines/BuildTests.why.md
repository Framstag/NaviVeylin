# Build Tests — why

The trap each test constraint was written for, for `guidelines/BuildTests.md`. Read beside the section it
anchors to.

## §6 Test constraints

Measured 2026-10-22: the sub-sections cost 1 057–5 271 bytes each, against 22 053 bytes for §6 before the
split.

## §6a The JNI stub and the classloader sandbox

A second Robolectric sandbox makes the host stub "already loaded in another classloader". The case lives in
`:app`, so a `./gradlew :auto:test` run alone enforces nothing on `:auto` sources — the gate runs both modules.
`:auto` carries a module-wide `sdk=…` in `src/test/resources/robolectric.properties`, which is outside the rule.

## §6b Teardown, the Compose sandbox and geometry claims

- **Teardown.** `AutoMapRenderer` starts its render, extrapolation and zoom-walk loops in `init`, and only
  `shutdown()` ends them — every `:auto` renderer test that forgot it leaked a ~30 Hz loop on
  `Dispatchers.Default` plus a 1296×720 overrun bitmap (3.7 MB) per test, which made the module suite die with
  `OutOfMemoryError` in one JVM (change `fix-auto-unit-test-heap-overflow`).
- **Compose sandbox.** Measured 2026-10-16: 24 cases / 24 minutes in one `:app` mobile slice; 31 cases / ~40
  minutes in the gate (change `fix-compose-sandbox-idle`, spec `unit-test-suite-runtime`). Measured 2026-10-17
  on the `ui.map.*` + `ui.mapmanager.*` slice: `hadSnapshotChanges=true` in every iteration and ~3 M pumped
  frames in the 60 s budget; clearing the manager's flags instead of replacing the notification was falsified —
  same 24 cases. The dispatcher repair touches a finished test's half-torn-down objects if it resumes, which
  fails the next case with `Detected multithreaded access to SnapshotStateObserver: previousThreadId=…`.
- **Geometry.** A node drawn below the screen edge passes `assertExists()` and `assertIsDisplayed()`
  (`TODO.md` §138); a container's `clickable` covering a control does **not** stop Compose delivering the tap to
  the innermost target (measured 2026-10-05), so a click case alone cannot falsify an overlap guard.

## §6c Queue post-conditions and declared fork budgets

- The queue case: `MapManagerUpdatesQueueTest` lost a failure to exactly this on 2026-10-20 (one mobile-suite
  run, `expected:<[new types]> but was:<[installed]>`, 20/20 green on its own and green in three full-suite
  runs that failed 69/184/5 cases on `EDQUOT` disk writes); issue #217, change `fix-updates-queue-failure-signal`.
- **The declared-vs-measured table:**

  | Module | Suite | Declared | Measured |
  |---|---|---|---|
  | `:auto` | 49 classes / 516 tests | `1024m` | 512 MB → FAILED (141 `OutOfMemoryError` lines, 0 result XMLs, 9m08s); 1024 MB → green (one fork, 20s) |
  | `:app` | >165 classes per flavor | `1024m` | 512 MB → deterministic `FavoritesSheetReorderComposeTest` failure (`ComposeTimeoutException` after 5000 ms); 1024 MB → green (1m49s, 146 classes / 1054 tests, 2026-09-20); 2048 MB → green (2m06s 2026-09-20); 1024 MB again from 2026-09-26 with the canary bound at 30 s → green twice in a row (3m57s / 3m20s, 1245 tests per flavor, 0 failures) after the canary's real cause was fixed |

  The 2026-09-26 `:app` canary was a race in the test, not a budget problem:
  `FavoritesSheetReorderComposeTest` awaited a state change the repository produces on
  `Dispatchers.Default` (`FavoriteRepository.defaultDispatcher`); in a one-fork suite that pool competes with
  every other class's work, so the awaited change was late by an amount the bound measured as luck. It was
  insensitive to heap (1024 MB and 2048 MB both failed) and to batching (`forkEvery` 10/20/40: green once at 10,
  red at 10 and 20 on the next runs).
- A full `./gradlew test` holds up to two `:app` forks, one `:auto` fork and one `:core` fork at once; at the
  current `:app` budget that peak needs roughly 4.5-5 GB of RAM. `forkEvery` (e.g. 24 for `:auto`, 40 for
  `:app`) bounds per-fork accumulation; it is **not** part of the test task's build-cache key.

## §6d Declared test-JVM concurrency

Measured 2026-10-04, one invocation per setting with `-PforceTests -PnoCoverage --no-build-cache`, no other
Gradle build on the machine, class set printed with each run:

| suite | 1 fork | 2 forks | 4 forks | declared |
|---|---|---|---|---|
| `:app:testMobileDebugUnitTest` (218 classes / 1649 tests) | 2m50s · 1.92 GB | 2m02s · 3.42 GB | 1m51s · 4.65 GB · **1 failure** | `2` |
| `:app:testAutomotiveDebugUnitTest` (same 218 classes / 1649 tests) | 2m35s · 1.94 GB | 2m02s · 3.14 GB | 1m46s · 4.42 GB | `2` |
| `:auto:testDebugUnitTest` (76 classes / 779 tests) | 38.3s · 1.21 GB | 40.0s · 1.70 GB | 48.9s · 2.62 GB | `1` |

Durations are the test task's own execution from `build/gate-timings.json`; memory is the peak sum of the
suite's worker-JVM RSS, sampled every 2 s. Four forks failed
`NavigationEngineRerouteTest.instructionListUpdatesAfterAReroute` (awaiting a state change inside its bound)
while the same class was green at 1 and 2 forks in the same session. The gate measured 2026-10-21: mobile 2m40s
and automotive 3m00s against the 2m02s rows, `:auto` 1m16s against 40.0s, with 3.42 GiB across the two
concurrent `:app` forks — so a wall time from the table is off by 1.3× (mobile) to 1.9× (`:auto`). Re-derive
after a meaningful test-suite change (215/1635 → 218/1649 on 2026-10-04; 2 096 tests per `:app` flavor and 866
for `:auto` on 2026-10-21).

## §6e Wall-clock waits and the conversion measured

Measured 2026-10-06 on the forced gate (`test :app:assembleMobileDebug :app:assembleAutomotiveDebug
-PforceTests --no-build-cache -I tools/gate-timings.init.gradle.kts` → `BUILD SUCCESSFUL in 4m 54s`,
123 executed tasks, 0 failed tasks):

| suite | classes / tests | class time | task duration in that gate |
|---|---|---|---|
| `:app` mobile | 221 / 1719, 0 failures | 129 s | 75 s |
| `:app` automotive | 221 / 1719, 0 failures | 138 s | 79 s |
| `:auto` | 76 / 781, 0 failures | 64 s | 70 s |
| `:core` | 41 / 447, 0 failures | 14 s | 18 s |
| JNI | 2 / 26, 0 failures | 1 s | 1.7 s |

Against the 2026-10-05 gate record (`:app` mobile 4m54s, automotive 3m56s, `:auto` 1m39s, `:core` 32.8 s —
12m00s of suite work in a 9m46s wall) the same suites now take **4m02s of suite work in a 4m54s wall**. The
automotive suite alone went **252 s → 138 s of class time** with the same 221 classes:
`MapCanvasViewModelBrowseReCenterTest` 16.485 s → 0.359 s, `MapCanvasViewModelViewportRestoreTest`
16.083 s → 7.22 s, `MapCanvasViewModelAutoZoomCommitTest` 18.337 s → 9.42 s,
`MapCanvasViewModelSingleFollowCenterTest` 13.221 s → 9.29 s, `MapCanvasViewModelFollowModeTest`
7.044 s → 4.812 s. Cases that await a real route calculation still cost 5-9 s on a loaded machine
(`TODO.md` §143) and one viewport-restore case costs 6.2 s with no wait left in it (§142).

## §6f Diagnosing a red run

- A `runTest`-wrapped case always reports its `runTest(...)` line (measured: an exception thrown at
  `MapScreenTest.kt:218` reported as `at MapScreenTest.kt:198`).
- CI: measured 2026-10-08, a 16-core dev box could not be made to reproduce the load-dependent reds — 11
  aggregate reps of one mutation (one with 12 extra CPU-saturating shells, which only slowed the run to 9m32s),
  8 of another (3 with `-XX:ActiveProcessorCount=2`) — all green. Two CI runs on 2026-10-08 (runs
  `37842541876`, `37842545107`) stayed green with their mutations in place.
- Thread identity: measured 2026-10-08, `SDK 34 Main Thread @coroutine#6` against `SDK 34 Main Thread`, which
  made `MapScreenTest`'s "the client is never resolved on the host thread" assertion vacuous.

## §6g Instrumented runs and the submodule's suite

Measured 2026-09-27: one `./gradlew test` past a 45-minute tool window, mobile suite done and automotive just
started; the per-module form measured 1 m 58 s with the native build warm.

## §6h The JNI stub for unit tests

The Android `.so` cannot load on the host JVM; the stub is the host-compiled stand-in. See
`guidelines/Build.recipes.md` §6h for the stub names and the `java.library.path` wiring.
