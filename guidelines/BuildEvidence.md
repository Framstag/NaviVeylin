# Build Evidence Guidelines — verdicts, evidence rules, coverage and phone measurement

What a run's result means, what evidence it owes, what the coverage reports are, and how a phone-UI finding
is measured before code changes. This document was cut from `Build.md`; the numbers did not change, only the
document that carries them.

> Rationale, baselines and case histories are `guidelines/BuildEvidence.why.md`; the command transcripts and
> tallies are `guidelines/Build.recipes.md`.

---

## 4. Result evaluation

Every sub-section answers one question about a run; read the one that owns it. §4a "The verdict, the warnings
and the failures", §4b "Which flavors and suites a run must cover", §4c "Native scope, pruned configurations
and a restored mutation", §4d "Attributing a red run", §4e "Falsification, flakes and scheduling traps",
§4f "Artifacts, evidence rules and native verification".

## 4a. The verdict, the warnings and the failures

- **Rule: a run succeeds on exit code `0` and `BUILD SUCCESSFUL`, and fails on a non-zero code or
  `BUILD FAILED`.**
- **Rule: on failure, extract and report the Kotlin compile errors, the C++/NDK errors, the failed tasks and
  the stylesheet-check cause.** Extract `e: ` lines, `error:` lines from CMake/ninja, `> Task ... FAILED`
  lines; a `checkSubmoduleStylesheets` failure means the libosmscout submodule is not initialized
  (`git submodule update --init --recursive`).
- **Rule: the build must have no warnings — report them, don't ignore.**
- **Rule: a qualifier applied to an injected property states its target** — `@param:ApplicationContext`, not
  `@ApplicationContext` — because the unqualified form is applied to the value parameter only and a later
  Kotlin default also applies it to the field.
- **Rule: a source-scanning guard walks the source-set roots, not a repository walk pruned by directory
  name** — the project's own source-scan guards live in the package `com.naviveylin.build`, which a `build`
  prune removes, leaving a green that scanned nothing.
- **Rule: a fixture inside a guard's own surface is assembled from parts**, or it becomes a finding of the
  guard it feeds.
- **Rule: the unit-test source sets carry none of the `… is deprecated. Deprecated in Java.` family**, and the
  `:auto` source set carries none of its three non-opt-in diagnostics (a final-class type parameter, an
  always-true condition, an unchecked cast), and the `:app` source set names a nullable value's non-null
  assumption. Each absence is held by a baseline case (`UnitTestSourceDeprecationBaselineTest`,
  `AutoTestSourceWarningBaselineTest`, `UnitTestSourceNullabilityBaselineTest`); the requirement is spec
  `build-test-gate`.
- **Rule: a test failure is isolated by rerunning a single test** with `--tests "<FQCN>"`, then reported.

(The warning counts and the families each baseline holds are `guidelines/BuildEvidence.why.md` §4a.)

## 4b. Which flavors and suites a run must cover

- **Rule: `:app` has one test task per flavor** — `:app:testMobileDebugUnitTest` and
  `:app:testAutomotiveDebugUnitTest` (there is no `:app:testDebugUnitTest`).
- **Rule: the routine gate runs the `:app` suite of the flavors the change can affect** (change
  `speed-up-build-test-gate`, spec `build-test-gate` — "Gate runs the affected flavors") — **both** when it
  modifies `app/src/automotive/**`, a flavor's manifest or resources, the flavor definitions in
  `app/build.gradle.kts`, or native inputs; **one** (`mobile`, the base flavor) for a change that touches only
  sources the two flavors share.
- **Rule: before a change is called complete, run both flavors with every shipped ABI built** — the reduced
  form is an iteration form and must not be reported as a full gate. `build/gate-timings.json` (§2c) shows how
  many suites a run actually executed.
- **Rule: iteration runs the change's declared cases first** (change `speed-up-test-iteration`, spec
  `build-test-gate` — "Iteration pre-gate runs the change's declared cases first"). Before the module suite,
  `bash tools/declared-cases.sh <change> --diff <this change's own file list> --command` prints the test
  classes the change declares, with their measured class time and the ready Gradle line. Run it in **two
  stages**: `--rules declared,touched` is the author-declared stage, the full union (including `mention`) is
  the second stage. A shared working tree inflates `touched` and `mention` with other changes' dirty files, so
  the change's own file list is the honest `--diff` input. **A pre-gate run is not gate evidence** and the
  completion rule is unchanged.
- **Rule: report tallies, not vibes** — quote `tests/failures/errors` per module from the XML
  (`app/build/test-results/testMobileDebugUnitTest/*.xml` etc.). A bare `BUILD SUCCESSFUL` says nothing about
  what ran.
