# Build Test Guidelines — the constraints a unit or instrumented test must obey

The constraints a test must obey, by family. This document was cut from `Build.md`; the numbers did not
change, only the document that carries them.

> The trap each constraint was written for is `guidelines/BuildTests.why.md`; the JUnit stub detail and sample
> tallies are `guidelines/Build.recipes.md`.

---

## 6. Test constraints

Every sub-section states one family of constraints; read the one your case falls under. §6a "The JNI stub and
the classloader sandbox", §6b "Teardown, the Compose sandbox and geometry claims", §6c "Queue post-conditions
and declared fork budgets", §6d "Declared test-JVM concurrency", §6e "Wall-clock waits and the conversion
measured", §6f "Diagnosing a red run", §6g "Instrumented runs and the submodule's suite", §6h "The JNI stub
for unit tests".

## 6a. The JNI stub and the classloader sandbox

- **Rule: a test class that loads the host JNI stub runs under `@RunWith(RobolectricTestRunner::class)` with
  the default sandbox config — no `@Config(sdk=…)` or `@GraphicsMode(…)`.** `app/src/test/jniLibs/` holds a
  host stub so `OSMScoutClient`'s `System.loadLibrary` succeeds in JVM tests; any test class that instantiates
  `FakeOSMScoutClient` (or otherwise triggers that load) hit with a second sandbox fails with "already loaded in
  another classloader" in full-suite runs. A `@Config(qualifiers=...)` starts no second sandbox and stays
  allowed. The rule is enforced, not remembered: `JniStubSandboxRuleTest` (`app/src/test/java/com/naviveylin/test/`)
  reads the test sources of both Robolectric stub-shipping modules (`:app`, `:auto`) and fails naming file and
  line for a line-anchored `@Config(… sdk …)`/`@GraphicsMode(…)` in a file that names the client package. The
  scan reads `*.kt` under `src/test/java`, so a module-wide `sdk=…` in `src/test/resources/robolectric.properties`
  is outside the rule, and the case lives in `:app`. `:osmscout-client-java` carries the same stub (§6h) and runs
  plain JUnit 5, one classloader for the whole suite, so the rule does not reach its `*.java` cases.

## 6b. Teardown, the Compose sandbox and geometry claims

- **Rule: teardown — release what the test starts.** Any test that constructs a component owning long-lived
  background work (its own coroutine scope, timers, a render loop, a native surface, a large retained bitmap)
  MUST release it before the test returns. The pattern is `auto/src/test/java/com/naviveylin/auto/RendererTestRule.kt`:
  a JUnit rule that hands out the component, shuts every tracked instance down after the test and FAILS the test
  when one still reports active background work (`AutoMapRenderer.activeBackgroundJobCount()`; `Job.isActive` is
  false immediately after `cancel()`, so nothing needs waiting for). A leak must fail its own class, not starve
  the suite's heap later.
- **Rule: a Compose test class repairs the Robolectric sandbox's Compose plumbing first — the dispatcher and the
  snapshot-apply notifications.** `AndroidUiDispatcher.Main` is a *per-sandbox* static that outlives
  Robolectric's per-test reset, while the two callbacks that drain it do **not** survive that reset, so a
  non-Compose test that writes a Compose snapshot state leaves the dispatcher believing a dispatch is in flight
  and no later Compose case in that JVM can idle. Every Compose test class therefore declares, **before** its
  Compose rule:
  ```kotlin
      @get:Rule(order = 0)
      val composeSandbox = ComposeSandboxRule()
  ```
  `app/src/test/java/com/naviveylin/test/ComposeSandboxRule.kt` holds the entry point, `ComposeSandboxDispatcher.kt`
  both defects and both repairs; `ComposeSandboxIsolationTest` and `ComposeSandboxSnapshotPipelineTest` are the
  guards, and `tools/check-compose-sandbox-rule.sh` (CI, text only) fails the run when a Compose test class is
  added without the rule. The dispatcher repair **drops** the stranded work instead of running it; the
  notification repair applies whatever is pending and then replaces the manager's once-per-sandbox notification
  with a latch-free kick per global write.
- **Rule: geometry claims are asserted as Dp bounds, not as existence** (change `fix-nav-overlay-stop-tap`, spec
  `navigation-status-details`; the `compose-geometry` skill). A Compose case for a control's size, its tap
  target, its disjointness from a container's tap area, or a node's visibility inside its parent SHALL assert
  bounds: `assertWidthIsAtLeast(48.dp)` / `assertHeightIsAtLeast(48.dp)` for size, bare Dp comparisons for
  disjointness (`region.right <= control.left`) and for visibility. `SemanticsNodeInteraction.getBoundsInRoot()`
  returns a **`DpRect`** here, so `bounds.width` and `bounds.right - bounds.left` do not compile — never convert
  to pixels by hand. Assert the geometry and falsify it with the `revert-check` skill.

