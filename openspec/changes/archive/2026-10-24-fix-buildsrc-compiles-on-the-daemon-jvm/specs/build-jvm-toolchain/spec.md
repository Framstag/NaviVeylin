# build-jvm-toolchain — deltas

## ADDED Requirements

### Requirement: The build logic compiles on the JVM the daemon criteria select

The build logic SHALL declare no JVM toolchain of its own: `buildSrc` is a build of its own, its toolchain cannot
be provisioned, and a machine without that JDK fails at configuration. The JVM compiling it SHALL be the one
`gradle/gradle-daemon-jvm.properties` selects. The absence SHALL be asserted by a host case and by the pipeline's
step "Check daemon JVM criteria", and stated in `guidelines/Build.md` §2d.

#### Scenario: The build script names no JVM of its own

- **WHEN** `buildSrc/build.gradle.kts` is read as the build logic's own build script
- **THEN** every line that asks for a toolchain, a `languageVersion` or a `JavaLanguageVersion` is reported by
  line number and text, the script reports none, and the printed measurement names the file and its line count
- **Case**: `app: BuildSrcJvmRequirementTest` "the build script names no JVM of its own"

#### Scenario: The criteria still pin the JVM the build logic compiles with

- **WHEN** the build logic names no JVM of its own, so the daemon criteria are the only declaration of what
  compiles it
- **THEN** `gradle/gradle-daemon-jvm.properties` carries exactly one `toolchainVersion` line and no
  `toolchainVendor` line, so the absence means "the criteria's JDK" and never "no pinned JVM at all"
- **Case**: `app: BuildSrcJvmRequirementTest` "the criteria still pin the JVM the build logic compiles with"

#### Scenario: A machine that satisfies the criteria configures the build logic

- **WHEN** toolchain auto-detection is switched off, so the criteria are the only way to a daemon JVM
- **THEN** the configuration of this repository reaches a task instead of failing in buildSrc, which is the
  defect's own reproduction: `BUILD FAILED in 645ms` on the base tree, `BUILD SUCCESSFUL` after the fix
- **Case**: `./gradlew -Dorg.gradle.java.installations.auto-detect=false help` (`guidelines/Build.md` §2d, the
  checks block; `evidence/repro-on-head.log` → `evidence/repro-after.log`)

#### Scenario: A reintroduced toolchain is refused

- **WHEN** a contributor adds a toolchain or a `languageVersion` to `buildSrc/build.gradle.kts` — for instance by
  restoring the Java 17 pin this change removes
- **THEN** the workflow's step "Check daemon JVM criteria" fails the job with `::error file=buildSrc/build.gradle.kts`
  before the SDK work, and the host case above reddens on the same lines
- **Case**: `app: BuildSrcJvmRequirementTest` "a reintroduced toolchain is refused" and the step's own run
  (`evidence/ci-step-revert-red.log`)

#### Scenario: The scan is still detectable

- **WHEN** the build script states a toolchain (`toolchain { … }`, `jvmToolchain(17)`) or a `languageVersion`,
  or names one only in a comment, or sets a compile target (`sourceCompatibility`, `jvmTarget`)
- **THEN** the scan reports each requirement line and leaves the comment and the compile target alone — so a
  passing scan means the requirement is absent, not that the scan cannot see it
- **Case**: `app: BuildSrcJvmRequirementTest` "the scan is still detectable"
