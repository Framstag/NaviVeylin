# Build Guidelines — Build, Test & Release Skills

How to build NaviVeylin, run its tests, and produce release builds. This document
names the skills that wrap the Gradle workflows and explains when and how to use
them.

**Maintenance rule** — when a change supersedes a build convention here, update
this document in the same change.

---

## 1. Skills

Five skills wrap the existing Gradle calls and the verification discipline around them. They are named
and discoverable by LLM agents (loaded on demand from `.pi/skills/`); use them whenever build, test,
verification, or release work is requested or required by the OpenSpec apply/archive guidance.

| Skill | Purpose | When to use |
|---|---|---|
| `build-app` | Compile the app — debug APKs, flavors, ABIs | User asks to build/compile; verify code compiles; after Kotlin/native changes |
| `run-tests` | Execute unit + instrumented tests | User asks to run tests; verify tests pass; before marking a task complete |
| `revert-check` | Falsify a new guard: one mutation → the named case must fail → restore → forced green, with the evidence recorded | A task says "revert-check"/"one mutation"/"the case that must fail"; a change adds a test for an invariant; before claiming a test covers new behaviour |
| `compose-geometry` | Assert a UI geometry invariant (size, tap target, disjointness, visibility inside a parent) in a Compose case instead of `assertExists()` | A phone control or tap area is added/moved; a task asks for a bounds/48 dp/disjointness/visibility case; a device run shows a tap landing on the wrong action or an action that is unreachable |
| `release-build` | Produce Play-ready release AABs (mobile + automotive) | User asks for a release build or version bump; Play upload / sideload artifacts |

Each skill lives in `.pi/skills/<name>/SKILL.md` (gitignored — copy to
`~/.pi/agent/skills/<name>/` to make it available across projects).

### Screenshot reading (harness prerequisite)

Reading a screenshot is a tool the harness must provide; it is not part of the repository.
**`view_image`** comes from the Pi package `@luan.sh/pi-view-image`:

```bash
pi install npm:@luan.sh/pi-view-image   # verified working 2026-10-06, pi 1.0.4
```

It reads a local PNG/JPEG/GIF/WebP and returns it as an image content block, so a vision-capable
model sees the pixels — `ctx_read` returns only a placeholder for an image file, and lean-ctx runs in
`mode: "replace"`, so Pi's built-in image-capable `read` is not available. The package builds a small
Rust binary on first use (a Rust toolchain is required; set `PI_VIEW_IMAGE_BIN` to use a prebuilt
executable). Look first, then measure with `tools/measure-highlight.py` — the look is triage, the
script's numbers and exit code are the evidence (`pixel-check` skill, §10 below).

## 2. Common behavior

All four skills follow the same contract:

- **Wrap the existing Gradle calls** (`./gradlew ...`) — no new build system,
  no wrapper scripts, no duplicated command logic.
