# Spec Delta — sonar-analysis

## ADDED Requirements

### Requirement: The workflow's Gradle steps run a JVM-only graph

Every Gradle invocation in `.github/workflows/sonar.yml` whose requested tasks are unit tests or coverage
reports SHALL carry `-PjvmTestsOnly`. The licence inventory is a variant asset source, so such a graph carries
`generateLicenseAssets` → `generateSbom` → `mergeNativeSbom`, and `mergeNativeSbom` reads vcpkg's
installed-status file while it is configured; the scan workflow installs the Android SDK and never runs
`./setup-vcpkg.sh`, so a step without the toggle fails before it does any work and the scanner never runs. The
report step SHALL therefore read the reports the test step produced, which is why the workflow has two steps.
A packaging invocation is outside this rule: the toggle is refused for one, because an APK or AAB built with it
would ship without its `licenses/` assets.

#### Scenario: The report step carries the JVM-only toggle

- **WHEN** the coverage-report step of the scan workflow is read for the Gradle properties it passes, after the
  toggle was dropped from it or from the test step above it
- **THEN** the structural check reports that step and its line, because its graph would regain `mergeNativeSbom`
  and the run would fail before the scanner on a runner with no vcpkg tree
- **Case**: `sonar-workflow#theCoverageStepCarriesTheJvmOnlyFlag`
