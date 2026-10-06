# Proposal — Speed up the build and test gate

**Change:** `speed-up-build-test-gate`
**Date:** 2026-10-04
**Scope:** build/test infrastructure for all modules and both distribution flavors — no user-facing
behaviour, no AAOS/Auto-specific capability, no UI.

## Why

Every agent iteration and every pre-commit gate pays roughly nine minutes for a task set whose work is
partly duplicated and largely single-threaded, on a 16-core machine that sat at a load average of
1.2–4.0 while it ran. Measured on the gate that ran 2026-10-04 18:22:38 → ~18:31:40
(`:app:testMobileDebugUnitTest :app:testAutomotiveDebugUnitTest :app:assembleMobileDebug
:app:assembleAutomotiveDebug --rerun-tasks --continue`, both flavors green, 1635 tests per flavor,
0 failures):

| phase | wall | share |
|---|---|---|
| config + compile + dex + package + native merge, both flavors, forced by `--rerun-tasks` | 2m13s | 25% |
| `:app:testMobileDebugUnitTest` — 1635 tests, ONE fork | 3m15s | 36% |
| `:app:testAutomotiveDebugUnitTest` — the same 215 classes / 1635 tests, ONE fork | 3m04s | 34% |
| total | ~9m02s | 100% |

Two structural facts explain it:

1. **The second flavor is identical work.** Both result directories list the same 215 classes
   (`comm` on the two class lists is empty in both directions) and `app/src/` has no
   `testMobile`/`testAutomotive` source set — the flavor reaches the tests only through the merged
   manifest and `BuildConfig`.
2. **The suite is single-threaded.** The sum of `<testcase time>` is 190 s against a 195 s suite wall,
   with the fork at 170–210 % CPU: JUnit executes one class at a time, so most of 16 cores stay idle
   while 3270 tests run per gate.

Machine state sampled during that run: ~1 GB RAM free, 19 GB of 36 GB swap in use, kswapd active,
two Kotlin daemons alive (1.28 GB + 0.37 GB, different compiler classpaths), an emulator up for
4.5 h. The box is memory-bound, not CPU-bound — so concurrency has to be paid for with freed memory
before it can help.

## What Changes

- **Gate task set.** The routine gate runs each unit-test suite once per *affected* flavor instead of
  unconditionally running both, and drops APK assembly for changes that cannot affect packaging
  (native, manifests, resources, flavors). **BREAKING (procedure):** a green routine gate no longer
  implies that both flavors were tested; the flavor-affected rule and the full-gate trigger become the
  documented contract.
- **Forcing discipline.** A run that must prove test execution forces the *test tasks* — remove their
  result outputs, disable the build cache for that invocation — instead of forcing every task in the
  graph with `--rerun-tasks`. **BREAKING (procedure):** the evidence line changes from a bare
  `BUILD SUCCESSFUL` to executed-task count + per-module tallies + fresh result-XML timestamps.
- **Coverage instrumentation is per invocation.** The Kover agent is attached only when the invocation
  asks for coverage, so iteration runs stop paying instrumentation for 3270 tests per gate.
- **Test parallelism is declared and measured.** `:app` and `:auto` declare a fork count next to the
  existing heap ceiling; a suite still writes one result XML per executed class, but the failure set
  with N parallel forks must equal the one-fork baseline, and the budget comment in
  `guidelines/Build.md` §6 gains the measurement that justifies the declared values.
- **Timing is instrumented.** Every gate run leaves per-task and per-suite timings behind, and the
  documented gate recipe quotes the per-phase breakdown, so a regression shows up as numbers instead
  of an impression.
- **Iteration builds stay JVM-only where they can.** Native artifacts are scoped per iteration (single
  ABI for iteration builds, no forced native rebuild for JVM-only changes), stale
  `app/.cxx/<config-hash>` trees are pruned, and CCache is evaluated rather than assumed.
- **Configuration cache.** Evaluate and — only if the release-version hazard below is resolved —
  enable it for iteration invocations.
- **CI alignment.** `.github/workflows/build.yml` runs the same gate recipe as the local one, so local
  evidence and CI evidence stay comparable.

## Capabilities

### New Capabilities

- `build-test-gate`: the routine verification gate — which suites and flavors it runs for a given
  change, how a run proves that tests actually executed, how its duration is measured and reported,
  and how the local recipe stays aligned with CI.

