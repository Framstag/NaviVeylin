# Build Guidelines — Build, Test & Release Skills

How to build NaviVeylin, run its tests, and produce release builds. This document
names the skills that wrap the Gradle workflows and explains when and how to use
them.

**Maintenance rule** — when a change supersedes a build convention here, update
this document in the same change.

---

## 1. Skills

Three skills wrap the existing Gradle calls. They are named and discoverable by
LLM agents (loaded on demand from `.pi/skills/`); use them whenever build, test,
or release work is requested or required by the OpenSpec apply/archive guidance.

| Skill | Purpose | When to use |
|---|---|---|
| `build-app` | Compile the app — debug APKs, flavors, ABIs | User asks to build/compile; verify code compiles; after Kotlin/native changes |
| `run-tests` | Execute unit + instrumented tests | User asks to run tests; verify tests pass; before marking a task complete |
| `release-build` | Produce Play-ready release AABs (mobile + automotive) | User asks for a release build or version bump; Play upload / sideload artifacts |

Each skill lives in `.pi/skills/<name>/SKILL.md` (gitignored — copy to
`~/.pi/agent/skills/<name>/` to make it available across projects).

## 2. Common behavior

All three skills follow the same contract:

- **Wrap the existing Gradle calls** (`./gradlew ...`) — no new build system,
  no wrapper scripts, no duplicated command logic.
- **Print status messages** before and after each run (e.g. "Building debug APK
  (all ABIs, both flavors)…", "Build succeeded — …").
- **Stream build output to the console** — output is never suppressed,
  redirected to a file, or filtered away; the console is the primary channel.
- **Evaluate the result by return code AND build output**:
  - Exit code `0` **and** output contains `BUILD SUCCESSFUL` → success
  - Exit code non-zero **or** output contains `BUILD FAILED` → failure
- **Report a clear verdict** with actionable error excerpts on failure.

## 3. Commands reference

| Goal | Command |
|---|---|
| Build debug APK (all 3 ABIs, both flavors) | `./gradlew :app:assembleDebug` |
| Build phone/Android Auto flavor only | `./gradlew :app:assembleMobileDebug` |
| Build AAOS flavor only | `./gradlew :app:assembleAutomotiveDebug` |
| Build single ABI (fastest iteration) | `./gradlew :app:assembleMobileDebug -Pandroid.injected.build.abi=arm64-v8a` |
| Run all unit tests | `./gradlew test` |
| Run app unit tests only | `./gradlew :app:testDebugUnitTest` |
| Run single test class | `./gradlew :app:testDebugUnitTest --tests "<FQCN>"` |
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
- **Test reports**: HTML at
  `app/build/reports/tests/testDebugUnitTest/index.html`, XML at
  `app/build/test-results/testDebugUnitTest/*.xml`.

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
  made the module suite die with `OutOfMemoryError` in one JVM (`TODO.md` §33,
  change `fix-auto-unit-test-heap-overflow`). The pattern to use is
  `auto/src/test/java/com/naviveylin/auto/RendererTestRule.kt`: a JUnit rule that
  hands out the component, shuts every tracked instance down after the test and
  FAILS the test when one still reports active background work (`AutoMapRenderer
  .activeBackgroundJobCount()`; `Job.isActive` is false immediately after
  `cancel()`, so nothing needs waiting for). A leak must fail its own class, not
  starve the suite's heap later.
- **Declared unit-test fork budgets.** Two modules need more than the AGP default
  fork heap (512 MB) to hold their whole suite in one JVM; both declare it in
  `testOptions { unitTests { all { it.maxHeapSize = … } } }` so a fresh checkout and
  CI get the same budget (spec `unit-test-suite-runtime`, change
  `fix-auto-unit-test-heap-overflow`):

  | Module | Suite | Declared | Measured |
  |---|---|---|---|
  | `:auto` | 49 classes / 516 tests | `1024m` | 512 MB → FAILED (141 `OutOfMemoryError` lines, 0 result XMLs, 9m08s); 1024 MB → green (one fork, 20s) |
  | `:app` | 146 classes / 1054 tests per flavor | `1024m` | 512 MB → deterministic `FavoritesSheetReorderComposeTest` failure (`ComposeTimeoutException` after 5000 ms, `TODO.md` §43); 1024 MB → green (1m49s); 2048 MB → green (2m06s) |

  Both values are the measured minimum plus headroom, deliberately not 2g: a full
  `./gradlew test` holds up to two `:app` forks, one `:auto` fork and one `:core`
  fork at once. If a machine cannot afford the ceiling, `forkEvery` (e.g. 24 for
  `:auto`, 40 for `:app`) bounds per-fork accumulation at the cost of a JVM start
  per batch — prefer that over raising the ceiling. Verify a budget by CONTENT,
  not by the build result: the fork args (`-Xmx…`, `--info`) and the per-class
  result XMLs (§4, §17).
- **One invocation per suite.** `:auto` and `:app` each complete in a single
  Gradle invocation at the declared budget; splitting a suite into class batches is
  a diagnostic fallback (e.g. to isolate one class), never the procedure, and a
  batched run is not evidence for the suite as it really runs. A Gradle
  build-cache hit (`FROM-CACHE`, `BUILD SUCCESSFUL in 2s` with no test executor)
  is not test evidence either — use `--rerun` when the run itself is the evidence.
- Instrumented tests need a connected device/emulator; if none is available,
  say so instead of running them.

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

**Generated-code exclusions** — Kover report filters (per module AND at the
root merge) exclude `BuildConfig`, `R`/`R$*`, and Hilt/Dagger/KSP-generated
classes (`dagger.hilt.*`, `hilt_aggregated_deps.*`, `*.Hilt_*`, `*_Hilt*`,
`*.Dagger*Component*`) so numbers reflect hand-written logic. Hand-written
`*Factory` classes are intentionally NOT excluded.

**Baseline (2026-09-11)** — merged report: 662 classes, 59.7 % line
(8839/14805), 58.2 % instruction; per module: `app` 58.9 %, `auto` 53.8 %,
`core` 67.3 % (line). `osmscout-client-java`: LINE 5 covered / 1 missed.
Re-measure after meaningful test work; a future change may add a
`koverVerify` threshold gate on top of this baseline.

**CI** — `.github/workflows/build.yml` generates the reports after "Run unit
tests" and uploads them as the `coverage-reports` artifact
(`if-no-files-found: error`).

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

