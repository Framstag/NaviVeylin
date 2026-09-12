## Why

The CI coverage step fails: `:koverHtmlReport` aborts with `Could not find :app:` / `:auto:` / `:core:` while resolving the `:koverExternalArtifacts` configuration. The root `build.gradle.kts` declares the merged modules as string project paths — `dependencies { kover(":app"); kover(":auto"); kover(":core") }` — and with Gradle 9.6.1 + Kover 0.9.8 Kover interprets those strings as external module coordinates (`:app:` with empty group/version) instead of project dependencies, so resolution fails. The failure reproduces locally (`./gradlew :koverHtmlReport`) and blocks the CI build after tests pass. `add-kover-coverage`'s design chose string notation on the premise that Project-object notation is deprecated in Gradle 9.6 — empirically false on this toolchain: Project-object notation resolves and produces no deprecation warning.

## What Changes

- Root `build.gradle.kts`: replace `kover(":app")` / `kover(":auto")` / `kover(":core")` with `kover(project(":app"))` / `kover(project(":auto"))` / `kover(project(":core"))` — Kover then binds the subprojects as project dependencies and merges their coverage.
- No Kover version change, no module changes, no report filters changes.
- Update the `add-kover-coverage` design note (string-notation rationale) to record the fix and the true behavior on Gradle 9.6.1.

## Capabilities

### New Capabilities

- `kover-aggregate-report`: The root Kover merge — `:koverHtmlReport` / `:koverXmlReport` resolve the `app`, `auto`, `core` project dependencies and produce the aggregated coverage report in CI and locally.

### Modified Capabilities

- None.

## Impact

- Root `build.gradle.kts` — 3 dependency declarations.
- Openspec change artifacts; a one-line correction in `add-kover-coverage/design.md` (in-flight change, kept as reference note).
- CI coverage step unblocked: `:koverHtmlReport :koverXmlReport :osmscout-client-java:jacocoTestReport` runs green.
- Rollback: revert the commit — coverage step fails again with `Could not find :app:`.
