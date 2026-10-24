# build-jvm-toolchain — deltas

## ADDED Requirements

### Requirement: The versioned property source names no Java home

The project-wide Gradle property source (`gradle.properties`) SHALL carry no `org.gradle.java.home` line: the
checked-in daemon JVM criteria select the daemon's JVM, so a machine-local absolute path there selects nothing
where the criteria apply. The absence SHALL be asserted twice — by a host case that scans the property source
and by the pipeline's step "Check daemon JVM criteria" — so a returning line fails a test run and the CI run.

#### Scenario: The property source carries no Java home

- **WHEN** the repository is built, its property source read as one of the files the project owns
- **THEN** every line that sets `org.gradle.java.home` is reported by line number and text, the property source
  reports none, and the printed measurement names the file it read and the number of findings
- **Case**: `app: VersionedGradleJavaHomeTest` "the versioned property source names no Java home"

#### Scenario: The pipeline refuses a reintroduced line

- **WHEN** a contributor copies a `org.gradle.java.home=<absolute path>` line from an archived design into
  `gradle.properties`
- **THEN** the workflow's step "Check daemon JVM criteria" reads that property source and fails the job before
  the SDK work, and the host case above reddens on the same line
- **Case**: `app: VersionedGradleJavaHomeTest` "the pipeline refuses a reintroduced line"

#### Scenario: The scan is still detectable

- **WHEN** a property source sets the key, indented or padded around the `=`, or names it only in a comment
- **THEN** the scan reports each setting line and leaves a comment and every other property alone — so a
  passing scan means the line is absent, not that the scan cannot see it
- **Case**: `app: VersionedGradleJavaHomeTest` "the scan is still detectable"
