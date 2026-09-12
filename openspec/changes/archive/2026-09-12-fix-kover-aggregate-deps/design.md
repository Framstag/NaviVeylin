## Context

See proposal.md - Why. The failure was reproduced locally: `./gradlew :koverHtmlReport` → `Could not find :app:` (plus `:auto:`, `:core:`) in configuration `:koverExternalArtifacts`, required by root project. The CI coverage step hit the same error before any test ran (2 s failure). The `add-kover-coverage` change (in-flight) adopted string notation citing a Gradle 9.6 deprecation of Project-object notation; empirical check on Gradle 9.6.1 + Kover 0.9.8 shows Project-object notation resolves cleanly with no deprecation warning.

## Goals / Non-Goals

**Goals:**
- `:koverHtmlReport` / `:koverXmlReport` resolve and merge `app`, `auto`, `core` coverage.
- No new deprecation warnings.
- Keep the report filters (BuildConfig/R/Hilt exclusions) untouched.

**Non-Goals:**
- No Kover version upgrade (0.9.8 stays; upgrade is a separate concern for Gradle 10 readiness).
- No fix to the unrelated, JDK-17-only flake in `MapCanvasViewModelViewportRestoreTest` (belongs to the in-flight zoom change; CI runs JDK 21 where it is stable).
- No threshold gate (`koverVerify`) — report-only as before.

## Decisions

### Decision 1: Project-object notation for the kover dependencies

Replace `kover(":app")` with `kover(project(":app"))` (same for `:auto`, `:core`). Kover 0.9.8 then treats the declarations as project dependencies; its internal `:koverExternalArtifacts` resolution no longer tries to fetch `:app:` as an external module.

**Alternatives considered:**

1. **Keep string notation and upgrade Kover** to a version that handles Gradle 9 project paths. Rejected — version bump is a bigger change with its own compatibility surface; project notation works on the pinned Kover today.
2. **Disable the external-artifacts step** (`kover { reports { ... } }` has no supported switch for it). Rejected — no supported option; project notation sidesteps the problem instead.
3. **Drop Kover aggregation; merge HTML/XML per module** (change the CI invocation to per-module report tasks). Rejected — changes the deliverable shape (root merged report) documented in guidelines/Build.md and `add-kover-coverage`.

### Decision 2: Keep the string-notation rationale corrected in add-kover-coverage

The in-flight `add-kover-coverage/design.md` records the wrong premise (string avoids a Gradle 9.6 deprecation). Add a correction note so the rationale is accurate for anyone reading the earlier change.

**Alternative:** leave the earlier design unmodified. Rejected — it actively misleads (claims the notation that breaks CI was preferred).

## Risks / Trade-offs

- **Gradle 10 readiness** — if Gradle 10 deprecates/fails `project(...)` in `dependencies {}` as the earlier design assumed, the Kover version must move forward anyway (Kover 0.9.8 is already flagged for Gradle 10). → Mitigation: documented as a separate upgrade task; project notation is the supported shape for Kover 0.9.x on Gradle 9.x.
- **Report content change risk** — switching dependency notation does not change which artifacts Kover merges (same three projects), so coverage numbers are unaffected. → Verified by a green run with an HTML report at the documented path.

## Migration Plan

1. Edit root `build.gradle.kts` (three declarations).
2. Verify locally on JDK 21 (CI parity): `./gradlew :koverHtmlReport :koverXmlReport :osmscout-client-java:jacocoTestReport` → green, report exists.
3. Push; CI coverage step must pass.
4. Rollback: revert the commit.

## Open Questions

None.
