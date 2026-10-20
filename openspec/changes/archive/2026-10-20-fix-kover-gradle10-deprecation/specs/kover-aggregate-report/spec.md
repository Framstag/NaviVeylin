## MODIFIED Requirements

### Requirement: No deprecation regression from the declaration

The Kover Gradle plugin pinned in the root `build.gradle.kts` SHALL be at least 0.9.9 — the release
whose changelog entry for upstream issue #818 states that its plugin-apply path no longer passes a
`Project` object to `DependencyHandler.add`, the call path Gradle 10 rejects. `guidelines/Build.md` §7
SHALL name the pinned version and SHALL NOT record that deprecation as an open upstream issue.

#### Scenario: The pinned release is the one the changelog names clean

- **WHEN** the Kover plugin version pinned in the root `build.gradle.kts` is read
- **THEN** it is 0.9.9 or newer — exercised by
  `KoverDeprecationBaselineTest.the pinned kover version clears the project-object deprecation`

#### Scenario: The coverage guideline names the pinned version

- **WHEN** `guidelines/Build.md` §7 is read
- **THEN** its engine table states the pinned version and it keeps no `Known upstream issue` record of
  the Kover deprecation — exercised by
  `KoverDeprecationBaselineTest.the coverage guideline names the pinned version and records no open upstream issue`
