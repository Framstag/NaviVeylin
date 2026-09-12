# Design: add-kover-coverage

## Context

See proposal.md — Why. Current state: 4 modules with JVM unit tests
(`app` 116 test files, `auto` 40, `core` 12, `osmscout-client-java` 1 JUnit5
test), no coverage tooling, CI runs plain `./gradlew test` on ubuntu with no
device. AGP 9.4.0, Kotlin 2.2.10, Gradle wrapper per `gradle/wrapper/`.
Existing build conventions: all plugins declared in root `build.gradle.kts`
with `apply false`, module build files apply them; CI mirrors local Gradle
invocations; build guidance lives in `guidelines/Build.md`.

## Goals / Non-Goals

Goals:
- JVM unit test coverage for all 4 modules: Kover (app, auto, core) + JaCoCo
  (osmscout-client-java), per-module + one aggregated report (HTML + XML)
- CI step generating reports after unit tests + artifact upload
- Generated-code exclusions so numbers reflect hand-written logic
- Documented workflow in `guidelines/Build.md`

Non-goals (design boundaries):
- No `koverVerify` threshold gate — report-only; baseline-first (user decision)
- No instrumented/device coverage (`androidTest`) — 0 test files exist, CI has
  no device; Kover does not support it anyway
- No change to runtime app behavior, release pipeline, or native C++
- No coverage for Kotlin compiler plugins' synthetic output beyond the
  generated-code exclusions below

## Decisions

### D1: Coverage engine — Kover 0.9.8 (+ JaCoCo for the pure-Java module)
Chosen: `org.jetbrains.kotlinx.kover` version `0.9.8` at root (merging
module) and in the Kotlin modules (`app`, `auto`, `core`); see D6 for
`osmscout-client-java`.

