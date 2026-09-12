## Why

The project runs a real test suite (~24k LOC Kotlin across 4 modules: app,
auto, core, osmscout-client-java) but has **no code coverage measurement** —
no JaCoCo, no Kover, no CI coverage step. Tests stay on, but nobody can see
what they cover. The archive guidance already demands "enough tests per
feature", yet nothing measures it; coverage visibility is the missing link.
CI already runs `./gradlew test`, so measurement is a small, additive step on
top of existing infrastructure.

## What Changes

- Introduce **Kover** (`org.jetbrains.kotlinx.kover` 0.9.8, AGP 9.4-compatible
  since 0.9.4) as a Gradle plugin across all four modules
- Generate per-module + aggregated cross-module coverage reports
  (HTML + XML) for the JVM unit test task graph (`test` / `testDebugUnitTest`,
  incl. Robolectric Compose UI tests)
- Exclude generated code from instrumentation/metrics: `BuildConfig`, `R`,
  Dagger/Hilt/KSP-generated classes
- Wire CI (`.github/workflows/build.yml`): a coverage report step after
  "Run unit tests" that uploads HTML/XML as a workflow artifact
- Document the workflow in `guidelines/Build.md` (how to run, where reports go,
  how to read the numbers)
- **No enforcement gate** in this change — report-only first; a baseline is
  measured so a future `koverVerify` threshold can be set on real numbers
- Additive, no app/runtime behavior change, no `androidTest`/device involvement
  (0 instrumented test files exist; CI has no device)

## Capabilities

### New Capabilities

- `test-coverage`: The build system measures JVM unit test coverage via Kover —
  per-module and aggregated HTML/XML reports for `app`, `auto`, `core`,
  `osmscout-client-java`, generated output excluded, results publishable
  from CI as an artifact.

### Modified Capabilities

None — no existing capability's requirements change (pure build-tooling
addition, app behavior untouched).

## Impact

Affected files:
- `build.gradle.kts` (root): add `org.jetbrains.kotlinx.kover` 0.9.8 to the
  plugin management (applied in modules + aggregation at root)
- `app/build.gradle.kts`: apply Kover, configure report filters (exclude
  `BuildConfig`, `R`, Hilt/Dagger generated)
- `auto/build.gradle.kts`: same
- `core/build.gradle.kts`: same
- `osmscout-client-java/build.gradle.kts`: apply Kover (plain `java-library`,
  JUnit5; covered via its `test` task)
- `.github/workflows/build.yml`: coverage report step + artifact upload after
  "Run unit tests"
- `guidelines/Build.md`: new "Code coverage" section (commands, report paths,
  module coverage, exclusion rationale)

Non-affected: app runtime, native C++/JNI code, stylesheets, release pipeline,
Play distribution. Kover measures JVM bytecode — Robolectric tests run on the
host JVM and are covered; on-device instrumented coverage is explicitly out of
scope (no device tests exist, CI has no device).

Dependencies: Kover 0.9.8 (Gradle Plugin Portal; Kotlin 2.2.x line; AGP 9.x
support landed in 0.9.4). No new native/vcpkg deps.

Guidelines affected: `guidelines/Build.md` (documented workflow).

Additive change; rollback = revert plugin declarations + CI step, no residual
state (reports live in `build/` only).