### Modified Capabilities

- `unit-test-suite-runtime`: the declared fork budget gains a concurrency dimension (a declared fork
  count with its measured justification) and a result-equivalence requirement (the failure set under
  parallel forks equals the one-fork baseline), instead of only a heap ceiling and a fork cadence.
- `test-coverage`: coverage instrumentation becomes a property of the invocation rather than an
  always-attached agent, while the aggregated HTML/XML reports stay producible and unchanged.

## Impact

**Gradle / build files**

- `gradle.properties` (root) — `org.gradle.*` settings: configuration cache, build cache, worker
  count, daemon `jvmargs`. The permission policy in this workspace denies reading this file, so an
  owner must confirm or supply its current values before the design fixes the deltas.
- `app/build.gradle.kts` — `testOptions.unitTests.all` (heap ceiling plus the new fork count), the
  Kover instrumentation switch, the `preBuild`-wired scanners (`checkHardcodedStrings`,
  `checkNoCoordinatesInLogs`, `checkSubmoduleStylesheets`, `checkSubmoduleIcons`), and the
  configuration-time release version bump (lines 54–130, `releaseStateFile`) if the configuration
  cache forces it to move.
- `auto/build.gradle.kts` — the same test options.
- `build.gradle.kts` (root) — Kover aggregation and the instrumentation switch if it lives here;
  the `release` task.
- `.github/workflows/build.yml` — the `Run unit tests` and `Generate coverage reports` steps of the
  `build` job (today: plain `./gradlew … test`).

**Modules touched:** `:app` (both flavors), `:auto`, `:core`, `:osmscout-client-java`. No manifest,
resource, or `app/src/automotive/**` change; no Android component behaviour change.

**Guidelines and tooling (durable rules must land here, not only in the gitignored `.pi/skills/`)**

- `guidelines/Build.md` §2–§4 (gate behaviour, result evaluation, forcing), §6 (fork-budget table and
  the "one fork is calibrated" statement), §7 (coverage) — the affected document for this change.
- `AGENTS.md` — the "Agent iteration loop (measure first)" section (focused suites, gate once).
- `.pi/skills/run-tests`, `.pi/skills/build-app`, `.pi/skills/revert-check` — local wrappers; anything a
  fresh clone needs must also be written into `guidelines/Build.md`, `openspec/config.yaml`, or
  `AGENTS.md`.
- `TODO.md` — §319 (dex `OutOfMemoryError` in a long-lived daemon), §813 (Gradle 10 deprecations and
  the untried configuration cache), and the run-tests findings that this change resolves.
- `tools/` — only if a timing report helper is introduced; it needs a self-test that runs without a
  device (`tools/measure-highlight.py` is the precedent).

**Specifications at risk but not modified:** `release-target` (the version bump runs at configuration
time today; enabling the configuration cache must keep "builds other than the release target SHALL NOT
mutate the persisted version state" true — if the bump moves into a task, the requirement text does not
change), `kover-aggregate-report`, `ci-unit-test-jni`, `ci-vcpkg-cache` (paths and behaviour unchanged).

**Native / JNI:** no submodule patch and no bridge-module override. No C++ source, JNI signature, or
CMake target changes — only the native build's *invocation scope* (single-ABI iteration) and the
`app/.cxx` cache layout.

**Additive or breaking:** additive to the codebase and to the app's behaviour; two procedural
**BREAKING** changes as marked above. **Rollback:** every lever is confined to Gradle build scripts,
`gradle.properties`, the workflow file and documentation — revert the commits and the previous
nine-minute gate with both flavors and forced tasks returns. No runtime, data, or user-visible state is
involved, and the spec deltas revert with the change.

## Decision to confirm before design

**Coverage default (large consequence).**

- *Option 1 (assumed by this proposal):* instrumentation stays on by default; local iteration passes an
  opt-out property. CI and archive evidence keep matching today's behaviour, and only local iteration
  wins the time.
- *Option 2:* instrumentation is off by default; CI passes an opt-in property. Local and CI gates both
  skip it, but the opt-in must be wired into the CI test step or the coverage report becomes empty; a
  report step that enables instrumentation itself would re-run the whole suite.

The design document settles this before any build script is touched.