- **Print status messages** before and after each run (e.g. "Building debug APK
  (all ABIs, both flavors)…", "Build succeeded — …").
- **Redirect the output to a log, keep the console readable** — a full build or
  suite exceeds the shell tool's ~120s **output** cap, so run it in the
  **foreground** with `> /tmp/<name>.log 2>&1` and grep the verdict, the `e: ` /
  `w: ` lines and the tallies back. Do **not** background it (`nohup … &`): the
  harness kills the process group when the tool call ends, and the run then dies
  mid-build with no verdict (`setsid` is not available either, so there is no way to detach that survives
  the tool call).
- **Evaluate the result by return code AND build output**:
  - Exit code `0` **and** output contains `BUILD SUCCESSFUL` → success
  - Exit code non-zero **or** output contains `BUILD FAILED` → failure
- **Report a clear verdict** with actionable error excerpts on failure.

### Liveness guard and shell recipes

- **The "no other build is running" guard must be able to match the real process.**
  `pgrep -f 'GradleWrapper[M]ain'` never matches: the wrapper runs as
  `java -Xmx64m … -Dorg.gradle.appname=gradlew -jar …/gradle-wrapper.jar <tasks>`, so
  `org.gradle.wrapper.GradleWrapperMain` lives *inside* the jar and never appears in the argument vector
  (the daemon shows as `gradle-daemon-main-<version>.jar`). Use `pgrep -af 'gradle-wrapper\.ja[r]'` — the
  bracket keeps the pattern from matching the `pgrep` call itself — or track the PID the launcher printed,
  and then confirm with the `BUILD SUCCESSFUL|BUILD FAILED` verdict line in the log. A liveness probe that
  cannot match the real process reports "finished" for work still in flight (measured 2026-10-04 and again
  2026-10-04/05), and a guard's own `echo` must never state a verdict the guard did not measure.
- **Allowlist recipes (use these instead of re-discovering them):** `find <dir> -print | xargs -r rm -rf`
  (never `find … -exec rm -rf {} +`); `awk` for arithmetic (`bc` is not allowed); `perl -0pi` is blocked —
  for a bulk edit use the editor's `replace_all` with an anchor on a neighbouring unique line; no `$()` or
  backtick substitution at command position (prepare the command once, print it, paste it verbatim); a
  helper script under `/tmp` is not runnable — inline the commands; the projection head unit binary needs
  `lean-ctx allow desktop-head-unit` (or a human at the DHU window).
- **Shell traps that look like build failures:** zsh (the harness shell) does **not** word-split — a
  `$filters` variable or `for x in "a b"` arrives as one argv element, so Gradle answers
  "Task ' --tests …' not found" (pass filters literally); an `adb` call inside a `printf | while read` loop
  consumes the pipeline's stdin (add `</dev/null` to every `adb` call in the loop); and
  `adb logcat -d --pid <pid>` hangs — use `-t <N>` plus an `awk` filter on the PID column instead.
- **Init-script traps:** in `*.init.gradle.kts` a deprecated Gradle member is a **compile error**, not a
  warning (a Groovy `*.init.gradle` would only warn). Probe the script API by reflection in a throwaway
  Groovy init script before writing the Kotlin one. A harness that overrides a value the build script also
  sets must apply in `gradle.projectsEvaluated { }` (an earlier hook is overridden by the script's own
  `configureEach`) and must be verified by an observable — the worker-JVM count, not the flag it was given.
- **A tool that infers a directory layout needs a shape check.** `tools/prune-native-configs.sh` deleted
  ABI directories *inside* hash trees once because the layout had shifted one level; refuse a root whose
  grandchildren are ABI names, and dry-run with the **same** arguments as the real run — a dry run validates
  only the exact invocation that is repeated.
- **A suite run that died mid-flight is not neutral.** "No verdict in the log" means *killed*, not failed
  (`TODO.md` §40.3): split the gate per module instead of re-running the aggregate. Before the next run,
  delete the dead run's results (`rm -rf app/build/test-results/<flavor>`) — otherwise the next invocation
  can abort with `java.nio.file.NoSuchFileException: …/in-progress-results-generic.bin` *after* printing its
  tallies, losing the per-test detail. A killed run also leaves its daemon `BUSY`: `./gradlew --status`,
  `./gradlew --stop`, then re-run (extends §40.4).

- **The allowlist is not a planning assumption — probe it** (§40.1). `python3` (3.14.7), `perl` and `tesseract`
  (5.5.3) all **run** in this shell (verified 2026-10-03; `TODO.md` §106's device pass used `tesseract`), so
  do not write a plan around a tool being blocked. `jq`/`grep`/`sed`/`awk` stay the house style for text
  work, and `openspec` interaction always goes through the CLI's own `--json` output — never a script.
  Reach for `python3` only when shell text tools genuinely cannot express the thing.
- **Never `pkill -f "gradlew …"`** (§40.2): `-f` matches the calling tool's own command line, kills the
  shell, and the rest of the chained command (e.g. a `git commit`) silently never runs. Kill by PID:
  `ps -o pid,args | grep 'gradle-wrapper\.ja[r]'` → `kill -9 <pid>`.
- **Do not use `/tmp` for large map imports** (§40.7): it is a RAM tmpfs (~7.7 GB quota), so an import
  there competes with the build for memory — use a disk path.

### Gate timing record and baseline

Every gate run can leave a machine-readable record of what it cost, so "the gate got faster" is a
measurement instead of an impression (change `speed-up-build-test-gate`, spec `build-test-gate`):

```bash
./gradlew -I tools/gate-timings.init.gradle.kts <tasks…>   # writes build/gate-timings.json
bash tools/gate-timing-report.sh                           # defaults: build/gate-timings.json  .
```

The script only observes. It never adds an action to a task: a task action would change the task's
action class and with it the build-cache key, so every instrumented run would miss the cache and
re-execute everything the record exists to measure. It reports per task (path, outcome, duration) and per
suite (wall time from the first and last result-XML `timestamp`, class count, test count, failures). Its
own behaviour is covered without a device: `bash tools/gate-timings-selftest.sh`.

To force execution of the tests — the case where the run itself is the evidence — use
`-PforceTests --no-build-cache`, not `--rerun-tasks`: it re-executes the test tasks and leaves the rest of
the graph up to date (§4).

**Baseline (2026-10-04)** — `:app:testMobileDebugUnitTest :app:testAutomotiveDebugUnitTest
:app:assembleMobileDebug :app:assembleAutomotiveDebug --rerun-tasks --continue`; host with 15 GB RAM of
which ~1 GB free, Android Studio and the Pixel_8 emulator running, load average 0.8:

| phase (sum of executed task durations) | baseline |
|---|---|
| compile & package | 7m57s over 107 tasks |
| tests | 5m38s over 2 tasks |
| other executed | 18.0s over 89 tasks |
| Gradle's own build (buildSrc) | 0.8s over 3 tasks |
| **wall clock** | **8m25s** (log: `BUILD SUCCESSFUL in 8m26s`, `189 actionable tasks: 189 executed`) |
| `:app:testMobileDebugUnitTest` | 2m47s — 215 classes, 1635 tests, 0 failures |
| `:app:testAutomotiveDebugUnitTest` | 2m38s — 215 classes, 1635 tests, 0 failures |

The per-task sums exceed the wall clock because Gradle overlaps compile, packaging and test tasks:
quote the wall clock and the per-suite windows, and read a phase sum as work, never as elapsed time.

**After the change (2026-10-05)** — the levers this baseline was measured against, each with its numbers:
the two `:app` suites run at the declared two forks (mobile 2m50s → 2m02s, automotive 2m35s → 2m02s per
suite, §6); the Kover agent costs 10-12 % and is dropped for an iteration that feeds no report (§7); a
JVM-only change builds no native artifact, one ABI is buildable for a device target, and `app/.cxx` is kept
at 4 configuration trees instead of 13 (§4); forcing the test tasks instead of the whole graph leaves the
compiles `UP-TO-DATE` (§4); a shared-source change runs one flavor instead of two (§4). The full gate — all
modules, both flavors, instrumentation attached — ran green: `:app` mobile 219 classes / 1666 tests,
automotive 219 / 1666, `:auto` 76 / 779, `:core` 39 / 444, JNI 2 / 26, all with 0 failures and 0 errors.

**The aggregate is not the sum of those levers — measured, and worth reading before quoting the table
above.** One clean full gate (`test -PforceTests --no-build-cache`, all four modules, no other build on the
machine, 4.4 GB RAM free) took **9m46s** wall with only **17 of 185 tasks executed** (168 up-to-date) and
suites `:app` mobile **4m54s**, automotive **3m56s**, `:auto` 1m39s, `:core` 32.8s, JNI 1.7s. The suites
share the invocation, so their forks (2+2+1) plus the daemon contend for CPU and — on a box with ~4 GB
free — for memory: 12m00s of test-task work compressed into 9m46s of wall, and every suite is slower than
its isolated measurement (2m02s alone at two forks). Consequences to keep in mind: the per-suite table in
§6 describes **single-suite** runs, the aggregate gate is bounded by suite work rather than by any flag this
change added, and the declared two forks may be too optimistic when several modules' suites run at once —
see `TODO.md` §132.

## 3. Commands reference

| Goal | Command |
|---|---|
| Build debug APK (all 3 ABIs, both flavors) | `./gradlew :app:assembleDebug` |
| Build phone/Android Auto flavor only | `./gradlew :app:assembleMobileDebug` |
| Build AAOS flavor only | `./gradlew :app:assembleAutomotiveDebug` |
| Build single ABI (fastest iteration) | `./gradlew :app:assembleMobileDebug -Pandroid.injected.build.abi=arm64-v8a` |
| Run all unit tests | `./gradlew test` |
| Run all unit tests — the **full gate** (all modules, both `:app` flavors, forced) | `./gradlew test -PforceTests --no-build-cache` |
| Run app unit tests only (one flavor — there is no `:app:testDebugUnitTest`) | `./gradlew :app:testMobileDebugUnitTest` (or `:app:testAutomotiveDebugUnitTest`) |
| Run single test class | `./gradlew :app:testMobileDebugUnitTest --tests "<FQCN>"` |
| Run instrumented tests (device required) | `./gradlew connectedAndroidTest` |
| Release build (both AABs, bumps version) | `./gradlew release` |
| Generate SBOM for one variant | `./gradlew :app:generateSbom<MobileDebug|MobileRelease|AutomotiveRelease>` |
| Generate only the native SBOM section | `./gradlew :app:mergeNativeSbom` |

## 4. Result evaluation

- **Success**: exit code `0` and `BUILD SUCCESSFUL` in the output.
- **Failure**: non-zero exit code or `BUILD FAILED`. Extract and report:
  - Kotlin compile errors: lines starting with `e: `
  - C++/NDK errors: `error:` lines from CMake/ninja
  - Failed tasks: `> Task ... FAILED` lines
  - `checkSubmoduleStylesheets` failure → libosmscout submodule not initialized
    (`git submodule update --init --recursive`)
- **Warnings**: the build must have no warnings — report them, don't ignore.
- **Test failures**: extract `FAILED` lines (test class + method), rerun a
  single test with `--tests "<FQCN>"` to isolate, then report.
- **Per-flavor test tasks**: `:app` has one task per flavor —
  `:app:testMobileDebugUnitTest` and `:app:testAutomotiveDebugUnitTest` (there is
  no `:app:testDebugUnitTest`).
- **Which flavors a run must cover** (change `speed-up-build-test-gate`, spec `build-test-gate` —
  "Gate runs the affected flavors"): the routine gate runs the `:app` suite of the flavors the change can
  affect — **both** when it modifies `app/src/automotive/**`, a flavor's manifest or resources, the flavor
  definitions in `app/build.gradle.kts`, or native inputs; **one** (`mobile`, the base flavor) for a change
  that touches only sources the two flavors share. Measured 2026-10-04: both suites execute the identical
  215 test classes / 1635 tests, so the second suite is duplicate work unless a flavor input changed —
  `mobile` 2m58s + `automotive` 2m57s for both against 2m58s for one (agent attached, §7), a saving of about
  3 minutes per gate. **Before a change is called complete, run both flavors with every shipped ABI built**:
  the reduced form is an iteration form and must not be reported as a full gate. `build/gate-timings.json`
  (§2) shows how many suites a run actually executed.
- **Iteration: run the change's declared cases first** (change `speed-up-test-iteration`, spec
  `build-test-gate` — "Iteration pre-gate runs the change's declared cases first"). Before the module
  suite, `bash tools/declared-cases.sh <change> --diff <this change's own file list> --command` prints
  the test classes the change declares — those its artifacts name, those its diff touches, those whose
  source mentions a type the diff changes — with their measured class time, and the ready Gradle line.
  Run it in **two stages**: `--rules declared,touched` is the author-declared stage (measured 9 classes /
  11.1 s class time for `fix-route-session-stop-path` against 220 classes / 252 s for the whole `:app`
  suite), the full union is the second stage (82 classes / 91.1 s for the same change — `mention` is the
  wide rule, because the app's central types are named across the suite). A shared working tree inflates
  `touched` and `mention` with other changes' dirty files, so the change's own file list is the honest
  `--diff` input. **A pre-gate run is not gate evidence** and the completion rule is unchanged: the
  unfiltered module suite, both `:app` flavors and every shipped ABI are still owed before the change is
  called complete. Expect ~30-45 s for a declared stage — the Robolectric fork bootstrap (~11 s per fork),
  the daemon and a test-source compile are the floor, so a 10 s iteration loop is not on offer.
- **Report tallies, not vibes**: quote `tests/failures/errors` per module from
  the XML (`app/build/test-results/testMobileDebugUnitTest/*.xml` etc.), e.g.
  ":core 379/0/0 · :auto 699/0/0 · :app mobile 1314/0/0 · :app automotive 1314/0/0".
  A bare `BUILD SUCCESSFUL` says nothing about what ran.
- **A cached run is not a run**: `BUILD SUCCESSFUL in 3s` with `UP-TO-DATE` / `FROM-CACHE`
  executed no tests. When the run itself is the evidence, force the **test tasks** (next bullet), and use
  `--rerun-tasks` only when the whole graph has to be re-executed.
- **How to force it: `-PforceTests --no-build-cache`** (change `speed-up-build-test-gate`, spec
  `build-test-gate`). It makes the JVM unit-test tasks not up to date without re-executing the rest of the
  graph — measured 2026-10-04 on `:core`: `:core:compileDebugKotlin UP-TO-DATE` with
  `30 actionable tasks: 2 executed, 28 up-to-date`, where `--rerun-tasks` re-executes every compile,
  package and native task. `--no-build-cache` is not optional: a task that is not up to date may still
  have its outputs restored from the cache rather than executed. Keep `--rerun-tasks` for the completion
  gate and for anything whose inputs the change touched; the gate record in §2 shows what each phase
  costs.
- **Native scope of a run** (change `speed-up-build-test-gate`, spec `build-test-gate` — "Iteration runs
  build no more native artifacts than they test"). A JVM-only change needs **no** native build: the
  `:app` unit tests use the host stub (`src/test/jniLibs`), not the packaged libraries. When a run does need
  native artifacts for one device or emulator, `-Pandroid.injected.build.abi=arm64-v8a` builds that ABI
  only — measured 2026-10-04, the record shows `configureCMakeDebug[arm64-v8a]` / `buildCMakeDebug[arm64-v8a]`
  executing and nothing for the other ABIs, while the APK carries the app's own libraries
  (`libosmscout*.so`, `libnaviveylin_log_bridge.so`, `libomp.so`) only under `lib/arm64-v8a/` — the other
  ABI directories hold just the prebuilt `libandroidx.graphics.path.so`, which AGP ships regardless. Before
  a change is called complete, every shipped ABI must be built (`:app:assembleMobileDebug
  :app:assembleAutomotiveDebug` → all three ABIs in both APKs). A one-file native change costs ~5.5 s of
  CMake work across the three ABIs (arm64 2982 ms, armv7 2500 ms, x86_64 78 ms on 2026-10-05), so the
  expensive native case is a **new configuration hash**, which is what the prune below and a stable
  configuration avoid — not an everyday incremental build. CCache/sccache are not installed on this machine;
  the measured potential saving for an incremental change is a fraction of those 5.5 s, so it was declined
  rather than assumed (a cold full rebuild is the case that would justify revisiting it).
- **Superseded native configurations are pruned** (spec `build-test-gate` — "Superseded native
  configurations are pruned"): `bash tools/prune-native-configs.sh --dry-run --skip tools app/.cxx`, then the
  same command without `--dry-run`. It keeps the newest N (default 2) configuration trees per build type and
  removes whole `<buildType>/<hash>` trees; it refuses while a Gradle build is active (deleting the tree of
  a configuration AGP still considers current would force a reconfigure mid-build) and refuses a mis-scoped
  root (a build-type directory instead of the `.cxx` root). `--skip tools` leaves the tiny host-tool
  configurations alone, whose "current" entry is ambiguous and whose deletion costs a re-configure for
  almost nothing. Measured 2026-10-04/05: `app/.cxx` went from 7.6 GB / 13 trees to 2.1 GB / 4 configuration
  trees (+ the tool configs), and the next native build reconfigured and rebuilt as needed with no manual
  step. Device-free self-test: `bash tools/prune-native-configs-selftest.sh`.
- **Restoring a mutation re-creates a cached tree**: after a revert-check the source hash equals the state
  whose successful result is already cached, so the restored run answers `UP-TO-DATE` in seconds and the
  XML keeps the **previous** run's `timestamp` — a 4-second "green" is not the second half of a
  revert-check. Force it and check the `timestamp` against the wall clock (`TODO.md` §17; the
  `revert-check` skill).
- **Sweep the suite for what the change moves, before the gate**: a behaviour change can move an
  expectation pinned verbatim in an unrelated class, and focused runs cannot see it (2026-10-03: the POI
  fit's floor changed and only the full gate found `PoiSearchViewModelTest` asserting the old `14.0`).
  `grep -rn '<the constant or value you changed>' app/src/test core/src/test auto/src/test`, plan that
  expectation change, and quote old → new in the task. Prefer re-expressing the assertion against the spec
  (what must be visible/equal/ordered) over pinning a new number.
- **Attribute before blaming or claiming** — a failure that moves between runs or
  flavors, and that passes alone, may not be yours. Ritual:
  1. rerun the failing class **alone** (`--tests <FQCN>`);
  2. rerun it **together with the classes the change added**;
  3. if it still fails, find who ran **before** it: the JUnit XML `timestamp` of
     each `testsuite` gives the execution order (one fork here, so the order is a
     single sequence — sort the timestamps and look at the neighbours);
  4. bisect with `--tests` filters over that order. Note the shell does **not**
     word-split variables: pass the filters as literal arguments, or wrap the
     call in a script;
  5. quote **both** numbers (the failing set and the alone run) in the change and
     file the residual in `TODO.md`; leave the "zero failures" task item
     **unchecked** rather than talking the run green.
- **`UncaughtExceptionsBeforeTest`** is not a flaky assertion: a coroutine threw
  on a *process-wide* dispatcher (`Dispatchers.Default`/`IO`, or the main thread)
  after the test that owned it ended, and the next `runTest` in that JVM reports
  it. Hunt the owner, don't retry the victim: look for scopes/tickers that outlive
  a test (a process-scoped poller, a `CoroutineScope` in a `@Singleton`) and for
  any coroutine body on a real dispatcher **without** a fault confinement
  (`runCatching` or a `CoroutineExceptionHandler`). This is a production defect
  too: the same escaping throwable reaches the thread's uncaught-exception handler
  and kills the app on a device.
- **`Dispatchers.Main is used concurrently with setting it`** is the same leak one
  layer down: a coroutine from an earlier test — or a state holder that outlives
  its test — is still dispatching onto the main dispatcher while the *next* test's
  rule replaces it. `TestMainDispatcher` records the offending **read** and throws
  it on the next modification, so the failure lands in `MainDispatcherRule`
  (`starting`/`finished`) of an unrelated case and the victim moves between runs
  (observed 2026-09-28: CI runs `36449101190`/`36453748630`, 14 and 6 failures over
  eight different classes). Read the exception's stack, it names the leaker: the
  primary trace when it is created in `NonConcurrentlyModifiable.getValue`
  (`TestMainDispatcher.kt:73`), or the `Caused by: java.lang.Throwable: reader
  location` when the *writer* was the caller. Those frames were
  `DispatchedCoroutine.afterResume` ← `CoroutineScheduler$Worker.run`, i.e. a
  `withContext(…background…)` completing and resuming into the main dispatcher.
  Hunt repeating `withContext`-back-to-main **before** bisecting classes: grep the
  `while (true)`/`while (isActive)` loops for a `withContext`, then move the loop's
  home off the main dispatcher (`guidelines/Design.md` §4) instead of enumerating
  the classes that leak a holder.
- **A new regression test must fail against the *unfixed* revision first.** One that passes against the bug
  is worse than none: run it on the pre-fix tree (stash, or the parent commit) before claiming it guards the
  fix. If it cannot fail there — e.g. Robolectric defers the child collector that runs inline on the device —
  delete it and record that a device run is the only evidence.
- **A revert-check's green half needs `--rerun-tasks`, and the case must assert the *positive* fact.** After
  restoring a mutated file the source hash matches an earlier state, so the test task answers `UP-TO-DATE` or
  restores from cache while the XML keeps the **previous** run's `timestamp`: force it and compare that
  `timestamp` with the wall clock before quoting counts (`TODO.md` §17). And the assertion must be what the
  guard produces — the buffer *was* acquired exactly once — not the absence of growth: an "allocation count
  did not increase" case passes under the reverted path when its baseline is already zero.
- **A flaky victim needs a rate per configuration (N ≥ 3) before a bisect means anything.** One run per
  combination decides nothing at a ~1-in-2 rate; measure runs per configuration instead, and never infer the
  leaker from the victim's *package* — one process-wide leak produced failures in three unrelated packages
  (`UncaughtExceptionsBeforeTest` above). Also bound the test that perturbs the suite: an oversized drag left
  a reorder library's auto-scroll running into whatever executed next in the same JVM.
- **Test-scheduling traps.** `advanceTimeBy` is *exclusive* at its boundary, so a debounce deadline landing
  exactly on the advanced-to instant never fires — advance past it by more than a tick, or `runCurrent()`
  after an exactly-at-the-deadline advance (a case that looked 50/50 flaky was this). And a production
  `withContext(…)` inside a suspend accessor makes a virtual-time test non-deterministic (green alone, red
  under suite load): give the hop its own dispatcher seam (`guidelines/Design.md` §4).
- **A background publisher publishes atomically, and its timing decision reads an injected source** (change
  `fix-navigation-engine-test-flakes`, specs `navigation-engine` / `unit-test-suite-runtime`): a tick or
  worker that runs off the main thread publishes through `MutableStateFlow.update { … }`, never
  `state.value = state.value.copy(…)` — the whole-snapshot form writes back a state it read earlier and
  silently reverts a field another writer published inside that window. Measured 2026-10-05: the
  navigation engine's stale-speed tick reverted `maxSpeedKmH` to its `Double.NaN` default, which is what
  made `NavigationEngineTest`'s lane/instruction/max-speed assertions look `~1-in-2` flaky for days
  (`fix-navigation-engine-test-flakes`, archived 2026-10-06 — the entry it closed, `TODO.md` §121, was
  removed with it; the same shape was fixed for the road-info lookup's publish, which now hops with
  `withContext(Dispatchers.Main)`). Timing decisions — staleness, throttle, cooldown — read the
  component's injected time source, never `System.currentTimeMillis()` at the decision site, so a test
  moves time instead of waiting for it; and a test may not compute a deadline from the wall clock or pace
  itself with `Thread.sleep` — it drains the injected schedulers and asserts.
- **Test reports**: HTML at
  `app/build/reports/tests/testMobileDebugUnitTest/index.html`, XML at
  `app/build/test-results/testMobileDebugUnitTest/*.xml`.

### Artifacts and evidence rules

- **Verify an artifact against its inputs, not by the existence of an output path** (§40.6): a
  build-cache-restored APK in `outputs/apk/` and a stale `build/outputs/sbom/*` both look exactly like a
  product bug. Compare content/mtime against the change, and probe a packaged literal rather than trusting
  the path.
- **One mutation per revert check** (§40.12): a combined mutation masks the other behaviour and the
  assertion becomes vacuously true — mutate one guard, run every case it protects, restore, then force the
  green run.
- **After a semantic change to a shared value, grep the test tree for the observable it moved** (§40.32):
  `lastRenderMag` holds the raw scale now (`2^level`), and a focused run cannot see an expectation pinned
  verbatim in an unrelated class.

### Native verification — new methods, hidden visibility

- **A *new* native method is invisible to the compiler.** Adding a method to `OSMScoutClient` breaks
  nothing: the test fakes compile without overriding it, and the first *call* throws `UnsatisfiedLinkError`
  (the host stub exports no symbols). The `native-bridge-signature-change` skill's compiler sweep catches
  *changed* signatures only — update every fake in the same change and run the affected suites, because the
  suite is the only proof the call path works. (Two-`#ifdef` verification and meson target naming:
  `TODO.md` §40.47/§40.48.)
- **A hidden-visibility library may only expose *exported* types on its interface.** `libosmscout` builds
  with `-fvisibility=hidden`: taking an older, un-annotated class by value fails for every consumer outside
  the library with `undefined reference to vtable for …` (including `Tests/`); hold the exported factory
  handle (`StringMatcherTransliterateFactory`) instead of a by-value base member.
- **`ctest` after a partial `ninja` is not evidence.** Build every target first (`ninja -k 0`) before
  reading a suite verdict, and prove a suspected regression by rebuilding that target with the change
  stashed — a stale executable run against a new shared library was this repo's false positive.
- **Any change inside an `#ifdef OSMSCOUT_HAVE_LIB_MARISA` block is verified in BOTH configurations**
  (§40.47): the Android build always defines it (vcpkg ships marisa), so CI's non-Marisa path is invisible
  here. Reproduce: insert `#undef OSMSCOUT_HAVE_LIB_MARISA` after the include block of `OSMScoutClient.cpp`,
  build `:app:assembleMobileDebug -Pandroid.injected.build.abi=arm64-v8a`, then remove the `#undef`.
- **Native test binaries are not directly executable here** (§40.48): `ctest -R <Test> --output-on-failure`
  does **not** build — `ninja -C <build> <Target>` first, and filter `ctest -N` output for `Test #` lines.
  The meson `hostbuild/` directory has no `CTestTestfile.cmake`, so `ctest` answers "No tests were
  found!!!" even for a registered, green test: use `meson test -C hostbuild "<test name>"
  --print-errorlogs` there, list the real names with `meson test -C hostbuild --list | grep -i <topic>`
  first, and spend one throwaway `ninja` invocation after a `meson.build` edit so the regeneration lands
  before the target lookup.
- **`System.loadLibrary` needs the plain-name `.so`** (§40.49) — a versioned `.so.1` symlink is not found.
- **Plain `openDatabase(containerRoot)` wipes the DBThread** (§40.50) — the root has no `types.dat`; load a
  container of maps through the builder's map-lookup scan.
- **The `:osmscout-client-java` Gradle JAR excludes `OSMScoutClient.java`/`Builder`** (§40.51): never feed
  it to JavaScout Maven (a stale `~/.m2` JAR breaks the signature) — build the JAR from the submodule's
  `java/` sources.
- **JavaScout Maven tests need `JAVA_HOME=java-21`** (§40.52): JUnit 5.10.2 on Java 26 discovers but
  executes 0 tests.
- **Stale meson host builds: `sed` the ninja link line to a stub path before rebuilding** (§40.53) —
  `/usr/lib` is not writable and there is no sudo.
- **Native index fixtures must be FULL (non-eco) imports** (§40.34): `--eco true` skips the POI indexes, so
  an index test silently asserts nothing.
- **`DBThread` loads databases sequentially** (§40.35): wait for two consecutive identical results before
  asserting.
- **Verify `md5` after any `git stash` cycle before rebuilding** (§40.36): a stash+pop silently reverted a
  submodule patch and the "patched" host library was unpatched.

## 5. Release versioning

- `./gradlew release` generates `versionName` as `<yyyy>-<MM>-<dd>-<N>`
  (4-digit year, zero-padded month/day, running number `N` without leading
  zeros), increments `versionCode` by one, then runs
  `:app:bundleMobileRelease` and `:app:bundleAutomotiveRelease`.
- Version state lives in `app/release-version.properties` (**gitignored**,
  machine-local — keep it on the release machine): `lastDate`,
  `runningNumber`, `versionCode`, `lastVersionName`. Same day → `N+1`; new day
  → `N` resets to 1; `versionCode` starts at 20. `release` **fails fast** if the
  file is missing or its `lastDate` is in the future (a silent reset would emit
  a duplicate `versionName` or a `versionCode` at/below the published one, which
  Play rejects). **Play's dedup key is `versionCode`, not `versionName`** — a
  date-`-N` name can repeat across days, but every uploaded AAB needs a
  versionCode strictly greater than every published one (incl. AABs built from
  stale state). If the state lags reality (release published elsewhere), set
  the values to the last published ones before running `release`.
- The bump happens at configuration time, gated on the `release` task being
  requested — every other build uses the fixed fallback `1.0.0`/`19` and never
  touches the state file. Direct `bundleRelease` without `release` reuses the
  last persisted values; only `release` bumps (single release machine assumed).
- Signing: `app/release.keystore` present → signed AAB; absent → warning
  logged, unsigned AAB still produced.
- Outputs: `app/build/outputs/bundle/mobileRelease/app-mobile-release.aab`
  (phone + Android Auto) and
  `app/build/outputs/bundle/automotiveRelease/app-automotive-release.aab`
  (AAOS) — upload each to its own Play track.
- Each release variant also emits a CycloneDX SBOM next to its AAB; see
  §8 SBOM generation.

## 6. Test constraints

- **JNI stub / classloader rule**: `app/src/test/jniLibs/` holds a host stub so
  `OSMScoutClient`'s `System.loadLibrary` succeeds in JVM tests. Any test class
  that instantiates `FakeOSMScoutClient` (or otherwise triggers that load) MUST
  run under `@RunWith(RobolectricTestRunner::class)` with the DEFAULT sandbox
  config — do NOT set `@Config(sdk=...)` or `@GraphicsMode(...)`. Violations
  cause "already loaded in another classloader" failures in full-suite runs.
- **Teardown rule — release what the test starts.** Any test that constructs a
  component owning long-lived background work (its own coroutine scope, timers, a
  render loop, a native surface, a large retained bitmap) MUST release it before
  the test returns. `AutoMapRenderer` is the case that made this a rule: it starts
  its render, extrapolation and zoom-walk loops in `init`, and only `shutdown()`
  ends them — every `:auto` renderer test that forgot it leaked a ~30 Hz loop on
  `Dispatchers.Default` plus a 1296×720 overrun bitmap (3.7 MB) per test, which
  made the module suite die with `OutOfMemoryError` in one JVM (change
  `fix-auto-unit-test-heap-overflow`). The pattern to use is
  `auto/src/test/java/com/naviveylin/auto/RendererTestRule.kt`: a JUnit rule that
  hands out the component, shuts every tracked instance down after the test and
  FAILS the test when one still reports active background work (`AutoMapRenderer
  .activeBackgroundJobCount()`; `Job.isActive` is false immediately after
  `cancel()`, so nothing needs waiting for). A leak must fail its own class, not
  starve the suite's heap later.
- **Geometry claims are asserted as Dp bounds, not as existence** (change `fix-nav-overlay-stop-tap`, spec
  `navigation-status-details`; skill `.pi/skills/compose-geometry`, machine-local). A Compose case for a
  control's size, its tap target, its disjointness from a container's tap area, or a node's visibility
  inside its parent SHALL assert bounds: `assertWidthIsAtLeast(48.dp)` / `assertHeightIsAtLeast(48.dp)`
  for size, bare Dp comparisons for disjointness (`region.right <= control.left`) and for visibility.
  `SemanticsNodeInteraction.getBoundsInRoot()` returns a **`DpRect`** here, so `bounds.width` and
  `bounds.right - bounds.left` do not compile (`Unresolved reference 'width'` / `actual type is 'Float',
  but 'Dp' was expected`) — never convert to pixels by hand. Two findings were invisible to a suite that
  asserted existence: a node drawn below the screen edge passes `assertExists()` and `assertIsDisplayed()`
  (`TODO.md` §138), and a container's `clickable` covering a control does **not** stop Compose delivering
  the tap to the innermost target (measured 2026-10-05), so a click case alone cannot falsify an overlap
  guard — assert the geometry and falsify it with the `revert-check` skill (move the tap area / shrink the
  box / neuter the handler).
- **Declared unit-test fork budgets.** Two modules need more than the AGP default
  fork heap (512 MB) to hold their whole suite in one JVM; both declare it in
  `testOptions { unitTests { all { it.maxHeapSize = … } } }` so a fresh checkout and
  CI get the same budget (spec `unit-test-suite-runtime`, change
  `fix-auto-unit-test-heap-overflow`):

  | Module | Suite | Declared | Measured |
  |---|---|---|---|
  | `:auto` | 49 classes / 516 tests | `1024m` | 512 MB → FAILED (141 `OutOfMemoryError` lines, 0 result XMLs, 9m08s); 1024 MB → green (one fork, 20s) |
  | `:app` | >165 classes per flavor | `1024m` | 512 MB → deterministic `FavoritesSheetReorderComposeTest` failure (`ComposeTimeoutException` after 5000 ms); 1024 MB → green (1m49s, 146 classes / 1054 tests, 2026-09-20); 2048 MB → green (2m06s 2026-09-20); 1024 MB again from 2026-09-26 with the canary bound at 30 s → green twice in a row (3m57s / 3m20s, 1245 tests per flavor, 0 failures) **after the canary's real cause was fixed** |

  **The 2026-09-26 `:app` canary was not a budget problem — it was a race in the test.**
  `FavoritesSheetReorderComposeTest` awaited a state change the repository produces on
  `Dispatchers.Default` (`FavoriteRepository.defaultDispatcher`): in a one-fork suite that pool
  competes with every other class's work, so the awaited change was late by an amount the bound
  measured as luck — the class always passed alone, failed more often as the suite grew, and was
  insensitive to heap (1024 MB and 2048 MB both failed) and to batching (`forkEvery` 10/20/40 was
  measured during the diagnosis: green once at 10, red at 10 and 20 on the next runs). The test now
  pins the repository dispatcher (`Dispatchers.Unconfined`) for its own fixtures, and the suite is
  green repeatedly on ONE 1024 MB fork. A bound raise is therefore still not the answer — fix the
  awaited dependency instead.

  Both values are the measured minimum plus headroom. A full `./gradlew test`
  holds up to two `:app` forks, one `:auto` fork and one `:core` fork at once; at
  the current `:app` budget that peak needs roughly 4.5-5 GB of RAM. If a machine
  cannot afford the ceiling, `forkEvery` (e.g. 24 for `:auto`, 40 for `:app`)
  bounds per-fork accumulation at the cost of a JVM start per batch — prefer that
  over raising the ceiling. Note that `forkEvery` is **not** part of the test
  task's build-cache key: after changing it, run with `--no-build-cache`, or the
  previous configuration's results are restored and the setting looks proven when
  it never ran. Verify a budget by CONTENT, not by the build result:
  the fork args (`-Xmx…`, `--info`) and the per-class result XMLs (§4, §17).
- **Declared test-JVM concurrency** (change `speed-up-build-test-gate`, spec `unit-test-suite-runtime` —
  "Unit-test parallelism is declared and result-preserving"): the fork budget above decides *how much
  heap* a fork gets, this one decides *how many run at once*, and both are declared in the same block
  (`it.maxParallelForks`). Measured 2026-10-04, one invocation per setting with
  `-PforceTests -PnoCoverage --no-build-cache`, no other Gradle build on the machine during the runs
  (checked by sampling the wrapper and daemon counts), class set printed with each run:

  | suite | 1 fork | 2 forks | 4 forks | declared |
  |---|---|---|---|---|
  | `:app:testMobileDebugUnitTest` (218 classes / 1649 tests) | 2m50s · 1.92 GB | 2m02s · 3.42 GB | 1m51s · 4.65 GB · **1 failure** | `2` |
  | `:app:testAutomotiveDebugUnitTest` (same 218 classes / 1649 tests) | 2m35s · 1.94 GB | 2m02s · 3.14 GB | 1m46s · 4.42 GB | `2` |
  | `:auto:testDebugUnitTest` (76 classes / 779 tests) | 38.3s · 1.21 GB | 40.0s · 1.70 GB | 48.9s · 2.62 GB | `1` |

  Durations are the test task's own execution from `build/gate-timings.json`; memory is the peak sum of
  the suite's worker-JVM RSS, sampled every 2 s. The class-name sets were identical at every setting.
  **Four forks is deliberately not declared: it is not result-preserving.** At 4 forks mobile failed
  `NavigationEngineRerouteTest.instructionListUpdatesAfterAReroute` (awaiting a state change inside its
  bound) while the same class was green at 1 and 2 forks in the same session — four workers held 4.65 GB on
  top of the daemon. `:auto` is the opposite case: short enough that per-JVM start-up outweighs the split,
  so more forks only cost wall time (38.3s → 40.0s → 48.9s) and memory. Re-derive both numbers after a
  meaningful test-suite change (the suites grew from 215/1635 to 218/1649 classes/tests on 2026-10-04).
- **Wall-clock waits are refused, and the conversion is measured** (change `speed-up-test-iteration`, spec
  `unit-test-suite-runtime` — Wall-clock waits in test sources are refused by a build check). Each
  test-bearing module wires `checkNoWallClockWaits` (the pure `buildSrc` object
  `com.naviveylin.build.testing.WallClockWaitScanner`) into its `preBuild`, and the check has **no
  allowlist**: a `Thread.sleep`, a `TimeUnit.X.sleep`, a `while (… System.currentTimeMillis() …)` deadline
  loop or a clock-derived `deadline` binding fails the build naming file and line. A test that must wait
  instead drives the component's seam — the ViewModel's `EngineTimeSource` / `rendererDispatcher`,
  `EngineDispatchers` for the navigation engine, `DiagnosticsLog.awaitDrained()`, the auto renderer's
  `zoomWalkLoopTurns`, copy-on-write collections in a fake — and awaits observable state. Measured
  2026-10-06 on the forced gate (`test :app:assembleMobileDebug :app:assembleAutomotiveDebug
  -PforceTests --no-build-cache -I tools/gate-timings.init.gradle.kts` → `BUILD SUCCESSFUL in 4m 54s`,
  123 executed tasks, 0 failed tasks):

  | suite | classes / tests | class time | task duration in that gate |
  |---|---|---|---|
  | `:app` mobile | 221 / 1719, 0 failures | 129 s | 75 s |
  | `:app` automotive | 221 / 1719, 0 failures | 138 s | 79 s |
  | `:auto` | 76 / 781, 0 failures | 64 s | 70 s |
  | `:core` | 41 / 447, 0 failures | 14 s | 18 s |
  | JNI | 2 / 26, 0 failures | 1 s | 1.7 s |

  Against the 2026-10-05 gate record (§2: `:app` mobile 4m54s, automotive 3m56s, `:auto` 1m39s, `:core`
  32.8 s — 12m00s of suite work in a 9m46s wall) the same suites now take **4m02s of suite work in a
  4m54s wall**. The automotive suite alone went **252 s → 138 s of class time** with the same 221 classes;
  the classes that owed their time to a wait lost most of it: `MapCanvasViewModelBrowseReCenterTest`
  16.485 s → 0.359 s, `MapCanvasViewModelViewportRestoreTest` 16.083 s → 7.22 s,
  `MapCanvasViewModelAutoZoomCommitTest` 18.337 s → 9.42 s, `MapCanvasViewModelSingleFollowCenterTest`
  13.221 s → 9.29 s, `MapCanvasViewModelFollowModeTest` 7.044 s → 4.812 s. Not every class improved and
  the honest number needs the right control: cases that await a **real** route calculation still cost
  5-9 s on a loaded machine (`TODO.md` §143) and one viewport-restore case costs 6.2 s with no wait left
  in it (§142) — both pre-existing. An earlier reading of this conversion looked like a regression until
  the *unconverted* class was measured in the same window (18.337 s against the converted 9.42 s), which
  is why this bullet quotes same-session baselines and not the older, quieter machine's numbers.
- **One invocation per suite.** `:auto` and `:app` each complete in a single
  Gradle invocation at the declared budget; splitting a suite into class batches is
  a diagnostic fallback (e.g. to isolate one class), never the procedure, and a
  batched run is not evidence for the suite as it really runs. A Gradle
  build-cache hit (`FROM-CACHE`, `BUILD SUCCESSFUL in 2s` with no test executor)
  is not test evidence either — use `--rerun` when the run itself is the evidence.
- **A timeout-shaped failure tests the resource hypothesis first.** Before hunting state pollution, check
  the fork heap/cadence budget (the table above): a `ComposeTimeoutException`/awaited-state failure that
  went green only after raising the fork heap from the AGP default 512 MB to 1024 MB was the resource
  dimension, not a state bug — one measurement run is cheaper and was correct here. And "pre-existing" is
  proven by a control run (`git stash push -u` → the same failing command → `git stash pop`), never by
  reasoning about the dependency graph.
- Instrumented tests need a connected device/emulator; if none is available,
  say so instead of running them.
- **After a submodule bump, `./gradlew test` is not one invocation.** It carries the native CMake
  configure/build for **both** flavors × three ABIs (measured 2026-09-27: past a 45-minute tool window,
  mobile suite done and automotive just started), and the daemon that ran it then failed
  `:app:mergeExtDexAutomotiveDebug` with `D8: java.lang.OutOfMemoryError: Java heap space`. Prefer the
  per-module form for the gate (`./gradlew :app:testAutomotiveDebugUnitTest :auto:testDebugUnitTest
  :core:testDebugUnitTest`, measured 1 m 58 s with the native build warm), quote per-module counts from
  `test-results/*.xml`, and `./gradlew --stop` before a flavor assemble when a long suite ran first.
- **The submodule's meson suite has a per-test budget that one test exceeds:**
  `libosmscout:Check threaded database` (`Tests/ThreadedDatabaseTest --threads 100 --iterations 1000`)
  is killed at 60 s (`30 s` default × the repo recipe's `--timeout-multiplier 2`) and passes in **120 s**
  when run alone with `--timeout-multiplier 60`. Quote that timeout in any "suite is green" verdict, and
  re-run a lone timeout with a bigger multiplier before calling it a failure.

## 7. Code coverage

Coverage is **report-only** — no threshold gate. `./gradlew test` never fails
on coverage percentages; measurement is an explicit, separate step.

**Engines** — JVM unit tests only (Robolectric runs on the host JVM and is
covered; no instrumented/device coverage):

| Module | Engine | Report task | Output |
|---|---|---|---|
| `app`, `auto`, `core` | Kover 0.9.8 | `./gradlew :koverHtmlReport :koverXmlReport` (root merge) | `build/reports/kover/` (root merged), `app|auto|core/build/reports/kover/` (per module) |
| `osmscout-client-java` | Gradle JaCoCo | `./gradlew :osmscout-client-java:jacocoTestReport` | `osmscout-client-java/build/reports/jacoco/test/` |

Rationale for two engines: Kover measures Kotlin sources accurately (lambdas,
inline functions) and cannot measure a pure-Java module without the Kotlin
plugin (empty reports); the Java-only JNI bridge module therefore uses the
standard JaCoCo plugin and is reported separately, outside the Kover merge.

**Usage**

```bash
# One command: all coverage (root Kover merge + JaCoCo report)
./gradlew :koverHtmlReport :koverXmlReport :osmscout-client-java:jacocoTestReport

# Per-module Kover report (HTML + XML)
./gradlew :app:koverHtmlReport :app:koverXmlReport
```

Running a Kover report task automatically runs the unit tests of the merged
modules under instrumentation (full suite ≈ a few minutes).

**The agent is attached by default; `-PnoCoverage` detaches it** (change
`speed-up-build-test-gate`, spec `test-coverage`). The default keeps every existing report path working
without an extra flag, and an iteration run that does not need coverage data can stop paying for
the instrumentation:

```bash
# Iteration run: forced tests, no coverage data
./gradlew :app:testMobileDebugUnitTest -PforceTests -PnoCoverage --no-build-cache

# Report, from a run that requested coverage
./gradlew :koverXmlReport :osmscout-client-java:jacocoTestReport
```

Measured 2026-10-04, one invocation per suite on the same tree, suite wall time from the first and last
result-XML `timestamp`:

| suite | agent attached | agent detached (`-PnoCoverage`) | classes | tests |
|---|---|---|---|---|
| `:app:testMobileDebugUnitTest` | 2m58s | 2m40s | 215 both | 1635 both |
| `:app:testAutomotiveDebugUnitTest` | 2m57s | 2m36s | 215 both | 1635 both |

The class-name sets and the tallies (1635 tests, 0 failures, 0 errors) are identical with and without the
agent, so the opt-out buys time and changes nothing about the result — the agent costs 10-12 % of a suite.

**A `-PnoCoverage` run deletes that task's execution data.** Kover removes data it did not produce, so a
stale file can never be mistaken for this run's coverage; consequently the invocation that feeds a report
must not pass the opt-out, or the tests have to run again. `> Task :core:testDebugUnitTest UP-TO-DATE`
while `:core:koverXmlReportDebug` runs is the check that the report came from real data.

**Generated-code exclusions** — Kover report filters (per module AND at the
root merge) exclude `BuildConfig`, `R`/`R$*`, and Hilt/Dagger/KSP-generated
classes (`dagger.hilt.*`, `hilt_aggregated_deps.*`, `*.Hilt_*`, `*_Hilt*`,
`*.Dagger*Component*`) so numbers reflect hand-written logic. Hand-written
`*Factory` classes are intentionally NOT excluded.

**Baseline (2026-09-11)** — merged report: 662 classes, 59.7 % line
(8839/14805), 58.2 % instruction; per module: `app` 58.9 %, `auto` 53.8 %,
`core` 67.3 % (line). `osmscout-client-java`: LINE 5 covered / 1 missed.
Re-measure after meaningful test work; a future change may add a
`koverVerify` threshold gate on top of this baseline. Re-checked 2026-10-04 after the opt-out
(`:core` `com/naviveylin/core/AccuracyClass` LINE 0 missed / 3 covered), i.e. the default path still
yields non-empty counters. The JNI module's own suite is unchanged by the opt-out: 2 classes /
26 tests / 0 failures.

**Configuration cache: deliberately off (evaluated 2026-10-04/05).** `./gradlew --configuration-cache
:app:testMobileDebugUnitTest …` does not store an entry: Gradle reports **8 problems**, all of them
script-level `DefaultTask` registrations whose `doLast` closures capture script objects —
`:app:checkHardcodedStrings`, `:app:checkNoCoordinatesInLogs`, `:app:generateSbomMobileDebug` (twice: a
script object and a `DefaultProject`), `:app:mergeNativeSbom`, `:app:downloadSbomCli`, and
`:auto:`/`:core:checkHardcodedStrings`. Clearing them means moving those tasks into `buildSrc` classes with
serializable inputs — a change of its own, not a build-flag flip. Until then every invocation pays
configuration time; `TODO.md` §813 carries the same conclusion, now measured. The release-state contract
holds meanwhile: `:app:checkLicensePolicy` on 2026-10-05 left `app/release-version.properties`
byte-identical (same md5), as `release-target` requires.

**CI** — `.github/workflows/build.yml` generates the reports after "Run unit tests" and uploads them as the
`coverage-reports` artifact (`if-no-files-found: error`). The test step runs the documented full gate
(`test -PforceTests --no-build-cache`, §3) and a following step prints the per-module, per-flavor tallies
from the result XML, so the log names which suites executed rather than only the verdict.

**Known upstream issue** — Kover 0.9.8 emits a Gradle deprecation warning on
Gradle 9.6 (Project-object dependency notation from its own internals); the
project's own scripts use string notation. This becomes a hard error in
Gradle 10 — revisit when upgrading either dependency (tracked in TODO.md).

## 8. SBOM generation (CycloneDX)

Every build variant can produce a CycloneDX JSON SBOM covering the JVM
Gradle dependency graph, the native vcpkg dependency tree, and the
libosmscout submodule. Format: CycloneDX 1.7 JSON.

**Tasks** (group `sbom`, defined in `app/build.gradle.kts`):

| Task | Produces |
|---|---|
| `:app:generateSbomMobileDebug` / `...MobileRelease` / `...AutomotiveRelease` | full merged SBOM, `app/build/outputs/sbom/<variant>/bom.json` |
| `:app:generateSbomJvm<Variant>` | JVM section, `.../jvm-bom.json` (plugin direct task, `includeConfigs=<variant>RuntimeClasspath`, tests excluded) |
| `:app:mergeNativeSbom` | native section, `app/build/outputs/sbom/native/native-bom.json` (shared by all variants) |
| `:app:downloadSbomCli` | cached `cyclonedx-cli` binary under `app/build/cyclonedx-cli/` (pinned `0.33.1`, one-time download) |

**Wiring**: `./gradlew release` runs both release-variant SBOM tasks, so each
AAB has a sibling `bom.json` carrying the release `versionName`. CI
(`.github/workflows/build.yml`) generates the `mobileDebug` SBOM after the APK
build and uploads it as the `naviveylin-sbom` artifact. SBOM tasks only read
the release version state — they never bump it (versioning behavior of §5 is
untouched).

**Composition**: JVM section comes from the `org.cyclonedx.bom` 3.4.1 plugin
(direct task per variant, `schemaVersion` 1.7). Native section: vcpkg ships a
per-package SPDX SBOM (`vcpkg/installed/<triplet>/share/<port>/vcpkg.spdx.json`);
the port list is read from `vcpkg/installed/vcpkg/status` (Architecture
filtered; vcpkg-* tool ports and the intentionally-not-installed
`libosmscout` port are excluded). Each SPDX file is converted to CDX with
`cyclonedx convert`, template-URL external references (vcpkg source-origin
heuristics like `${VERSION_MAJOR_MINOR}` — invalid URIs) are stripped, and
only real `pkg:vcpkg/` components are kept, deduplicated by
group+name+version across the three triplets (arm64 / arm-neon / x64 share the
same software). The final merge combines JVM + native via the core-java model
and records the submodule SHA (`git rev-parse HEAD` of
`app/src/main/cpp/libosmscout`) as a `libosmscout` component. The root
component (`metadata.component`) is the application itself, stamped at the
build version **with the application license** (`GPL-3.0-or-later`, the same
`FIRST_PARTY_LICENSE_REF` constant the first-party components resolve
through). Every output passes `cyclonedx validate` inside the task.

**Known cyclonedx-cli quirks** (handled in the Gradle code — do not
re-introduce): `--input-files` must be repeated per file (a single
space-joined argument crashes with a .NET `PathTooLongException`), and
`merge` concatenates without deduplicating components.

**Troubleshooting**: an SBOM task fails with `Missing SBOM data for vcpkg
package ...` when an installed package predates vcpkg SBOM support (binary
cache content built by an older vcpkg or restored from cache without the
SPDX file). Rebuild the named package, e.g.:

```bash
VCPKG_BINARY_SOURCES=clear ./vcpkg/vcpkg install gettext:arm64-android --recurse \
  --overlay-ports=vcpkg-overlays --overlay-triplets=vcpkg-overlays/triplets
```

(with `ANDROID_SDK_ROOT`/`ANDROID_NDK_HOME` set as in `setup-vcpkg.sh`) and
re-run; the task names the exact missing file.

## 9. License compliance

The SBOM also carries license data, and that data is what the app shows and
what the gate enforces. Three pieces fit together:

| Piece | Where | Purpose |
|---|---|---|
| Curated data | `licenses/native-license-map.json`, `licenses/license-policy.json`, `licenses/texts/` | what each native component declares, what the build permits, where each license text comes from |
| Evaluation logic | `buildSrc/src/main/kotlin/com/naviveylin/build/licensing/` | pure Kotlin: expression/election resolution, policy gate, scope classification, asset generation — unit-tested by the build itself |
| Generated output | `licenses/dependencies.json` + `licenses/texts/*` in each APK's assets; `NOTICE` next to each SBOM | what users read, tied to the artifact that was built |

**SBOM license data.** Each component of a variant SBOM carries at least one
license identifier (`license.id`, or a `licenses[].expression` for a
`LicenseRef-`), plus properties: `naviveylin:license:scope`
(`shipped`/`buildTimeOnly`), `naviveylin:license:scope-evidence` (why),
`naviveylin:license:scope-ambiguity` (shipped without symbol evidence),
`naviveylin:license:notice`, and `naviveylin:license:caveat` (an unresolved
doubt, reported rather than hidden).

**Scope is derived from the built artifact** — the variant's stripped native
libraries under `intermediates/stripped_native_libs/<variant>/`, filtered to
the ABIs this build produces. A component is *shipped* when a packaged object
carries its name, or a probe symbol from `probeSymbols` appears in a packaged
object, or one of its static archives reaches a native `target_link_libraries`
call (recorded as ambiguous when no symbol of the component itself was found —
`expat` is the current example). Find-module cache defaults such as
`LIBXML2_LIBRARY` are **not** link inputs; counting them once made protobuf and
libxml2 look shipped. Everything else is `buildTimeOnly`. The curated `scope` in
the map is a cross-check: a disagreement fails the build rather than passing
silently. The SBOM task therefore depends on the variant's native libraries.

**The gate.** `checkLicensePolicy<Variant>` reads the generated SBOM and
`licenses/license-policy.json` and fails when a component's license is missing,
unresolved, not permitted for its scope, or a choice without a recorded
election. `checkLicensePolicy` covers the CI variant; the `release` target gates
both release flavors. It is deliberately **not** wired into `assemble`: a policy
failure must not block unrelated local work, but it must fail the gate that runs
it. Warnings surface every recorded caveat (unresolved evidence or a claim
needing re-confirmation — e.g. libosmscout's version-less LGPL mapping on
submodule bumps) and any license still awaiting the application's own license
decision (`reviewRequired`; currently empty — the GPL-3.0-or-later decision
covers the verified compatible shipped set).

> The gate enforces the policy **declared** in this repository. It is not a
> legal assessment, and passing it does not mean the project's obligations are
> satisfied. The application's own code is licensed under **GPL-3.0-or-later**
> (`LICENSE` declares `SPDX-License-Identifier: GPL-3.0-or-later`; README §License
> repeats it). Because GPL §5 requires a copy of the license with the program,
> the full GPLv3 text is embedded in the inventory (`GPL-3.0-or-later.txt`).
> Older *released* APKs built before this decision carry the previous TBD
> inventory data — immutable, and not re-generated retroactively.

**Policy file** (`licenses/license-policy.json`):

- `permitted.shipped` / `permitted.buildTimeOnly` — two separate lists on
  purpose: `GPL-3.0-only` (the `gettext` build tool) is permitted as build-time
  tooling, and the same license on a *shipped* component fails.
- `elections` — for a component whose license offers a choice, the alternative
  relied upon, and it must be one the declaration actually offers. Currently
  cairo `MPL-1.1`, freetype `FTL`, marisa-trie `BSD-2-Clause`, glib
  `LGPL-2.1-or-later AND LGPL-2.1-only`.
- `licenseRefs` — SPDX's mechanism for licenses outside the SPDX list.
  `LicenseRef-AndroidSDK` (Play Services, terms at a URL, text not distributed)
  is the only remaining entry: first-party code used to resolve through
  `LicenseRef-NaviVeylin`, but since the application license decision it
  resolves to the plain SPDX id `GPL-3.0-or-later` (in `permitted.shipped`, with
  embedded text).
- `textSources` — where each identifier's text comes from, declared rather than
  inferred: `file` (canonical text under `licenses/texts/`), `vcpkgPort` (a
  port's `copyright`), `androidNdk` (the NDK's `NOTICE.toolchain`), or `none`.
  An identifier used by a distributed component without a source fails the
  build. Two examples of why inference was abandoned: the `Apache-2.0` text
  came out as the entire NDK bundle, and `LGPL-2.1-or-later` came out as
  marisa-trie's one-line statement instead of the license text.
- `noticeRequired` — identifiers whose notice must travel with redistribution.
  Only *distributed* components are asked for one. `GPL-3.0-or-later` is listed
  so the first-party license text is embedded like any other distributed
  license.
- `reviewRequired` — identifiers awaiting the project owner's license decision;
  the gate reports them and never treats presence in the file as clearance.
  Currently **empty**: the GPL-3.0-or-later application license decision covers
  the third-party set verified GPLv3-compatible. Entries declare a `components`
  scope (empty = every carrier); the decoder honours it, so a scoped entry warns
  only the named components. The residual evidence duty for libosmscout (no
  version clause → `LGPL-2.1-or-later` conservative mapping) lives in
  `native-license-map.json`'s caveat (submodule LICENSE since 2026-03, commit
  f4a9dabe7: LGPL plus app-store exceptions, **no GPL text**) and is reported by
  the gate as a caveat on every build.

**Generated assets.** `generateLicenseAssets<Variant>` (a `buildSrc` task,
`GenerateLicenseAssets`) writes `licenses/dependencies.json` and
`licenses/texts/*` into `build/generated/assets-licenses/<variant>/`, wired as
that variant's assets source through the Variant API
(`variant.sources.assets.addGeneratedSourceDirectory`), plus `NOTICE` next to the
variant's SBOM. Two constraints learned the hard way: AGP rejects Provider
instances in the SourceSet API, and a single shared generated root leaks one
variant's inventory into another's APK (the automotive merged assets had picked
up `licenses/mobile/debug/`). The app reads `licenses/dependencies.json` from its
own assets — no shared directory, no runtime lookup by flavor.

**buildSrc.** `./gradlew -p buildSrc test` runs the license suite (52 tests); the
`build` task Gradle runs on every invocation includes them, and unchanged inputs
are up-to-date. Java 17 toolchain is pinned there so Kotlin and Java targets
agree (a newer daemon JVM otherwise emits an inconsistent-target warning).

**CI.** `Check license policy` runs after the SBOM step, and
`Upload license inventory` publishes the generated inventory
(`app/build/generated/assets-licenses/mobileDebug/licenses/**`) so a reviewer can
read it without unpacking an APK.


## 10. On-device evidence for Android Auto / AAOS (host-crash triage)

The car host (templates host / car UI) is a **different process** from the app, so a host
failure is not visible in the app's own crash log. This is the recipe that produced the
evidence in change `fix-aaos-host-crash` (tasks 1.6 and 7.3-7.5); run it before, during
and after the scenario you want to judge.

**Boot the AAOS AVD and install the automotive build.** The AVD's ABI is x86_64, so an
arm64-only APK will not install:

```bash
/home/tim/Android/Sdk/emulator/emulator -avd Automotive_Distant_Display_with_Google_Play \
    -no-snapshot-load -no-boot-anim &
./gradlew :app:assembleAutomotiveDebug -Pandroid.injected.build.abi=x86_64
adb -s emulator-5556 install -r -t app/build/intermediates/apk/automotive/debug/app-automotive-debug.apk
adb -s emulator-5556 shell am start -n com.framstag.naviveylin/androidx.car.app.activity.CarAppActivity
```

The car UI itself is not reachable through `uiautomator`; drive the session through intents
instead (a deep link starts navigation, `KEYCODE_HOME` backgrounds the app):

```bash
adb -s emulator-5556 shell am start -n com.framstag.naviveylin/com.naviveylin.DeepLinkActivity \
    -a android.intent.action.VIEW -d "geo:51.5142,7.4653"
adb -s emulator-5556 shell input keyevent KEYCODE_HOME       # background the car app
```

Start the bare URI form without `-n` only if you want the system chooser; with several
`geo:` handlers it opens the resolver dialog instead of the app.

**Judge the car surface and the render loop.** `lockCanvas` succeeds only for a live
surface, and the renderer logs it, so counting those lines is the cheapest "is the map
drawing" test:

```bash
adb -s emulator-5556 logcat -c
# ... run the scenario ...
L=$(adb -s emulator-5556 logcat -d | grep -E 'AutoMapRenderer|CarSurfaceHost')
echo "lock OK:   $(echo "$L" | grep -c 'lock OK')"          # frames actually drawn
echo "created:   $(echo "$L" | grep -c 'surface created')"  # surface adoptions
echo "releases:  $(echo "$L" | grep -c 'releasing session surface')"
echo "failures:  $(echo "$L" | grep -c 'surface invalid\|lockCanvas failed')"
echo "drops:     $(echo "$L" | grep -c 'dropping frame')"   # stale frames dropped
echo "detached:  $(echo "$L" | grep -c 'detaching surface')" # screen stop released its surface + buffer
echo "re-deliver: $(echo "$L" | grep -c 're-delivered')"     # a released instance came back
```

Expected after a push/pop or a background round trip: a `surface created` for the incoming
screen **with the same surface id** the outgoing one had, **no** `releasing session surface`
in between, and `lock OK` continuing. A `detaching surface` for the outgoing screen should
precede the incoming `surface created` (the session revokes the superseded owner at `attach`)
and appear once per screen stop — a stop **without** it means the screen's `onStop` did not
detach, so that renderer still holds the surface and its overrun buffer. Any `surface
invalid`/`lockCanvas failed` (or an
`invalidate … attempt` line from a screen's surface-refresh recovery) is a defect, not noise.

`re-delivered` means the host handed back a `Surface` instance this session had already
released (change `fix-car-surface-ownership-and-host-callbacks` tracks lifetimes per delivery,
so the instance is adopted and released again). It is expected only when the host really
re-delivers; a run that shows it repeatedly is worth keeping, because it is the case the
per-instance bookkeeping used to leak the buffer-queue producer on.

**Host-side failure.** The AVD's logcat covers `system_server`, the templates host and
`systemui`, so the host's stack trace is retrievable even though the app has none:

```bash
adb -s emulator-5556 logcat -b crash -d
adb -s emulator-5556 shell dumpsys dropbox --print | grep -B5 -A40 -iE 'templates.host|system_server|systemui'
adb -s emulator-5556 logcat -d | grep -iE 'lmkd|lowmemorykiller|Killing'   # host killed by pressure?
adb -s emulator-5556 shell dumpsys meminfo | head -40
adb -s emulator-5556 shell dumpsys cpuinfo
```

**Measuring the app's own footprint — the rule.** Every footprint counter this repo reads
(`Graphics`, `EGL mtrack`, `GL mtrack`, native heap, `Bitmap (malloced)`, `TOTAL PSS`) is a **high-water
mark inside a process**: measured 2026-09-27, `GL mtrack` went 80.6 MB → 130.6 MB across three zoom
gestures and was still 130.6 MB 25 s later, and the native heap went 115 MB → 337 MB across one scripted
map walk (retaining 324 MB after returning to the start viewport). So each "before" and each "after"
needs its **own freshly started process**, and two numbers are only comparable at the same gesture/walk
script and the same render count. **Quote the UI state with every number** — whether the phone canvas was
visible (map or car-session surface), whether a car session was live, and whether the sample was taken idle or
mid-gesture — because the same device state measured a `Graphics` 31.8 MB for the car-session surface and
100.2 MB for the map canvas in one process minutes apart, and a "before" number whose UI state is undocumented
cannot be compared with an "after" one (that is what left `reduce-render-peak-memory` task 5.3 without a valid
comparison against its own recorded baseline). A walk script that needs no car and no driving:
`adb shell input keyevent 69` (zoom out) ×10, then `adb shell input swipe` pan pairs — the app must be in
the foreground (`topResumedActivity`, not `mCurrentFocus`).

**App-side host sends.** The app records what it sent the host with the diagnostics tag
`HOST` (notification posts, trip updates, host navigation-state calls, surface
adopt/release) and the native client build with the thread that ran it (tag `WARMUP`). The
logcat route works on every device and is the primary one:

```bash
adb -s emulator-5556 logcat -d | grep -E 'Diag/HOST|Diag/WARMUP'
```

The phone's route analysis records its per-step values under `ROUTE` — what the step rows add up to
against the route's own total distance, numbers only (spec: `route-analysis` — Step values describe the
step's own leg). It is the number that tells a leg from a geometry edge, so read it next to the row text:

```bash
adb logcat -d | grep 'Diag/ROUTE route analysis'
```

The instrumented route check logs the same relationship with the per-step extremes, under its own tag:

```bash
adb logcat -d | grep 'RouteDeviceTest: per-step values'
```

**Rejected host mutations and confined faults.** Every screen-stack mutation (push, pop,
popToRoot, remove) runs through the guarded seam, and every car-facing scope carries a fault
handler (change `fix-car-host-mutation-guards`), so a mutation the host refused and a fault
inside a session/screen/renderer coroutine are both recorded instead of killing the process:

```bash
G=$(adb -s emulator-5556 logcat -d | grep -E 'Diag/(HOST|SESSION|SCREEN)')
echo "rejected:  $(echo "$G" | grep -c 'rejected')"     # expected 0 in a healthy run
echo "confined:  $(echo "$G" | grep -c 'failed: ')"     # expected 0 in a healthy run
```

A rejection names the mutation (`push FavoritesScreen rejected: …`), so the screen the driver
tapped is identifiable; a confinement names the coroutine (`session work 'trip' failed`,
`screen work '…' failed`, `observation 'gps' failed`) plus its first stack frame. Both are
**defects to chase**, not noise: either the driver saw no navigation, or a screen stopped
updating. Correlate them with the app crash buffer — a guarded rejection must never be
followed by `Diag/CRASH`.

**The error-notice path.** An error raised while navigating must leave the navigation view on
the stack (it used to be popped with the notice). Navigate, trigger the notice (a route to an
unreachable destination, or the existing error path), let it clear: the navigation template and
its live guidance must still be shown, with `Diag/HOST` naming the notice push and its removal
and no rejection.

Map registration on a startup is readable in the same stream (change
`fix-native-database-open-race`): one `openDatabases -> N/M registered` line per batch call,
then one `openDatabase(<directory>) -> <true|false>` line per directory. A **per-directory**
run of `openDatabases -> 1/1 registered` lines instead of one line with the whole set means a
caller still registers one by one - each of those calls closes and reopens every open database
and stalls the render path:

The same lines are mirrored to the file-backed diagnostics log, readable without a
debugger (verified on the phone AVD). On the automotive AVD `run-as` resolves in **user 0**
while the app process runs in **user 10**, so `files/diagnostics/` looks missing although it is
written under `/data/user/10/com.framstag.naviveylin/files/` (measured 2026-09-21 on
`emulator-5556`; on a userdebug image `adb root && ls /data/user/10/com.framstag.naviveylin/files/diagnostics/`
shows it). Use the logcat route below as the primary one there:

```bash
adb -s emulator-5554 shell run-as com.framstag.naviveylin cat files/diagnostics/app.log | tail -50
```

**Timing of the file route (change `fix-diagnostics-log-host-path-io`).** A line is buffered in
memory and written by the log's worker thread, so it reaches `app.log` within the flush bound
(250 ms) rather than instantly; the logcat line is immediate. For the last moments before a crash,
read logcat, and treat a file tail that ends a fraction of a second earlier as normal. A crash
trace is the exception — the uncaught-exception handler writes it directly, so it is on disk before
the process dies.

Correlate: the last `HOST …` line before the host's death names the sender (a `NOTIF post`
that repeats every second, a `trip update` burst, a surface release at a moment the host
still owned the buffer). The car's own `DiagnosticsScreen` shows the same tail on screen.

**Bisect by disabling one sender per run** (routing active, app backgrounded, wait): skip
the `CarAppExtender`, no-op the trip publishing, gate the session observers. Whichever run
keeps the host up names the sender — or rules the app out and points at the host itself.

**Pitfall — never install over a live session.** Replacing the APK of a car app the host is
bound to force-stops the app, invalidates the host's `CarHost`, and the host's queued
template application then dies with `IllegalStateException: Accessed the car host after it
became invalidated` (`com.google.android.apps.automotive.templates.host:renderer_service`,
observed 2026-09-21 on `emulator-5556`). The crash is host-side and needs no app code to run,
so an install mid-scenario contaminates every measurement in it: install first, then start
the session, and treat a host crash whose log shows `onPackageUpdateFinished`/`replacing=true`
for the app package as a harness artifact, not an app defect.

**Expectation baseline for a healthy run** (stationary vehicle, navigation active, ~75 s,
browse -> navigate -> HOME -> return): a handful of `lock OK` (one per full render), one
`surface adopt` per delivery and one `surface release` per host-driven teardown, **0**
`surface invalid`/`lockCanvas failed`, and - with the change `fix-aaos-host-crash` in -
**one** `Diag/HOST NOTIF post` and **one** `Diag/HOST trip update` for a session whose
displayed content never changed (before it: one of each per second).

**Expectation baseline for a session restart while free driving is active** (with the change
`fix-host-crash-residual-paths` in): exactly **one** `Diag/SESSION Push FreeDrivingScreen
(restore)` per session, and one `BACK`/`Exit` returns to the map root - a second restore push
means two free-driving screens and two native renderers (before the change, the first screen
creation and the warmup completion each pushed one).

**Car search scoped by the driver's region** (change `fix-car-search-default-admin-region`,
spec `auto-search`). The car search is scoped with the admin region containing the car's
position, exactly as the phone search panel is, so a query naming a POI without its city
resolves. The car adapter logs its own scope decision under the `CarSearchRegion` tag
(`android.util.Log`, not the `Diag/` stream), with the handle and the region name and
**never** a position:

```bash
adb -s emulator-5556 logcat -c
adb -s emulator-5556 shell am start -n com.framstag.naviveylin/androidx.car.app.activity.CarAppActivity
# car UI -> search, then type a POI name WITHOUT its city (e.g. "Hilpert Theater")
adb -s emulator-5556 logcat -d | grep -E 'CarSearchRegion'
```

Expected with a usable fix: one `resolveAdminRegion -> handle=<N>` (`N != 0`) followed by
`search scope RESOLVED: handle=<N> region=<name>`, then `search scope REUSED: handle=<N>
region=<name>` for further queries while the car stays within the movement threshold (one
line per query; a second `resolveAdminRegion` line there would mean the threshold is not
holding). With no usable fix (location off, or before the
first fix) the same query logs `search scope FIX_UNUSABLE: handle=0 region=null` and the
search runs unconstrained, exactly as before the change; a coarse fix logs the same. After a
drive beyond ~500 m the next query logs `RESOLVED` with a new handle and the previous one
appears in no released-handle error line.

**Data blocker for this check** (TODO.md §91): the installed map sets on the AVDs do not
carry every POI type the stylesheets declare, so an empty result list for a POI-only query does
**not** by itself mean the scoping failed — read the `CarSearchRegion` line for that, and use a
street/address query when a result is needed. Those missing types appear as **one line per parsed
style file** (change `condense-style-load-warnings`), not as one line per rule occurrence:

```bash
adb logcat -s NaviVeylin | grep "Unknown types in"
# W NaviVeylin: Unknown types in '…/stylesheets/standard.oss': 42 (amenity_theatre, …, 34 more)
```

One line per style file that references a type the installed database's type configuration lacks
— the top-level stylesheet and each `MODULE` include; a file whose names all resolve logs nothing,
and a healthy install logs no such line at all. Count them with
`adb logcat -d | grep -c "Unknown types in"`; thousands of lines here mean the map data predates
the stylesheet, not a defect. **The same file appears once per load**, and a startup loads the
stylesheet set more than once (measured 2026-10-02 on the phone AVD with stale data: 8 files × 13
loads = 104 lines per startup, `Created new style` 12×), so read the count per file and not the raw
total; the repeated loads themselves are `TODO.md` §120. The full per-name list is obtainable
programmatically and, with `osmscout::log.Debug(true)`, as one additional debug line; the
per-reference findings are kept (one per occurrence, with its position), so the condensation loses
no evidence.
Phone parity is checked with the same query and a fix on the phone
(`resolveAdminRegion -> handle=…` in `adb logcat -s MapCanvasVM`): both surfaces must
show the same matches for the same position.

**Two checks that need evidence before any behaviour is specified** (the template-rate one is
recorded in `TODO.md` §64):

- A car-only session that starts the ongoing notification: `adb logcat -d | grep
  ForegroundServiceDidNotStartInTime` - if a *refused* `startForeground` followed by `stopSelf()`
  still trips the platform's foreground-start deadline, the degrade path itself kills the process
  (and that death takes the car host down, per the mechanism above).
- Template rebuild rate: with navigation and lane hints active, count the host-visible template
  activity over a minute (`Diag/HOST` lines, plus the lane-image allocation when logging is
  verbose). The distance values are bucketed to the host's own rounding and the lane image is
  reused; the residual rate follows the arrival estimate's update rate (`TODO.md` §64).

### Start-map selection on the phone (`fix-start-map-selection`)

The phone opens the map resolved by `StartMapResolver`: the last opened database, else the
lexicographically smallest installed one, else the entry point that leads to the map manager
(spec: `start-map-selection`). It never opens the basemap overlay. Two installed regional maps are
enough to exercise it (the map manager's *Download Maps* screen shows what is installed):

```bash
L='com.naviveylin.ui.map.MapCanvasViewModel'
# 1. open map B in the map manager, then restart the app cold
a=$(adb logcat -d -s "$L" | grep -c 'initMap: initialising with path=')  # per-open lines
adb shell am force-stop com.framstag.naviveylin && adb shell am start -n com.framstag.naviveylin/.MainActivity
adb logcat -d -s "$L" | grep 'initMap: initialising with path=' | tail -1   # expect map B
adb logcat -d -s StartMapResolver | grep 'start map resolved'               # chosen + recorded
# 2. delete map B in the map manager, restart again
#    expect: the same remaining database on every restart, and no
adb logcat -d | grep -c 'Could not open map database'                        # must stay 0
# 3. the basemap overlay is never the primary map
adb logcat -d -s NaviVeylin | grep -c "Cannot open db '.*/maps/basemap"      # must stay 0
```

`StartMapResolver` logs the chosen path and the recorded one (identity only, no coordinates), so a
start that picked the wrong database is visible without a debug build. The car side needs no change
(its session registers every installed database except the basemap): a car session started after a
phone start must still report the same database count in the `WARMUP` line.

### Stylesheet load count per start (`dedupe-stylesheet-loads`)

The app applies the stylesheet/flag pair once per (style name, flag set) — see
`guidelines/MapRendering.md` §15 for the key and the legitimate triggers. Both the phone and the car
report every load through the shared native logger, so a start can be counted without a debug build
(the `NaviVeylin` tag carries `osmscout::log` output):

```bash
adb shell am force-stop com.framstag.naviveylin
adb logcat -c
adb shell am start -n com.framstag.naviveylin/.MainActivity
sleep 20
# one native load of the whole set; the basemap's own stylesheet adds one more on its open
adb logcat -d -s NaviVeylin | grep -c 'Created new style with'
# the condensed unresolved-type report: one line per parsed style file per load
adb logcat -d -s NaviVeylin | grep -c "Unknown types in '"
# which style/flag applications happened, in order
adb logcat -d -s NaviVeylin | grep -E 'Created new style with|ensureMapStyle'
```

Expected on the phone: **1-2** `Created new style with` lines per start (the main style plus the
basemap's stylesheet) and **8** report lines (8 files × 1 load) against the 12 loads / 104 lines the
2026-10-02 baseline measured (`TODO.md` §120). Both numbers move with a legitimate trigger: a style
switch, a real day/night change, a map database open (each `initMap`, i.e. every map re-entry, is one),
an app update that refreshed the bundled stylesheets, and a basemap download
(`reloadBasemap`). Count per start and compare per source rather than trusting one number —
and remember `TODO.md` §17: a gradle or logcat verdict must be an execution, not a cache hit.

### Device recipes that cost a round every time (2026-10-05)

- **Device paths and dumps.** The harness refuses absolute *device* paths in a command (`/sdcard/…`,
  `/data/local/tmp/…` — `external_directory_read`): keep paths relative inside `adb shell`
  (`adb shell 'cd /data/local/tmp && uiautomator dump map.xml'`), read the default dump through the device's
  own variable (`adb shell "cat \$EXTERNAL_STORAGE/window_dump.xml"`), or build the path from an octal-escaped
  separator (`S=$(printf '\57'); DUMP="${S}sdcard${S}ui.xml"`). A dump is usable only when it is provably
  **fresh**: require `dumped to` in the output, use a unique literal path per call, retry a few times — a
  *failed* dump (`ERROR: could not get idle state.`) leaves the previous file behind, and every later tap
  then aims at the old screen (this cost two contaminated runs in one session).
- **Tap the node, not its text.** Resolve a node once from one dump and tap *that* node's bounds, class
  filtered (a result row is a `TextView`, the search field an `EditText`) — re-grepping the text can match the
  search field and tap (0,0). Read `clickable="true"` (a node carrying the `content-desc` is not necessarily
  the touch target) and check `mCurrentFocus` before concluding the app ignored input. The top strip
  (y ≈ 63) belongs to the notification-shade gesture, not to the app.
- **Verify the install, not the script that installed it.** `dumpsys package <pkg> | grep lastUpdateTime`
  must match the build's timestamp — a timed-out `adb install` leaves the previous build running and the next
  smoke run silently exercises it. For a native change, probe the packaged entry that **exists** (the debug
  library is `libosmscout_client_javad.so`) and grep a *calibrated* control literal from the same file: a
  surprising probe result is a candidate verification bug before it is a product defect (grep a known
  literal first to find the library that owns the code).
- **Host load explains ANR-shaped evidence.** Read `uptime`/`ps` before blaming the app — `./gradlew --stop`
  stops the daemon but not the native `cc1plus` children it already spawned; ANRs with the app at 63-109 %
  CPU arrived at host load 19-27 and vanished once the build finished. Check `isKeyguardShowing`/
  `mDreamingLockscreen` before trusting any UI-driven step (a secure lockscreen returns empty dumps, a
  `Graphics` reading of ~10 MB, and silently redirects taps; `run-as` does not work on a Play-signed build,
  so logcat is the only stream). Keep `logcat -G 16M` for a long pass — the default buffer rotates records
  away before they are counted.
- **A long-press drag on device is a motionevent sequence.** `input draganddrop x1 y1 x2 y2 900` and
  `input swipe` never start a reorder library's drag (its own long-press timeout never elapses):
  `adb shell input motionevent DOWN x y`, `sleep 1`, a loop of `input motionevent MOVE x' y'` at ~120 ms per
  step, then `input motionevent UP x' y'` — consecutive invocations share pointer 0, so the library sees one
  long press followed by a drag.
- **Multi-user and a11y-less car surfaces.** The car session may live in user 10 while `run-as` reaches
  user 0 (`/data/user/<id>/…` in the app's own log lines; `run-as --user` is rejected on the API 33 toybox;
  `adb root` fails on a production image): build the state through the car's **own UI** instead of seeding a
  fixture, or use a userdebug/eng image (`TODO.md` §106). The car surface exposes no accessibility nodes, so
  read text and positions from `adb exec-out screencap -p` + `tesseract <png> out tsv`, and expect the
  distant-display mirror to re-assert `CarAppActivity` within seconds — the phone UI is not a stand-in
  (`am start --user 10` on an external display is refused with a `SecurityException`). A force-stop with a
  live car session crashes the *host's* renderer process, so it is not a neutral restart on that AVD.
- **Record a partial pass as partial.** A disappeared AVD (`offline` → gone, no crash trace), an unreachable
  state or a stale frame goes into the change's tasks with the numbers actually collected, never implied as
  proof.

- **Never install an ABI-filtered APK for an on-device run** (§40.39): packaging strips the other ABIs, the
  install succeeds and the app dies at `System.loadLibrary`. Verify with
  `unzip -l <apk> | grep -o 'lib/[a-z0-9_-]*/'` (the character class needs the underscore) and check the APK
  mtime against the newest source edit.
- **An emulator cannot inject a bearing** (§40.40): `geo fix` has no bearing argument (always `bear=0.0`) and
  NMEA RMC is dropped by `FusedLocationProviderClient` ("too close / too fast"). Cover heading/bearing
  behaviour with unit tests, never with a GMS-emulator run.
- **A headless emulator needs `-dns-server 8.8.8.8`** (§40.41): broken DNS answers "Unable to resolve host"
  while a raw IP works — compare `adb shell ping` against the app's network code before debugging the app.
- **Check which maps are already installed before attempting a catalog download** (§40.42) — `adb install -r`
  preserves the app's data, so the maps survive a reinstall (§95).
- **After toggling `location_mode`, re-send `geo fix`** (§40.43) — Fused may need provider re-registration.
- **`adb shell input text` goes through the active IME** (§40.44): GBoard rewrote `Erbstollenstrasse` →
  `Er Stollenstraße`. Disable the IME for a replay and assert the field's actual value from a fresh dump.
- **The AAOS/car AVD is not usable for headless on-device steps** (§40.45): car system-UI ANRs swallow
  `input tap`, `uiautomator dump` returns an empty hierarchy, `geo fix` is answered OK but no fix reaches the
  app, and the host `RendererService` disconnects ~40 s after launch. Plan car verification for an
  interactive window or a real head unit, and state the blocker instead of burning a session.

## 11. Measuring a phone UI finding (do this before changing code)

A finding that lives in pixels ("the segment is still not completely visible", "the card is too
high", "the highlight is the wrong one") cannot be settled by reading projection, fit or layout
code: the model and the tests you write from it are self-consistent by construction. Change
`route-planning-session` spent nine review rounds on exactly that (`ki_processing_failures.log`) and
found both real causes only after measuring.

1. **Make the app tell you the numbers, coordinate-free.** Add (or read) a diagnostics line that
   reports indices, pixels, the free band and a verdict — never a position (spec
   `auto-diagnostics`). The route analysis carries `MapCanvasVM: segment focus: range=… mag=…
   covered=… band=[0,…] margin=… bboxPx=[…] inside=…`.
2. **Look at the screenshot, then measure it — never only describe it.** Read the frame with
   `view_image` (a local PNG path) and say what is on screen; that look is triage. The verdict comes
   from `tools/measure-highlight.py`, which finds the analysed-segment highlight by its casing colour,
   derives the card top from a `uiautomator dump` and prints the highlight's bounding box against the
   free band (exit 0 inside / 1 clipped / 2 no highlight). Its detector is covered by
   `.pi/skills/pixel-check/selftest.sh` (ImageMagick, no device). Only the script's numbers and exit
   code are evidence — a vision description never replaces them, and a vision model's impression is
   not a pixel verdict. Screenshot and dump must come from the same moment. A positive verdict also
   needs its precondition: `band` must end above the canvas bottom (a measured card top) and the match
   must be a stroke — hundreds or thousands of `px` over many rows, not tens of pixels spread wide,
   which is a colour coincidence (measured 2026-10-06 on a frame with no route: `px=18`,
   `band=[0,2400]`, `verdict: inside`; the look caught it — `TODO.md` §143). The look establishes the
   precondition, the script measures given it.
3. **Compare the two verdicts.** Model `inside=true` + measured `CLIPPED` is the bug (it found the
   follow-drift offset, which the fit model knows nothing about). Model `inside=true` + measured
   `inside=true` means the symptom is somewhere else — ask the owner *where* and *when* before
   changing anything.
4. **Say when a measurement was impossible** (a stationary emulator cannot produce follow drift)
   instead of implying device proof.
5. Iterate with focused suites (`--tests "com.naviveylin.ui.<area>.*"`) and run the full both-flavor
   gate once before the commit; drive the device with one reusable script (`.pi/skills/device-check`),
   and delete it before committing. `.pi/` is gitignored — rules that must survive belong here, in
   `AGENTS.md` or in `openspec/config.yaml`.
