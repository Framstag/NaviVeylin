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
`app/src/main/cpp/libosmscout`) as a `libosmscout` component. Every output
passes `cyclonedx validate` inside the task.

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