- **Rule: a cached run is not a run** — `BUILD SUCCESSFUL in 3s` with `UP-TO-DATE` / `FROM-CACHE` executed no
  tests.
- **Rule: to force it, use `-PforceTests --no-build-cache`** (change `speed-up-build-test-gate`, spec
  `build-test-gate`). It makes the JVM unit-test tasks not up to date without re-executing the rest of the
  graph; `--no-build-cache` is not optional, because a task that is not up to date may still have its outputs
  restored from the cache. Keep `--rerun-tasks` for the completion gate and for anything whose inputs the
  change touched.
- **Rule: `-PjvmTestsOnly` joins it** (change `fix-jvm-test-run-needs-no-native-tree`, spec `build-sbom` — "A
  JVM-only test run does not require the native tree"): without the property the unit-test graph carries
  `generateLicenseAssets` → `generateSbom` → `mergeNativeSbom`, and a tree with no vcpkg checkout cannot
  configure the suite. The documented gate, `loop.conf`'s `LOOP_GATE_COMPILED_CMD` and the CI unit-test step
  pass it; the invocation that packages must not, and is refused by name.

(The measured savings, the stage tallies and the configuration failure are `guidelines/BuildEvidence.why.md`
§4b.)

## 4c. Native scope, pruned configurations and a restored mutation

- **Rule: a JVM-only change needs no native build** (change `speed-up-build-test-gate`, spec `build-test-gate`
  — "Iteration runs build no more native artifacts than they test"). The `:app` unit tests use the host stub
  (`src/test/jniLibs`), not the packaged libraries.
- **Rule: when a run needs native artifacts for one device or emulator, build that ABI only** with
  `-Pandroid.injected.build.abi=arm64-v8a`. **Rule: before a change is called complete, every shipped ABI must
  be built** (`:app:assembleMobileDebug :app:assembleAutomotiveDebug` → all three ABIs in both APKs).
- **Rule: superseded native configurations are pruned** (spec `build-test-gate` — "Superseded native
  configurations are pruned"): `bash tools/prune-native-configs.sh --dry-run --skip tools app/.cxx`, then the
  same command without `--dry-run`. It keeps the newest N (default 2) configuration trees per build type; it
  refuses while a Gradle build is active and refuses a mis-scoped root; `--skip tools` leaves the host-tool
  configurations alone. Device-free self-test: `bash tools/prune-native-configs-selftest.sh`.
- **Rule: restoring a mutation re-creates a cached tree** — after a revert-check the source hash equals the
  state whose successful result is already cached, so the restored run answers `UP-TO-DATE` in seconds and the
  XML keeps the **previous** run's `timestamp`. Force it and check the `timestamp` against the wall clock (the
  `revert-check` skill).

(The measured ABI costs, the prune's saving and the historical references are `guidelines/BuildEvidence.why.md`
§4c.)

## 4d. Attributing a red run

- **Rule: read the mechanical half in one call** — `tools/gate-attribute.sh <gate.log>` prints the failing
  case, its `file:line`, the first error line, the failed step, the flake line, and the rerun command derived
  from the step's own command. The flake line reads `not recorded — this tree keeps no flake list`, which is
  the fact and not a verdict.
- **Rule: a flake of unknown origin gets an entry with its rerun evidence, never a silent retry** — record the
  red run and the green rerun together with the mechanism they ruled out.
- **Rule: sweep the suite for what the change moves, before the gate** — a behaviour change can move an
  expectation pinned verbatim in an unrelated class, and focused runs cannot see it. `grep -rn '<the constant
  or value you changed>' app/src/test core/src/test auto/src/test`, plan that expectation change, and quote old
  → new in the task. Prefer re-expressing the assertion against the spec over pinning a new number.
- **Rule: attribute before blaming or claiming** — a failure that moves between runs or flavors, and that
  passes alone, may not be yours. The ritual: (1) rerun the failing class **alone** (`--tests <FQCN>`); (2)
  rerun it **together with the classes the change added**; (3) find who ran **before** it from the JUnit XML
  `timestamp` order; (4) bisect with `--tests` filters over that order (the shell does **not** word-split
  variables — pass the filters as literal arguments, or wrap the call in a script); (5) quote **both** numbers
  (the failing set and the alone run) and file the residual as an issue, leaving the "zero failures" task item
  **unchecked** rather than talking the run green.
- **Rule: `UncaughtExceptionsBeforeTest` is not a flaky assertion** — a coroutine threw on a *process-wide*
  dispatcher (`Dispatchers.Default`/`IO`, or the main thread) after the test that owned it ended. Hunt the
  owner: look for scopes/tickers that outlive a test and for any coroutine body on a real dispatcher without a
  fault confinement (`runCatching` or a `CoroutineExceptionHandler`). The same escaping throwable kills the app
  on a device.
- **Rule: `Dispatchers.Main is used concurrently with setting it` is the same leak one layer down** — a
  coroutine from an earlier test, or a state holder that outlives its test, is still dispatching onto the main
  dispatcher while the next test's rule replaces it. Read the exception's stack, it names the leaker; hunt
  repeating `withContext`-back-to-main **before** bisecting classes, then move the loop's home off the main
  dispatcher (`guidelines/Design.md` §4).

(The measured log-cost, the worked example and the CI run numbers are `guidelines/BuildEvidence.why.md` §4d.)

## 4e. Falsification, flakes and scheduling traps

- **Rule: a new regression test must fail against the *unfixed* revision first.** One that passes against the
  bug is worse than none: run it on the pre-fix tree before claiming it guards the fix. If it cannot fail there
  — e.g. Robolectric defers the child collector that runs inline on the device — delete it and record that a
  device run is the only evidence.
- **Rule: a revert-check's green half needs a forced run, and the case must assert the *positive* fact.** Force
  the test tasks with `-PforceTests --no-build-cache`; after restoring a mutated file the source hash matches an
  earlier state, so compare the XML `timestamp` with the wall clock before quoting counts. The assertion must be
  what the guard produces — the buffer *was* acquired exactly once — not the absence of growth.
- **Rule: a flaky victim needs a rate per configuration (N ≥ 3) before a bisect means anything.** Measure runs
  per configuration instead of one run per combination; never infer the leaker from the victim's *package*. Also
  bound the test that perturbs the suite.
- **Rule: test-scheduling traps are honoured.** `advanceTimeBy` is *exclusive* at its boundary, so a debounce
  deadline landing exactly on the advanced-to instant never fires — advance past it by more than a tick, or
  `runCurrent()` after an exactly-at-the-deadline advance. A production `withContext(…)` inside a suspend
  accessor makes a virtual-time test non-deterministic: give the hop its own dispatcher seam
  (`guidelines/Design.md` §4), and a test that builds such a component points the seam at its own scheduler —
  an unwired seam leaves a real thread that no `advanceUntilIdle()` reaches.
- **Rule: a background publisher publishes atomically, and its timing decision reads an injected source**
  (change `fix-navigation-engine-test-flakes`, specs `navigation-engine` / `unit-test-suite-runtime`): a tick or
  worker that runs off the main thread publishes through `MutableStateFlow.update { … }`, never
  `state.value = state.value.copy(…)`. Timing decisions — staleness, throttle, cooldown — read the component's
  injected time source, never `System.currentTimeMillis()` at the decision site; and a test may not compute a
  deadline from the wall clock or pace itself with `Thread.sleep` — it drains the injected schedulers and
  asserts.
- **Rule: test reports are read at known paths** — HTML at
  `app/build/reports/tests/testMobileDebugUnitTest/index.html`, XML at
  `app/build/test-results/testMobileDebugUnitTest/*.xml`.

(The measured flake history and the case that proved each rule are `guidelines/BuildEvidence.why.md` §4e.)

## 4f. Artifacts, evidence rules and native verification

### Artifacts and evidence rules

- **Rule: verify an artifact against its inputs, not by the existence of an output path** — a
  build-cache-restored APK in `outputs/apk/` and a stale `build/outputs/sbom/*` both look exactly like a
  product bug. Compare content/mtime against the change, and probe a packaged literal rather than trusting the
  path.
- **Rule: a test that reads a packaged asset reads the unit-test asset APK; mutate the asset's *source*, never
  a task's output.** Robolectric reads
  `app/build/intermediates/apk_for_local_test/<variant>/package<Variant>UnitTestForUnitTest/apk-for-local-test.ap_`,
  which AGP rebuilds from the variant's merged assets.
- **Rule: one mutation per revert check.** A combined mutation masks the other behaviour and the assertion
  becomes vacuously true — mutate one guard, run every case it protects, restore, then force the green run.
- **Rule: after a semantic change to a shared value, grep the test tree for the observable it moved.**

### Native verification — new methods, hidden visibility

- **Rule: a new native method is invisible to the compiler.** Adding a method to `OSMScoutClient` breaks
  nothing: the test fakes compile without overriding it, and the first *call* throws `UnsatisfiedLinkError`.
  Update every fake in the same change and run the affected suites, because the suite is the only proof the call
  path works.
- **Rule: a hidden-visibility library may only expose *exported* types on its interface.** `libosmscout` builds
  with `-fvisibility=hidden`: taking an older, un-annotated class by value fails for every consumer outside the
  library; hold the exported factory handle instead of a by-value base member.
- **Rule: `ctest` after a partial `ninja` is not evidence.** Build every target first (`ninja -k 0`) before
  reading a suite verdict, and prove a suspected regression by rebuilding that target with the change stashed.
- **Rule: any change inside an `#ifdef OSMSCOUT_HAVE_LIB_MARISA` block is verified in BOTH configurations.**
  The Android build always defines it, so CI's non-Marisa path is invisible here; reproduce by inserting
  `#undef OSMSCOUT_HAVE_LIB_MARISA` after the include block of `OSMScoutClient.cpp`, building
  `:app:assembleMobileDebug -Pandroid.injected.build.abi=arm64-v8a`, then removing the `#undef`.
- **Rule: native test binaries are not directly executable here.** `ctest -R <Test> --output-on-failure` does
  **not** build — `ninja -C <build> <Target>` first, and filter `ctest -N` output for `Test #` lines. The meson
  `hostbuild/` directory has no `CTestTestfile.cmake`, so use `meson test -C hostbuild "<test name>"
  --print-errorlogs` there, and spend one throwaway `ninja` invocation after a `meson.build` edit.
- **Rule: the host JNI target exists only where `pkg-config` finds a `jni` module.** Provision one for an
  installed JDK (`~/.local/share/pkgconfig/jni.pc`), keep `PKG_CONFIG_PATH` pointing at that directory, and
  re-run `meson setup --reconfigure hostbuild`.
- **Rule: `System.loadLibrary` needs the plain-name `.so`** — a versioned `.so.1` symlink is not found.
- **Rule: plain `openDatabase(containerRoot)` wipes the DBThread** — the root has no `types.dat`; load a
  container of maps through the builder's map-lookup scan.
- **Rule: the `:osmscout-client-java` Gradle JAR excludes `OSMScoutClient.java`/`Builder`** — never feed it to
  JavaScout Maven; build the JAR from the submodule's `java/` sources.
- **Rule: JavaScout Maven tests need `JAVA_HOME=java-21`** — JUnit 5.10.2 on Java 26 discovers but executes 0
  tests.
- **Rule: stale meson host builds — `sed` the ninja link line to a stub path before rebuilding** —
  `/usr/lib` is not writable and there is no sudo.
- **Rule: native index fixtures must be FULL (non-eco) imports** — `--eco true` skips the POI indexes, so an
  index test silently asserts nothing.
- **Rule: `DBThread` loads databases sequentially** — wait for two consecutive identical results before
  asserting.
- **Rule: verify `md5` after any `git stash` cycle before rebuilding** — a stash+pop silently reverted a
  submodule patch and the "patched" host library was unpatched.
- **Rule: the search scope's extent derivation is a host unit**
  (`libosmscout-client-java/src/search_scope_extent.cpp`, case `Tests/src/SearchScopeExtentTest.cpp`): it loads
  the scope region's object and touches no JNI, so it compiles into the host suite. The case imports
  `Tests/LocationTest.olt` at start-up instead of opening the committed `Tests/data/testregion`, whose location
  index is a header carrying no region, and deletes the several-hundred-MB `coord.dat` the full import writes
  once done.

(The dated reproductions behind each bullet are `guidelines/BuildEvidence.why.md` §4f.)

## 7. Code coverage

- **Rule: coverage is report-only — no threshold gate.** `./gradlew test` never fails on coverage percentages;
  measurement is an explicit, separate step.
- **Rule: two engines report coverage.** JVM unit tests only (Robolectric runs on the host JVM and is covered;
  no instrumented/device coverage):
  - `app`, `auto`, `core` use Kover 0.9.9: `./gradlew :koverHtmlReport :koverXmlReport` (root merge),
    `build/reports/kover/` merged and per-module.
  - `osmscout-client-java` own sources use Gradle JaCoCo: `./gradlew :osmscout-client-java:jacocoTestReport`.
  - `osmscout-client-java` vendored client API use Gradle JaCoCo:
    `./gradlew :osmscout-client-java:jacocoVendoredTestReport`.
  Kover measures Kotlin sources accurately and cannot measure a pure-Java module without the Kotlin plugin, so
  the Java-only JNI bridge uses the standard JaCoCo plugin, reported separately.
- **Rule: the JNI module has two provenances, and each is its own report and its own coverage-row key**
  (spec `test-coverage`). It compiles its own local overrides (`src/main/java`) beside the vendored
  `libosmscout` Java client API as two Gradle source sets, so `tools/build-inputs.sh` yields two keys —
  `osmscout-client-java` (own code) and `osmscout-client-java-vendored` (what the pin ships) — and the
  `coverage` row applies its floor to each, so a vendored-code gain cannot pay for an own-code deficit.
- **Rule: the agent is attached by default; `-PnoCoverage` detaches it** (change
  `speed-up-build-test-gate`, spec `test-coverage`):
  ```bash
  ./gradlew :app:testMobileDebugUnitTest -PforceTests -PnoCoverage --no-build-cache
  ./gradlew :koverXmlReport :osmscout-client-java:jacocoTestReport :osmscout-client-java:jacocoVendoredTestReport
  ```
- **Rule: a `-PnoCoverage` run deletes that task's execution data.** Kover removes data it did not produce, so
  the invocation that feeds a report must not pass the opt-out, or the tests have to run again.
  `> Task :core:testDebugUnitTest UP-TO-DATE` while `:core:koverXmlReportDebug` runs is the check that the
  report came from real data.
- **Rule: generated-code exclusions are applied** — Kover report filters (per module and at the root merge)
  exclude `BuildConfig`, `R`/`R$*`, and Hilt/Dagger/KSP-generated classes (`dagger.hilt.*`,
  `hilt_aggregated_deps.*`, `*.Hilt_*`, `*_Hilt*`, `*.Dagger*Component*`) so numbers reflect hand-written
  logic; hand-written `*Factory` classes are intentionally NOT excluded.
- **Rule: configuration cache is deliberately off** (evaluated 2026-10-04/05). `./gradlew --configuration-cache
  :app:testMobileDebugUnitTest …` does not store an entry: the script-level `DefaultTask` registrations capture
  script objects. Clearing them means moving those tasks into `buildSrc` classes with serializable inputs — a
  change of its own, not a build-flag flip.
- **Rule: CI generates the reports after "Run unit tests" and uploads them** as the `coverage-reports`
  artifact (`if-no-files-found: error`); a following step prints the per-module, per-flavor tallies from the
  result XML.

(The baseline figures, the engine/agent timings, the deprecation baselines and the case histories are
`guidelines/BuildEvidence.why.md` §7; the sample commands and tallies are `guidelines/Build.recipes.md` §7.)

## 11. Measuring a phone UI finding (do this before changing code)

A finding that lives in pixels ("the segment is still not completely visible", "the card is too high", "the
highlight is the wrong one") cannot be settled by reading projection, fit or layout code: the model and the
tests you write from it are self-consistent by construction.

1. **Rule: make the app tell you the numbers, coordinate-free.** Add (or read) a diagnostics line that reports
   indices, pixels, the free band and a verdict — never a position (spec `auto-diagnostics`). The route analysis
   carries `MapCanvasVM: segment focus: range=… mag=… covered=… band=[0,…] margin=… bboxPx=[…] inside=…`.
2. **Rule: look at the screenshot, then measure it — never only describe it.** Have a throwaway child session
   read the frame (§1, "Looking at a screenshot") and say what is on screen; that look is triage, and only its
   text enters this session. The verdict comes from `tools/measure-highlight.py`, which finds the
   analysed-segment highlight by its casing colour, derives the card top from a `uiautomator dump` and prints
   the highlight's bounding box against the free band (exit 0 inside / 1 clipped / 2 no highlight). Only the
   script's numbers and exit code are evidence — a vision description never replaces them. Screenshot and dump
   must come from the same moment. A positive verdict also needs its precondition: `band` must end above the
   canvas bottom and the match must be a stroke — hundreds or thousands of `px` over many rows, not tens of
   pixels spread wide.
3. **Rule: compare the two verdicts.** Model `inside=true` + measured `CLIPPED` is the bug. Model `inside=true`
   + measured `inside=true` means the symptom is somewhere else — ask the owner *where* and *when* before
   changing anything.
4. **Rule: say when a measurement was impossible** (a stationary emulator cannot produce follow drift) instead
   of implying device proof.
5. **Rule: iterate with focused suites and drive the device with one reusable script** —
   `--tests "com.naviveylin.ui.<area>.*"`, the full both-flavor gate once before the commit, and a scratch
   script kept outside the tracked tree (`.pi/logs/<check-name>/`, `/tmp`), never beside a skill. The skills
   under `.pi/skills/` are versioned (§1) — rules that must survive belong here, in `AGENTS.md` or in
   `openspec/config.yaml`.

(The detector's coverage, the measured false-positive case and the worked example are
`guidelines/BuildEvidence.why.md` §11.)