(The measured failures each rule was written for are `guidelines/BuildTests.why.md` §6a, §6b.)

## 6c. Queue post-conditions and declared fork budgets

- **Rule: a case that asserts a queue's post-condition asserts the queue's own row state too.** A case that
  drives work through a queue and then asserts only what the queue was supposed to produce cannot name *why*
  the expectation was not met: "the item failed" — its row carries a failure, a text and a cleanup path of its
  own — and "the item had not finished" reach that assertion with the same message. Assert the row state
  (`uiState.updateTransfers` and no `UpdateTransferState.Failed` for the item) before the installed file, with
  the row in the message.
- **Rule: a module that needs more than the AGP default fork heap (512 MB) declares its budget** in
  `testOptions { unitTests { all { it.maxHeapSize = … } } }`, so a fresh checkout and CI get the same budget
  (spec `unit-test-suite-runtime`). Both `:app` and `:auto` declare `1024m`.
- **Rule: a bound raise is not the answer when the awaited dependency is the cause — fix the awaited
  dependency instead.** A canary that always passed alone and failed more often as the suite grew, insensitive
  to heap and to batching, was a test race against a repository's `Dispatchers.Default`, not a budget problem;
  the test pins the repository dispatcher (`Dispatchers.Unconfined`) for its own fixtures.
- **Rule: `forkEvery` bounds per-fork accumulation instead of raising the heap ceiling — but it is not part of
  the test task's build-cache key.** After changing it, run with `--no-build-cache`, or the previous
  configuration's results are restored. Verify a budget by CONTENT, not by the build result: the fork args
  (`-Xmx…`, `--info`) and the per-class result XMLs (§4f).