Rationale over **AGP built-in JaCoCo** (`testCoverage { jacocoVersion = ... }`
+ `isTestCoverageEnabled`): the codebase is ~100% Kotlin; Kover instruments
Kotlin sources directly (lambdas, inline functions, default args counted
accurately), while JaCoCo works on JVM bytecode where Kotlin produces
systematic false misses (null-check branches, synthetic accessors). Kover also
ships multi-module aggregation and a `koverVerify` task for a future gate.
AGP 9.x support landed in Kover 0.9.4 (fixes #776/#784/#785); 0.9.8 is chosen
over 0.9.4 for later bugfixes (Gradle 9.6 deprecation, `com.android.*` 0%
coverage fix #810). Kotlin 2.2.x alignment matches the project's Kotlin 2.2.10.

Alternative rejected — **manual JaCoCo Gradle plugin**: requires offline
instrumentation wiring, agent flags on the test JVM, per-variant report merge,
and separate config for the plain `java-library` module. No aggregation or
verify tooling; strictly worse for this project.

### D2: Plugin wiring — root merging module + per-module apply
Chosen: root `build.gradle.kts` declares `id("org.jetbrains.kotlinx.kover") version "0.9.8"` (applied to root — Kover explicitly supports a source-less root as “merging module”); `app`, `auto`, `core` apply the plugin in their own plugin blocks (version inherited). The root merges per-module coverage via the `kover` configuration (`dependencies { kover(":app"); kover(":auto"); kover(":core") }` — string project-path notation; Project-object notation is deprecated in Gradle 9.6), producing one aggregated HTML+XML report (`:koverHtmlReport`, `:koverXmlReport`).

Rationale: mirrors the project's existing plugin declaration pattern;
aggregation satisfies spec requirement "Aggregated multi-module report" with a
single CI artifact. Per-module report tasks remain available for local use
(`./gradlew :app:koverHtmlReport` etc.).

Alternative rejected — **per-module reports only**: simpler wiring but no
single project-wide number; CI would upload 4 artifacts and the spec's
aggregation requirement would be unmet. The merged report is a superset; both
are cheap, so aggregation wins.

### D3: Generated-code exclusions
Chosen: Kover report filters exclude, per module AND at the root merging
module (per-module filters do not propagate into the merged report):
- `BuildConfig` (any package)
- `R` / `R$*` resource classes
- `dagger.hilt.*`, `hilt_aggregated_deps.*`, `*.Hilt_*`, `*_Hilt*`
  (Hilt-generated entry points and `_HiltModules` wiring), KSP-generated DI
  components

Verified against the first real report: exactly the 69 Hilt/Dagger-generated
classes appeared in the merged report pre-filter and all match the patterns
(glob `*` matches any chars in the fully-qualified name). Hand-written
`*Factory` classes are NOT blanket-excluded — the list is conservative.

### D4: CI integration — dedicated step + artifact
Chosen: in `.github/workflows/build.yml`, after "Run unit tests", a new step
runs the report tasks `:koverHtmlReport :koverXmlReport
:osmscout-client-java:jacocoTestReport`, and a step uploads
`build/reports/kover/**` (aggregated HTML+XML) plus
`osmscout-client-java/build/reports/jacoco/test/**` via
`actions/upload-artifact@v5`, `if-no-files-found: error`.

Rationale: keeps the existing test step byte-identical (build stays green
without thresholds — spec requirement), makes coverage an explicit artifact,
and fails loudly when reports are missing. Gradle cache key already hashes
`*.gradle.kts`, so the new plugin changes the cache key once — expected cold
cache, no further action.

### D5: Report task naming
Use Kover's default task names (`koverHtmlReport`, `koverXmlReport`,
aggregated root task) — no custom renames. Documentation references
`./gradlew koverHtmlReport koverXmlReport` (root) and per-module variants.

### D6: osmscout-client-java measured by JaCoCo, not Kover
Chosen: the pure-Java module (`java-library`, no Kotlin plugin) uses the
standard Gradle `jacoco` plugin — `jacocoTestReport` with HTML+XML. Kover
produces an empty report (0 counters) for it: Kover's project analyzer only
recognizes compilations from Kotlin plugins; it documents support for
“Kotlin JVM / Kotlin Multiplatform / Kotlin Android” and mixed
Kotlin+Java sources, but not a Java-only module.

Consequence: the root Kover merge covers `app`/`auto`/`core`; the Java module
is reported separately at
`osmscout-client-java/build/reports/jacoco/test/`. Its JUnit5 `test` task is
a Kover-merge-independent JaCoCo input (`jacocoTestReport` dependsOn `test`).

Alternatives rejected: (a) apply the Kotlin-JVM plugin to the module purely
for Kover — distorts a deliberately Java-only module and changes its API
surface; (b) import JaCoCo XML into the Kover merge — Kover only supports its
own `.ic` binary reports as additional input.

## Risks / Trade-offs

- [Kover emits a Gradle deprecation (`Project` dependency notation from its
own internals) on Gradle 9.6 → hard failure in Gradle 10] → Not fixable from
our scripts; string notation used everywhere we control. Tracked in TODO.md;
revisit on Kover upgrade.
- [Kover × AGP 9.4 integration still young] → Pin 0.9.8 (AGP 9 fixes since
  0.9.4); verified locally: per-variant + total report tasks registered and
  reports produced for app/auto/core; JaCoCo built-in is the documented
  fallback.
- [Robolectric sandbox classloading vs Kover instrumentation] → Verified:
  full `:koverXmlReport` run executed all Robolectric/Compose tests and
  produced non-empty coverage for app classes.
- [Double counting across modules] → Verified: merged class count (662) = sum
  of per-module class counts (462+162+38); no class duplication.
- [Instrumentation slows test runs] → Full instrumented suite ~4.5 min locally
  vs ~few s cached; plain `./gradlew test` unchanged. Acceptable.
- [`*`-wildcard exclusion drift (new generated class kinds)] → Requirement is
  behavioral (no generated classes in metrics): verified merged report has 0
  Hilt/Dagger/BuildConfig/R entries; future verify gate would harden it.
- [Kover pulls Gradle Plugin Portal deps, cold CI cache] → One-time; cache
  key changes via `*.gradle.kts` hash.

## Migration Plan

Additive. Land root + module build changes, run reports locally, then CI step.
Rollback: revert the commits; no persisted state (reports live under
`build/`). No `skip_specs`, no runtime surface.

## Verification

- Local (done): `./gradlew :koverHtmlReport :koverXmlReport` succeeds;
  merged report 662 classes, line 8839/14805 covered (59.7%), instruction
  58.2%, zero BuildConfig/R/Hilt/Dagger entries; per-module: app 58.9%, auto
  53.8%, core 67.3%; `./gradlew test` and `:app:assembleDebug` pass
- osmscout-client-java: `:osmscout-client-java:jacocoTestReport` produces
  HTML+XML with non-zero counters (LINE 5 covered / 1 missed)
- CI: PR run green; coverage artifact downloadable; app debug APK step
  unaffected

## Open Questions

- (resolved) Kover exclusion wildcard set — verified, see D3
- (resolved) aggregated root task naming — root `:koverHtmlReport`
  `:koverXmlReport` merge all `kover`-configuration projects
- (resolved) osmscout-client-java Kover support — none (pure Java), see D6
- app release-variant unit tests (default `testBuildType=debug`) — debug-only
  accepted; revisit only if numbers skew

> **Correction (2026-09-12, see change `fix-kover-aggregate-deps`)**: the note above
> ("string project-path notation; Project-object notation is deprecated in Gradle 9.6")
> is empirically wrong on Gradle 9.6.1 + Kover 0.9.8: `kover(":app")` string notation
> makes Kover resolve `:app:` as an external module coordinate and `:koverHtmlReport`
> fails with "Could not find :app:". `kover(project(":app"))` resolves correctly with
> no deprecation warning. The string-vs-project claim applies to `implementation`
> etc. only if a future Gradle deprecates it; for Kover aggregation use project notation.
