## 1. Implementation (spec: kover-aggregate-report)

- [x] 1.1 Replace `kover(":app")`, `kover(":auto")`, `kover(":core")` with `kover(project(":app"))` etc. in root `build.gradle.kts`; verify `:koverExternalArtifacts` resolves without `Could not find` errors
- [x] 1.2 Add a correction note to `add-kover-coverage/design.md` recording that string notation breaks on Gradle 9.6.1 + Kover 0.9.8 and project notation resolves cleanly

## 2. Validation and CI verification

- [x] 2.1 Run `openspec validate` for the change and verify it passes
- [x] 2.2 Verify locally on JDK 21 (CI parity): `:koverHtmlReport :koverXmlReport :osmscout-client-java:jacocoTestReport` green and `build/reports/kover/html/index.html` exists
- [x] 2.3 Verify no new deprecation warnings from the `kover` declaration
- [x] 2.4 Push; verify the CI coverage step passes and the whole build is green
- [x] 2.5 Run `./gradlew test` on JDK 21 and verify green (whole-suite, CI parity) — green CI run on JDK 21 covers this