(The declared-vs-measured table and the canary's history are `guidelines/BuildTests.why.md` §6c.)

## 6d. Declared test-JVM concurrency

- **Rule: unit-test parallelism is declared and result-preserving** (change `speed-up-build-test-gate`, spec
  `unit-test-suite-runtime`). The fork budget above decides *how much heap* a fork gets, this one decides *how
  many run at once*, and both are declared in the same block (`it.maxParallelForks`): `:app` declares `2`, `:auto`
  declares `1`. Four forks is deliberately not declared: it is not result-preserving (a `:app` case failed at 4
  forks and was green at 1 and 2 in the same session), and `:auto` is short enough that per-JVM start-up
  outweighs the split. The class-name sets are identical at every setting. The declared per-suite table
  describes one suite per invocation, and the gate is not that shape — it runs all four modules in one
  invocation, where the forks plus the daemon contend.

(The fork/memory table, the 4-fork failure and the gate-contention figures are
`guidelines/BuildTests.why.md` §6d and `guidelines/BuildGate.why.md` §2c.)

## 6e. Wall-clock waits and the conversion measured

- **Rule: wall-clock waits are refused** (change `speed-up-test-iteration`, spec `unit-test-suite-runtime`).
  Each test-bearing module wires `checkNoWallClockWaits` (the pure `buildSrc` object
  `com.naviveylin.build.testing.WallClockWaitScanner`) into its `preBuild`, and the check has **no allowlist**:
  a `Thread.sleep`, a `TimeUnit.X.sleep`, a `while (… System.currentTimeMillis() …)` deadline loop or a
  clock-derived `deadline` binding fails the build naming file and line.
- **Rule: a test that must wait drives the component's seam and awaits observable state** — the ViewModel's
  `EngineTimeSource` / `rendererDispatcher`, `EngineDispatchers` for the navigation engine,
  `DiagnosticsLog.awaitDrained()`, the auto renderer's `zoomWalkLoopTurns`, copy-on-write collections in a fake.
  Cases that await a **real** route calculation still cost seconds on a loaded machine; that is pre-existing.

(The conversion's measured before/after and the per-class timings are `guidelines/BuildTests.why.md` §6e.)

## 6f. Diagnosing a red run

- **Rule: a red run is diagnosed from the failure's message, not from its location** (change
  `fix-aggregate-run-test-flakes`, spec `build-test-gate`). `:app`, `:auto` and `:core` each set
  `exceptionFormat = "full"` on their unit-test tasks, so the run's own console output names each failing case
  and carries the message the failure was raised with — no `--info`, no downloaded result XML. Gradle's short
  location is the frame of the **test method**: read the message as the diagnosis and the location as the
  method, never as the throwing statement. A load-dependent case is diagnosed from the failing aggregate run's
  own record — the invocation that reproduces it is the aggregate one
  (`./gradlew test -PforceTests --no-build-cache`, §3), never a batched or single-class rerun.
- **Rule: a falsification that no local machine reproduces is run in CI, on a throwaway branch** (change
  `fix-aggregate-run-test-flakes`, spec `unit-test-suite-runtime`). Commit **one** mutation on a throwaway
  branch → `gh workflow run build.yml --ref <branch>` → read the failing case from the step log
  (`gh run view <id> --log`) → delete the branch (`git push upstream --delete <branch>`). Keep the mutation off
  `main`: the branch is evidence, not a fix. **Read the result honestly**: a green run with the mutation in
  place means *not falsified* — the task stays open, it is not marked done.
- **Rule: a thread-identity assertion compares the `Thread`, not its name** (change
  `fix-aggregate-run-test-flakes`, spec `unit-test-suite-runtime`). Under Robolectric a coroutine's thread name
  carries a `@coroutine#N` suffix, so `Thread.currentThread().name` inside a coroutine never equals the bare
  main-thread name captured outside one — capture `Thread.currentThread()` once and compare identity
  (`assertSame` / `assertNotSame`). A name is fine in a failure message, not as the guard.
- **Rule: one invocation per suite.** `:auto` and `:app` each complete in a single Gradle invocation at the
  declared budget; splitting a suite into class batches is a diagnostic fallback, never the procedure, and a
  batched run is not evidence for the suite as it really runs. A Gradle build-cache hit (`FROM-CACHE`,
  `BUILD SUCCESSFUL in 2s` with no test executor) is not test evidence either — use `--rerun` when the run
  itself is the evidence.
- **Rule: a timeout-shaped failure tests the resource hypothesis first.** Before hunting state pollution, check
  the fork heap/cadence budget: a `ComposeTimeoutException`/awaited-state failure that went green only after
  raising the fork heap was the resource dimension, not a state bug. "Pre-existing" is proven by a control run
  (`git stash push -u` → the same failing command → `git stash pop`), never by reasoning about the dependency
  graph.

(The CI run IDs, the measured thread-name string and the case that made each rule are
`guidelines/BuildTests.why.md` §6f.)

## 6g. Instrumented runs and the submodule's suite

- **Rule: instrumented tests need a connected device/emulator; if none is available, say so instead of running
  them.**
- **Rule: after a submodule bump, use the per-module gate form, not one `./gradlew test`** — it carries the
  native CMake configure/build for both flavors × three ABIs, and the daemon that ran it failed
  `:app:mergeExtDexAutomotiveDebug` with `D8: java.lang.OutOfMemoryError: Java heap space`. Use
  `./gradlew :app:testAutomotiveDebugUnitTest :auto:testDebugUnitTest :core:testDebugUnitTest`, quote
  per-module counts from `test-results/*.xml`, and `./gradlew --stop` before a flavor assemble when a long
  suite ran first.
- **Rule: the submodule's meson suite has a per-test budget that one test exceeds** —
  `libosmscout:Check threaded database` (`Tests/ThreadedDatabaseTest --threads 100 --iterations 1000`) is killed
  at 60 s (`30 s` default × the repo recipe's `--timeout-multiplier 2`) and passes in 120 s when run alone with
  `--timeout-multiplier 60`. Quote that timeout in any "suite is green" verdict, and re-run a lone timeout with a
  bigger multiplier before calling it a failure.

(The measured 2026-09-27 run past a 45-minute window is `guidelines/BuildTests.why.md` §6g.)

## 6h. The JNI stub for unit tests

- **Rule: the committed JNI host stub ships in each test source set so `System.loadLibrary` succeeds.**
  `app/src/test/jniLibs/`, `auto/src/test/jniLibs/` and `osmscout-client-java/src/test/jniLibs/` each hold the
  same tiny host-compiled stub (ELF, no symbols, named both `libosmscout_client_java.so` and
  `libosmscout_client_javad.so`); the `_javad` variant is the fallback name the loader tries second. The stubs
  are **committed** (`.gitignore` re-includes them via `!app/src/test/jniLibs/*.so`, `!auto/src/test/jniLibs/*.so`
  and `!osmscout-client-java/src/test/jniLibs/*.so`). Keep them in the test source set only, and never use them
  in the app. Tests override the native methods through fakes. The classloader rule this stub imposes is §6a.

(The exact stub names, the `java.library.path` wiring and the fake classes are `guidelines/Build.recipes.md`
§6h.)
